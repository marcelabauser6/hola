package com.athensmc.fsshopkeepers.event;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    /**
     * How many checks in a row a body must be missing before it is re-created.
     *
     * <p>Not one. An entity is registered a moment after its chunk loads, so a single miss means "not yet" at least as often
     * as it means "gone" - and acting on the first miss is what filled the world with duplicate shopkeepers. Three misses is
     * three seconds, long enough that the entity store has certainly settled.</p>
     */
    private static final int MISSES_BEFORE_RESPAWN = 3;

    /** Consecutive checks each shop's body has been missing for. */
    private static final Map<UUID, Integer> misses = new HashMap<>();

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
        restoreMissingBodies(server, registry);

        if (registry.bodyVersion() == lastBroadcastVersion) {
            return;
        }
        lastBroadcastVersion = registry.bodyVersion();
        Net.sendShopBodiesToAll(registry.bodyIds());
    }

    /**
     * Puts back shop bodies that have genuinely gone.
     *
     * <p>This is where bodies are created, rather than when a chunk loads. Chunk load was the wrong moment: entities load from
     * a separate store a little after the chunk itself, so the shop looked bodyless and a second one was made - every reload
     * could add another. Here the shop must look bodyless for {@link #MISSES_BEFORE_RESPAWN} checks in a row, and only while
     * its position is loaded, so "not yet" is never mistaken for "gone".</p>
     */
    private static void restoreMissingBodies(MinecraftServer server, ShopRegistry registry) {
        for (Shopkeeper shop : registry.all()) {
            if (!shop.objectKind().hasEntity()) {
                misses.remove(shop.id());
                continue;
            }
            ServerLevel level = server.getLevel(shop.level());
            if (level == null || !level.isLoaded(shop.pos())) {
                // Nothing can be judged about a place that is not loaded.
                misses.remove(shop.id());
                continue;
            }
            if (ShopSpawner.findEntity(level, shop) != null) {
                misses.remove(shop.id());
                continue;
            }
            int count = misses.merge(shop.id(), 1, Integer::sum);
            if (count >= MISSES_BEFORE_RESPAWN) {
                misses.remove(shop.id());
                if (ShopSpawner.ensureSpawned(level, shop, registry)) {
                    FantasticShopkeepers.LOGGER.info("Repuesto el tendero de la tienda {}.", shop.id());
                }
            }
        }
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
        misses.clear();
    }
}
