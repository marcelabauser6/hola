package com.athensmc.fcbridge;

/**
 * Converts between Shopkeepers' prices and Fantastic Cash.
 *
 * <p>Shopkeepers prices everything as a whole number of currency units - it counts items, so a price is
 * always an integer. FantasticCurrency counts exact cents. This holds the single number that joins them: how
 * much Cash one currency unit is worth.</p>
 *
 * <p>With the default of one unit to 1.00 Cash, a shop owner who sets a price of 25 has set 25.00 Cash, which
 * is the point - prices are set in the digital currency, and the item count is only how Shopkeepers happens
 * to store it. Setting the rate to 0.01 instead lets prices be entered in cents, at the cost of a much lower
 * ceiling, since Shopkeepers can only fit so many items in a trade slot.</p>
 *
 * <p>Pure arithmetic, no Bukkit, so the conversion can be checked directly. Every method refuses rather than
 * wraps: a negative price is a payout, and that is not a rounding error anyone notices until the money is
 * gone.</p>
 */
public final class CashRate {

    /** Cents in one unit of Cash. */
    public static final long CENTS_PER_UNIT = 100L;

    /** Cash cents that one Shopkeepers currency unit is worth. */
    private final long centsPerCurrencyUnit;

    private CashRate(long centsPerCurrencyUnit) {
        this.centsPerCurrencyUnit = centsPerCurrencyUnit;
    }

    /**
     * Builds a rate from the configured Cash value of one currency unit.
     *
     * @param cashPerUnit e.g. 1.0 for one unit to 1.00 Cash, or 0.01 for prices in cents.
     * @throws IllegalArgumentException when the rate is not a positive whole number of cents. A fractional
     *                                  cent cannot be charged, so a rate of 0.005 would silently truncate
     *                                  every price.
     */
    public static CashRate of(double cashPerUnit) {
        if (Double.isNaN(cashPerUnit) || Double.isInfinite(cashPerUnit) || cashPerUnit <= 0.0D) {
            throw new IllegalArgumentException(
                    "El valor en Cash de una unidad de moneda debe ser mayor que cero, no " + cashPerUnit);
        }
        double cents = cashPerUnit * CENTS_PER_UNIT;
        long rounded = Math.round(cents);
        if (Math.abs(cents - rounded) > 1.0E-6D) {
            throw new IllegalArgumentException("El valor en Cash de una unidad de moneda tiene que ser "
                    + "un número exacto de céntimos; " + cashPerUnit + " no lo es.");
        }
        if (rounded <= 0L) {
            throw new IllegalArgumentException(
                    "El valor en Cash de una unidad de moneda redondea a cero céntimos.");
        }
        return new CashRate(rounded);
    }

    /** Cash cents one currency unit is worth. */
    public long centsPerUnit() {
        return centsPerCurrencyUnit;
    }

    /**
     * What a price of {@code units} currency units costs in Cash cents.
     *
     * @return the cost, or -1 when it cannot be represented. Callers must treat -1 as "refuse the trade",
     *         never as zero: a free trade is worse than a refused one.
     */
    public long costCents(long units) {
        if (units < 0L) {
            return -1L;
        }
        if (units == 0L) {
            return 0L;
        }
        long cents = units * centsPerCurrencyUnit;
        // Detect the wrap rather than trust it: a wrapped total goes negative, and a negative charge pays
        // the buyer instead of charging them.
        return cents / units == centsPerCurrencyUnit ? cents : -1L;
    }

    /**
     * How many whole currency units a Cash balance can cover.
     *
     * <p>Rounded down, always. Handing over a unit that is only partly paid for is how a balance goes
     * negative one trade at a time.</p>
     */
    public long unitsAffordable(long balanceCents) {
        if (balanceCents <= 0L) {
            return 0L;
        }
        return balanceCents / centsPerCurrencyUnit;
    }

    /** The Cash value of {@code units}, for refunding unspent currency. Never more than was charged. */
    public long refundCents(long units) {
        long cents = costCents(units);
        return cents < 0L ? 0L : cents;
    }
}
