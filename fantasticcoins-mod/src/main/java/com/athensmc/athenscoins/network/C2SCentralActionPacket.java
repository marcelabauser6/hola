package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
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
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Central bank actions. Operator only, re-checked here. */
public class C2SCentralActionPacket {

    public enum Action {
        INJECT,
        DRAIN,
        SET_OFFICIAL_BRONZE,
        SET_OFFICIAL_SILVER,
        SET_OFFICIAL_GOLD;

        private static final Action[] VALUES = values();

        static Action byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : INJECT;
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
            if (player == null || !player.hasPermissions(2)) {
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
        }
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
