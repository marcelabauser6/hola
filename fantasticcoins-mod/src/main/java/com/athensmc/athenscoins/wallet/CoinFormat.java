package com.athensmc.athenscoins.wallet;

import java.util.Locale;

/** Number formatting helpers shared by the GUIs and chat output. */
public final class CoinFormat {

    private CoinFormat() {
    }

    /** Groups thousands with dots: {@code 1234567 -> "1.234.567"}. */
    public static String plain(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', '.');
    }

    /** Short form that fits inside a 16x16 GUI cell: {@code 12345 -> "12.3k"}. */
    public static String compact(long value) {
        long abs = Math.abs(value);
        if (abs < 1_000L) {
            return Long.toString(value);
        }
        if (abs < 1_000_000L) {
            return trim(value / 1_000.0D) + "k";
        }
        if (abs < 1_000_000_000L) {
            return trim(value / 1_000_000.0D) + "M";
        }
        return trim(value / 1_000_000_000.0D) + "B";
    }

    private static String trim(double value) {
        if (Math.abs(value) >= 100.0D) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** Renders a bronze-unit total as "3g 4s 5b". */
    public static String breakdown(long bronzeUnits) {
        long[] parts = CoinType.breakDown(bronzeUnits);
        return parts[0] + "g " + parts[1] + "s " + parts[2] + "b";
    }
}
