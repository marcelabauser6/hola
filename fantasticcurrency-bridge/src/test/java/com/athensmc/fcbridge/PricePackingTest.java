package com.athensmc.fcbridge;

import com.athensmc.fcbridge.PricePacking.Packed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a price becomes exactly the cost items Shopkeepers will charge.
 *
 * <p>Every case here is a shop selling for the wrong money if it goes wrong. A packing that comes out light is
 * the dangerous one, because the shop keeps working and nobody notices until the stock is gone.</p>
 */
class PricePackingTest {

    /** Shopkeepers' defaults: base worth 1, high worth 9, both stacking to 64. */
    private static PricePacking defaults() {
        return new PricePacking(1, 64, 9, 64);
    }

    @Test
    @DisplayName("a packed price adds up to exactly the price asked for")
    void packsExactly() {
        PricePacking packing = defaults();

        Packed twentyFive = packing.pack(25L);
        assertNotNull(twentyFive);
        assertEquals(2, twentyFive.highCount(), "two nines");
        assertEquals(7, twentyFive.baseCount(), "and seven ones");
        assertEquals(25L, twentyFive.highCount() * 9L + twentyFive.baseCount());
    }

    @Test
    @DisplayName("a price under the high denomination uses base items only")
    void smallPricesUseBaseOnly() {
        Packed five = defaults().pack(5L);

        assertNotNull(five);
        assertEquals(0, five.highCount());
        assertEquals(5, five.baseCount());
    }

    @Test
    @DisplayName("a price of one unit is valid, and zero is not")
    void handlesTheEdges() {
        PricePacking packing = defaults();

        Packed one = packing.pack(1L);
        assertNotNull(one);
        assertEquals(1, one.baseCount());
        assertFalse(one.isEmpty());

        assertNull(packing.pack(0L), "a free trade is not a price");
        assertNull(packing.pack(-5L), "a negative price would pay the buyer");
    }

    /**
     * The ceiling, which is the thing about Shopkeepers most likely to surprise.
     *
     * <p>Two slots, one stack each: 64 nines plus 64 ones is 640, and 641 cannot be expressed at any
     * price. Refusing is the only honest answer.</p>
     */
    @Test
    @DisplayName("the two-slot ceiling is enforced and explained")
    void enforcesTheCeiling() {
        PricePacking packing = defaults();

        assertEquals(640L, packing.maxUnits());
        assertNotNull(packing.pack(640L), "the ceiling itself is reachable");
        assertNull(packing.pack(641L), "one over the ceiling cannot be expressed");
        assertTrue(packing.explainRefusal(641L).contains("640"),
                "the refusal should name the limit: " + packing.explainRefusal(641L));
    }

    @Test
    @DisplayName("the ceiling rises with a more valuable high denomination")
    void aBiggerHighDenominationRaisesTheCeiling() {
        PricePacking generous = new PricePacking(1, 64, 100, 64);

        assertEquals(6464L, generous.maxUnits());
        Packed price = generous.pack(5000L);
        assertNotNull(price);
        assertEquals(50, price.highCount());
        assertEquals(0, price.baseCount());
    }

    @Test
    @DisplayName("with no high denomination the ceiling is one stack of base")
    void worksWithoutAHighCurrency() {
        PricePacking baseOnly = new PricePacking(1, 64, 0, 64);

        assertFalse(baseOnly.hasHigh());
        assertEquals(64L, baseOnly.maxUnits());
        Packed price = baseOnly.pack(64L);
        assertNotNull(price);
        assertEquals(0, price.highCount());
        assertEquals(64, price.baseCount());
        assertNull(baseOnly.pack(65L));
    }

    /**
     * A base worth more than one cannot express every number.
     *
     * <p>With a base of 5 and a high of 20, a price of 23 is unreachable, and giving back 20 would be a
     * three-unit discount on every sale.</p>
     */
    @Test
    @DisplayName("a price that the denominations cannot form is refused, not rounded")
    void refusesPricesItCannotForm() {
        PricePacking coarse = new PricePacking(5, 64, 20, 64);

        assertNull(coarse.pack(23L), "23 is not reachable with 5s and 20s");
        assertFalse(coarse.canExpress(23L));
        assertTrue(coarse.explainRefusal(23L).contains("5"),
                "the refusal should hint at the base value: " + coarse.explainRefusal(23L));

        assertNotNull(coarse.pack(25L), "25 is a 20 and a 5");
        assertNotNull(coarse.pack(20L));
        assertNotNull(coarse.pack(5L));
    }

    /**
     * Dropping one high item to make the remainder divide.
     *
     * <p>With a base of 4 and a high of 10, a price of 18 taken greedily is one 10 with 8 left over, which
     * divides. A price of 14 is one 10 with 4 left, also fine. A price of 12 is one 10 with 2 left, which does
     * not divide - trading the 10 back for base items gives three 4s, which does.</p>
     */
    @Test
    @DisplayName("it gives back a high item when that is what makes the price fit")
    void backsOffAHighItemToFit() {
        PricePacking packing = new PricePacking(4, 64, 10, 64);

        Packed twelve = packing.pack(12L);
        assertNotNull(twelve, "12 is three 4s");
        assertEquals(0, twelve.highCount());
        assertEquals(3, twelve.baseCount());

        Packed eighteen = packing.pack(18L);
        assertNotNull(eighteen);
        assertEquals(18L, eighteen.highCount() * 10L + eighteen.baseCount() * 4L);
    }

    /** Whatever comes back must always total the price, at every value up to the ceiling. */
    @Test
    @DisplayName("no packing anywhere under the ceiling is ever short")
    void neverPacksShort() {
        PricePacking packing = defaults();

        for (long units = 1L; units <= packing.maxUnits(); units++) {
            Packed packed = packing.pack(units);
            if (packed == null) {
                continue;
            }
            long total = packed.highCount() * 9L + packed.baseCount();
            assertEquals(units, total, "packing " + units + " came out as " + total);
            assertTrue(packed.highCount() <= 64 && packed.baseCount() <= 64,
                    "packing " + units + " overflowed a slot");
        }
    }
}
