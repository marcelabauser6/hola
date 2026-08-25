package com.athensmc.fsshopkeepers.money;

/**
 * Money as an exact number of cents.
 *
 * <p>Fantastic Cash has two decimal places, so a price is a {@code long} count of cents and never a
 * {@code double}. A double price is how a shop ends up charging 24.999999 for something the admin typed as 25,
 * and once that value is saved to the world there is no way to tell it was meant to be round.</p>
 *
 * <p>Pure Java on purpose: no Minecraft and no Forge, so the parsing and formatting can be exercised by a plain
 * JVM test and so the client can format a price without the currency mod being installed.</p>
 */
public final class Money {

    /** Cents in one unit of currency. */
    public static final long SCALE = 100L;

    /**
     * The largest price a shop may hold.
     *
     * <p>Bounded well below {@link Long#MAX_VALUE} so that adding two prices, or multiplying a price by a stack
     * count, cannot silently wrap into a negative number. A trade that overflows to a negative price is a trade
     * that pays the buyer, which is the worst possible way for an arithmetic bug to surface.</p>
     */
    public static final long MAX_CENTS = 1_000_000_000_000L;

    private Money() {
    }

    /** Thrown when text a player typed is not a price. */
    public static final class InvalidAmountException extends Exception {
        public InvalidAmountException(String message) {
            super(message);
        }
    }

    /** True when {@code a + b} stays inside {@link #MAX_CENTS}. */
    public static boolean canAdd(long a, long b) {
        return a >= 0L && b >= 0L && a <= MAX_CENTS - b;
    }

    /**
     * Adds two balances the way money must be added: failing closed.
     *
     * <p>A negative reading is treated as nothing rather than subtracted, and a sum too large to represent saturates instead
     * of wrapping. Both matter because this total is what decides whether someone can afford something: an earlier version
     * answered {@link #MAX_CENTS} whenever the two would not add cleanly, so an unreadable or negative balance came out as
     * "can afford anything" and goods left the shop for free.</p>
     */
    public static long addSpendable(long a, long b) {
        long left = Math.max(0L, a);
        long right = Math.max(0L, b);
        if (left > MAX_CENTS - right) {
            return MAX_CENTS;
        }
        return Math.min(MAX_CENTS, left + right);
    }

    /** Clamps a value into the representable range. */
    public static long clamp(long cents) {
        if (cents < 0L) {
            return 0L;
        }
        return Math.min(cents, MAX_CENTS);
    }

    /**
     * Multiplies a price by a count, saturating at {@link #MAX_CENTS}.
     *
     * <p>Saturating rather than wrapping: a price too large to represent should refuse to grow, not become
     * small again.</p>
     */
    public static long multiply(long cents, long count) {
        if (cents <= 0L || count <= 0L) {
            return 0L;
        }
        if (cents > MAX_CENTS / count) {
            return MAX_CENTS;
        }
        return cents * count;
    }

    /**
     * Parses a typed price into cents.
     *
     * <p>A comma is accepted as well as a dot, because a Spanish keyboard offers the comma and rejecting a
     * price for using it would be a pointless obstacle in an editor whose text is in Spanish.</p>
     *
     * <p>More than two decimals is refused rather than rounded. {@code 1.005} is a typo, and turning it into
     * {@code 1.00} or {@code 1.01} hides the typo behind a price the admin never chose.</p>
     *
     * @throws InvalidAmountException when the text is not a non-negative price within range.
     */
    public static long parse(String text) throws InvalidAmountException {
        if (text == null) {
            throw new InvalidAmountException("falta el importe");
        }
        String cleaned = text.trim().replace(',', '.').replace("_", "").replace(" ", "");
        if (cleaned.isEmpty()) {
            throw new InvalidAmountException("falta el importe");
        }
        if (cleaned.startsWith("-")) {
            throw new InvalidAmountException("el importe no puede ser negativo");
        }
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        int dot = cleaned.indexOf('.');
        String whole = dot < 0 ? cleaned : cleaned.substring(0, dot);
        String fraction = dot < 0 ? "" : cleaned.substring(dot + 1);
        if (fraction.indexOf('.') >= 0) {
            throw new InvalidAmountException("el importe tiene dos puntos decimales");
        }
        if (fraction.length() > 2) {
            throw new InvalidAmountException("el importe admite como maximo dos decimales");
        }
        if (whole.isEmpty()) {
            whole = "0";
        }
        if (!isDigits(whole) || !isDigits(fraction)) {
            throw new InvalidAmountException("el importe solo admite digitos");
        }
        long units;
        try {
            units = Long.parseLong(whole);
        } catch (NumberFormatException tooBig) {
            throw new InvalidAmountException("el importe es demasiado grande");
        }
        long cents = fraction.isEmpty() ? 0L
                : Long.parseLong(fraction.length() == 1 ? fraction + "0" : fraction);
        if (units > MAX_CENTS / SCALE) {
            throw new InvalidAmountException("el importe es demasiado grande");
        }
        long total = units * SCALE + cents;
        if (total > MAX_CENTS) {
            throw new InvalidAmountException("el importe es demasiado grande");
        }
        return total;
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a price, returning {@code -1} instead of throwing.
     *
     * <p>For the editor's text field, which repaints on every keystroke and treats "not a price yet" as a
     * normal state rather than an error worth an exception.</p>
     */
    public static long parseOrInvalid(String text) {
        try {
            return parse(text);
        } catch (InvalidAmountException notAPrice) {
            return -1L;
        }
    }

    /** {@code 2500} to {@code "25.00"}, for a text field the admin will edit. */
    public static String plain(long cents) {
        long safe = Math.max(0L, cents);
        return (safe / SCALE) + "." + String.format("%02d", safe % SCALE);
    }

    /** {@code 2500} to {@code "25.00 $"}, for display. */
    public static String format(long cents, String symbol) {
        String suffix = symbol == null || symbol.isBlank() ? "" : " " + symbol;
        return group(plain(cents)) + suffix;
    }

    /**
     * Inserts thousands separators into an already formatted amount.
     *
     * <p>A shop can legitimately be selling something for six figures, and {@code 1250000.00} is a number a
     * human has to count digits on. {@code 1.250.000,00} is not.</p>
     */
    private static String group(String plain) {
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        String fraction = plain.substring(dot + 1);
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = whole.length() - 1; i >= 0; i--) {
            out.append(whole.charAt(i));
            if (++count % 3 == 0 && i > 0) {
                out.append('.');
            }
        }
        return out.reverse().append(',').append(fraction).toString();
    }
}
