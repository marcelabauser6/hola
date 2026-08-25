package com.athensmc.fcbridge;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.currency.Currencies;
import com.nisovin.shopkeepers.currency.Currency;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lets Shopkeepers shops be priced in Fantastic Cash.
 *
 * <p>Shopkeepers cannot be told to charge a balance. Its trading window is the vanilla merchant, and the
 * vanilla merchant will not let a trade be clicked at all unless the cost items are physically in the slots -
 * there is no virtual-currency path anywhere in the plugin. So instead of fighting that, the exchange happens
 * around the trade, invisibly:</p>
 *
 * <ol>
 *   <li>The window opens. Cash is debited and exactly enough currency is lent to cover the priciest thing the
 *       shop offers.</li>
 *   <li>The player trades. Shopkeepers sees an ordinary trade and behaves normally.</li>
 *   <li>The window closes. Whatever was not spent is taken back and returned to Cash.</li>
 * </ol>
 *
 * <p>From the buyer's side they paid from their balance and never handled a coin. A price of 25 is 25.00 Cash,
 * set in the editor exactly as Shopkeepers has always done.</p>
 *
 * <p><strong>Money cannot be created by this.</strong> Every lent item is backed by a charge that has already
 * happened, so a crash mid-trade leaves the player holding currency worth precisely what left their balance -
 * nothing appears and nothing vanishes. That is why the exchange is done this way rather than by faking the
 * items and settling afterwards, which duplicates money the moment the server stops unexpectedly.</p>
 *
 * <p><strong>Buyers only.</strong> These are automated NPC shops: there is no seller to pay, no owner and no
 * container, and the currency a trade consumes simply ceases to exist. So there is no crediting side to this
 * at all, which removes the one part that could have gone wrong while nobody was looking.</p>
 */
public final class CashTrading implements Listener {

    /** How long after clicking an NPC its trade window is still considered to be that NPC's. */
    private static final long CLICK_MEMORY_TICKS = 40L;

    private final Plugin plugin;
    private final FantasticCurrency currency;
    private final CashRate rate;

    /** Currency units lent to each player for the window they have open. */
    private final Map<UUID, Long> lent = new HashMap<>();

    /** The last shopkeeper each player clicked, and when, so an opening window can be attributed. */
    private final Map<UUID, long[]> lastClick = new HashMap<>();

    public CashTrading(Plugin plugin, FantasticCurrency currency, CashRate rate) {
        this.plugin = plugin;
        this.currency = currency;
        this.rate = rate;
    }

    /**
     * Remembers that a shopkeeper was clicked.
     *
     * <p>The merchant window carries no reference back to the entity that opened it, and Bukkit offers no map
     * from one to the other. Recording the click is what keeps this from firing on an ordinary villager, where
     * lending currency against someone's bank balance would be an unpleasant surprise.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!isShopkeeper(clicked)) {
            return;
        }
        lastClick.put(event.getPlayer().getUniqueId(),
                new long[]{clicked.getWorld().getFullTime()});
    }

    // ---- lending on open ------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!openedAShopkeeper(player)) {
            return;
        }
        Currency base = Currencies.getBase();
        if (base == null) {
            return;
        }

        long needed = priciestTradeUnits(event.getInventory());
        if (needed <= 0L) {
            return;
        }
        long affordable = rate.unitsAffordable(currency.balanceCents(player.getUniqueId()));
        long toLend = Math.min(needed, affordable);
        if (toLend <= 0L) {
            player.sendMessage(ChatColor.RED + "No tienes saldo suficiente para comprar aquí.");
            return;
        }

        long cost = rate.costCents(toLend);
        if (cost <= 0L) {
            return;
        }
        // Charged before the items exist, never after. The other order would put currency into an inventory
        // that had not been paid for, and a disconnect in between would be free money.
        if (!currency.charge(player.getUniqueId(), cost)) {
            player.sendMessage(ChatColor.RED + "No pude retirar el importe de tu cuenta.");
            return;
        }

        long given = give(player, base, toLend);
        if (given < toLend) {
            // The inventory was fuller than expected. Refund the part that could not be handed over, rather
            // than keep a charge for currency the player never received.
            currency.deposit(player.getUniqueId(), rate.refundCents(toLend - given));
        }
        if (given > 0L) {
            lent.merge(player.getUniqueId(), given, Long::sum);
            player.sendMessage(ChatColor.GRAY + "Pagas con tu saldo. Disponible aquí: "
                    + currency.format(rate.costCents(given)));
        }
    }

    /** True when the window being opened belongs to a shopkeeper this player just clicked. */
    private boolean openedAShopkeeper(Player player) {
        long[] clicked = lastClick.remove(player.getUniqueId());
        if (clicked == null) {
            return false;
        }
        long now = player.getWorld().getFullTime();
        return now - clicked[0] <= CLICK_MEMORY_TICKS;
    }

    /**
     * The most any single trade in this window costs.
     *
     * <p>Read off the merchant's own recipes rather than the shopkeeper's, so what is lent matches what the
     * player can actually click. Enough for the priciest trade and no more, because lending the whole balance
     * would fill the inventory with currency for a shop selling one cheap thing.</p>
     */
    private long priciestTradeUnits(Inventory inventory) {
        if (!(inventory instanceof MerchantInventory merchant)) {
            return 0L;
        }
        long most = 0L;
        for (MerchantRecipe recipe : merchant.getMerchant().getRecipes()) {
            long units = 0L;
            for (ItemStack ingredient : recipe.getIngredients()) {
                units += unitsOf(ingredient);
            }
            most = Math.max(most, units);
        }
        return most;
    }

    private long unitsOf(ItemStack stack) {
        if (stack == null) {
            return 0L;
        }
        Currency matched = Currencies.match(stack);
        return matched == null ? 0L : (long) matched.getValue() * stack.getAmount();
    }

    // ---- reclaiming on close -------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            // Next tick: items still sitting in the merchant's slots are returned to the inventory after
            // this event, and reclaiming now would miss them and leave the player short.
            Bukkit.getScheduler().runTask(plugin, () -> settle(player));
        }
    }

    /** A player who disconnects with the window open still has to be settled. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
        settle(event.getPlayer());
    }

    /**
     * Takes back what was lent and returns its value to Cash.
     *
     * <p>Never more than was lent, so currency the player already had of their own is left alone. What is
     * missing was spent in a trade and has already been paid for.</p>
     */
    private void settle(Player player) {
        Long outstanding = lent.remove(player.getUniqueId());
        if (outstanding == null || outstanding <= 0L) {
            return;
        }
        Currency base = Currencies.getBase();
        if (base == null) {
            return;
        }
        long reclaimed = takeFrom(player.getInventory(), base, outstanding);
        if (reclaimed > 0L) {
            currency.deposit(player.getUniqueId(), rate.refundCents(reclaimed));
        }
        long spent = outstanding - reclaimed;
        if (spent > 0L && player.isOnline()) {
            player.sendMessage(ChatColor.GRAY + "Gastado: " + currency.format(rate.costCents(spent)));
        }
    }

    // ---- item plumbing -------------------------------------------------------------------------

    /** Gives up to {@code units} worth of currency, returning how much actually fit. */
    private long give(Player player, Currency base, long units) {
        int unitValue = Math.max(1, base.getValue());
        long itemsNeeded = units / unitValue;
        long given = 0L;
        int maxStack = Math.max(1, base.getMaxStackSize());

        while (itemsNeeded > 0L) {
            int amount = (int) Math.min(itemsNeeded, maxStack);
            Map<Integer, ItemStack> leftover =
                    player.getInventory().addItem(base.getItemData().createItemStack(amount));
            int placed = amount;
            for (ItemStack remaining : leftover.values()) {
                placed -= remaining.getAmount();
            }
            if (placed <= 0) {
                break;
            }
            given += (long) placed * unitValue;
            itemsNeeded -= placed;
        }
        return given;
    }

    /** Removes up to {@code units} worth of currency, returning how much was removed. */
    private long takeFrom(Inventory inventory, Currency base, long units) {
        int unitValue = Math.max(1, base.getValue());
        long itemsLeft = units / unitValue;
        long removed = 0L;
        ItemStack[] contents = inventory.getContents();

        for (int slot = 0; slot < contents.length && itemsLeft > 0L; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !base.getItemData().matches(stack)) {
                continue;
            }
            int amount = (int) Math.min(stack.getAmount(), itemsLeft);
            if (amount >= stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - amount);
                inventory.setItem(slot, stack);
            }
            itemsLeft -= amount;
            removed += (long) amount * unitValue;
        }
        return removed;
    }

    private boolean isShopkeeper(Entity entity) {
        try {
            return ShopkeepersAPI.getShopkeeperRegistry().isShopkeeper(entity);
        } catch (RuntimeException notReady) {
            return false;
        }
    }
}
