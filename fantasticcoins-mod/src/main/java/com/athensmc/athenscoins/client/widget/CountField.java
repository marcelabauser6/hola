package com.athensmc.athenscoins.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A whole-number entry box, for things that are counted rather than paid.
 *
 * <p>Coins are items: you exchange three of them, never 3.50. Reusing {@link AmountField} here would
 * reintroduce exactly the ambiguity it was written to remove - a box that sometimes means units and
 * sometimes means hundredths - so counts get their own field whose filter admits no separator at
 * all.</p>
 */
public class CountField extends EditBox {

    /** A stack count can never approach this, and it keeps the parse inside an int. */
    private static final int MAX_DIGITS = 7;

    private Component error;

    public CountField(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setMaxLength(MAX_DIGITS);
        setFilter(CountField::typable);
        setHint(Component.translatable("gui.athens_coins.count_hint"));
        setValue("");
    }

    static boolean typable(String candidate) {
        return candidate != null && candidate.matches("^\\d{0," + MAX_DIGITS + "}$");
    }

    /**
     * Reads the box as a positive count.
     *
     * @return the count, or {@code -1} when the box is empty or not a positive number, in which case
     *         {@link #error()} explains why
     */
    public int count() {
        error = null;
        String raw = getValue().trim();
        if (raw.isEmpty()) {
            error = Component.translatable("message.athens_coins.amount_missing");
            return -1;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                error = Component.translatable("message.athens_coins.amount_positive");
                return -1;
            }
            return value;
        } catch (NumberFormatException ignored) {
            error = Component.translatable("message.athens_coins.amount_invalid");
            return -1;
        }
    }

    public Component error() {
        return error;
    }

    public void clear() {
        setValue("");
        error = null;
    }
}
