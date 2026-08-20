package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Read-only menu backing the wallet GUI.
 *
 * <p>It holds no slots at all: the screen paints everything itself, which keeps the wallet
 * purely informational and means nothing can be dragged out of it.</p>
 */
public class WalletMenu extends AbstractContainerMenu implements WalletStateHolder {

    private final long[] digital = new long[CoinType.ORDERED.length];
    private final int[] cash = new int[CoinType.ORDERED.length];
    private UUID ownerId = new UUID(0L, 0L);
    private String ownerName = "";
    private boolean atmNearby;

    /** Server-side: snapshot the target player's wallet and pockets. */
    public WalletMenu(int containerId, Inventory inventory, ServerPlayer target) {
        super(ModMenus.WALLET.get(), containerId);
        applyState(target);
    }

    /** Client-side: rebuild the snapshot from the network. */
    public WalletMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenus.WALLET.get(), containerId);
        readState(buffer);
    }

    // ------------------------------------------------------------------ state

    public void applyState(ServerPlayer target) {
        Wallet wallet = WalletManager.walletOf(target);
        for (CoinType type : CoinType.ORDERED) {
            digital[type.ordinal()] = wallet.get(type);
            cash[type.ordinal()] = WalletManager.countCash(target, type);
        }
        ownerId = target.getUUID();
        ownerName = target.getGameProfile().getName();
        atmNearby = WalletManager.findNearbyAtm(target) != null;
    }

    @Override
    public void applyState(long[] newDigital, int[] newCash, boolean newAtmNearby) {
        System.arraycopy(newDigital, 0, digital, 0, digital.length);
        System.arraycopy(newCash, 0, cash, 0, cash.length);
        atmNearby = newAtmNearby;
    }

    public static void writeState(FriendlyByteBuf buffer, ServerPlayer target) {
        Wallet wallet = WalletManager.walletOf(target);
        for (CoinType type : CoinType.ORDERED) {
            buffer.writeVarLong(wallet.get(type));
            buffer.writeVarInt(WalletManager.countCash(target, type));
        }
        buffer.writeUUID(target.getUUID());
        buffer.writeUtf(target.getGameProfile().getName(), 32);
        buffer.writeBoolean(WalletManager.findNearbyAtm(target) != null);
    }

    public void readState(FriendlyByteBuf buffer) {
        for (CoinType type : CoinType.ORDERED) {
            digital[type.ordinal()] = buffer.readVarLong();
            cash[type.ordinal()] = buffer.readVarInt();
        }
        ownerId = buffer.readUUID();
        ownerName = buffer.readUtf(32);
        atmNearby = buffer.readBoolean();
    }

    // ------------------------------------------------------------------ accessors

    public long digital(CoinType type) {
        return digital[type.ordinal()];
    }

    public int cash(CoinType type) {
        return cash[type.ordinal()];
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean atmNearby() {
        return atmNearby;
    }

    public long totalDigitalInBronze() {
        long total = 0L;
        for (CoinType type : CoinType.ORDERED) {
            total += digital[type.ordinal()] * (long) type.bronzeValue();
        }
        return total;
    }

    public long totalCashInBronze() {
        long total = 0L;
        for (CoinType type : CoinType.ORDERED) {
            total += (long) cash[type.ordinal()] * (long) type.bronzeValue();
        }
        return total;
    }

    // ------------------------------------------------------------------ menu contract

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
