package com.athensmc.fcbridge;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reaches FantasticCurrency's API from the plugin side.
 *
 * <p>By reflection, on purpose. FantasticCurrency is a Forge mod and this is a Bukkit plugin: on a hybrid
 * server they share a JVM, but compiling against it would mean this plugin fails to load at all when the mod
 * is missing, disabled or has moved a method - and it would fail with a {@code NoClassDefFoundError} before
 * reaching any code able to explain why. Reflection turns that into a message an admin can act on.</p>
 *
 * <p>Amounts are always <strong>cents</strong>, matching the mod. Nothing here rounds, and no {@code double}
 * touches a balance; the conversion to Vault's {@code double} happens once, at the edge, in
 * {@link VaultEconomy}.</p>
 *
 * <p><strong>Thread safety.</strong> The mod's API is documented as server-thread only. Every call here goes
 * through {@link MainThread}, which runs it on the server thread and waits, so a plugin calling from its own
 * thread cannot corrupt a balance. That is a real risk rather than a theoretical one: Vault is routinely
 * called from async tasks by other plugins.</p>
 */
public final class FantasticCurrency {

    private static final String API_CLASS = "com.athensmc.athenscoins.api.FantasticCurrencyAPI";
    private static final String COIN_TYPE_CLASS = "com.athensmc.athenscoins.wallet.CoinType";

    /** The mod's item ids, in the order they are registered. */
    private static final String[] COIN_IDS = {"bronze_coin", "silver_coin", "gold_coin"};
    private static final String[] COIN_ENUMS = {"BRONZE", "SILVER", "GOLD"};
    private static final String[] COIN_NAMES = {"Moneda de bronce", "Moneda de plata", "Moneda de oro"};

    private final Logger logger;
    private final MainThread mainThread;

    private Object server;
    private Method getBalance;
    private Method has;
    private Method charge;
    private Method depositToAccount;
    private Method format;
    private Method currencySymbol;
    private Method coinValue;
    private Class<?> coinType;
    private String unavailableReason;

    public FantasticCurrency(Logger logger, MainThread mainThread) {
        this.logger = logger;
        this.mainThread = mainThread;
    }

    /**
     * Resolves the mod's API.
     *
     * @return true when everything needed was found.
     */
    public boolean connect() {
        try {
            Class<?> api = Class.forName(API_CLASS);
            getBalance = api.getMethod("getBalance", minecraftServer(), UUID.class);
            has = api.getMethod("has", minecraftServer(), UUID.class, long.class);
            charge = api.getMethod("charge", minecraftServer(), UUID.class, long.class);
            depositToAccount = api.getMethod("depositToAccount", minecraftServer(), UUID.class,
                    long.class);
            format = api.getMethod("format", long.class);
            currencySymbol = api.getMethod("currencySymbol");

            coinType = Class.forName(COIN_TYPE_CLASS);
            coinValue = api.getMethod("coinValue", coinType);

            server = nmsServer();
            if (server == null) {
                unavailableReason = "No pude obtener el servidor de Minecraft desde Bukkit. "
                        + "¿Es este un servidor híbrido (Mohist, Arclight)?";
                return false;
            }
            return true;
        } catch (ClassNotFoundException missingMod) {
            unavailableReason = "FantasticCurrency no está instalado, o su mod no cargó. "
                    + "Falta la clase " + missingMod.getMessage() + ".";
            return false;
        } catch (NoSuchMethodException changedApi) {
            unavailableReason = "FantasticCurrency está presente pero su API ha cambiado: "
                    + changedApi.getMessage() + ". Hace falta una versión compatible del puente.";
            return false;
        } catch (RuntimeException | LinkageError failure) {
            unavailableReason = "No pude conectar con FantasticCurrency: " + failure;
            return false;
        }
    }

    public boolean isConnected() {
        return server != null && getBalance != null;
    }

    /** Why the connection failed, for the log and the diagnostics command. */
    public String unavailableReason() {
        return unavailableReason == null ? "" : unavailableReason;
    }

    /** Spendable wallet balance, in cents. Zero when unavailable, never negative. */
    public long balanceCents(UUID player) {
        Long balance = call(() -> (Long) getBalance.invoke(null, server, player), "getBalance");
        return balance == null ? 0L : Math.max(0L, balance);
    }

    public boolean has(UUID player, long cents) {
        if (cents <= 0L) {
            return true;
        }
        Boolean result = call(() -> (Boolean) has.invoke(null, server, player, cents), "has");
        return result != null && result;
    }

    /**
     * Takes money from a player's wallet.
     *
     * @return true only when the money actually moved. A false here must always be treated as the
     *         transaction not happening, never as a warning to ignore.
     */
    public boolean charge(UUID player, long cents) {
        if (cents <= 0L) {
            return false;
        }
        Boolean result = call(() -> (Boolean) charge.invoke(null, server, player, cents), "charge");
        return result != null && result;
    }

    /** Pays money into a player's bank account, the mod's own default payout path. */
    public boolean deposit(UUID player, long cents) {
        if (cents <= 0L) {
            return false;
        }
        Boolean result = call(() -> (Boolean) depositToAccount.invoke(null, server, player, cents),
                "depositToAccount");
        return result != null && result;
    }

    /** The mod's own formatting, so amounts read the same in a shop as in the wallet. */
    public String format(long cents) {
        String formatted = call(() -> (String) format.invoke(null, cents), "format");
        return formatted == null ? String.valueOf(cents) : formatted;
    }

    public String symbol() {
        String symbol = call(() -> (String) currencySymbol.invoke(null), "currencySymbol");
        return symbol == null ? "" : symbol;
    }

    /** The three coins with their configured values, read live from the mod. */
    public List<Denominations.Coin> coins() {
        List<Denominations.Coin> coins = new ArrayList<>(COIN_IDS.length);
        for (int i = 0; i < COIN_IDS.length; i++) {
            long value = coinValueCents(COIN_ENUMS[i]);
            coins.add(new Denominations.Coin(COIN_IDS[i], COIN_NAMES[i], value));
        }
        return coins;
    }

    private long coinValueCents(String enumName) {
        Long value = call(() -> {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object constant = Enum.valueOf((Class<Enum>) coinType.asSubclass(Enum.class), enumName);
            return (Long) coinValue.invoke(null, constant);
        }, "coinValue " + enumName);
        return value == null ? 0L : value;
    }

    /** Runs a call on the server thread, logging and swallowing any failure. */
    private <T> T call(MainThread.Work<T> work, String what) {
        if (!isConnected()) {
            return null;
        }
        try {
            return mainThread.get(work);
        } catch (Exception failure) {
            logger.log(Level.WARNING, "FantasticCurrency." + what + " falló", failure);
            return null;
        }
    }

    private Class<?> minecraftServer() throws ClassNotFoundException {
        return Class.forName("net.minecraft.server.MinecraftServer");
    }

    /**
     * The NMS server behind Bukkit's.
     *
     * <p>{@code CraftServer.getServer()} is not part of the Bukkit API, so it is reached by name. On a
     * hybrid server it is there; anywhere else this plugin has nothing to bridge to anyway.</p>
     */
    private Object nmsServer() {
        try {
            Method getServer = Bukkit.getServer().getClass().getMethod("getServer");
            return getServer.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException | RuntimeException notHybrid) {
            return null;
        }
    }
}
