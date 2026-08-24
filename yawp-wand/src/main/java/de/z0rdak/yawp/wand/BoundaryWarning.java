package de.z0rdak.yawp.wand;

import de.z0rdak.yawp.api.core.ILevelRegionApi;
import de.z0rdak.yawp.api.core.RegionManager;
import de.z0rdak.yawp.core.area.IMarkableArea;
import de.z0rdak.yawp.core.region.IMarkableRegion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Shows the edges of existing regions in red, while the wand is held.
 *
 * <p>A marking aid, not an ambient warning. Holding the rod, red is where the regions that already exist
 * end and green is the selection being drawn - so the two colours together answer the question that matters
 * while marking: is this new region going to land on top of one already there. Put the rod away and the red
 * goes with it.</p>
 *
 * <p>It was the other way round at first, drawn whenever a player was near an edge and suppressed while
 * marking. That is worse in both directions: as an ambient effect it is noise nobody asked for, following
 * you around every time you walk near a claim, and it was hidden at exactly the moment the information is
 * worth having.</p>
 *
 * <p>Only the part of the edge you are next to is painted, not the whole perimeter, which would be
 * unreadable and a great many particles.</p>
 *
 * <p><strong>Found by asking the area, not by shape.</strong> A block is on the boundary when it is inside
 * the region and at least one of its six neighbours is not. That test is the only geometry here, and it
 * needs nothing but {@code IMarkableArea.contains} - so it is exactly right for all five of YAWP's shapes,
 * including the polygon and the prism, whose surfaces this code could not otherwise describe. The
 * alternative, a formula per shape, would have been five chances to disagree with what the region actually
 * protects.</p>
 *
 * <p>Bounded work: a small cube around the player is scanned and nothing else, so the cost does not depend
 * on how large the region is or how many exist. Only the player who is close sees it.</p>
 */
public final class BoundaryWarning {

    /** How often the warning is redrawn, in ticks. */
    public static final int REFRESH_TICKS = 5;

    /**
     * How near an edge lights it up, and the radius of the scan that finds it.
     *
     * <p>Four rather than the three it was as an ambient effect. Now that it only appears with the rod in
     * hand, seeing a little further is the point - a neighbouring region's wall is worth noticing before
     * the corner is placed, not after. Still a bounded 9x9x9, so the cost does not follow the size of the
     * region or how many exist.</p>
     */
    private static final int WARN_RADIUS = 4;

    /** Most regions considered at once, so standing where several overlap stays cheap. */
    private static final int MAX_REGIONS = 4;

    /** Most particles per refresh. A 7x7x7 scan cannot exceed this by much, but a cap is a cap. */
    private static final int MAX_PARTICLES = 180;

    /** Red, and a little smaller than the wand's outline so the two are not mistaken for each other. */
    private static final DustParticleOptions EDGE =
            new DustParticleOptions(new Vector3f(1.00f, 0.15f, 0.15f), 0.8f);

    /** The six neighbours whose absence makes a block an edge. */
    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    private BoundaryWarning() {
    }

    /** Called on the server tick for one player. */
    public static void tick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Only with the rod in hand. This is there to be read against the selection being marked, and as
        // something that followed a player around whenever they passed a claim it was just noise.
        if (!holdingWand(player)) {
            return;
        }

        Optional<ILevelRegionApi> api = RegionManager.get().getDimRegionApi(level.dimension());
        if (api.isEmpty()) {
            return;
        }

        BlockPos at = player.blockPosition();
        List<IMarkableRegion> nearby;
        try {
            nearby = api.get().getRegionsAround(at, WARN_RADIUS);
        } catch (RuntimeException lookupFailed) {
            return;
        }
        if (nearby.isEmpty()) {
            return;
        }

        int drawn = 0;
        int regions = 0;
        for (IMarkableRegion region : nearby) {
            if (regions++ >= MAX_REGIONS || drawn >= MAX_PARTICLES) {
                break;
            }
            if (!region.isActive()) {
                // An inactive region protects nothing, so drawing its edge would be a warning about a
                // limit that is not being enforced.
                continue;
            }
            drawn += drawEdgeNear(level, player, region.getArea(), at, MAX_PARTICLES - drawn);
        }
    }

    /**
     * Draws the boundary blocks of one area within the scan cube.
     *
     * @return how many particles were sent.
     */
    private static int drawEdgeNear(ServerLevel level, ServerPlayer player, IMarkableArea area,
                                    BlockPos centre, int budget) {
        if (area == null || budget <= 0) {
            return 0;
        }
        int sent = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

        for (int dx = -WARN_RADIUS; dx <= WARN_RADIUS && sent < budget; dx++) {
            for (int dy = -WARN_RADIUS; dy <= WARN_RADIUS && sent < budget; dy++) {
                for (int dz = -WARN_RADIUS; dz <= WARN_RADIUS && sent < budget; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!isEdge(area, cursor, neighbour)) {
                        continue;
                    }
                    level.sendParticles(player, EDGE, false,
                            cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                            1, 0.0, 0.0, 0.0, 0.0);
                    sent++;
                }
            }
        }
        return sent;
    }

    /** Inside the area, with at least one neighbour outside it. */
    private static boolean isEdge(IMarkableArea area, BlockPos pos,
                                  BlockPos.MutableBlockPos scratch) {
        try {
            if (!area.contains(pos)) {
                return false;
            }
            for (int[] offset : NEIGHBOURS) {
                scratch.set(pos.getX() + offset[0], pos.getY() + offset[1], pos.getZ() + offset[2]);
                if (!area.contains(scratch)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException areaMisbehaved) {
            return false;
        }
    }

    private static boolean holdingWand(ServerPlayer player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            if (RegionWand.isWand(player.getItemInHand(hand))) {
                return true;
            }
        }
        return false;
    }
}
