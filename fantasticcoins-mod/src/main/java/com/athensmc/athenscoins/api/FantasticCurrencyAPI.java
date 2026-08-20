package com.athensmc.athenscoins.api;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Stable entry point for other mods — shops, quests, jobs, auction houses — that want to use
 * Fantastic Cash as their currency.
 *
 * <h2>Money is always cents</h2>
 * Every amount in this API is a {@code long} count of cents: {@code 125} means {@code $1.25}.
 * There is no floating point anywhere in the money path, which is what keeps balances exact to
 * two decimal places. Use {@link #parseAmount(String)} to turn user input into cents and
 * {@link #format(long)} to turn cents back into a display string.
 *
 * <h2>Threading</h2>
 * Call these from the server thread only. Every method needs a {@link MinecraftServer} because
 * balances live in the overworld's saved data.
 *
 * <h2>Typical shop flow</h2>
 * <pre>{@code
 * long price = FantasticCurrencyAPI.parseAmount("49.99");
 * if (FantasticCurrencyAPI.charge(player, price)) {
 *     giveGoods(player);
 * } else {
 *     player.sendSystemMessage(Component.literal(
 *             "Need " + FantasticCurrencyAPI.format(price)));
 * }
 * }</pre>
 */
public final class FantasticCurrencyAPI {

    private FantasticCurrencyAPI() {
    }

    // ------------------------------------------------------------------ currency metadata

    /** Display name of the digital currency, e.g. {@code "Fantastic Cash"}. */
    public static String currencyName() {
        return CurrencyConfig.get().currencyName;
    }

    /** Display symbol, e.g. {@code "$"}. */
    public static String currencySymbol() {
        return CurrencyConfig.get().currencySymbol;
    }

    /** Formats cents for display, e.g. {@code 123456 -> "$1,234.56"}. */
    public static String format(long cents) {
        return Money.format(cents, currencySymbol());
    }

    /** Formats cents without a symbol, e.g. {@code 123456 -> "1234.56"}. */
    public static String formatPlain(long cents) {
        return Money.plain(cents);
    }

    /**
     * Parses text such as {@code "49.99"} into cents.
     *
     * @throws Money.InvalidAmountException if it is not a plain positive decimal with at most two
     *                                      decimal places
     */
    public static long parseAmount(String text) throws Money.InvalidAmountException {
        return Money.parse(text);
    }

    /** Builds a cents value from whole units and cents, e.g. {@code (49, 99) -> 4999}. */
    public static long cents(long units, int cents) {
        return units * Money.SCALE + cents;
    }

    // ------------------------------------------------------------------ balances

    private static Wallet account(MinecraftServer server, UUID playerId) {
        return WalletData.get(server).account(playerId);
    }

    private static void markDirty(MinecraftServer server) {
        WalletData.get(server).setDirty();
    }

    /** Current balance in cents. Works for offline players too. */
    public static long getBalance(MinecraftServer server, UUID playerId) {
        return account(server, playerId).balance();
    }

    public static long getBalance(ServerPlayer player) {
        return getBalance(player.server, player.getUUID());
    }

    /** True if the player can cover {@code cents}. */
    public static boolean has(MinecraftServer server, UUID playerId, long cents) {
        return account(server, playerId).canAfford(cents);
    }

    public static boolean has(ServerPlayer player, long cents) {
        return has(player.server, player.getUUID(), cents);
    }

    /** Adds money to an account. Use for payouts, rewards and refunds. */
    public static void deposit(MinecraftServer server, UUID playerId, long cents) {
        if (cents <= 0L) {
            return;
        }
        account(server, playerId).add(cents);
        markDirty(server);
    }

    public static void deposit(ServerPlayer player, long cents) {
        deposit(player.server, player.getUUID(), cents);
    }

    /**
     * Takes money from an account, all or nothing.
     *
     * @return true if the full amount was taken; false if the balance was too low, in which case
     *         nothing changed
     */
    public static boolean charge(MinecraftServer server, UUID playerId, long cents) {
        if (cents <= 0L) {
            return true;
        }
        Wallet wallet = account(server, playerId);
        if (!wallet.withdraw(cents)) {
            return false;
        }
        markDirty(server);
        return true;
    }

    public static boolean charge(ServerPlayer player, long cents) {
        return charge(player.server, player.getUUID(), cents);
    }

    /** Overwrites a balance outright. Intended for admin tooling, not for shops. */
    public static void setBalance(MinecraftServer server, UUID playerId, long cents) {
        account(server, playerId).set(cents);
        markDirty(server);
    }

    /**
     * Moves money between two accounts, all or nothing.
     *
     * @return false if the sender could not cover it, in which case nothing changed
     */
    public static boolean transfer(MinecraftServer server, UUID from, UUID to, long cents) {
        if (cents <= 0L || from.equals(to)) {
            return false;
        }
        if (!account(server, from).withdraw(cents)) {
            return false;
        }
        account(server, to).add(cents);
        markDirty(server);
        return true;
    }

    // ------------------------------------------------------------------ physical coins

    /** Cash value of one physical coin of the given denomination, in cents. */
    public static long coinValue(CoinType type) {
        return CurrencyConfig.get().coinValueCents(type);
    }

    /** How many coins of a denomination the player is carrying. */
    public static int countCoins(Player player, CoinType type) {
        return com.athensmc.athenscoins.wallet.WalletManager.countCoins(player, type);
    }

    /** Combined cash value of every coin in the player's inventory, in cents. */
    public static long inventoryCoinValue(Player player) {
        return com.athensmc.athenscoins.wallet.WalletManager.inventoryValueCents(player);
    }

    /** Cash balance plus the value of the coins carried, in cents. */
    public static long netWorth(ServerPlayer player) {
        return Money.clampBalance(getBalance(player) + inventoryCoinValue(player));
    }
}
