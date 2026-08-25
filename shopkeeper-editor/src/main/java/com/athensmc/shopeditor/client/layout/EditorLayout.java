package com.athensmc.shopeditor.client.layout;

import com.athensmc.shopeditor.client.layout.ScreenLayout.Rect;
import com.athensmc.shopeditor.client.layout.ScreenLayout.Regions;

/**
 * The geometry of the shop editor's screens.
 *
 * <p>Two screens, both filling the viewport: the <strong>trade list</strong>, where an admin sees what the NPC
 * sells and for how much, and the <strong>item picker</strong>, where they choose what to sell. Neither is a
 * fixed-size panel, because both are lists that grow - a shop can have dozens of trades, and the picker has to
 * show a grid of every item in the game.</p>
 *
 * <p>Every control gets a help line, and the help has <em>reserved space</em> rather than a tooltip under the
 * cursor. On a dense grid a tooltip covers the very items being compared, and two near the edge overlap each
 * other; an area nothing else may draw in cannot. On a wide window it is a column down the right, and on a
 * narrow one a band across the bottom, because a column of ninety pixels holds about four words a line.</p>
 *
 * <p>Deliberately free of any Minecraft import, so the layout guard runs on a plain JVM and every rectangle
 * can be checked for overlap before the game is ever started.</p>
 */
public final class EditorLayout {

    /** Gap between the panel and the edge of the screen. */
    public static final int MARGIN = 6;

    /** Inner padding of a content band. */
    public static final int PAD = 8;

    /** Gap between the working area and the help area. */
    public static final int HELP_GAP = 6;

    /** One row of the trade list: an item, its name, its price, and the buttons that act on it. */
    public static final int TRADE_ROW_H = 22;

    /** One line of text. */
    public static final int LINE_H = 11;

    /** A cell in the item grid, big enough for a 16px item with room to highlight it. */
    public static final int ITEM_CELL = 20;

    /** Trade list: title, the toolbar, and a footer with save and close. */
    public static final int LIST_HEADER = 24;
    public static final int LIST_TOOLBAR = 26;
    public static final int LIST_FOOTER = 30;

    /** Item picker: title, the source switch and search box, and a footer. */
    public static final int PICKER_HEADER = 24;
    public static final int PICKER_TOOLBAR = 26;
    public static final int PICKER_FOOTER = 30;

    /** Narrowest the help column may be before an explanation stops fitting in it. */
    public static final int HELP_COLUMN_MIN = 130;

    /** Widest it gets. Past this the lines are long enough to lose your place in. */
    public static final int HELP_COLUMN_MAX = 220;

    /** Height of the help band used when the window is too narrow for a column. */
    public static final int HELP_BAND_H = 46;

    /** The working area and the reserved help area, which never overlap. */
    public record HelpSplit(Rect main, Rect help, boolean helpIsColumn) {
    }

    /** The two halves of the picker's toolbar: the source switch, and the search box. */
    public record Toolbar(Rect sourceSwitch, Rect search) {
    }

    private EditorLayout() {
    }

    public static Rect panel(int viewportWidth, int viewportHeight) {
        int width = Math.max(1, viewportWidth - MARGIN * 2);
        int height = Math.max(1, viewportHeight - MARGIN * 2);
        return new Rect(MARGIN, MARGIN, width, height);
    }

    public static Regions tradeList(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                LIST_HEADER, LIST_TOOLBAR, LIST_FOOTER);
    }

    public static Regions itemPicker(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                PICKER_HEADER, PICKER_TOOLBAR, PICKER_FOOTER);
    }

    /**
     * Splits a content band into the working area and the reserved help area.
     *
     * <p>The two tile the band with exactly one gap between them and never intersect, which is what lets the
     * help be drawn unconditionally instead of suppressed whenever it might collide.</p>
     */
    public static HelpSplit withHelp(Rect content) {
        Rect inner = content.inset(PAD);
        if (inner.isEmpty()) {
            return new HelpSplit(inner, inner, false);
        }

        // A column needs room for itself, the gap, and a working area at least as wide again - otherwise the
        // "main" area ends up narrower than the help beside it, which is the wrong way round.
        if (inner.width() >= HELP_COLUMN_MIN * 2 + HELP_GAP) {
            int helpWidth = ScreenLayout.clamp(inner.width() / 3, HELP_COLUMN_MIN, HELP_COLUMN_MAX);
            int mainWidth = inner.width() - helpWidth - HELP_GAP;
            return new HelpSplit(
                    new Rect(inner.x(), inner.y(), mainWidth, inner.height()),
                    new Rect(inner.x() + mainWidth + HELP_GAP, inner.y(), helpWidth, inner.height()),
                    true);
        }

        // Narrow window: the help becomes a band along the bottom, capped at half the band so a short window
        // still leaves rows visible above it.
        int helpHeight = Math.min(HELP_BAND_H, Math.max(0, inner.height() / 2));
        int mainHeight = Math.max(0, inner.height() - helpHeight - HELP_GAP);
        return new HelpSplit(
                new Rect(inner.x(), inner.y(), inner.width(), mainHeight),
                new Rect(inner.x(), inner.y() + mainHeight + HELP_GAP, inner.width(),
                        Math.max(0, inner.height() - mainHeight - HELP_GAP)),
                false);
    }

    /**
     * Splits the picker's toolbar into the source switch and the search box.
     *
     * <p>The switch - "everything in the game" against "what I am carrying" - gets a fixed width, because its
     * two labels are known and it must never shrink to the point of being unreadable. The search box takes
     * what is left, since a longer box is only ever an improvement.</p>
     */
    public static Toolbar toolbar(Rect toolbarBand, int switchWidth) {
        Rect inner = toolbarBand.inset(2);
        if (inner.isEmpty()) {
            return new Toolbar(inner, inner);
        }
        int forSwitch = ScreenLayout.clamp(switchWidth, 0, Math.max(0, inner.width() - 40));
        Rect source = new Rect(inner.x(), inner.y(), forSwitch, inner.height());
        int searchX = source.right() + (forSwitch > 0 ? HELP_GAP : 0);
        return new Toolbar(source,
                new Rect(searchX, inner.y(), Math.max(0, inner.right() - searchX), inner.height()));
    }

    /** How many trade rows fit in an area. */
    public static int tradeRows(Rect area) {
        return ScreenLayout.visibleRows(area, TRADE_ROW_H);
    }

    /** One trade row, gap-free so drawing and hit-testing agree. */
    public static Rect tradeRow(Rect area, int index) {
        if (index < 0) {
            return new Rect(area.x(), area.y(), area.width(), 0);
        }
        return new Rect(area.x(), area.y() + index * TRADE_ROW_H, area.width(), TRADE_ROW_H);
    }

    /**
     * The index of the trade row at a point, or -1.
     *
     * <p>Bounded by the rows that were actually drawn, not by arithmetic: a click in the slack under the last
     * full row would otherwise select a row that is not there.</p>
     */
    public static int tradeRowAt(Rect area, int rowCount, double px, double py) {
        if (!area.contains(px, py)) {
            return -1;
        }
        int visible = Math.min(rowCount, tradeRows(area));
        int index = (int) ((py - area.y()) / TRADE_ROW_H);
        return index >= 0 && index < visible ? index : -1;
    }

    /** How many item cells fit across an area. Never zero, so a division by it is always safe. */
    public static int gridColumns(Rect area) {
        return Math.max(1, area.width() / ITEM_CELL);
    }

    public static int gridRows(Rect area) {
        return Math.max(0, area.height() / ITEM_CELL);
    }

    /** One cell of the item grid, by index within the page. */
    public static Rect gridCell(Rect area, int index) {
        int columns = gridColumns(area);
        int column = index % columns;
        int row = index / columns;
        return new Rect(area.x() + column * ITEM_CELL, area.y() + row * ITEM_CELL,
                ITEM_CELL, ITEM_CELL);
    }

    /**
     * The index of the item cell at a point, or -1.
     *
     * <p>Checked against the cell's own rectangle rather than computed from the coordinate alone, so the few
     * pixels of slack at the right edge - where the row does not divide evenly - belong to no cell.</p>
     */
    public static int gridCellAt(Rect area, int itemCount, double px, double py) {
        if (!area.contains(px, py)) {
            return -1;
        }
        int columns = gridColumns(area);
        int rows = gridRows(area);
        int perPage = Math.min(itemCount, columns * rows);
        for (int index = 0; index < perPage; index++) {
            if (gridCell(area, index).contains(px, py)) {
                return index;
            }
        }
        return -1;
    }

    /** How many items one page of the grid shows. */
    public static int itemsPerPage(Rect area) {
        return gridColumns(area) * gridRows(area);
    }
}
