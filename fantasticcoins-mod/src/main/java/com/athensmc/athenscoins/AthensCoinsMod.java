package com.athensmc.athenscoins;

import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.command.FsCurrencyCommand;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.item.ModCreativeModTabs;
import com.athensmc.athenscoins.item.ModItems;
import com.athensmc.athenscoins.menu.ModMenus;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.transfer.TransferManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Fantastic Currency.
 *
 * <p>The mod id stays {@code athens_coins} on purpose: changing it would rename every item and
 * orphan the coins already sitting in players' inventories, chests and shop configs.</p>
 */
@Mod(AthensCoinsMod.MOD_ID)
public class AthensCoinsMod {

    public static final String MOD_ID = "athens_coins";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Transfer requests are swept once a second, not every tick. */
    private static final int TRANSFER_SWEEP_INTERVAL = 20;

    private int tickCounter;

    public AthensCoinsMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModCreativeModTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModMenus.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        // Read config/fantasticcurrency.json now so registries and GUIs see real values.
        CurrencyConfig.LoadResult result = CurrencyConfig.load();
        LOGGER.info("Fantastic Currency config: {}", result.detail());

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FsCurrencyCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Pick up any edits made to the config file while the game was closed.
        CurrencyConfig.load();
        TransferManager.clear();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        // Pending requests are per-session; never leak them into the next world.
        TransferManager.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < TRANSFER_SWEEP_INTERVAL) {
            return;
        }
        tickCounter = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TransferManager.tick(server);
        }
    }
}
