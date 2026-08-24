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
 * mod. By that point YAWP has registered its {@code yawp} literal, so the node is there to hang it on.</p>
 *
 * <p>The two halves - the command and the listeners - are installed independently, each with its own
 * guard. They used to share one, and that was a mistake worth not repeating: a failure while adding the
 * command left the listeners unregistered too, so the tool would have been unreachable <em>and</em> inert,
 * from one fault. Either half can now fail on its own, and the log says which.</p>
 */
public final class WandHook {

    private static boolean listenersRegistered;
    private static boolean commandRegistered;
    private static int completions;

    private WandHook() {
    }

    /** Adds {@code /yawp wand} and starts the wand listening. Safe to call again on {@code /reload}. */
    public static void install(CommandDispatcher<CommandSourceStack> dispatcher) {
        installListeners();
        installCommand(dispatcher);
    }

    /**
     * Registers the game hooks, once.
     *
     * <p>Guarded on a flag because command registration runs again on every {@code /reload}, and a second
     * registration of the same listeners would mark every corner twice.</p>
     */
    private static void installListeners() {
        if (listenersRegistered) {
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.register(WandEvents.class);
            listenersRegistered = true;
            WandLog.LOGGER.info("Wand listeners registered.");
        } catch (Throwable failure) {
            WandLog.LOGGER.error("Could not register the wand's listeners. The outline will not draw "
                    + "and marking will only work through YAWP's own marker handler.", failure);
        }
    }

    /**
     * Adds the subcommand to YAWP's tree.
     *
     * <p>Failures are caught rather than allowed out. This runs inside YAWP's command registration, and an
     * exception escaping would take YAWP's whole command tree with it - trading a missing wand for a mod
     * with no commands at all.</p>
     */
    private static void installCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            CommandNode<CommandSourceStack> host = dispatcher.getRoot().getChild("yawp");
            if (host == null) {
                WandLog.LOGGER.error("No 'yawp' command node to add the wand to; wand unavailable.");
                return;
            }
            host.addChild(WandCommands.node());
            commandRegistered = true;
            WandLog.LOGGER.info("Wand subcommand registered: /yawp wand");

            // Feed the rod's corners into YAWP's own create command, so its position arguments
            // complete to what was marked instead of to `~ ~ ~`.
            completions = WandSuggestions.install(host);
            WandLog.LOGGER.info("Wand completion added to {} coordinate argument(s) of /yawp.",
                    completions);
        } catch (Throwable failure) {
            WandLog.LOGGER.error("Could not install the wand subcommand", failure);
        }
    }

    /** For {@code /yawp wand diag}: whether the game hooks are live. */
    public static boolean listenersRegistered() {
        return listenersRegistered;
    }

    /** For {@code /yawp wand diag}: whether the subcommand made it into the tree. */
    public static boolean commandRegistered() {
        return commandRegistered;
    }

    /** For {@code /yawp wand diag}: how many of YAWP's coordinate arguments complete from the rod. */
    public static int completions() {
        return completions;
    }
}
