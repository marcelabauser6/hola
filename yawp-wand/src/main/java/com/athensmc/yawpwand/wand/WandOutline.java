package com.athensmc.yawpwand.wand;

import de.z0rdak.yawp.core.area.CuboidArea;
import de.z0rdak.yawp.core.area.IMarkableArea;
import de.z0rdak.yawp.core.area.SphereArea;
import de.z0rdak.yawp.core.area.VerticalCylinderArea;
import de.z0rdak.yawp.core.stick.MarkerStick;
import de.z0rdak.yawp.util.LocalRegions;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
 * <p>Particles rather than a client-side renderer, and sent to the one player holding the rod rather
 * than broadcast. Both choices are deliberate:</p>
 *
 * <ul>
 *   <li><strong>Particles, so nothing has to be installed on the client.</strong> A renderer would be
 *       crisper and would follow the cursor at frame rate, but it would mean every administrator needs
 *       this mod in their own client before the wand does anything. For a tool whose whole job is to
 *       save typing, that is a poor trade.</li>
 *   <li><strong>Only to the holder.</strong> A preselection is scaffolding, not a feature of the world.
 *       Broadcasting it would light up the perimeter for anyone standing nearby, and on a server where
 *       several people build at once that is somebody else's screen full of dust.</li>
 * </ul>
 *
 * <p>The geometry is not computed here. The marked blocks are handed to YAWP's own
 * {@code LocalRegions.areaFrom}, and the outline drawn is the outline of the area YAWP would build from
 * them. So what is on screen is the region that will exist, not this mod's guess at it - including for
 * spheres and cylinders, where a hand-rolled approximation would disagree with YAWP's rounding.</p>
 */
public final class WandOutline {

    /** How often the outline is redrawn, in ticks. Five a second reads as continuous while marking. */
    public static final int REFRESH_TICKS = 4;

    /**
     * Most particles sent in one refresh.
     *
     * <p>A sphere of radius 40 has a few thousand blocks on its hull. Sending all of them five times a
     * second is a packet storm for a decoration, so a long outline is thinned to every nth block. It
     * reads as a dashed line, which still shows the shape and the extent.</p>
     */
    private static final int MAX_PARTICLES = 420;

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
        MarkerStick marker = RegionWand.markerOf(wand);
        if (marker == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        List<BlockPos> marked = marker.getMarkedBlocks();
        for (BlockPos corner : marked) {
            spawn(level, player, CORNER, corner);
        }

        IMarkableArea confirmed = areaOf(marker);
        if (confirmed != null) {
            drawOutline(level, player, confirmed, CONFIRMED);
            report(player, describe(confirmed, marked.size()), ChatFormatting.GREEN);
            return;
        }

        // Not yet a valid area. If one more corner would finish it, show what the block being looked
        // at would produce - the part that makes marking feel like drawing rather than guessing.
        IMarkableArea preview = previewArea(player, marker);
        if (preview != null) {
            drawOutline(level, player, preview, PREVIEW);
            report(player, describe(preview, marked.size() + 1) + "  (sin confirmar)",
                    ChatFormatting.YELLOW);
            return;
        }

        report(player, remaining(marker, marked.size()), ChatFormatting.GRAY);
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

    /** The area YAWP would build from the marked blocks, or null when they do not make one yet. */
    private static IMarkableArea areaOf(MarkerStick marker) {
        if (!marker.isValidArea()) {
            return null;
        }
        try {
            return LocalRegions.areaFrom(marker);
        } catch (RuntimeException notAnArea) {
            return null;
        }
    }

    /**
     * The area that would exist if the player clicked the block they are looking at.
     *
     * <p>Only offered when the selection is exactly one corner short. For a polygon that is already
     * valid at three points, the confirmed outline is drawn instead, so this never fights with it.</p>
     */
    private static IMarkableArea previewArea(ServerPlayer player, MarkerStick marker) {
        int needed = RegionWand.neededBlocks(marker);
        if (needed <= 0 || marker.getMarkedBlocks().size() != needed - 1) {
            return null;
        }
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        try {
            // A copy, so looking around never edits the rod the player is holding.
            MarkerStick provisional = new MarkerStick(marker.serializeNBT());
            provisional.addMarkedBlock(blockHit.getBlockPos());
            return areaOf(provisional);
        } catch (RuntimeException notAnArea) {
            return null;
        }
    }

    private static void drawOutline(ServerLevel level, ServerPlayer player, IMarkableArea area,
                                    ParticleOptions particle) {
        List<BlockPos> outline = outlineOf(area);
        if (outline.isEmpty()) {
            return;
        }

        List<BlockPos> visible = new ArrayList<>(Math.min(outline.size(), MAX_PARTICLES));
        for (BlockPos pos : outline) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    <= VIEW_DISTANCE * VIEW_DISTANCE) {
                visible.add(pos);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        int step = Math.max(1, (visible.size() + MAX_PARTICLES - 1) / MAX_PARTICLES);
        for (int i = 0; i < visible.size(); i += step) {
            spawn(level, player, particle, visible.get(i));
        }
    }

    private static List<BlockPos> outlineOf(IMarkableArea area) {
        try {
            var minimal = area.getMinimalOutline();
            if (minimal != null && !minimal.isEmpty()) {
                return new ArrayList<>(minimal);
            }
            var frame = area.getFrame();
            return frame == null ? List.of() : new ArrayList<>(frame);
        } catch (RuntimeException noOutline) {
            return List.of();
        }
    }

    private static void spawn(ServerLevel level, ServerPlayer player, ParticleOptions particle,
                              BlockPos pos) {
        level.sendParticles(player, particle, false,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * The size of an area, with units, for the readout above the hotbar.
     *
     * <p>Read off YAWP's own area objects rather than recomputed from the corners, so the figures agree
     * with the region that gets created. The three shapes are described in their own terms: a span for a
     * box, a radius for a ball, both for a cylinder.</p>
     */
    private static String describe(IMarkableArea area, int corners) {
        if (area instanceof CuboidArea cuboid) {
            return cuboid.getXsize() + " x " + cuboid.getYsize() + " x " + cuboid.getZsize()
                    + " bloques";
        }
        if (area instanceof SphereArea sphere) {
            return "radio " + sphere.getRadius() + " bloques";
        }
        if (area instanceof VerticalCylinderArea cylinder) {
            return "radio " + cylinder.getRadius() + " bloques";
        }
        return corners + (corners == 1 ? " punto marcado" : " puntos marcados");
    }

    /** What is still missing, when there is not enough marked to draw anything. */
    private static String remaining(MarkerStick marker, int marked) {
        int needed = RegionWand.neededBlocks(marker);
        int left = Math.max(0, needed - marked);
        if (left <= 0) {
            return "Marca los bloques de la zona con clic derecho.";
        }
        if (left == 1) {
            return "Falta 1 bloque por marcar.";
        }
        return "Faltan " + left + " bloques por marcar.";
    }

    private static void report(ServerPlayer player, String text, ChatFormatting colour) {
        player.displayClientMessage(Component.literal(text).withStyle(colour), true);
    }
}
