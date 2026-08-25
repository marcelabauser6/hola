package com.athensmc.fsshopkeepers.trade;

import com.athensmc.fsshopkeepers.money.Money;

/**
 * The single decision about whether a trade may go ahead.
 *
 * <p>Pure arithmetic with no Minecraft in it, so it can be exercised exhaustively by a test. It exists because free
 * purchases got out twice from the same cause: the rule about paying lived inside one code path, and a second path -
 * bartering - simply did not have it. A rule written once and consulted by every path cannot be forgotten by one of
 * them.</p>
 *
 * <p>Every unclear case is a refusal. A price that does not fit, a balance that cannot be read, a row that asks for nothing
 * at all: all of them answer no. That direction is deliberate. Refusing a trade that should have worked is an annoyance the
 * player reports; allowing one that should not have is an economy quietly filling with goods that were never paid for.</p>
 */
public final class TradeGate {

    /** Why a trade was refused, or {@link #ALLOWED} when it was not. */
    public enum Verdict {
        /** The trade may proceed. */
        ALLOWED,
        /** The row asks for no payment of any kind, which is a mis-configured shop rather than a gift. */
        NOTHING_ASKED,
        /** Multiplying the price by the quantity overflows. */
        PRICE_TOO_LARGE,
        /** The shop wants money and there is no money system to take it with. */
        NO_ECONOMY,
        /** The buyer cannot afford it. */
        TOO_EXPENSIVE;

        public boolean allowed() {
            return this == ALLOWED;
        }
    }

    private TradeGate() {
    }

    /**
     * Decides whether a purchase may proceed.
     *
     * @param unitPriceCents  price of one trade, zero when the row is paid for with items only.
     * @param times           how many trades at once, at least one.
     * @param hasItemCost     whether the row also asks for items.
     * @param economyPresent  whether a money system is connected.
     * @param spendableCents  everything the buyer can spend, already floored at zero.
     * @return the verdict, and {@link Verdict#ALLOWED} only when nothing is wrong.
     */
    public static Verdict check(long unitPriceCents, int times, boolean hasItemCost, boolean economyPresent,
            long spendableCents) {
        if (times < 1) {
            return Verdict.PRICE_TOO_LARGE;
        }
        if (unitPriceCents < 0L) {
            return Verdict.PRICE_TOO_LARGE;
        }
        if (unitPriceCents == 0L) {
            // No money asked. That is fine only when the row asks for items instead; a row that asks for neither is a
            // shop giving things away by accident, so it is refused rather than honoured.
            return hasItemCost ? Verdict.ALLOWED : Verdict.NOTHING_ASKED;
        }
        if (!economyPresent) {
            return Verdict.NO_ECONOMY;
        }
        long total = totalPrice(unitPriceCents, times);
        if (total < 0L) {
            return Verdict.PRICE_TOO_LARGE;
        }
        if (spendableCents < total) {
            return Verdict.TOO_EXPENSIVE;
        }
        return Verdict.ALLOWED;
    }

    /**
     * The price of {@code times} trades, or {@code -1} when that cannot be represented.
     *
     * <p>Negative rather than saturating, because a total that had to be capped is not a price anyone agreed to. Saturating
     * would charge a different number from the one on the label.</p>
     */
    public static long totalPrice(long unitPriceCents, int times) {
        if (unitPriceCents < 0L || times < 1) {
            return -1L;
        }
        if (unitPriceCents == 0L) {
            return 0L;
        }
        if (unitPriceCents > Money.MAX_CENTS / times) {
            return -1L;
        }
        return unitPriceCents * times;
    }
}
