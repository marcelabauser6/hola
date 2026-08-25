package com.athensmc.fsshopkeepers.menu;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod's menu types. */
public final class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, FantasticShopkeepers.MOD_ID);

    /**
     * The customer-facing trading window.
     *
     * <p>Created through {@link IForgeMenuType} rather than the vanilla constructor because the window is opened
     * with the shop's offers attached. A vanilla {@code MenuType} can only build a menu from an id and an
     * inventory, which would leave the client asking for the offers in a second round trip and drawing an empty
     * window until they arrived.</p>
     */
    public static final RegistryObject<MenuType<ShopTradeMenu>> SHOP_TRADE = MENUS.register("shop_trade",
            () -> IForgeMenuType.create((containerId, inventory, buf) ->
                    new ShopTradeMenu(containerId, inventory, ShopTradeMenu.Data.read(buf))));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
