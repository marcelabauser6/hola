package com.athensmc.fcbridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which Fantastic Coins can serve as Shopkeepers' two currencies.
 *
 * <p>This exists because the two systems count money differently, and the mismatch is easy to miss until
 * prices are quietly wrong.</p>
 *
 * <ul>
 *   <li><strong>FantasticCurrency</strong> values each coin in <em>cents</em>. Any coin can be worth any
 *       number of cents, set in its config and rebalanced at will.</li>
 *   <li><strong>Shopkeepers</strong> prices everything as a whole number of <em>base currency units</em>,
 *       and supports exactly two denominations: a base worth 1 and an optional high one worth an
 *       {@code int} multiple of the base.</li>
 * </ul>
 *
 * <p>So the base coin has to be the cheapest, and the high coin's value has to be an exact multiple of it.
 * If gold is worth 500 cents and bronze 3, there is no integer that expresses gold in bronzes, and letting
 * Shopkeepers round it would mean every gold-priced trade silently loses or invents money. That case is
 * reported rather than approximated - a shop that cannot be set up is recoverable, a shop that
 * miscounts by a few cents on every sale is not.</p>
 *
 * <p>Pure arithmetic, no Bukkit and no reflection, so the mapping can be checked directly.</p>
 */
public final class Denominations {

    /** Shopkeepers' own ceiling on a currency's value, and on any price. */
    public static final int MAX_CURRENCY_VALUE = Integer.MAX_VALUE;

    /** One coin, as FantasticCurrency describes it. */
    public record Coin(String id, String displayName, long valueCents) {
    }

    /** The chosen pairing, ready to hand to Shopkeepers. */
    public record Plan(Coin base, Coin high, int highValue, List<String> notes) {

        /** True when a second denomination is in use. */
        public boolean hasHigh() {
            return high != null && highValue > 1;
        }
    }

    /** Raised when no usable pairing exists, carrying the reason for the admin. */
    public static final class Unusable extends Exception {
        public Unusable(String message) {
            super(message);
        }
    }

    private Denominations() {
    }

    /**
     * Chooses the base and high currencies from the available coins.
     *
     * <p>The base is the cheapest coin with a positive value, because everything else has to be a whole
     * multiple of it. The high one is the most valuable coin that divides evenly by the base; coins that do
     * not divide evenly are left out and named in the notes, so an admin can see why their gold is not
     * being offered rather than wondering.</p>
     */
    public static Plan plan(List<Coin> coins) throws Unusable {
        List<Coin> usable = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (Coin coin : coins) {
            if (coin.valueCents() <= 0L) {
                notes.add(coin.displayName() + " se omite: su valor es "
                        + coin.valueCents() + " céntimos.");
                continue;
            }
            usable.add(coin);
        }
        if (usable.isEmpty()) {
            throw new Unusable("Ninguna moneda tiene un valor positivo en la configuración de "
                    + "FantasticCurrency, así que no hay con qué comerciar.");
        }

        usable.sort((left, right) -> Long.compare(left.valueCents(), right.valueCents()));
        Coin base = usable.get(0);

        Coin high = null;
        long highMultiple = 1L;
        for (int i = usable.size() - 1; i >= 1; i--) {
            Coin candidate = usable.get(i);
            if (candidate.valueCents() % base.valueCents() != 0L) {
                notes.add(candidate.displayName() + " no se puede usar: " + candidate.valueCents()
                        + " céntimos no es múltiplo exacto de " + base.valueCents() + ".");
                continue;
            }
            long multiple = candidate.valueCents() / base.valueCents();
            if (multiple > MAX_CURRENCY_VALUE) {
                notes.add(candidate.displayName() + " no se puede usar: vale " + multiple
                        + " veces la moneda base, más de lo que Shopkeepers admite.");
                continue;
            }
            high = candidate;
            highMultiple = multiple;
            break;
        }

        // A coin that divides evenly but is worth the same as the base is not a second denomination, it
        // is the same denomination twice - and Shopkeepers would offer two identical currencies.
        if (high != null && highMultiple <= 1L) {
            notes.add(high.displayName() + " vale lo mismo que la moneda base, así que no se usa como "
                    + "segunda moneda.");
            high = null;
            highMultiple = 1L;
        }

        for (Coin coin : usable) {
            if (coin != base && coin != high && coin.valueCents() % base.valueCents() == 0L) {
                notes.add(coin.displayName() + " queda sin usar: Shopkeepers solo admite dos monedas.");
            }
        }

        return new Plan(base, high, (int) highMultiple, List.copyOf(notes));
    }

    /**
     * Converts a price in cents to whole base-currency units.
     *
     * @return the number of units, or -1 when the amount is not an exact number of them.
     */
    public static long unitsFor(long cents, long baseValueCents) {
        if (baseValueCents <= 0L || cents < 0L) {
            return -1L;
        }
        return cents % baseValueCents == 0L ? cents / baseValueCents : -1L;
    }

    /** Converts whole base-currency units back to cents. */
    public static long centsFor(long units, long baseValueCents) {
        if (units < 0L || baseValueCents <= 0L) {
            return 0L;
        }
        long cents = units * baseValueCents;
        // Guard the multiplication: a price near Long.MAX_VALUE would wrap to a negative amount and a
        // negative charge is a payout.
        return units != 0L && cents / units != baseValueCents ? -1L : cents;
    }

    /** The coins as a map for logging, in ascending value. */
    public static Map<String, Long> summarise(List<Coin> coins) {
        Map<String, Long> summary = new LinkedHashMap<>();
        List<Coin> sorted = new ArrayList<>(coins);
        sorted.sort((left, right) -> Long.compare(left.valueCents(), right.valueCents()));
        for (Coin coin : sorted) {
            summary.put(coin.id(), coin.valueCents());
        }
        return summary;
    }
}
