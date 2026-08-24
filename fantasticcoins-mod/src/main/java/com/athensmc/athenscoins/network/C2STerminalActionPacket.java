package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.bank.BankConfirmations;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.bank.LoanRequest;
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
        REPAY_LOAN,
        /**
         * Approves a loan application. {@code account} carries the request id and {@code value} the
         * amount actually granted, so a banker can approve less than was asked for.
         */
        APPROVE_LOAN,
        /** Refuses an application; {@code account} carries the request id. */
        REJECT_LOAN;

        private static final Action[] VALUES = values();

        static Action byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ADD_BANKER;
        }

        boolean operatorOnly() {
            return switch (this) {
                case OPEN_ACCOUNT, ISSUE_ATM, WITHDRAW_ALL, GRANT_LOAN, REPAY_LOAN,
                        APPROVE_LOAN, REJECT_LOAN -> false;
                default -> true;
            };
        }

        /**
         * Whether this action asks before it acts.
         *
         * <p>The test is "can a stray click cause harm somebody else has to live with". Handing over
         * banker access, opening an account in somebody's name, closing one, and deciding a loan all
         * qualify; changing a rate or issuing an ATM to your own inventory does not.</p>
         */
        boolean needsConfirmation() {
            return switch (this) {
                case ADD_BANKER, REMOVE_BANKER, OPEN_ACCOUNT, WITHDRAW_ALL,
                        APPROVE_LOAN, REJECT_LOAN -> true;
                default -> false;
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
        apply(player, false);
    }

    /**
     * Runs a proposal the actor has confirmed.
     *
     * <p>Rebuilds the original request and takes the same path an unconfirmed action takes, so the
     * confirmed and unconfirmed routes cannot drift apart - the permission and reach checks are
     * re-run here too, because the actor may have walked away or lost access while the question sat
     * in their chat.</p>
     */
    public static void performConfirmed(ServerPlayer player, BankConfirmations.Pending pending) {
        Action action = switch (pending.kind()) {
            case OPEN_ACCOUNT -> Action.OPEN_ACCOUNT;
            case GRANT_BANKER -> Action.ADD_BANKER;
            case REVOKE_BANKER -> Action.REMOVE_BANKER;
            case CLOSE_ACCOUNT -> Action.WITHDRAW_ALL;
            case APPROVE_LOAN -> Action.APPROVE_LOAN;
            case REJECT_LOAN -> Action.REJECT_LOAN;
        };
        new C2STerminalActionPacket(pending.pos(), action, pending.target(), pending.account(),
                pending.amount(), "").apply(player, true);
    }

    private void apply(ServerPlayer player, boolean confirmed) {
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
        // Nothing outside the settings tab may run before the bank has terms; an account opened
        // against defaults is an account opened on terms nobody chose.
        if (!bank.configured() && action != Action.SET_NAME && action != Action.SET_COLOR) {
            player.sendSystemMessage(Component.translatable("gui.athens_coins.cfg_required")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        // The irreversible ones ask first. A click on a row proposes; the chat buttons decide.
        if (!confirmed && action.needsConfirmation()) {
            proposeConfirmation(player, data, bank);
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
            case APPROVE_LOAN -> approveLoan(player, data, bank);
            case REJECT_LOAN -> rejectLoan(player, data, bank);
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
        // liquidate = false: a closure must not settle a live loan or unpaid fees out of the balance
        // behind the customer's back. If there is debt, the close is refused and says why, and the
        // banker collects it first. This used to pass true, which meant closing an account was also a
        // way to make a loan disappear.
        BankManager.CloseResult closed = BankManager.closeAccount(
                player.server, target, false, player.getUUID());
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

    /**
     * Approves an application and lends the money.
     *
     * <p>{@code account} is the request id and {@code value} the amount to grant, which lets a banker
     * approve less than was asked for. The request is only removed once the loan actually exists: if
     * the reserve cannot cover it the application stays in the queue rather than vanishing.</p>
     */
    private void approveLoan(ServerPlayer player, BankData data, Bank bank) {
        LoanRequest request = data.loanRequest(this.account);
        if (request == null || !request.bankId().equals(bank.id())) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.loan_request_gone")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankAccount target = data.account(request.account());
        if (target == null || !target.bankId().equals(bank.id())) {
            // The applicant closed or moved their account while the request waited.
            data.removeLoanRequest(request.id());
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_no_such_account")
                    .withStyle(ChatFormatting.RED));
            ModNetwork.toPlayer(player, S2COpenTerminalPacket.of(player, bank, pos, player.hasPermissions(2)));
            return;
        }
        long amount = value > 0L ? Math.min(value, request.amount()) : request.amount();
        BankManager.LoanResult result = BankManager.grantLoan(player.server, target, amount);
        if (!result.ok()) {
            player.sendSystemMessage(Component.translatable(result.messageKey())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        data.removeLoanRequest(request.id());
        String symbol = CurrencyConfig.get().currencySymbol;
        player.sendSystemMessage(Component.translatable("message.athens_coins.loan_request_approved",
                        request.borrowerName(), Money.format(result.amount(), symbol))
                .withStyle(ChatFormatting.GREEN));
        com.athensmc.athenscoins.bank.Loan loan = target.loan();
        if (loan != null) {
            com.athensmc.athenscoins.bank.LoanNotices.granted(player.server, target,
                    result.amount(), loan.dueAt());
        }
        refreshAccount(player, bank, target.number());
    }

    private void rejectLoan(ServerPlayer player, BankData data, Bank bank) {
        LoanRequest request = data.loanRequest(this.account);
        if (request == null || !request.bankId().equals(bank.id())) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.loan_request_gone")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        data.removeLoanRequest(request.id());
        player.sendSystemMessage(Component.translatable("message.athens_coins.loan_request_refused",
                        request.borrowerName()).withStyle(ChatFormatting.YELLOW));
        com.athensmc.athenscoins.bank.LoanNotices.applicationRejected(player.server, bank, request);
    }

    /**
     * Turns a click into a question, with the subject resolved to a name the actor will recognise.
     *
     * <p>Resolved here rather than in the confirmation store because this is the only place that has
     * the bank data to hand; a proposal reading "confirm action on 7f3a-..." would be worse than no
     * proposal at all.</p>
     */
    private void proposeConfirmation(ServerPlayer player, BankData data, Bank bank) {
        String subject;
        long amount = value;
        switch (action) {
            case ADD_BANKER, REMOVE_BANKER, OPEN_ACCOUNT -> {
                ServerPlayer who = player.server.getPlayerList().getPlayer(target);
                if (who == null) {
                    player.sendSystemMessage(Component
                            .translatable("message.athens_coins.bank_holder_offline")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                subject = who.getGameProfile().getName();
            }
            case WITHDRAW_ALL -> {
                BankAccount account = data.account(this.account);
                if (account == null || !account.bankId().equals(bank.id())) {
                    player.sendSystemMessage(Component
                            .translatable("message.athens_coins.bank_no_such_account")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                subject = "#" + account.number() + " " + account.ownerName();
            }
            case APPROVE_LOAN, REJECT_LOAN -> {
                LoanRequest request = data.loanRequest(this.account);
                if (request == null || !request.bankId().equals(bank.id())) {
                    player.sendSystemMessage(Component
                            .translatable("message.athens_coins.loan_request_gone")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                subject = request.borrowerName();
                amount = request.amount();
            }
            default -> {
                return;
            }
        }
        BankConfirmations.Kind kind = switch (action) {
            case ADD_BANKER -> BankConfirmations.Kind.GRANT_BANKER;
            case REMOVE_BANKER -> BankConfirmations.Kind.REVOKE_BANKER;
            case OPEN_ACCOUNT -> BankConfirmations.Kind.OPEN_ACCOUNT;
            case WITHDRAW_ALL -> BankConfirmations.Kind.CLOSE_ACCOUNT;
            case APPROVE_LOAN -> BankConfirmations.Kind.APPROVE_LOAN;
            default -> BankConfirmations.Kind.REJECT_LOAN;
        };
        BankConfirmations.propose(player, kind, pos, target, this.account, amount, subject);
    }

    private void grantLoan(ServerPlayer player, BankData data, Bank bank) {
        BankAccount target = data.account(this.account);
        if (target == null || !target.bankId().equals(bank.id())) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_no_such_account")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankManager.LoanResult result = BankManager.grantLoan(player.server, target, value);
        if (!result.ok()) {
            player.sendSystemMessage(Component.translatable(result.messageKey())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        // Say how much was lent. The bank clamps the request against its reserve, its policy and what
        // the customer already owes, so "Loan granted" on its own left the banker unable to tell a
        // full grant from a heavily reduced one.
        String symbol = CurrencyConfig.get().currencySymbol;
        player.sendSystemMessage(Component.translatable(
                        "message.athens_coins.bank_loan_granted_amount",
                        Money.format(result.amount(), symbol))
                .withStyle(ChatFormatting.GREEN));
        if (result.partial()) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_loan_partial",
                            Money.format(result.amount(), symbol))
                    .withStyle(ChatFormatting.YELLOW));
        }
        // The borrower is the one who has to repay it, so tell them too.
        com.athensmc.athenscoins.bank.Loan loan = target.loan();
        if (loan != null) {
            com.athensmc.athenscoins.bank.LoanNotices.granted(player.server, target,
                    result.amount(), loan.dueAt());
        }
        refreshAccount(player, bank, target.number());
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
