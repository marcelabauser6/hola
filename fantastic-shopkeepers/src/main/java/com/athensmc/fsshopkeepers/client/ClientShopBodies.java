package com.athensmc.fsshopkeepers.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which entities the client knows to be shopkeepers.
 *
 * <p>Filled from {@link com.athensmc.fsshopkeepers.net.ShopBodiesPacket}, because there is no other way for a client to
 * tell: a shopkeeper is an ordinary mob with a tag in its server-side data, and entity NBT is never sent to clients.</p>
 *
 * <p>Read from the client's own thread during input handling, and written from the network thread's work queue, which also
 * runs on the client thread. It is a plain {@link HashSet} for that reason - the two never touch it at the same time.</p>
 */
public final class ClientShopBodies {

    private static Set<UUID> bodies = Set.of();

    private ClientShopBodies() {
    }

    /** Replaces the whole set. The server sends all of it whenever any of it changes. */
    public static void replace(List<UUID> ids) {
        bodies = ids.isEmpty() ? Set.of() : new HashSet<>(ids);
    }

    public static boolean isShopBody(UUID entityId) {
        return entityId != null && bodies.contains(entityId);
    }

    /**
     * Forgets everything.
     *
     * <p>Called on disconnect. Stale uuids would otherwise be carried into the next world, where they could match an
     * unrelated entity and make an ordinary villager unclickable.</p>
     */
    public static void clear() {
        bodies = Set.of();
    }
}
