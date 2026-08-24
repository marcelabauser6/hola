package com.athensmc.fantasticregions.client.layout;

import com.athensmc.fantasticregions.client.layout.ScreenLayout.Rect;
import com.athensmc.fantasticregions.client.layout.ScreenLayout.Regions;

/**
 * Band heights and content splits for the region screens.
 *
 * <p>Both screens fill the viewport, less a margin. A fixed panel was never an option here: the flag
 * list alone is 93 rows across six tabs, and the members list has to show a name, a group and a way to
 * remove each one. Anything smaller than the window turns both into a two-item window with a scrollbar.</p>
 *
 * <p>The part worth explaining is the help area. Every control has to carry an explanation, and the
 * usual answer - a tooltip under the cursor - is the one thing that cannot work here: tooltips are
 * drawn over the content, so on a dense list they cover the very rows the player is comparing, and two
 * of them near the panel edge overlap each other. Instead the explanation gets its own reserved space
 * that nothing else is allowed to draw in, and the row under the cursor writes into it. On a wide
 * window that space is a column down the right; on a narrow one it becomes a band across the bottom,
 * because a column of 90 pixels holds about four words per line.</p>
 *
 * <p>Deliberately free of any Minecraft import: the layout guard runs on a plain JVM.</p>
 */
public final class RegionLayout {

    /** Gap between the panel and the edge of the screen. */
    public static final int MARGIN = 6;

    /** Inner padding of the content bands. */
    public static final int CONTENT_PAD = 8;

    /** Gap between the main content and the help area. */
    public static final int HELP_GAP = 6;

    /** One row in a list, and one line of text. */
    public static final int ROW_H = 16;
    public static final int LINE_H = 11;

    /** Region list: title, the dimension strip, and a footer with the actions. */
    public static final int LIST_HEADER = 24;
    public static final int LIST_TABS = 22;
    public static final int LIST_FOOTER = 30;

    /** Region settings: title, the six flag tabs, and a footer with Save and Back. */
    public static final int CONFIG_HEADER = 24;
    public static final int CONFIG_TABS = 22;
    public static final int CONFIG_FOOTER = 30;

    /**
     * Narrowest the help column may be before it stops being worth having.
     *
     * <p>Below this an explanation of 120 characters wraps into eight or nine lines and runs out of the
     * bottom of the column, so the help moves to a band across the width instead.</p>
     */
    public static final int HELP_COLUMN_MIN = 130;

    /** Widest the help column gets. Past this the lines are long enough to lose your place in. */
    public static final int HELP_COLUMN_MAX = 220;

    /** Height of the help band used when the window is too narrow for a column. */
    public static final int HELP_BAND_H = 46;

    /** The main content and the reserved help area, which never overlap. */
    public record HelpSplit(Rect main, Rect help, boolean helpIsColumn) {
    }

    private RegionLayout() {
    }

    public static Rect panel(int viewportWidth, int viewportHeight) {
        int width = Math.max(1, viewportWidth - MARGIN * 2);
        int height = Math.max(1, viewportHeight - MARGIN * 2);
        return new Rect(MARGIN, MARGIN, width, height);
    }

    public static Regions list(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                LIST_HEADER, LIST_TABS, LIST_FOOTER);
    }

    public static Regions config(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                CONFIG_HEADER, CONFIG_TABS, CONFIG_FOOTER);
    }

    /**
     * Splits a content band into the working area and the reserved help area.
     *
     * <p>The two rectangles tile the band with exactly one gap between them and never intersect, which
     * is what lets the help be drawn unconditionally instead of being suppressed whenever it might
     * collide with something.</p>
     */
    public static HelpSplit withHelp(Rect content) {
        Rect inner = content.inset(CONTENT_PAD);
        if (inner.isEmpty()) {
            return new HelpSplit(inner, inner, false);
        }

        // A column needs room for itself, the gap, and a main area at least as wide again. Otherwise
        // the "main" area ends up narrower than the help beside it, which is the wrong way round.
        int widthForColumn = HELP_COLUMN_MIN * 2 + HELP_GAP;
        if (inner.width() >= widthForColumn) {
            int helpWidth = ScreenLayout.clamp(inner.width() / 3, HELP_COLUMN_MIN, HELP_COLUMN_MAX);
            int mainWidth = inner.width() - helpWidth - HELP_GAP;
            Rect main = new Rect(inner.x(), inner.y(), mainWidth, inner.height());
            Rect help = new Rect(inner.x() + mainWidth + HELP_GAP, inner.y(), helpWidth,
                    inner.height());
            return new HelpSplit(main, help, true);
        }

        // Narrow window: the help becomes a band along the bottom. It is capped at half the band so a
        // short window still leaves rows visible above it - a help box with nothing left to explain
        // would be a strange way to run out of space.
        int helpHeight = Math.min(HELP_BAND_H, Math.max(0, inner.height() / 2));
        int mainHeight = Math.max(0, inner.height() - helpHeight - HELP_GAP);
        Rect main = new Rect(inner.x(), inner.y(), inner.width(), mainHeight);
        Rect help = new Rect(inner.x(), inner.y() + mainHeight + HELP_GAP, inner.width(),
                Math.max(0, inner.height() - mainHeight - HELP_GAP));
        return new HelpSplit(main, help, false);
    }

    /** How many list rows fit in an area. */
    public static int rowsIn(Rect area, int rowHeight) {
        return ScreenLayout.visibleRows(area, rowHeight);
    }

    /** One row of a list, by index, gap-free so drawing and hit-testing agree. */
    public static Rect row(Rect area, int index, int rowHeight) {
        if (rowHeight <= 0 || index < 0) {
            return new Rect(area.x(), area.y(), area.width(), 0);
        }
        return new Rect(area.x(), area.y() + index * rowHeight, area.width(), rowHeight);
    }

    /**
     * The index of the row at a point, or -1.
     *
     * <p>Bounded by the rows that actually fit, not by arithmetic alone: a click in the few pixels of
     * slack under the last full row would otherwise select a row that was never drawn.</p>
     */
    public static int rowIndexAt(Rect area, int rowHeight, int rowCount, double px, double py) {
        if (rowHeight <= 0 || !area.contains(px, py)) {
            return -1;
        }
        int visible = Math.min(rowCount, rowsIn(area, rowHeight));
        int index = (int) ((py - area.y()) / rowHeight);
        return index >= 0 && index < visible ? index : -1;
    }
}
