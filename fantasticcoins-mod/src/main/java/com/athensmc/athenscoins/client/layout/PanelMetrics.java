package com.athensmc.athenscoins.client.layout;

import com.athensmc.athenscoins.client.layout.ScreenLayout.Regions;

/**
 * The band heights of the mod's standard panels, in one place.
 *
 * <p>These panels used to be a fixed 340x232 centred in the viewport, and that size was the root of
 * most of the crowding: the central bank's list had room for exactly two banks, the terminal's
 * settings had to cram eight controls into a 2x4 grid of buttons sharing two entry boxes, and every
 * label was clipped to about a quarter of the panel width. Now they fill the viewport, so the layout
 * has room to be a list instead of a puzzle.</p>
 *
 * <p>The ATM is deliberately <em>not</em> here: it stays a small fixed panel (see {@link AtmLayout}),
 * because it is a machine you walk up to rather than a dashboard you administer.</p>
 *
 * <p>Deliberately free of any Minecraft import: the layout guard runs on a plain JVM, and loading a
 * {@code Screen} subclass just to read a constant off it would drag the client in.</p>
 */
public final class PanelMetrics {

    /** Gap between the panel and the edge of the screen. */
    public static final int MARGIN = 6;

    /**
     * Bank terminal: title, the tab strip, and one row of buttons.
     *
     * <p>Slightly taller bands than before, because a full-screen panel with 20px furniture looks
     * starved.</p>
     */
    public static final int TERMINAL_HEADER = 22;
    public static final int TERMINAL_TABS = 24;
    public static final int TERMINAL_FOOTER = 30;

    /** Account detail: title, no tabs, and a footer of three rows (actions, feedback, Back). */
    public static final int ACCOUNT_HEADER = 22;
    public static final int ACCOUNT_TABS = 0;
    public static final int ACCOUNT_FOOTER = 70;

    /**
     * Central bank: title, three tabs, and a footer of three rows.
     *
     * <p>It had no tabs and tried to show the rate table, the system totals, the bank list and the
     * selection all at once in two columns. At anything below a very tall window the totals ran straight
     * under the footer and the last two lines were drawn over the amount box. Splitting the content across
     * tabs is what makes each part fit rather than shrinking all of them until none of them are readable.</p>
     */
    public static final int CENTRAL_HEADER = 22;
    public static final int CENTRAL_TABS = 24;
    public static final int CENTRAL_FOOTER = 74;

    /** Hologram editor: title, two tabs plus the preset row, and a footer of two rows. */
    public static final int STATS_HEADER = 22;
    public static final int STATS_TABS = 24;
    public static final int STATS_FOOTER = 34;

    /** Inner padding shared by the standard panels' content bands. */
    public static final int CONTENT_PAD = 8;

    /** Height of one row in the mod's lists, and of one line of labelled text. */
    public static final int ROW_H = 14;
    public static final int LINE_H = 12;

    private PanelMetrics() {
    }

    /**
     * A panel filling the viewport, less a margin on every side.
     *
     * <p>No maximum size. A cap would keep line lengths tidy on a very large window, but it would
     * also mean the panel stops growing exactly when there is most room to use, and the screens
     * inside all lay out in columns and bounded lists that cope with width on their own.</p>
     */
    public static ScreenLayout.Rect panel(int viewportWidth, int viewportHeight) {
        int width = Math.max(1, viewportWidth - MARGIN * 2);
        int height = Math.max(1, viewportHeight - MARGIN * 2);
        return new ScreenLayout.Rect(MARGIN, MARGIN, width, height);
    }

    public static Regions terminal(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                TERMINAL_HEADER, TERMINAL_TABS, TERMINAL_FOOTER);
    }

    public static Regions account(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                ACCOUNT_HEADER, ACCOUNT_TABS, ACCOUNT_FOOTER);
    }

    public static Regions central(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                CENTRAL_HEADER, CENTRAL_TABS, CENTRAL_FOOTER);
    }

    public static Regions stats(int viewportWidth, int viewportHeight) {
        return ScreenLayout.regions(panel(viewportWidth, viewportHeight),
                STATS_HEADER, STATS_TABS, STATS_FOOTER);
    }

    /**
     * Splits an area into as many equal columns as fit at {@code minColumnWidth}, capped at
     * {@code maxColumns}.
     *
     * <p>What makes a full-screen panel usable rather than merely large: at 320 logical pixels a
     * summary is one column, on a wide window it is three, and neither case needs its own layout.</p>
     */
    public static int columnsFor(ScreenLayout.Rect area, int minColumnWidth, int maxColumns) {
        if (minColumnWidth <= 0) {
            return 1;
        }
        return ScreenLayout.clamp(area.width() / minColumnWidth, 1, Math.max(1, maxColumns));
    }
}
