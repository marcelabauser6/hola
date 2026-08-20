package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.config.CoinsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bridge between physical coins (inventory "cash") and digital coins (the wallet).
 */
public final class WalletManager {

    /** Result of a deposit/withdrawal: how much cash moved and how much digital value moved. */
    public record Tx(int cash, long digital) {
        public boolean isEmpty() {
            return cash <= 0 && digital <= 0;
        }

        public static final Tx NONE = new Tx(0, 0L);
    }

    private WalletManager() {
    }

    // ------------------------------------------------------------------ wallet access

    public static WalletData data(ServerPlayer player) {
        return WalletData.get(player.server);
    }

    public static Wallet walletOf(ServerPlayer player) {
        return data(player).wallet(player.getUUID());
    }

    public static void markDirty(ServerPlayer player) {
        data(player).setDirty();
    }

    // ------------------------------------------------------------------ cash helpers

    /** Counts the physical coins of one denomination carried by the player. */
    public static int countCash(Player player, CoinType type) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(type.item())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int[] countAllCash(Player player) {
        int[] cash = new int[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            cash[type.ordinal()] = countCash(player, type);
        }
        return cash;
    }

    /** Removes up to {@code amount} physical coins, returning how many were actually taken. */
    public static int removeCash(Player player, CoinType type, int amount) {
        if (amount <= 0) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(type.item())) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= take;
        }
        return amount - remaining;
    }

    /** Gives physical coins to the player, dropping whatever does not fit. */
    public static void giveCash(Player player, CoinType type, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(type.item());
            int size = Math.min(remaining, stack.getMaxStackSize());
            stack.setCount(size);
            remaining -= size;
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    // ------------------------------------------------------------------ transactions

    /**
     * Moves cash from the inventory into the digital wallet.
     *
     * @param requested how many coins to deposit; clamped to what the player actually carries
     */
    public static Tx deposit(ServerPlayer player, CoinType type, int requested) {
        int available = countCash(player, type);
        int amount = Math.min(requested, available);
        if (amount <= 0) {
            return Tx.NONE;
        }
        int taken = removeCash(player, type, amount);
        if (taken <= 0) {
            return Tx.NONE;
        }
        long credited = afterFee(taken, CoinsConfig.DEPOSIT_FEE_PERCENT.get());
        walletOf(player).add(type, credited);
        markDirty(player);
        syncInventory(player);
        return new Tx(taken, credited);
    }

    /**
     * Moves digital funds back out as physical coins.
     *
     * @param requested how much digital value to withdraw; clamped to the wallet balance
     */
    public static Tx withdraw(ServerPlayer player, CoinType type, long requested) {
        Wallet wallet = walletOf(player);
        long amount = Math.min(requested, wallet.get(type));
        // Never hand out more items than the configured cap: without this, "withdraw everything"
        // on a huge balance would try to spawn millions of item stacks and hang the server.
        amount = Math.min(amount, CoinsConfig.MAX_WITHDRAW_PER_TRANSACTION.get());
        if (amount <= 0) {
            return Tx.NONE;
        }
        long payout = afterFee(amount, CoinsConfig.WITHDRAW_FEE_PERCENT.get());
        if (payout <= 0) {
            return Tx.NONE;
        }
        wallet.add(type, -amount);
        markDirty(player);
        giveCash(player, type, (int) payout);
        syncInventory(player);
        return new Tx((int) payout, amount);
    }

    /**
     * Pushes the player's inventory back to the client.
     *
     * <p>Necessary because the ATM menu has no slots: while it is open the vanilla per-tick
     * sync runs against that menu, so coins added to or removed from the inventory would not
     * reach the client until the screen closed.</p>
     */
    private static void syncInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastFullState();
    }

    private static long afterFee(long amount, int feePercent) {
        if (feePercent <= 0) {
            return amount;
        }
        long fee = amount * feePercent / 100L;
        return Math.max(0L, amount - fee);
    }

    // ------------------------------------------------------------------ ATM proximity

    /** Finds an ATM block within the configured range, or null if the player is not at a bank. */
    @Nullable
    public static BlockPos findNearbyAtm(Player player) {
        Level level = player.level();
        int range = CoinsConfig.ATM_RANGE.get();
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range),
                origin.offset(range, range, range))) {
            if (!level.getBlockState(pos).is(ModBlocks.ATM.get())) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ misc

    public static Wallet walletOf(ServerPlayer contextPlayer, UUID owner) {
        return data(contextPlayer).wallet(owner);
    }
}
