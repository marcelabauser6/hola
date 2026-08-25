package com.athensmc.athenscoins.client.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.IntConsumer;

/**
 * A slider over a plain integer range, labelled with its own value.
 *
 * <p>Vanilla's slider works in a 0..1 double and leaves the mapping to the caller, which is fine once
 * and a trap five times: the hologram editor has sliders for scale, line spacing, height and the number
 * of leaderboard places, and each hand-rolled conversion is a chance to be off by one at an end stop.
 * This does the mapping once and rounds, so dragging to either extreme lands exactly on the bound.</p>
 *
 * <p>The label carries the value because a slider with no readout is a guess. The editor's sliders set
 * things like "scale 140%" that an admin will want to reproduce on another projector.</p>
 */
@OnlyIn(Dist.CLIENT)
public class IntSlider extends AbstractSliderButton {

    private final String labelKey;
    private final String suffix;
    private final int min;
    private final int max;
    private final IntConsumer onChange;

    public IntSlider(int x, int y, int width, int height, String labelKey, String suffix,
                     int min, int max, int initial, IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), fraction(initial, min, max));
        this.labelKey = labelKey;
        this.suffix = suffix == null ? "" : suffix;
        this.min = min;
        this.max = Math.max(min, max);
        this.onChange = onChange;
        updateMessage();
    }

    private static double fraction(int value, int min, int max) {
        if (max <= min) {
            return 0.0D;
        }
        int clamped = Math.max(min, Math.min(max, value));
        return (clamped - min) / (double) (max - min);
    }

    public int intValue() {
        return (int) Math.round(min + value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable(labelKey)
                .append(Component.literal(": " + intValue() + suffix)));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }
}
