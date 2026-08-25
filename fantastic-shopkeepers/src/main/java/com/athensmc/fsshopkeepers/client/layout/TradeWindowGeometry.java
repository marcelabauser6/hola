package com.athensmc.fsshopkeepers.client.layout;

/**
 * The buying window's geometry: vanilla's merchant layout, plus the scale that enlarges it.
 *
 * <p>Pure arithmetic, so the hit areas can be checked by a test rather than by hovering things in game. Every constant is
 * vanilla's own, read out of {@code MerchantScreen}: the 276x166 window, seven 88x20 rows from {@code topPos + 18},
 * payments at {@code +10} and {@code +40}, the result at {@code +73}, the scroller at {@code +94}.</p>
 *
 * <p>The item rectangles are 16 pixels, not the 18 of a slot well and certainly not the 88 of a row. That distinction is
 * the whole point of having this class: a tooltip keyed to the row instead of the item covered half the screen whenever
 * the cursor crossed the list.</p>
 */
public final class TradeWindowGeometry {

    public static final int WINDOW_WIDTH = 276;
    public static final int WINDOW_HEIGHT = 166;

    public static final int TRADE_BUTTON_X = 5;
    public static final int TRADE_BUTTON_Y = 18;
    public static final int TRADE_BUTTON_WIDTH = 88;
    public static final int TRADE_BUTTON_HEIGHT = 20;
    public static final int VISIBLE_TRADES = 7;

    /** Offsets of the three items inside a row, from the row's left edge. */
    public static final int ROW_PAYMENT_A = 5;
    public static final int ROW_PAYMENT_B = 35;
    public static final int ROW_RESULT = 68;
    public static final int ROW_ARROW = 55;

    public static final int SCROLLER_X = 94;
    public static final int SCROLLER_TOP = 18;
    public static final int SCROLLER_WIDTH = 6;
    public static final int SCROLLER_HEIGHT = 27;
    public static final int SCROLL_TRACK_HEIGHT = 139;

    public static final int SLOT_PAYMENT_A_X = 136;
    public static final int SLOT_PAYMENT_B_X = 162;
    public static final int SLOT_RESULT_X = 220;
    public static final int SLOT_Y = 37;

    /** An item is 16 pixels square. */
    public static final int ITEM = 16;

    /** The largest magnification applied to the window. */
    public static final float MAX_SCALE = 1.75F;

    /** Space left around the window when working out how much it can grow. */
    public static final int SCREEN_MARGIN = 20;

    private TradeWindowGeometry() {
    }

    /**
     * How much to magnify the window for a given screen.
     *
     * <p>Never below one, so a small screen shows the window at vanilla size instead of shrinking it, and never so large
     * that the window plus its margin would not fit.</p>
     */
    public static float scaleFor(int screenWidth, int screenHeight) {
        float fitWidth = (float) screenWidth / (WINDOW_WIDTH + SCREEN_MARGIN);
        float fitHeight = (float) screenHeight / (WINDOW_HEIGHT + SCREEN_MARGIN);
        return Math.max(1.0F, Math.min(MAX_SCALE, Math.min(fitWidth, fitHeight)));
    }

    /** The window's left edge, in the scaled space the screen draws in. */
    public static int leftPos(int screenWidth, float scale) {
        return (int) (((float) screenWidth / scale - WINDOW_WIDTH) / 2.0F);
    }

    public static int topPos(int screenHeight, float scale) {
        return (int) (((float) screenHeight / scale - WINDOW_HEIGHT) / 2.0F);
    }

    /** A trade row's clickable area. */
    public static Rect row(int leftPos, int topPos, int slot) {
        if (slot < 0 || slot >= VISIBLE_TRADES) {
            return Rect.EMPTY;
        }
        return new Rect(leftPos + TRADE_BUTTON_X, topPos + TRADE_BUTTON_Y + slot * TRADE_BUTTON_HEIGHT,
                TRADE_BUTTON_WIDTH, TRADE_BUTTON_HEIGHT);
    }

    /** Where a row's items sit vertically, one pixel above the row as vanilla draws them. */
    public static int rowItemY(int topPos, int slot) {
        return topPos + 16 + 1 + slot * TRADE_BUTTON_HEIGHT + 2;
    }

    public static Rect rowPaymentA(int leftPos, int topPos, int slot) {
        return item(leftPos + TRADE_BUTTON_X + ROW_PAYMENT_A, rowItemY(topPos, slot), slot);
    }

    public static Rect rowPaymentB(int leftPos, int topPos, int slot) {
        return item(leftPos + TRADE_BUTTON_X + ROW_PAYMENT_B, rowItemY(topPos, slot), slot);
    }

    public static Rect rowResult(int leftPos, int topPos, int slot) {
        return item(leftPos + TRADE_BUTTON_X + ROW_RESULT, rowItemY(topPos, slot), slot);
    }

    /** The arrow between a row's payments and its result. */
    public static Rect rowArrow(int leftPos, int topPos, int slot) {
        if (slot < 0 || slot >= VISIBLE_TRADES) {
            return Rect.EMPTY;
        }
        return new Rect(leftPos + TRADE_BUTTON_X + ROW_ARROW, rowItemY(topPos, slot) + 3, 10, 9);
    }

    private static Rect item(int x, int y, int slot) {
        return slot < 0 || slot >= VISIBLE_TRADES ? Rect.EMPTY : new Rect(x, y, ITEM, ITEM);
    }

    public static Rect slotPaymentA(int leftPos, int topPos) {
        return new Rect(leftPos + SLOT_PAYMENT_A_X, topPos + SLOT_Y, ITEM, ITEM);
    }

    public static Rect slotPaymentB(int leftPos, int topPos) {
        return new Rect(leftPos + SLOT_PAYMENT_B_X, topPos + SLOT_Y, ITEM, ITEM);
    }

    public static Rect slotResult(int leftPos, int topPos) {
        return new Rect(leftPos + SLOT_RESULT_X, topPos + SLOT_Y, ITEM, ITEM);
    }

    /** The scrollbar track. */
    public static Rect scrollTrack(int leftPos, int topPos) {
        return new Rect(leftPos + SCROLLER_X, topPos + SCROLLER_TOP, SCROLLER_WIDTH, SCROLL_TRACK_HEIGHT);
    }
}
