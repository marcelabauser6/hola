package com.athensmc.athenscoins.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Fantastic Cash arithmetic.
 *
 * <p>Money is <em>always</em> handled as an integer number of cents. Nothing in the mod ever
 * stores a {@code double} balance, which is what guarantees the "only two decimals" rule:
 * {@code 1.25} is the long {@code 125}, and a value like {@code 1.2543} cannot exist.</p>
 */
public final class Money {

    /** Cents in one unit of currency. */
    public static final long SCALE = 100L;

    /** Largest balance we accept, chosen so cents arithmetic cannot overflow a long. */
    public static final long MAX_CENTS = 1_000_000_000_000L;

    private Money() {
    }

    // ------------------------------------------------------------------ parsing

    /** Thrown when a player types an amount we cannot accept. */
    public static class InvalidAmountException extends Exception {
        private final String reasonKey;

        public InvalidAmountException(String reasonKey) {
            super(reasonKey);
            this.reasonKey = reasonKey;
        }

        public String reasonKey() {
            return reasonKey;
        }
    }

    /**
     * Plain decimal money only: digits, optionally a dot and one or two more digits.
     *
     * <p>Anything else is refused on purpose. In particular this rejects scientific notation:
     * {@code BigDecimal} happily reads {@code "1e5"} as 100000 with a negative scale, which would
     * sail past a naive "scale &lt;= 2" check and let a player move $100,000 by typing three
     * characters.</p>
     */
    private static final java.util.regex.Pattern AMOUNT_PATTERN =
            java.util.regex.Pattern.compile("^-?\\d{1,15}(\\.\\d{1,2})?$");

    /**
     * Parses player input such as {@code "34"}, {@code "34.5"} or {@code "34.50"} into cents.
     *
     * @throws InvalidAmountException if it is not a plain decimal number, is not positive, or has
     *                                more than two decimal places
     */
    public static long parse(String raw) throws InvalidAmountException {
        if (raw == null) {
            throw new InvalidAmountException("message.athens_coins.amount_invalid");
        }
        String cleaned = raw.trim().replace("$", "").replace(",", ".");
        if (cleaned.isEmpty()) {
            throw new InvalidAmountException("message.athens_coins.amount_invalid");
        }
        if (!AMOUNT_PATTERN.matcher(cleaned).matches()) {
            // Give the more helpful message when the shape is right but the precision is not.
            if (cleaned.matches("^-?\\d{1,15}\\.\\d{3,}$")) {
                throw new InvalidAmountException("message.athens_coins.amount_decimals");
            }
            throw new InvalidAmountException("message.athens_coins.amount_invalid");
        }

        BigDecimal value = new BigDecimal(cleaned);
        if (value.signum() <= 0) {
            throw new InvalidAmountException("message.athens_coins.amount_positive");
        }
        long cents = value.movePointRight(2).longValueExact();
        if (cents > MAX_CENTS) {
            throw new InvalidAmountException("message.athens_coins.amount_too_big");
        }
        return cents;
    }

    /** Converts a config value like {@code 0.15} into cents, rounding to two decimals. */
    public static long fromConfig(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValue();
    }

    // ------------------------------------------------------------------ formatting

    /** Full form with the configured symbol and thousands separators: {@code $1,234.56}. */
    public static String format(long cents, String symbol) {
        long abs = Math.abs(cents);
        String digits = String.format(Locale.ROOT, "%,d.%02d", abs / SCALE, abs % SCALE);
        return (cents < 0 ? "-" : "") + symbol + digits;
    }

    /** Plain two-decimal form without a symbol: {@code 1234.56}. */
    public static String plain(long cents) {
        long abs = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (abs / SCALE) + "." + String.format(Locale.ROOT, "%02d", abs % SCALE);
    }

    /** Short form that fits a 16x16 GUI cell: {@code $1.2k}. */
    public static String compact(long cents, String symbol) {
        long units = cents / SCALE;
        if (Math.abs(units) < 1_000L) {
            return format(cents, symbol);
        }
        if (Math.abs(units) < 1_000_000L) {
            return symbol + trim(units / 1_000.0D) + "k";
        }
        if (Math.abs(units) < 1_000_000_000L) {
            return symbol + trim(units / 1_000_000.0D) + "M";
        }
        return symbol + trim(units / 1_000_000_000.0D) + "B";
    }

    /** Short form for plain item counts (no currency symbol): {@code 1234 -> "1.2k"}. */
    public static String compactCount(long count) {
        if (Math.abs(count) < 1_000L) {
            return Long.toString(count);
        }
        if (Math.abs(count) < 1_000_000L) {
            return trim(count / 1_000.0D) + "k";
        }
        return trim(count / 1_000_000.0D) + "M";
    }

    private static String trim(double value) {
        if (Math.abs(value) >= 100.0D) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    // ------------------------------------------------------------------ helpers

    /** Multiplies a unit price by a count, saturating instead of overflowing. */
    public static long multiply(long unitCents, long count) {
        if (unitCents <= 0L || count <= 0L) {
            return 0L;
        }
        if (count > MAX_CENTS / unitCents) {
            return MAX_CENTS;
        }
        return unitCents * count;
    }

    /** Applies a percentage fee, never returning less than zero. */
    public static long afterFee(long cents, int feePercent) {
        if (feePercent <= 0) {
            return cents;
        }
        return Math.max(0L, cents - cents * feePercent / 100L);
    }

    /** Adds a percentage fee on top of a price. */
    public static long plusFee(long cents, int feePercent) {
        if (feePercent <= 0) {
            return cents;
        }
        return Math.min(MAX_CENTS, cents + cents * feePercent / 100L);
    }

    public static long clampBalance(long cents) {
        return Math.max(0L, Math.min(cents, MAX_CENTS));
    }

    /** True when two non-negative money values can be combined without overflow or saturation. */
    public static boolean canAdd(long left, long right) {
        return left >= 0L && right >= 0L && left <= MAX_CENTS && right <= MAX_CENTS - left;
    }

    /** Adds money exactly, rejecting overflow instead of silently minting or losing cents. */
    public static long addExact(long left, long right) {
        if (!canAdd(left, right)) {
            throw new ArithmeticException("Fantastic Cash balance overflow");
        }
        return left + right;
    }

    /** Subtracts non-negative money exactly. */
    public static long subtractExact(long left, long right) {
        if (left < 0L || right < 0L || right > left) {
            throw new ArithmeticException("Fantastic Cash balance underflow");
        }
        return left - right;
    }
}
