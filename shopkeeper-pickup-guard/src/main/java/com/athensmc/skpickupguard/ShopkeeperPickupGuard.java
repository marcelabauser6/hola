package com.athensmc.skpickupguard;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;

/**
 * Keeps Easy Villagers from picking up Shopkeepers shops.
 *
 * <p>Server-side and single-purpose. The work is one mixin on Easy Villagers' {@code pickUp}, which is where
 * both of its pickup routes converge, refusing only entities Shopkeepers owns.</p>
 */
@Mod(ShopkeeperPickupGuard.MOD_ID)
public final class ShopkeeperPickupGuard {

    public static final String MOD_ID = "skpickupguard";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShopkeeperPickupGuard() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    /**
     * Says at startup whether the guard can actually see Shopkeepers.
     *
     * <p>Worth a line in the log: if Shopkeepers cannot be reached the guard allows every pickup, which is the
     * right default but looks identical to the mod not working.</p>
     */
    private void setup(FMLCommonSetupEvent event) {
        if (ShopkeeperCheck.isAvailable()) {
            LOGGER.info("Shopkeepers localizado: sus tiendas no se podrán recoger con Easy Villagers.");
        } else {
            LOGGER.warn("No encuentro Shopkeepers, así que no protejo nada y Easy Villagers "
                    + "sigue igual. Esto es lo esperado en un servidor sin ese plugin.");
        }
    }
}
