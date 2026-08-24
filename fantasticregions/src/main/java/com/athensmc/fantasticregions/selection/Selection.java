package com.athensmc.fantasticregions.selection;

/**
 * A region being marked out: its shape and the blocks clicked so far.
 *
 * <p>Immutable, and free of any Minecraft type, so the measurements shown live while marking can be
 * checked on a plain JVM. That matters more here than in most places: the numbers on screen while the
 * player is picking corners are the only feedback they get before the region exists, and getting the
 * span off by one - the classic mistake of {@code max - min} instead of {@code max - min + 1} - would
 * mean the readout disagrees with the region that gets built.</p>
 */
public record Selection(Shape shape, Point first, Point second) {

    /** A block position. Not {@code BlockPos}, so this class stays testable without a client. */
    public record Point(int x, int y, int z) {
        public String display() {
            return x + ", " + y + ", " + z;
        }
    }

    /** The measured extent of a selection, ready to be put on screen. */
    public record Extent(int spanX, int spanY, int spanZ, int radius, long blocks) {
    }

    public static Selection empty(Shape shape) {
        return new Selection(shape, null, null);
    }

    /**
     * Records the next click.
     *
     * <p>Once both points are set, a further click replaces the second one rather than being ignored.
     * The alternative - refusing it - meant that overshooting the far corner left no way to fix the
     * selection except to start the whole region again.</p>
     */
    public Selection withPoint(Point point) {
        if (point == null) {
            return this;
        }
        if (first == null) {
            return new Selection(shape, point, null);
        }
        return new Selection(shape, first, point);
    }

    /** Drops the last click, so a misplaced corner can be taken back. */
    public Selection withoutLastPoint() {
        if (second != null) {
            return new Selection(shape, first, null);
        }
        return new Selection(shape, null, null);
    }

    public int markedCount() {
        if (first == null) {
            return 0;
        }
        return second == null ? 1 : 2;
    }

    public boolean isComplete() {
        return markedCount() >= Shape.POINTS_NEEDED;
    }

    /** What the player should do next. */
    public String hint() {
        return shape.hintFor(markedCount());
    }

    public Point min() {
        if (first == null) {
            return null;
        }
        if (second == null) {
            return first;
        }
        return new Point(Math.min(first.x(), second.x()),
                Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()));
    }

    public Point max() {
        if (first == null) {
            return null;
        }
        if (second == null) {
            return first;
        }
        return new Point(Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()),
                Math.max(first.z(), second.z()));
    }

    /**
     * The radius of a centred shape, measured from the first click to the second.
     *
     * <p>Rounded to the nearest block rather than truncated, because the player clicked a block they
     * consider to be on the edge and a radius one short would leave it outside the region they just
     * drew.</p>
     */
    public int radius() {
        if (first == null || second == null) {
            return 0;
        }
        long dx = (long) second.x() - first.x();
        long dy = (long) second.y() - first.y();
        long dz = (long) second.z() - first.z();
        if (shape == Shape.CYLINDER) {
            // A vertical cylinder's radius is measured on the ground plane only; the height comes
            // from the two clicks' own y values, so counting dy here would inflate it.
            return (int) Math.round(Math.sqrt((double) (dx * dx + dz * dz)));
        }
        return (int) Math.round(Math.sqrt((double) (dx * dx + dy * dy + dz * dz)));
    }

    /**
     * The size of what is currently marked.
     *
     * <p>Spans are inclusive of both end blocks: two corners one apart span two blocks, not one.</p>
     */
    public Extent extent() {
        if (first == null) {
            return new Extent(0, 0, 0, 0, 0L);
        }
        if (second == null) {
            return new Extent(1, 1, 1, 0, 1L);
        }
        Point min = min();
        Point max = max();
        int spanX = max.x() - min.x() + 1;
        int spanY = max.y() - min.y() + 1;
        int spanZ = max.z() - min.z() + 1;
        int radius = radius();
        return switch (shape) {
            case CUBOID -> new Extent(spanX, spanY, spanZ, 0,
                    (long) spanX * spanY * spanZ);
            case SPHERE -> {
                int diameter = radius * 2 + 1;
                // Volume of the sphere, not of the box around it, so the figure matches the region.
                long blocks = Math.round(4.0 / 3.0 * Math.PI * radius * radius * radius);
                yield new Extent(diameter, diameter, diameter, radius, Math.max(1L, blocks));
            }
            case CYLINDER -> {
                int diameter = radius * 2 + 1;
                long blocks = Math.round(Math.PI * radius * radius) * spanY;
                yield new Extent(diameter, spanY, diameter, radius, Math.max(1L, blocks));
            }
        };
    }

    /** The extent as one line, with units, for the readout shown while marking. */
    public String extentDisplay() {
        if (first == null) {
            return "Sin marcar";
        }
        Extent extent = extent();
        return switch (shape) {
            case CUBOID -> extent.spanX() + " x " + extent.spanY() + " x " + extent.spanZ()
                    + " bloques";
            case SPHERE -> "radio " + extent.radius() + " bloques";
            case CYLINDER -> "radio " + extent.radius() + " y alto " + extent.spanY() + " bloques";
        };
    }
}
