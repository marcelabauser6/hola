package com.athensmc.fsshopkeepers.client.layout;

/**
 * The buying window's geometry: vanilla's merchant layout, exactly.
 *
 * <p>Pure arithmetic, so the hit areas can be checked by a test instead of by hovering things in game. Every constant is
 * vanilla's own, read out of {@code MerchantScreen}: the 276x166 window, seven 88x20 rows from {@code topPos + 18},
 * payments at {@code +10} and {@code +40}, the result at {@code +73}, the scroller at {@code +94}.</p>
 *
 * <p>No magnification. An earlier version drew the window through a scale transform to make it larger, which looked wrong
 * the moment anything else was on screen: the creative inventory and JEI draw at the game's own GUI scale, so a window
 * scaled on top of them sat at a different size from everything around it. The window is vanilla-sized because everything
 * it shares the screen with is.</p>
 *
 * <p>The item rectangles are 16 pixels, not the 18 of a slot well and certainly not the 88 of a row. That distinction is
 * the point of this class: a tooltip keyed to the row instead of the item covered half the screen whenever the cursor
 * crossed the list.</p>
 */
public final class TradeWindowGeometry {

    public static final int WINDOW_WIDTH = 276;
    public static final int WINDOW_HEIGHT = 166;

    public static final int TRADE_BUTTON_X = 5;
    public static final int TRADE_BUTTON_Y = 18;
    public static final int TRADE_BUTTON_WIDTH = 88;
    public static final int TRADE_BUTTON_HEIGHT = 20;
    public static final int VISIBLE_TRADES = 7;

    /** Offsets of a row's contents, from the row's left edge. */
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

    /** The plus drawn between two payments, and the space it is centred in. */
    private static final int PLUS_WIDTH = 6;
    private static final int PLUS_HEIGHT = 9;

    private TradeWindowGeometry() {
    }

    public static int leftPos(int screenWidth) {
        return (screenWidth - WINDOW_WIDTH) / 2;
    }

    public static int topPos(int screenHeight) {
        return (screenHeight - WINDOW_HEIGHT) / 2;
    }

    /** A trade row's clickable area. */
    public static Rect row(int leftPos, int topPos, int slot) {
        if (slot < 0 || slot >= VISIBLE_TRADES) {
            return Rect.EMPTY;
        }
        return new Rect(leftPos + TRADE_BUTTON_X, topPos + TRADE_BUTTON_Y + slot * TRADE_BUTTON_HEIGHT,
                TRADE_BUTTON_WIDTH, TRADE_BUTTON_HEIGHT);
    }

    /** Where a row's items sit, matching how vanilla offsets them from the row. */
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

    /** Width and height of vanilla's little arrow sprite. */
    public static final int ARROW_WIDTH = 10;
    public static final int ARROW_HEIGHT = 9;

    /**
     * The arrow between a row's payments and its result.
     *
     * <p>Centred in whatever gap is actually left, which is not where vanilla puts it. Vanilla assumes two payment stacks
     * and places the arrow just past the second, so with a single payment - the normal case here - it sat hard against the
     * result with a wide empty space behind it.</p>
     *
     * @param twoPayments true when the row shows two payments, so the gap starts further right.
     */
    public static Rect rowArrow(int leftPos, int topPos, int slot, boolean twoPayments) {
        if (slot < 0 || slot >= VISIBLE_TRADES) {
            return Rect.EMPTY;
        }
        int gapStart = twoPayments
                ? rowPaymentB(leftPos, topPos, slot).right()
                : rowPaymentA(leftPos, topPos, slot).right();
        int gapEnd = rowResult(leftPos, topPos, slot).x();
        int x = gapStart + (gapEnd - gapStart - ARROW_WIDTH) / 2;
        return new Rect(x, rowItemY(topPos, slot) + 3, ARROW_WIDTH, ARROW_HEIGHT);
    }

    /**
     * The plus sign between a row's two payments.
     *
     * <p>Centred in the gap the two payment squares leave. Without it the two costs read as a list of things in the row
     * rather than as one price made of both, which is the difference between "five coins, or three Cash" and "five coins
     * <em>and</em> three Cash".</p>
     */
    public static Rect rowPaymentPlus(int leftPos, int topPos, int slot) {
        Rect a = rowPaymentA(leftPos, topPos, slot);
        Rect b = rowPaymentB(leftPos, topPos, slot);
        return plusBetween(a, b);
    }

    private static Rect item(int x, int y, int slot) {
        return slot < 0 || slot >= VISIBLE_TRADES ? Rect.EMPTY : new Rect(x, y, ITEM, ITEM);
    }

    public static Rect slotPaymentA(int leftPos, int topPos) {
        return new Rect(leftPos + SLOT_PAYMENT_A_X, topPos + SLOT_Y, ITEM, ITEM);
    }

    /**
     * Where a lone payment goes: centred between the two slot positions.
     *
     * <p>Vanilla always draws two input wells because a villager trade can want two stacks. Most shop trades want one, and
     * leaving the second well empty looked like a slot that had failed to fill. With one payment the well is drawn once,
     * midway between the two, which also brings it nearer the arrow instead of stranding it at the far left.</p>
     */
    public static Rect slotPaymentSingle(int leftPos, int topPos) {
        // Sat against the second slot's position rather than midway between the two, which still left a visible gap
        // before the arrow. Here the payment reads as feeding straight into it.
        return new Rect(leftPos + SLOT_PAYMENT_B_X, topPos + SLOT_Y, ITEM, ITEM);
    }

    /** The 18x18 well behind a slot, one pixel out from the item on every side. */
    public static Rect wellAround(Rect item) {
        return item.isEmpty() ? Rect.EMPTY
                : new Rect(item.x() - 1, item.y() - 1, item.width() + 2, item.height() + 2);
    }

    public static Rect slotPaymentB(int leftPos, int topPos) {
        return new Rect(leftPos + SLOT_PAYMENT_B_X, topPos + SLOT_Y, ITEM, ITEM);
    }

    public static Rect slotResult(int leftPos, int topPos) {
        return new Rect(leftPos + SLOT_RESULT_X, topPos + SLOT_Y, ITEM, ITEM);
    }

    /** The plus between the two big payment slots. */
    public static Rect slotPaymentPlus(int leftPos, int topPos) {
        return plusBetween(slotPaymentA(leftPos, topPos), slotPaymentB(leftPos, topPos));
    }

    /**
     * Centres the plus in the gap between two squares.
     *
     * <p>Returns empty when the gap is too narrow to hold the glyph, so a plus is never drawn overlapping the items it
     * sits between.</p>
     */
    private static Rect plusBetween(Rect a, Rect b) {
        if (a.isEmpty() || b.isEmpty()) {
            return Rect.EMPTY;
        }
        int gapStart = a.right();
        int gapWidth = b.x() - gapStart;
        if (gapWidth < PLUS_WIDTH) {
            return Rect.EMPTY;
        }
        int x = gapStart + (gapWidth - PLUS_WIDTH) / 2;
        int y = a.y() + (ITEM - PLUS_HEIGHT) / 2;
        return new Rect(x, y, PLUS_WIDTH, PLUS_HEIGHT);
    }

    public static Rect scrollTrack(int leftPos, int topPos) {
        return new Rect(leftPos + SCROLLER_X, topPos + SCROLLER_TOP, SCROLLER_WIDTH, SCROLL_TRACK_HEIGHT);
    }
}
