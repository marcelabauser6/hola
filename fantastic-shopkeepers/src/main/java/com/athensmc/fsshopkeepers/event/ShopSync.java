package com.athensmc.fsshopkeepers.event;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps clients told which entities are shopkeepers.
 *
 * <p>Clients need this to defend a shopkeeper from mods that act on a click locally, before the server is involved. The list
 * has to live on the client for that check to be possible at all.</p>
 *
 * <p>Sent when a player joins, and afterwards only when the set actually changes. Change is detected by comparing the
 * registry's version counter once a second rather than by hooking every place a shop could spawn: chunk loading re-spawns
 * bodies constantly, and a broadcast per spawn would be hundreds of packets for a set that ends up identical.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID)
public final class ShopSync {

    /** How often the version is compared. Twenty ticks is a second, which is far faster than anyone can react. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    private static int ticks;

    /** The version last broadcast, or {@code -1} when nothing has been sent yet this run. */
    private static int lastBroadcastVersion = -1;

    private ShopSync() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks < CHECK_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;

        MinecraftServer server = event.getServer();
        if (server == null || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        ShopRegistry registry = ShopRegistry.get(server);
        if (registry.bodyVersion() == lastBroadcastVersion) {
            return;
        }
        lastBroadcastVersion = registry.bodyVersion();
        Net.sendShopBodiesToAll(registry.bodyIds());
    }

    /**
     * Sends the set to a player who has just joined.
     *
     * <p>Sent to them alone rather than broadcast, and without touching the version counter, so one player joining does not
     * suppress the next real change for everyone else.</p>
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Net.sendShopBodies(player, ShopRegistry.get(player.server).bodyIds());
    }

    /**
     * Forgets what was broadcast when the server stops.
     *
     * <p>The registry starts again from version zero on the next world, and a remembered version from the last one would
     * match it by accident and stop the first broadcast from happening at all.</p>
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastBroadcastVersion = -1;
        ticks = 0;
    }
}
