package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankData;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Economy snapshots, shared by every board that asks for the same one within a few seconds.
 *
 * <p>{@link EconomyStats} walks accounts and online inventories. That is fine for a command; it is not
 * fine once boards exist, because each projector refreshes on its own ticker and a market square with six
 * boards would run the same sweep six times a second apart for identical results.</p>
 *
 * <p><b>Keyed by bank, not just by server.</b> Boards are branded now: one may report the whole economy
 * while its neighbour reports one bank. A single cached snapshot would have handed both of them whichever
 * scope happened to compute first, so a bank's board would silently show the server's figures - a bug that
 * looks like working software, because the numbers are real, just not about the bank named above them.</p>
 */
public final class StatsCache {

    /** Slightly under the projectors' refresh interval, so a refresh never reuses a stale entry. */
    private static final long TTL_MS = 4_000L;
    /** The key for the server-wide snapshot, which has no bank of its own. */
    private static final UUID GLOBAL = new UUID(0L, 0L);

    private record Entry(EconomySnapshot snapshot, long computedAt) {
    }

    @Nullable
    private static MinecraftServer owner;
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private StatsCache() {
    }

    /**
     * @param bankId the bank to report on, or {@code null} for the whole server
     */
    public static synchronized EconomySnapshot snapshot(MinecraftServer server, @Nullable UUID bankId) {
        if (owner != server) {
            ENTRIES.clear();
            owner = server;
        }
        UUID key = bankId == null ? GLOBAL : bankId;
        long now = System.currentTimeMillis();
        Entry cached = ENTRIES.get(key);
        if (cached != null && now - cached.computedAt() < TTL_MS) {
            return cached.snapshot();
        }
        EconomySnapshot fresh = compute(server, bankId);
        ENTRIES.put(key, new Entry(fresh, now));
        return fresh;
    }

    /**
     * A board whose bank has been deleted falls back to the server-wide figures rather than to zeros.
     *
     * <p>Zeros would read as "this economy is empty", which is a statement about the world rather than
     * about a missing binding. The whole-server view is at least true.</p>
     */
    private static EconomySnapshot compute(MinecraftServer server, @Nullable UUID bankId) {
        if (bankId == null) {
            return EconomyStats.compute(server);
        }
        Bank bank = BankData.get(server).bank(bankId);
        return bank == null ? EconomyStats.compute(server) : EconomyStats.computeForBank(server, bank);
    }

    /** Forces the next request to recompute; used when an admin has just changed the money supply. */
    public static synchronized void invalidate() {
        ENTRIES.clear();
    }

    public static synchronized void clear() {
        owner = null;
        ENTRIES.clear();
    }
}
