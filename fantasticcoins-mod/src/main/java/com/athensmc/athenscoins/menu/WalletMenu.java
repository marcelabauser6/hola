package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Read-only menu behind the wallet GUI.
 *
 * <p>Carries both halves of the economy: the Fantastic Cash balance and the Fantastic Coins the
 * player is physically carrying. It has no slots at all, so nothing can be taken out of it.</p>
 */
public class WalletMenu extends AbstractContainerMenu implements WalletStateHolder {

    private long cashCents;
    private final int[] coinCounts = new int[CoinType.ORDERED.length];
    private UUID ownerId = new UUID(0L, 0L);
    private String ownerName = "";
    private boolean atmNearby;
    private DisplaySettings display = DisplaySettings.fromConfig();

    /** Server-side. */
    public WalletMenu(int containerId, Inventory inventory, ServerPlayer target) {
        super(ModMenus.WALLET.get(), containerId);
        this.cashCents = WalletManager.accountOf(target).balance();
        int[] counts = WalletManager.countAllCoins(target);
        System.arraycopy(counts, 0, coinCounts, 0, counts.length);
        this.ownerId = target.getUUID();
        this.ownerName = target.getGameProfile().getName();
        this.atmNearby = WalletManager.findNearbyAtm(target) != null;
        this.display = DisplaySettings.fromConfig();
    }

    /** Client-side. */
    public WalletMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenus.WALLET.get(), containerId);
        this.cashCents = buffer.readVarLong();
        for (int i = 0; i < coinCounts.length; i++) {
            coinCounts[i] = buffer.readVarInt();
        }
        this.ownerId = buffer.readUUID();
        this.ownerName = buffer.readUtf(32);
        this.atmNearby = buffer.readBoolean();
        this.display = DisplaySettings.read(buffer);
    }

    public static void writeState(FriendlyByteBuf buffer, ServerPlayer target) {
        buffer.writeVarLong(WalletManager.accountOf(target).balance());
        for (CoinType type : CoinType.ORDERED) {
            buffer.writeVarInt(WalletManager.countCoins(target, type));
        }
        buffer.writeUUID(target.getUUID());
        buffer.writeUtf(target.getGameProfile().getName(), 32);
        buffer.writeBoolean(WalletManager.findNearbyAtm(target) != null);
        DisplaySettings.fromConfig().write(buffer);
    }

    @Override
    public void applyState(long newCash, int[] newCounts, boolean newAtmNearby) {
        this.cashCents = newCash;
        System.arraycopy(newCounts, 0, coinCounts, 0, coinCounts.length);
        this.atmNearby = newAtmNearby;
    }

    // ------------------------------------------------------------------ accessors

    public long cashCents() {
        return cashCents;
    }

    public int coinCount(CoinType type) {
        return coinCounts[type.ordinal()];
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

    public DisplaySettings display() {
        return display;
    }

    /** Cash value of every coin in the inventory. */
    public long coinsValueCents() {
        long total = 0L;
        for (CoinType type : CoinType.ORDERED) {
            total += Money.multiply(display.valueOf(type), coinCounts[type.ordinal()]);
        }
        return total;
    }

    /** Cash plus the value of the carried coins. */
    public long netWorthCents() {
        return Money.clampBalance(cashCents + coinsValueCents());
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
