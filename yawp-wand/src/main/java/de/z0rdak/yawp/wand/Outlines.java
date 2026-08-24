package de.z0rdak.yawp.wand;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The perimeter to draw for a set of marked corners.
 *
 * <p>Computed here rather than asked of YAWP, so the outline appears whether or not YAWP is loaded. The
 * shapes match YAWP's own definitions: a cuboid is the box between two corners, a sphere is a centre and
 * a radius taken from the second corner, a cylinder is that circle extended between the heights of its
 * marked blocks.</p>
 *
 * <p>Wireframes, not hulls. A sphere is drawn as three great circles and a cylinder as two rings joined
 * by uprights, because the alternative - every block whose distance from the centre is close to the
 * radius - is thousands of positions for a radius of forty, and this runs several times a second while
 * the rod is held.</p>
 *
 * <p>Free of any Minecraft import, so the geometry can be checked on a plain JVM. Positions are
 * {@code int[]{x, y, z}} block coordinates.</p>
 */
public final class Outlines {

    /** Angular resolution: roughly one point per block along a circle, never fewer than this many. */
    private static final int MIN_CIRCLE_POINTS = 12;

    private Outlines() {
    }

    /**
     * The outline for what is currently marked, or an empty list when there is nothing to draw.
     *
     * <p>Polygons and prisms get the box around their vertices rather than their true footprint. Their
     * real shape depends on the order the vertices were clicked and on how YAWP triangulates them, and a
     * guess at that would draw a perimeter the region does not have. The box is honest about being a
     * bound, and the vertices themselves are marked separately.</p>
     */
    public static List<int[]> forShape(WandShape shape, List<int[]> corners) {
        if (shape == null || corners == null || corners.isEmpty()) {
            return List.of();
        }
        return switch (shape) {
            case CUBOID -> corners.size() < 2 ? List.of()
                    : boxEdges(min(corners), max(corners));
            case SPHERE -> corners.size() < 2 ? List.of()
                    : sphereWireframe(corners.get(0), radius(corners.get(0), corners.get(1), true));
            case CYLINDER -> corners.size() < 2 ? List.of()
                    : cylinderWireframe(corners);
            case POLYGON, PRISM -> corners.size() < 2 ? List.of()
                    : boxEdges(min(corners), max(corners));
        };
    }

    /** The 12 edges of the box between two corners, at one point per block. */
    public static List<int[]> boxEdges(int[] min, int[] max) {
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> points = new ArrayList<>();

        for (int x = min[0]; x <= max[0]; x++) {
            add(points, seen, x, min[1], min[2]);
            add(points, seen, x, min[1], max[2]);
            add(points, seen, x, max[1], min[2]);
            add(points, seen, x, max[1], max[2]);
        }
        for (int y = min[1]; y <= max[1]; y++) {
            add(points, seen, min[0], y, min[2]);
            add(points, seen, min[0], y, max[2]);
            add(points, seen, max[0], y, min[2]);
            add(points, seen, max[0], y, max[2]);
        }
        for (int z = min[2]; z <= max[2]; z++) {
            add(points, seen, min[0], min[1], z);
            add(points, seen, min[0], max[1], z);
            add(points, seen, max[0], min[1], z);
            add(points, seen, max[0], max[1], z);
        }
        return points;
    }

    /** Three great circles through the centre: the readable way to show a sphere in particles. */
    public static List<int[]> sphereWireframe(int[] centre, int radius) {
        if (radius <= 0) {
            return List.of(new int[]{centre[0], centre[1], centre[2]});
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> points = new ArrayList<>();
        int steps = circleSteps(radius);

        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int a = (int) Math.round(radius * Math.cos(angle));
            int b = (int) Math.round(radius * Math.sin(angle));
            add(points, seen, centre[0] + a, centre[1] + b, centre[2]);
            add(points, seen, centre[0] + a, centre[1], centre[2] + b);
            add(points, seen, centre[0], centre[1] + a, centre[2] + b);
        }
        return points;
    }

    /**
     * A ring at the bottom, a ring at the top, and four uprights joining them.
     *
     * <p>The centre and radius come from the first two corners; the height spans every marked block, so
     * the third click sets the top or the bottom depending on where the player stood.</p>
     */
    public static List<int[]> cylinderWireframe(List<int[]> corners) {
        int[] centre = corners.get(0);
        int radius = radius(centre, corners.get(1), false);
        int lowY = min(corners)[1];
        int highY = max(corners)[1];
        if (radius <= 0) {
            List<int[]> column = new ArrayList<>();
            for (int y = lowY; y <= highY; y++) {
                column.add(new int[]{centre[0], y, centre[2]});
            }
            return column;
        }

        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> points = new ArrayList<>();
        int steps = circleSteps(radius);
        List<int[]> ring = new ArrayList<>(steps);

        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int dx = (int) Math.round(radius * Math.cos(angle));
            int dz = (int) Math.round(radius * Math.sin(angle));
            ring.add(new int[]{centre[0] + dx, centre[2] + dz});
            add(points, seen, centre[0] + dx, lowY, centre[2] + dz);
            add(points, seen, centre[0] + dx, highY, centre[2] + dz);
        }

        // Four uprights, evenly spaced around the ring, so the height reads at a glance.
        for (int corner = 0; corner < 4; corner++) {
            int[] at = ring.get(corner * steps / 4);
            for (int y = lowY; y <= highY; y++) {
                add(points, seen, at[0], y, at[1]);
            }
        }
        return points;
    }

    /**
     * Distance between two blocks, rounded to the nearest whole block.
     *
     * <p>Rounded rather than truncated, because the player clicked a block they consider to be on the
     * edge, and a radius one short would leave that very block outside the region they just drew. A
     * cylinder ignores the vertical difference: its radius is measured on the ground, and counting the
     * height would inflate it.</p>
     */
    public static int radius(int[] centre, int[] edge, boolean includeVertical) {
        long dx = (long) edge[0] - centre[0];
        long dy = (long) edge[1] - centre[1];
        long dz = (long) edge[2] - centre[2];
        double squared = includeVertical
                ? (double) (dx * dx + dy * dy + dz * dz)
                : (double) (dx * dx + dz * dz);
        return (int) Math.round(Math.sqrt(squared));
    }

    public static int[] min(List<int[]> corners) {
        int[] min = {corners.get(0)[0], corners.get(0)[1], corners.get(0)[2]};
        for (int[] corner : corners) {
            min[0] = Math.min(min[0], corner[0]);
            min[1] = Math.min(min[1], corner[1]);
            min[2] = Math.min(min[2], corner[2]);
        }
        return min;
    }

    public static int[] max(List<int[]> corners) {
        int[] max = {corners.get(0)[0], corners.get(0)[1], corners.get(0)[2]};
        for (int[] corner : corners) {
            max[0] = Math.max(max[0], corner[0]);
            max[1] = Math.max(max[1], corner[1]);
            max[2] = Math.max(max[2], corner[2]);
        }
        return max;
    }

    /**
     * The measurement shown above the hotbar, with units.
     *
     * <p>Each shape described in its own terms: a span for a box, a radius for a ball, both for a
     * cylinder, a vertex count for the two that have no single figure.</p>
     */
    public static String measure(WandShape shape, List<int[]> corners) {
        if (corners.isEmpty()) {
            return "Sin marcar";
        }
        if (corners.size() < 2) {
            return "1 punto marcado";
        }
        int[] min = min(corners);
        int[] max = max(corners);
        return switch (shape) {
            case CUBOID -> (max[0] - min[0] + 1) + " x " + (max[1] - min[1] + 1)
                    + " x " + (max[2] - min[2] + 1) + " bloques";
            case SPHERE -> "radio " + radius(corners.get(0), corners.get(1), true) + " bloques";
            case CYLINDER -> "radio " + radius(corners.get(0), corners.get(1), false)
                    + " y alto " + (max[1] - min[1] + 1) + " bloques";
            case POLYGON, PRISM -> corners.size() + " puntos marcados";
        };
    }

    private static int circleSteps(int radius) {
        return Math.max(MIN_CIRCLE_POINTS, (int) Math.ceil(2 * Math.PI * radius));
    }

    /** Adds a point unless that exact block is already in the list. */
    private static void add(List<int[]> points, Set<Long> seen, int x, int y, int z) {
        // Packed the way vanilla packs a block position, so the key is unique for any real coordinate.
        long key = ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
        if (seen.add(key)) {
            points.add(new int[]{x, y, z});
        }
    }
}
