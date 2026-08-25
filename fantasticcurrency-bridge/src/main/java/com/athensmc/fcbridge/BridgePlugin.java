package com.athensmc.fcbridge;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lets Shopkeepers shops be priced in Fantastic Cash.
 *
 * <p>Shopkeepers has no virtual-economy hook: its window is the vanilla merchant, which will not let a trade be
 * clicked unless the cost items are in the slots. So the currency exchange is made to happen around the trade
 * instead - Cash out when the window opens, Cash back when it closes - and the buyer never handles an item. See
 * {@link CashTrading}.</p>
 *
 * <p>The Vault economy alongside it is what lets the rest of the server's plugins spend the same money.</p>
 */
public final class BridgePlugin extends JavaPlugin {

    private FantasticCurrency currency;
    private CashRate rate;

    /**
     * The exact instance handed to Vault.
     *
     * <p>Kept because unregistering needs the same object. Building a fresh one to pass to {@code unregister}
     * compares unequal and quietly leaves a dead economy registered, which after a reload means every plugin
     * talking to a provider whose plugin is gone.</p>
     */
    private VaultEconomy vaultEconomy;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        currency = new FantasticCurrency(getLogger(), new MainThread(this));
        if (!currency.connect()) {
            getLogger().severe("Sin FantasticCurrency no hay nada que conectar: "
                    + currency.unavailableReason());
            getLogger().severe("El plugin queda cargado para poder informar, pero inactivo.");
            return;
        }
        getLogger().info("FantasticCurrency conectado. Moneda: " + currency.symbol());

        if (!readRate()) {
            return;
        }
        registerVault();
        setUpShopkeepers();
    }

    /**
     * Reads how much Cash one unit of price is worth.
     *
     * <p>Refused rather than defaulted when the value is unusable. A silently corrected rate would price every
     * shop on the server wrongly, and the mistake would show up as takings that are out by a factor.</p>
     */
    private boolean readRate() {
        double cashPerUnit = getConfig().getDouble("cash-per-price-unit", 1.0D);
        try {
            rate = CashRate.of(cashPerUnit);
        } catch (IllegalArgumentException bad) {
            getLogger().severe("cash-per-price-unit no vale: " + bad.getMessage());
            getLogger().severe("Corrige config.yml y reinicia; no toco las tiendas mientras.");
            return false;
        }
        getLogger().info("Un punto de precio = " + currency.format(rate.centsPerUnit())
                + ". Un precio de 25 en el editor son " + currency.format(rate.costCents(25L)) + ".");
        return true;
    }

    private void registerVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault no está instalado, así que no publico la economía. "
                    + "Shopkeepers no lo necesita.");
            return;
        }
        Economy existing = Bukkit.getServicesManager().load(Economy.class);
        if (existing != null && !(existing instanceof VaultEconomy)) {
            getLogger().warning("Ya había otra economía en Vault (" + existing.getName()
                    + "). Publico la de FantasticCurrency con prioridad alta, pero conviene quitar la "
                    + "otra para no repartir el dinero en dos sitios.");
        }
        vaultEconomy = new VaultEconomy(currency, getName());
        Bukkit.getServicesManager().register(Economy.class, vaultEconomy, this,
                ServicePriority.Highest);
        getLogger().info("Economía publicada en Vault.");
    }

    private void setUpShopkeepers() {
        if (Bukkit.getPluginManager().getPlugin("Shopkeepers") == null) {
            getLogger().info("Shopkeepers no está instalado, así que no hay tiendas que enlazar.");
            return;
        }
        Bukkit.getPluginManager().registerEvents(new CashTrading(this, currency, rate), this);
        getLogger().info("Las tiendas de Shopkeepers cobran del saldo digital.");
    }

    @Override
    public void onDisable() {
        if (vaultEconomy != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultEconomy);
            vaultEconomy = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§6FantasticCurrency Bridge");
        line(sender, "FantasticCurrency", currency != null && currency.isConnected(),
                currency == null ? "" : currency.unavailableReason());
        line(sender, "Economía en Vault", vaultEconomy != null, "Vault no instalado.");
        line(sender, "Shopkeepers", Bukkit.getPluginManager().getPlugin("Shopkeepers") != null,
                "Shopkeepers no instalado.");
        if (rate != null && currency != null) {
            sender.sendMessage("§7Un punto de precio = §f" + currency.format(rate.centsPerUnit()));
            sender.sendMessage("§7Precio 25 en el editor = §f" + currency.format(rate.costCents(25L)));
        }
        return true;
    }

    private void line(CommandSender sender, String label, boolean ok, String reason) {
        sender.sendMessage("§7" + label + ": " + (ok ? "§así" : "§cNO"));
        if (!ok && reason != null && !reason.isBlank()) {
            sender.sendMessage("§8  " + reason);
        }
    }
}
