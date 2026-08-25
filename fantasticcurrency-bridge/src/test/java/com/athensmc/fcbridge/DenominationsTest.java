package com.athensmc.fcbridge;

import com.athensmc.fcbridge.Denominations.Coin;
import com.athensmc.fcbridge.Denominations.Plan;
import com.athensmc.fcbridge.Denominations.Unusable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the mapping from FantasticCurrency's cent values onto Shopkeepers' two integer currencies.
 *
 * <p>Worth testing properly because the failure is silent money loss: Shopkeepers prices in whole units of
 * a base currency, so a high denomination that is not an exact multiple of the base cannot be expressed,
 * and rounding it would shave cents off every trade.</p>
 */
class DenominationsTest {

    private static Coin coin(String id, long cents) {
        return new Coin(id, id, cents);
    }

    @Test
    @DisplayName("the cheapest coin becomes the base and the dearest the high one")
    void picksCheapestAsBase() throws Unusable {
        Plan plan = Denominations.plan(List.of(
                coin("gold", 10000L), coin("bronze", 100L), coin("silver", 1000L)));

        assertEquals("bronze", plan.base().id());
        assertEquals("gold", plan.high().id());
        assertEquals(100, plan.highValue(), "gold is 100 bronzes");
        assertTrue(plan.hasHigh());
    }

    @Test
    @DisplayName("silver is reported as unused rather than silently dropped")
    void namesTheUnusedCoin() throws Unusable {
        Plan plan = Denominations.plan(List.of(
                coin("bronze", 100L), coin("silver", 1000L), coin("gold", 10000L)));

        assertTrue(plan.notes().stream().anyMatch(note -> note.contains("silver")),
                "an admin should be told why their silver is not offered: " + plan.notes());
    }

    /**
     * The case that motivates the whole class.
     *
     * <p>Gold at 500 cents cannot be expressed in bronzes worth 3, so it must be refused rather than
     * rounded to 166 and left losing two cents a coin.</p>
     */
    @Test
    @DisplayName("a high coin that is not an exact multiple is refused, not rounded")
    void refusesInexactMultiples() throws Unusable {
        Plan plan = Denominations.plan(List.of(coin("bronze", 3L), coin("gold", 500L)));

        assertNull(plan.high(), "500 is not a multiple of 3, so gold cannot be a currency");
        assertEquals(1, plan.highValue());
        assertFalse(plan.hasHigh());
        assertTrue(plan.notes().stream().anyMatch(note -> note.contains("múltiplo")),
                "the reason should say so: " + plan.notes());
    }

    @Test
    @DisplayName("coins worth nothing are skipped with a reason")
    void skipsWorthlessCoins() throws Unusable {
        Plan plan = Denominations.plan(List.of(
                coin("bronze", 0L), coin("silver", 50L), coin("gold", 100L)));

        assertEquals("silver", plan.base().id(), "a coin worth zero cannot be the base");
        assertEquals(2, plan.highValue());
        assertTrue(plan.notes().stream().anyMatch(note -> note.contains("bronze")));
    }

    @Test
    @DisplayName("no positive coin at all is an error, not an empty plan")
    void refusesWhenNothingIsWorthAnything() {
        Unusable failure = assertThrows(Unusable.class,
                () -> Denominations.plan(List.of(coin("bronze", 0L), coin("gold", -5L))));
        assertTrue(failure.getMessage().contains("valor positivo"));
    }

    @Test
    @DisplayName("a single coin works, with no second denomination")
    void singleCoinIsFine() throws Unusable {
        Plan plan = Denominations.plan(List.of(coin("bronze", 25L)));

        assertEquals("bronze", plan.base().id());
        assertNull(plan.high());
        assertFalse(plan.hasHigh());
    }

    /** Two coins of equal value are one denomination twice, which Shopkeepers must not be given. */
    @Test
    @DisplayName("an equal-valued coin is not offered as a second currency")
    void equalValuesAreNotTwoDenominations() throws Unusable {
        Plan plan = Denominations.plan(List.of(coin("bronze", 100L), coin("silver", 100L)));

        assertFalse(plan.hasHigh());
        assertEquals(1, plan.highValue());
    }

    @Test
    @DisplayName("cents convert to whole units both ways, and refuse when inexact")
    void convertsBetweenCentsAndUnits() {
        assertEquals(7L, Denominations.unitsFor(700L, 100L));
        assertEquals(0L, Denominations.unitsFor(0L, 100L));
        assertEquals(-1L, Denominations.unitsFor(750L, 100L), "7.5 units is not a price");
        assertEquals(-1L, Denominations.unitsFor(-100L, 100L), "a negative price is refused");
        assertEquals(-1L, Denominations.unitsFor(100L, 0L), "a base worth nothing is refused");

        assertEquals(700L, Denominations.centsFor(7L, 100L));
        assertEquals(0L, Denominations.centsFor(0L, 100L));
    }

    /**
     * A price large enough to overflow must be refused, because a wrapped total goes negative and a
     * negative charge pays the buyer.
     */
    @Test
    @DisplayName("an overflowing price is refused rather than wrapping negative")
    void refusesOverflow() {
        assertEquals(-1L, Denominations.centsFor(Long.MAX_VALUE / 2L, 100L));
        assertEquals(Long.MAX_VALUE, Denominations.centsFor(Long.MAX_VALUE, 1L));
    }
}
