package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.AthensCoinsMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, AthensCoinsMod.MOD_ID);

    public static final RegistryObject<MenuType<WalletMenu>> WALLET =
            MENUS.register("wallet", () -> IForgeMenuType.create(WalletMenu::new));

    public static final RegistryObject<MenuType<AtmMenu>> ATM =
            MENUS.register("atm", () -> IForgeMenuType.create(AtmMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
