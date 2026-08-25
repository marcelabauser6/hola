package com.athensmc.fsshopkeepers.client.layout;

/**
 * Where every region of the editor sits, as arithmetic with no Minecraft in it.
 *
 * <p>Separated from the screen so it can be checked by a test on a plain JVM, and used by the screen for both the widgets
 * it creates and the icons it draws. That second part is the important one: the first version of this screen positioned
 * its buttons with one formula and its item icons with another, and the two drifted, which is exactly how a coin ended up
 * drawn on top of a button and a tooltip over a price field.</p>
 *
 * <p>The numbers are the Fantastic family's, matching the Crates editor: a centred panel capped at 540x320, an 8-pixel
 * gutter, 18-pixel controls, a 22-pixel row pitch, a 20-pixel title strip and a body inset 62 from the top and 28 from
 * the bottom.</p>
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

    /** An item icon, and the gap it leaves before whatever follows it. */
    public static final int ICON = 18;
    public static final int ICON_GAP = 2;

    /** A quantity stepper: minus, the number, plus. */
    public static final int STEP_BUTTON = 16;
    public static final int STEP_COUNT = 22;
    public static final int STEPPER_WIDTH = STEP_BUTTON * 2 + STEP_COUNT + ICON_GAP * 2;

    /**
     * One block of the detail column: a label line, a control under it, and a gap.
     *
     * <p>Stacking the label above its control rather than beside it is what makes the narrow right-hand column usable.
     * Side by side, a label like "Precio en Fantastic Cash" leaves under a hundred pixels for the field.</p>
     */
    public static final int DETAIL_LABEL_HEIGHT = 10;
    public static final int DETAIL_BLOCK = DETAIL_LABEL_HEIGHT + CONTROL_HEIGHT + 4;

    public static final int SAVE_WIDTH = 150;
    public static final int CLOSE_WIDTH = 80;
    public static final int DELETE_WIDTH = 96;
    private static final int FOOTER_GAP = 4;

    private final int panelWidth;
    private final int panelHeight;
    private final int leftPos;
    private final int topPos;

    public EditorGeometry(int screenWidth, int screenHeight) {
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

    public Rect panel() {
        return new Rect(leftPos, topPos, panelWidth, panelHeight);
    }

    public Rect titleBar() {
        return new Rect(leftPos, topPos, panelWidth, TITLE_HEIGHT);
    }

    public Rect tabBand() {
        return new Rect(leftPos + GUTTER, topPos + 24, panelWidth - GUTTER * 2, TAB_HEIGHT);
    }

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

    /** One tab button. The last absorbs the rounding so the row finishes flush with the band. */
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

    // ------------------------------------------------------------ trades tab

    public int tradeListWidth() {
        return Math.max(120, body().width() * 44 / 100);
    }

    public Rect tradeListColumn() {
        Rect body = body();
        return new Rect(body.x(), body.y(), tradeListWidth(), body.height());
    }

    public Rect tradeDetailColumn() {
        Rect body = body();
        int x = body.x() + tradeListWidth() + GUTTER;
        return new Rect(x, body.y(), Math.max(0, body.right() - x), body.height());
    }

    /** How many rows fit above the add and remove buttons. */
    public int tradeVisibleRows() {
        return Math.max(1, (body().height() - CONTROL_HEIGHT - 4) / ROW_PITCH);
    }

    /** The icon well at the left of a list row. */
    public Rect tradeRowIcon(int slot) {
        if (slot < 0 || slot >= tradeVisibleRows()) {
            return Rect.EMPTY;
        }
        Rect body = body();
        return new Rect(body.x(), body.y() + slot * ROW_PITCH + 1, ICON, ICON);
    }

    /** The clickable row itself, to the right of its icon and left of the scrollbar. */
    public Rect tradeRow(int slot) {
        if (slot < 0 || slot >= tradeVisibleRows()) {
            return Rect.EMPTY;
        }
        Rect body = body();
        int x = body.x() + ICON + ICON_GAP;
        int width = tradeListWidth() - ICON - ICON_GAP - 8;
        return new Rect(x, body.y() + slot * ROW_PITCH + 1, Math.max(0, width), CONTROL_HEIGHT);
    }

    /** The scrollbar track beside the trade list. */
    public Rect tradeScrollbar() {
        Rect body = body();
        return new Rect(body.x() + tradeListWidth() - 6, body.y(), 5, tradeVisibleRows() * ROW_PITCH);
    }

    public Rect addTradeButton() {
        Rect body = body();
        return new Rect(body.x(), body.bottom() - CONTROL_HEIGHT, Math.max(0, tradeListWidth() / 2 - 3),
                CONTROL_HEIGHT);
    }

    public Rect removeTradeButton() {
        Rect body = body();
        int listW = tradeListWidth();
        int x = body.x() + listW / 2 + 3;
        return new Rect(x, body.bottom() - CONTROL_HEIGHT, Math.max(0, body.x() + listW - 8 - x),
                CONTROL_HEIGHT);
    }

    // ------------------------------------------------------------ detail blocks

    /** The label line of the nth detail block. */
    public Rect detailLabel(int block) {
        Rect column = tradeDetailColumn();
        int y = column.y() + block * DETAIL_BLOCK;
        if (y + DETAIL_LABEL_HEIGHT > column.bottom()) {
            return Rect.EMPTY;
        }
        return new Rect(column.x(), y, column.width(), DETAIL_LABEL_HEIGHT);
    }

    /** The full-width control of the nth detail block. */
    public Rect detailControl(int block) {
        Rect column = tradeDetailColumn();
        int y = column.y() + block * DETAIL_BLOCK + DETAIL_LABEL_HEIGHT;
        if (y + CONTROL_HEIGHT > column.bottom()) {
            return Rect.EMPTY;
        }
        return new Rect(column.x(), y, column.width(), CONTROL_HEIGHT);
    }

    /** The icon at the left of a block's control row. */
    public Rect detailIcon(int block) {
        Rect control = detailControl(block);
        return control.isEmpty() ? Rect.EMPTY : new Rect(control.x(), control.y(), ICON, ICON);
    }

    /** A block's control, shortened to leave room for its icon. */
    public Rect detailControlAfterIcon(int block) {
        Rect control = detailControl(block);
        if (control.isEmpty()) {
            return Rect.EMPTY;
        }
        int x = control.x() + ICON + ICON_GAP;
        return new Rect(x, control.y(), Math.max(0, control.right() - x), CONTROL_HEIGHT);
    }

    /** A block's control, shortened to leave room for both an icon and a stepper. */
    public Rect detailControlAfterIconBeforeStepper(int block) {
        Rect afterIcon = detailControlAfterIcon(block);
        if (afterIcon.isEmpty()) {
            return Rect.EMPTY;
        }
        int width = afterIcon.width() - STEPPER_WIDTH - ICON_GAP;
        return width <= 0 ? Rect.EMPTY : new Rect(afterIcon.x(), afterIcon.y(), width, CONTROL_HEIGHT);
    }

    /**
     * The pieces of a quantity stepper, right-aligned in a block's control row.
     *
     * <p>Right-aligned so the steppers of the payment blocks line up with each other whatever the label beside them
     * says.</p>
     */
    public Rect stepperMinus(int block) {
        Rect control = detailControl(block);
        if (control.isEmpty()) {
            return Rect.EMPTY;
        }
        return new Rect(control.right() - STEPPER_WIDTH, control.y(), STEP_BUTTON, CONTROL_HEIGHT);
    }

    public Rect stepperCount(int block) {
        Rect minus = stepperMinus(block);
        return minus.isEmpty() ? Rect.EMPTY
                : new Rect(minus.right() + ICON_GAP, minus.y(), STEP_COUNT, CONTROL_HEIGHT);
    }

    public Rect stepperPlus(int block) {
        Rect count = stepperCount(block);
        return count.isEmpty() ? Rect.EMPTY
                : new Rect(count.right() + ICON_GAP, count.y(), STEP_BUTTON, CONTROL_HEIGHT);
    }

    /** The status line under the last detail block. */
    public Rect detailStatus(int afterBlocks) {
        Rect column = tradeDetailColumn();
        int y = column.y() + afterBlocks * DETAIL_BLOCK + 2;
        if (y + DETAIL_LABEL_HEIGHT > column.bottom()) {
            return Rect.EMPTY;
        }
        return new Rect(column.x(), y, column.width(), DETAIL_LABEL_HEIGHT);
    }

    // ------------------------------------------------------------ footer

    private int footerCloseWidth() {
        int available = panelWidth - GUTTER * 2;
        return Math.max(1, Math.min(CLOSE_WIDTH, available * 22 / 100));
    }

    private int footerDeleteWidth() {
        int available = panelWidth - GUTTER * 2;
        return Math.max(1, Math.min(DELETE_WIDTH, available * 26 / 100));
    }

    /**
     * How wide save gets: whatever the others left, capped at its natural width.
     *
     * <p>Deriving it from the remainder rather than from its own share is what makes the three buttons provably
     * non-overlapping, however narrow the panel.</p>
     */
    private int footerSaveWidth() {
        int available = panelWidth - GUTTER * 2;
        int remaining = available - footerCloseWidth() - footerDeleteWidth() - FOOTER_GAP * 2;
        return Math.max(1, Math.min(SAVE_WIDTH, remaining));
    }

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

    // ------------------------------------------------------------ appearance tab

    /** The name field spans the body, above the two columns. */
    public Rect nameLabel() {
        Rect body = body();
        return new Rect(body.x(), body.y(), body.width(), DETAIL_LABEL_HEIGHT);
    }

    public Rect nameField() {
        Rect body = body();
        return new Rect(body.x(), body.y() + DETAIL_LABEL_HEIGHT + 1, body.width(), CONTROL_HEIGHT);
    }

    /** The row of body-kind buttons under the name. */
    public Rect kindButton(int index, int count) {
        if (count <= 0 || index < 0 || index >= count) {
            return Rect.EMPTY;
        }
        Rect body = body();
        int y = nameField().bottom() + 4;
        int each = (body.width() - 2 * (count - 1)) / count;
        if (each <= 0) {
            return Rect.EMPTY;
        }
        int x = body.x() + index * (each + 2);
        int width = index == count - 1 ? body.right() - x : each;
        return new Rect(x, y, Math.max(0, width), CONTROL_HEIGHT);
    }

    private int appearanceColumnsTop() {
        return kindButton(0, 3).bottom() + 4;
    }

    public int mobListWidth() {
        return Math.max(130, body().width() * 52 / 100);
    }

    public Rect mobSearchField() {
        Rect body = body();
        return new Rect(body.x(), appearanceColumnsTop(), mobListWidth() - 8, CONTROL_HEIGHT);
    }

    public Rect mobListColumn() {
        Rect body = body();
        int top = mobSearchField().bottom() + 4;
        return new Rect(body.x(), top, mobListWidth(), Math.max(0, body.bottom() - top));
    }

    public int mobVisibleRows() {
        Rect column = mobListColumn();
        return Math.max(0, column.height() / ROW_PITCH);
    }

    public Rect mobRow(int slot) {
        if (slot < 0 || slot >= mobVisibleRows()) {
            return Rect.EMPTY;
        }
        Rect column = mobListColumn();
        return new Rect(column.x(), column.y() + slot * ROW_PITCH, Math.max(0, column.width() - 8),
                CONTROL_HEIGHT);
    }

    public Rect mobScrollbar() {
        Rect column = mobListColumn();
        return new Rect(column.right() - 6, column.y(), 5, mobVisibleRows() * ROW_PITCH);
    }

    public Rect variantsColumn() {
        Rect body = body();
        int x = body.x() + mobListWidth() + GUTTER;
        int top = appearanceColumnsTop();
        return new Rect(x, top, Math.max(0, body.right() - x), Math.max(0, body.bottom() - top));
    }

    public int variantVisibleRows() {
        Rect column = variantsColumn();
        return Math.max(0, (column.height() - DETAIL_LABEL_HEIGHT - 2) / ROW_PITCH);
    }

    public Rect variantsHeader() {
        Rect column = variantsColumn();
        return column.isEmpty() ? Rect.EMPTY
                : new Rect(column.x(), column.y(), column.width(), DETAIL_LABEL_HEIGHT);
    }

    public Rect variantRow(int slot) {
        if (slot < 0 || slot >= variantVisibleRows()) {
            return Rect.EMPTY;
        }
        Rect column = variantsColumn();
        int y = column.y() + DETAIL_LABEL_HEIGHT + 2 + slot * ROW_PITCH;
        return new Rect(column.x(), y, column.width(), CONTROL_HEIGHT);
    }

    // ------------------------------------------------------------ settings tab

    /**
     * A settings block: a label, its control, and a line explaining what the control does.
     *
     * <p>Taller than a detail block because of that third line. Every setting on this tab needs saying in words - an
     * operator should not have to guess what "traspaso" does or what happens if the account field is left empty.</p>
     */
    public static final int SETTING_NOTE_HEIGHT = 9;
    public static final int SETTING_BLOCK = DETAIL_LABEL_HEIGHT + CONTROL_HEIGHT + SETTING_NOTE_HEIGHT + 5;

    public Rect settingLabel(int block) {
        Rect body = body();
        int y = body.y() + block * SETTING_BLOCK;
        return y + DETAIL_LABEL_HEIGHT > body.bottom() ? Rect.EMPTY
                : new Rect(body.x(), y, body.width(), DETAIL_LABEL_HEIGHT);
    }

    public Rect settingControl(int block) {
        Rect body = body();
        int y = body.y() + block * SETTING_BLOCK + DETAIL_LABEL_HEIGHT;
        return y + CONTROL_HEIGHT > body.bottom() ? Rect.EMPTY
                : new Rect(body.x(), y, body.width(), CONTROL_HEIGHT);
    }

    /** The explanation under a setting's control. */
    public Rect settingNote(int block) {
        Rect body = body();
        int y = body.y() + block * SETTING_BLOCK + DETAIL_LABEL_HEIGHT + CONTROL_HEIGHT + 1;
        return y + SETTING_NOTE_HEIGHT > body.bottom() ? Rect.EMPTY
                : new Rect(body.x(), y, body.width(), SETTING_NOTE_HEIGHT);
    }

    /** How many setting blocks fit in the body. */
    public int settingBlocks() {
        return Math.max(0, body().height() / SETTING_BLOCK);
    }
}
