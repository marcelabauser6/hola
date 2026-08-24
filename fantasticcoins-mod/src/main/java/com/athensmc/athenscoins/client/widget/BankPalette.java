package com.athensmc.athenscoins.client.widget;

/**
 * The colours a bank can brand itself with.
 *
 * <p>A bank's colour is its identity across the mod: the terminal's header and outline, its swatch in
 * the central bank's register, and the tint on every ATM it issues. There was already a
 * {@code SET_COLOR} action on the wire with nothing on the client that could send it, so the colour
 * was whatever the bank was created with - which is why every bank looked the same.</p>
 *
 * <p>A fixed palette rather than a free picker, on purpose: these are chosen to stay legible as a
 * one-pixel outline, as a header band behind white text, and as a tint multiplied over the ATM's
 * metal, and an arbitrary RGB value satisfies none of those reliably. The list is ordered by hue so
 * cycling through it feels like a colour wheel.</p>
 */
public final class BankPalette {

    /** Name key and RGB, with no alpha; {@link com.athensmc.athenscoins.bank.Bank} stores 24 bits. */
    public record Swatch(String key, int rgb) {
    }

    private static final Swatch[] SWATCHES = {
            new Swatch("gui.athens_coins.color_slate", 0x2E4756),
            new Swatch("gui.athens_coins.color_navy", 0x24406E),
            new Swatch("gui.athens_coins.color_teal", 0x1F6E63),
            new Swatch("gui.athens_coins.color_forest", 0x2C6136),
            new Swatch("gui.athens_coins.color_olive", 0x5E6524),
            new Swatch("gui.athens_coins.color_gold", 0x9A7420),
            new Swatch("gui.athens_coins.color_amber", 0xA85E1C),
            new Swatch("gui.athens_coins.color_rust", 0x8E3B22),
            new Swatch("gui.athens_coins.color_crimson", 0x8A2436),
            new Swatch("gui.athens_coins.color_plum", 0x6B2A5A),
            new Swatch("gui.athens_coins.color_violet", 0x4A3080),
            new Swatch("gui.athens_coins.color_graphite", 0x33373D),
    };

    private BankPalette() {
    }

    public static int count() {
        return SWATCHES.length;
    }

    public static Swatch at(int index) {
        return SWATCHES[Math.floorMod(index, SWATCHES.length)];
    }

    /**
     * The palette index whose colour matches {@code rgb}, or the nearest one.
     *
     * <p>Nearest rather than exact so a bank created before the palette existed, or one whose colour
     * was set by command, still lands on a real entry instead of resetting to the first.</p>
     */
    public static int indexOf(int rgb) {
        int target = rgb & 0xFFFFFF;
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < SWATCHES.length; i++) {
            int candidate = SWATCHES[i].rgb();
            if (candidate == target) {
                return i;
            }
            long dr = ((candidate >> 16) & 0xFF) - ((target >> 16) & 0xFF);
            long dg = ((candidate >> 8) & 0xFF) - ((target >> 8) & 0xFF);
            long db = (candidate & 0xFF) - (target & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}
