package com.athensmc.athenscoins.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Panel chrome shared by every screen in the mod.
 *
 * <p>{@link #outline} used to live on {@code StatsScreen} as a package-private static, which meant
 * five unrelated screens reached into the stats screen to draw their own border. That was fine until
 * the stats screen was replaced by the hologram editor and the helper had to survive the rewrite.
 * A border is not the stats screen's business; it lives here.</p>
 */
public final class Panels {

    private Panels() {
    }

    /** A one-pixel border just inside the given rectangle. */
    public static void outline(GuiGraphics graphics, int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + 1, argb);
        graphics.fill(x, y + h - 1, x + w, y + h, argb);
        graphics.fill(x, y, x + 1, y + h, argb);
        graphics.fill(x + w - 1, y, x + w, y + h, argb);
    }
}
