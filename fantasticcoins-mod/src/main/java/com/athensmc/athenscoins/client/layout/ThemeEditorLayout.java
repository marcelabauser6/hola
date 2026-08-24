package com.athensmc.athenscoins.client.layout;

import com.athensmc.athenscoins.client.layout.ScreenLayout.Rect;
import com.athensmc.athenscoins.client.layout.ScreenLayout.Regions;

/**
 * The theme editor's geometry, allocated by a single downward cursor.
 *
 * <p>The editor's right column used to be built from two opposite ends. The colour picker, hex
 * readout, swatch rows and opacity slider each sat at a literal offset below the one before it, while
 * the three style toggles were anchored to {@code right.bottom() - 47}. Both ends moved when the
 * column's height changed, so they converged: below roughly 168px of column the opacity slider and
 * its label were drawn directly on top of the toggle buttons, and because the slider's hit test only
 * checked its own rectangle a click in that region both dragged the slider and pressed a button. The
 * left column had the same shape of bug between the nine colour slots and the preset row.</p>
 *
 * <p>Here every band is cut from the remaining space by one cursor that never moves backwards, so a
 * band cannot be positioned inside one that came before it. When the column is too short the last
 * bands collapse to zero height - a row that is not there is a visible, debuggable failure, whereas
 * two rows drawn on top of each other is not.</p>
 */
public record ThemeEditorLayout(
        Regions regions,
        Rect left,
        Rect right,
        Rect slotList,
        Rect presetRow,
        Rect pickerSquare,
        int hueHeight,
        Rect hexRow,
        Rect rainbowRow,
        Rect neutralRow,
        Rect alphaLabel,
        Rect alphaBar,
        Rect toggleColumn) {

    public static final int PANEL_W = 340;
    public static final int PANEL_H = 232;
    public static final int HEADER_H = 20;
    public static final int FOOTER_H = 28;

    public static final int SLOT_H = 14;
    public static final int SWATCH_H = 8;
    public static final int TEXT_H = 10;
    public static final int TOGGLE_H = 15;
    public static final int TOGGLES = 3;
    public static final int HUE_H = 8;
    /** The alpha knob sticks 2px above and below its bar, so the band reserves room for it. */
    public static final int ALPHA_BAND_H = SWATCH_H + 4;
    public static final int PRESET_ROW_H = 15;

    private static final int GAP = 4;
    private static final int PICKER_MIN = 36;
    private static final int PICKER_MAX = 48;

    public static ThemeEditorLayout of(int viewportWidth, int viewportHeight, int slotCount) {
        Rect panel = ScreenLayout.centeredPanel(viewportWidth, viewportHeight, PANEL_W, PANEL_H);
        Regions regions = ScreenLayout.regions(panel, HEADER_H, 0, FOOTER_H);
        ScreenLayout.Columns split = ScreenLayout.columns(regions.content().inset(8), 10);
        Rect left = split.first();
        Rect right = split.second();

        // ---- left column: the preset row is reserved first, the slot list takes what is left.
        Rect presetRow = new Rect(left.x(), Math.max(left.y(), left.bottom() - PRESET_ROW_H),
                left.width(), Math.min(PRESET_ROW_H, left.height()));
        int slotListHeight = Math.max(0, presetRow.y() - GAP - left.y());
        // Never taller than the rows it holds, so the highlight band cannot reach the presets.
        int wanted = Math.max(0, slotCount) * SLOT_H;
        Rect slotList = new Rect(left.x(), left.y(), left.width(), Math.min(slotListHeight, wanted));

        // ---- right column: one cursor, top to bottom.
        // Everything below the picker has a fixed height, so the picker absorbs the slack. Sizing it
        // from what is genuinely left over is what keeps the tail of the column inside the band.
        int fixedBelow = TEXT_H + GAP                       // hex readout
                + SWATCH_H + GAP                            // rainbow
                + SWATCH_H + GAP                            // neutrals
                + TEXT_H + GAP                              // opacity label
                + ALPHA_BAND_H + GAP                        // opacity bar
                + TOGGLES * TOGGLE_H + (TOGGLES - 1) * 1;   // toggles
        int pickerTotalRoom = right.height() - fixedBelow - GAP;
        int square = ScreenLayout.clamp(pickerTotalRoom - GAP - HUE_H, PICKER_MIN, PICKER_MAX);

        Cursor cursor = new Cursor(right);
        Rect pickerSquare = cursor.take(square);
        cursor.skip(GAP + HUE_H);
        cursor.skip(GAP);
        Rect hexRow = cursor.take(TEXT_H);
        cursor.skip(GAP);
        Rect rainbowRow = cursor.take(SWATCH_H);
        cursor.skip(GAP);
        Rect neutralRow = cursor.take(SWATCH_H);
        cursor.skip(GAP);
        Rect alphaLabel = cursor.take(TEXT_H);
        cursor.skip(GAP);
        Rect alphaBand = cursor.take(ALPHA_BAND_H);
        // The bar sits 2px inside its band so the knob's overhang stays inside the band too. Both the
        // offset and the height are clamped to the band, because the band itself collapses first when
        // the column runs out of room - and a bar hanging out of a collapsed band is the overlap this
        // whole class exists to prevent.
        int barTop = Math.min(alphaBand.y() + 2, alphaBand.bottom());
        Rect alphaBar = new Rect(alphaBand.x(), barTop, alphaBand.width(),
                ScreenLayout.clamp(SWATCH_H, 0, alphaBand.bottom() - barTop));
        cursor.skip(GAP);
        Rect toggleColumn = cursor.take(TOGGLES * TOGGLE_H + (TOGGLES - 1));

        return new ThemeEditorLayout(regions, left, right, slotList, presetRow,
                pickerSquare, HUE_H, hexRow, rainbowRow, neutralRow, alphaLabel, alphaBar,
                toggleColumn);
    }

    /** The {@code index}-th toggle button, inside {@link #toggleColumn}. */
    public Rect toggle(int index) {
        int y = toggleColumn.y() + index * (TOGGLE_H + 1);
        int top = Math.min(y, toggleColumn.bottom());
        return new Rect(toggleColumn.x(), top, toggleColumn.width(),
                ScreenLayout.clamp(TOGGLE_H, 0, toggleColumn.bottom() - top));
    }

    /** How many slot rows actually fit, so drawing and hit-testing agree on the count. */
    public int visibleSlots() {
        return ScreenLayout.visibleRows(slotList, SLOT_H);
    }

    /** Total height the picker widget occupies, square plus gap plus hue strip. */
    public int pickerTotalHeight() {
        return pickerSquare.height() + GAP + hueHeight;
    }

    /** Hands out consecutive, non-overlapping horizontal bands from a column. */
    private static final class Cursor {
        private final Rect column;
        private int y;

        Cursor(Rect column) {
            this.column = column;
            this.y = column.y();
        }

        Rect take(int height) {
            int top = Math.min(y, column.bottom());
            int usable = ScreenLayout.clamp(height, 0, column.bottom() - top);
            y = top + usable;
            return new Rect(column.x(), top, column.width(), usable);
        }

        void skip(int amount) {
            y = Math.min(y + Math.max(0, amount), column.bottom());
        }
    }
}
