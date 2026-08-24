package com.athensmc.athenscoins.stats;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/**
 * One economy snapshot, shared by every hologram that asks for it within a few seconds.
 *
 * <p>{@link EconomyStats#compute} walks every account in the world and every online player's
 * inventory. That is fine for an admin pressing a command; it is not fine once holograms exist,
 * because each projector refreshes on its own ticker and a market square with six boards would run the
 * same full sweep six times a second apart for identical results.</p>
 *
 * <p>Keyed on the server instance, so a single-player client that quits one world and opens another
 * cannot be handed the previous world's figures - the same reason the transfer and confirmation
 * registries are cleared on shutdown.</p>
 */
public final class StatsCache {

    /** Slightly under the projectors' refresh interval, so a refresh never reuses a stale entry. */
    private static final long TTL_MS = 4_000L;

    @Nullable
    private static MinecraftServer owner;
    @Nullable
    private static EconomySnapshot cached;
    private static long computedAt;

    private StatsCache() {
    }

    public static synchronized EconomySnapshot snapshot(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (cached == null || owner != server || now - computedAt >= TTL_MS) {
            cached = EconomyStats.compute(server);
            computedAt = now;
            owner = server;
        }
        return cached;
    }

    /** Forces the next request to recompute; used when an admin has just changed the money supply. */
    public static synchronized void invalidate() {
        computedAt = 0L;
    }

    public static synchronized void clear() {
        owner = null;
        cached = null;
        computedAt = 0L;
    }
}
