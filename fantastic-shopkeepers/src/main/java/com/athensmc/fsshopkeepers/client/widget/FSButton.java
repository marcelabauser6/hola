package com.athensmc.fsshopkeepers.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The Fantastic family's button.
 *
 * <p>A port of the button the other Fantastic mods use, kept faithful down to the numbers: rounded two-pixel corners, a
 * drop shadow one pixel low, a body gradient tinted towards the button's accent colour, a bright sheen on the top edge
 * and an accent line along the bottom. Hovering lifts a glow up from the base and sends twenty motes drifting through
 * it, eased over 160 milliseconds rather than snapping on.</p>
 *
 * <p>The hover sound is rate-limited across every instance. Without that, dragging the cursor along a row of tabs plays
 * one click per button per frame, which is the sort of thing that makes people turn the UI volume off.</p>
 */
public class FSButton extends AbstractButton {

    /** How long the hover animation takes to reach full brightness. */
    private static final float HOVER_MS = 160.0F;

    /** How many motes drift up through a hovered button. */
    private static final int MOTES = 20;

    /** Shortest gap between two hover sounds, across all buttons. */
    private static final long HOVER_SOUND_GAP_MS = 90L;

    private static long lastHoverSoundMs;

    private final int accent;
    private final Runnable action;

    private boolean centered = true;
    private float hover;
    private long lastFrameMs = System.currentTimeMillis();
    private float clock;
    private boolean wasHovered;

    public FSButton(int x, int y, int width, int height, Component message, int accent, Runnable action) {
        super(x, y, width, height, message);
        this.accent = accent;
        this.action = action;
    }

    /** Draws the label against the left edge instead of centred, for list rows. */
    public FSButton leftAligned() {
        this.centered = false;
        return this;
    }

    @Override
    public void onPress() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.92F, 0.9F));
    }

    private void playHoverSound() {
        long now = System.currentTimeMillis();
        if (now - lastHoverSoundMs < HOVER_SOUND_GAP_MS) {
            return;
        }
        lastHoverSoundMs = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.45F, 0.32F));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        updateHover();
        float lit = active ? hover : 0.0F;

        rounded(graphics, x, y + 2, w, h, 0x55000000, 0x55000000);
        if (!active) {
            rounded(graphics, x, y, w, h, -15066598, -15066598);
            rounded(graphics, x + 1, y + 1, w - 2, h - 2, -11842741, -12961222);
        } else {
            int border = mix(-15460838, darken(accent, 0.55F), 0.25F + 0.75F * lit);
            rounded(graphics, x, y, w, h, border, border);
            int top = mix(-11380378, mix(accent, -1, 0.25F), 0.22F + 0.3F * lit);
            int bottom = mix(-13881030, darken(accent, 0.7F), 0.22F + 0.3F * lit);
            rounded(graphics, x + 1, y + 1, w - 2, h - 2, top, bottom);
            if (lit > 0.0F) {
                renderGlow(graphics, x, y, w, h, lit);
            }
            graphics.fill(x + 3, y + 1, x + w - 3, y + 2, 0x33FFFFFF);
            graphics.fill(x + 3, y + h - 2, x + w - 3, y + h - 1,
                    withAlpha(accent, (int) (130.0F + 100.0F * lit)));
        }

        int textColour = active ? -1 : -6645094;
        Font font = Minecraft.getInstance().font;
        int textY = y + (h - 9) / 2 + 1;
        if (centered) {
            graphics.drawCenteredString(font, getMessage(), x + w / 2, textY, textColour);
        } else {
            graphics.drawString(font, getMessage(), x + 8, textY, textColour, true);
        }
    }

    /**
     * The glow that rises from the bottom of a hovered button, plus its motes.
     *
     * <p>The glow breathes on a sine so a button left under the cursor keeps moving slightly, and each mote is faded in
     * over the first seventh of its travel so none of them pop into existence.</p>
     */
    private void renderGlow(GuiGraphics graphics, int x, int y, int w, int h, float lit) {
        float breath = 0.5F + 0.13F * (float) Math.sin(clock * 2.6F);
        float glowRows = (float) (h - 2) * breath * lit;
        int baseAlpha = (int) (255.0F * lit);
        for (int row = 0; row < h - 2; row++) {
            float up = (float) row / Math.max(1.0F, glowRows);
            if (up > 1.0F) {
                break;
            }
            float fade = (float) Math.pow(1.0F - up, 1.15);
            int alpha = (int) ((float) baseAlpha * fade);
            if (alpha <= 0) {
                continue;
            }
            int rowY = y + h - 2 - row;
            int inset = Math.max(1, cornerInset(rowY - y, h));
            graphics.fill(x + inset, rowY, x + w - inset, rowY + 1, withAlpha(accent, alpha));
        }

        int usable = w - 8;
        if (usable <= 0) {
            return;
        }
        for (int i = 0; i < MOTES; i++) {
            float speed = 1.6F + 0.55F * (float) (i * 5 % 7) / 7.0F;
            float phase = (clock * speed + (float) i * 0.7548F % 1.0F) % 1.0F;
            float travel = phase * ((float) h * 0.78F);
            float moteY = (float) (y + h - 2) - travel;
            if (moteY <= (float) (y + 2)) {
                continue;
            }
            int moteX = x + 4 + (int) ((0.13F + (float) i * 0.618F) % 1.0F * (float) usable);
            int alpha = (int) (235.0F * lit * (1.0F - phase) * Math.min(1.0F, phase * 7.0F));
            if (alpha <= 4) {
                continue;
            }
            int row = (int) moteY;
            float frac = moteY - (float) row;
            // Split across two rows so a mote moves smoothly rather than jumping a pixel at a time.
            int upper = (int) ((float) alpha * (1.0F - frac));
            int lower = (int) ((float) alpha * frac);
            if (upper > 3) {
                graphics.fill(moteX, row, moteX + 1, row + 1, withAlpha(-1, upper));
            }
            if (lower > 3 && row + 1 < y + h - 1) {
                graphics.fill(moteX, row + 1, moteX + 1, row + 2, withAlpha(-1, lower));
            }
        }
    }

    private void updateHover() {
        long now = System.currentTimeMillis();
        float step = (float) (now - lastFrameMs) / HOVER_MS;
        lastFrameMs = now;
        step = Math.min(1.0F, Math.max(0.0F, step));
        boolean target = isHovered() && active;
        if (target && !wasHovered) {
            playHoverSound();
        }
        wasHovered = target;
        hover = Math.max(0.0F, Math.min(1.0F, hover + (target ? step : -step)));
        clock += step * 0.16F;
    }

    private static void rounded(GuiGraphics graphics, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 4 || h <= 2) {
            graphics.fill(x, y, x + w, y + h, top);
            return;
        }
        for (int row = 0; row < h; row++) {
            int inset = cornerInset(row, h);
            float t = h == 1 ? 0.0F : (float) row / (float) (h - 1);
            graphics.fill(x + inset, y + row, x + w - inset, y + row + 1, mixKeepAlpha(top, bottom, t));
        }
    }

    /** Two pixels in at the very edge, one on the next row: a softer corner than the panel's. */
    private static int cornerInset(int row, int h) {
        if (row == 0 || row == h - 1) {
            return 2;
        }
        if (row == 1 || row == h - 2) {
            return 1;
        }
        return 0;
    }

    private static int mix(int a, int b, float amount) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = (int) ((float) ar + (float) (br - ar) * amount);
        int g = (int) ((float) ag + (float) (bg - ag) * amount);
        int bl = (int) ((float) ab + (float) (bb - ab) * amount);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int mixKeepAlpha(int a, int b, float amount) {
        int aa = a >>> 24;
        int ba = b >>> 24;
        int alpha = (int) ((float) aa + (float) (ba - aa) * amount);
        return (alpha & 0xFF) << 24 | mix(a, b, amount) & 0xFFFFFF;
    }

    private static int darken(int colour, float factor) {
        return mix(colour, -16777216, 1.0F - factor);
    }

    private static int withAlpha(int colour, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | colour & 0xFFFFFF;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
