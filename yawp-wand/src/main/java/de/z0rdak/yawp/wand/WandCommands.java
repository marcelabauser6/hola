package de.z0rdak.yawp.wand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;

import de.z0rdak.yawp.api.core.RegionManager;
import de.z0rdak.yawp.commands.WandRegionCreator;
import de.z0rdak.yawp.core.region.IProtectedRegion;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The {@code wand} subcommand of {@code /yawp}.
 *
 * <pre>
 * /yawp wand &lt;forma&gt;   hand over a rod set to that shape
 * /yawp wand info      what the held rod has marked so far
 * /yawp wand deshacer  drop the last corner
 * /yawp wand reiniciar clear every corner, keeping the shape
 * </pre>
 *
 * <p>Operator level 2, the same bar YAWP puts on its own marker command.</p>
 */
public final class WandCommands {

    private static final int PERMISSION_LEVEL = 2;

    private WandCommands() {
    }

    /** The node to hang off YAWP's {@code yawp} literal. */
    public static LiteralCommandNode<CommandSourceStack> node() {
        return build().build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("wand")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(WandCommands::listShapes)
                .then(Commands.literal("info").executes(WandCommands::showState))
                .then(Commands.literal("diag").executes(WandCommands::diagnose))
                .then(Commands.literal("crear")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .executes(WandCommands::createFromWand)))
                .then(Commands.literal("deshacer").executes(WandCommands::undo))
                .then(Commands.literal("reiniciar").executes(WandCommands::reset))
                .then(Commands.argument("forma", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(WandShape.ids(), builder))
                        .executes(WandCommands::giveWand));
    }

    /** With no shape given, list them rather than failing with Brigadier's parse error. */
    private static int listShapes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Formas de zona:")
                .withStyle(ChatFormatting.GOLD), false);
        for (WandShape shape : WandShape.values()) {
            String points = shape.takesRange()
                    ? shape.neededBlocks() + "-" + shape.maxBlocks() + " esquinas"
                    : shape.neededBlocks() + " esquinas";
            source.sendSuccess(() -> Component.literal("  /yawp wand " + shape.id())
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("  " + points).withStyle(ChatFormatting.DARK_AQUA))
                    .append(Component.literal("  " + shape.help()).withStyle(ChatFormatting.GRAY)),
                    false);
        }
        return WandShape.values().length;
    }

    private static int giveWand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "La vara se entrega en mano, así que este comando lo usa un jugador."));
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
            // Dropped rather than deleted: a full inventory should not mean a success message and
            // nothing in hand.
            player.drop(wand, false);
            source.sendSuccess(() -> Component.literal(
                            "Tenías el inventario lleno, la vara está en el suelo.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        source.sendSuccess(() -> Component.literal("Vara de zona: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(shape.label()).withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal(RegionWand.progress(shape, 0))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(
                        "El contorno se dibuja mientras la llevas. Agachado y clic derecho deshace.")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int showState(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        ItemStack wand = heldWand(player);
        if (wand == null) {
            source.sendFailure(noWandHeld());
            return 0;
        }

        WandShape shape = MarkerData.shapeOf(wand);
        List<BlockPos> corners = MarkerData.corners(wand);
        source.sendSuccess(() -> Component.literal("Forma: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(shape.label()).withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal(RegionWand.progress(shape, corners.size()))
                .withStyle(MarkerData.isComplete(shape, corners.size())
                        ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        if (!corners.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                            Outlines.measure(shape, RegionWand.asCoordinates(corners)))
                    .withStyle(ChatFormatting.AQUA), false);
            for (int i = 0; i < corners.size(); i++) {
                BlockPos corner = corners.get(i);
                int number = i + 1;
                source.sendSuccess(() -> Component.literal("  " + number + ": "
                                + corner.getX() + ", " + corner.getY() + ", " + corner.getZ())
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return 1;
    }

    /**
     * Creates the region straight from the rod, with no coordinates typed at all.
     *
     * <p>Calls YAWP's own {@code DimensionCommands} rather than building the region here, so the name
     * checks, the parent linkage, the default flags and the saving are all done exactly as they are for a
     * region made by hand. This command supplies the two numbers and nothing else.</p>
     *
     * <p>Cuboid and sphere only, because those are the two whose creation YAWP exposes publicly. For the
     * other three the completion on {@code /yawp create} is the route, and the message says so rather than
     * failing silently.</p>
     */
    private static int createFromWand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        ItemStack wand = heldWand(player);
        if (wand == null) {
            source.sendFailure(noWandHeld());
            return 0;
        }

        WandShape shape = MarkerData.shapeOf(wand);
        List<BlockPos> corners = MarkerData.corners(wand);
        if (shape == null) {
            source.sendFailure(Component.literal("Esa vara no tiene una forma que reconozca."));
            return 0;
        }
        if (!MarkerData.isComplete(shape, corners.size())) {
            source.sendFailure(Component.literal("Falta marcar la zona: "
                    + RegionWand.progress(shape, corners.size())));
            return 0;
        }

        String name = StringArgumentType.getString(context, "nombre");
        IProtectedRegion parent = RegionManager.get()
                .getDimensionalRegion(player.level().dimension()).orElse(null);
        if (parent == null) {
            source.sendFailure(Component.literal(
                    "YAWP no tiene datos de esta dimensión, así que no puedo crear la zona aquí."));
            return 0;
        }

        return switch (shape) {
            case CUBOID -> WandRegionCreator.cuboid(context, name,
                    corners.get(0), corners.get(1), parent);
            case SPHERE -> WandRegionCreator.sphere(context, name, corners.get(0),
                    Outlines.radius(coords(corners.get(0)), coords(corners.get(1)), true), parent);
            case CYLINDER, POLYGON, PRISM -> {
                source.sendFailure(Component.literal("YAWP solo deja crear cuboide y esfera por API. "
                        + "Para " + shape.label().toLowerCase(java.util.Locale.ROOT)
                        + " usa /yawp create y pulsa tabulador en las coordenadas."));
                yield 0;
            }
        };
    }

    private static int[] coords(BlockPos pos) {
        return new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    /**
     * Reports what is actually hooked up, in game.
     *
     * <p>Here because the failure that mattered most was invisible from both sides: the rod was being
     * marked by YAWP and nothing was reporting it, so the tool looked dead while working. This says which
     * half is live, whether the held item is recognised, and what its tag holds - which turns "it does
     * nothing" into a specific answer without needing the server log.</p>
     */
    private static int diagnose(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Diagnóstico de la vara")
                .withStyle(ChatFormatting.GOLD), false);
        report(source, "Subcomando registrado", WandHook.commandRegistered());
        report(source, "Escuchadores activos", WandHook.listenersRegistered());
        int completions = WandHook.completions();
        report(source, "Autocompletado en create (" + completions + ")", completions > 0);

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSuccess(() -> Component.literal("  Sin jugador: el resto se comprueba en mano.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        ItemStack held = player.getMainHandItem();
        report(source, "Vara en la mano", RegionWand.isWand(held));
        if (!RegionWand.isWand(held)) {
            source.sendSuccess(() -> Component.literal(
                            "  Consigue una con /yawp wand <forma> y repite este comando.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        WandShape shape = MarkerData.shapeOf(held);
        report(source, "Forma reconocida", shape != null);
        source.sendSuccess(() -> Component.literal("  Etiqueta type: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(shape == null ? "(desconocida)" : shape.yawpTypeName())
                        .withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("  Dimensión: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MarkerData.dimensionOf(held))
                        .withStyle(ChatFormatting.WHITE)), false);
        int marked = MarkerData.corners(held).size();
        source.sendSuccess(() -> Component.literal("  Esquinas marcadas: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(marked)).withStyle(ChatFormatting.WHITE)),
                false);
        return 1;
    }

    private static void report(CommandSourceStack source, String label, boolean ok) {
        source.sendSuccess(() -> Component.literal("  " + label + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ok ? "sí" : "NO")
                        .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
    }

    private static int undo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        ItemStack wand = heldWand(player);
        if (wand == null) {
            context.getSource().sendFailure(noWandHeld());
            return 0;
        }
        WandMarking.undo(player, wand);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        ItemStack wand = heldWand(player);
        if (wand == null) {
            context.getSource().sendFailure(noWandHeld());
            return 0;
        }
        WandMarking.reset(player, wand);
        return 1;
    }

    private static ItemStack heldWand(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (RegionWand.isWand(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static Component noWandHeld() {
        return Component.literal("Llevas ninguna vara de zona en la mano. "
                + "Consigue una con /yawp wand <forma>.");
    }
}
