package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2CWalletSyncPacket;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The ATM (cash machine) menu: turns physical coins into digital funds and back.
 *
 * <p>Actions travel over vanilla's container-button channel, so no custom serverbound packet is
 * needed. The button id encodes the operation, the denomination and the amount preset.</p>
 */
public class AtmMenu extends AbstractContainerMenu implements WalletStateHolder {

    public static final int MODE_DEPOSIT = 0;
    public static final int MODE_WITHDRAW = 1;

    /** Amount presets; {@code -1} means "everything available". */
    public static final int[] AMOUNTS = { 1, 10, 64, -1 };

    private static final int BUTTONS_PER_MODE = CoinType.ORDERED.length * 4;

    private final ContainerLevelAccess access;
    private final long[] digital = new long[CoinType.ORDERED.length];
    private final int[] cash = new int[CoinType.ORDERED.length];
    private boolean atmNearby = true;

    /** Server-side. */
    public AtmMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.ATM.get(), containerId);
        this.access = access;
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            refreshFrom(serverPlayer);
        }
    }

    /** Client-side. */
    public AtmMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenus.ATM.get(), containerId);
        this.access = ContainerLevelAccess.NULL;
        readState(buffer);
    }

    // ------------------------------------------------------------------ button ids

    public static int buttonId(int mode, CoinType type, int amountIndex) {
        return mode * BUTTONS_PER_MODE + type.ordinal() * AMOUNTS.length + amountIndex;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id < 0 || id >= BUTTONS_PER_MODE * 2) {
            return false;
        }
        if (!stillValid(player)) {
            return false;
        }

        int mode = id / BUTTONS_PER_MODE;
        int rest = id % BUTTONS_PER_MODE;
        CoinType type = CoinType.ORDERED[rest / AMOUNTS.length];
        int preset = AMOUNTS[rest % AMOUNTS.length];

        WalletManager.Tx tx;
        if (mode == MODE_DEPOSIT) {
            int requested = preset < 0 ? Integer.MAX_VALUE : preset;
            tx = WalletManager.deposit(serverPlayer, type, requested);
        } else {
            long requested = preset < 0 ? Long.MAX_VALUE : preset;
            tx = WalletManager.withdraw(serverPlayer, type, requested);
        }

        if (tx.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable(mode == MODE_DEPOSIT
                                    ? "message.athens_coins.no_cash"
                                    : "message.athens_coins.no_funds",
                            type.displayName()).withStyle(ChatFormatting.RED),
                    true);
        } else {
            serverPlayer.displayClientMessage(
                    Component.translatable(mode == MODE_DEPOSIT
                                    ? "message.athens_coins.deposited"
                                    : "message.athens_coins.withdrawn",
                            tx.cash(), type.displayName()).withStyle(ChatFormatting.GREEN),
                    true);
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.BLOCKS, 0.4F, mode == MODE_DEPOSIT ? 1.4F : 1.0F);
        }

        refreshFrom(serverPlayer);
        ModNetwork.toPlayer(serverPlayer, new S2CWalletSyncPacket(digital, cash, atmNearby));
        return true;
    }

    // ------------------------------------------------------------------ state

    public void refreshFrom(ServerPlayer player) {
        Wallet wallet = WalletManager.walletOf(player);
        for (CoinType type : CoinType.ORDERED) {
            digital[type.ordinal()] = wallet.get(type);
            cash[type.ordinal()] = WalletManager.countCash(player, type);
        }
        atmNearby = true;
    }

    public static void writeState(FriendlyByteBuf buffer, ServerPlayer player) {
        Wallet wallet = WalletManager.walletOf(player);
        for (CoinType type : CoinType.ORDERED) {
            buffer.writeVarLong(wallet.get(type));
            buffer.writeVarInt(WalletManager.countCash(player, type));
        }
    }

    private void readState(FriendlyByteBuf buffer) {
        for (CoinType type : CoinType.ORDERED) {
            digital[type.ordinal()] = buffer.readVarLong();
            cash[type.ordinal()] = buffer.readVarInt();
        }
    }

    @Override
    public void applyState(long[] newDigital, int[] newCash, boolean newAtmNearby) {
        System.arraycopy(newDigital, 0, digital, 0, digital.length);
        System.arraycopy(newCash, 0, cash, 0, cash.length);
        this.atmNearby = newAtmNearby;
    }

    // ------------------------------------------------------------------ accessors

    public long digital(CoinType type) {
        return digital[type.ordinal()];
    }

    public int cash(CoinType type) {
        return cash[type.ordinal()];
    }

    // ------------------------------------------------------------------ menu contract

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ATM.get());
    }
}
