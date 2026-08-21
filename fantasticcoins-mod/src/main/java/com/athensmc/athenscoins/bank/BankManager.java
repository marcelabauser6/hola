package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletData;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Bank operations: opening and closing accounts, moving money between the account and the wallet,
 * commissions and loans.
 *
 * <p>The rules themselves live in {@link BankRules}, which is tested on its own. This layer is the
 * plumbing that applies them to the world.</p>
 */
public final class BankManager {

    /** NBT keys on the account name tag handed to the player. */
    public static final String TAG_ACCOUNT = "FcAccount";
    public static final String TAG_OWNER = "FcOwner";
    public static final String TAG_BANK = "FcBank";

    private static final Random RANDOM = new Random();

    private BankManager() {
    }

    public static BankData data(MinecraftServer server) {
        return BankData.get(server);
    }

    // ------------------------------------------------------------------ queries

    @Nullable
    public static BankAccount accountOf(ServerPlayer player) {
        return data(player.server).accountOf(player.getUUID());
    }

    @Nullable
    public static Bank bankOf(ServerPlayer player) {
        BankAccount account = accountOf(player);
        return account == null ? null : data(player.server).bank(account.bankId());
    }

    public static boolean hasAccount(ServerPlayer player) {
        return data(player.server).hasAccount(player.getUUID());
    }

    /**
     * Ceiling on the player's electronic cash.
     *
     * <p>Without an account there is no electronic money at all, which is why this returns -1 to
     * mean "not allowed" rather than zero, which would read as "unlimited".</p>
     */
    public static long walletCeiling(ServerPlayer player) {
        Bank bank = bankOf(player);
        return bank == null ? -1L : bank.walletLimit();
    }

    // ------------------------------------------------------------------ opening and closing

    /** Result of trying to open an account. */
    public record OpenResult(boolean ok, String messageKey, int number) {
        static OpenResult fail(String key) {
            return new OpenResult(false, key, 0);
        }
    }

    /**
     * Opens an account and hands the holder's name tag back to the banker.
     *
     * @param carriedOver money arriving from a closed account elsewhere, via a bank card
     */
    public static OpenResult openAccount(MinecraftServer server, Bank bank, UUID owner,
                                         String ownerName, long carriedOver) {
        BankData data = data(server);
        if (data.hasAccount(owner)) {
            return OpenResult.fail("message.athens_coins.bank_already_has_account");
        }
        int number = BankRules.allocateAccountNumber(data::numberTaken, RANDOM);
        if (number < 0) {
            return OpenResult.fail("message.athens_coins.bank_numbers_exhausted");
        }
        long now = System.currentTimeMillis();
        BankAccount account = new BankAccount(number, owner, ownerName, bank.id(), now);
        account.record(LedgerEntry.Kind.OPENED, 0L, bank.name(), now);
        if (carriedOver > 0L) {
            account.credit(carriedOver, LedgerEntry.Kind.WALLET_IN, "traspaso", now);
        }
        data.register(account);
        return new OpenResult(true, "message.athens_coins.bank_account_opened", number);
    }

    /**
     * Closes an account, returning everything the holder had so it can be put on a card.
     *
     * <p>The wallet is emptied too: electronic cash is the bank's money, so it cannot outlive the
     * account.</p>
     */
    public static long closeAccount(MinecraftServer server, BankAccount account) {
        long now = System.currentTimeMillis();
        long total = account.balance();

        // Sweep the holder's wallet into the payout.
        WalletData wallets = WalletData.get(server);
        Wallet wallet = wallets.account(account.owner());
        total += wallet.balance();
        wallet.set(0L);
        wallets.setDirty();

        account.record(LedgerEntry.Kind.CLOSED, -total, "cierre", now);
        data(server).unregister(account);
        return Math.max(0L, total - account.commissionDebt());
    }

    /** The name tag a holder carries as proof of their account. */
    public static ItemStack accountTag(BankAccount account, Bank bank) {
        ItemStack stack = new ItemStack(Items.NAME_TAG);
        stack.setHoverName(Component.translatable("item.athens_coins.account_tag",
                account.number()));
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_ACCOUNT, account.number());
        tag.putString(TAG_OWNER, account.ownerName() == null ? "" : account.ownerName());
        tag.putString(TAG_BANK, bank.name());
        return stack;
    }

    // ------------------------------------------------------------------ account <-> wallet

    /**
     * Moves money out of the account onto the wallet card, capped by the bank's limit.
     *
     * @return how much actually moved
     */
    public static long toWallet(ServerPlayer player, long requested) {
        BankAccount account = accountOf(player);
        Bank bank = bankOf(player);
        if (account == null || bank == null) {
            return 0L;
        }
        Wallet wallet = WalletManager.accountOf(player);
        long room = BankRules.fitIntoWallet(requested, wallet.balance(), bank.walletLimit());
        long amount = Math.min(room, account.balance());
        if (amount <= 0L) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (!account.debit(amount, LedgerEntry.Kind.WALLET_OUT, "a la cartera", now)) {
            return 0L;
        }
        wallet.add(amount);
        WalletManager.markDirty(player);
        data(player.server).setDirty();
        WalletManager.pushBalance(player);
        return amount;
    }

    /** Moves money off the wallet card back into the account. */
    public static long toAccount(ServerPlayer player, long requested) {
        BankAccount account = accountOf(player);
        if (account == null) {
            return 0L;
        }
        Wallet wallet = WalletManager.accountOf(player);
        long amount = Math.min(requested, wallet.balance());
        if (amount <= 0L) {
            return 0L;
        }
        if (!wallet.withdraw(amount)) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        account.credit(amount, LedgerEntry.Kind.WALLET_IN, "desde la cartera", now);
        WalletManager.markDirty(player);
        data(player.server).setDirty();
        WalletManager.pushBalance(player);
        return amount;
    }

    /**
     * Credits money arriving from outside, wallet first then the account.
     *
     * <p>Used when coins are exchanged at an ATM: fill the card up to its ceiling and park the rest
     * in the account rather than refusing the deposit.</p>
     */
    public static long credit(ServerPlayer player, long amount, LedgerEntry.Kind kind, String note) {
        if (amount <= 0L) {
            return 0L;
        }
        Bank bank = bankOf(player);
        if (bank == null) {
            return 0L;
        }
        Wallet wallet = WalletManager.accountOf(player);
        long toCard = BankRules.fitIntoWallet(amount, wallet.balance(), bank.walletLimit());
        wallet.add(toCard);
        long remainder = amount - toCard;
        BankAccount account = accountOf(player);
        if (remainder > 0L && account != null) {
            account.credit(remainder, kind, note, System.currentTimeMillis());
        }
        WalletManager.markDirty(player);
        data(player.server).setDirty();
        WalletManager.pushBalance(player);
        return amount;
    }

    // ------------------------------------------------------------------ commission

    /**
     * Collects any commission periods that have elapsed for one account.
     *
     * @return how much was taken
     */
    public static long collectCommission(MinecraftServer server, BankAccount account, long now) {
        Bank bank = data(server).bank(account.bankId());
        if (bank == null || bank.commissionFee() <= 0L) {
            return 0L;
        }
        int due = BankRules.commissionsDue(account.lastChargeAt(), now,
                bank.commissionPeriodDays(), CurrencyConfig.get().commissionMaxCatchUp);
        if (due <= 0) {
            return 0L;
        }
        long owed = BankRules.commissionTotal(bank.commissionFee(), due);
        long taken = account.debitPartial(owed, LedgerEntry.Kind.COMMISSION,
                due + "x " + Money.format(bank.commissionFee(), CurrencyConfig.get().currencySymbol),
                now);
        if (taken < owed) {
            account.addCommissionDebt(owed - taken);
        }
        // The fee is the bank's income; this is what funds its lending.
        bank.addReserve(taken);
        account.setLastChargeAt(BankRules.advanceChargeClock(
                account.lastChargeAt(), bank.commissionPeriodDays(), due));
        data(server).setDirty();
        return taken;
    }

    /** Applies overdue interest to one account's loan. */
    public static long accrueInterest(MinecraftServer server, BankAccount account, long now) {
        Loan loan = account.loan();
        if (loan == null || loan.settled() || !loan.overdue(now)) {
            return 0L;
        }
        Bank bank = data(server).bank(account.bankId());
        if (bank == null) {
            return 0L;
        }
        int days = BankRules.overdueBusinessDays(loan.lastInterestAt(), now);
        if (days <= 0) {
            return 0L;
        }
        long interest = BankRules.overdueInterest(loan.owed(), days, bank.loanInterestBasisPoints());
        if (interest <= 0L) {
            return 0L;
        }
        loan.addInterest(interest);
        loan.setLastInterestAt(BankRules.addBusinessDays(loan.lastInterestAt(), days));
        account.record(LedgerEntry.Kind.LOAN_INTEREST, 0L, days + " dias de mora", now);
        data(server).setDirty();
        return interest;
    }

    // ------------------------------------------------------------------ loans

    public record LoanResult(boolean ok, String messageKey, long amount) {
        static LoanResult fail(String key) {
            return new LoanResult(false, key, 0L);
        }
    }

    /** Grants a loan from the bank's reserve. */
    public static LoanResult grantLoan(MinecraftServer server, BankAccount account, long requested) {
        Bank bank = data(server).bank(account.bankId());
        if (bank == null || !bank.loansEnabled()) {
            return LoanResult.fail("message.athens_coins.bank_loans_disabled");
        }
        if (account.loan() != null && !account.loan().settled()) {
            return LoanResult.fail("message.athens_coins.bank_loan_active");
        }
        long allowed = BankRules.maxLoan(bank.reserve(), bank.loanMaxAmount(), 0L);
        long amount = Math.min(requested, allowed);
        if (amount <= 0L) {
            return LoanResult.fail("message.athens_coins.bank_reserve_empty");
        }
        if (!bank.drawReserve(amount)) {
            return LoanResult.fail("message.athens_coins.bank_reserve_empty");
        }
        long now = System.currentTimeMillis();
        Loan loan = new Loan(amount, now, BankRules.addBusinessDays(now, bank.loanDays()));
        account.setLoan(loan);
        account.credit(amount, LedgerEntry.Kind.LOAN_GRANTED,
                bank.loanDays() + " dias habiles", now);
        data(server).setDirty();
        return new LoanResult(true, "message.athens_coins.bank_loan_granted", amount);
    }

    /** Repays from the account balance, returning how much was applied. */
    public static long repayLoan(MinecraftServer server, BankAccount account, long requested) {
        Loan loan = account.loan();
        if (loan == null || loan.settled()) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long amount = Math.min(Math.min(requested, loan.owed()), account.balance());
        if (amount <= 0L) {
            return 0L;
        }
        if (!account.debit(amount, LedgerEntry.Kind.LOAN_REPAID, "pago de prestamo", now)) {
            return 0L;
        }
        loan.repay(amount);
        Bank bank = data(server).bank(account.bankId());
        if (bank != null) {
            bank.addReserve(amount);
        }
        if (loan.settled()) {
            account.setLoan(null);
        }
        data(server).setDirty();
        return amount;
    }

    // ------------------------------------------------------------------ central bank

    /** Puts cash into a bank's reserve out of nothing, which is what a central bank does. */
    public static void inject(MinecraftServer server, Bank bank, long amount) {
        if (amount <= 0L) {
            return;
        }
        bank.addReserve(amount);
        data(server).addIssued(amount);
        data(server).setDirty();
    }

    /** Pulls every bank's rates back inside the band after an official rate moves. */
    public static void realignAllRates(MinecraftServer server) {
        List<Bank> banks = data(server).banks();
        for (Bank bank : banks) {
            bank.realignRates();
        }
        data(server).setDirty();
    }

    // ------------------------------------------------------------------ terminal seating

    /** Called when a terminal block is placed: adopt an orphaned bank or create a new one. */
    public static Bank seatTerminal(MinecraftServer server, BlockPos pos, String defaultName) {
        BankData data = data(server);
        Bank existing = data.bankAt(pos);
        if (existing != null) {
            return existing;
        }
        Bank adopted = data.adoptSeat(pos);
        if (adopted != null) {
            return adopted;
        }
        return data.createBank(defaultName + " " + (data.bankCount() + 1), pos);
    }
}
