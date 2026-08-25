package com.athensmc.fsshopkeepers.trade;

import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The chest behind a player shop.
 *
 * <p>A player shop owns no items of its own: what it can sell is what is in its container at the instant a buyer
 * clicks, and what it can buy is limited by the room left in that container. Reading the chest live, rather than
 * caching a stock count, is what keeps a shop honest when the owner empties it mid-trade.</p>
 *
 * <p>Every method takes the container as an argument rather than looking it up again, so that a single trade reads
 * the chest once and then counts, removes and adds against that same view. Looking it up per operation would open a
 * window where the chest is replaced between the check and the removal.</p>
 */
public final class ShopStock {

    private ShopStock() {
    }

    /**
     * The container a shop draws from, or null when there is none.
     *
     * <p>Resolves double chests through {@link ChestBlock}, because half a double chest is not the container the
     * player thinks they filled, and a shop that can only see 27 of 54 slots looks like it is losing stock.</p>
     */
    public static Container container(ServerLevel level, Shopkeeper shop) {
        BlockPos pos = shop.containerPos();
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            Container joined = ChestBlock.getContainer(chest, state, level, pos, true);
            if (joined != null) {
                return joined;
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    /** True when two stacks are the same item with the same NBT, ignoring how many. */
    public static boolean matches(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameTags(a, b);
    }

    /** How many of an item the container holds. */
    public static int count(Container container, ItemStack wanted) {
        if (container == null || wanted.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (matches(inSlot, wanted)) {
                total += inSlot.getCount();
                if (total < 0) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return total;
    }

    /**
     * Takes items out of the container.
     *
     * <p>Checks the whole amount is present before removing any of it, so a shop cannot half-complete a sale and
     * leave the buyer paying for goods that were not all there.</p>
     *
     * @return true when the full amount was removed.
     */
    public static boolean remove(Container container, ItemStack wanted, int amount) {
        if (container == null || wanted.isEmpty() || amount <= 0) {
            return false;
        }
        if (count(container, wanted) < amount) {
            return false;
        }
        int left = amount;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (!matches(inSlot, wanted)) {
                continue;
            }
            int taken = Math.min(left, inSlot.getCount());
            inSlot.shrink(taken);
            if (inSlot.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            left -= taken;
        }
        container.setChanged();
        return left == 0;
    }

    /** How many more of an item the container could accept. */
    public static int room(Container container, ItemStack incoming) {
        if (container == null || incoming.isEmpty()) {
            return 0;
        }
        int max = Math.min(incoming.getMaxStackSize(), container.getMaxStackSize());
        int space = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (inSlot.isEmpty()) {
                space += max;
            } else if (matches(inSlot, incoming)) {
                space += Math.max(0, max - inSlot.getCount());
            }
            if (space < 0) {
                return Integer.MAX_VALUE;
            }
        }
        return space;
    }

    /**
     * Puts items into the container.
     *
     * <p>Refuses unless everything fits, for the same reason {@link #remove} refuses a partial take: a buying shop
     * that accepts half a stack has taken goods it cannot store, and the remainder would have to be dropped on the
     * floor or destroyed.</p>
     *
     * @return true when the whole stack was stored.
     */
    public static boolean add(Container container, ItemStack incoming) {
        if (container == null || incoming.isEmpty()) {
            return false;
        }
        if (room(container, incoming) < incoming.getCount()) {
            return false;
        }
        ItemStack left = incoming.copy();
        int max = Math.min(incoming.getMaxStackSize(), container.getMaxStackSize());
        // Top up existing stacks first, so a chest does not fill with part-stacks of the same item.
        for (int slot = 0; slot < container.getContainerSize() && !left.isEmpty(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (!matches(inSlot, left)) {
                continue;
            }
            int fits = Math.min(left.getCount(), max - inSlot.getCount());
            if (fits > 0) {
                inSlot.grow(fits);
                left.shrink(fits);
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !left.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                ItemStack placed = left.copy();
                placed.setCount(Math.min(left.getCount(), max));
                container.setItem(slot, placed);
                left.shrink(placed.getCount());
            }
        }
        container.setChanged();
        return left.isEmpty();
    }

    /**
     * How many times a shop could complete an offer right now.
     *
     * <p>Used to grey out a trade the shop cannot honour, so a buyer finds out before paying rather than after.
     * Admin shops are unlimited and answer with a large number rather than a real count, because they have no
     * container to count.</p>
     */
    public static int availableTrades(ServerLevel level, Shopkeeper shop, com.athensmc.fsshopkeepers.shop.TradeOffer offer) {
        if (!shop.type().needsContainer()) {
            return Integer.MAX_VALUE;
        }
        Container container = container(level, shop);
        if (container == null) {
            return 0;
        }
        if (shop.type().buysFromBuyer()) {
            ItemStack incoming = offer.cost1().isEmpty() ? offer.cost2() : offer.cost1();
            if (incoming.isEmpty()) {
                return 0;
            }
            return room(container, incoming) / Math.max(1, incoming.getCount());
        }
        ItemStack outgoing = offer.result();
        if (outgoing.isEmpty()) {
            return 0;
        }
        if (shop.type() == com.athensmc.fsshopkeepers.shop.ShopType.PLAYER_BOOK) {
            // A book shop copies its original rather than handing it over, so one book is unlimited stock.
            return count(container, outgoing) > 0 ? Integer.MAX_VALUE : 0;
        }
        return count(container, outgoing) / Math.max(1, outgoing.getCount());
    }
}
