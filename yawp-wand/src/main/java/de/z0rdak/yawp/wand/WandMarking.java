package de.z0rdak.yawp.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /**
     * The last click handled per player, to swallow a repeat of the same one.
     *
     * <p>The click arrives by one of two routes - YAWP's marking handler when its interaction mixin is
     * applied, and Forge's interact event when it is not - and in practice only one of them can happen for
     * any given click, because the first swallows the method the second is fired from. This is here because
     * "in practice" is not "always": a future YAWP could move its injection and both would fire, and the
     * result would be every corner counted twice. Cheaper to make that impossible than to find out.</p>
     */
    private static final Map<UUID, long[]> LAST_CLICK = new HashMap<>();

    /** Ticks within which the same player clicking the same block is treated as one click. */
    private static final long DEDUP_TICKS = 4L;

    private WandMarking() {
    }

    /** True when this exact click was already handled a moment ago and should be ignored. */
    private static boolean isRepeat(ServerPlayer player, BlockPos clicked) {
        long packed = clicked.asLong();
        long now = player.level().getGameTime();
        long[] last = LAST_CLICK.get(player.getUUID());
        if (last != null && last[0] == packed && now - last[1] < DEDUP_TICKS) {
            return true;
        }
        LAST_CLICK.put(player.getUUID(), new long[]{packed, now});
        return false;
    }

    /** Forgets a player's last click, so their next one always counts. */
    public static void forget(ServerPlayer player) {
        LAST_CLICK.remove(player.getUUID());
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
        if (isRepeat(player, clicked)) {
            // Already handled by the other route a moment ago. Consumed so the caller still cancels.
            return true;
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
