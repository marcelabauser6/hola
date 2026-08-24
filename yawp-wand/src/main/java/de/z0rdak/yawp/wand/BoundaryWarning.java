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
 * Shows a region's edge in red when a player walks up to it.
 *
 * <p>A region's boundary is invisible, so the first sign of it is usually an action being refused. This
 * paints the part of the edge you are next to, and only that part: walking along a wall shows the wall, not
 * the whole perimeter, which would be both unreadable and a great deal of particles.</p>
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

    /** How near an edge a player has to be for it to light up. Also the scan radius. */
    private static final int WARN_RADIUS = 3;

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
        // The wand draws its own outline in green and yellow. Adding a red one on top of it while marking
        // would turn two clear signals into one confusing one.
        if (holdingWand(player)) {
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
