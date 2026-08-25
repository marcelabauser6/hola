package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccess;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Central bank actions. Founders and operators, re-checked here; licensing is operators only. */
public class C2SCentralActionPacket {

    public enum Action {
        INJECT,
        DRAIN,
        SET_OFFICIAL_BRONZE,
        SET_OFFICIAL_SILVER,
        SET_OFFICIAL_GOLD,
        /** Licenses a player to found and run banks. {@code bankId} carries the player's UUID. */
        ADD_FOUNDER,
        /** Withdraws that licence. */
        REMOVE_FOUNDER,
        /** Hands over a stats board reporting the whole server rather than one bank. */
        ISSUE_HOLOGRAM;

        private static final Action[] VALUES = values();

        static Action byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : INJECT;
        }

        /**
         * Whether only a real operator may do this.
         *
         * <p>Licensing is the one thing a founder cannot do. Otherwise the first founder could appoint a
         * second, and the licence would stop being something the server's owner controls.</p>
         */
        boolean operatorOnly() {
            return this == ADD_FOUNDER || this == REMOVE_FOUNDER;
        }
    }

    private final BlockPos pos;
    private final Action action;
    private final UUID bankId;
    private final long value;

    public C2SCentralActionPacket(BlockPos pos, Action action, UUID bankId, long value) {
        this.pos = pos;
        this.action = action;
        this.bankId = bankId == null ? new UUID(0L, 0L) : bankId;
        this.value = value;
    }

    public C2SCentralActionPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.action = Action.byOrdinal(buffer.readByte());
        this.bankId = buffer.readUUID();
        this.value = buffer.readVarLong();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(action.ordinal());
        buffer.writeUUID(bankId);
        buffer.writeVarLong(value);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !BankAccess.canFoundBanks(player)) {
                return;
            }
            if (action.operatorOnly() && !BankAccess.isOperator(player)) {
                player.sendSystemMessage(Component
                        .translatable("message.athens_coins.central_licence_op_only")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            apply(player);
            ModNetwork.toPlayer(player, S2COpenCentralPacket.of(player, pos));
        });
        ctx.setPacketHandled(true);
    }

    private void apply(ServerPlayer player) {
        BankData data = BankData.get(player.server);
        String symbol = CurrencyConfig.get().currencySymbol;

        switch (action) {
            case INJECT -> {
                Bank bank = data.bank(bankId);
                if (bank == null || value <= 0L) {
                    return;
                }
                BankManager.inject(player.server, bank, value);
                player.sendSystemMessage(Component.translatable("message.athens_coins.central_injected",
                                Money.format(value, symbol), bank.name())
                        .withStyle(ChatFormatting.GREEN));
            }
            case DRAIN -> {
                Bank bank = data.bank(bankId);
                if (bank == null || value <= 0L) {
                    return;
                }
                // Only ever takes what is actually there, so a reserve cannot go negative.
                long taken = Math.min(value, bank.reserve());
                if (taken <= 0L || !bank.drawReserve(taken)) {
                    player.sendSystemMessage(Component
                            .translatable("message.athens_coins.central_nothing_to_drain")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                data.addIssued(-taken);
                data.setDirty();
                player.sendSystemMessage(Component.translatable("message.athens_coins.central_drained",
                                Money.format(taken, symbol), bank.name())
                        .withStyle(ChatFormatting.YELLOW));
            }
            case SET_OFFICIAL_BRONZE -> setOfficial(player, CoinType.BRONZE, symbol);
            case SET_OFFICIAL_SILVER -> setOfficial(player, CoinType.SILVER, symbol);
            case SET_OFFICIAL_GOLD -> setOfficial(player, CoinType.GOLD, symbol);
            case ADD_FOUNDER -> licence(player, data, true);
            case REMOVE_FOUNDER -> licence(player, data, false);
            case ISSUE_HOLOGRAM -> issueGlobalBoard(player);
        }
    }

    /**
     * Grants or withdraws a banking licence.
     *
     * <p>{@code bankId} carries the player's UUID here rather than a bank's. Reusing the field is
     * deliberate: it is already a UUID on the wire and the packet only ever names one subject, so adding
     * a second UUID field would mean every existing action writing eight wasted bytes.</p>
     */
    private void licence(ServerPlayer player, BankData data, boolean grant) {
        if (bankId.equals(new UUID(0L, 0L))) {
            return;
        }
        // Never strip an operator's own authority: it does not come from the licence, so removing one
        // they never needed would look like it had worked and changed nothing.
        ServerPlayer subject = player.server.getPlayerList().getPlayer(bankId);
        String name = subject != null ? subject.getGameProfile().getName() : bankId.toString();
        boolean changed = grant ? data.addFounder(bankId) : data.removeFounder(bankId);
        if (!changed) {
            return;
        }
        player.sendSystemMessage(Component.translatable(grant
                        ? "message.athens_coins.central_licensed"
                        : "message.athens_coins.central_unlicensed", name)
                .withStyle(grant ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        if (subject != null) {
            subject.sendSystemMessage(Component.translatable(grant
                            ? "message.athens_coins.central_licensed_you"
                            : "message.athens_coins.central_unlicensed_you")
                    .withStyle(grant ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            // The licence changes which terminals they may open, so refresh their command tree too.
            player.server.getCommands().sendCommands(subject);
        }
    }

    /**
     * Hands over a board that reports the whole server.
     *
     * <p>The counterpart to the bank terminal's own issue button. A board issued there is branded with
     * that bank and reports only its figures; one issued here is unbranded and reports the economy - which
     * is the only kind of board that can honestly show the money supply, because no single bank knows it.</p>
     */
    private void issueGlobalBoard(ServerPlayer player) {
        ItemStack board = new ItemStack(
                com.athensmc.athenscoins.item.ModItems.STATS_HOLOGRAM_ITEM.get(),
                (int) Math.max(1L, Math.min(16L, value)));
        if (!player.getInventory().add(board)) {
            player.drop(board, false);
        }
        player.sendSystemMessage(Component
                .translatable("message.athens_coins.central_board_issued")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Moves an official rate and pulls every bank back inside the new band.
     *
     * <p>Without the realign, a bank sitting at the old ceiling would silently be out of policy.</p>
     */
    private void setOfficial(ServerPlayer player, CoinType type, String symbol) {
        if (value <= 0L) {
            return;
        }
        CurrencyConfig.setOfficialRate(type, value);
        BankManager.realignAllRates(player.server);
        player.sendSystemMessage(Component.translatable("message.athens_coins.central_rate_set",
                        type.shortName(), Money.format(value, symbol))
                .withStyle(ChatFormatting.GREEN));
    }
}
