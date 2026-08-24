package com.athensmc.yawpwand.wand;

import com.athensmc.yawpwand.YawpWand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the wand command, by two routes on purpose.
 *
 * <p><strong>{@code /yawp wand <forma>}</strong> is the one to use. It is added to YAWP's own command
 * tree, alongside {@code marker} and {@code region}, so it reads as part of the mod it extends. Nothing
 * of YAWP's is removed or shadowed.</p>
 *
 * <p><strong>{@code /yawpwand <forma>}</strong> is a standalone command that does the same thing. It
 * exists because the first route is not fully in this mod's hands: it works by adding a child to a node
 * another mod built, which assumes that node exists by the time this runs and that nothing rebuilds the
 * tree afterwards. Both assumptions normally hold, and neither is guaranteed - hybrid servers such as
 * Mohist wrap the command map for Bukkit, and load order between two mods listening to the same event
 * is a matter of priority rather than a promise. A wand that cannot be asked for is useless, so there
 * is a route that depends on nothing but this mod.</p>
 */
public final class WandCommands {

    /** YAWP's command roots, in the order they are tried. */
    private static final String[] HOST_COMMANDS = {"yawp", "wp"};

    /** The standalone fallback, which needs no other mod's tree. */
    private static final String STANDALONE_COMMAND = "yawpwand";

    /** Operator level, matching what YAWP requires for its own marker command. */
    private static final int PERMISSION_LEVEL = 2;

    private WandCommands() {
    }

    /**
     * Puts the command in the dispatcher, and says where it ended up.
     *
     * <p>Safe to call more than once on the same dispatcher. Brigadier merges a literal that is already
     * present rather than duplicating it, which is what lets this be retried after the server has
     * started without building a second, divergent copy of the node.</p>
     *
     * @return the command paths that were registered, for logging.
     */
    public static List<String> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        List<String> registered = new ArrayList<>(2);

        for (String host : HOST_COMMANDS) {
            CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(host);
            if (node == null) {
                continue;
            }
            if (node.getRedirect() != null) {
                // An alias that redirects into the real tree already reaches the node being added to
                // that tree. Adding it here as well would build a second copy that can drift.
                continue;
            }
            node.addChild(build().build());
            registered.add("/" + host + " wand");
        }

        // Always registered, not only as a rescue when the above finds nothing. If the host tree is
        // rebuilt later by something else, this is the path that still answers.
        dispatcher.register(Commands.literal(STANDALONE_COMMAND)
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(WandCommands::explainUsage)
                .then(shapeArgument()));
        registered.add("/" + STANDALONE_COMMAND);

        return registered;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("wand")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(WandCommands::explainUsage)
                .then(shapeArgument());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            shapeArgument() {
        return Commands.argument("forma", StringArgumentType.word())
                .suggests((context, builder) ->
                        SharedSuggestionProvider.suggest(WandShape.ids(), builder))
                .executes(WandCommands::giveWand);
    }

    /** With no shape given, list them rather than failing with Brigadier's parse error. */
    private static int explainUsage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Elige la forma de la zona:")
                .withStyle(ChatFormatting.GOLD), false);
        for (WandShape shape : WandShape.values()) {
            source.sendSuccess(() -> Component.literal("  /yawp wand " + shape.id())
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("  " + shape.help())
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return WandShape.values().length;
    }

    private static int giveWand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "La vara se entrega en mano, así que este comando lo tiene que usar un jugador."));
            return 0;
        }

        String requested = StringArgumentType.getString(context, "forma");
        WandShape shape = WandShape.fromId(requested);
        if (shape == null) {
            source.sendFailure(Component.literal("No conozco la forma '" + requested
                    + "'. Las que hay son: " + String.join(", ", WandShape.ids()) + "."));
            return 0;
        }

        ItemStack wand = RegionWand.create(shape, player.level().dimension());
        if (!player.getInventory().add(wand)) {
            // Dropped rather than deleted. The rod is cheap, but a player whose inventory was full
            // would otherwise run the command, see a success message and have nothing in hand.
            player.drop(wand, false);
            source.sendSuccess(() -> Component.literal(
                            "Tenías el inventario lleno, así que la vara está en el suelo.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        int needed = neededFor(wand);
        source.sendSuccess(() -> Component.literal("Vara de zona lista: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(shape.label()).withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal(pointsInstruction(needed))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                        "Luego crea la zona con el comando de crear de YAWP y la forma "
                                + shape.id() + ", que recogerá lo preseleccionado.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int neededFor(ItemStack wand) {
        var marker = RegionWand.markerOf(wand);
        return marker == null ? 0 : RegionWand.neededBlocks(marker);
    }

    /**
     * What to click, phrased for the shape's own point count.
     *
     * <p>The count comes from YAWP's {@code AreaType}, so a polygon says what YAWP will accept rather
     * than what this mod assumed.</p>
     */
    private static String pointsInstruction(int needed) {
        if (needed <= 0) {
            return "Haz clic derecho en los bloques para marcar la zona.";
        }
        if (needed == 1) {
            return "Haz clic derecho en un bloque para marcar la zona.";
        }
        return "Haz clic derecho en " + needed
                + " bloques para marcar la zona. El contorno se dibuja mientras la llevas en la mano.";
    }

    /** Logs what was registered, so a server log answers "why can I not use the command". */
    public static void reportRegistration(List<String> paths, String when) {
        if (paths.isEmpty()) {
            YawpWand.LOGGER.error("YAWP Wand registered no commands at {}. The wand is unavailable.",
                    when);
            return;
        }
        YawpWand.LOGGER.info("YAWP Wand available as: {} (registered at {})",
                String.join(", ", paths), when);
        if (paths.stream().noneMatch(path -> path.startsWith("/yawp "))) {
            YawpWand.LOGGER.warn("Could not attach to YAWP's command tree, so /yawp wand is absent. "
                    + "Use /{} instead.", STANDALONE_COMMAND);
        }
    }
}
