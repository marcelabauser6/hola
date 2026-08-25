package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** Pure-JVM persistence and transaction regression checks; it never starts Minecraft. */
public final class FinancialPersistenceStaticTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        long now = 1_700_000_000_000L;

        BankAccount account = new BankAccount(12345, owner, "holder", bankId, now);
        String correlation = LedgerEntry.correlation();
        check(account.credit(500L, LedgerEntry.Kind.SHOP_SALE, "sale", now,
                correlation, owner.toString(), "test"), "principal credit");
        check(account.debit(125L, LedgerEntry.Kind.SHOP_PURCHASE, "purchase", now + 1,
                correlation, owner.toString(), "test"), "principal debit");
        check(account.creditWallet(200L) && account.debitWallet(50L), "wallet mutation");
        LedgerEntry last = account.ledger().get(1);
        check(last.balanceBefore() == 500L && last.balanceAfter() == 375L, "ledger before/after");
        check(last.correlationId().equals(correlation) && last.source().equals("test"), "ledger context");
        BankAccount roundTrip = BankAccount.load(account.save());
        check(roundTrip.balance() == 375L && roundTrip.walletBalance() == 150L
                && roundTrip.ledger().size() == 2, "account + wallet atomic NBT round trip");

        CompoundTag legacyLedger = new CompoundTag();
        legacyLedger.putLong("at", now); legacyLedger.putInt("kind", LedgerEntry.Kind.WALLET_IN.ordinal());
        legacyLedger.putLong("amount", 25L); legacyLedger.putLong("after", 100L);
        LedgerEntry migratedLedger = LedgerEntry.load(legacyLedger);
        check(migratedLedger.balanceBefore() == 75L && migratedLedger.balanceAfter() == 100L,
                "old ledger reconstructs before balance");

        WalletData wallets = new WalletData();
        wallets.account(owner); // zero wallet must still be persisted as initialized storage
        CompoundTag walletTag = wallets.save(new CompoundTag());
        check(walletTag.getList("Wallets", Tag.TAG_COMPOUND).size() == 1,
                "zero wallet is persisted");
        wallets.markInitialized(owner);
        check(wallets.claimStartingBalance(owner) == 0L, "initialization cannot repeat");
        check(wallets.quarantine(owner, Money.MAX_CENTS + 7L), "large legacy value is quarantined");
        WalletData walletRoundTrip = WalletData.load(wallets.save(new CompoundTag()));
        check(walletRoundTrip.quarantined(owner) == Money.MAX_CENTS + 7L, "quarantine round trip");

        CompoundTag v2 = new CompoundTag(); v2.putInt("DataVersion", 2);
        ListTag oldWallets = new ListTag(); CompoundTag oldWallet = new CompoundTag();
        oldWallet.putUUID("Owner", UUID.randomUUID()); oldWallet.putLong("cash", 123L); oldWallets.add(oldWallet);
        v2.put("Wallets", oldWallets);
        WalletData pendingMigration = WalletData.load(v2);
        check(pendingMigration.migrationPending(), "v2 wallet migration remains pending");
        check(pendingMigration.save(new CompoundTag()).getInt("DataVersion") == 2,
                "pending migration does not falsely persist current version");

        BankData data = new BankData();
        BankData.CardRecord card = data.issueCard(owner, 900L, 12345, "Old Bank");
        check(!card.redeemed(), "new card is unredeemed");
        check(data.redeemCard(card.token(), owner, 900L, now), "first redemption succeeds");
        check(!data.redeemCard(card.token(), owner, 900L, now + 1), "replay is rejected");
        BankData cardRoundTrip = BankData.load(data.save(new CompoundTag()));
        check(cardRoundTrip.card(card.token()) != null && cardRoundTrip.card(card.token()).redeemed(),
                "redemption persists");

        CompoundTag orphanRoot = new CompoundTag();
        ListTag orphanList = new ListTag(); CompoundTag orphan = account.save();
        orphan.putLong("balance", Long.MAX_VALUE); orphanList.add(orphan); orphanRoot.put("Accounts", orphanList);
        BankData orphanData = BankData.load(orphanRoot);
        check(orphanData.orphanAccounts().size() == 1, "missing-bank account is retained");
        check(orphanData.orphanAccounts().get(0).balance() == Long.MAX_VALUE,
                "out-of-policy legacy balance is not truncated");

        verifyLoans(now);
        verifyBankPolicyPersistence();
        verifyBusinessDayCountdown();

        System.out.println("Financial persistence verified: " + checks + " checks.");
    }

    /**
     * Loan behaviour, which had no coverage at all: no test constructed a {@link Loan}.
     *
     * <p>That absence is why the penalty could compound against its own documentation, why the network
     * form could quietly drop the interest anchor, and why an abandoned debt could grow without bound.
     * The checks below pin all three down.</p>
     */
    private static void verifyLoans(long now) {
        long due = now + 5L * 86_400_000L;
        Loan loan = new Loan(100_000L, now, due);
        check(loan.principal() == 100_000L && loan.owed() == 100_000L, "new loan owes its principal");
        check(loan.lastInterestAt() == due, "interest anchor starts at the due date");
        check(loan.interestAccrued() == 0L, "new loan has charged no interest");
        check(!loan.settled() && !loan.overdue(now), "a fresh loan is neither settled nor overdue");
        check(loan.overdue(due + 1L), "a loan past its due date is overdue");

        check(loan.repay(30_000L) == 30_000L && loan.owed() == 70_000L, "partial repayment");
        check(loan.repay(999_999L) == 70_000L && loan.settled(), "repayment cannot exceed what is owed");
        check(!loan.overdue(due + 1L), "a settled loan is never overdue");

        // ---- the interest ceiling
        Loan capped = new Loan(100_000L, now, due);
        check(capped.interestHeadroom(100) == 100_000L, "100% ceiling allows one principal of interest");
        check(capped.interestHeadroom(0) == Money.MAX_CENTS, "a zero ceiling means uncapped");
        check(capped.addInterest(60_000L), "interest below the ceiling is accepted");
        check(capped.owed() == 160_000L && capped.interestAccrued() == 60_000L,
                "interest raises the balance and the running total together");
        check(capped.interestHeadroom(100) == 40_000L, "headroom shrinks by what was charged");
        check(!capped.addInterest(0L) && !capped.addInterest(-5L), "non-positive interest is refused");

        // ---- simple, not compound
        // Three charges at 2.5% a day must total exactly 7.5% of the principal. Computed against the
        // running balance instead, the third charge alone would already exceed its share.
        Loan simple = new Loan(100_000L, now, due);
        for (int day = 0; day < 3; day++) {
            simple.addInterest(BankRules.overdueInterest(simple.principal(), 1, 250));
        }
        check(simple.interestAccrued() == 7_500L,
                "three days of simple interest is 7.5% of principal, was " + simple.interestAccrued());
        Loan compounding = new Loan(100_000L, now, due);
        for (int day = 0; day < 3; day++) {
            compounding.addInterest(BankRules.overdueInterest(compounding.owed(), 1, 250));
        }
        check(compounding.interestAccrued() > simple.interestAccrued(),
                "charging against the running balance would compound, so the two must differ");

        // ---- NBT round trip, including both fields the old format lacked
        Loan saved = new Loan(50_000L, now, due);
        saved.addInterest(1_234L);
        saved.setLastInterestAt(due + 86_400_000L);
        Loan loadedLoan = Loan.load(saved.save());
        check(loadedLoan.principal() == 50_000L && loadedLoan.owed() == 51_234L,
                "loan NBT round trip keeps principal and balance");
        check(loadedLoan.lastInterestAt() == due + 86_400_000L, "loan NBT keeps the interest anchor");
        check(loadedLoan.interestAccrued() == 1_234L, "loan NBT keeps the interest total");

        // ---- network round trip: lastInterestAt used to be dropped and rebuilt as dueAt, telling a
        // viewing client no interest had been charged however long the loan had been overdue.
        net.minecraft.network.FriendlyByteBuf buffer =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        saved.write(buffer);
        Loan wired = Loan.read(buffer);
        check(wired.lastInterestAt() == saved.lastInterestAt(),
                "loan network form preserves the interest anchor");
        check(wired.interestAccrued() == saved.interestAccrued(),
                "loan network form preserves the interest total");
        check(wired.owed() == saved.owed() && wired.principal() == saved.principal(),
                "loan network form preserves the amounts");

        // ---- a loan written before the ceiling existed carries no total; reconstruct it
        CompoundTag legacyLoan = new CompoundTag();
        legacyLoan.putLong("principal", 10_000L);
        legacyLoan.putLong("owed", 12_500L);
        legacyLoan.putLong("takenAt", now);
        legacyLoan.putLong("dueAt", due);
        Loan migrated = Loan.load(legacyLoan);
        check(migrated.interestAccrued() == 2_500L,
                "legacy loan reconstructs its interest total from owed minus principal");
        check(migrated.lastInterestAt() == due, "legacy loan defaults its anchor to the due date");

        CompoundTag corruptLoan = new CompoundTag();
        corruptLoan.putLong("principal", -5_000L);
        corruptLoan.putLong("owed", -1L);
        Loan clamped = Loan.load(corruptLoan);
        check(clamped.principal() == 0L && clamped.owed() == 0L,
                "a corrupt loan clamps to zero instead of poisoning later comparisons");
    }

    /**
     * A bank's saved policy, which had no round-trip coverage either.
     *
     * <p>Reading the reserve raw let a negative value disable lending permanently and silently, and
     * {@code getBoolean} on an absent key turned lending off for every bank saved before the flag
     * existed.</p>
     */
    private static void verifyBankPolicyPersistence() {
        Bank bank = new Bank(UUID.randomUUID(), "Banco de Prueba", null);
        bank.addReserve(250_000L);
        bank.setLoanMaxAmount(400_000L);
        bank.setLoanDays(9);
        bank.setLoanInterestBasisPoints(175);
        Bank reloaded = Bank.load(bank.save());
        check(reloaded.reserve() == 250_000L, "bank reserve round trip");
        check(reloaded.loanMaxAmount() == 400_000L && reloaded.loanDays() == 9
                && reloaded.loanInterestBasisPoints() == 175, "bank loan policy round trip");
        check(reloaded.loansEnabled(), "lending stays enabled across a save");

        CompoundTag corrupt = bank.save();
        corrupt.putLong("reserve", -999L);
        corrupt.putInt("loanDays", 5000);
        corrupt.putInt("loanBp", -20);
        Bank repaired = Bank.load(corrupt);
        check(repaired.reserve() == 0L, "a negative saved reserve is clamped, not carried through");
        check(repaired.loanDays() == 60, "an out-of-range loan term is clamped to the maximum");
        check(repaired.loanInterestBasisPoints() == 0, "a negative penalty rate is clamped to zero");

        CompoundTag preFlag = bank.save();
        preFlag.remove("loans");
        check(Bank.load(preFlag).loansEnabled(),
                "a bank saved before the lending flag existed keeps lending enabled");
    }

    /** The countdown a borrower reads, which used to be measured in the wrong unit entirely. */
    private static void verifyBusinessDayCountdown() {
        // Wednesday 2023-11-15 12:00 UTC.
        long wednesday = 1_700_049_600_000L;
        long friday = wednesday + 2L * 86_400_000L;
        check(BankRules.businessDaysUntil(friday, wednesday) == 2, "two business days to Friday");
        check(BankRules.businessDaysUntil(wednesday, wednesday) == 0, "nothing left once due");
        check(BankRules.businessDaysUntil(wednesday, friday) == 0, "an overdue loan counts down to zero");
        // Friday to Monday is one business day, not three: the weekend does not count.
        long monday = friday + 3L * 86_400_000L;
        check(BankRules.businessDaysUntil(monday, friday) == 1,
                "the weekend adds no business days, got " + BankRules.businessDaysUntil(monday, friday));
        check(BankRules.overdueBusinessDays(friday, monday) == 1,
                "the countdown and the overdue count agree on the weekend");
    }
}
