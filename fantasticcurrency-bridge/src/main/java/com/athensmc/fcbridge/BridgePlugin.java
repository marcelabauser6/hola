package com.athensmc.fcbridge;

import com.nisovin.shopkeepers.api.events.ShopkeepersStartupEvent;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Joins FantasticCurrency to Shopkeepers, and to anything else that speaks Vault.
 *
 * <p>Two separate jobs, because the two need different things:</p>
 *
 * <ul>
 *   <li><strong>Shopkeepers</strong> has no virtual-economy hook - its trades move currency items, and the
 *       Vault reference in its jar is a metrics chart. So its currency is replaced with FantasticCurrency's
 *       own coins, and every part of it then works untouched. See {@link ShopkeepersCurrencies}.</li>
 *   <li><strong>Everything else</strong> speaks Vault, so a Vault economy backed by the same cash is
 *       registered. See {@link VaultEconomy}.</li>
 * </ul>
 *
 * <p>Neither half is required for the other. Each reports its own state, and a failure in one is logged with
 * the reason and leaves the other running - there is no arrangement of missing plugins in which this one
 * fails to load and leaves an admin guessing.</p>
 */
public final class BridgePlugin extends JavaPlugin implements Listener {

    private MainThread mainThread;
    private FantasticCurrency currency;
    private CoinItems coins;
    private ShopkeepersCurrencies shopkeepers;

    /**
     * The exact instance handed to Vault.
     *
     * <p>Kept because unregistering needs the same object. Building a fresh one to pass to
     * {@code unregister} would compare unequal and quietly leave a dead economy registered, which on a
     * {@code /reload} means every plugin talking to a provider whose plugin is gone.</p>
     */
    private VaultEconomy vaultEconomy;

    @Override
    public void onEnable() {
        mainThread = new MainThread(this);
        currency = new FantasticCurrency(getLogger(), mainThread);
        coins = new CoinItems(getLogger());

        if (!currency.connect()) {
            getLogger().severe("Sin FantasticCurrency no hay nada que conectar: "
                    + currency.unavailableReason());
            getLogger().severe("El plugin queda cargado para poder informar, pero inactivo.");
            return;
        }
        getLogger().info("FantasticCurrency conectado. Moneda: " + currency.symbol());

        if (!coins.connect()) {
            getLogger().warning("No puedo construir items del juego: " + coins.unavailableReason());
            getLogger().warning("Shopkeepers seguirá con su moneda actual; Vault sí funcionará.");
        }

        registerVault();
        setUpShopkeepers();
    }

    @Override
    public void onDisable() {
        if (vaultEconomy != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultEconomy);
            vaultEconomy = null;
        }
    }

    /**
     * Publishes the economy to Vault.
     *
     * <p>At {@code Highest} priority, so it wins over a placeholder economy that a pack might ship. If two
     * real economies are installed the server owner has a decision to make, and it is logged plainly rather
     * than resolved silently.</p>
     */
    private void registerVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault no está instalado, así que no publico la economía. "
                    + "Shopkeepers no lo necesita; otros plugins sí.");
            return;
        }
        Economy existing = Bukkit.getServicesManager().load(Economy.class);
        if (existing != null && !(existing instanceof VaultEconomy)) {
            getLogger().warning("Ya había otra economía registrada en Vault (" + existing.getName()
                    + "). Publico la de FantasticCurrency con prioridad alta, pero conviene "
                    + "desinstalar la otra para no repartir el dinero en dos sitios.");
        }
        vaultEconomy = new VaultEconomy(currency, getName());
        Bukkit.getServicesManager().register(Economy.class, vaultEconomy, this,
                ServicePriority.Highest);
        getLogger().info("Economía publicada en Vault.");
    }

    private void setUpShopkeepers() {
        if (Bukkit.getPluginManager().getPlugin("Shopkeepers") == null) {
            getLogger().info("Shopkeepers no está instalado, así que no toco su moneda.");
            return;
        }
        shopkeepers = new ShopkeepersCurrencies(getLogger(), currency, coins);
        Bukkit.getPluginManager().registerEvents(this, this);

        // Shopkeepers may already have started, in which case its startup event is long past and the
        // install has to happen now. Trying both ways is what makes the load order not matter.
        if (!shopkeepers.install()) {
            getLogger().info("La moneda de Shopkeepers se instalará cuando arranque: "
                    + shopkeepers.failureReason());
        }
    }

    /**
     * Re-installs the coins after Shopkeepers has loaded its own config.
     *
     * <p>{@code Currencies.load()} runs during that startup and on every {@code /shopkeeper reload}, and it
     * rebuilds the list from the config file - putting emeralds back. Monitor priority, so this runs after
     * Shopkeepers has finished its own setup.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopkeepersStartup(ShopkeepersStartupEvent event) {
        // Deferred by a tick: the currencies are loaded as part of the startup this event announces, and
        // replacing them from inside it would be undone a moment later.
        Bukkit.getScheduler().runTask(this, () -> {
            if (shopkeepers != null && !shopkeepers.install()) {
                getLogger().warning("No pude poner las Fantastic Coins como moneda de Shopkeepers: "
                        + shopkeepers.failureReason());
                getLogger().warning("Shopkeepers sigue con su moneda del config.yml.");
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("§6FantasticCurrency Bridge");
        line(sender, "FantasticCurrency", currency != null && currency.isConnected(),
                currency == null ? "" : currency.unavailableReason());
        line(sender, "Items del juego", coins != null && coins.isConnected(),
                coins == null ? "" : coins.unavailableReason());
        line(sender, "Economía en Vault", vaultEconomy != null, "Vault no instalado o no publicada.");

        boolean shopkeepersOk = shopkeepers != null && shopkeepers.installed() != null;
        line(sender, "Moneda de Shopkeepers", shopkeepersOk,
                shopkeepers == null ? "Shopkeepers no instalado." : shopkeepers.failureReason());

        if (shopkeepersOk) {
            Denominations.Plan plan = shopkeepers.installed();
            sender.sendMessage("§7  Base: §f" + plan.base().displayName() + " §7= "
                    + currency.format(plan.base().valueCents()));
            if (plan.hasHigh()) {
                sender.sendMessage("§7  Alta: §f" + plan.high().displayName() + " §7= "
                        + plan.highValue() + " x base = "
                        + currency.format(plan.high().valueCents()));
            }
            for (String note : plan.notes()) {
                sender.sendMessage("§8  " + note);
            }
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
