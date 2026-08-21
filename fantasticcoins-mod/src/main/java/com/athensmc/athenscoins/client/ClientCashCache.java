package com.athensmc.athenscoins.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Last known Fantastic Cash balance of the local player.
 *
 * <p>Accounts live only on the server, but GUIs owned by other mods (FantasticShop's wallet strip,
 * for example) need to show the player's balance while rendering on the client. The server pushes
 * the balance whenever it changes and this holds onto it.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCashCache {

    private static long cents;

    private ClientCashCache() {
    }

    public static void set(long value) {
        cents = Math.max(0L, value);
    }

    public static long get() {
        return cents;
    }

    public static void clear() {
        cents = 0L;
    }
}
