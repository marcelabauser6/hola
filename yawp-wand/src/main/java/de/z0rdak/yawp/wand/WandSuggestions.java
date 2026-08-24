package de.z0rdak.yawp.wand;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Feeds the wand's marked corners into YAWP's own create command as tab completions.
 *
 * <p>YAWP's create takes explicit coordinates - {@code /yawp create <nombre> cuboid <pos1> <pos2>} - and
 * offers {@code ~ ~ ~} for each, which is the block you are standing on and almost never the corner you
 * marked. Having marked the region with the rod, you then had to read the numbers off it and type them
 * back in. This replaces those suggestions with the corners actually held on the rod, so completing the
 * argument fills in the corner it belongs to.</p>
 *
 * <p>Which corner goes with which argument is decided by the argument's own name, taken from YAWP's
 * {@code CommandConstants}: {@code pos1} and {@code center-pos} are the first marked block, {@code pos2}
 * and {@code radius-pos} the second, and {@code radius} gets the distance between them.</p>
 *
 * <p>The original suggestions are kept and offered after ours, not replaced. Without a wand in hand the
 * command behaves exactly as it did, which matters because these are YAWP's arguments and this must not
 * take anything away from someone who is not using the rod.</p>
 */
public final class WandSuggestions {

    /** Argument names that want the first marked corner. */
    private static final Set<String> FIRST_CORNER = Set.of("pos1", "center-pos");

    /** Argument names that want the second. */
    private static final Set<String> SECOND_CORNER = Set.of("pos2", "radius-pos");

    /** The argument that wants a distance rather than a position. */
    private static final String RADIUS = "radius";

    /** Guards against walking a cycle in the command graph, which redirects make possible. */
    private static final int MAX_DEPTH = 12;

    private WandSuggestions() {
    }

    /**
     * Rewrites the suggestion providers under a command node.
     *
     * @return how many arguments were given wand-aware completion, for the log.
     */
    public static int install(CommandNode<CommandSourceStack> root) {
        Map<String, Integer> counts = new HashMap<>();
        decorate(root, new HashSet<>(), 0, counts);
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void decorate(CommandNode<CommandSourceStack> node,
                                 Set<CommandNode<CommandSourceStack>> visited, int depth,
                                 Map<String, Integer> counts) {
        if (depth > MAX_DEPTH || !visited.add(node)) {
            return;
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (child instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
                String name = argument.getName();
                if (FIRST_CORNER.contains(name) || SECOND_CORNER.contains(name)
                        || RADIUS.equals(name)) {
                    if (replaceProvider(argument, name)) {
                        counts.merge(name, 1, Integer::sum);
                    }
                }
            }
            decorate(child, visited, depth + 1, counts);
        }
    }

    /**
     * Swaps in a wrapping suggestion provider.
     *
     * <p>By reflection, because Brigadier holds the provider in a private final field and gives no way to
     * change it after the node is built - and the node here is one YAWP built. Rebuilding YAWP's create
     * command to get at it would mean reimplementing the command, which is a great deal more to get wrong
     * than one field write.</p>
     *
     * <p>Failures are swallowed and reported false: a command that still suggests {@code ~ ~ ~} is a minor
     * inconvenience, and it is not worth risking the command tree over.</p>
     */
    private static boolean replaceProvider(ArgumentCommandNode<CommandSourceStack, ?> argument,
                                           String name) {
        try {
            Field field = ArgumentCommandNode.class.getDeclaredField("customSuggestions");
            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            SuggestionProvider<CommandSourceStack> original =
                    (SuggestionProvider<CommandSourceStack>) field.get(argument);
            if (original instanceof WandAware) {
                // Already done. Command registration runs again on /reload, and wrapping the wrapper
                // would stack a new layer every time.
                return false;
            }
            field.set(argument, wrap(name, original, argument));
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            WandLog.LOGGER.debug("Could not add wand completion to '{}': {}", name,
                    failure.toString());
            return false;
        }
    }

    /** Marker interface, so a second pass can tell its own work from YAWP's. */
    private interface WandAware extends SuggestionProvider<CommandSourceStack> {
    }

    /** Offers the wand's corner first, then whatever the argument suggested before. */
    private record WandProvider(String argumentName,
                                SuggestionProvider<CommandSourceStack> original,
                                ArgumentCommandNode<CommandSourceStack, ?> argument)
            implements WandAware {

        @Override
        public CompletableFuture<Suggestions> getSuggestions(
                CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            for (String suggestion : wandSuggestions(context)) {
                builder.suggest(suggestion, Component.literal("de la vara"));
            }
            return fallback(context, builder);
        }

        private List<String> wandSuggestions(CommandContext<CommandSourceStack> context) {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) {
                return List.of();
            }
            ItemStack wand = heldWand(player);
            if (wand == null) {
                return List.of();
            }
            List<BlockPos> corners = MarkerData.corners(wand);
            if (corners.isEmpty()) {
                return List.of();
            }

            if (RADIUS.equals(argumentName)) {
                if (corners.size() < 2) {
                    return List.of();
                }
                WandShape shape = MarkerData.shapeOf(wand);
                boolean spherical = shape != WandShape.CYLINDER;
                int radius = Outlines.radius(coords(corners.get(0)), coords(corners.get(1)),
                        spherical);
                return radius > 0 ? List.of(String.valueOf(radius)) : List.of();
            }

            int index = FIRST_CORNER.contains(argumentName) ? 0 : 1;
            if (index >= corners.size()) {
                return List.of();
            }
            BlockPos corner = corners.get(index);
            List<String> suggestions = new ArrayList<>(1);
            suggestions.add(corner.getX() + " " + corner.getY() + " " + corner.getZ());
            return suggestions;
        }

        /**
         * Whatever this argument suggested before us.
         *
         * <p>When YAWP set no provider of its own, Brigadier would have asked the argument type - which for
         * a position is what produces {@code ~ ~ ~} - so that is asked here instead. Losing it would make
         * the command harder to use without a wand than it was before.</p>
         */
        private CompletableFuture<Suggestions> fallback(
                CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
            try {
                if (original != null) {
                    return original.getSuggestions(context, builder);
                }
                return argument.getType().listSuggestions(context, builder);
            } catch (Exception failure) {
                return builder.buildFuture();
            }
        }
    }

    /** Small factory so the record stays private to this class. */
    private static WandAware wrap(String name, SuggestionProvider<CommandSourceStack> original,
                                  ArgumentCommandNode<CommandSourceStack, ?> argument) {
        return new WandProvider(name, original, argument);
    }

    private static ItemStack heldWand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (RegionWand.isWand(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static int[] coords(BlockPos pos) {
        return new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }
}
