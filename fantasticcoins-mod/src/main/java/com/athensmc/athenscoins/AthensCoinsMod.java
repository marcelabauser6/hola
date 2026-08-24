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
import net.minecraftforge.event.entity.player.PlayerEvent;
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
        com.athensmc.athenscoins.block.ModBlockEntities.register(modEventBus);
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
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // Seed the client's balance cache so other mods' GUIs have a figure from the first frame.
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.athensmc.athenscoins.bank.BankManager.migrateLegacyWallets(player.server);
            com.athensmc.athenscoins.bank.Bank bank =
                    com.athensmc.athenscoins.bank.BankManager.bankOf(player);
            if (bank != null) {
                com.athensmc.athenscoins.bank.BankManager.setWalletLimit(
                        player.server, bank, bank.walletLimit(), player.getUUID());
            }
            com.athensmc.athenscoins.wallet.WalletManager.pushBalance(player);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Pick up any edits made to the config file while the game was closed.
        CurrencyConfig.load();
        TransferManager.clear();
        // A confirmation is a question asked of somebody standing at a terminal; one that survived a
        // restart would be a click nobody remembers making.
        com.athensmc.athenscoins.bank.AccountOffers.clear();
        com.athensmc.athenscoins.bank.LoanNotices.reset();
        com.athensmc.athenscoins.bank.BankManager.migrateLegacyWallets(event.getServer());
        for (com.athensmc.athenscoins.bank.BankAccount account
                : com.athensmc.athenscoins.bank.BankData.get(event.getServer()).allAccounts()) {
            account.resetCommissionSession();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        // Pending requests are per-session; never leak them into the next world.
        TransferManager.clear();
        com.athensmc.athenscoins.bank.AccountOffers.clear();
        // Same reason: a single-player client that opens a second world must not be handed the first
        // world's economy figures on the next hologram refresh.
        com.athensmc.athenscoins.stats.StatsCache.clear();
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
            com.athensmc.athenscoins.bank.AccountOffers.tick(server);
            tickBanking(server);
        }
    }

    /**
     * Collects any commission periods that came due and applies overdue loan interest.
     *
     * <p>Runs once a second over the account list. Periods are counted from each account's own
     * clock, so this stays correct whether the server has been up for a minute or was off for
     * days.</p>
     */
    private void tickBanking(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (com.athensmc.athenscoins.bank.BankAccount account
                : com.athensmc.athenscoins.bank.BankData.get(server).allAccounts()) {
            com.athensmc.athenscoins.bank.BankManager.collectCommission(server, account, now);
            com.athensmc.athenscoins.bank.BankManager.accrueInterest(server, account, now);
            // Rate-limited inside: an overdue loan is overdue on every one of these ticks.
            com.athensmc.athenscoins.bank.LoanNotices.remindIfOverdue(server, account, now);
        }
    }
}
