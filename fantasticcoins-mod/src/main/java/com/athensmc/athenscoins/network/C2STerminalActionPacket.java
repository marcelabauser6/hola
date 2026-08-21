package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.bank.BankRules;
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

/**
 * Everything the terminal can ask the server to do.
 *
 * <p>Authority is re-checked here rather than trusted from the screen: a client could send any of
 * these at any time, so operator-only actions verify permissions and banker actions verify the
 * sender is on that bank's staff and standing at its terminal.</p>
 */
public class C2STerminalActionPacket {

    public enum Action {
        ADD_BANKER,
        REMOVE_BANKER,
        OPEN_ACCOUNT,
        SET_NAME,
        SET_COLOR,
        SET_WALLET_LIMIT,
        SET_FEE,
        SET_FEE_DAYS,
        SET_RATE_BRONZE,
        SET_RATE_SILVER,
        SET_RATE_GOLD,
        SET_LOANS_ENABLED,
        SET_LOAN_MAX,
        SET_LOAN_DAYS,
        SET_LOAN_INTEREST,
        ISSUE_ATM,
        WITHDRAW_ALL,
        GRANT_LOAN,
        REPAY_LOAN;

        private static final Action[] VALUES = values();

        static Action byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ADD_BANKER;
        }

        boolean operatorOnly() {
            return switch (this) {
                case OPEN_ACCOUNT, ISSUE_ATM, WITHDRAW_ALL, GRANT_LOAN, REPAY_LOAN -> false;
                default -> true;
            };
        }
    }

    private final BlockPos pos;
    private final Action action;
    private final UUID target;
    /** Account this action applies to, 0 when it does not apply to one. */
    private final int account;
    private final long value;
    private final String text;

    public C2STerminalActionPacket(BlockPos pos, Action action, UUID target, long value, String text) {
        this(pos, action, target, 0, value, text);
    }

    public C2STerminalActionPacket(BlockPos pos, Action action, UUID target, int account,
                                   long value, String text) {
        this.pos = pos;
        this.action = action;
        this.target = target == null ? new UUID(0L, 0L) : target;
        this.account = account;
        this.value = value;
        this.text = text == null ? "" : text;
    }

    public C2STerminalActionPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.action = Action.byOrdinal(buffer.readByte());
        this.target = buffer.readUUID();
        this.account = buffer.readVarInt();
        this.value = buffer.readVarLong();
        this.text = buffer.readUtf(32);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(action.ordinal());
        buffer.writeUUID(target);
        buffer.writeVarInt(account);
        buffer.writeVarLong(value);
        buffer.writeUtf(text, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                apply(player);
            }
        });
        ctx.setPacketHandled(true);
    }

    private void apply(ServerPlayer player) {
        // Must actually be at a terminal, and within reach of it.
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return;
        }
        BankData data = BankData.get(player.server);
        Bank bank = data.bankAt(pos);
        if (bank == null) {
            return;
        }

        boolean operator = player.hasPermissions(2);
        if (!operator && !bank.isBanker(player.getUUID())) {
            return;
        }
        if (action.operatorOnly() && !operator) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_op_only")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        switch (action) {
            case ADD_BANKER -> {
                bank.bankers().add(target);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_banker_added");
            }
            case REMOVE_BANKER -> {
                bank.bankers().remove(target);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_banker_removed");
            }
            case OPEN_ACCOUNT -> openAccount(player, data, bank);
            case ISSUE_ATM -> issueAtm(player, bank);
            case WITHDRAW_ALL -> withdrawAll(player, data, bank);
            case GRANT_LOAN -> grantLoan(player, data, bank);
            case REPAY_LOAN -> repayLoan(player, data, bank);
            case SET_NAME -> {
                bank.setName(text);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
            case SET_COLOR -> {
                bank.setThemeColor((int) value);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
            case SET_WALLET_LIMIT -> {
                if (BankManager.setWalletLimit(player.server, bank, value, player.getUUID())) {
                    feedback(player, "message.athens_coins.bank_saved");
                } else {
                    player.sendSystemMessage(Component.translatable("message.athens_coins.amount_too_big")
                            .withStyle(ChatFormatting.RED));
                }
            }
            case SET_FEE -> {
                bank.setCommissionFee(value);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
            case SET_FEE_DAYS -> {
                bank.setCommissionPeriodDays((int) value);
                BankManager.resetCommissionPolicyClock(player.server, bank, System.currentTimeMillis());
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
            case SET_RATE_BRONZE -> setRate(player, data, bank, CoinType.BRONZE);
            case SET_RATE_SILVER -> setRate(player, data, bank, CoinType.SILVER);
            case SET_RATE_GOLD -> setRate(player, data, bank, CoinType.GOLD);
            case SET_LOANS_ENABLED -> {
                bank.setLoansEnabled(value != 0L);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
            case SET_LOAN_MAX -> {
                bank.setLoanMaxAmount(value);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
            case SET_LOAN_DAYS -> {
                bank.setLoanDays((int) value);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
            case SET_LOAN_INTEREST -> {
                bank.setLoanInterestBasisPoints((int) value);
                data.setDirty();
                feedback(player, "message.athens_coins.bank_saved");
            }
        }

        // Refresh the terminal list. The loan actions additionally re-push the detail screen,
        // which arrives after this and therefore wins.
        ModNetwork.toPlayer(player, S2COpenTerminalPacket.of(player, bank, pos, operator));
    }

    private void setRate(ServerPlayer player, BankData data, Bank bank, CoinType type) {
        long official = CurrencyConfig.get().coinValueCents(type);
        int margin = CurrencyConfig.get().rateMarginPercent;
        long clamped = BankRules.clampRate(value, official, margin);
        bank.setRate(type, value);
        data.setDirty();
        String symbol = CurrencyConfig.get().currencySymbol;
        if (clamped != value) {
            // Say so rather than silently changing it: the banker needs to know the band exists.
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_rate_clamped",
                            Money.format(clamped, symbol),
                            Money.format(BankRules.rateFloor(official, margin), symbol),
                            Money.format(BankRules.rateCeiling(official, margin), symbol))
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            feedback(player, "message.athens_coins.bank_saved");
        }
    }

    private void openAccount(ServerPlayer player, BankData data, Bank bank) {
        ServerPlayer holder = player.server.getPlayerList().getPlayer(target);
        if (holder == null) {
            player.sendSystemMessage(Component
                    .translatable("message.athens_coins.bank_holder_offline")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankManager.OpenResult result = BankManager.openAccount(player.server, bank,
                holder.getUUID(), holder.getGameProfile().getName(), 0L);
        if (!result.ok()) {
            player.sendSystemMessage(Component.translatable(result.messageKey())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankAccount account = data.account(result.number());
        // The banker receives the tag and hands it to the customer.
        ItemStack tag = BankManager.accountTag(account, bank);
        if (!player.getInventory().add(tag)) {
            player.drop(tag, false);
        }
        player.sendSystemMessage(Component.translatable("message.athens_coins.bank_account_opened",
                        holder.getGameProfile().getName(), result.number())
                .withStyle(ChatFormatting.GREEN));
        holder.sendSystemMessage(Component.translatable("message.athens_coins.bank_account_yours",
                        bank.name(), result.number())
                .withStyle(ChatFormatting.GREEN));
    }

    /** Hands the banker a machine branded with this bank. */
    private void issueAtm(ServerPlayer player, Bank bank) {
        ItemStack atm = new ItemStack(
                com.athensmc.athenscoins.item.ModItems.ATM_ITEM.get(),
                (int) Math.max(1L, Math.min(16L, value)));
        com.athensmc.athenscoins.block.AtmBlockEntity.brand(atm, bank);
        atm.setHoverName(Component.translatable("item.athens_coins.atm_of", bank.name()));
        if (!player.getInventory().add(atm)) {
            player.drop(atm, false);
        }
        feedback(player, "message.athens_coins.bank_atm_issued");
    }

    /** Closes an account and puts everything it held onto a signed card. */
    private void withdrawAll(ServerPlayer player, BankData data, Bank bank) {
        BankAccount target = data.account(this.account);
        if (target == null || !target.bankId().equals(bank.id())) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_no_such_account")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String holder = target.ownerName();
        int number = target.number();
        java.util.UUID ownerId = target.owner();
        BankManager.CloseResult closed = BankManager.closeAccount(
                player.server, target, true, player.getUUID());
        if (!closed.ok()) {
            player.sendSystemMessage(Component.translatable(closed.messageKey())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        long payout = closed.payout();
        ItemStack card = com.athensmc.athenscoins.item.BankCardItem.create(
                player.server, closed.card(), holder);
        if (!player.getInventory().add(card)) {
            player.drop(card, false);
        }
        player.sendSystemMessage(Component.translatable("message.athens_coins.bank_closed_account",
                        holder, Money.format(payout, CurrencyConfig.get().currencySymbol))
                .withStyle(ChatFormatting.YELLOW));
        ServerPlayer owner = player.server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.athens_coins.bank_closed_yours",
                    bank.name()).withStyle(ChatFormatting.YELLOW));
            com.athensmc.athenscoins.wallet.WalletManager.pushBalance(owner);
        }
    }

    private void grantLoan(ServerPlayer player, BankData data, Bank bank) {
        BankAccount target = data.account(this.account);
        if (target == null || !target.bankId().equals(bank.id())) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_no_such_account")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankManager.LoanResult result = BankManager.grantLoan(player.server, target, value);
        player.sendSystemMessage(Component.translatable(result.messageKey())
                .withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (result.ok()) {
            refreshAccount(player, bank, target.number());
        }
    }

    private void repayLoan(ServerPlayer player, BankData data, Bank bank) {
        BankAccount target = data.account(this.account);
        if (target == null || !target.bankId().equals(bank.id())) {
            return;
        }
        long paid = BankManager.repayLoan(player.server, target, value);
        player.sendSystemMessage(Component.translatable(paid > 0L
                        ? "message.athens_coins.bank_loan_repaid"
                        : "message.athens_coins.bank_loan_nothing",
                        Money.format(paid, CurrencyConfig.get().currencySymbol))
                .withStyle(paid > 0L ? ChatFormatting.GREEN : ChatFormatting.RED));
        refreshAccount(player, bank, target.number());
    }

    /** Re-sends the detail screen so the banker sees the result immediately. */
    private void refreshAccount(ServerPlayer player, Bank bank, int number) {
        BankAccount fresh = BankData.get(player.server).account(number);
        if (fresh != null) {
            ModNetwork.toPlayer(player, new S2COpenAccountPacket(pos, fresh, bank));
        }
    }

    private void feedback(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GREEN), true);
    }
}
