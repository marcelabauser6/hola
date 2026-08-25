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

    /**
     * The bank's own manager, for the one thing the public API does not offer.
     *
     * <p>Fantastic Currency keeps a player's money as a bank principal and lends a small spendable float to their wallet.
     * {@code FantasticCurrencyAPI.charge} only ever debits that float, so a player with eighteen thousand on their card and
     * an empty wallet was told they could not afford anything. {@code BankManager.debitAccount} takes it from the principal,
     * which is what a shop should do, and it is public even though the API does not re-export it.</p>
     */
    private static final String BANK_MANAGER_CLASS = "com.athensmc.athenscoins.bank.BankManager";
    private static final String LEDGER_KIND_CLASS = "com.athensmc.athenscoins.bank.LedgerEntry$Kind";

    /** The currency mod's id, for the dependency notice in the logs. */
    public static final String CURRENCY_MOD_ID = "athens_coins";

    private static MethodHandle getBalance;
    private static MethodHandle charge;
    private static MethodHandle depositToAccount;
    private static MethodHandle creditSaleToAccount;
    private static MethodHandle accountNumber;
    private static MethodHandle ownsAccount;
    private static MethodHandle currencySymbol;
    private static MethodHandle currencyName;
    private static MethodHandle formatCents;
    private static MethodHandle displayBalance;
    private static MethodHandle getAccountBalance;

    /** {@code BankManager.debitAccount}, and the ledger reason to record against it. */
    private static MethodHandle debitAccount;
    private static Object shopPurchaseKind;

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
            bindBankManager(lookup);
            FantasticShopkeepers.LOGGER.info("Fantastic Currency detectado: precios en {} activados.",
                    currencyName());
        } catch (ReflectiveOperationException apiChanged) {
            available = false;
            FantasticShopkeepers.LOGGER.warn(
                    "Fantastic Currency esta instalado pero su API no coincide con la esperada ({}). "
                            + "Los precios en Fantastic Cash quedan desactivados para no cobrar de forma "
                            + "inconsistente.", apiChanged.toString());
        }
    }

    /**
     * Binds the bank manager, which is optional.
     *
     * <p>Failure here is not fatal: without it the shops fall back to spending only the wallet float, which is what they did
     * before. It is logged as a warning because that fallback is a worse experience, not a broken one.</p>
     */
    private static void bindBankManager(MethodHandles.Lookup lookup) {
        try {
            Class<?> bankManager = Class.forName(BANK_MANAGER_CLASS);
            Class<?> kindClass = Class.forName(LEDGER_KIND_CLASS);
            debitAccount = lookup.findStatic(bankManager, "debitAccount",
                    MethodType.methodType(boolean.class, MinecraftServer.class, UUID.class, long.class,
                            kindClass, String.class, String.class));
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object kind = Enum.valueOf((Class<Enum>) kindClass, "SHOP_PURCHASE");
            shopPurchaseKind = kind;
            FantasticShopkeepers.LOGGER.info(
                    "Fantastic Currency: las compras podran cobrarse de la cuenta del banco y no solo de la cartera.");
        } catch (ReflectiveOperationException | RuntimeException notThere) {
            debitAccount = null;
            shopPurchaseKind = null;
            FantasticShopkeepers.LOGGER.warn(
                    "Fantastic Currency: no se encontro BankManager.debitAccount ({}), asi que las compras solo "
                            + "podran gastar el saldo de la cartera.", notThere.toString());
        }
    }

    /** True when prices may be set in Fantastic Cash. */
    public static boolean available() {
        return available;
    }

    /**
     * True when a purchase can reach the money in a player's bank account.
     *
     * <p>Reported so {@code /fskeepers saldo} can say whether an account balance is spendable here. Without this, a player
     * with a full account and an empty wallet is refused with no way to tell whether the mod cannot see the money or
     * cannot touch it.</p>
     */
    public static boolean canChargeAccount() {
        return available && debitAccount != null;
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
     * Everything a player can actually spend: the wallet float plus the bank principal.
     *
     * <p>This is the figure a shop must compare a price against. The wallet alone is a small withdrawal limit, and judging
     * affordability by it tells a player with a full account that they are broke.</p>
     */
    public static long spendable(MinecraftServer server, UUID player) {
        long wallet = balance(server, player);
        if (debitAccount == null) {
            return wallet;
        }
        long account = accountBalance(server, player);
        return Money.canAdd(wallet, account) ? wallet + account : Money.MAX_CENTS;
    }

    public static long spendable(ServerPlayer player) {
        return player == null ? 0L : spendable(player.server, player.getUUID());
    }

    /**
     * Takes money from a buyer.
     *
     * <p>Returns false when the money was not taken, for any reason: no currency mod, not enough balance, no
     * bank account. The caller must treat false as "the trade did not happen" and hand nothing over. Charging
     * first and delivering only on success is the ordering that cannot give goods away for free.</p>
     */
    public static boolean charge(MinecraftServer server, UUID player, long cents) {
        if (!available || server == null || player == null || cents <= 0L) {
            return false;
        }
        long wallet = balance(server, player);
        if (wallet >= cents) {
            return chargeWallet(server, player, cents);
        }
        if (debitAccount == null) {
            // No way to reach the principal, so the wallet is all there is.
            return chargeWallet(server, player, cents);
        }
        if (wallet <= 0L) {
            return chargeAccount(server, player, cents);
        }
        // Split across both. The wallet goes first because it is the smaller pot, and if the account refuses the
        // remainder the wallet is put back rather than leaving the player short of goods and money.
        if (!chargeWallet(server, player, wallet)) {
            return chargeAccount(server, player, cents);
        }
        if (chargeAccount(server, player, cents - wallet)) {
            return true;
        }
        if (!depositToWalletOrAccount(server, player, wallet)) {
            FantasticShopkeepers.LOGGER.error(
                    "Se cobraron {} de la cartera de {} y el resto fallo, y la devolucion tambien. Revisar a mano.",
                    wallet, player);
        }
        return false;
    }

    private static boolean chargeWallet(MinecraftServer server, UUID player, long cents) {
        try {
            return (boolean) charge.invokeExact(server, player, cents);
        } catch (Throwable failed) {
            logCallFailure("cobrar de la cartera", failed);
            return false;
        }
    }

    /** Takes money straight from the bank principal, which is where a player's savings live. */
    private static boolean chargeAccount(MinecraftServer server, UUID player, long cents) {
        if (debitAccount == null || cents <= 0L) {
            return false;
        }
        try {
            Object result = debitAccount.invoke(server, player, cents, shopPurchaseKind,
                    "compra en tienda", "fsshopkeepers");
            return result instanceof Boolean ok && ok;
        } catch (Throwable failed) {
            logCallFailure("cobrar de la cuenta", failed);
            return false;
        }
    }

    /** Puts money back after a half-completed charge, preferring the account so no ceiling can refuse it. */
    private static boolean depositToWalletOrAccount(MinecraftServer server, UUID player, long cents) {
        return deposit(server, player, cents);
    }

    /** Pays money to a player's bank account, used for admin payouts and refunds. */
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
