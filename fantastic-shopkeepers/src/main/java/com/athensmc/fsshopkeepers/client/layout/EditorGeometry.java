package com.athensmc.fsshopkeepers.client.layout;

/**
 * Where the editor's regions sit, as arithmetic with no Minecraft in it.
 *
 * <p>Separated from the screen so the geometry can be checked by a test on a plain JVM. That is the only way to be sure
 * a control is not drawn over another one: a screen that looks right at the size it was built at will happily stack the
 * footer on the last list row at 640x480, and nobody notices until a player screenshots it.</p>
 *
 * <p>The numbers are the Fantastic family's: a centred panel capped at 540x320, an 8-pixel gutter, an 18-pixel control
 * height, a 22-pixel row pitch, a 20-pixel title strip, and a body inset 62 from the top and 28 from the bottom of the
 * panel.</p>
 */
public final class EditorGeometry {

    public static final int MAX_PANEL_WIDTH = 540;
    public static final int MAX_PANEL_HEIGHT = 320;
    public static final int GUTTER = 8;
    public static final int TITLE_HEIGHT = 20;
    public static final int TAB_HEIGHT = 18;
    public static final int CONTROL_HEIGHT = 18;
    public static final int ROW_PITCH = 22;
    public static final int BODY_TOP_INSET = 62;
    public static final int BODY_BOTTOM_INSET = 28;

    /** Footer button widths, right to left: save, then delete and close on the left. */
    public static final int SAVE_WIDTH = 150;
    public static final int CLOSE_WIDTH = 80;
    public static final int DELETE_WIDTH = 96;

    private final int screenWidth;
    private final int screenHeight;
    private final int panelWidth;
    private final int panelHeight;
    private final int leftPos;
    private final int topPos;

    public EditorGeometry(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.panelWidth = Math.min(screenWidth - 16, MAX_PANEL_WIDTH);
        this.panelHeight = Math.min(screenHeight - 16, MAX_PANEL_HEIGHT);
        this.leftPos = (screenWidth - panelWidth) / 2;
        this.topPos = (screenHeight - panelHeight) / 2;
    }

    public int panelWidth() {
        return panelWidth;
    }

    public int panelHeight() {
        return panelHeight;
    }

    public int leftPos() {
        return leftPos;
    }

    public int topPos() {
        return topPos;
    }

    /** The whole window. */
    public Rect panel() {
        return new Rect(leftPos, topPos, panelWidth, panelHeight);
    }

    /** The title strip along the top. */
    public Rect titleBar() {
        return new Rect(leftPos, topPos, panelWidth, TITLE_HEIGHT);
    }

    /** The band the tab buttons sit in. */
    public Rect tabBand() {
        return new Rect(leftPos + GUTTER, topPos + 24, panelWidth - GUTTER * 2, TAB_HEIGHT);
    }

    /** The rule under the tabs, where the help line is written. */
    public int separatorY() {
        return topPos + 24 + TAB_HEIGHT + 4;
    }

    public Rect helpLine() {
        return new Rect(leftPos + GUTTER, separatorY() + 4, panelWidth - GUTTER * 2, 9);
    }

    public Rect body() {
        return new Rect(leftPos + GUTTER, topPos + BODY_TOP_INSET, panelWidth - GUTTER * 2,
                Math.max(0, panelHeight - BODY_TOP_INSET - BODY_BOTTOM_INSET));
    }

    public Rect footer() {
        return new Rect(leftPos + GUTTER, topPos + panelHeight - 24, panelWidth - GUTTER * 2, CONTROL_HEIGHT);
    }

    /**
     * One tab button.
     *
     * <p>Tabs share the band evenly with a two-pixel gap, and the last one absorbs the rounding remainder so the row
     * finishes flush with the band rather than leaving a ragged edge.</p>
     */
    public Rect tab(int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            return Rect.EMPTY;
        }
        Rect band = tabBand();
        int each = (band.width() - 2 * (count - 1)) / count;
        if (each <= 0) {
            return Rect.EMPTY;
        }
        int x = band.x() + index * (each + 2);
        int width = index == count - 1 ? band.right() - x : each;
        return new Rect(x, band.y(), Math.max(0, width), TAB_HEIGHT);
    }

    /** The trades tab's left column, holding the list of rows. */
    public int tradeListWidth() {
        return Math.max(120, body().width() * 46 / 100);
    }

    public Rect tradeListColumn() {
        Rect body = body();
        return new Rect(body.x(), body.y(), tradeListWidth(), body.height());
    }

    /** The trades tab's right column, holding the selected row's details. */
    public Rect tradeDetailColumn() {
        Rect body = body();
        int x = body.x() + tradeListWidth() + GUTTER;
        return new Rect(x, body.y(), Math.max(0, body.right() - x), body.height());
    }

    /** How many trade rows fit above the add/remove buttons. */
    public int tradeVisibleRows() {
        return Math.max(1, (body().height() - 24) / ROW_PITCH);
    }

    /** One trade row in the list. Room is left on the left for the item icon. */
    public Rect tradeRow(int slot) {
        if (slot < 0 || slot >= tradeVisibleRows()) {
            return Rect.EMPTY;
        }
        Rect body = body();
        return new Rect(body.x() + 20, body.y() + slot * ROW_PITCH, tradeListWidth() - 20 - 6,
                CONTROL_HEIGHT + 2);
    }

    /** The icon well beside a trade row. */
    public Rect tradeRowIcon(int slot) {
        if (slot < 0 || slot >= tradeVisibleRows()) {
            return Rect.EMPTY;
        }
        Rect body = body();
        return new Rect(body.x(), body.y() + slot * ROW_PITCH + 1, 18, 18);
    }

    /** The add and remove buttons at the foot of the trade list. */
    public Rect addTradeButton() {
        Rect body = body();
        return new Rect(body.x(), body.y() + body.height() - CONTROL_HEIGHT,
                Math.max(0, tradeListWidth() / 2 - 2), CONTROL_HEIGHT);
    }

    public Rect removeTradeButton() {
        Rect body = body();
        int listW = tradeListWidth();
        return new Rect(body.x() + listW / 2 + 2, body.y() + body.height() - CONTROL_HEIGHT,
                Math.max(0, listW / 2 - 4), CONTROL_HEIGHT);
    }

    /** Gap between the footer's buttons. */
    private static final int FOOTER_GAP = 4;

    /**
     * How wide the close button gets.
     *
     * <p>Capped at its natural width but allowed to shrink to a share of the panel, because on a narrow window the three
     * footer buttons at their natural widths add up to more than the panel has. Shrinking is better than overflowing:
     * a narrower "Cerrar" is still readable, whereas a button drawn past the panel edge is unclickable.</p>
     */
    private int footerCloseWidth() {
        int available = panelWidth - GUTTER * 2;
        return Math.max(1, Math.min(CLOSE_WIDTH, available * 22 / 100));
    }

    private int footerDeleteWidth() {
        int available = panelWidth - GUTTER * 2;
        return Math.max(1, Math.min(DELETE_WIDTH, available * 26 / 100));
    }

    /**
     * How wide the save button gets.
     *
     * <p>Whatever is left after the other two and the gaps, capped at its natural width. Deriving it from the remainder
     * rather than from its own share is what makes the three buttons provably non-overlapping: the save button starts at
     * the right edge and can never reach further left than the space the others did not take.</p>
     */
    private int footerSaveWidth() {
        int available = panelWidth - GUTTER * 2;
        int remaining = available - footerCloseWidth() - footerDeleteWidth() - FOOTER_GAP * 2;
        return Math.max(1, Math.min(SAVE_WIDTH, remaining));
    }

    /** Save, right-aligned against the panel's gutter. */
    public Rect saveButton() {
        int width = footerSaveWidth();
        return new Rect(leftPos + panelWidth - GUTTER - width, topPos + panelHeight - 24, width,
                CONTROL_HEIGHT);
    }

    public Rect closeButton() {
        return new Rect(leftPos + GUTTER, topPos + panelHeight - 24, footerCloseWidth(), CONTROL_HEIGHT);
    }

    public Rect deleteButton() {
        return new Rect(leftPos + GUTTER + footerCloseWidth() + FOOTER_GAP, topPos + panelHeight - 24,
                footerDeleteWidth(), CONTROL_HEIGHT);
    }

    /** The appearance tab's mob list column. */
    public int mobListWidth() {
        return Math.max(130, body().width() * 52 / 100);
    }

    public Rect mobListColumn() {
        Rect body = body();
        return new Rect(body.x(), body.y() + 32 + ROW_PITCH, mobListWidth(),
                Math.max(0, body.bottom() - (body.y() + 32 + ROW_PITCH)));
    }

    /** The appearance tab's variants column. */
    public Rect variantsColumn() {
        Rect body = body();
        int x = body.x() + mobListWidth() + GUTTER;
        return new Rect(x, body.y() + 32 + ROW_PITCH, Math.max(0, body.right() - x),
                Math.max(0, body.bottom() - (body.y() + 32 + ROW_PITCH)));
    }
}
