/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.Item
 *  net.minecraftforge.eventbus.api.IEventBus
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package com.claimblocks.item;

import com.claimblocks.data.ClaimTier;
import com.claimblocks.item.ProtectionItem;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ClaimItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ITEMS, (String)"claimblocks");
    private static final Map<String, RegistryObject<Item>> BY_TIER = new HashMap<String, RegistryObject<Item>>();

    private ClaimItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static Item itemFor(ClaimTier tier) {
        if (tier == null) {
            return null;
        }
        RegistryObject<Item> ro = BY_TIER.get(tier.id);
        return ro != null && ro.isPresent() ? (Item)ro.get() : null;
    }

    public static String registryName(ClaimTier tier) {
        return tier == null ? "" : "claimblocks:proteccion_" + tier.label().toLowerCase(Locale.ROOT);
    }

    static {
        for (ClaimTier tier : ClaimTier.VALUES) {
            String name = "proteccion_" + tier.label().toLowerCase(Locale.ROOT);
            BY_TIER.put(tier.id, (RegistryObject<Item>)ITEMS.register(name, () -> (Item)new ProtectionItem(tier)));
        }
    }
}

