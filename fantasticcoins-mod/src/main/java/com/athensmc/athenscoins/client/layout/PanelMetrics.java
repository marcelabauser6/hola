package com.athensmc.athenscoins.client.layout;

import com.athensmc.athenscoins.client.layout.ScreenLayout.Regions;

/**
 * The band heights of the mod's standard panels, in one place.
 *
 * <p>The layout guard used to re-type each screen's numbers. That made it a test of its own copy of
 * the arithmetic: changing a footer height in a screen could not make it fail, which is the opposite
 * of what a regression guard is for. These constants are the single source, so the guard and the
 * screens cannot disagree - and if a band height changes, the guard re-checks it automatically.</p>
 *
 * <p>Deliberately free of any Minecraft import: the guard runs on a plain JVM, and loading a
 * {@code Screen} subclass just to read a constant off it would drag the client in.</p>
 */
public final class PanelMetrics {

    public static final int PANEL_W = 340;
    public static final int PANEL_H = 232;

    /** Bank terminal: title, five tabs, one button row. */
    public static final int TERMINAL_HEADER = 20;
    public static final int TERMINAL_TABS = 22;
    public static final int TERMINAL_FOOTER = 28;

    /**
     * Account detail: title, no tabs, and a footer of three rows.
     *
     * <p>66, not 54: the extra twelve pixels are the feedback line that tells a banker why an amount
     * was refused, instead of silently sending a zero.</p>
     */
    public static final int ACCOUNT_HEADER = 20;
    public static final int ACCOUNT_TABS = 0;
    public static final int ACCOUNT_FOOTER = 66;

    /**
     * Central bank: title, no tabs, and a footer of four rows.
     *
     * <p>82, not 70: the close button sat two pixels past the band that was meant to contain it, and
     * there is now a feedback line as well.</p>
     */
    public static final int CENTRAL_HEADER = 20;
    public static final int CENTRAL_TABS = 0;
    public static final int CENTRAL_FOOTER = 82;

    /** Economy statistics: title, two tabs, one button row. */
    public static final int STATS_HEADER = 20;
    public static final int STATS_TABS = 22;
    public static final int STATS_FOOTER = 28;

    /** Inner padding shared by the standard panels' content bands. */
    public static final int CONTENT_PAD = 8;

    private PanelMetrics() {
    }

    public static ScreenLayout.Rect panel(int viewportWidth, int viewportHeight) {
        return ScreenLayout.centeredPanel(viewportWidth, viewportHeight, PANEL_W, PANEL_H);
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
}
