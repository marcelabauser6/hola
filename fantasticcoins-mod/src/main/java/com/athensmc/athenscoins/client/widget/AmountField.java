package com.athensmc.athenscoins.client.widget;

import com.athensmc.athenscoins.wallet.AmountInput;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The one and only way a player types money in this mod.
 *
 * <p>Every amount box used to carry its own copy of a parse helper, and those copies disagreed
 * with each other: an input without a separator went through {@code Long.parseLong} and was taken
 * as <em>cents</em>, while an input with a separator went through {@link Money#parse} and was taken
 * as <em>units</em>. Typing {@code 5} moved five cents and typing {@code 5.00} moved five hundred,
 * a factor of a hundred inside the same field. On top of that a rejected string silently became
 * {@code 0}, the packet was sent anyway, and the four translated reasons {@code Money.parse}
 * already produces were never shown to anybody.</p>
 *
 * <p>This field fixes both halves. Interpretation is always {@link Money#parse}, so the value on
 * screen means what it reads: {@code 5} is five, {@code 5.50} is five and a half. And the filter
 * makes the third decimal untypable rather than diagnosing it afterwards, so the two-decimal rule
 * is enforced by the keyboard instead of by an error message.</p>
 */
public class AmountField extends EditBox {

    /** 15 digits, a separator and 2 decimals - matches {@link Money#parse}'s own ceiling. */
    public static final int MAX_LENGTH = 18;

    private Component error;
    /** True for policy fields where zero means something - "no ceiling", "free". */
    private boolean zeroAllowed;

    public AmountField(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setMaxLength(MAX_LENGTH);
        setFilter(AmountInput::typable);
        setHint(Component.translatable("gui.athens_coins.amount_hint"));
        setValue("");
    }

    /**
     * Lets this box accept zero.
     *
     * <p>Off by default, because most amount boxes move money and moving nothing is a mistake. On for the
     * settings whose own hint text offers zero as a choice - the card ceiling and the cross-bank charge -
     * which until now sat above a box that refused the value it was recommending.</p>
     */
    public AmountField allowingZero() {
        zeroAllowed = true;
        return this;
    }

    /** True when the player has not typed anything yet. */
    public boolean isBlank() {
        return getValue().trim().isEmpty();
    }

    /**
     * Reads the box as cents.
     *
     * @return the amount in cents, or {@code -1} when the box does not hold a usable amount, in
     *         which case {@link #error()} explains why in the player's language
     */
    public long cents() {
        error = null;
        String raw = getValue().trim();
        if (raw.isEmpty()) {
            error = Component.translatable("message.athens_coins.amount_missing");
            return -1L;
        }
        try {
            return Money.parse(raw, zeroAllowed);
        } catch (Money.InvalidAmountException exception) {
            error = Component.translatable(exception.reasonKey());
            return -1L;
        }
    }

    /** The reason the last {@link #cents()} call refused the input, or {@code null} if it did not. */
    public Component error() {
        return error;
    }

    /** Shows an amount in the canonical two-decimal form, for pre-filling and round-tripping. */
    public void setCents(long cents) {
        setValue(Money.plain(cents));
        error = null;
    }

    /** Empties the box after a successful action, so the amount cannot be sent twice by accident. */
    public void clear() {
        setValue("");
        error = null;
    }
}
