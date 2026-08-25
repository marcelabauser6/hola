package com.athensmc.skpickupguard;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * Asks Shopkeepers whether an entity is one of its shops.
 *
 * <p>By reflection, and through Bukkit, because Shopkeepers is a plugin and this is a Forge mod. On a hybrid
 * server they share a JVM; compiling against it would mean this mod fails to load with a
 * {@code NoClassDefFoundError} on any server without the plugin, before reaching code able to say so.</p>
 *
 * <p>When the answer cannot be obtained - no Shopkeepers, not a hybrid server, plugin still starting - the
 * answer is <strong>no</strong>, and the pickup is allowed. Guarding on an unknown would break Easy Villagers
 * for every ordinary villager on a server that has no shops to protect.</p>
 */
public final class ShopkeeperCheck {

    private static Method getBukkitEntity;
    private static Method getShopkeeperRegistry;
    private static Method isShopkeeper;
    private static boolean resolved;
    private static boolean available;

    private ShopkeeperCheck() {
    }

    /** True when the entity is a Shopkeepers shop. */
    public static boolean isShopkeeper(Entity entity) {
        if (entity == null || !resolve()) {
            return false;
        }
        try {
            Object bukkitEntity = getBukkitEntity.invoke(entity);
            if (bukkitEntity == null) {
                return false;
            }
            Object registry = getShopkeeperRegistry.invoke(null);
            if (registry == null) {
                return false;
            }
            Object answer = isShopkeeper.invoke(registry, bukkitEntity);
            return answer instanceof Boolean yes && yes;
        } catch (ReflectiveOperationException | RuntimeException cannotTell) {
            // Cannot tell means allow, for the same reason as above: a server without Shopkeepers must not
            // lose the ability to pick up villagers because this check went wrong.
            return false;
        }
    }

    /** True when Shopkeepers could be reached at all, for the startup log. */
    public static boolean isAvailable() {
        return resolve();
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return available;
        }
        resolved = true;
        try {
            // Present only on a CraftBukkit-derived server. Its absence is how this mod knows it is on plain
            // Forge, where there are no shopkeepers to protect.
            getBukkitEntity = Entity.class.getMethod("getBukkitEntity");

            Class<?> api = Class.forName("com.nisovin.shopkeepers.api.ShopkeepersAPI");
            getShopkeeperRegistry = api.getMethod("getShopkeeperRegistry");

            Class<?> registry =
                    Class.forName("com.nisovin.shopkeepers.api.shopkeeper.ShopkeeperRegistry");
            isShopkeeper = registry.getMethod("isShopkeeper",
                    Class.forName("org.bukkit.entity.Entity"));

            available = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notPresent) {
            available = false;
        }
        return available;
    }
}
