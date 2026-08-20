package com.athensmc.athenscoins;

import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.command.FsCoinsCommand;
import com.athensmc.athenscoins.config.CoinsConfig;
import com.athensmc.athenscoins.item.ModCreativeModTabs;
import com.athensmc.athenscoins.item.ModItems;
import com.athensmc.athenscoins.menu.ModMenus;
import com.athensmc.athenscoins.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AthensCoinsMod.MOD_ID)
public class AthensCoinsMod {
    public static final String MOD_ID = "athens_coins";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AthensCoinsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModCreativeModTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModMenus.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CoinsConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FsCoinsCommand.register(event.getDispatcher());
    }
}
