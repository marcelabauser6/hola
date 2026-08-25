package com.athensmc.fsshopkeepers.shop;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Puts shop mobs into the world and keeps them there.
 *
 * <p>A shopkeeper is a real vanilla mob, not a custom entity. Any creature known by the server can therefore be a
 * shopkeeper, including creatures from other mods. Variant NBT is applied while a fresh entity is constructed; a live
 * mob is never round-tripped through {@code saveWithoutId/load}, because saving an uninitialised cartographer asks it to
 * generate treasure-map offers and can synchronously search for an ocean monument on the server thread.</p>
 */
public final class ShopSpawner {

    /** Forge-persistent marker linking a body to its shop. */
    private static final String SHOP_TAG = "FsShopkeeperId";

    private ShopSpawner() {
    }

    /** True when this entity is a shopkeeper's body. */
    public static boolean isShopEntity(Entity entity) {
        return entity != null && entity.getPersistentData().hasUUID(SHOP_TAG);
    }

    /** The shop id written on a mob, or null when it is not a shopkeeper. */
    public static UUID shopIdOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(SHOP_TAG) ? data.getUUID(SHOP_TAG) : null;
    }

    /** The mob currently representing a shop, or null when it is gone. */
    public static Entity findEntity(ServerLevel level, Shopkeeper shop) {
        UUID entityId = shop.entityId();
        if (entityId == null) {
            return null;
        }
        Entity entity = level.getEntity(entityId);
        return entity == null || entity.isRemoved() ? null : entity;
    }

    /**
     * Spawns a missing body once.
     *
     * <p>The UUID is published before {@link ServerLevel#addFreshEntity(Entity)}. EntityJoinLevelEvent runs inside that
     * call; publishing afterwards made this mod's own reconciliation compare the new body with a stale UUID, reject it as
     * a duplicate, and retry three seconds later forever.</p>
     *
     * <p>If construction or another mod still rejects the body, the failure is persisted on the shop. Automatic sync then
     * leaves it alone instead of producing an infinite spawn/log loop. An explicit edit or move clears the block and grants
     * one new attempt.</p>
     */
    public static boolean ensureSpawned(ServerLevel level, Shopkeeper shop, ShopRegistry registry) {
        if (!shop.objectKind().hasEntity() || shop.bodySpawnBlocked()) {
            return false;
        }
        Entity current = findEntity(level, shop);
        if (current != null) {
            applyShopFlags(current, shop);
            return false;
        }

        Entity spawned = buildEntity(level, shop);
        if (spawned == null) {
            registry.updateBody(shop, null, true);
            FantasticShopkeepers.LOGGER.error(
                    "Suspendido el respawn del NPC de la tienda {}: no se pudo construir. Edita o mueve la tienda para reintentarlo.",
                    shop.id());
            return false;
        }

        // Must happen before addFreshEntity: the join event can now recognise this exact UUID as the expected body.
        registry.updateBody(shop, spawned.getUUID(), false);
        if (!level.addFreshEntity(spawned)) {
            spawned.discard();
            registry.updateBody(shop, null, true);
            FantasticShopkeepers.LOGGER.error(
                    "El servidor rechazo el NPC de la tienda {}. Respawn automatico suspendido para evitar el bucle de 3 segundos; edita o mueve la tienda para reintentar.",
                    shop.id());
            return false;
        }
        return true;
    }

    /** Gives a staff-driven operation one new spawn attempt after a previous rejection. */
    public static void allowSpawnRetry(Shopkeeper shop, ShopRegistry registry) {
        if (shop.bodySpawnBlocked()) {
            registry.updateBody(shop, shop.entityId(), false);
        }
    }

    /**
     * Constructs a fresh entity directly from the small variant tag.
     *
     * <p>No {@code finalizeSpawn} followed by save/load is needed for a frozen command-created body. The constructor
     * supplies normal defaults and the stored NBT supplies only the selected appearance. Avoiding the second full NBT pass
     * is what prevents cartographer trade generation and synchronous structure searches.</p>
     */
    private static Entity buildEntity(ServerLevel level, Shopkeeper shop) {
        CompoundTag tag = shop.entityData();
        tag.putString("id", shop.entityType().toString());
        tag.remove("UUID");
        tag.remove("Pos");

        Entity entity;
        try {
            entity = EntityType.loadEntityRecursive(tag, level, built -> {
                built.moveTo(shop.pos().getX() + 0.5D, shop.pos().getY(), shop.pos().getZ() + 0.5D,
                        built.getYRot(), 0.0F);
                return built;
            });
        } catch (RuntimeException badData) {
            FantasticShopkeepers.LOGGER.error("No se pudo crear el NPC de la tienda {} ({}): {}",
                    shop.id(), shop.entityType(), badData.toString());
            return null;
        }
        if (entity == null) {
            FantasticShopkeepers.LOGGER.warn(
                    "La tienda {} usa la entidad {}, que este servidor no conoce. La tienda se conserva sin cuerpo.",
                    shop.id(), shop.entityType());
            return null;
        }
        applyShopFlags(entity, shop);
        return entity;
    }

    /**
     * Makes an entity behave as a shop body and neutralises vanilla villager offers.
     *
     * <p>An empty, already-initialised offer list is important. {@code AbstractVillager#getOffers()} lazily generates
     * offers when its field is null; for a cartographer that includes {@code locateStructure}. Giving shop villagers a
     * real empty list means later world saves serialize it immediately and never invoke trade generation.</p>
     */
    public static void applyShopFlags(Entity entity, Shopkeeper shop) {
        ShopConfig config = ShopConfig.get();
        entity.getPersistentData().putUUID(SHOP_TAG, shop.id());
        entity.setInvulnerable(true);
        entity.setSilent(config.silenceShopEntities);
        entity.setNoGravity(false);

        String name = shop.displayName();
        if (name.isBlank()) {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        } else {
            entity.setCustomName(shop.displayNameComponent());
            entity.setCustomNameVisible(config.alwaysShowNameplates);
        }

        if (entity instanceof Villager villager) {
            neutraliseVillagerOffers(villager);
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setCanPickUpLoot(false);
            mob.setBaby(mob.isBaby());
        }
        if (entity instanceof ItemEntity item) {
            item.setUnlimitedLifetime();
        }
    }

    /**
     * Re-initialises offers on every loaded shop villager without touching normal villagers.
     *
     * <p>{@code EntityJoinLevelEvent} is the first defence, but hybrid servers can restore or transfer entities through
     * paths whose ordering differs from plain Forge. The world entity store later serialises those entities directly;
     * serialising a cartographer whose offers are still null calls {@code getOffers()}, generates its treasure-map trade,
     * and performs a synchronous monument search from the autosave thread. This lightweight sweep runs before the mod's
     * once-per-second body check, comfortably before normal autosaves.</p>
     *
     * <p>The registry pass also covers a body whose Forge marker was stripped by another plugin. The level pass covers old
     * marked bodies whose UUID index is stale, and reconciles actual leftovers. Only bodies identified by this mod are
     * changed; ordinary and Citizens villagers retain their vanilla offers.</p>
     */
    public static void sanitizeLoadedVillagerBodies(MinecraftServer server, ShopRegistry registry) {
        Set<UUID> handled = new HashSet<>();

        for (Shopkeeper shop : registry.all()) {
            if (!shop.objectKind().hasEntity()) {
                continue;
            }
            ServerLevel level = server.getLevel(shop.level());
            if (level == null) {
                continue;
            }
            Entity body = findEntity(level, shop);
            if (body instanceof Villager) {
                applyShopFlags(body, shop);
                handled.add(body.getUUID());
            }
        }

        EntityTypeTest<Entity, Villager> villagers = EntityTypeTest.forClass(Villager.class);
        for (ServerLevel level : server.getAllLevels()) {
            for (Villager villager : level.getEntities(villagers, ShopSpawner::isShopEntity)) {
                if (handled.add(villager.getUUID())) {
                    reconcileLoadedBody(villager, registry);
                }
            }
        }
    }

    /**
     * Installs a concrete empty list without asking the villager to generate its vanilla offers.
     *
     * <p>Do not use {@code Merchant#overrideOffers} here: {@link Villager} inherits a deliberately empty server-side
     * implementation from {@code AbstractVillager}. {@link Villager#setOffers(MerchantOffers)} is the real mutator for the
     * protected offers field and is what prevents {@code getOffers()} from entering {@code updateTrades()} during save.</p>
     */
    private static void neutraliseVillagerOffers(Villager villager) {
        villager.setOffers(new MerchantOffers());
    }

    /** Refreshes flags and text only; variant changes replace the body rather than loading NBT into a live mob. */
    public static void refreshAppearance(ServerLevel level, Shopkeeper shop, ShopRegistry registry) {
        Entity entity = findEntity(level, shop);
        if (entity == null) {
            ensureSpawned(level, shop, registry);
            return;
        }
        boolean sameSpecies = EntityType.getKey(entity.getType()).equals(shop.entityType());
        if (!sameSpecies) {
            despawn(level, shop, registry);
            ensureSpawned(level, shop, registry);
            return;
        }
        applyShopFlags(entity, shop);
    }

    /** Removes a shop body, clearing both stale UUID indices and any previous spawn block. */
    public static void despawn(ServerLevel level, Shopkeeper shop, ShopRegistry registry) {
        if (shop.entityId() != null) {
            level.getChunk(shop.pos());
        }
        Entity entity = findEntity(level, shop);
        if (entity != null) {
            entity.discard();
        } else if (shop.entityId() != null) {
            FantasticShopkeepers.LOGGER.warn(
                    "No se encontro el cuerpo {} de la tienda {} para retirarlo; si reaparece se limpiara al cargar.",
                    shop.entityId(), shop.id());
        }
        registry.updateBody(shop, null, false);
    }

    /**
     * Reconciles a body as it enters a level.
     *
     * <p>The exact pre-published UUID is accepted. A body is adopted only when the shop has no recorded body; any other
     * UUID is an actual leftover and is discarded. Accepted old bodies are also sanitised here, so cartographer NPCs made
     * by earlier versions receive an empty offer list before a later chunk save can generate maps.</p>
     *
     * @return true when the entity was discarded.
     */
    public static boolean reconcileLoadedBody(Entity entity, ShopRegistry registry) {
        UUID shopId = shopIdOf(entity);
        if (shopId == null) {
            return false;
        }
        Shopkeeper shop = registry.byId(shopId);
        if (shop == null || !shop.objectKind().hasEntity()) {
            entity.discard();
            return true;
        }
        UUID recorded = shop.entityId();
        if (recorded == null) {
            registry.updateBody(shop, entity.getUUID(), false);
            applyShopFlags(entity, shop);
            return false;
        }
        if (!recorded.equals(entity.getUUID())) {
            FantasticShopkeepers.LOGGER.info("Retirado un tendero duplicado de la tienda {}.", shop.id());
            entity.discard();
            return true;
        }
        if (shop.bodySpawnBlocked()) {
            registry.updateBody(shop, recorded, false);
        }
        applyShopFlags(entity, shop);
        return false;
    }
}
