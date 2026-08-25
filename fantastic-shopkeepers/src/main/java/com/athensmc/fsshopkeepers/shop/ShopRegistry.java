package com.athensmc.fsshopkeepers.shop;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every shop on the server, saved with the world.
 *
 * <p>Stored as world data rather than as a file of this mod's own. That means shops are backed up, copied and
 * rolled back with the save, so restoring yesterday's world restores yesterday's shops instead of leaving a
 * separate file describing shops whose chests and NPCs no longer match it. Shopkeepers kept its own
 * {@code save.yml} and paid for it with a whole subsystem of snapshots and recovery; letting the world own the
 * data removes the problem rather than managing it.</p>
 *
 * <p>Attached to the overworld's storage even for shops in other dimensions, because there is one registry for
 * the server and the overworld is the one level guaranteed to be loaded.</p>
 *
 * <p>Three lookups are kept alongside the main map, all rebuilt from it: by the mob's uuid, by block position and
 * by owner. They exist because each is asked on a hot path - every entity interaction, every block break, every
 * shop-creation attempt - and scanning every shop on the server for those would make the cost of a click grow
 * with the size of the server.</p>
 */
public final class ShopRegistry extends SavedData {

    /** The file this becomes inside the world's {@code data} folder. */
    public static final String FILE_ID = FantasticShopkeepers.MOD_ID + "_shops";

    private static final String SHOPS = "Shops";
    private static final int TAG_COMPOUND = 10;

    /**
     * Bumped whenever the set of live shop bodies could have changed.
     *
     * <p>The client needs to know which entities are shopkeepers, and the only cheap way to keep it up to date is to
     * notice when the answer changes. A counter compared once a second costs nothing; broadcasting the set on every
     * chunk load would send it hundreds of times for no reason.</p>
     */
    private int bodyVersion;

    private final Map<UUID, Shopkeeper> byId = new LinkedHashMap<>();
    private final Map<UUID, UUID> byEntity = new HashMap<>();
    private final Map<PlacedAt, UUID> byPosition = new HashMap<>();
    private final Map<UUID, List<UUID>> byOwner = new HashMap<>();

    /** A shop's location, as a map key. */
    private record PlacedAt(ResourceKey<Level> level, long packedPos) {
        static PlacedAt of(ResourceKey<Level> level, BlockPos pos) {
            return new PlacedAt(level, pos.asLong());
        }
    }

    public ShopRegistry() {
    }

    /** The registry for a server, created empty the first time. */
    public static ShopRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ShopRegistry::load, ShopRegistry::new, FILE_ID);
    }

    public static ShopRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    public int count() {
        return byId.size();
    }

    /** The current version of the set of shop bodies. */
    public int bodyVersion() {
        return bodyVersion;
    }

    /**
     * The uuids of every entity that is a shopkeeper right now.
     *
     * <p>Sent to clients so they can recognise a shopkeeper without reading server-side data. Entity NBT is not synced, so
     * a client has no other way to tell a shop villager from an ordinary one - which is exactly why another mod could pick
     * one up before this mod had a chance to refuse.</p>
     */
    public List<UUID> bodyIds() {
        List<UUID> ids = new ArrayList<>();
        for (Shopkeeper shop : byId.values()) {
            if (shop.entityId() != null) {
                ids.add(shop.entityId());
            }
        }
        return ids;
    }

    /** Every shop, in the order they were created. */
    public Collection<Shopkeeper> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public Shopkeeper byId(UUID shopId) {
        return shopId == null ? null : byId.get(shopId);
    }

    /** The shop a mob represents, or null when the mob is not a shopkeeper. */
    public Shopkeeper byEntity(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        UUID shopId = byEntity.get(entityId);
        return shopId == null ? null : byId.get(shopId);
    }

    /** The shop standing at a block, for sign shops and for protecting the ground a shop stands on. */
    public Shopkeeper byPosition(ResourceKey<Level> level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        UUID shopId = byPosition.get(PlacedAt.of(level, pos));
        return shopId == null ? null : byId.get(shopId);
    }

    /**
     * The shop drawing stock from a container, or null when the container is not a shop's.
     *
     * <p>Walked rather than indexed. A container is only looked up when one is broken or opened by someone who is
     * not the owner, which is rare enough that a scan is cheaper than a fourth index to keep correct.</p>
     */
    public Shopkeeper byContainer(ResourceKey<Level> level, BlockPos containerPos) {
        if (level == null || containerPos == null) {
            return null;
        }
        for (Shopkeeper shop : byId.values()) {
            if (shop.level().equals(level) && containerPos.equals(shop.containerPos())) {
                return shop;
            }
        }
        return null;
    }

    /** Every shop a player owns. */
    public List<Shopkeeper> shopsOf(UUID owner) {
        if (owner == null) {
            return List.of();
        }
        List<UUID> ids = byOwner.get(owner);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Shopkeeper> shops = new ArrayList<>(ids.size());
        for (UUID shopId : ids) {
            Shopkeeper shop = byId.get(shopId);
            if (shop != null) {
                shops.add(shop);
            }
        }
        return Collections.unmodifiableList(shops);
    }

    /** How many shops a player owns, for the per-player limit. */
    public int countOf(UUID owner) {
        List<UUID> ids = byOwner.get(owner);
        return ids == null ? 0 : ids.size();
    }

    /** Every admin shop, for the admin listing. */
    public List<Shopkeeper> adminShops() {
        List<Shopkeeper> shops = new ArrayList<>();
        for (Shopkeeper shop : byId.values()) {
            if (!shop.type().isPlayerShop()) {
                shops.add(shop);
            }
        }
        return Collections.unmodifiableList(shops);
    }

    /** Registers a new shop, or re-registers one whose indexed fields changed. */
    public void put(Shopkeeper shop) {
        if (shop == null) {
            return;
        }
        Shopkeeper previous = byId.get(shop.id());
        if (previous != null) {
            unindex(previous);
        }
        byId.put(shop.id(), shop);
        index(shop);
        bodyVersion++;
        setDirty();
    }

    /**
     * Re-indexes a shop after an edit.
     *
     * <p>Called whenever a shop's mob, position or owner changes. Without it a shop that was moved would still be
     * found at its old position and not at its new one, which is the kind of bug that only shows up once someone
     * builds on the old spot.</p>
     */
    public void refresh(Shopkeeper shop) {
        put(shop);
    }

    public Shopkeeper remove(UUID shopId) {
        Shopkeeper shop = byId.remove(shopId);
        if (shop != null) {
            unindex(shop);
            bodyVersion++;
            setDirty();
        }
        return shop;
    }

    /**
     * Atomically changes the body identity and spawn-suppression state.
     *
     * <p>The shop object is mutable and already stored in {@code byId}; changing its UUID before calling
     * {@link #refresh(Shopkeeper)} makes the old UUID impossible to unindex. This method captures and removes the old
     * mapping first, then publishes the new one, which also lets an in-flight spawn be recognised by the join event.</p>
     */
    public void updateBody(Shopkeeper shop, UUID entityId, boolean spawnBlocked) {
        if (shop == null || byId.get(shop.id()) != shop) {
            return;
        }
        UUID previous = shop.entityId();
        if (previous != null) {
            byEntity.remove(previous, shop.id());
        }
        shop.setEntityId(entityId);
        shop.setBodySpawnBlocked(spawnBlocked);
        if (entityId != null) {
            byEntity.put(entityId, shop.id());
        }
        bodyVersion++;
        setDirty();
    }

    /** Marks the registry as needing a save after a field on a shop was changed directly. */
    public void touch() {
        setDirty();
    }

    private void index(Shopkeeper shop) {
        if (shop.entityId() != null) {
            byEntity.put(shop.entityId(), shop.id());
        }
        if (shop.objectKind().isPlaced()) {
            byPosition.put(PlacedAt.of(shop.level(), shop.pos()), shop.id());
        }
        if (shop.owner() != null) {
            byOwner.computeIfAbsent(shop.owner(), key -> new ArrayList<>()).add(shop.id());
        }
    }

    private void unindex(Shopkeeper shop) {
        if (shop.entityId() != null) {
            byEntity.remove(shop.entityId(), shop.id());
        }
        byPosition.remove(PlacedAt.of(shop.level(), shop.pos()), shop.id());
        if (shop.owner() != null) {
            List<UUID> owned = byOwner.get(shop.owner());
            if (owned != null) {
                owned.remove(shop.id());
                if (owned.isEmpty()) {
                    byOwner.remove(shop.owner());
                }
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Shopkeeper shop : byId.values()) {
            list.add(shop.save());
        }
        tag.put(SHOPS, list);
        return tag;
    }

    /**
     * Reads the registry back.
     *
     * <p>A shop that fails to load is dropped with a warning instead of aborting the load. One unreadable entry -
     * an item deleted by a mod update, a dimension that no longer exists - taking every shop on the server with
     * it is a far worse outcome than losing the single shop that referenced it, and an exception thrown here would
     * do exactly that.</p>
     */
    public static ShopRegistry load(CompoundTag tag) {
        ShopRegistry registry = new ShopRegistry();
        int dropped = 0;
        for (Tag element : tag.getList(SHOPS, TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag shopTag)) {
                continue;
            }
            try {
                Shopkeeper shop = Shopkeeper.load(shopTag);
                registry.byId.put(shop.id(), shop);
                registry.index(shop);
            } catch (RuntimeException unreadable) {
                dropped++;
                FantasticShopkeepers.LOGGER.warn("Tienda descartada al cargar: {}", unreadable.toString());
            }
        }
        FantasticShopkeepers.LOGGER.info("Cargadas {} tiendas{}.", registry.byId.size(),
                dropped == 0 ? "" : " (" + dropped + " descartadas)");
        return registry;
    }
}
