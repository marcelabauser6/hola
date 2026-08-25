package com.athensmc.fsshopkeepers.money;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * The link to Fantastic Currency's Fantastic Cash.
 *
 * <p>Bound by reflection rather than by a compile-time dependency, and that is the point of the class. Fantastic
 * Currency is a separate mod: if this one imported its API directly, a server without it installed would fail to
 * load this mod at all, with a {@code NoClassDefFoundError} pointing at a class nobody asked for. Instead every
 * call goes through a handle resolved once at startup, and when the currency mod is absent {@link #available()}
 * is false and the shops fall back to item prices.</p>
 *
 * <p>All amounts are exact cents, matching the currency API, which counts in cents for the same reason
 * {@link Money} does.</p>
 *
 * <p>Every method here must be called on the server thread. The currency mod's API says so, and the balances it
 * guards are shared mutable state; the one exception is {@link #displayBalance(Player)}, which exists so the
 * editor can show the admin their own balance on the client.</p>
 */
public final class Cash {

    private static final String API_CLASS = "com.athensmc.athenscoins.api.FantasticCurrencyAPI";

    /** The currency mod's id, for the dependency notice in the logs. */
    public static final String CURRENCY_MOD_ID = "athens_coins";

    private static MethodHandle getBalance;
    private static MethodHandle charge;
    private static MethodHandle depositToAccount;
    private static MethodHandle depositToWallet;
    private static MethodHandle creditSaleToAccount;
    private static MethodHandle accountNumber;
    private static MethodHandle ownsAccount;
    private static MethodHandle currencySymbol;
    private static MethodHandle currencyName;
    private static MethodHandle formatCents;
    private static MethodHandle displayBalance;
    private static MethodHandle getAccountBalance;

    private static boolean available;

    private Cash() {
    }

    /**
     * Resolves the currency API once.
     *
     * <p>Called during mod setup. A missing class means the currency mod is not installed, which is a supported
     * configuration and logged as information. A class that is present but missing a method is different: it
     * means the two mods disagree about the API, so it is logged as a warning and the whole bridge is switched
     * off rather than left half-working, because a bridge that can charge but not refund is worse than no
     * bridge.</p>
     */
    public static void bind() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> api;
        try {
            api = Class.forName(API_CLASS);
        } catch (ClassNotFoundException notInstalled) {
            available = false;
            FantasticShopkeepers.LOGGER.info(
                    "Fantastic Currency no esta instalado: los precios en Fantastic Cash quedan desactivados "
                            + "y las tiendas usaran items como moneda.");
            return;
        }
        try {
            getBalance = lookup.findStatic(api, "getBalance",
                    MethodType.methodType(long.class, MinecraftServer.class, UUID.class));
            charge = lookup.findStatic(api, "charge",
                    MethodType.methodType(boolean.class, MinecraftServer.class, UUID.class, long.class));
            depositToAccount = lookup.findStatic(api, "depositToAccount",
                    MethodType.methodType(boolean.class, MinecraftServer.class, UUID.class, long.class));
            depositToWallet = lookup.findStatic(api, "depositToWallet",
                    MethodType.methodType(boolean.class, MinecraftServer.class, UUID.class, long.class));
            creditSaleToAccount = lookup.findStatic(api, "creditSaleToAccount",
                    MethodType.methodType(boolean.class, MinecraftServer.class, UUID.class, int.class,
                            long.class));
            accountNumber = lookup.findStatic(api, "accountNumber",
                    MethodType.methodType(int.class, MinecraftServer.class, UUID.class));
            ownsAccount = lookup.findStatic(api, "ownsAccount",
                    MethodType.methodType(boolean.class, MinecraftServer.class, UUID.class, int.class));
            currencySymbol = lookup.findStatic(api, "currencySymbol",
                    MethodType.methodType(String.class));
            currencyName = lookup.findStatic(api, "currencyName",
                    MethodType.methodType(String.class));
            formatCents = lookup.findStatic(api, "format",
                    MethodType.methodType(String.class, long.class));
            displayBalance = lookup.findStatic(api, "getDisplayBalance",
                    MethodType.methodType(long.class, Player.class));
            getAccountBalance = lookup.findStatic(api, "getAccountBalance",
                    MethodType.methodType(long.class, MinecraftServer.class, UUID.class));
            available = true;
            FantasticShopkeepers.LOGGER.info(
                    "Fantastic Currency detectado: precios en {} activados; las compras usan solo la wallet.",
                    currencyName());
        } catch (ReflectiveOperationException apiChanged) {
            available = false;
            FantasticShopkeepers.LOGGER.warn(
                    "Fantastic Currency esta instalado pero su API no coincide con la esperada ({}). "
                            + "Los precios en Fantastic Cash quedan desactivados para no cobrar de forma "
                            + "inconsistente.", apiChanged.toString());
        }
    }

    /** True when prices may be set in Fantastic Cash. */
    public static boolean available() {
        return available;
    }

    /** The currency symbol, or a neutral fallback when the currency mod is absent. */
    public static String symbol() {
        if (!available) {
            return "$";
        }
        try {
            return (String) currencySymbol.invokeExact();
        } catch (Throwable failed) {
            return "$";
        }
    }

    /** The currency's display name, for help text and command feedback. */
    public static String currencyName() {
        if (!available) {
            return "Fantastic Cash";
        }
        try {
            return (String) currencyName.invokeExact();
        } catch (Throwable failed) {
            return "Fantastic Cash";
        }
    }

    /**
     * Formats an amount the way the currency mod does.
     *
     * <p>Falls back to this mod's own formatting when the currency mod is absent, so a price still reads
     * correctly in the editor on a server that has no economy installed.</p>
     */
    public static String format(long cents) {
        if (!available) {
            return Money.format(cents, symbol());
        }
        try {
            return (String) formatCents.invokeExact(cents);
        } catch (Throwable failed) {
            return Money.format(cents, symbol());
        }
    }

    /** Spendable balance in cents, or zero when there is no currency mod. */
    public static long balance(MinecraftServer server, UUID player) {
        if (!available || server == null || player == null) {
            return 0L;
        }
        try {
            return (long) getBalance.invokeExact(server, player);
        } catch (Throwable failed) {
            logCallFailure("consultar el saldo", failed);
            return 0L;
        }
    }

    public static long balance(ServerPlayer player) {
        return player == null ? 0L : balance(player.server, player.getUUID());
    }

    /** The principal held in the player's bank account: the money on their card. */
    public static long accountBalance(MinecraftServer server, UUID player) {
        if (!available || server == null || player == null) {
            return 0L;
        }
        try {
            return (long) getAccountBalance.invokeExact(server, player);
        } catch (Throwable failed) {
            logCallFailure("consultar la cuenta", failed);
            return 0L;
        }
    }

    /**
     * What a shop may spend from a player: only the wallet/card balance exposed by Fantastic Currency.
     *
     * <p>The bank principal is deliberately excluded. Money must be withdrawn to the wallet first; a shop purchase
     * never reaches into savings to complete a payment.</p>
     */
    public static long spendable(MinecraftServer server, UUID player) {
        return Math.max(0L, balance(server, player));
    }

    public static long spendable(ServerPlayer player) {
        return player == null ? 0L : spendable(player.server, player.getUUID());
    }

    /**
     * Takes money from the buyer's wallet/card balance only.
     *
     * <p>Returns false when the available wallet balance cannot cover the complete price. The bank principal is never
     * considered and never debited; players must withdraw money to the wallet before shopping.</p>
     */
    public static boolean charge(MinecraftServer server, UUID player, long cents) {
        if (!available || server == null || player == null || cents <= 0L) {
            return false;
        }
        long before = spendable(server, player);
        if (before < cents) {
            FantasticShopkeepers.LOGGER.warn("Cobro rechazado: {} necesita {} en wallet y solo tiene {}.",
                    player, cents, before);
            return false;
        }
        if (!chargeWallet(server, player, cents)) {
            return false;
        }
        // Confirm the whole amount really left the same wallet that was checked.
        long after = spendable(server, player);
        if (after > before - cents) {
            FantasticShopkeepers.LOGGER.error(
                    "Fantastic Currency dijo que cobro {} a {} pero su wallet paso de {} a {}. Compra anulada.",
                    cents, player, before, after);
            return false;
        }
        return true;
    }

    private static boolean chargeWallet(MinecraftServer server, UUID player, long cents) {
        try {
            return (boolean) charge.invokeExact(server, player, cents);
        } catch (Throwable failed) {
            logCallFailure("cobrar de la wallet", failed);
            return false;
        }
    }

    /** Returns a cancelled purchase to the wallet it was charged from. */
    public static boolean refundWallet(MinecraftServer server, UUID player, long cents) {
        if (!available || server == null || player == null || cents <= 0L) {
            return false;
        }
        try {
            return (boolean) depositToWallet.invokeExact(server, player, cents);
        } catch (Throwable failed) {
            logCallFailure("devolver a la wallet", failed);
            return false;
        }
    }

    /** Pays money to a player's bank account, used for sale income and payouts. */
    public static boolean deposit(MinecraftServer server, UUID player, long cents) {
        if (!available || server == null || player == null || cents <= 0L) {
            return false;
        }
        try {
            return (boolean) depositToAccount.invokeExact(server, player, cents);
        } catch (Throwable failed) {
            logCallFailure("abonar", failed);
            return false;
        }
    }

    /**
     * Pays a sale to the shop owner, but only into the account the shop was linked to.
     *
     * <p>The currency API checks that the linked account still belongs to the seller before crediting it. That
     * check matters because a shop outlives the account it was set up with: without it, a player who gave up an
     * account could keep being paid into it, or worse, into someone else's.</p>
     */
    public static boolean creditSale(MinecraftServer server, UUID seller, int linkedAccount, long cents) {
        if (!available || server == null || seller == null || cents <= 0L) {
            return false;
        }
        if (linkedAccount <= 0) {
            return deposit(server, seller, cents);
        }
        try {
            return (boolean) creditSaleToAccount.invokeExact(server, seller, linkedAccount, cents);
        } catch (Throwable failed) {
            logCallFailure("abonar la venta", failed);
            return false;
        }
    }

    /** The player's bank account number, or zero when they have none. */
    public static int accountNumber(MinecraftServer server, UUID player) {
        if (!available || server == null || player == null) {
            return 0;
        }
        try {
            return (int) accountNumber.invokeExact(server, player);
        } catch (Throwable failed) {
            logCallFailure("consultar la cuenta", failed);
            return 0;
        }
    }

    /** True when the account number still belongs to the player. */
    public static boolean ownsAccount(MinecraftServer server, UUID player, int number) {
        if (!available || server == null || player == null || number <= 0) {
            return false;
        }
        try {
            return (boolean) ownsAccount.invokeExact(server, player, number);
        } catch (Throwable failed) {
            logCallFailure("validar la cuenta", failed);
            return false;
        }
    }

    /**
     * A balance safe to read on the client, for showing the admin what they can afford.
     *
     * <p>The currency mod keeps a client-side cache for exactly this, so the editor can print a balance without
     * inventing a request of its own.</p>
     */
    public static long displayBalance(Player player) {
        if (!available || player == null) {
            return 0L;
        }
        try {
            return (long) displayBalance.invokeExact(player);
        } catch (Throwable failed) {
            return 0L;
        }
    }

    /**
     * Logs a failed API call once per kind of failure.
     *
     * <p>A currency call failing inside a trade could otherwise repeat every tick a player holds the button
     * down, and a log that scrolls thousands of identical lines is a log nobody can read.</p>
     */
    private static void logCallFailure(String action, Throwable failure) {
        FantasticShopkeepers.LOGGER.error("Fallo al {} con Fantastic Currency: {}", action, failure.toString());
    }
}
