package com.athensmc.fsshopkeepers.menu;

import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

import org.jetbrains.annotations.Nullable;

/** Opens shop windows for a player. */
public final class ShopMenus {

    private ShopMenus() {
    }

    /**
     * Opens the trading window on a customer's screen.
     *
     * <p>The shop's offers are written into the opening packet, so the window is drawn correctly the first frame it
     * appears instead of filling in a moment later.</p>
     */
    public static void openTrade(ServerPlayer customer, Shopkeeper shop) {
        openTrade(customer, shop, 0);
    }

    /** Reopens a refreshed trading window without jumping back to the first offer. */
    public static void openTrade(ServerPlayer customer, Shopkeeper shop, int selectedOffer) {
        ShopTradeMenu.Data data = ShopTradeMenu.dataFor(customer, shop, selectedOffer);
        NetworkHooks.openScreen(customer, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return shop.displayNameComponent();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return new ShopTradeMenu(containerId, inventory, data);
            }
        }, data::write);
    }
}
