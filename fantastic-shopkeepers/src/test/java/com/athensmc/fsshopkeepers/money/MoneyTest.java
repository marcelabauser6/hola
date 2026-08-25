package com.athensmc.fsshopkeepers.money;

/**
 * The guard on prices: what an admin types becoming what a buyer is charged.
 *
 * <p>Every case here is a way a price can go wrong that would be invisible until money moved. Rounding a third
 * decimal, accepting a negative, wrapping a large total into a small one, or reading {@code "25,50"} as twenty-five.
 * A plain {@code main} for the same reason as the layout guard: no test framework needed.</p>
 */
public final class MoneyTest {

    private static int failures;

    public static void main(String[] args) {
        checkParsing();
        checkRejections();
        checkFormatting();
        checkArithmetic();

        if (failures > 0) {
            throw new AssertionError(failures + " comprobaciones de dinero fallaron.");
        }
        System.out.println("Dinero verificado: analisis, formato y aritmetica de precios.");
    }

    private static void checkParsing() {
        parses("25", 2500L);
        parses("25.00", 2500L);
        parses("25.5", 2550L);
        parses("25.50", 2550L);
        // A Spanish keyboard offers the comma, so a comma must mean the same as a dot.
        parses("25,50", 2550L);
        parses("0.01", 1L);
        parses("0", 0L);
        parses(".5", 50L);
        parses("  12.34  ", 1234L);
        parses("+7.25", 725L);
        parses("1000000", 100000000L);
    }

    private static void checkRejections() {
        rejects(null);
        rejects("");
        rejects("   ");
        rejects("-1");
        rejects("-0.01");
        // Three decimals is a typo, and rounding it would hide the typo behind a price nobody chose.
        rejects("1.005");
        rejects("1.2345");
        rejects("abc");
        rejects("1.2.3");
        rejects("12a.5");
        rejects("1e5");
        rejects("99999999999999999999");
    }

    private static void checkFormatting() {
        equalsText("plain(2500)", Money.plain(2500L), "25.00");
        equalsText("plain(1)", Money.plain(1L), "0.01");
        equalsText("plain(0)", Money.plain(0L), "0.00");
        equalsText("plain(2505)", Money.plain(2505L), "25.05");
        equalsText("format(2500)", Money.format(2500L, "$"), "25,00 $");
        // Six figures without separators is a number you have to count the digits of.
        equalsText("format(125000000)", Money.format(125000000L, "$"), "1.250.000,00 $");
        equalsText("format sin simbolo", Money.format(2500L, ""), "25,00");

        // Anything parsed then printed must survive the round trip unchanged.
        for (long cents = 0L; cents < 5000L; cents += 7L) {
            long round = Money.parseOrInvalid(Money.plain(cents));
            if (round != cents) {
                fail("ida y vuelta de " + cents + " dio " + round);
            }
        }
    }

    private static void checkArithmetic() {
        equalsLong("multiply(2500,4)", Money.multiply(2500L, 4L), 10000L);
        equalsLong("multiply(0,4)", Money.multiply(0L, 4L), 0L);
        equalsLong("multiply(2500,0)", Money.multiply(2500L, 0L), 0L);
        equalsLong("multiply negativo", Money.multiply(-5L, 4L), 0L);
        // Saturating rather than wrapping: a total too large must not come back small, still less negative.
        equalsLong("multiply saturado", Money.multiply(Money.MAX_CENTS, 1000L), Money.MAX_CENTS);
        if (Money.multiply(Long.MAX_VALUE / 2L, 4L) < 0L) {
            fail("multiply desbordo a negativo");
        }
        if (!Money.canAdd(1000L, 2000L)) {
            fail("canAdd rechazo una suma valida");
        }
        if (Money.canAdd(Money.MAX_CENTS, 1L)) {
            fail("canAdd acepto una suma que se sale del rango");
        }
        if (Money.canAdd(-1L, 5L)) {
            fail("canAdd acepto un operando negativo");
        }

        // The spendable total decides whether a purchase is allowed, so every odd input must make the buyer look
        // poorer and never richer. A version of this that answered MAX_CENTS on a bad reading let goods out for free.
        equalsLong("addSpendable normal", Money.addSpendable(1000L, 2500L), 3500L);
        equalsLong("addSpendable con cero", Money.addSpendable(0L, 0L), 0L);
        equalsLong("addSpendable primer negativo", Money.addSpendable(-5000L, 2500L), 2500L);
        equalsLong("addSpendable segundo negativo", Money.addSpendable(2500L, -5000L), 2500L);
        equalsLong("addSpendable ambos negativos", Money.addSpendable(-1L, -1L), 0L);
        equalsLong("addSpendable saturado", Money.addSpendable(Money.MAX_CENTS, Money.MAX_CENTS),
                Money.MAX_CENTS);
        equalsLong("addSpendable al limite", Money.addSpendable(Money.MAX_CENTS, 0L), Money.MAX_CENTS);
        if (Money.addSpendable(Long.MAX_VALUE, Long.MAX_VALUE) != Money.MAX_CENTS) {
            fail("addSpendable no saturo con dos valores enormes");
        }
        if (Money.addSpendable(Long.MIN_VALUE, Long.MIN_VALUE) != 0L) {
            fail("addSpendable no trato dos negativos extremos como cero");
        }
        for (long wallet = -2000L; wallet <= 2000L; wallet += 250L) {
            for (long account = -2000L; account <= 2000L; account += 250L) {
                long total = Money.addSpendable(wallet, account);
                if (total < 0L || total > Money.MAX_CENTS) {
                    fail("addSpendable(" + wallet + ", " + account + ") dio " + total);
                }
                long expected = Math.max(0L, wallet) + Math.max(0L, account);
                if (total != expected) {
                    fail("addSpendable(" + wallet + ", " + account + ") dio " + total
                            + " y se esperaba " + expected);
                }
            }
        }
        equalsLong("clamp negativo", Money.clamp(-50L), 0L);
        equalsLong("clamp excesivo", Money.clamp(Money.MAX_CENTS + 1L), Money.MAX_CENTS);
        equalsLong("parseOrInvalid malo", Money.parseOrInvalid("nope"), -1L);
    }

    private static void parses(String text, long expected) {
        try {
            long actual = Money.parse(text);
            if (actual != expected) {
                fail("parse(\"" + text + "\") dio " + actual + " y se esperaba " + expected);
            }
        } catch (Money.InvalidAmountException rejected) {
            fail("parse(\"" + text + "\") se rechazo: " + rejected.getMessage());
        }
    }

    private static void rejects(String text) {
        try {
            long value = Money.parse(text);
            fail("parse(\"" + text + "\") devolvio " + value + " en vez de rechazarlo");
        } catch (Money.InvalidAmountException expected) {
            // This is the required outcome.
        }
    }

    private static void equalsText(String what, String actual, String expected) {
        if (!expected.equals(actual)) {
            fail(what + " dio \"" + actual + "\" y se esperaba \"" + expected + "\"");
        }
    }

    private static void equalsLong(String what, long actual, long expected) {
        if (actual != expected) {
            fail(what + " dio " + actual + " y se esperaba " + expected);
        }
    }

    private static void fail(String message) {
        failures++;
        System.err.println("  - " + message);
    }
}
