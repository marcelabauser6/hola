package com.athensmc.yawpwand.wand;

import com.athensmc.yawpwand.YawpWand;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Where the wand hooks into the game.
 *
 * <p>There is no interaction hook. YAWP's own mixin gates on the marker data rather than on the item
 * holding it, so it registers the wand's clicks itself; adding a handler here would have marked every
 * corner twice.</p>
 */
@Mod.EventBusSubscriber(modid = YawpWand.MOD_ID)
public final class WandEvents {

    private WandEvents() {
    }

    /**
     * Registers the command once the tree is being built.
     *
     * <p>Lowest priority, so this runs after every other mod's registration on the same event. YAWP
     * registers here too, and {@code /yawp wand} is added as a child of the node YAWP creates - so the
     * order is not a preference, it is the difference between finding that node and not.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        List<String> paths = WandCommands.register(event.getDispatcher());
        WandCommands.reportRegistration(paths, "command registration");
    }

    /**
     * Checks the command really is there once the server is up, and puts it back if not.
     *
     * <p>Registration during the event assumes nothing rebuilds or replaces the tree afterwards. On a
     * plain Forge server nothing does. On a hybrid such as Mohist, the command map is wrapped for
     * Bukkit's benefit, and a node grafted onto another mod's literal is exactly the kind of thing that
     * does not survive being copied. Re-registering is cheap and idempotent - Brigadier merges a literal
     * that is already present instead of duplicating it - so it is checked rather than assumed.</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        var dispatcher = event.getServer().getCommands().getDispatcher();
        boolean present = dispatcher.getRoot().getChild("yawpwand") != null;
        var yawp = dispatcher.getRoot().getChild("yawp");
        boolean attached = yawp != null && yawp.getChild("wand") != null;

        if (present && attached) {
            YawpWand.LOGGER.info("YAWP Wand ready: /yawp wand and /yawpwand are both in the tree.");
            return;
        }

        YawpWand.LOGGER.warn("YAWP Wand was missing from the command tree after startup "
                + "(/yawp wand: {}, /yawpwand: {}). Registering again.", attached, present);
        List<String> paths = WandCommands.register(dispatcher);
        WandCommands.reportRegistration(paths, "server start");

        // Anyone already connected holds a stale copy of the tree and would not see the new command.
        event.getServer().getPlayerList().getPlayers()
                .forEach(player -> event.getServer().getCommands().sendCommands(player));
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
