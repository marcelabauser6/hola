package com.athensmc.fsshopkeepers.client;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.client.screen.ShopTradeScreen;
import com.athensmc.fsshopkeepers.menu.ModMenus;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Wires the client's screens to the menus the server opens. */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    private ClientSetup() {
    }

    /**
     * Registers the trading screen.
     *
     * <p>Done inside {@code enqueueWork} because the screen registry is shared between mods and client setup runs in
     * parallel by default.</p>
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.SHOP_TRADE.get(), ShopTradeScreen::new));
    }
}
