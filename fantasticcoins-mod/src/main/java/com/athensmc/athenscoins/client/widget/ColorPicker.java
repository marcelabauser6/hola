package com.athensmc.athenscoins.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Hue strip plus saturation/value square, the usual way to pick a colour.
 *
 * <p>Drawn without any texture: the saturation axis is a run of thin vertical strips
 * interpolating white to the pure hue, and the value axis is one gradient quad fading to black
 * over the top. That keeps it to about fifty draws a frame instead of one per pixel.</p>
 */
@OnlyIn(Dist.CLIENT)
public class ColorPicker {

    private static final int SV_STRIPS = 48;
    private static final int HUE_STRIPS = 60;

    private int x;
    private int y;
    private int width;
    private int height;
    private int hueHeight;

    private float hue;
    private float saturation;
    private float value = 1.0F;

    private boolean draggingSquare;
    private boolean draggingHue;

    public void setBounds(int x, int y, int width, int height, int hueHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.hueHeight = hueHeight;
    }

    // ------------------------------------------------------------------ colour conversion

    public static int hsvToRgb(float h, float s, float v) {
        float chroma = v * s;
        float hp = (h % 1.0F) * 6.0F;
        float x = chroma * (1.0F - Math.abs(hp % 2.0F - 1.0F));
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        if (hp < 1.0F) {
            r = chroma;
            g = x;
        } else if (hp < 2.0F) {
            r = x;
            g = chroma;
        } else if (hp < 3.0F) {
            g = chroma;
            b = x;
        } else if (hp < 4.0F) {
            g = x;
            b = chroma;
        } else if (hp < 5.0F) {
            r = x;
            b = chroma;
        } else {
            r = chroma;
            b = x;
        }
        float m = v - chroma;
        int ri = Math.round((r + m) * 255.0F);
        int gi = Math.round((g + m) * 255.0F);
        int bi = Math.round((b + m) * 255.0F);
        return (ri << 16) | (gi << 8) | bi;
    }

    /** Loads the picker from an existing RGB value so editing starts where the colour is. */
    public void setRgb(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        value = max;
        saturation = max == 0.0F ? 0.0F : delta / max;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta % 6.0F) / 6.0F;
        } else if (max == g) {
            hue = ((b - r) / delta + 2.0F) / 6.0F;
        } else {
            hue = ((r - g) / delta + 4.0F) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
    }

    public int rgb() {
        return hsvToRgb(hue, saturation, value);
    }

    // ------------------------------------------------------------------ rendering

    public void render(GuiGraphics graphics) {
        int pure = hsvToRgb(hue, 1.0F, 1.0F);

        // saturation across, white to the pure hue
        for (int i = 0; i < SV_STRIPS; i++) {
            int x0 = x + i * width / SV_STRIPS;
            int x1 = x + (i + 1) * width / SV_STRIPS;
            float t = SV_STRIPS == 1 ? 0.0F : i / (float) (SV_STRIPS - 1);
            graphics.fill(x0, y, x1, y + height, 0xFF000000 | lerp(0xFFFFFF, pure, t));
        }
        // value down, transparent to black
        graphics.fillGradient(x, y, x + width, y + height, 0x00000000, 0xFF000000);
        frame(graphics, x, y, width, height);

        // marker on the square
        int markerX = x + Math.round(saturation * (width - 1));
        int markerY = y + Math.round((1.0F - value) * (height - 1));
        crosshair(graphics, markerX, markerY);

        // hue strip
        int hueY = y + height + 4;
        for (int i = 0; i < HUE_STRIPS; i++) {
            int x0 = x + i * width / HUE_STRIPS;
            int x1 = x + (i + 1) * width / HUE_STRIPS;
            graphics.fill(x0, hueY, x1, hueY + hueHeight,
                    0xFF000000 | hsvToRgb(i / (float) HUE_STRIPS, 1.0F, 1.0F));
        }
        frame(graphics, x, hueY, width, hueHeight);
        int hueMarkX = x + Math.round(hue * (width - 1));
        graphics.fill(hueMarkX - 1, hueY - 2, hueMarkX + 2, hueY + hueHeight + 2, 0xFF000000);
        graphics.fill(hueMarkX, hueY - 1, hueMarkX + 1, hueY + hueHeight + 1, 0xFFFFFFFF);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, 0xFF000000);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF000000);
        graphics.fill(x - 1, y, x, y + h, 0xFF000000);
        graphics.fill(x + w, y, x + w + 1, y + h, 0xFF000000);
    }

    private static void crosshair(GuiGraphics graphics, int cx, int cy) {
        graphics.fill(cx - 3, cy, cx + 4, cy + 1, 0xFF000000);
        graphics.fill(cx, cy - 3, cx + 1, cy + 4, 0xFF000000);
        graphics.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFFFFFFF);
        graphics.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFFFFFFF);
    }

    private static int lerp(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ input

    /** @return true when the click landed on the picker */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, x, y, width, height)) {
            draggingSquare = true;
            applySquare(mouseX, mouseY);
            return true;
        }
        int hueY = y + height + 4;
        if (inside(mouseX, mouseY, x, hueY, width, hueHeight)) {
            draggingHue = true;
            applyHue(mouseX);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingSquare) {
            applySquare(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            applyHue(mouseX);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        draggingSquare = false;
        draggingHue = false;
    }

    private void applySquare(double mouseX, double mouseY) {
        saturation = clamp01((float) (mouseX - x) / Math.max(1, width - 1));
        value = 1.0F - clamp01((float) (mouseY - y) / Math.max(1, height - 1));
    }

    private void applyHue(double mouseX) {
        hue = clamp01((float) (mouseX - x) / Math.max(1, width - 1));
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public int totalHeight() {
        return height + 4 + hueHeight;
    }
}
