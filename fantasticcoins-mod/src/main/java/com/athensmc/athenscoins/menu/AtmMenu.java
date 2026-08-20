package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2CWalletSyncPacket;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The ATM: exchanges Fantastic Coins for Fantastic Cash and back.
 *
 * <p>Actions ride vanilla's container-button channel, so no custom serverbound packet is needed.
 * The button id packs the direction, the denomination and the amount preset into one byte.</p>
 */
public class AtmMenu extends AbstractContainerMenu implements WalletStateHolder {

    /** Coins out of the inventory, cash into the account. */
    public static final int MODE_TO_CASH = 0;
    /** Cash out of the account, coins into the inventory. */
    public static final int MODE_TO_COINS = 1;

    /** Amount presets; {@code -1} means "as many as possible". */
    public static final int[] AMOUNTS = { 1, 10, 64, -1 };

    private static final int BUTTONS_PER_MODE = CoinType.ORDERED.length * 4;

    private final ContainerLevelAccess access;
    private long cashCents;
    private final int[] coinCounts = new int[CoinType.ORDERED.length];
    private DisplaySettings display = DisplaySettings.fromConfig();

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
        this.cashCents = buffer.readVarLong();
        for (int i = 0; i < coinCounts.length; i++) {
            coinCounts[i] = buffer.readVarInt();
        }
        this.display = DisplaySettings.read(buffer);
    }

    public static void writeState(FriendlyByteBuf buffer, ServerPlayer player) {
        buffer.writeVarLong(WalletManager.accountOf(player).balance());
        for (CoinType type : CoinType.ORDERED) {
            buffer.writeVarInt(WalletManager.countCoins(player, type));
        }
        DisplaySettings.fromConfig().write(buffer);
    }

    // ------------------------------------------------------------------ buttons

    public static int buttonId(int mode, CoinType type, int amountIndex) {
        return mode * BUTTONS_PER_MODE + type.ordinal() * AMOUNTS.length + amountIndex;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id < 0 || id >= BUTTONS_PER_MODE * 2 || !stillValid(player)) {
            return false;
        }

        int mode = id / BUTTONS_PER_MODE;
        int rest = id % BUTTONS_PER_MODE;
        CoinType type = CoinType.ORDERED[rest / AMOUNTS.length];
        int preset = AMOUNTS[rest % AMOUNTS.length];
        String symbol = CurrencyConfig.get().currencySymbol;

        WalletManager.Exchange result = mode == MODE_TO_CASH
                ? WalletManager.exchangeToCash(serverPlayer, type, preset)
                : WalletManager.exchangeToCoins(serverPlayer, type, preset);

        if (result.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable(
                            mode == MODE_TO_CASH
                                    ? "message.athens_coins.no_coins"
                                    : "message.athens_coins.no_cash",
                            type.shortName()).withStyle(ChatFormatting.RED),
                    true);
        } else {
            serverPlayer.displayClientMessage(Component.translatable(
                            mode == MODE_TO_CASH
                                    ? "message.athens_coins.exchanged_to_cash"
                                    : "message.athens_coins.exchanged_to_coins",
                            result.coins(), type.shortName(),
                            Money.format(result.cents(), symbol))
                            .withStyle(ChatFormatting.GREEN),
                    true);
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS,
                    0.4F, mode == MODE_TO_CASH ? 1.4F : 1.0F);
        }

        refreshFrom(serverPlayer);
        ModNetwork.toPlayer(serverPlayer, new S2CWalletSyncPacket(cashCents, coinCounts, true));
        return true;
    }

    // ------------------------------------------------------------------ state

    public void refreshFrom(ServerPlayer player) {
        this.cashCents = WalletManager.accountOf(player).balance();
        int[] counts = WalletManager.countAllCoins(player);
        System.arraycopy(counts, 0, coinCounts, 0, counts.length);
        this.display = DisplaySettings.fromConfig();
    }

    @Override
    public void applyState(long newCash, int[] newCounts, boolean atmNearby) {
        this.cashCents = newCash;
        System.arraycopy(newCounts, 0, coinCounts, 0, coinCounts.length);
    }

    // ------------------------------------------------------------------ accessors

    public long cashCents() {
        return cashCents;
    }

    public int coinCount(CoinType type) {
        return coinCounts[type.ordinal()];
    }

    public DisplaySettings display() {
        return display;
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
