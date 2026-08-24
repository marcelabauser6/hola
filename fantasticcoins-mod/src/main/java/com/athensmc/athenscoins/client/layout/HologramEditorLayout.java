package com.athensmc.athenscoins.client.layout;

/**
 * The bands of the hologram editor's appearance column, allocated so they cannot overlap.
 *
 * <p>This replaces {@code ThemeEditorLayout}, and it exists for the same reason that one did. The old
 * colour editor stacked a colour list, a picker, a hex readout, an opacity slider and three toggles down
 * one column by adding hand-written offsets. Below a certain column height the opacity slider was drawn
 * on top of the toggles beneath it and <em>both</em> reacted to a single click - a bug that is invisible
 * at the size a developer happens to have their window, and which a static test caught only because the
 * geometry lived in a class it could call without starting a client.</p>
 *
 * <p>So the geometry stays out here, free of Minecraft imports, and the screen asks for it. The
 * {@link Cursor} never moves backwards, which is what makes "these bands do not overlap" true by
 * construction rather than by arithmetic that has to be re-checked every time a control is added.</p>
 */
public record HologramEditorLayout(ScreenLayout.Rect column,
                                   ScreenLayout.Rect colorList,
                                   ScreenLayout.Rect pickerSquare,
                                   int hueHeight,
                                   ScreenLayout.Rect hexRow,
                                   ScreenLayout.Rect alphaBar) {

    /**
     * Band sizes, tuned so the whole stack fits the smallest viewport Minecraft will hand us.
     *
     * <p>320x240 leaves this column 132 pixels. The stack needs the heading, five colour rows, the
     * picker with its hue strip, one readout row and the opacity bar - which came to 148 at comfortable
     * sizes, so it did not fit and the bottom two controls collapsed into each other. The numbers below
     * add up to 129, leaving three spare: {@code PICKER_MIN} is what gives, because a small colour square
     * is still usable while a colour row you cannot click is not.</p>
     */
    public static final int HEADING_H = 13;
    public static final int SLOT_H = 11;
    public static final int TEXT_H = 9;
    public static final int HUE_H = 8;
    public static final int ALPHA_H = 8;
    public static final int GAP = 3;
    public static final int PICKER_MIN = 24;
    public static final int PICKER_MAX = 64;
    /** The picker is inset by a pixel so its own 1px frame stays inside the column. */
    private static final int FRAME = 1;

    public static HologramEditorLayout of(ScreenLayout.Rect column, int slotCount) {
        int width = Math.max(1, column.width() - FRAME * 2);
        Cursor cursor = new Cursor(column.y(), column.bottom());
        cursor.skip(HEADING_H);
        ScreenLayout.Rect colorList = band(column, cursor.take(Math.max(0, slotCount) * SLOT_H), width);
        cursor.skip(GAP);

        // Everything below the picker is fixed, so the picker absorbs whatever is left. Sizing the
        // picker first and hoping the rest fitted is what let the slider land on the toggles.
        int fixedBelow = GAP + HUE_H + GAP + TEXT_H + GAP + ALPHA_H + GAP;
        int room = Math.max(0, column.bottom() - cursor.y() - fixedBelow);
        int pickerSize = ScreenLayout.clamp(room, PICKER_MIN, PICKER_MAX);
        ScreenLayout.Rect picker = band(column, cursor.take(pickerSize), width);
        // The hue strip is drawn by the picker itself at square bottom + GAP; account for it here so
        // nothing below is allocated over it.
        cursor.skip(GAP + HUE_H + GAP);
        ScreenLayout.Rect hexRow = band(column, cursor.take(TEXT_H), width);
        cursor.skip(GAP);
        ScreenLayout.Rect alphaBar = band(column, cursor.take(ALPHA_H), width);

        return new HologramEditorLayout(column, colorList, picker, HUE_H, hexRow, alphaBar);
    }

    private static ScreenLayout.Rect band(ScreenLayout.Rect column, int[] span, int width) {
        return new ScreenLayout.Rect(column.x() + FRAME, span[0], width, span[1]);
    }

    /** How many colour slots actually fit, so drawing and hit-testing agree on the row count. */
    public int visibleSlots() {
        return ScreenLayout.visibleRows(colorList, SLOT_H);
    }

    public ScreenLayout.Rect slot(int index) {
        return new ScreenLayout.Rect(colorList.x(), colorList.y() + index * SLOT_H,
                colorList.width(), SLOT_H);
    }

    /**
     * Hands out consecutive bands down a column and never moves backwards.
     *
     * <p>A band is clamped to what is left rather than being allowed to run past the bottom, so a very
     * short column degrades into collapsed bands - which draw as nothing - instead of into controls
     * stacked on top of each other, which draw as a mess and steal each other's clicks.</p>
     */
    private static final class Cursor {
        private int y;
        private final int floor;
        private final int bottom;

        private Cursor(int start, int bottom) {
            this.y = start;
            this.floor = start;
            this.bottom = Math.max(start, bottom);
        }

        private int y() {
            return ScreenLayout.clamp(y, floor, bottom);
        }

        /**
         * @return {@code {top, height}} for the next band, clamped to what is left in the column
         *
         * <p>Both ends are clamped, not just the height. A band whose top had run past the column
         * floor would report a position outside the column even at zero height, which is how a
         * collapsed control ends up "escaping" the band that is supposed to hold it.</p>
         */
        private int[] take(int height) {
            int top = y();
            int safe = ScreenLayout.clamp(height, 0, bottom - top);
            y = top + safe;
            return new int[] { top, safe };
        }

        private void skip(int amount) {
            y = Math.min(bottom, y() + Math.max(0, amount));
        }
    }
}
