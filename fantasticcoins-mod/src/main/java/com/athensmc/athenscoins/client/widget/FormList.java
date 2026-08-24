package com.athensmc.athenscoins.client.widget;

import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A vertical, scrollable list of labelled controls.
 *
 * <p>Written for the bank's settings, which were eight buttons around two shared entry boxes: you
 * typed a number, worked out which button it belonged to, and pressed it, with nothing on screen
 * saying what the bank's current values were. A form wants one labelled box per field, top to bottom,
 * and once there is one box per field there are more fields than fit - so it needs to scroll.</p>
 *
 * <p>Scrolling repositions the real widgets rather than translating the drawing. Minecraft's widgets
 * hit-test against their own absolute coordinates, so a list that scrolled by shifting the render
 * pose would draw a box in one place and accept clicks in another. Rows scrolled out of the band are
 * also marked invisible, which is what stops them being clicked through the header and footer.</p>
 */
public class FormList {

    /**
     * One field: its label, the control that edits it, an optional line of explanation, and an
     * optional colour swatch.
     *
     * <p>The swatch is an {@link java.util.function.IntSupplier} rather than a value because the
     * colour row's control changes the colour: read once at build time it would show the old colour
     * until the form was reopened.</p>
     */
    public record Row(Component label, @Nullable AbstractWidget widget, @Nullable Component hint,
                      @Nullable java.util.function.IntSupplier swatch) {

        public static Row of(Component label, AbstractWidget widget) {
            return new Row(label, widget, null, null);
        }

        public static Row of(Component label, AbstractWidget widget, Component hint) {
            return new Row(label, widget, hint, null);
        }

        public static Row swatched(Component label, AbstractWidget widget, Component hint,
                                   java.util.function.IntSupplier swatch) {
            return new Row(label, widget, hint, swatch);
        }

        /** A heading with no control, for grouping fields. */
        public static Row heading(Component label) {
            return new Row(label, null, null, null);
        }

        public boolean isHeading() {
            return widget == null;
        }
    }

    private static final int ROW_H = 22;
    private static final int HEADING_H = 16;
    private static final int SCROLLBAR_W = 4;
    private static final int LABEL_GAP = 6;

    private final List<Row> rows = new ArrayList<>();
    private ScreenLayout.Rect area = new ScreenLayout.Rect(0, 0, 0, 0);
    private int scroll;
    /** Share of the width given to labels, so the controls all start at the same x. */
    private int labelWidth;

    public void clear() {
        rows.clear();
        scroll = 0;
    }

    public void add(Row row) {
        rows.add(row);
    }

    public List<Row> rows() {
        return rows;
    }

    /** Height one row occupies, headings being shorter than fields. */
    private static int heightOf(Row row) {
        return row.isHeading() ? HEADING_H : ROW_H;
    }

    public int contentHeight() {
        int total = 0;
        for (Row row : rows) {
            total += heightOf(row);
        }
        return total;
    }

    public int maxScroll() {
        return Math.max(0, contentHeight() - area.height());
    }

    public boolean scrollable() {
        return maxScroll() > 0;
    }

    /**
     * Positions every control inside {@code band} and hides the ones scrolled out of it.
     *
     * <p>Call after any change to the band or the scroll offset.</p>
     */
    public void layout(ScreenLayout.Rect band, int controlWidth) {
        this.area = band;
        this.labelWidth = Math.max(40, Math.min(band.width() - controlWidth - LABEL_GAP - SCROLLBAR_W,
                band.width() * 42 / 100));
        scroll = ScreenLayout.clamp(scroll, 0, maxScroll());

        int y = band.y() - scroll;
        for (Row row : rows) {
            int height = heightOf(row);
            AbstractWidget widget = row.widget();
            if (widget != null) {
                int controlX = band.x() + labelWidth + LABEL_GAP;
                int available = Math.max(20, band.right() - controlX - SCROLLBAR_W - 2);
                widget.setX(controlX);
                widget.setY(y + 2);
                widget.setWidth(Math.min(controlWidth, available));
                // A row is only live while it is fully inside the band; a half-clipped box that still
                // accepts clicks is worse than one that is simply not there.
                widget.visible = y >= band.y() - 1 && y + height <= band.bottom() + 1;
                widget.active = widget.visible;
            }
            y += height;
        }
    }

    public void scrollBy(int delta) {
        scroll = ScreenLayout.clamp(scroll + delta, 0, maxScroll());
    }

    public boolean contains(double mouseX, double mouseY) {
        return area.contains(mouseX, mouseY);
    }

    /**
     * Draws the labels, hints and scrollbar. The controls draw themselves as ordinary widgets.
     *
     * @return the hint of the row under the cursor, for the caller's tooltip, or {@code null}
     */
    @Nullable
    public Component render(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                            int labelColor, int headingColor, int hintColor) {
        Component hovered = null;
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int y = area.y() - scroll;
        int index = 0;
        for (Row row : rows) {
            int height = heightOf(row);
            boolean onScreen = y + height > area.y() && y < area.bottom();
            if (onScreen) {
                if (row.isHeading()) {
                    graphics.fill(area.x(), y + height - 3, area.right() - SCROLLBAR_W - 2,
                            y + height - 2, 0x30FFFFFF);
                    drawFitted(graphics, font, row.label().getString(), area.x(), y + 3,
                            area.width() - SCROLLBAR_W, headingColor);
                } else {
                    // Zebra striping: with one box per row this is what keeps a label tied to its
                    // own control rather than to the one above it.
                    if (index % 2 == 0) {
                        graphics.fill(area.x(), y, area.right() - SCROLLBAR_W - 2, y + height,
                                0x18FFFFFF);
                    }
                    int labelY = row.hint() == null ? y + 6 : y + 2;
                    int textX = area.x() + 2;
                    if (row.swatch() != null) {
                        int rgb = 0xFF000000 | row.swatch().getAsInt();
                        graphics.fill(textX, y + 5, textX + 10, y + 15, 0xFF000000);
                        graphics.fill(textX + 1, y + 6, textX + 9, y + 14, rgb);
                        textX += 14;
                    }
                    int textWidth = labelWidth - 4 - (textX - area.x() - 2);
                    drawFitted(graphics, font, row.label().getString(), textX, labelY,
                            textWidth, labelColor);
                    if (row.hint() != null) {
                        drawFitted(graphics, font, row.hint().getString(), textX, y + 12,
                                textWidth, hintColor);
                    }
                    if (mouseY >= y && mouseY < y + height && area.contains(mouseX, mouseY)) {
                        hovered = row.hint() == null ? row.label() : row.hint();
                    }
                }
            }
            if (!row.isHeading()) {
                index++;
            }
            y += height;
        }
        graphics.disableScissor();

        if (scrollable()) {
            int trackX = area.right() - SCROLLBAR_W;
            graphics.fill(trackX, area.y(), trackX + SCROLLBAR_W, area.bottom(), 0x40000000);
            int thumb = Math.max(12, area.height() * area.height() / Math.max(1, contentHeight()));
            int travel = area.height() - thumb;
            int thumbY = area.y() + (travel <= 0 ? 0 : travel * scroll / maxScroll());
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumb, 0x90FFFFFF);
        }
        return hovered;
    }

    private static void drawFitted(GuiGraphics graphics, Font font, String text, int x, int y,
                                   int maxWidth, int color) {
        graphics.drawString(font, ScreenText.fit(font, text, maxWidth), x, y, color, false);
    }
}
