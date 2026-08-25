package com.athensmc.fcbridge;

import com.nisovin.shopkeepers.currency.Currencies;
import com.nisovin.shopkeepers.currency.Currency;
import com.nisovin.shopkeepers.util.inventory.ItemData;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Makes Fantastic Coins the currency Shopkeepers trades in.
 *
 * <p>Shopkeepers is built around currency <em>items</em> - it has no virtual-economy hook at all, and the
 * Vault reference in its jar is a metrics chart and nothing more. So the way to have it trade in this
 * server's money is not to intercept its trades, it is to make its currency be the money: the coins
 * FantasticCurrency already mints, which players already carry and can already exchange for cash at the ATM.
 * Once the currency item is a coin, every part of Shopkeepers works untouched - player shops, admin shops,
 * hiring, the trade log - because as far as it knows nothing unusual is happening.</p>
 *
 * <p>The install goes through the currency registry rather than the config file, because the config cannot
 * express a modded item: it names a Bukkit {@code Material}, and {@code athens_coins:gold_coin} is not one.
 * The registry holds {@code ItemData}, which wraps a whole {@code ItemStack}, and that can hold a modded
 * item. See {@link CoinItems}.</p>
 *
 * <p>It has to be re-run after every {@code Currencies.load()} - startup and each {@code /shopkeeper reload}
 * - because that method rebuilds the list from the config and would put emeralds back.</p>
 */
public final class ShopkeepersCurrencies {

    /** Shopkeepers' own ids for its two currency slots. Reused so its messages still make sense. */
    private static final String BASE_ID = "base";
    private static final String HIGH_ID = "high";

    private final Logger logger;
    private final FantasticCurrency currency;
    private final CoinItems coins;

    private Denominations.Plan installed;
    private String failureReason;

    public ShopkeepersCurrencies(Logger logger, FantasticCurrency currency, CoinItems coins) {
        this.logger = logger;
        this.currency = currency;
        this.coins = coins;
    }

    /** The pairing currently in force, or null when the install has not succeeded. */
    public Denominations.Plan installed() {
        return installed;
    }

    public String failureReason() {
        return failureReason == null ? "" : failureReason;
    }

    /**
     * Replaces Shopkeepers' currencies with Fantastic Coins.
     *
     * <p>All or nothing. A half-applied install - the base coin replaced and the high one still an emerald
     * block - would price goods in a mixture of two economies, so the existing currencies are only cleared
     * once every replacement has been built successfully.</p>
     *
     * @return true when the currencies were replaced.
     */
    public boolean install() {
        failureReason = null;
        installed = null;

        if (!currency.isConnected()) {
            failureReason = currency.unavailableReason();
            return false;
        }
        if (!coins.isConnected()) {
            failureReason = coins.unavailableReason();
            return false;
        }

        Denominations.Plan plan;
        try {
            plan = Denominations.plan(currency.coins());
        } catch (Denominations.Unusable unusable) {
            failureReason = unusable.getMessage();
            return false;
        }

        List<Currency> replacements = new ArrayList<>(2);
        Currency base = build(BASE_ID, plan.base(), 1);
        if (base == null) {
            return false;
        }
        replacements.add(base);

        if (plan.hasHigh()) {
            Currency high = build(HIGH_ID, plan.high(), plan.highValue());
            if (high == null) {
                return false;
            }
            replacements.add(high);
        }

        if (!replaceRegistry(replacements)) {
            return false;
        }

        installed = plan;
        report(plan);
        return true;
    }

    private Currency build(String id, Denominations.Coin coin, int value) {
        ItemStack stack = coins.coin(coin.id(), 1);
        if (stack == null) {
            failureReason = "El item '" + coin.id() + "' de FantasticCurrency no está registrado en "
                    + "este servidor, así que no puedo usarlo como moneda.";
            return null;
        }
        try {
            return new Currency(id, coin.displayName(), new ItemData(stack), value);
        } catch (RuntimeException rejected) {
            failureReason = "Shopkeepers rechazó la moneda '" + coin.id() + "': " + rejected;
            return null;
        }
    }

    /**
     * Swaps the contents of Shopkeepers' currency list.
     *
     * <p>By reflection, because the list is private and its {@code add} is too - Shopkeepers builds it from
     * its own config and offers no way in. The list's <em>contents</em> are replaced rather than the field,
     * so anything that already captured a reference to it keeps seeing the truth; swapping the field would
     * leave stale readers pricing in emeralds.</p>
     */
    @SuppressWarnings("unchecked")
    private boolean replaceRegistry(List<Currency> replacements) {
        try {
            Field field = Currencies.class.getDeclaredField("ALL");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof List<?> list)) {
                failureReason = "El registro de monedas de Shopkeepers no es una lista; su versión no "
                        + "es compatible con este puente.";
                return false;
            }
            List<Currency> all = (List<Currency>) list;
            all.clear();
            all.addAll(replacements);
            return true;
        } catch (NoSuchFieldException changed) {
            failureReason = "Shopkeepers ha cambiado su registro de monedas (no encuentro 'ALL'), "
                    + "hace falta una versión compatible del puente.";
            return false;
        } catch (UnsupportedOperationException immutable) {
            failureReason = "El registro de monedas de Shopkeepers es inmutable en esta versión, "
                    + "así que no puedo sustituirlo.";
            return false;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            failureReason = "No pude sustituir las monedas de Shopkeepers: " + failure;
            return false;
        }
    }

    private void report(Denominations.Plan plan) {
        logger.info("Moneda de Shopkeepers: " + plan.base().displayName()
                + " (" + currency.format(plan.base().valueCents()) + " cada una)");
        if (plan.hasHigh()) {
            logger.info("Moneda alta: " + plan.high().displayName() + " = " + plan.highValue()
                    + " x " + plan.base().displayName()
                    + " (" + currency.format(plan.high().valueCents()) + " cada una)");
        } else {
            logger.info("Sin moneda alta: no hay una segunda moneda que sea múltiplo exacto de la base.");
        }
        for (String note : plan.notes()) {
            logger.info("  " + note);
        }
    }
}
