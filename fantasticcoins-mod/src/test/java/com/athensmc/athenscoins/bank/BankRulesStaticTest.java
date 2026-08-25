package com.athensmc.athenscoins.bank;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Standalone checks on the banking rules. BankRules has no Minecraft dependencies, so this runs
 * with plain javac:
 *
 *   javac -d out src/main/java/com/athensmc/athenscoins/bank/BankRules.java tools/BankRulesTest.java
 *   java -cp out BankRulesTest
 */
public class BankRulesStaticTest {

    static int checks = 0;
    static int failures = 0;

    static void eq(String label, Object actual, Object expected) {
        checks++;
        if (!String.valueOf(actual).equals(String.valueOf(expected))) {
            failures++;
            System.out.println("  FAIL " + label + ": got <" + actual + "> expected <" + expected + ">");
        }
    }

    static long at(String isoDate, int hour) {
        return LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).plusHours(hour)
                .toInstant().toEpochMilli();
    }

    public static void main(String[] args) {
        System.out.println("-- exchange rate band (official 0.15 = 15 cents, 15% margin) --");
        eq("ceiling", BankRules.rateCeiling(15L, 15), 17L);
        eq("floor", BankRules.rateFloor(15L, 15), 13L);
        eq("a bank at 0.17 is allowed", BankRules.rateAllowed(17L, 15L, 15), true);
        eq("a bank at 0.18 is not", BankRules.rateAllowed(18L, 15L, 15), false);
        eq("0.18 clamps down to 0.17", BankRules.clampRate(18L, 15L, 15), 17L);
        eq("0.01 clamps up to 0.13", BankRules.clampRate(1L, 15L, 15), 13L);
        eq("silver 1.35 ceiling", BankRules.rateCeiling(135L, 15), 155L);
        eq("gold 12.15 ceiling", BankRules.rateCeiling(1215L, 15), 1397L);
        eq("floor never reaches zero", BankRules.rateFloor(1L, 100), 1L);
        eq("no official rate means no band", BankRules.clampRate(500L, 0L, 15), 500L);

        System.out.println("-- wallet ceiling (the card limit) --");
        eq("room under the limit", BankRules.walletRoom(1_000_00L, 25_000_00L), 24_000_00L);
        eq("room at the limit", BankRules.walletRoom(25_000_00L, 25_000_00L), 0L);
        eq("room over the limit clamps to zero", BankRules.walletRoom(30_000_00L, 25_000_00L), 0L);
        eq("limit 0 means unlimited", BankRules.walletRoom(999_999L, 0L), Long.MAX_VALUE);
        eq("a withdrawal is cut to the room left",
                BankRules.fitIntoWallet(5_000_00L, 24_000_00L, 25_000_00L), 1_000_00L);
        eq("a withdrawal that fits is untouched",
                BankRules.fitIntoWallet(500_00L, 1_000_00L, 25_000_00L), 500_00L);
        eq("nothing fits into a full wallet",
                BankRules.fitIntoWallet(100L, 25_000_00L, 25_000_00L), 0L);
        eq("full wallet detected", BankRules.walletFull(25_000_00L, 25_000_00L), true);
        eq("unlimited wallet is never full", BankRules.walletFull(999_999_999L, 0L), false);

        // The ATM bug: handing coins in credited the account while buying them back charged the card,
        // so the machine appeared to swallow coins and pay nothing. A coin deposit has to reach the
        // card, because the card is where the next exchange takes it from.
        System.out.println("-- coin deposits land on the card first --");
        eq("an empty card takes the whole deposit",
                BankRules.splitWalletFirst(5_000_00L, 0L, 25_000_00L)[0], 5_000_00L);
        eq("and leaves nothing for the account",
                BankRules.splitWalletFirst(5_000_00L, 0L, 25_000_00L)[1], 0L);
        eq("an unlimited card takes everything",
                BankRules.splitWalletFirst(9_999_999L, 500_00L, 0L)[0], 9_999_999L);

        // A card near its ceiling splits rather than refusing, so the deposit is never lost.
        eq("a nearly full card takes what fits",
                BankRules.splitWalletFirst(5_000_00L, 24_000_00L, 25_000_00L)[0], 1_000_00L);
        eq("and the rest goes to the account",
                BankRules.splitWalletFirst(5_000_00L, 24_000_00L, 25_000_00L)[1], 4_000_00L);
        eq("a full card sends it all to the account",
                BankRules.splitWalletFirst(1_000_00L, 25_000_00L, 25_000_00L)[1], 1_000_00L);
        eq("and puts nothing on the card",
                BankRules.splitWalletFirst(1_000_00L, 25_000_00L, 25_000_00L)[0], 0L);

        // Whatever the split, it must add up: money that vanishes in the arithmetic is money the
        // player paid coins for and never received.
        long[][] cases = {
                {1L, 0L, 25_000_00L}, {5_000_00L, 24_999_99L, 25_000_00L},
                {123_45L, 1_000_00L, 0L}, {25_000_00L, 0L, 25_000_00L},
        };
        for (long[] example : cases) {
            long[] split = BankRules.splitWalletFirst(example[0], example[1], example[2]);
            eq("split of " + example[0] + " adds up", split[0] + split[1], example[0]);
        }
        eq("nothing to pay splits to nothing", BankRules.splitWalletFirst(0L, 0L, 0L)[0], 0L);
        eq("a negative payout splits to nothing", BankRules.splitWalletFirst(-5L, 0L, 0L)[1], 0L);

        System.out.println("-- commission, a fixed fee every N days --");
        long start = at("2026-03-02", 12);              // a Monday
        eq("nothing owed after 5 hours",
                BankRules.commissionsDue(start, start + 5 * 3600_000L, 1, 7), 0);
        eq("one day, one charge",
                BankRules.commissionsDue(start, start + BankRules.DAY_MILLIS, 1, 7), 1);
        eq("three days on a daily fee",
                BankRules.commissionsDue(start, start + 3 * BankRules.DAY_MILLIS, 1, 7), 3);
        eq("three days on a three-day fee",
                BankRules.commissionsDue(start, start + 3 * BankRules.DAY_MILLIS, 3, 7), 1);
        eq("six days on a weekly fee",
                BankRules.commissionsDue(start, start + 6 * BankRules.DAY_MILLIS, 7, 7), 0);
        eq("eight days on a weekly fee",
                BankRules.commissionsDue(start, start + 8 * BankRules.DAY_MILLIS, 7, 7), 1);
        eq("a 30 day outage is capped",
                BankRules.commissionsDue(start, start + 30 * BankRules.DAY_MILLIS, 1, 7), 7);
        eq("clock does not move backwards",
                BankRules.commissionsDue(start, start - BankRules.DAY_MILLIS, 1, 7), 0);
        eq("a period of zero never charges",
                BankRules.commissionsDue(start, start + 99 * BankRules.DAY_MILLIS, 0, 7), 0);

        // The clock must advance by exactly what was collected, so no time is lost or double billed.
        long afterTwo = BankRules.advanceChargeClock(start, 1, 2);
        eq("clock advances two days", afterTwo, start + 2 * BankRules.DAY_MILLIS);
        eq("and nothing more is owed at that instant",
                BankRules.commissionsDue(afterTwo, afterTwo, 1, 7), 0);
        // partial time survives: charge at 2.5 days, half a day should remain
        long now = start + (5 * BankRules.DAY_MILLIS) / 2;
        int due = BankRules.commissionsDue(start, now, 1, 7);
        eq("two charges owed at 2.5 days", due, 2);
        long moved = BankRules.advanceChargeClock(start, 1, due);
        eq("half a day is carried over, not dropped", now - moved, BankRules.DAY_MILLIS / 2);

        eq("fee total", BankRules.commissionTotal(250L, 3), 750L);
        eq("fee total saturates instead of overflowing",
                BankRules.commissionTotal(Long.MAX_VALUE, Integer.MAX_VALUE),
                com.athensmc.athenscoins.wallet.Money.MAX_CENTS);
        eq("no charges, no total", BankRules.commissionTotal(250L, 0), 0L);

        System.out.println("-- loans on business days --");
        // 2026-03-02 is a Monday. Five business days later is Monday the 9th.
        eq("5 business days from Monday", BankRules.addBusinessDays(at("2026-03-02", 12), 5),
                at("2026-03-09", 12));
        // From Friday the 6th, one business day is Monday the 9th, skipping the weekend.
        eq("1 business day from Friday skips the weekend",
                BankRules.addBusinessDays(at("2026-03-06", 12), 1), at("2026-03-09", 12));
        eq("3 business days from Thursday lands on Tuesday",
                BankRules.addBusinessDays(at("2026-03-05", 12), 3), at("2026-03-10", 12));
        eq("time of day is preserved",
                BankRules.addBusinessDays(at("2026-03-02", 9), 1), at("2026-03-03", 9));
        eq("zero days is a no-op",
                BankRules.addBusinessDays(at("2026-03-02", 12), 0), at("2026-03-02", 12));
        eq("Saturday is not a business day",
                BankRules.isBusinessDay(LocalDate.parse("2026-03-07")), false);
        eq("Sunday is not a business day",
                BankRules.isBusinessDay(LocalDate.parse("2026-03-08")), false);
        eq("Monday is", BankRules.isBusinessDay(LocalDate.parse("2026-03-09")), true);

        long dueFriday = at("2026-03-06", 12);
        eq("not overdue before the date",
                BankRules.overdueBusinessDays(dueFriday, at("2026-03-06", 10)), 0);
        eq("the weekend adds no overdue days",
                BankRules.overdueBusinessDays(dueFriday, at("2026-03-08", 12)), 0);
        eq("Monday is one overdue day",
                BankRules.overdueBusinessDays(dueFriday, at("2026-03-09", 12)), 1);
        eq("Wednesday is three",
                BankRules.overdueBusinessDays(dueFriday, at("2026-03-11", 12)), 3);

        eq("interest, 2.5% a day for 3 days on 1000.00",
                BankRules.overdueInterest(100_000L, 3, 250), 7_500L);
        eq("no interest when not overdue", BankRules.overdueInterest(100_000L, 0, 250), 0L);
        eq("no interest on nothing owed", BankRules.overdueInterest(0L, 5, 250), 0L);
        eq("tiny debts still accrue at least a cent",
                BankRules.overdueInterest(10L, 1, 250), 1L);

        System.out.println("-- a bank cannot lend what it does not hold --");
        eq("reserve is the binding limit", BankRules.maxLoan(500_00L, 5_000_00L, 0L), 500_00L);
        eq("policy is the binding limit", BankRules.maxLoan(50_000_00L, 5_000_00L, 0L), 5_000_00L);
        eq("existing debt reduces headroom",
                BankRules.maxLoan(50_000_00L, 5_000_00L, 4_000_00L), 1_000_00L);
        eq("an empty reserve lends nothing", BankRules.maxLoan(0L, 5_000_00L, 0L), 0L);
        eq("debt beyond policy lends nothing",
                BankRules.maxLoan(50_000_00L, 5_000_00L, 9_000_00L), 0L);

        System.out.println("-- account numbers are five digits and unique --");
        eq("10000 valid", BankRules.isValidAccountNumber(10_000), true);
        eq("99999 valid", BankRules.isValidAccountNumber(99_999), true);
        eq("9999 invalid", BankRules.isValidAccountNumber(9_999), false);
        eq("100000 invalid", BankRules.isValidAccountNumber(100_000), false);

        Set<Integer> used = new HashSet<>();
        Random random = new Random(1234L);
        boolean allValid = true;
        boolean allUnique = true;
        for (int i = 0; i < 5_000; i++) {
            int number = BankRules.allocateAccountNumber(used::contains, random);
            if (!BankRules.isValidAccountNumber(number)) {
                allValid = false;
            }
            if (!used.add(number)) {
                allUnique = false;
            }
        }
        eq("5000 allocations all in range", allValid, true);
        eq("5000 allocations all distinct", allUnique, true);
        eq("and the set really holds 5000", used.size(), 5_000);

        // exhaustion must be reported, not looped forever
        Set<Integer> everything = new HashSet<>();
        for (int n = BankRules.ACCOUNT_MIN; n <= BankRules.ACCOUNT_MAX; n++) {
            everything.add(n);
        }
        eq("a full range returns -1",
                BankRules.allocateAccountNumber(everything::contains, random), -1);

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failure(s)");
        if (failures > 0) {
            System.exit(1);
        }
        System.out.println("BANK RULES OK");
    }
}
