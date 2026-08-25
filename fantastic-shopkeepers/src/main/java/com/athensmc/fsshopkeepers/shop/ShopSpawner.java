package com.athensmc.fsshopkeepers.shop;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.UUID;

/**
 * Puts shop mobs into the world and keeps them there.
 *
 * <p>A shopkeeper is a real vanilla mob, not a custom entity. That choice is what makes any creature the server
 * knows about usable as a shopkeeper, including creatures added by other mods, and it means a client without this
 * mod still sees a normal sheep standing there rather than a missing-entity error.</p>
 *
 * <p>The mob is built from NBT, which is how a variant is applied. Handing vanilla a tag of
 * {@code {id: "minecraft:sheep", Color: 4}} and letting it construct the entity reuses the game's own loading code
 * for every field a mob can have. Setting those fields one by one would mean knowing about each of them, which is
 * the trap that turned 30 mobs into 83 classes in the original.</p>
 */
public final class ShopSpawner {

    /**
     * The key marking a mob as a shopkeeper, stored in Forge's persistent data so it survives a restart.
     *
     * <p>Written on the entity as well as recorded in the registry. The registry is the source of truth, but the
     * tag lets an interaction be answered from the entity alone, and lets a stray shop mob left behind by a
     * deleted shop be recognised and cleaned up.</p>
     */
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
     * Spawns a shop's mob if it is missing.
     *
     * <p>Called when a chunk loads and after an edit. Doing nothing when the mob is already there is what makes it
     * safe to call repeatedly, which matters because chunk load events fire far more often than shops change.</p>
     *
     * @return true when a mob was created by this call.
     */
    public static boolean ensureSpawned(ServerLevel level, Shopkeeper shop, ShopRegistry registry) {
        if (!shop.objectKind().hasEntity()) {
            return false;
        }
        if (findEntity(level, shop) != null) {
            return false;
        }
        Entity spawned = spawn(level, shop);
        if (spawned == null) {
            return false;
        }
        shop.setEntityId(spawned.getUUID());
        registry.refresh(shop);
        return true;
    }

    /**
     * Builds and adds the mob.
     *
     * <p>Returns null rather than throwing when the entity type does not resolve, because an unknown type means a
     * mod was removed and the right response is to leave the shop in the registry - so its trades and owner are not
     * lost - and log it, not to crash the chunk load.</p>
     */
    private static Entity spawn(ServerLevel level, Shopkeeper shop) {
        CompoundTag tag = shop.entityData();
        tag.putString("id", shop.entityType().toString());
        // A fresh identity every spawn: reusing the saved uuid would collide with the corpse of a mob the
        // server has not finished removing, and vanilla silently drops the second entity when that happens.
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
                    "La tienda {} usa la entidad {}, que este servidor no conoce. La tienda se conserva pero no "
                            + "tiene cuerpo.", shop.id(), shop.entityType());
            return null;
        }

        applyShopFlags(entity, shop);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(shop.pos()), MobSpawnType.COMMAND, null, null);
            // finalizeSpawn can re-roll a variant, so the stored appearance is applied again after it.
            reapplyVariant(mob, shop);
        }
        if (!level.addFreshEntity(entity)) {
            FantasticShopkeepers.LOGGER.warn("El servidor rechazo el NPC de la tienda {}.", shop.id());
            return null;
        }
        return entity;
    }

    /**
     * Re-reads the stored variant onto a mob after vanilla may have randomised it.
     *
     * <p>{@code finalizeSpawn} is what gives a naturally spawned sheep its colour, and it does not know the colour
     * was already chosen. Without this a white sheep shop comes back a random colour every time its chunk
     * reloads.</p>
     */
    private static void reapplyVariant(Mob mob, Shopkeeper shop) {
        CompoundTag variant = shop.entityData();
        if (variant.isEmpty()) {
            return;
        }
        CompoundTag full = new CompoundTag();
        mob.saveWithoutId(full);
        for (String key : variant.getAllKeys()) {
            full.put(key, variant.get(key).copy());
        }
        try {
            mob.load(full);
        } catch (RuntimeException badVariant) {
            FantasticShopkeepers.LOGGER.warn("No se pudo aplicar la apariencia de la tienda {}: {}",
                    shop.id(), badVariant.toString());
        }
    }

    /**
     * Makes a mob behave like a shopkeeper rather than like a mob.
     *
     * <p>Frozen, invulnerable, never despawned and never picking anything up. Each of those is a way a shop
     * otherwise walks away, dies, vanishes on a distant chunk, or eats an item a player dropped nearby.</p>
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

        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setCanPickUpLoot(false);
            // A shop that can be led away on a lead, or bred, is a shop that stops being where it was put.
            mob.setBaby(mob.isBaby());
        }
        if (entity instanceof ItemEntity item) {
            // Not a valid shop body, and left un-flagged rather than special-cased further up.
            item.setUnlimitedLifetime();
        }
    }

    /** Refreshes a live mob after an edit, without a respawn when the species did not change. */
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
        if (entity instanceof Mob mob) {
            reapplyVariant(mob, shop);
            applyShopFlags(mob, shop);
        }
    }

    /**
     * Removes a shop's mob from the world, leaving the shop itself registered.
     *
     * <p>The chunk is loaded first. {@link ServerLevel#getEntity} only knows about entities that are loaded, so despawning
     * a shop nobody is standing near found nothing, cleared the recorded uuid, and left the old body in the world - which is
     * how moving a shop ended up with two of them.</p>
     */
    public static void despawn(ServerLevel level, Shopkeeper shop, ShopRegistry registry) {
        if (shop.entityId() != null) {
            // Forces the chunk in, so the body is reachable even if no player is nearby.
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
        shop.setEntityId(null);
        registry.refresh(shop);
    }

    /**
     * Decides what to do with a shop body that has just loaded.
     *
     * <p>A mob carrying a shop tag whose shop no longer points at it is a leftover: the shop already has another body, so
     * this one is removed. A tag whose shop points at nothing is adopted instead, which recovers a shop whose body could not
     * be found when it should have been removed.</p>
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
            // The shop is gone, so its body has no reason to exist.
            entity.discard();
            return true;
        }
        UUID recorded = shop.entityId();
        if (recorded == null) {
            shop.setEntityId(entity.getUUID());
            registry.refresh(shop);
            return false;
        }
        if (!recorded.equals(entity.getUUID())) {
            FantasticShopkeepers.LOGGER.info("Retirado un tendero duplicado de la tienda {}.", shop.id());
            entity.discard();
            return true;
        }
        return false;
    }
}
