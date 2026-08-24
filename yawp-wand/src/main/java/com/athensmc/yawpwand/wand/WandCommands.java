package com.athensmc.yawpwand.wand;

import com.athensmc.yawpwand.YawpWand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Adds {@code /yawp wand <forma>} to YAWP's own command tree.
 *
 * <p>Added, not replaced. Nothing of YAWP's is removed or shadowed: the node is hung off the existing
 * {@code yawp} literal alongside {@code marker}, {@code region} and the rest, so every command that
 * worked before still works and the wand is simply one more of them. That is why the region is still
 * created with YAWP's own create command - this mod's job ends when the corners are in the rod.</p>
 */
public final class WandCommands {

    /** YAWP's command roots. Its alias is registered separately, hence both. */
    private static final String[] HOST_COMMANDS = {"yawp", "wp"};

    /** Operator level, matching what YAWP requires for its own marker command. */
    private static final int PERMISSION_LEVEL = 2;

    private WandCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> wand = build().build();

        boolean attached = false;
        for (String host : HOST_COMMANDS) {
            CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(host);
            if (node == null) {
                continue;
            }
            if (node.getRedirect() != null) {
                // An alias that redirects to the real tree already reaches the node we are about to
                // add to that tree. Adding it here as well would build a second, divergent copy.
                continue;
            }
            node.addChild(wand);
            attached = true;
        }

        if (!attached) {
            // YAWP is a mandatory dependency, so this should be unreachable. Logged as an error rather
            // than ignored because the symptom otherwise is a command that is merely absent, which
            // looks like the mod failing to load at all.
            YawpWand.LOGGER.error(
                    "No YAWP command tree to attach /yawp wand to. The wand will be unavailable.");
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("wand")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(WandCommands::explainUsage)
                .then(Commands.argument("forma", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(WandShape.ids(), builder))
                        .executes(WandCommands::giveWand));
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
     * <p>The count comes from YAWP's {@code AreaType}, so a polygon says "al menos 3" because that is
     * what YAWP will accept, not because this mod decided so.</p>
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
}
