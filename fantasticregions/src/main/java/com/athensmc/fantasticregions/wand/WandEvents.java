package com.athensmc.fantasticregions.wand;

import com.athensmc.fantasticregions.FantasticRegions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where the wand hooks into the game.
 *
 * <p>Two hooks, and no more. There is no interaction hook: YAWP's own mixin gates on the marker data
 * rather than on the item holding it, so it registers the wand's clicks itself. Adding one here would
 * have marked every corner twice.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticRegions.MOD_ID)
public final class WandEvents {

    private WandEvents() {
    }

    /**
     * Hangs {@code /yawp wand} off YAWP's tree once YAWP has built it.
     *
     * <p>Low priority so it runs after the default-priority registration YAWP uses. The mod dependency
     * is declared as {@code AFTER} as well, which orders the mod loading, but not the listeners on a
     * shared event - the priority is what actually guarantees the node exists to attach to.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        WandCommands.register(event.getDispatcher());
    }

    /**
     * Redraws the outline for whoever is holding a wand.
     *
     * <p>On the player tick rather than the level tick, so the cost is per holder and a server with no
     * wand out does no work beyond the phase check. The counter is per player for the same reason a
     * global one would be wrong: two administrators marking at once would otherwise share a refresh
     * slot and each see half the frames.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % WandOutline.REFRESH_TICKS != 0) {
            return;
        }
        WandOutline.tick(player);
    }
}
