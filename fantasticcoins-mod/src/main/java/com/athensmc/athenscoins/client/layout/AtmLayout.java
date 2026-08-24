package com.athensmc.athenscoins.client.layout;

import com.athensmc.athenscoins.client.layout.ScreenLayout.Rect;
import com.athensmc.athenscoins.client.layout.ScreenLayout.Regions;

/**
 * The ATM's geometry, computed once and shared by the screen and the layout guard.
 *
 * <p>The old ATM was the only screen with no responsive layout at all: every position was a
 * constant tied to a baked 248x198 texture, and the geometry test re-typed those constants instead
 * of importing them. That meant the test validated its own copy of the arithmetic - moving a button
 * in the screen could not make it fail. Putting the real math here and having both call it is what
 * makes the guard a guard.</p>
 *
 * <p>The bands are laid out so that overlap is arithmetically impossible rather than merely
 * unobserved: {@link ScreenLayout#regions} already guarantees header, tabs, content and footer are
 * disjoint, and the three footer rows are carved from the footer with an explicit running cursor, so
 * no row can be positioned past the band that contains it.</p>
 */
public record AtmLayout(
        Regions regions,
        Rect content,
        Rect amountRow,
        Rect messageRow,
        Rect closeRow) {

    public static final int PANEL_W = 300;
    public static final int PANEL_H = 214;

    public static final int HEADER_H = 20;
    public static final int TABS_H = 22;
    /** Three stacked rows: the amount entry, one line of feedback, and the close button. */
    public static final int FOOTER_H = 56;

    private static final int SIDE_PAD = 6;
    private static final int ROW_H = 16;
    private static final int MESSAGE_H = 10;
    private static final int CLOSE_H = 18;
    private static final int GAP = 3;

    public static AtmLayout of(int viewportWidth, int viewportHeight) {
        Rect panel = ScreenLayout.centeredPanel(viewportWidth, viewportHeight, PANEL_W, PANEL_H);
        Regions regions = ScreenLayout.regions(panel, HEADER_H, TABS_H, FOOTER_H);

        Rect footer = new Rect(
                regions.footer().x() + SIDE_PAD,
                regions.footer().y(),
                Math.max(0, regions.footer().width() - SIDE_PAD * 2),
                regions.footer().height());

        // One cursor walks down the footer, and every row is clamped to what is left, so a short
        // panel makes rows collapse instead of spilling past the panel floor.
        int cursor = footer.y() + 4;
        Rect amountRow = band(footer, cursor, ROW_H);
        cursor = amountRow.bottom() + GAP;
        Rect messageRow = band(footer, cursor, MESSAGE_H);
        cursor = messageRow.bottom() + GAP;
        Rect closeRow = band(footer, cursor, CLOSE_H);

        return new AtmLayout(regions, regions.content().inset(SIDE_PAD),
                amountRow, messageRow, closeRow);
    }

    private static Rect band(Rect footer, int y, int height) {
        int top = Math.min(y, footer.bottom());
        return new Rect(footer.x(), top, footer.width(),
                ScreenLayout.clamp(height, 0, footer.bottom() - top));
    }

    /**
     * Splits the amount row into the entry box and {@code buttons} action cells.
     *
     * <p>The box takes a fixed share rather than a leftover, so the buttons never get squeezed to
     * zero and the box never grows under them.</p>
     */
    public Rect amountBox(int buttons) {
        int cells = Math.max(1, buttons) + 2;
        int boxWidth = Math.max(36, amountRow.width() * 2 / cells);
        return new Rect(amountRow.x(), amountRow.y(), boxWidth, amountRow.height());
    }

    /** The {@code index}-th action cell to the right of the entry box. */
    public Rect actionCell(int buttons, int index) {
        Rect box = amountBox(buttons);
        int available = Math.max(0, amountRow.right() - box.right() - GAP);
        Rect strip = new Rect(box.right() + GAP, amountRow.y(), available, amountRow.height());
        Rect cell = ScreenLayout.partition(strip, Math.max(1, buttons), index);
        // partition is gap-free; trim each cell so adjacent buttons do not share a border pixel.
        return new Rect(cell.x(), cell.y(), Math.max(0, cell.width() - GAP), cell.height());
    }

    /** The tab strip, already inset so the buttons sit inside the band. */
    public Rect tabStrip() {
        return regions.tabs().inset(4);
    }

    /** The close button, right-aligned in its row. */
    public Rect closeButton() {
        int width = Math.min(80, closeRow.width());
        return new Rect(closeRow.right() - width, closeRow.y(), width, closeRow.height());
    }

    /** A scrollable list occupying the content band below a one-line title. */
    public Rect listArea(int titleHeight) {
        int top = Math.min(content.y() + titleHeight, content.bottom());
        return new Rect(content.x(), top, content.width(), Math.max(0, content.bottom() - top));
    }
}
