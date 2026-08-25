package com.athensmc.fsshopkeepers.client.theme;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The Fantastic family's drawing primitives.
 *
 * <p>A port of the helper the other Fantastic mods draw their screens with, kept deliberately faithful: the colour
 * constants are the same literal values, the corner rounding uses the same three-pixel inset table, and the panel is
 * built from the same shadow rings, vertical gradient, rounded border, top highlight and header sheen. Matching by eye
 * would drift; matching the numbers cannot.</p>
 *
 * <p>Colours are written as the signed decimal integers the family already uses rather than as hex. Re-typing them as
 * hex is a chance to fat-finger a digit and end up with a panel that is nearly, but not quite, the same grey as every
 * other Fantastic screen.</p>
 */
public final class FSGui {

    /** Panel body, top and bottom of its vertical gradient. */
    public static final int PANEL_TOP = -299619784;
    public static final int PANEL_BOTTOM = -300541404;
    public static final int PANEL_BG = -299619784;

    /** The panel's one-pixel outline, and the lighter line just inside its top edge. */
    public static final int PANEL_BORDER = -11774872;
    public static final int PANEL_HIGHLIGHT = -9208429;

    /** A sunken well, for fields and lists. */
    public static final int INSET_BG = 1292767768;
    public static final int INSET_BORDER = -13090994;

    public static final int TEXT = -1644826;
    public static final int TEXT_DIM = -6577748;

    public static final int ACCENT_GREEN = -11746198;
    public static final int ACCENT_BLUE = -11884320;
    public static final int ACCENT_RED = -2796465;

    /** The window's own backdrop, title strip and closing rule. */
    public static final int WINDOW_BG = -535291870;
    public static final int TITLE_BAR = -14408646;
    public static final int RULE = -12961206;

    /** The colour the help line under the tabs is drawn in. */
    public static final int HELP_TEXT = 10133680;

    private FSGui() {
    }

    /**
     * How far a row is pulled in from the sides to fake a rounded corner.
     *
     * <p>Three pixels on the outermost row, then two, then one. Measured from whichever edge is nearer, so the same
     * table rounds the bottom as well as the top.</p>
     */
    private static int cornerInset(int row, int height) {
        int fromEdge = Math.min(row, height - 1 - row);
        if (fromEdge == 0) {
            return 3;
        }
        if (fromEdge == 1) {
            return 2;
        }
        if (fromEdge == 2) {
            return 1;
        }
        return 0;
    }

    private static void roundedGradient(GuiGraphics graphics, int x, int y, int width, int height, int top,
            int bottom) {
        for (int row = 0; row < height; row++) {
            int inset = cornerInset(row, height);
            float t = height <= 1 ? 0.0F : (float) row / (float) (height - 1);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, lerpColor(top, bottom, t));
        }
    }

    /** Blends two colours, alpha included, which is what lets the shadow rings fade. */
    private static int lerpColor(int a, int b, float t) {
        int aa = a >>> 24;
        int ba = b >>> 24;
        int alpha = (int) ((float) aa + (float) (ba - aa) * t);
        int r = (int) ((float) (a >> 16 & 0xFF) + (float) ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = (int) ((float) (a >> 8 & 0xFF) + (float) ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = (int) ((float) (a & 0xFF) + (float) ((b & 0xFF) - (a & 0xFF)) * t);
        return (alpha & 0xFF) << 24 | r << 16 | g << 8 | bl;
    }

    /**
     * A raised panel with a drop shadow.
     *
     * <p>Three shadow rings outside the panel at increasing opacity, then the gradient body, then the border, then the
     * bright line inside the top edge, then a sheen over the first eighteen pixels. Drawn in that order because each
     * layer is meant to sit over the last.</p>
     */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        for (int ring = 3; ring >= 1; ring--) {
            int alpha = 20 * ring;
            roundedGradient(graphics, x - ring, y - ring + 1, width + ring * 2, height + ring * 2,
                    alpha << 24, alpha << 24);
        }
        roundedGradient(graphics, x, y, width, height, PANEL_TOP, PANEL_BOTTOM);
        borderRounded(graphics, x, y, width, height, PANEL_BORDER);
        graphics.fill(x + 4, y + 1, x + width - 4, y + 2, PANEL_HIGHLIGHT);
        graphics.fillGradient(x + 2, y + 2, x + width - 2, y + 20, 0x1AFFFFFF, 0xFFFFFF);
    }

    private static void borderRounded(GuiGraphics graphics, int x, int y, int width, int height, int colour) {
        for (int row = 0; row < height; row++) {
            int inset = cornerInset(row, height);
            int previous = row == 0 ? -1 : cornerInset(row - 1, height);
            if (row == 0 || row == height - 1 || inset != previous) {
                graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, colour);
                continue;
            }
            graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, colour);
            graphics.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, colour);
        }
        int lastInset = cornerInset(height - 1, height);
        graphics.fill(x + lastInset, y + height - 1, x + width - lastInset, y + height, colour);
    }

    /** A sunken well: the background for a list, a field or an item slot. */
    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        roundedGradient(graphics, x, y, width, height, 1510673939, 1024332312);
        borderRounded(graphics, x, y, width, height, INSET_BORDER);
    }

    /** The scrollbar, with a brighter thumb while it is being dragged or hovered. */
    public static void scrollbar(GuiGraphics graphics, int x, int y, int width, int trackHeight, int thumbTop,
            int thumbHeight, boolean highlighted) {
        graphics.fill(x, y, x + width, y + trackHeight, 0x60000000);
        int body = highlighted ? -7695450 : -10854030;
        int edge = highlighted ? -4800819 : -9011568;
        graphics.fill(x, thumbTop, x + width, thumbTop + thumbHeight, body);
        graphics.fill(x, thumbTop, x + width, thumbTop + 1, edge);
        graphics.fill(x, thumbTop + thumbHeight - 1, x + width, thumbTop + thumbHeight, -12696496);
    }

    /** A faint horizontal rule. */
    public static void separator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0x30FFFFFF);
    }

    /** Where a scrollbar thumb starts, for a given scroll position. */
    public static int thumbTop(int trackTop, int trackHeight, int thumbHeight, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return trackTop;
        }
        int travel = Math.max(0, trackHeight - thumbHeight);
        return trackTop + travel * Math.max(0, Math.min(scroll, maxScroll)) / maxScroll;
    }

    /** How tall the thumb is, floored so it stays grabbable on a very long list. */
    public static int thumbHeight(int trackHeight, int visibleRows, int totalRows) {
        if (totalRows <= 0) {
            return trackHeight;
        }
        return Math.max(12, Math.min(trackHeight, trackHeight * visibleRows / totalRows));
    }
}
