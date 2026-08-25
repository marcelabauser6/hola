package com.athensmc.fsshopkeepers.trade;

import com.athensmc.fsshopkeepers.money.Money;

/**
 * The guard on paying for things.
 *
 * <p>Free purchases got out of this mod twice, and both times because the rule about paying lived inside one code path while
 * another had no rule at all. This checks the rule itself, exhaustively, on a plain JVM: no balance below a price is ever
 * allowed through, a row that asks for nothing is refused rather than honoured, and no combination of price and quantity
 * produces a total that has silently wrapped.</p>
 */
public final class TradeGateTest {

    private static int failures;

    public static void main(String[] args) {
        checkCannotBuyWithoutMoney();
        checkFreeRowsRefused();
        checkItemOnlyRows();
        checkNoEconomy();
        checkTotals();
        checkExhaustively();

        if (failures > 0) {
            throw new AssertionError(failures + " comprobaciones de cobro fallaron.");
        }
        System.out.println("Cobro verificado: no se entrega nada sin pagarlo.");
    }

    /** The case that matters: a price above the balance must always be refused. */
    private static void checkCannotBuyWithoutMoney() {
        verdict("sin nada y con precio", TradeGate.check(2300L, 1, false, true, 0L),
                TradeGate.Verdict.TOO_EXPENSIVE);
        verdict("un centimo de menos", TradeGate.check(2300L, 1, false, true, 2299L),
                TradeGate.Verdict.TOO_EXPENSIVE);
        verdict("justo lo que cuesta", TradeGate.check(2300L, 1, false, true, 2300L),
                TradeGate.Verdict.ALLOWED);
        verdict("de sobra", TradeGate.check(2300L, 1, false, true, 1_000_000L),
                TradeGate.Verdict.ALLOWED);
        // Buying several at once must be judged on the total, not on one unit.
        verdict("ocho a la vez sin saldo", TradeGate.check(2300L, 8, false, true, 2300L),
                TradeGate.Verdict.TOO_EXPENSIVE);
        verdict("ocho a la vez con saldo justo", TradeGate.check(2300L, 8, false, true, 18400L),
                TradeGate.Verdict.ALLOWED);
        // A negative balance must never read as affordable.
        verdict("saldo negativo", TradeGate.check(100L, 1, false, true, -5000L),
                TradeGate.Verdict.TOO_EXPENSIVE);
    }

    /** A row that asks for neither money nor items is a mistake, not a giveaway. */
    private static void checkFreeRowsRefused() {
        verdict("nada de nada", TradeGate.check(0L, 1, false, true, 0L),
                TradeGate.Verdict.NOTHING_ASKED);
        verdict("nada de nada con saldo", TradeGate.check(0L, 1, false, true, 999_999L),
                TradeGate.Verdict.NOTHING_ASKED);
        verdict("nada de nada sin economia", TradeGate.check(0L, 1, false, false, 0L),
                TradeGate.Verdict.NOTHING_ASKED);
    }

    /** Item-only rows need no money, and must not be blocked for lacking it. */
    private static void checkItemOnlyRows() {
        verdict("solo articulos", TradeGate.check(0L, 1, true, true, 0L), TradeGate.Verdict.ALLOWED);
        verdict("solo articulos sin economia", TradeGate.check(0L, 1, true, false, 0L),
                TradeGate.Verdict.ALLOWED);
        verdict("solo articulos varias veces", TradeGate.check(0L, 8, true, false, 0L),
                TradeGate.Verdict.ALLOWED);
        // Items plus money still needs the money.
        verdict("articulos y dinero sin saldo", TradeGate.check(500L, 1, true, true, 0L),
                TradeGate.Verdict.TOO_EXPENSIVE);
    }

    /** A priced row cannot be paid for when there is no money system at all. */
    private static void checkNoEconomy() {
        verdict("precio sin economia", TradeGate.check(2300L, 1, false, false, 0L),
                TradeGate.Verdict.NO_ECONOMY);
        // Not even when the reported balance looks generous, since it cannot be taken.
        verdict("precio sin economia con saldo", TradeGate.check(2300L, 1, false, false, 999_999L),
                TradeGate.Verdict.NO_ECONOMY);
    }

    private static void checkTotals() {
        equalsLong("total de uno", TradeGate.totalPrice(2300L, 1), 2300L);
        equalsLong("total de ocho", TradeGate.totalPrice(2300L, 8), 18400L);
        equalsLong("total gratis", TradeGate.totalPrice(0L, 8), 0L);
        equalsLong("total de cantidad invalida", TradeGate.totalPrice(2300L, 0), -1L);
        equalsLong("total de precio negativo", TradeGate.totalPrice(-1L, 1), -1L);
        // Too large to represent must be refused, not capped: a capped total charges a price nobody agreed to.
        equalsLong("total imposible", TradeGate.totalPrice(Money.MAX_CENTS, 2), -1L);
        equalsLong("total al limite", TradeGate.totalPrice(Money.MAX_CENTS, 1), Money.MAX_CENTS);
    }

    /**
     * Sweeps the whole space of prices, quantities and balances.
     *
     * <p>The invariant is the one that was broken in the field: whenever a trade is allowed and money was asked for, the
     * balance must genuinely cover the total. Nothing else about the verdict matters as much as that never being false.</p>
     */
    private static void checkExhaustively() {
        long[] prices = {0L, 1L, 99L, 100L, 2300L, 1_000_000L, Money.MAX_CENTS - 1L, Money.MAX_CENTS};
        int[] counts = {1, 2, 8, 64, 1000};
        long[] balances = {Long.MIN_VALUE, -1L, 0L, 1L, 99L, 2299L, 2300L, 18400L, Money.MAX_CENTS,
                Long.MAX_VALUE};

        for (long price : prices) {
            for (int times : counts) {
                for (long balance : balances) {
                    for (boolean items : new boolean[] {false, true}) {
                        for (boolean economy : new boolean[] {false, true}) {
                            TradeGate.Verdict verdict = TradeGate.check(price, times, items, economy, balance);
                            if (!verdict.allowed()) {
                                continue;
                            }
                            if (price == 0L) {
                                if (!items) {
                                    fail("permitio un trato que no pide nada (x" + times + ")");
                                }
                                continue;
                            }
                            if (!economy) {
                                fail("permitio cobrar sin economia (precio " + price + ")");
                                continue;
                            }
                            long total = TradeGate.totalPrice(price, times);
                            if (total < 0L) {
                                fail("permitio un total imposible (precio " + price + " x" + times + ")");
                                continue;
                            }
                            if (balance < total) {
                                fail("permitio comprar por " + total + " con un saldo de " + balance);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void verdict(String what, TradeGate.Verdict actual, TradeGate.Verdict expected) {
        if (actual != expected) {
            fail(what + ": dio " + actual + " y se esperaba " + expected);
        }
    }

    private static void equalsLong(String what, long actual, long expected) {
        if (actual != expected) {
            fail(what + ": dio " + actual + " y se esperaba " + expected);
        }
    }

    private static void fail(String message) {
        failures++;
        System.err.println("  - " + message);
    }
}
