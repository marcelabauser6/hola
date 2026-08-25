package com.athensmc.fcbridge;

/**
 * Turns a price in Fantastic Cash into the two cost stacks Shopkeepers stores.
 *
 * <p>A Shopkeepers trade has room for exactly <strong>two</strong> cost slots, each holding at most one stack.
 * So a price is not a number it keeps - it is a pile of currency items, and that puts a hard ceiling on what
 * any single trade can cost. With a base currency worth 1 and a high one worth 9, the most expensive trade
 * possible is {@code 64x9 + 64 = 640} units. Past that the price simply cannot be expressed, and the only
 * remedy is a more valuable high denomination.</p>
 *
 * <p>That ceiling is the reason this class exists rather than a couple of divisions inline: a price that
 * silently lands one stack short would create a shop selling diamonds for pocket change, and nobody would
 * notice until the shop was empty. Every method here refuses what it cannot represent exactly.</p>
 *
 * <p>Pure arithmetic, no Bukkit, so the packing can be checked directly.</p>
 */
public final class PricePacking {

    /** A price expressed as counts of the two currency denominations. */
    public record Packed(int highCount, int baseCount, long units) {

        /** True when nothing is owed, which is not a valid trade cost. */
        public boolean isEmpty() {
            return highCount <= 0 && baseCount <= 0;
        }
    }

    private final int baseValue;
    private final int baseStackLimit;
    private final int highValue;
    private final int highStackLimit;

    /**
     * @param baseValue      units one base currency item is worth, normally 1.
     * @param baseStackLimit how many base items fit in a slot.
     * @param highValue      units one high currency item is worth, or 0 when there is no second currency.
     * @param highStackLimit how many high items fit in a slot.
     */
    public PricePacking(int baseValue, int baseStackLimit, int highValue, int highStackLimit) {
        this.baseValue = Math.max(1, baseValue);
        this.baseStackLimit = Math.max(1, baseStackLimit);
        this.highValue = Math.max(0, highValue);
        this.highStackLimit = Math.max(1, highStackLimit);
    }

    /** The dearest price that can be expressed at all, in currency units. */
    public long maxUnits() {
        long fromBase = (long) baseValue * baseStackLimit;
        long fromHigh = hasHigh() ? (long) highValue * highStackLimit : 0L;
        return fromBase + fromHigh;
    }

    public boolean hasHigh() {
        return highValue > 0;
    }

    /**
     * Packs a price into the two slots.
     *
     * <p>High denomination first, then the remainder in base items, which is what keeps a price inside the
     * slot limits for as long as possible. The result is checked to add up to exactly the price asked for;
     * anything else is refused rather than rounded, because a cost that is a few units light is a shop
     * quietly selling below its price.</p>
     *
     * @return the packing, or null when this price cannot be expressed exactly.
     */
    public Packed pack(long units) {
        if (units <= 0L || units > maxUnits()) {
            return null;
        }

        long remaining = units;
        int highCount = 0;
        if (hasHigh()) {
            long wanted = remaining / highValue;
            highCount = (int) Math.min(wanted, highStackLimit);
            remaining -= (long) highCount * highValue;
        }

        // The remainder has to divide evenly into base items. With a base worth 1 it always does; with a
        // base worth more, a price between two multiples cannot be built and is refused.
        if (remaining % baseValue != 0L) {
            // Trading a high item for base ones can make it divide: try one fewer high.
            if (highCount > 0) {
                highCount--;
                remaining += highValue;
                if (remaining % baseValue != 0L) {
                    return null;
                }
            } else {
                return null;
            }
        }

        long baseCount = remaining / baseValue;
        if (baseCount > baseStackLimit) {
            return null;
        }

        long total = (long) highCount * highValue + baseCount * baseValue;
        if (total != units) {
            return null;
        }
        return new Packed(highCount, (int) baseCount, units);
    }

    /**
     * Whether a price is expressible, for telling an admin before they save.
     *
     * <p>A separate question from {@link #pack}, because the interface wants to grey out an impossible price as
     * it is typed rather than reject it on save.</p>
     */
    public boolean canExpress(long units) {
        return pack(units) != null;
    }

    /** A short explanation of why a price will not fit, for the interface to show. */
    public String explainRefusal(long units) {
        if (units <= 0L) {
            return "El precio tiene que ser mayor que cero.";
        }
        if (units > maxUnits()) {
            return "Demasiado caro. El máximo por trato es " + maxUnits()
                    + " unidades, porque Shopkeepers solo tiene dos huecos de pago.";
        }
        if (!canExpress(units)) {
            return "Ese precio no se puede formar con las monedas configuradas. "
                    + "Prueba un múltiplo de " + baseValue + ".";
        }
        return "";
    }
}
