package com.athensmc.fsshopkeepers.client.theme;

import com.athensmc.fsshopkeepers.client.layout.Rect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * The look of the Fantastic screens.
 *
 * <p>Panels are drawn from filled rectangles rather than from a texture atlas. That is a deliberate trade: a nine-slice
 * texture would look richer, but it also has a fixed corner size, which is what forces a texture-based screen into a
 * fixed layout and is the usual reason such screens overlap when resized. Rectangles stretch to whatever
 * {@link com.athensmc.fsshopkeepers.client.layout.EditorLayout} decides, and the screen carries no assets that could
 * fail to load.</p>
 *
 * <p>Colours are one palette in one place. Every band, button and label reads from here so a screen cannot end up with
 * two nearly-identical greys, and so the contrast between text and its background is decided once rather than per
 * label.</p>
 */
public final class FantasticTheme {

    /** The screen's backdrop, drawn over the world. */
    public static final int BACKDROP = 0xE8121218;

    /** A raised panel: headers, footers, list rows. */
    public static final int PANEL = 0xFF23232E;
    public static final int PANEL_DARK = 0xFF1A1A22;
    public static final int PANEL_LIGHT = 0xFF2E2E3C;

    /** A one-pixel border, lighter at the top and darker at the bottom to suggest depth. */
    public static final int BORDER = 0xFF3E3E52;
    public static final int BORDER_BRIGHT = 0xFF55556E;

    /** The family's accent, used for the selected tab and the primary action. */
    public static final int ACCENT = 0xFFE8A33D;
    public static final int ACCENT_DIM = 0xFF8A5F22;

    /** Confirmation and refusal. */
    public static final int POSITIVE = 0xFF4CAF6A;
    public static final int NEGATIVE = 0xFFD05050;
    public static final int NEGATIVE_DIM = 0xFF6E2E2E;

    public static final int TEXT = 0xFFF0F0F5;
    public static final int TEXT_MUTED = 0xFF9A9AAE;
    public static final int TEXT_DISABLED = 0xFF66667A;
    public static final int TEXT_ON_ACCENT = 0xFF201404;

    /** A hovered control, drawn as a wash over whatever is beneath it. */
    public static final int HOVER = 0x33FFFFFF;

    private FantasticTheme() {
    }

    /** Fills a rectangle. */
    public static void fill(GuiGraphics graphics, Rect rect, int colour) {
        if (rect.isEmpty()) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), colour);
    }

    /**
     * A panel with a border.
     *
     * <p>The border is drawn as four one-pixel edges inside the rectangle, so a panel never grows beyond the
     * geometry the layout gave it. A border drawn outside would make every panel one pixel larger than measured,
     * which is exactly how neighbouring panels come to touch.</p>
     */
    public static void panel(GuiGraphics graphics, Rect rect, int fillColour, int borderColour) {
        if (rect.isEmpty()) {
            return;
        }
        fill(graphics, rect, fillColour);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, borderColour);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), borderColour);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(), borderColour);
        graphics.fill(rect.right() - 1, rect.y(), rect.right(), rect.bottom(), borderColour);
    }

    public static void panel(GuiGraphics graphics, Rect rect, int fillColour) {
        panel(graphics, rect, fillColour, BORDER);
    }

    /** A slot-shaped well, for the payment item cells. */
    public static void slot(GuiGraphics graphics, Rect rect, boolean hovered) {
        panel(graphics, rect, PANEL_DARK, hovered ? ACCENT : BORDER);
    }

    /** A text field's well, so a field is visibly a place to type even when empty. */
    public static void field(GuiGraphics graphics, Rect rect, boolean focused, boolean valid) {
        int border = !valid ? NEGATIVE : focused ? ACCENT : BORDER;
        panel(graphics, rect, PANEL_DARK, border);
    }

    /** A button, coloured by state. */
    public static void button(GuiGraphics graphics, Rect rect, Style style, boolean hovered, boolean enabled) {
        if (rect.isEmpty()) {
            return;
        }
        int base = switch (style) {
            case PRIMARY -> enabled ? ACCENT : ACCENT_DIM;
            case DANGER -> enabled ? NEGATIVE : NEGATIVE_DIM;
            case SELECTED -> ACCENT;
            case PLAIN -> enabled ? PANEL_LIGHT : PANEL_DARK;
        };
        panel(graphics, rect, base, style == Style.PLAIN ? BORDER : BORDER_BRIGHT);
        if (hovered && enabled) {
            fill(graphics, rect.shrink(1), HOVER);
        }
    }

    /** How a button is coloured. */
    public enum Style {
        /** The action the screen exists to perform. */
        PRIMARY,
        /** An ordinary action. */
        PLAIN,
        /** Something that cannot be undone. */
        DANGER,
        /** The currently chosen option among several. */
        SELECTED
    }

    /** The text colour that reads against a button of the given style. */
    public static int textOn(Style style, boolean enabled) {
        if (!enabled) {
            return TEXT_DISABLED;
        }
        return switch (style) {
            case PRIMARY, SELECTED -> TEXT_ON_ACCENT;
            case DANGER -> TEXT;
            case PLAIN -> TEXT;
        };
    }

    /**
     * Draws text centred in a rectangle, cut to fit.
     *
     * <p>Truncation happens here rather than being left to the caller, because a label longer than its cell is the
     * other way text ends up on top of a neighbouring control: nothing overlaps in the layout, but the glyphs run
     * past the edge anyway.</p>
     */
    public static void centeredText(GuiGraphics graphics, Font font, Rect rect, String text, int colour) {
        if (rect.isEmpty() || text == null || text.isEmpty()) {
            return;
        }
        String fitted = truncate(font, text, rect.width() - 4);
        int x = rect.x() + (rect.width() - font.width(fitted)) / 2;
        int y = rect.y() + (rect.height() - font.lineHeight) / 2 + 1;
        graphics.drawString(font, fitted, x, y, colour, false);
    }

    /** Draws text against the left edge of a rectangle, cut to fit. */
    public static void leftText(GuiGraphics graphics, Font font, Rect rect, String text, int colour) {
        if (rect.isEmpty() || text == null || text.isEmpty()) {
            return;
        }
        String fitted = truncate(font, text, rect.width());
        int y = rect.y() + (rect.height() - font.lineHeight) / 2 + 1;
        graphics.drawString(font, fitted, rect.x(), y, colour, false);
    }

    /** Draws text against the right edge, for numbers that should line up. */
    public static void rightText(GuiGraphics graphics, Font font, Rect rect, String text, int colour) {
        if (rect.isEmpty() || text == null || text.isEmpty()) {
            return;
        }
        String fitted = truncate(font, text, rect.width());
        int x = rect.right() - font.width(fitted);
        int y = rect.y() + (rect.height() - font.lineHeight) / 2 + 1;
        graphics.drawString(font, fitted, x, y, colour, false);
    }

    /**
     * Shortens text with an ellipsis until it fits.
     *
     * <p>Returns an empty string rather than a lone ellipsis when there is no room at all: a cell showing only
     * {@code ...} tells the reader nothing and still costs the pixels.</p>
     */
    public static String truncate(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth > maxWidth) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int used = ellipsisWidth;
        for (int i = 0; i < text.length(); i++) {
            int charWidth = font.width(String.valueOf(text.charAt(i)));
            if (used + charWidth > maxWidth) {
                break;
            }
            out.append(text.charAt(i));
            used += charWidth;
        }
        return out + ellipsis;
    }

    /** Draws a title in the accent colour, for band headers. */
    public static void title(GuiGraphics graphics, Font font, Rect rect, Component text) {
        leftText(graphics, font, rect, text.getString(), ACCENT);
    }
}
