package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.bank.BankAccess;
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
         * Hands over a stats board branded with this bank. {@code value} is the stack count.
         *
         * <p>Appended at the end, like every action before it: the ordinal is the wire value, so
         * inserting one in the middle would renumber every action after it and an older client would
         * send "approve loan" where the server read "issue board".</p>
         */
        ISSUE_HOLOGRAM,
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

        /**
         * Whether this action changes the bank's terms rather than serving a customer.
         *
         * <p>Was {@code operatorOnly}. The name was the problem as much as the check: the tier that may
         * change a bank's terms is now "manager" - an operator <em>or</em> a founder licensed by the
         * central bank - and a method called {@code operatorOnly} invites the next reader to add an
         * {@code hasPermissions(2)} beside it.</p>
         *
         * <p>Bankers are excluded on purpose. A teller serves customers; the fee and the lending policy
         * are the owner's decision, and a banker who could move the fee could change a contract the
         * customers already signed up to.</p>
         */
        boolean managerOnly() {
            return switch (this) {
                case OPEN_ACCOUNT, ISSUE_ATM, ISSUE_HOLOGRAM, WITHDRAW_ALL, GRANT_LOAN, REPAY_LOAN,
                        APPROVE_LOAN, REJECT_LOAN -> false;
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

        boolean manager = BankAccess.canConfigure(player);
        if (!BankAccess.canOpenTerminal(player, bank)) {
            return;
        }
        if (action.managerOnly() && !manager) {
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
        // No confirmation step here any more. The irreversible actions are confirmed in the screen, by a
        // dialog drawn over the tab that started them, and the packet is only sent once the user has
        // said yes - so by the time anything arrives here the decision has already been made. Asking
        // again in chat put the question three rooms away from the click that caused it.
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
            case ISSUE_HOLOGRAM -> issueHologram(player, bank);
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
        ModNetwork.toPlayer(player, S2COpenTerminalPacket.of(player, bank, pos, manager));
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
        // Refused early rather than after the customer has agreed: being told "you already have an
        // account" is the banker's problem to see, not something to interrupt the customer with.
        if (data.hasAccount(holder.getUUID())) {
            player.sendSystemMessage(Component.translatable("gui.athens_coins.click_already_has",
                    holder.getGameProfile().getName()).withStyle(ChatFormatting.RED));
            return;
        }
        // The account is offered, not created. An account is an agreement, and the person who has to
        // agree is the one who will be paying the fee - so the question, and the terms, go to them.
        com.athensmc.athenscoins.bank.AccountOffers.offer(player, holder, bank);
        feedback(player, "message.athens_coins.offer_sent");
    }

    /**
     * Creates the account a customer has just accepted.
     *
     * <p>Lives here rather than in the offer store because this is where the rest of the opening lives:
     * the tag the banker hands over, the wording of both messages. The bank is re-resolved by id, and the
     * banker no longer has to be online - they made the offer, the customer accepted it, and whether the
     * staff member is still logged in is not the customer's problem.</p>
     */
    public static void completeOffer(ServerPlayer holder,
                                     com.athensmc.athenscoins.bank.AccountOffers.Offer offer) {
        BankData data = BankData.get(holder.server);
        Bank bank = data.bank(offer.bankId());
        if (bank == null) {
            holder.sendSystemMessage(Component
                    .translatable("message.athens_coins.bank_no_such_account")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankManager.OpenResult result = BankManager.openAccount(holder.server, bank,
                holder.getUUID(), holder.getGameProfile().getName(), 0L);
        if (!result.ok()) {
            holder.sendSystemMessage(Component.translatable(result.messageKey())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankAccount account = data.account(result.number());
        ServerPlayer banker = holder.server.getPlayerList().getPlayer(offer.banker());
        // The paperwork goes to the banker if they are still around, and to the customer otherwise, so
        // the tag is never quietly lost.
        ServerPlayer recipient = banker != null ? banker : holder;
        ItemStack tag = BankManager.accountTag(account, bank);
        if (!recipient.getInventory().add(tag)) {
            recipient.drop(tag, false);
        }
        if (banker != null) {
            banker.sendSystemMessage(Component.translatable("message.athens_coins.bank_account_opened",
                            holder.getGameProfile().getName(), result.number())
                    .withStyle(ChatFormatting.GREEN));
        }
        holder.sendSystemMessage(Component.translatable("message.athens_coins.bank_account_yours",
                        bank.name(), result.number())
                .withStyle(ChatFormatting.GREEN));
        com.athensmc.athenscoins.wallet.WalletManager.pushBalance(holder);
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

    /**
     * Hands the banker a stats board branded with this bank.
     *
     * <p>Branded exactly like an ATM, and for the same reason: a board shows figures, and "which
     * figures" has to be decided by whoever issued it rather than by whoever placed it. A board issued
     * here reports <em>this</em> bank - its reserve, its deposits, its own customers - so a market square
     * can carry one per bank and each one tells the truth about a different institution.</p>
     */
    private void issueHologram(ServerPlayer player, Bank bank) {
        ItemStack board = new ItemStack(
                com.athensmc.athenscoins.item.ModItems.STATS_HOLOGRAM_ITEM.get(),
                (int) Math.max(1L, Math.min(16L, value)));
        com.athensmc.athenscoins.block.StatsHologramBlockEntity.brand(board, bank);
        if (!player.getInventory().add(board)) {
            player.drop(board, false);
        }
        feedback(player, "message.athens_coins.bank_board_issued");
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
            ModNetwork.toPlayer(player, S2COpenTerminalPacket.of(player, bank, pos, BankAccess.canConfigure(player)));
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
