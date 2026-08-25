package com.athensmc.fcbridge;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Builds Bukkit item stacks for FantasticCurrency's coins.
 *
 * <p>This is the piece that makes the whole integration possible, and it is worth saying why it is needed.
 * Shopkeepers picks its currency out of the config by Bukkit {@code Material} name, and a modded item is not
 * in that enum - so {@code currency-item: athens_coins:gold_coin} cannot work no matter how it is spelled.
 * But Shopkeepers matches currency at runtime through {@code ItemData}, which wraps a whole
 * {@code ItemStack}, and an {@code ItemStack} <em>can</em> hold a modded item on a hybrid server. So the coin
 * is fetched from the game's own item registry and handed over as a stack, going around the config rather
 * than through it.</p>
 *
 * <p>Every lookup is reflective and version-tolerant: the CraftBukkit package is read off the running server
 * rather than compiled in, because it carries the Minecraft version in its name and hard-coding
 * {@code v1_20_R1} would break the plugin on the next update for no reason.</p>
 */
public final class CoinItems {

    /** FantasticCurrency's mod id, and the namespace its items live in. */
    private static final String NAMESPACE = "athens_coins";

    private final Logger logger;

    private Method registryGet;
    private Object itemRegistry;
    private Method resourceLocation;
    private Method asBukkitCopy;
    private Class<?> nmsItemStack;
    private Class<?> nmsItem;
    private String unavailableReason;

    public CoinItems(Logger logger) {
        this.logger = logger;
    }

    /** Resolves everything needed to turn an item id into a Bukkit stack. */
    public boolean connect() {
        try {
            nmsItem = Class.forName("net.minecraft.world.item.Item");
            nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");

            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
            resourceLocation = resourceLocationClass.getMethod("tryParse", String.class);

            // BuiltInRegistries is vanilla's own registry holder in 1.20.1, and it holds modded entries
            // too - Forge registers into it. Going through vanilla avoids depending on Forge's own
            // registry class, which has moved between versions more than once.
            Class<?> builtIn = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            itemRegistry = builtIn.getField("ITEM").get(null);
            registryGet = findRegistryGet(itemRegistry.getClass(), resourceLocationClass);
            if (registryGet == null) {
                unavailableReason = "No encontré cómo consultar el registro de items del juego.";
                return false;
            }

            Class<?> craftItemStack = Class.forName(craftBukkitPackage() + ".inventory.CraftItemStack");
            asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsItemStack);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            unavailableReason = "No pude acceder a los items del juego desde Bukkit: " + failure;
            return false;
        }
    }

    public boolean isConnected() {
        return asBukkitCopy != null && itemRegistry != null;
    }

    public String unavailableReason() {
        return unavailableReason == null ? "" : unavailableReason;
    }

    /**
     * One of FantasticCurrency's coins as a Bukkit stack, or null when the item is not registered.
     *
     * <p>A null here is the honest answer for a server where FantasticCurrency is absent, and it is also what
     * happens if the mod ever renames an item - which is why the caller reports it rather than substituting
     * something.</p>
     */
    public ItemStack coin(String itemId, int amount) {
        if (!isConnected()) {
            return null;
        }
        try {
            Object location = resourceLocation.invoke(null, NAMESPACE + ":" + itemId);
            if (location == null) {
                return null;
            }
            Object item = registryGet.invoke(itemRegistry, location);
            if (item == null || !nmsItem.isInstance(item)) {
                return null;
            }
            // Air is what the registry returns for anything it does not know, so an unregistered coin
            // arrives here as a perfectly valid empty item rather than as null.
            if (isAir(item)) {
                return null;
            }
            Object stack = nmsItemStack
                    .getConstructor(Class.forName("net.minecraft.world.level.ItemLike"), int.class)
                    .newInstance(item, Math.max(1, amount));
            Object bukkit = asBukkitCopy.invoke(null, stack);
            return bukkit instanceof ItemStack itemStack ? itemStack : null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logger.warning("No pude construir la moneda '" + itemId + "': " + failure);
            return null;
        }
    }

    private boolean isAir(Object item) {
        try {
            Object air = Class.forName("net.minecraft.world.item.Items").getField("AIR").get(null);
            return item == air;
        } catch (ReflectiveOperationException | RuntimeException cannotTell) {
            return false;
        }
    }

    /**
     * The registry's lookup method, whose name is obfuscated on a production server.
     *
     * <p>Searched for by shape - one {@code ResourceLocation} argument, returning an object - instead of by
     * name, so it is found whether the server runs Mojang names, SRG names or something remapped in between.
     * Naming it would tie this to one server flavour.</p>
     */
    private Method findRegistryGet(Class<?> registryClass, Class<?> resourceLocationClass) {
        for (Method method : registryClass.getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].equals(resourceLocationClass)) {
                continue;
            }
            if (method.getReturnType().equals(void.class) || method.getReturnType().isPrimitive()) {
                continue;
            }
            // get(ResourceLocation) returns the value; several others take a ResourceLocation and return
            // an Optional or a Holder, so prefer the one whose return type an Item can actually be.
            if (method.getReturnType().isAssignableFrom(nmsItem)
                    || method.getReturnType().equals(Object.class)) {
                return method;
            }
        }
        return null;
    }

    /** The versioned CraftBukkit package of the running server, e.g. org.bukkit.craftbukkit.v1_20_R1. */
    private String craftBukkitPackage() {
        return Bukkit.getServer().getClass().getPackage().getName();
    }
}
