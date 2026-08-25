package com.athensmc.shopeditor.trade;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the price field's parsing, which is the one place an admin's typing becomes money.
 *
 * <p>Runs on a plain JVM. Every case here is a shop priced wrongly if it goes through: a comma refused on a
 * Spanish keyboard, a third decimal quietly rounded, or a price that wraps negative and pays the buyer.</p>
 */
public final class TradeLineStaticTest {

    private static final List<String> FAILURES = new ArrayList<>();

    private TradeLineStaticTest() {
    }

    public static void main(String[] args) {
        checkWholeNumbers();
        checkDecimals();
        checkCommaIsAccepted();
        checkNonsenseIsRefused();
        checkTooManyDecimalsAreRefused();
        checkPriceTextRoundTrips();
        checkTradeValidation();

        if (!FAILURES.isEmpty()) {
            System.err.println("Trade line check failed:");
            FAILURES.forEach(failure -> System.err.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("Trade lines OK: price parsing, formatting and validation all behave.");
    }

    private static void checkWholeNumbers() {
        expect(TradeLine.parsePrice("25") == 2500L, "25 should be 2500 cents");
        expect(TradeLine.parsePrice("0") == 0L, "0 is a valid price to type, even if free");
        expect(TradeLine.parsePrice("1") == 100L, "1 should be 100 cents");
        expect(TradeLine.parsePrice(" 7 ") == 700L, "surrounding spaces should be ignored");
    }

    private static void checkDecimals() {
        expect(TradeLine.parsePrice("25.50") == 2550L, "25.50 should be 2550 cents");
        expect(TradeLine.parsePrice("0.01") == 1L, "one cent");
        expect(TradeLine.parsePrice("0.1") == 10L, "one decimal means tenths, so 10 cents");
        expect(TradeLine.parsePrice(".5") == 50L, "a leading dot is understood as 0.5");
        expect(TradeLine.parsePrice("3.") == 300L, "a trailing dot is understood as 3.00");
    }

    /** A Spanish keyboard offers the comma, and refusing it would be a silly way to reject a price. */
    private static void checkCommaIsAccepted() {
        expect(TradeLine.parsePrice("25,50") == 2550L, "a comma should work like a dot");
        expect(TradeLine.parsePrice("0,99") == 99L, "0,99 should be 99 cents");
    }

    private static void checkNonsenseIsRefused() {
        expect(TradeLine.parsePrice(null) == -1L, "no text is not a price");
        expect(TradeLine.parsePrice("") == -1L, "empty is not a price");
        expect(TradeLine.parsePrice("   ") == -1L, "blank is not a price");
        expect(TradeLine.parsePrice("abc") == -1L, "letters are not a price");
        expect(TradeLine.parsePrice("-5") == -1L, "a negative price would pay the buyer");
        expect(TradeLine.parsePrice("1.2.3") == -1L, "two dots is not a price");
        expect(TradeLine.parsePrice("9999999999999999999") == -1L,
                "a price too large for cents is refused rather than wrapped");
    }

    /**
     * A third decimal is refused, not rounded.
     *
     * <p>1.005 is a typo. Turning it into 1.00 or 1.01 hides the mistake, and the admin never finds out which
     * of the two their shop is charging.</p>
     */
    private static void checkTooManyDecimalsAreRefused() {
        expect(TradeLine.parsePrice("1.005") == -1L, "three decimals cannot be charged");
        expect(TradeLine.parsePrice("0.001") == -1L, "a tenth of a cent cannot be charged");
    }

    private static void checkPriceTextRoundTrips() {
        for (long cents : new long[]{0L, 1L, 50L, 99L, 100L, 2550L, 123456L}) {
            TradeLine line = new TradeLine("minecraft:diamond", 1, cents);
            long reparsed = TradeLine.parsePrice(line.priceText());
            expect(reparsed == cents, "price " + cents + " formatted as '" + line.priceText()
                    + "' came back as " + reparsed);
        }
    }

    private static void checkTradeValidation() {
        expectThrows(() -> new TradeLine(null, 1, 100L), "an item is required");
        expectThrows(() -> new TradeLine("", 1, 100L), "a blank item is refused");
        expectThrows(() -> new TradeLine("minecraft:diamond", 0, 100L), "an amount of zero is refused");
        expectThrows(() -> new TradeLine("minecraft:diamond", 65, 100L),
                "more than a stack cannot be handed over");
        expectThrows(() -> new TradeLine("minecraft:diamond", 1, -1L), "a negative price is refused");

        TradeLine free = new TradeLine("minecraft:dirt", 1, 0L);
        expect(free.isFree(), "a price of zero should be flagged as free");
        expect(!new TradeLine("minecraft:dirt", 1, 1L).isFree(), "one cent is not free");
    }

    private static void expect(boolean condition, String what) {
        if (!condition) {
            FAILURES.add("failed: " + what);
        }
    }

    private static void expectThrows(Runnable work, String what) {
        try {
            work.run();
            FAILURES.add("should have been refused: " + what);
        } catch (IllegalArgumentException expected) {
            // Correct.
        }
    }
}
