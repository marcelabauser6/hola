package com.athensmc.fcbridge;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Exposes FantasticCurrency through Vault, so the rest of the plugin ecosystem can spend it.
 *
 * <p>Shopkeepers does not use Vault - its trades are item-based, which is what {@link ShopkeepersCurrencies}
 * addresses - but almost everything else does, and an economy that only one plugin can see is not much of an
 * economy. With this in place jobs, protection plugins, chest shops and the rest all move the same money the
 * bank and the ATM do, instead of each keeping a private balance.</p>
 *
 * <p><strong>Cents to doubles, once, here.</strong> The mod counts exact cents and Vault's interface counts
 * {@code double}, so the conversion has to happen somewhere; doing it at this boundary and nowhere else keeps
 * the rounding in one reviewable place. Amounts coming in are rounded half-up to the nearest cent, and a
 * request that rounds to nothing is refused rather than silently succeeding as a free transaction.</p>
 *
 * <p><strong>No bank support.</strong> Vault's "banks" are shared accounts between players, which is a
 * different idea from FantasticCurrency's banks - those are branches a personal account is held at. Claiming
 * support and then mapping one onto the other would give plugins a broken abstraction, so every bank method
 * refuses clearly. {@link #hasBankSupport()} is false, which is how a well-behaved plugin knows not to ask.</p>
 */
public final class VaultEconomy implements Economy {

    /** Cents in one unit of currency. The mod's own scale. */
    private static final long SCALE = 100L;

    private final FantasticCurrency currency;
    private final String name;

    public VaultEconomy(FantasticCurrency currency, String name) {
        this.currency = currency;
        this.name = name;
    }

    // ---- identity -------------------------------------------------------------------------------

    @Override
    public boolean isEnabled() {
        return currency.isConnected();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return currency.format(toCents(amount));
    }

    @Override
    public String currencyNamePlural() {
        return currency.symbol();
    }

    @Override
    public String currencyNameSingular() {
        return currency.symbol();
    }

    // ---- accounts -------------------------------------------------------------------------------

    /**
     * Every player has an account as far as Vault is concerned.
     *
     * <p>FantasticCurrency requires a real bank account before money can be held, and a player without one
     * has a balance of zero. Reporting "no account" instead would make well-written plugins refuse to pay
     * wages to anyone who has not yet visited a bank, which is a confusing way to be broke.</p>
     */
    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && player.getUniqueId() != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        // Accounts are opened in the bank, by the player, and this must not forge one on their behalf.
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return createPlayerAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        UUID id = uuidOf(player);
        return id == null ? 0.0D : toAmount(currency.balanceCents(id));
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        UUID id = uuidOf(player);
        if (id == null) {
            return false;
        }
        long cents = toCents(amount);
        return cents <= 0L || currency.has(id, cents);
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    // ---- movements ------------------------------------------------------------------------------

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        UUID id = uuidOf(player);
        if (id == null) {
            return failure(0.0D, "Jugador desconocido.");
        }
        if (amount < 0.0D) {
            return failure(getBalance(player), "No se puede retirar una cantidad negativa.");
        }
        long cents = toCents(amount);
        if (cents == 0L) {
            // Refused rather than reported as a success. A plugin charging an amount that rounds to
            // nothing should find out, not be told it collected money it did not.
            return failure(getBalance(player), "La cantidad es menor que un céntimo.");
        }
        if (!currency.charge(id, cents)) {
            return failure(getBalance(player), "Saldo insuficiente en la cartera.");
        }
        return success(amount, getBalance(player));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        UUID id = uuidOf(player);
        if (id == null) {
            return failure(0.0D, "Jugador desconocido.");
        }
        if (amount < 0.0D) {
            return failure(getBalance(player), "No se puede ingresar una cantidad negativa.");
        }
        long cents = toCents(amount);
        if (cents == 0L) {
            return failure(getBalance(player), "La cantidad es menor que un céntimo.");
        }
        if (!currency.deposit(id, cents)) {
            return failure(getBalance(player),
                    "El ingreso fue rechazado: puede que el jugador no tenga cuenta bancaria "
                            + "o que se supere el límite del banco.");
        }
        return success(amount, getBalance(player));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    // ---- banks, all refused ---------------------------------------------------------------------

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    // ---- deprecated name-based API -------------------------------------------------------------
    //
    // Vault's original methods took a player name. They are unreliable - names change, and an offline
    // player cannot always be resolved - but plugins still call them, so each is forwarded to the
    // UUID-based version rather than refused.

    @Override
    public boolean hasAccount(String playerName) {
        return byName(playerName) != null;
    }

    @Override
    public boolean hasAccount(String playerName, String world) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String world) {
        return hasAccount(playerName);
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = byName(playerName);
        return player == null ? 0.0D : getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(String playerName, double amount) {
        OfflinePlayer player = byName(playerName);
        return player != null && has(player, amount);
    }

    @Override
    public boolean has(String playerName, String world, double amount) {
        return has(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = byName(playerName);
        return player == null ? failure(0.0D, "Jugador desconocido: " + playerName)
                : withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String world, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = byName(playerName);
        return player == null ? failure(0.0D, "Jugador desconocido: " + playerName)
                : depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String world, double amount) {
        return depositPlayer(playerName, amount);
    }

    // ---- helpers --------------------------------------------------------------------------------

    /**
     * Rounds an amount to whole cents, half-up.
     *
     * <p>Half-up rather than {@code (long) (amount * 100)}, whose truncation would turn a price of 0.07
     * arrived at by arithmetic - 0.06999999999999999 in binary - into six cents.</p>
     */
    static long toCents(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0.0D) {
            return 0L;
        }
        double cents = amount * SCALE;
        if (cents >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(cents);
    }

    static double toAmount(long cents) {
        return (double) cents / (double) SCALE;
    }

    private UUID uuidOf(OfflinePlayer player) {
        return player == null ? null : player.getUniqueId();
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer byName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return player.getUniqueId() == null ? null : player;
    }

    private EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance, ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(double balance, String reason) {
        return new EconomyResponse(0.0D, balance, ResponseType.FAILURE, reason);
    }

    private EconomyResponse noBanks() {
        return new EconomyResponse(0.0D, 0.0D, ResponseType.NOT_IMPLEMENTED,
                "FantasticCurrency no tiene cuentas compartidas de Vault.");
    }
}
