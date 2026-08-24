package com.athensmc.athenscoins.wallet;

/** Standalone checks on the Fantastic Cash arithmetic. Money has no Minecraft dependencies. */
public class MoneyStaticTest {

    static int failures = 0;
    static int checks = 0;

    static void eq(String label, Object actual, Object expected) {
        checks++;
        if (!String.valueOf(actual).equals(String.valueOf(expected))) {
            failures++;
            System.out.println("  FAIL " + label + ": got <" + actual + "> expected <" + expected + ">");
        }
    }

    static void parseOk(String input, long expectedCents) {
        checks++;
        try {
            long cents = Money.parse(input);
            if (cents != expectedCents) {
                failures++;
                System.out.println("  FAIL parse(\"" + input + "\"): got " + cents
                        + " expected " + expectedCents);
            }
        } catch (Money.InvalidAmountException exception) {
            failures++;
            System.out.println("  FAIL parse(\"" + input + "\") rejected: " + exception.reasonKey());
        }
    }

    static void parseZeroAccepts(String input, long expectedCents) {
        checks++;
        try {
            long cents = Money.parse(input, true);
            if (cents != expectedCents) {
                failures++;
                System.out.println("  FAIL parse(\"" + input + "\", allowZero): got " + cents
                        + " expected " + expectedCents);
            }
        } catch (Money.InvalidAmountException exception) {
            failures++;
            System.out.println("  FAIL parse(\"" + input + "\", allowZero) rejected: "
                    + exception.reasonKey());
        }
    }

    static void parseZeroRejects(String input, String expectedReasonSuffix) {
        checks++;
        try {
            Money.parse(input, true);
            failures++;
            System.out.println("  FAIL parse(\"" + input + "\", allowZero) should have been rejected");
        } catch (Money.InvalidAmountException exception) {
            if (!exception.reasonKey().endsWith(expectedReasonSuffix)) {
                failures++;
                System.out.println("  FAIL parse(\"" + input + "\", allowZero) reason: got "
                        + exception.reasonKey() + " expected *" + expectedReasonSuffix);
            }
        }
    }

    static void parseRejects(String input, String expectedReasonSuffix) {
        checks++;
        try {
            Money.parse(input);
            failures++;
            System.out.println("  FAIL parse(\"" + input + "\") should have been rejected");
        } catch (Money.InvalidAmountException exception) {
            if (!exception.reasonKey().endsWith(expectedReasonSuffix)) {
                failures++;
                System.out.println("  FAIL parse(\"" + input + "\") reason: got "
                        + exception.reasonKey() + " expected *" + expectedReasonSuffix);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("-- parsing: accepted --");
        parseOk("34", 3400);
        parseOk("34.5", 3450);
        parseOk("34.50", 3450);
        parseOk("1.25", 125);
        parseOk("0.01", 1);
        parseOk("0.15", 15);
        parseOk("1000000", 100000000L);
        parseOk("$34.50", 3450);          // symbol tolerated
        parseOk("34,50", 3450);           // comma decimal tolerated
        parseOk("  7.07  ", 707);         // whitespace tolerated

        System.out.println("-- parsing: rejected (the 2-decimal rule) --");
        parseRejects("1.2543", "amount_decimals");
        parseRejects("1.234", "amount_decimals");
        parseRejects("0.001", "amount_decimals");
        parseRejects("0", "amount_positive");
        parseRejects("-5", "amount_positive");

        // Policy fields accept zero, amounts to be moved do not. Both directions are pinned, because the
        // bug this fixes was a settings box refusing the value its own hint text recommended - and the
        // opposite mistake, letting somebody transfer nothing, is just as easy to reintroduce.
        parseZeroAccepts("0", 0L);
        parseZeroAccepts("0.00", 0L);
        parseZeroAccepts("2.50", 250L);
        parseZeroRejects("-0.01", "amount_negative");
        parseZeroRejects("-5", "amount_negative");
        parseZeroRejects("1.234", "amount_decimals");
        parseRejects("abc", "amount_invalid");
        parseRejects("", "amount_invalid");
        parseRejects("1e5", "amount_invalid");        // scientific notation must not sneak through
        parseRejects("1E5", "amount_invalid");
        parseRejects("0x10", "amount_invalid");
        parseRejects("+5", "amount_invalid");
        parseRejects("5.", "amount_invalid");
        parseRejects("..5", "amount_invalid");
        parseRejects("1.2.3", "amount_invalid");
        parseRejects("Infinity", "amount_invalid");
        parseRejects("NaN", "amount_invalid");
        parseRejects("99999999999999", "amount_too_big");

        System.out.println("-- config values to cents --");
        eq("fromConfig(0.15)", Money.fromConfig(0.15D), 15L);
        eq("fromConfig(1.35)", Money.fromConfig(1.35D), 135L);
        eq("fromConfig(12.15)", Money.fromConfig(12.15D), 1215L);
        eq("fromConfig(0.1)", Money.fromConfig(0.1D), 10L);
        eq("fromConfig(0.155 rounds)", Money.fromConfig(0.155D), 16L);

        System.out.println("-- the exchange rate is exact at 2 decimals --");
        // 9 bronze must equal exactly 1 silver, 9 silver exactly 1 gold
        eq("9 x bronze == silver", Money.multiply(15L, 9), 135L);
        eq("9 x silver == gold", Money.multiply(135L, 9), 1215L);
        eq("81 x bronze == gold", Money.multiply(15L, 81), 1215L);

        System.out.println("-- formatting always shows exactly 2 decimals --");
        eq("format(3400)", Money.format(3400L, "$"), "$34.00");
        eq("format(3450)", Money.format(3450L, "$"), "$34.50");
        eq("format(125)", Money.format(125L, "$"), "$1.25");
        eq("format(1)", Money.format(1L, "$"), "$0.01");
        eq("format(0)", Money.format(0L, "$"), "$0.00");
        eq("format(15)", Money.format(15L, "$"), "$0.15");
        eq("format(123456789)", Money.format(123456789L, "$"), "$1,234,567.89");
        eq("format with other symbol", Money.format(500L, "FC"), "FC5.00");
        eq("plain(3450)", Money.plain(3450L), "34.50");
        eq("plain(1)", Money.plain(1L), "0.01");

        System.out.println("-- round trip: parse then format is stable --");
        for (String input : new String[] { "0.01", "0.15", "1.25", "34.50", "999.99", "1234.05" }) {
            checks++;
            try {
                String out = Money.plain(Money.parse(input));
                String expected = new java.math.BigDecimal(input)
                        .setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
                if (!out.equals(expected)) {
                    failures++;
                    System.out.println("  FAIL round trip " + input + " -> " + out);
                }
            } catch (Exception exception) {
                failures++;
                System.out.println("  FAIL round trip " + input + ": " + exception);
            }
        }

        System.out.println("-- fees --");
        eq("afterFee(1000, 0)", Money.afterFee(1000L, 0), 1000L);
        eq("afterFee(1000, 10)", Money.afterFee(1000L, 10), 900L);
        eq("afterFee(15, 10) floors", Money.afterFee(15L, 10), 14L);
        eq("plusFee(1000, 0)", Money.plusFee(1000L, 0), 1000L);
        eq("plusFee(1000, 10)", Money.plusFee(1000L, 10), 1100L);

        System.out.println("-- overflow and clamping --");
        eq("multiply saturates", Money.multiply(1000L, Long.MAX_VALUE) <= Money.MAX_CENTS, true);
        eq("multiply by 0", Money.multiply(1000L, 0), 0L);
        eq("multiply negative count", Money.multiply(1000L, -5), 0L);
        eq("clamp negative", Money.clampBalance(-100L), 0L);
        eq("clamp over max", Money.clampBalance(Long.MAX_VALUE), Money.MAX_CENTS);
        eq("can add exactly to max", Money.canAdd(Money.MAX_CENTS - 1L, 1L), true);
        eq("cannot add beyond max", Money.canAdd(Money.MAX_CENTS, 1L), false);
        eq("cannot add wrapped negative", Money.canAdd(Long.MAX_VALUE, Long.MAX_VALUE), false);
        eq("exact add", Money.addExact(125L, 375L), 500L);
        checks++;
        try {
            Money.addExact(Money.MAX_CENTS, 1L);
            failures++;
            System.out.println("  FAIL addExact overflow should throw");
        } catch (ArithmeticException expected) {
            // expected: financial mutations must reject rather than saturate
        }
        eq("exact subtraction", Money.subtractExact(500L, 125L), 375L);

        System.out.println("-- compact forms --");
        eq("compact(50000)", Money.compact(50000L, "$"), "$500.00");
        eq("compact(150000)", Money.compact(150000L, "$"), "$1.5k");
        eq("compactCount(999)", Money.compactCount(999L), "999");
        eq("compactCount(1500)", Money.compactCount(1500L), "1.5k");

        System.out.println();
        System.out.println(checks + " checks, " + failures + " failure(s)");
        if (failures > 0) {
            System.exit(1);
        }
        System.out.println("MONEY MATH OK");
    }
}
