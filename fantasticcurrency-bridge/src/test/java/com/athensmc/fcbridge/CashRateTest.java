package com.athensmc.fcbridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the number that joins Shopkeepers' integer prices to exact cents.
 *
 * <p>Every failure here is money: a price that overflows, a rate that rounds, or a division that gives out a
 * unit that was only partly paid for.</p>
 */
class CashRateTest {

    @Test
    @DisplayName("a price of 25 costs 25.00 at the default rate")
    void defaultRateIsOneToOne() {
        CashRate rate = CashRate.of(1.0D);

        assertEquals(100L, rate.centsPerUnit());
        assertEquals(2500L, rate.costCents(25L));
        assertEquals(0L, rate.costCents(0L));
    }

    @Test
    @DisplayName("a rate of 0.01 puts prices in cents")
    void centRateWorks() {
        CashRate rate = CashRate.of(0.01D);

        assertEquals(1L, rate.centsPerUnit());
        assertEquals(25L, rate.costCents(25L));
    }

    /**
     * The rate has to be whole cents.
     *
     * <p>Half a cent cannot be charged, so a rate of 0.005 would truncate on every trade. Refused at startup,
     * where it is one message, rather than discovered later as takings that are quietly short.</p>
     */
    @Test
    @DisplayName("a rate that is not whole cents is refused")
    void refusesFractionalCents() {
        assertThrows(IllegalArgumentException.class, () -> CashRate.of(0.005D));
        assertThrows(IllegalArgumentException.class, () -> CashRate.of(0.001D));
    }

    @Test
    @DisplayName("a rate of zero or less is refused")
    void refusesNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> CashRate.of(0.0D));
        assertThrows(IllegalArgumentException.class, () -> CashRate.of(-1.0D));
        assertThrows(IllegalArgumentException.class, () -> CashRate.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> CashRate.of(Double.POSITIVE_INFINITY));
    }

    /** Binary cannot hold 0.07 exactly, and the rate still has to come out as seven cents. */
    @Test
    @DisplayName("a rate binary cannot hold exactly still lands on the right cent")
    void roundsToTheNearestCent() {
        assertEquals(7L, CashRate.of(0.07D).centsPerUnit());
        assertEquals(30L, CashRate.of(0.1D + 0.2D).centsPerUnit());
    }

    /**
     * A price large enough to overflow is refused, not wrapped.
     *
     * <p>A wrapped total goes negative, and charging a negative amount pays the buyer.</p>
     */
    @Test
    @DisplayName("an overflowing price is refused rather than wrapping negative")
    void refusesOverflow() {
        CashRate rate = CashRate.of(1.0D);

        assertEquals(-1L, rate.costCents(Long.MAX_VALUE / 2L));
        assertEquals(-1L, rate.costCents(-1L), "a negative price is not a price");
        assertTrue(rate.costCents(1_000_000L) > 0L, "an ordinary large price is fine");
    }

    /**
     * Affordability rounds down, always.
     *
     * <p>Handing over a unit that is only partly covered is how a balance goes negative one trade at a time.</p>
     */
    @Test
    @DisplayName("affordability never rounds up")
    void affordabilityRoundsDown() {
        CashRate rate = CashRate.of(1.0D);

        assertEquals(0L, rate.unitsAffordable(99L), "99 cents does not buy a 1.00 unit");
        assertEquals(1L, rate.unitsAffordable(100L));
        assertEquals(1L, rate.unitsAffordable(199L));
        assertEquals(25L, rate.unitsAffordable(2599L));
        assertEquals(0L, rate.unitsAffordable(0L));
        assertEquals(0L, rate.unitsAffordable(-500L), "a negative balance buys nothing");
    }

    /** A refund can never exceed what a charge would have been, or the lending would mint money. */
    @Test
    @DisplayName("a refund matches the charge, and never exceeds it")
    void refundMatchesCharge() {
        CashRate rate = CashRate.of(1.0D);

        for (long units : new long[]{0L, 1L, 7L, 25L, 640L}) {
            assertEquals(rate.costCents(units), rate.refundCents(units),
                    "refunding " + units + " units must equal charging them");
        }
        assertEquals(0L, rate.refundCents(-5L), "a nonsensical refund pays nothing");
        assertEquals(0L, rate.refundCents(Long.MAX_VALUE), "an unrepresentable refund pays nothing");
    }
}
