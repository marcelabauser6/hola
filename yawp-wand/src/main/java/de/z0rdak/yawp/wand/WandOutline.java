package de.z0rdak.yawp.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the region being marked, in the world, while the wand is held.
 *
 * <p>Particles rather than a client-side renderer, and sent to the one player holding the rod. A renderer
 * would be crisper and follow the cursor at frame rate, but it would need this mod installed in every
 * administrator's client; particles are drawn by any vanilla client. Addressing them to the holder keeps a
 * preselection from filling in someone else's screen, which is what broadcasting would do.</p>
 *
 * <p>The geometry comes from {@link Outlines}, computed here rather than asked of YAWP, so the outline
 * appears whether or not YAWP is loaded.</p>
 */
public final class WandOutline {

    /** How often the outline is redrawn, in ticks. Five a second reads as continuous while marking. */
    public static final int REFRESH_TICKS = 4;

    /**
     * Most particles sent in one refresh.
     *
     * <p>A long perimeter is thinned to every nth block rather than sent whole. A radius-40 sphere
     * wireframe is a few hundred points, a large box's edges more, and five times a second that adds up to
     * a packet storm for a decoration. Thinned, it reads as a dashed line, which still shows the shape.</p>
     */
    private static final int MAX_PARTICLES = 400;

    /** Blocks past which outline points are skipped, being out of sight anyway. */
    private static final double VIEW_DISTANCE = 72.0;

    /** How far ahead to look for the block the live preview should stretch to. */
    private static final double REACH = 6.0;

    private static final ParticleOptions CORNER = ParticleTypes.END_ROD;
    private static final DustParticleOptions CONFIRMED =
            new DustParticleOptions(new Vector3f(0.30f, 0.95f, 0.40f), 1.0f);
    private static final DustParticleOptions PREVIEW =
            new DustParticleOptions(new Vector3f(1.00f, 0.78f, 0.20f), 1.0f);

    private WandOutline() {
    }

    /** Called on the server tick for one player. Does nothing unless a wand is in hand. */
    public static void tick(ServerPlayer player) {
        ItemStack wand = heldWand(player);
        if (wand == null) {
            return;
        }
        WandShape shape = MarkerData.shapeOf(wand);
        if (shape == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        List<BlockPos> corners = MarkerData.corners(wand);
        for (BlockPos corner : corners) {
            spawn(level, player, CORNER, corner);
        }

        List<int[]> coordinates = RegionWand.asCoordinates(corners);
        if (MarkerData.isComplete(shape, corners.size())) {
            draw(level, player, Outlines.forShape(shape, coordinates), CONFIRMED);
            report(player, Outlines.measure(shape, coordinates), ChatFormatting.GREEN);
            return;
        }

        // One corner short: show what the block being looked at would produce. This is what makes marking
        // feel like drawing rather than guessing at where the far corner lands.
        if (corners.size() == shape.neededBlocks() - 1) {
            BlockPos aimed = lookingAt(player);
            if (aimed != null && !corners.contains(aimed)) {
                List<int[]> provisional = new ArrayList<>(coordinates);
                provisional.add(new int[]{aimed.getX(), aimed.getY(), aimed.getZ()});
                draw(level, player, Outlines.forShape(shape, provisional), PREVIEW);
                report(player, Outlines.measure(shape, provisional) + "  (sin confirmar)",
                        ChatFormatting.YELLOW);
                return;
            }
        }

        // Partial selection that cannot be previewed: draw what there is, and say what is missing.
        if (coordinates.size() >= 2) {
            draw(level, player, Outlines.forShape(shape, coordinates), PREVIEW);
        }
        report(player, RegionWand.progress(shape, corners.size()), ChatFormatting.GRAY);
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

    private static BlockPos lookingAt(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    private static void draw(ServerLevel level, ServerPlayer player, List<int[]> outline,
                             ParticleOptions particle) {
        if (outline.isEmpty()) {
            return;
        }

        List<int[]> visible = new ArrayList<>(Math.min(outline.size(), MAX_PARTICLES));
        for (int[] point : outline) {
            if (player.distanceToSqr(point[0] + 0.5, point[1] + 0.5, point[2] + 0.5)
                    <= VIEW_DISTANCE * VIEW_DISTANCE) {
                visible.add(point);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        int step = Math.max(1, (visible.size() + MAX_PARTICLES - 1) / MAX_PARTICLES);
        for (int i = 0; i < visible.size(); i += step) {
            int[] point = visible.get(i);
            spawn(level, player, particle, point[0], point[1], point[2]);
        }
    }

    private static void spawn(ServerLevel level, ServerPlayer player, ParticleOptions particle,
                              BlockPos pos) {
        spawn(level, player, particle, pos.getX(), pos.getY(), pos.getZ());
    }

    private static void spawn(ServerLevel level, ServerPlayer player, ParticleOptions particle,
                              int x, int y, int z) {
        level.sendParticles(player, particle, false, x + 0.5, y + 0.5, z + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void report(ServerPlayer player, String text, ChatFormatting colour) {
        player.displayClientMessage(Component.literal(text).withStyle(colour), true);
    }
}
