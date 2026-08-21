package com.athensmc.athenscoins.bank;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Every rule the banking system runs on, with no Minecraft in sight.
 *
 * <p>Deliberately free of game types so it can be exercised directly: the interesting mistakes in
 * a bank are arithmetic, not rendering. Amounts are always cents.</p>
 *
 * <h2>Time</h2>
 * Periods are measured in real 24 hour blocks of wall-clock time, not Minecraft days, and the
 * calendar work is done in UTC so a server moving timezone cannot shift a due date. Business days
 * skip Saturday and Sunday.
 */
public final class BankRules {

    public static final long DAY_MILLIS = 86_400_000L;

    public static final int ACCOUNT_MIN = 10_000;
    public static final int ACCOUNT_MAX = 99_999;

    private BankRules() {
    }

    // ================================================================== exchange rates

    /**
     * Highest rate a bank may offer for a coin, given the server's official rate.
     *
     * <p>The official rate is the reference every bank has to compete around: they may undercut it
     * or beat it, but only inside a margin, so no single bank can run away with the market. With
     * the default 15% margin an official 0.15 allows up to 0.17.</p>
     */
    public static long rateCeiling(long officialCents, int marginPercent) {
        if (officialCents <= 0L) {
            return 0L;
        }
        int margin = Math.max(0, Math.min(100, marginPercent));
        return officialCents + officialCents * margin / 100L;
    }

    /** Lowest rate a bank may offer, the mirror of {@link #rateCeiling}. */
    public static long rateFloor(long officialCents, int marginPercent) {
        if (officialCents <= 0L) {
            return 0L;
        }
        int margin = Math.max(0, Math.min(100, marginPercent));
        long floor = officialCents - officialCents * margin / 100L;
        return Math.max(1L, floor);
    }

    /** Forces a bank's proposed rate into the permitted band. */
    public static long clampRate(long proposedCents, long officialCents, int marginPercent) {
        if (officialCents <= 0L) {
            return Math.max(1L, proposedCents);
        }
        long low = rateFloor(officialCents, marginPercent);
        long high = rateCeiling(officialCents, marginPercent);
        return Math.max(low, Math.min(high, proposedCents));
    }

    /** True when a rate is inside the band, used to flag a bank's settings as out of policy. */
    public static boolean rateAllowed(long proposedCents, long officialCents, int marginPercent) {
        return proposedCents == clampRate(proposedCents, officialCents, marginPercent);
    }

    // ================================================================== wallet limit

    /**
     * How much more electronic cash the wallet can hold.
     *
     * <p>The wallet works like a card with a ceiling the bank sets: the account can hold any
     * amount, but only this much may be carried as spendable electronic cash. A limit of zero
     * means unlimited.</p>
     */
    public static long walletRoom(long walletCents, long limitCents) {
        if (limitCents <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, limitCents - walletCents);
    }

    /** Largest part of {@code desired} that may be moved into the wallet right now. */
    public static long fitIntoWallet(long desiredCents, long walletCents, long limitCents) {
        if (desiredCents <= 0L) {
            return 0L;
        }
        return Math.min(desiredCents, walletRoom(walletCents, limitCents));
    }

    /** True when the wallet is already at its ceiling. */
    public static boolean walletFull(long walletCents, long limitCents) {
        return walletRoom(walletCents, limitCents) <= 0L;
    }

    // ================================================================== commission

    /**
     * How many commission charges are owed.
     *
     * <p>The fee is a fixed sum, not a percentage, taken every {@code periodDays}. Counting the
     * elapsed periods rather than firing on a timer means a server that was switched off for a
     * week still collects the fees it missed. {@code maxCatchUp} stops a long outage from
     * presenting someone with a ruinous bill on login.</p>
     */
    public static int commissionsDue(long lastChargeAt, long now, int periodDays, int maxCatchUp) {
        if (periodDays <= 0 || now <= lastChargeAt) {
            return 0;
        }
        long period = periodDays * DAY_MILLIS;
        long elapsed = now - lastChargeAt;
        long due = elapsed / period;
        if (due <= 0L) {
            return 0;
        }
        int cap = Math.max(1, maxCatchUp);
        return (int) Math.min(due, cap);
    }

    /** Where {@code lastChargeAt} moves to after collecting {@code charges} periods. */
    public static long advanceChargeClock(long lastChargeAt, int periodDays, int charges) {
        if (periodDays <= 0 || charges <= 0) {
            return lastChargeAt;
        }
        return lastChargeAt + (long) charges * periodDays * DAY_MILLIS;
    }

    /** Total owed for {@code charges} periods at a fixed fee. */
    public static long commissionTotal(long feeCents, int charges) {
        if (feeCents <= 0L || charges <= 0) {
            return 0L;
        }
        return com.athensmc.athenscoins.wallet.Money.multiply(feeCents, charges);
    }

    // ================================================================== loans

    /**
     * Due date {@code days} business days after {@code from}, skipping weekends.
     *
     * <p>Keeps the time of day from {@code from} so a loan taken at noon is due at noon.</p>
     */
    public static long addBusinessDays(long from, int days) {
        if (days <= 0) {
            return from;
        }
        LocalDate date = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate();
        long timeOfDay = from - date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        int added = 0;
        while (added < days) {
            date = date.plusDays(1L);
            if (isBusinessDay(date)) {
                added++;
            }
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + timeOfDay;
    }

    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * Business days a loan is past due, zero if it is not.
     *
     * <p>Interest only builds on working days, so a borrower is not punished for the weekend.</p>
     */
    public static int overdueBusinessDays(long dueAt, long now) {
        if (now <= dueAt) {
            return 0;
        }
        LocalDate due = Instant.ofEpochMilli(dueAt).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate();
        int count = 0;
        LocalDate cursor = due;
        while (cursor.isBefore(today)) {
            cursor = cursor.plusDays(1L);
            if (isBusinessDay(cursor)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Interest to add for {@code days} overdue at {@code dailyBasisPoints}.
     *
     * <p>Basis points, so a rate under 1% a day can still be expressed: 250 is 2.5% of the
     * outstanding amount per overdue business day. Simple interest, not compound, and it is the
     * caller's job to add it to the balance and record when it was applied.</p>
     */
    public static long overdueInterest(long owedCents, int days, int dailyBasisPoints) {
        if (owedCents <= 0L || days <= 0 || dailyBasisPoints <= 0) {
            return 0L;
        }
        long perDay = com.athensmc.athenscoins.wallet.Money.multiply(owedCents, dailyBasisPoints) / 10_000L;
        if (perDay <= 0L) {
            perDay = 1L;
        }
        return com.athensmc.athenscoins.wallet.Money.multiply(perDay, days);
    }

    /**
     * Largest loan a bank can actually hand out.
     *
     * <p>Bounded by its own reserve as well as its policy: a bank cannot lend money it does not
     * have, which is what makes commissions and central bank injections matter.</p>
     */
    public static long maxLoan(long reserveCents, long policyMaxCents, long alreadyOwedCents) {
        long headroom = Math.max(0L, policyMaxCents - Math.max(0L, alreadyOwedCents));
        return Math.max(0L, Math.min(reserveCents, headroom));
    }

    // ================================================================== account numbers

    public static boolean isValidAccountNumber(int number) {
        return number >= ACCOUNT_MIN && number <= ACCOUNT_MAX;
    }

    /**
     * Picks a free five digit account number.
     *
     * <p>Walks upwards from a random start rather than retrying at random, so it still terminates
     * quickly when most numbers are taken instead of thrashing. Returns -1 only when every number
     * in the range is in use.</p>
     */
    public static int allocateAccountNumber(java.util.function.IntPredicate taken,
                                           java.util.Random random) {
        int span = ACCOUNT_MAX - ACCOUNT_MIN + 1;
        int start = ACCOUNT_MIN + random.nextInt(span);
        for (int offset = 0; offset < span; offset++) {
            int candidate = ACCOUNT_MIN + ((start - ACCOUNT_MIN + offset) % span);
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        return -1;
    }
}
