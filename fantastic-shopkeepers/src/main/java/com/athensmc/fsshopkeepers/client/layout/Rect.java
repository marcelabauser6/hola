package com.athensmc.fsshopkeepers.client.layout;

/**
 * A rectangle in screen pixels.
 *
 * <p>Plain arithmetic with no Minecraft types, so a whole screen's geometry can be worked out and checked by a test
 * running on a bare JVM. That is the point of separating layout from drawing: an overlap becomes a failing assertion
 * instead of something noticed in a screenshot.</p>
 */
public record Rect(int x, int y, int width, int height) {

    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    /**
     * Whether two rectangles share any pixel.
     *
     * <p>Empty rectangles never overlap anything. A control that is not shown has no geometry to clash with, and
     * treating a zero-width rectangle as overlapping would make every hidden control a layout failure.</p>
     */
    public boolean overlaps(Rect other) {
        if (other == null || isEmpty() || other.isEmpty()) {
            return false;
        }
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    /** The same rectangle inset on every side, clamped so it never becomes negative. */
    public Rect shrink(int by) {
        int newWidth = Math.max(0, width - by * 2);
        int newHeight = Math.max(0, height - by * 2);
        return new Rect(x + by, y + by, newWidth, newHeight);
    }

    /** A rectangle of the given height taken off the top, leaving the rest. */
    public Rect topRow(int rowHeight) {
        return new Rect(x, y, width, Math.min(rowHeight, height));
    }

    /** Vertically centres a fixed-height band inside this rectangle. */
    public Rect centeredBand(int bandHeight) {
        int h = Math.min(bandHeight, height);
        return new Rect(x, y + (height - h) / 2, width, h);
    }

    /** Whether this rectangle sits entirely inside another. */
    public boolean within(Rect outer) {
        return x >= outer.x && y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
    }
}
