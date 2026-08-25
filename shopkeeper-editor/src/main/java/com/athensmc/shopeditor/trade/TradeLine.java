package com.athensmc.shopeditor.trade;

/**
 * One row of a shop, as the editor shows and edits it.
 *
 * <p>The item being sold, how many of it, and the price in <strong>Cash cents</strong>. Cents rather than a
 * decimal, because the money is counted in cents everywhere else and a {@code double} price is how a shop ends
 * up selling for 24.999999.</p>
 *
 * <p>The item is carried as its registry id - {@code minecraft:diamond} - and not as an ItemStack, so this
 * record crosses the network and can be compared and tested without a game running.</p>
 *
 * @param itemId    registry id of the item on sale, e.g. {@code minecraft:diamond}.
 * @param amount    how many of it one trade gives.
 * @param priceCents what one trade costs, in Cash cents.
 */
public record TradeLine(String itemId, int amount, long priceCents) {

    /** Largest stack any trade can hand over. Shopkeepers' result slot is one stack. */
    public static final int MAX_AMOUNT = 64;

    public TradeLine {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("un trato necesita un item");
        }
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("la cantidad tiene que estar entre 1 y " + MAX_AMOUNT
                    + ", no " + amount);
        }
        if (priceCents < 0L) {
            throw new IllegalArgumentException("el precio no puede ser negativo");
        }
    }

    /** True when this trade would be given away, which is almost always a mistake worth flagging. */
    public boolean isFree() {
        return priceCents == 0L;
    }

    /** The price as a plain two-decimal string, for a text field. */
    public String priceText() {
        return (priceCents / 100L) + "." + String.format("%02d", Math.abs(priceCents % 100L));
    }

    /**
     * Parses a typed price into cents.
     *
     * <p>Accepts a comma or a dot, because a Spanish keyboard offers the comma and refusing it would be a
     * pointless way to reject a valid price. More than two decimals is refused rather than rounded: a price of
     * 1.005 is a typo, and silently making it 1.00 or 1.01 hides it.</p>
     *
     * @return the price in cents, or -1 when the text is not a price.
     */
    public static long parsePrice(String text) {
        if (text == null) {
            return -1L;
        }
        String cleaned = text.trim().replace(',', '.');
        if (cleaned.isEmpty()) {
            return -1L;
        }
        int dot = cleaned.indexOf('.');
        String whole = dot < 0 ? cleaned : cleaned.substring(0, dot);
        String fraction = dot < 0 ? "" : cleaned.substring(dot + 1);
        if (fraction.length() > 2 || cleaned.indexOf('.', dot + 1) >= 0) {
            return -1L;
        }
        if (whole.isEmpty()) {
            whole = "0";
        }
        try {
            long units = Long.parseLong(whole);
            long cents = fraction.isEmpty() ? 0L
                    : Long.parseLong(fraction.length() == 1 ? fraction + "0" : fraction);
            if (units < 0L || cents < 0L) {
                return -1L;
            }
            long total = units * 100L + cents;
            return total / 100L == units ? total : -1L;
        } catch (NumberFormatException notANumber) {
            return -1L;
        }
    }
}
