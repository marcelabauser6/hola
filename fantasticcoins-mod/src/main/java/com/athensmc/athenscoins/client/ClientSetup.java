package com.athensmc.athenscoins.client;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.client.screen.AtmScreen;
import com.athensmc.athenscoins.menu.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AthensCoinsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // The wallet is a plain Screen opened by packet, so only the ATM needs a menu screen.
        event.enqueueWork(() -> MenuScreens.register(ModMenus.ATM.get(), AtmScreen::new));
    }
}
