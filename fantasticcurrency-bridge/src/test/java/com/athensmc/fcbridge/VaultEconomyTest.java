package com.athensmc.fcbridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the one place cents meet doubles.
 *
 * <p>The mod counts exact cents; Vault's interface counts {@code double}. The conversion happens at that
 * boundary and nowhere else, so this is where a rounding mistake would turn into money quietly appearing or
 * vanishing on every transaction a plugin makes.</p>
 */
class VaultEconomyTest {

    @Test
    @DisplayName("whole and fractional amounts convert exactly")
    void convertsExactly() {
        assertEquals(0L, VaultEconomy.toCents(0.0D));
        assertEquals(1L, VaultEconomy.toCents(0.01D));
        assertEquals(100L, VaultEconomy.toCents(1.0D));
        assertEquals(1234L, VaultEconomy.toCents(12.34D));
        assertEquals(100000L, VaultEconomy.toCents(1000.0D));

        assertEquals(0.0D, VaultEconomy.toAmount(0L));
        assertEquals(0.01D, VaultEconomy.toAmount(1L));
        assertEquals(12.34D, VaultEconomy.toAmount(1234L));
    }

    /**
     * The case that makes truncation wrong.
     *
     * <p>0.07 has no exact binary form: a price arrived at by arithmetic can be 0.06999999999999999, and
     * {@code (long) (amount * 100)} would take seven cents down to six. Every such price would shave a cent
     * off the seller.</p>
     */
    @Test
    @DisplayName("a price that binary cannot hold exactly still rounds to the right cent")
    void roundsRatherThanTruncates() {
        assertEquals(7L, VaultEconomy.toCents(0.07D));
        assertEquals(7L, VaultEconomy.toCents(0.06999999999999999D));
        assertEquals(3L, VaultEconomy.toCents(0.025D), "half a cent rounds up");
        assertEquals(1L, VaultEconomy.toCents(0.005D), "half a cent rounds up");
        assertEquals(0L, VaultEconomy.toCents(0.004D), "under half a cent rounds down");

        // 0.1 + 0.2 is 0.30000000000000004, and must still be thirty cents.
        assertEquals(30L, VaultEconomy.toCents(0.1D + 0.2D));
    }

    /** Nonsense amounts become zero, which callers refuse, rather than a negative or wrapped charge. */
    @Test
    @DisplayName("negative, NaN and infinite amounts come out as zero")
    void refusesNonsense() {
        assertEquals(0L, VaultEconomy.toCents(-1.0D));
        assertEquals(0L, VaultEconomy.toCents(-0.01D));
        assertEquals(0L, VaultEconomy.toCents(Double.NaN));
        assertEquals(0L, VaultEconomy.toCents(Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY));
    }

    /**
     * Neither of the two ways an enormous amount could go wrong ends in a wrapped negative.
     *
     * <p>A wrapped {@code long} goes negative, and a negative charge is a payout - so overflow here would
     * mean handing out money. The two enormous cases are handled differently on purpose: a finite amount too
     * large for cents saturates, which any caller then refuses for lack of funds, while an infinity is
     * rejected outright along with NaN, because it is not a quantity at all and should never have reached an
     * economy.</p>
     */
    @Test
    @DisplayName("enormous amounts saturate, and infinity is rejected outright")
    void neverWrapsNegative() {
        assertEquals(Long.MAX_VALUE, VaultEconomy.toCents(Double.MAX_VALUE));
        assertEquals(0L, VaultEconomy.toCents(Double.POSITIVE_INFINITY));
        assertEquals(0L, VaultEconomy.toCents(Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("a round trip through cents keeps two decimals")
    void roundTripsTwoDecimals() {
        for (long cents : new long[]{0L, 1L, 7L, 99L, 100L, 12345L, 999999L}) {
            assertEquals(cents, VaultEconomy.toCents(VaultEconomy.toAmount(cents)),
                    "cents -> amount -> cents should be lossless for " + cents);
        }
    }
}
