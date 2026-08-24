package de.z0rdak.yawp.wand;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;

/**
 * The single entry point the wand is installed through.
 *
 * <p>Called from {@code CommandRegistryMixin} at the end of YAWP's own command registration, which is what
 * makes {@code wand} a subcommand of {@code /yawp} rather than a command of its own belonging to a second
 * mod. By that point YAWP has registered its {@code yawp} literal, so the node is there to hang the
 * subcommand on.</p>
 *
 * <p>The event listeners are registered here too, on first call. There is no {@code @Mod} class to do it
 * from: this is part of YAWP, not a mod beside it, so there is no second mod entry and no second entry
 * point. Guarding on a flag matters because command registration runs again on every {@code /reload}, and
 * registering the listeners a second time would mark every corner twice.</p>
 */
public final class WandHook {

    private static boolean listenersRegistered;

    private WandHook() {
    }

    /**
     * Adds {@code /yawp wand} and starts the wand listening, if it is not already.
     *
     * <p>Failures are caught and logged rather than allowed out. This runs inside YAWP's command
     * registration, and an exception escaping here would take YAWP's whole command tree with it - trading
     * a missing wand for a mod that has no commands at all.</p>
     */
    public static void install(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            CommandNode<CommandSourceStack> host = dispatcher.getRoot().getChild("yawp");
            if (host == null) {
                WandLog.LOGGER.error("No 'yawp' command node to add the wand to; wand unavailable.");
                return;
            }
            host.addChild(WandCommands.node());
            WandLog.LOGGER.info("Wand subcommand registered: /yawp wand");

            if (!listenersRegistered) {
                MinecraftForge.EVENT_BUS.register(WandEvents.class);
                listenersRegistered = true;
            }
        } catch (Throwable failure) {
            WandLog.LOGGER.error("Could not install the wand subcommand", failure);
        }
    }
}
