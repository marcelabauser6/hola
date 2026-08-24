package de.z0rdak.yawp.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the corners clicked with the wand.
 *
 * <p>This mod does its own marking rather than leaving it to YAWP's interaction mixin. The mixin does the
 * job when it is there, but it only exists if YAWP loaded and its mixins applied - and on a hybrid server
 * that is not a safe bet. Marking here means the rod works on its own.</p>
 *
 * <p>Doing it here does raise the possibility of a corner being counted twice, once by each handler. That
 * is why {@link MarkerData#write} always rewrites the whole corner list from this mod's own view of it
 * instead of appending: whoever else writes to the tag, the next click puts it right. Repeated clicks on
 * the same block are ignored for the same reason.</p>
 */
public final class WandMarking {

    private WandMarking() {
    }

    /**
     * Handles a right-click on a block with the wand in hand.
     *
     * @return true when the click was consumed as a mark, so the caller can cancel the interaction.
     */
    public static boolean onRightClick(ServerPlayer player, ItemStack wand, BlockPos clicked,
                                       boolean undoing) {
        WandShape shape = MarkerData.shapeOf(wand);
        if (shape == null) {
            // A rod whose tag names a shape this mod does not know. Left alone rather than reset, in
            // case a newer YAWP wrote it and knows better.
            return false;
        }

        List<BlockPos> before = MarkerData.corners(wand);
        List<BlockPos> after = undoing ? withoutLast(before)
                : MarkerData.withCorner(shape, before, clicked);

        if (after.equals(before)) {
            // Nothing changed: the same block clicked twice, or an undo with nothing to undo.
            if (undoing) {
                say(player, "No queda ninguna esquina por deshacer.", ChatFormatting.GRAY);
            }
            return true;
        }

        apply(player, wand, shape, after);
        feedback(player, shape, after, undoing);
        return true;
    }

    /** Clears every corner, keeping the shape. */
    public static void reset(ServerPlayer player, ItemStack wand) {
        WandShape shape = MarkerData.shapeOf(wand);
        if (shape == null) {
            return;
        }
        apply(player, wand, shape, List.of());
        say(player, "Vara reiniciada. " + RegionWand.progress(shape, 0), ChatFormatting.YELLOW);
    }

    /** Drops the most recent corner. */
    public static void undo(ServerPlayer player, ItemStack wand) {
        WandShape shape = MarkerData.shapeOf(wand);
        if (shape == null) {
            return;
        }
        List<BlockPos> before = MarkerData.corners(wand);
        if (before.isEmpty()) {
            say(player, "No queda ninguna esquina por deshacer.", ChatFormatting.GRAY);
            return;
        }
        List<BlockPos> after = withoutLast(before);
        apply(player, wand, shape, after);
        feedback(player, shape, after, true);
    }

    private static void apply(ServerPlayer player, ItemStack wand, WandShape shape,
                              List<BlockPos> corners) {
        MarkerData.write(wand, shape, player.level().dimension(), corners);
        RegionWand.describe(wand, shape, corners);
    }

    private static void feedback(ServerPlayer player, WandShape shape, List<BlockPos> corners,
                                 boolean undoing) {
        boolean complete = MarkerData.isComplete(shape, corners.size());

        // A different note for a corner going in, coming out, and the shape being finished, so the state
        // is audible without reading anything.
        if (undoing) {
            playNote(player, 0.8F);
        } else if (complete) {
            playNote(player, 1.5F);
        } else {
            playNote(player, 1.2F);
        }

        say(player, RegionWand.progress(shape, corners.size()),
                complete ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    private static List<BlockPos> withoutLast(List<BlockPos> corners) {
        if (corners.isEmpty()) {
            return corners;
        }
        List<BlockPos> shorter = new ArrayList<>(corners);
        shorter.remove(shorter.size() - 1);
        return shorter;
    }

    private static void playNote(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.PLAYERS, 0.6F, pitch);
    }

    private static void say(ServerPlayer player, String text, ChatFormatting colour) {
        player.displayClientMessage(Component.literal(text).withStyle(colour), true);
    }
}
