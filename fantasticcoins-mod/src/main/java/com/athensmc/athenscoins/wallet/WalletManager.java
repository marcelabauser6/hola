package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2CWalletSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The bank. Converts physical Fantastic Coins into Fantastic Cash and back, and moves cash
 * between accounts.
 */
public final class WalletManager {

    /** Result of an exchange: coins moved and the cash amount involved. */
    public record Exchange(int coins, long cents) {
        public static final Exchange NONE = new Exchange(0, 0L);

        public boolean isEmpty() {
            return coins <= 0;
        }
    }

    private WalletManager() {
    }

    // ------------------------------------------------------------------ account access

    public static WalletData data(ServerPlayer player) {
        return WalletData.get(player.server);
    }

    public static Wallet accountOf(ServerPlayer player) {
        com.athensmc.athenscoins.bank.BankAccount account =
                com.athensmc.athenscoins.bank.BankManager.accountOf(player);
        return new Wallet(account == null ? 0L : account.walletBalance());
    }

    public static Wallet accountOf(ServerPlayer context, UUID owner) {
        com.athensmc.athenscoins.bank.BankAccount account =
                com.athensmc.athenscoins.bank.BankData.get(context.server).accountOf(owner);
        return new Wallet(account == null ? 0L : account.walletBalance());
    }

    public static void markDirty(ServerPlayer player) {
        data(player).setDirty();
    }

    // ------------------------------------------------------------------ physical coins

    /** Counts the physical coins of one denomination in the player's inventory. */
    public static int countCoins(Player player, CoinType type) {
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

    public static int[] countAllCoins(Player player) {
        int[] counts = new int[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            counts[type.ordinal()] = countCoins(player, type);
        }
        return counts;
    }

    /** Total cash value of every coin the player is carrying. */
    public static long inventoryValueCents(Player player) {
        long total = 0L;
        for (CoinType type : CoinType.ORDERED) {
            total += Money.multiply(type.cashValueCents(), countCoins(player, type));
        }
        return Money.clampBalance(total);
    }

    private static int removeCoins(Player player, CoinType type, int amount) {
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

    private static void giveCoins(Player player, CoinType type, int amount) {
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

    // ------------------------------------------------------------------ exchange

    /**
     * Coins to cash: takes physical coins out of the inventory and credits the account.
     *
     * @param requested how many coins to hand in; {@code -1} means everything carried
     */
    public static Exchange exchangeToCash(ServerPlayer player, CoinType type, int requested,
                                          long unitRate) {
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        if (!com.athensmc.athenscoins.bank.BankManager.hasAccount(player)) {
            return Exchange.NONE;   // no account means no electronic money at all
        }
        int carried = countCoins(player, type);
        int amount = requested < 0 ? carried : Math.min(requested, carried);
        amount = Math.min(amount, settings.maxCoinsPerExchange);
        if (amount <= 0) {
            return Exchange.NONE;
        }

        long gross = Money.multiply(unitRate, amount);
        long credited = Money.afterFee(gross, settings.exchangeToCashFeePercent);
        if (credited <= 0L) {
            return Exchange.NONE;
        }

        int taken = removeCoins(player, type, amount);
        if (taken <= 0) {
            return Exchange.NONE;
        }
        if (taken != amount) {
            // Inventory changed underneath us; only pay for what we actually got.
            gross = Money.multiply(unitRate, taken);
            credited = Money.afterFee(gross, settings.exchangeToCashFeePercent);
        }

        // ATM coin deposits settle into the principal account. Refund inventory on any failure.
        boolean paid = com.athensmc.athenscoins.bank.BankManager.creditAccount(player.server,
                player.getUUID(), credited,
                com.athensmc.athenscoins.bank.LedgerEntry.Kind.COIN_DEPOSIT,
                type.id(), "atm");
        if (!paid) {
            giveCoins(player, type, taken);
            syncInventory(player);
            return Exchange.NONE;
        }
        syncInventory(player);
        pushBalance(player);
        return new Exchange(taken, credited);
    }

    /**
     * Cash to coins: debits the account and hands over physical coins.
     *
     * @param requested how many coins to buy; {@code -1} means as many as affordable
     */
    public static Exchange exchangeToCoins(ServerPlayer player, CoinType type, int requested,
                                           long unitRate) {
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        if (!com.athensmc.athenscoins.bank.BankManager.hasAccount(player)) {
            return Exchange.NONE;
        }
        long unitPrice = Money.plusFee(unitRate, settings.exchangeToCoinsFeePercent);
        if (unitPrice <= 0L) {
            return Exchange.NONE;
        }

        long available = com.athensmc.athenscoins.api.FantasticCurrencyAPI.getBalance(
                player.server, player.getUUID());
        long affordable = available / unitPrice;
        int amount = requested < 0
                ? (int) Math.min(affordable, settings.maxCoinsPerExchange)
                : (int) Math.min(Math.min(requested, affordable), settings.maxCoinsPerExchange);
        if (amount <= 0) {
            return Exchange.NONE;
        }

        long cost = Money.multiply(unitPrice, amount);
        if (!com.athensmc.athenscoins.bank.BankManager.chargeWallet(player.server,
                player.getUUID(), cost,
                com.athensmc.athenscoins.bank.LedgerEntry.Kind.COIN_WITHDRAW,
                type.id(), "atm")) {
            return Exchange.NONE;
        }
        giveCoins(player, type, amount);
        syncInventory(player);
        pushBalance(player);
        return new Exchange(amount, cost);
    }

    // ------------------------------------------------------------------ transfers

    /** Moves wallet cash between two banked players, atomically and under the receiver's ceiling. */
    public static boolean transfer(ServerPlayer from, ServerPlayer to, long cents) {
        boolean moved = com.athensmc.athenscoins.bank.BankManager.transferWallet(
                from.server, from.getUUID(), to.getUUID(), cents, "command");
        if (moved) {
            pushBalance(from);
            pushBalance(to);
        }
        return moved;
    }

    /**
     * Sends the player's balance to their client.
     *
     * <p>Called after every change so client-side GUIs - including other mods' - can show an
     * up-to-date figure without asking the server.</p>
     */
    public static void pushBalance(ServerPlayer player) {
        ModNetwork.toPlayer(player, new S2CWalletSyncPacket(
                accountOf(player).balance(),
                countAllCoins(player),
                true));
    }

    // ------------------------------------------------------------------ ATM proximity

    /** Finds the closest ATM within the configured range, or null if none is in reach. */
    @Nullable
    public static BlockPos findNearbyAtm(Player player) {
        Level level = player.level();
        int range = CurrencyConfig.get().atmDetectionRange;
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range),
                origin.offset(range, range, range))) {
            if (!level.getBlockState(pos).is(ModBlocks.ATM.get())) {
                continue;
            }
            if (!(player instanceof ServerPlayer serverPlayer)
                    || !(level.getBlockEntity(pos) instanceof com.athensmc.athenscoins.block.AtmBlockEntity atm)
                    || atm.bankId() == null
                    || !com.athensmc.athenscoins.bank.BankManager.ownsBank(serverPlayer,
                    atm.bank(serverPlayer.server))) {
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

    // ------------------------------------------------------------------ sync

    /**
     * Pushes the player's inventory back to the client.
     *
     * <p>Needed because the ATM menu has no slots: while it is open, vanilla's per-tick sync runs
     * against that menu, so coins added or removed would not reach the client until it closed.</p>
     */
    public static void syncInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastFullState();
    }
}
