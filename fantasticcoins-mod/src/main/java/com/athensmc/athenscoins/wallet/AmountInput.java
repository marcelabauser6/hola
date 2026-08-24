package com.athensmc.athenscoins.wallet;

import java.util.regex.Pattern;

/**
 * The keyboard-level half of the two-decimal rule, kept free of Minecraft so it can be tested
 * from a plain JVM.
 *
 * <p>{@link Money#parse} decides whether a finished amount is acceptable. This decides whether a
 * <em>half-typed</em> one may stay in the box, which is a different question: {@code ""} and
 * {@code "12."} are not valid amounts but are unavoidable states on the way to one, so a filter
 * that only accepted valid amounts would make the field impossible to fill.</p>
 */
public final class AmountInput {

    /**
     * Empty, or at least one integer digit optionally followed by a separator and up to two decimals.
     *
     * <p>The leading {@code \d{1,15}} is not decorative. Written as {@code \d{0,15}} the whole integer
     * part becomes optional, which makes {@code "."} and {@code ".5"} typable - and {@link Money#parse}
     * rejects both, so the box would happily accept a value that could never be submitted. The
     * alternation keeps the empty box legal, since that is where every entry starts.</p>
     */
    private static final Pattern TYPABLE = Pattern.compile("^(\\d{1,15}([.,]\\d{0,2})?)?$");

    private AmountInput() {
    }

    /**
     * True when {@code candidate} is a prefix of some valid amount.
     *
     * <p>Capping the decimals here is what makes a third decimal <em>untypable</em> rather than
     * diagnosed after the fact: the player's keystroke is simply dropped, so the box can never show
     * a precision the mod would go on to refuse.</p>
     */
    public static boolean typable(String candidate) {
        return candidate != null && TYPABLE.matcher(candidate).matches();
    }
}
