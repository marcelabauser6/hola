package com.athensmc.fsshopkeepers.client.layout;

/**
 * Where every control on the shop editor goes.
 *
 * <p>All of the screen's geometry, worked out from nothing but the window size, and none of its drawing. Keeping the
 * two apart is what lets {@code EditorLayoutTest} assert that no two controls share a pixel at a range of window
 * sizes, which is the only way to be sure of it - a screen that looks fine at the size it was built at will happily
 * stack a price field on top of a delete button at 1024x768.</p>
 *
 * <p>The screen is a stack of bands: a header naming the shop, a row of tabs, the tab's content, a line of help text
 * and a footer of actions. Bands are computed from the outside in and the content band takes whatever is left, so
 * shrinking the window shrinks the list rather than pushing the footer off the bottom.</p>
 *
 * <p>Within a trade row the cells are allocated from both ends towards the middle: the icon from the left, the
 * delete button, payment slots, price field and quantity stepper from the right, and the item's name takes the gap
 * that remains. The name is the only elastic cell because it is the only one that can be usefully truncated - a
 * price field narrower than its digits is unusable, whereas a shortened item name is still a label.</p>
 */
public final class EditorLayout {

    /** Space left around the whole screen. */
    public static final int MARGIN = 8;

    /** Gap between bands and between cells. */
    public static final int GAP = 4;

    public static final int HEADER_HEIGHT = 28;
    public static final int TABS_HEIGHT = 20;
    public static final int ACTION_BAR_HEIGHT = 20;
    public static final int HELP_HEIGHT = 12;
    public static final int FOOTER_HEIGHT = 24;

    /** Height of one trade row, tall enough for an 18px item icon plus breathing room. */
    public static final int ROW_HEIGHT = 26;

    /** Vertical space between rows, so two rows never read as one. */
    public static final int ROW_GAP = 2;

    /** Width of the scrollbar reserved on the right of any scrolling list. */
    public static final int SCROLLBAR_WIDTH = 6;

    private static final int ICON_WIDTH = 20;
    private static final int DELETE_WIDTH = 18;
    private static final int PAY_SLOT_WIDTH = 20;
    private static final int STEP_BUTTON_WIDTH = 16;
    private static final int COUNT_WIDTH = 22;

    /** Preferred and smallest usable width of the price field. */
    private static final int PRICE_WIDTH = 74;
    private static final int PRICE_MIN_WIDTH = 46;

    /** Below this the item name is dropped rather than drawn as one letter and an ellipsis. */
    private static final int NAME_MIN_WIDTH = 40;

    /** Mob buttons in the appearance tab. */
    private static final int MOB_CELL_WIDTH = 104;
    private static final int MOB_CELL_HEIGHT = 22;

    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int SAVE_WIDTH = 92;
    private static final int CANCEL_WIDTH = 78;
    private static final int DELETE_BUTTON_WIDTH = 82;

    private final int screenWidth;
    private final int screenHeight;

    private final Rect header;
    private final Rect tabs;
    private final Rect actionBar;
    private final Rect rowsArea;
    private final Rect help;
    private final Rect footer;

    public EditorLayout(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        int contentLeft = MARGIN;
        int contentWidth = Math.max(0, screenWidth - MARGIN * 2);

        this.header = new Rect(contentLeft, MARGIN, contentWidth, HEADER_HEIGHT);
        this.tabs = new Rect(contentLeft, header.bottom() + GAP, contentWidth, TABS_HEIGHT);
        this.footer = new Rect(contentLeft, Math.max(tabs.bottom() + GAP,
                screenHeight - MARGIN - FOOTER_HEIGHT), contentWidth, FOOTER_HEIGHT);
        this.help = new Rect(contentLeft, Math.max(tabs.bottom() + GAP, footer.y() - GAP - HELP_HEIGHT),
                contentWidth, HELP_HEIGHT);

        int contentTop = tabs.bottom() + GAP;
        int contentBottom = help.y() - GAP;
        int contentHeight = Math.max(0, contentBottom - contentTop);
        // The action bar only exists if there is room for it as well as at least one row underneath.
        boolean roomForActions = contentHeight >= ACTION_BAR_HEIGHT + GAP + ROW_HEIGHT;
        this.actionBar = roomForActions
                ? new Rect(contentLeft, contentTop, contentWidth, ACTION_BAR_HEIGHT)
                : Rect.EMPTY;
        int rowsTop = roomForActions ? actionBar.bottom() + GAP : contentTop;
        this.rowsArea = new Rect(contentLeft, rowsTop, contentWidth, Math.max(0, contentBottom - rowsTop));
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    /** The band naming the shop and its owner. */
    public Rect header() {
        return header;
    }

    /** The band holding the tab buttons. */
    public Rect tabs() {
        return tabs;
    }

    /** The band above the list, holding buttons that act on the whole list. */
    public Rect actionBar() {
        return actionBar;
    }

    /** The scrolling area holding the tab's rows. */
    public Rect rowsArea() {
        return rowsArea;
    }

    /** The single line of help text explaining whatever is under the cursor. */
    public Rect help() {
        return help;
    }

    /** The band holding save, cancel and delete. */
    public Rect footer() {
        return footer;
    }

    /**
     * One tab button.
     *
     * <p>Tabs share the band evenly with a gap between them. An even split rather than text-width sizing, because
     * tab labels change with the shop type and tabs that move as you switch between shops are tabs you have to look
     * for every time.</p>
     */
    public Rect tab(int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            return Rect.EMPTY;
        }
        int totalGap = GAP * (count - 1);
        int each = (tabs.width() - totalGap) / count;
        if (each <= 0) {
            return Rect.EMPTY;
        }
        // The last tab absorbs the rounding remainder so the row of tabs ends flush with the band.
        int x = tabs.x() + index * (each + GAP);
        int width = index == count - 1 ? tabs.right() - x : each;
        return new Rect(x, tabs.y(), Math.max(0, width), tabs.height());
    }

    /** How many rows fit in the list without the last one being cut off. */
    public int visibleRows() {
        if (rowsArea.height() <= 0) {
            return 0;
        }
        return Math.max(0, (rowsArea.height() + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    /**
     * The nth visible row of the list.
     *
     * <p>Indexed by position on screen, not by position in the shop's list of trades. Scrolling is the caller's
     * business: it decides which trade is drawn in which slot, and this only says where the slots are.</p>
     */
    public Rect row(int visibleIndex) {
        if (visibleIndex < 0 || visibleIndex >= visibleRows()) {
            return Rect.EMPTY;
        }
        int y = rowsArea.y() + visibleIndex * (ROW_HEIGHT + ROW_GAP);
        return new Rect(rowsArea.x(), y, Math.max(0, rowsArea.width() - SCROLLBAR_WIDTH - GAP), ROW_HEIGHT);
    }

    /** The scrollbar track beside the list, or empty when everything fits. */
    public Rect scrollbar(int totalRows) {
        if (rowsArea.isEmpty() || totalRows <= visibleRows()) {
            return Rect.EMPTY;
        }
        return new Rect(rowsArea.right() - SCROLLBAR_WIDTH, rowsArea.y(), SCROLLBAR_WIDTH, rowsArea.height());
    }

    /** The moving handle inside the scrollbar track. */
    public Rect scrollHandle(int totalRows, int firstVisibleRow) {
        Rect track = scrollbar(totalRows);
        if (track.isEmpty()) {
            return Rect.EMPTY;
        }
        int visible = visibleRows();
        int handleHeight = Math.max(12, track.height() * visible / totalRows);
        int scrollable = Math.max(1, totalRows - visible);
        int travel = Math.max(0, track.height() - handleHeight);
        int offset = travel * Math.min(firstVisibleRow, scrollable) / scrollable;
        return new Rect(track.x(), track.y() + offset, track.width(), handleHeight);
    }

    /** The cells of one trade row, all guaranteed not to overlap each other. */
    public record TradeRowCells(Rect icon, Rect name, Rect minus, Rect count, Rect plus, Rect price,
            Rect pay1, Rect pay2, Rect delete) {
    }

    /**
     * Divides a trade row into its controls.
     *
     * <p>Fixed-size cells are placed first, from both edges inwards, and the item's name takes what is left. When the
     * window is too narrow for everything, the price field gives up width down to a floor and then the name is
     * dropped entirely - in that order, because a row you cannot type a price into is useless while a row without a
     * visible name is merely terse, and the icon still identifies the item.</p>
     */
    public TradeRowCells tradeRow(Rect row) {
        if (row.isEmpty()) {
            return new TradeRowCells(Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY,
                    Rect.EMPTY, Rect.EMPTY, Rect.EMPTY);
        }
        int pad = GAP;
        int left = row.x() + pad;
        int rightEdge = row.right() - pad;

        Rect icon = centered(left, row, ICON_WIDTH, 18);
        int nameStart = icon.right() + GAP;

        // Everything right of the name, measured before anything is positioned.
        int fixedRight = DELETE_WIDTH + GAP + PAY_SLOT_WIDTH + GAP + PAY_SLOT_WIDTH + GAP
                + STEP_BUTTON_WIDTH + GAP + COUNT_WIDTH + GAP + STEP_BUTTON_WIDTH + GAP;
        int priceWidth = PRICE_WIDTH;
        int nameWidth = rightEdge - fixedRight - priceWidth - GAP - nameStart;
        if (nameWidth < NAME_MIN_WIDTH) {
            int deficit = NAME_MIN_WIDTH - nameWidth;
            priceWidth = Math.max(PRICE_MIN_WIDTH, priceWidth - deficit);
            nameWidth = rightEdge - fixedRight - priceWidth - GAP - nameStart;
        }
        boolean showName = nameWidth >= NAME_MIN_WIDTH;

        int cursor = rightEdge;
        Rect delete = centered(cursor - DELETE_WIDTH, row, DELETE_WIDTH, 18);
        cursor = delete.x() - GAP;
        Rect pay2 = centered(cursor - PAY_SLOT_WIDTH, row, PAY_SLOT_WIDTH, 18);
        cursor = pay2.x() - GAP;
        Rect pay1 = centered(cursor - PAY_SLOT_WIDTH, row, PAY_SLOT_WIDTH, 18);
        cursor = pay1.x() - GAP;
        Rect price = centered(cursor - priceWidth, row, priceWidth, 18);
        cursor = price.x() - GAP;
        Rect plus = centered(cursor - STEP_BUTTON_WIDTH, row, STEP_BUTTON_WIDTH, 18);
        cursor = plus.x() - GAP;
        Rect count = centered(cursor - COUNT_WIDTH, row, COUNT_WIDTH, 18);
        cursor = count.x() - GAP;
        Rect minus = centered(cursor - STEP_BUTTON_WIDTH, row, STEP_BUTTON_WIDTH, 18);

        Rect name = showName
                ? centered(nameStart, row, Math.min(nameWidth, Math.max(0, minus.x() - GAP - nameStart)), 10)
                : Rect.EMPTY;

        return new TradeRowCells(icon, name, minus, count, plus, price, pay1, pay2, delete);
    }

    private Rect centered(int x, Rect row, int width, int height) {
        if (width <= 0) {
            return Rect.EMPTY;
        }
        int y = row.y() + (row.height() - height) / 2;
        return new Rect(x, y, width, height);
    }

    /** How many mob buttons fit across the appearance tab. */
    public int mobColumns() {
        int usable = rowsArea.width() - SCROLLBAR_WIDTH - GAP;
        return Math.max(1, (usable + GAP) / (MOB_CELL_WIDTH + GAP));
    }

    /** How many rows of mob buttons are visible. */
    public int mobRows() {
        if (rowsArea.height() <= 0) {
            return 0;
        }
        return Math.max(0, (rowsArea.height() + GAP) / (MOB_CELL_HEIGHT + GAP));
    }

    /**
     * One mob button in the appearance grid.
     *
     * <p>A grid rather than a list, because a server may allow forty creatures and a one-per-row list of forty is a
     * lot of scrolling to find a pig.</p>
     */
    public Rect mobCell(int visibleIndex) {
        int columns = mobColumns();
        int rows = mobRows();
        if (columns <= 0 || rows <= 0 || visibleIndex < 0 || visibleIndex >= columns * rows) {
            return Rect.EMPTY;
        }
        int column = visibleIndex % columns;
        int rowIndex = visibleIndex / columns;
        int usable = rowsArea.width() - SCROLLBAR_WIDTH - GAP;
        int cellWidth = Math.max(1, (usable - GAP * (columns - 1)) / columns);
        int x = rowsArea.x() + column * (cellWidth + GAP);
        int y = rowsArea.y() + rowIndex * (MOB_CELL_HEIGHT + GAP);
        return new Rect(x, y, cellWidth, MOB_CELL_HEIGHT);
    }

    /** A labelled field in the settings tab: the label on the left, the control on the right. */
    public record SettingRow(Rect label, Rect control) {
    }

    /**
     * The nth settings row.
     *
     * <p>The label takes the left 45% and the control the right 55%, with a gap between. A fixed split rather than
     * text-width sizing so that the controls of every row line up vertically, which is what makes a column of fields
     * scannable.</p>
     */
    public SettingRow settingRow(int visibleIndex) {
        Rect row = row(visibleIndex);
        if (row.isEmpty()) {
            return new SettingRow(Rect.EMPTY, Rect.EMPTY);
        }
        int pad = GAP;
        int usable = row.width() - pad * 2;
        int labelWidth = Math.max(0, usable * 45 / 100 - GAP / 2);
        int controlWidth = Math.max(0, usable - labelWidth - GAP);
        Rect label = centered(row.x() + pad, row, labelWidth, 10);
        Rect control = centered(row.x() + pad + labelWidth + GAP, row, controlWidth, 18);
        return new SettingRow(label, control);
    }

    /** The footer's buttons, from the right edge inwards. */
    public record FooterButtons(Rect save, Rect cancel, Rect delete, Rect balance) {
    }

    /**
     * Places the footer's controls.
     *
     * <p>Save is furthest right because it is the action taken most; delete is separated from it by cancel, so the
     * irreversible button is never the neighbour of the routine one.</p>
     */
    public FooterButtons footerButtons() {
        if (footer.isEmpty()) {
            return new FooterButtons(Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY);
        }
        int cursor = footer.right();
        Rect save = centered(cursor - SAVE_WIDTH, footer, SAVE_WIDTH, FOOTER_BUTTON_HEIGHT);
        cursor = save.x() - GAP;
        Rect cancel = centered(cursor - CANCEL_WIDTH, footer, CANCEL_WIDTH, FOOTER_BUTTON_HEIGHT);
        cursor = cancel.x() - GAP;
        Rect delete = centered(cursor - DELETE_BUTTON_WIDTH, footer, DELETE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT);

        int balanceWidth = Math.max(0, delete.x() - GAP - footer.x());
        Rect balance = balanceWidth <= 0 ? Rect.EMPTY : centered(footer.x(), footer, balanceWidth, 10);

        boolean buttonsFit = delete.x() >= footer.x();
        if (!buttonsFit) {
            // Too narrow for all three: the balance label is what goes, since it is information and they are
            // actions.
            return new FooterButtons(save, cancel, delete, Rect.EMPTY);
        }
        return new FooterButtons(save, cancel, delete, balance);
    }

    /** The button that adds a new trade row, at the left of the action bar. */
    public Rect addTradeButton() {
        if (actionBar.isEmpty()) {
            return Rect.EMPTY;
        }
        int width = Math.min(120, Math.max(0, actionBar.width() / 2));
        return new Rect(actionBar.x(), actionBar.y(), width, actionBar.height());
    }

    /** The label at the right of the action bar saying how many trades there are. */
    public Rect tradeCountLabel() {
        if (actionBar.isEmpty()) {
            return Rect.EMPTY;
        }
        Rect add = addTradeButton();
        int x = add.right() + GAP;
        int width = Math.max(0, actionBar.right() - x);
        return width <= 0 ? Rect.EMPTY
                : new Rect(x, actionBar.y() + (actionBar.height() - 10) / 2, width, 10);
    }
}
