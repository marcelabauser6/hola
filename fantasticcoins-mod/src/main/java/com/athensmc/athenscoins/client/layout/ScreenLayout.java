package com.athensmc.athenscoins.client.layout;

/**
 * Pure geometry helpers shared by the client screens.
 *
 * <p>The class deliberately has no Minecraft dependency: screen layouts can be checked from a
 * plain JVM without starting a client or server.</p>
 */
public final class ScreenLayout {
    public static final int VIEWPORT_MARGIN = 4;

    private ScreenLayout() {
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        /**
         * Shrinks the rectangle on every side, staying inside the original.
         *
         * <p>The origin is moved by at most the rectangle's own size. Adding the inset unconditionally
         * instead let a band shorter than twice the inset end up with its top <em>below</em> its own
         * floor - the height clamped to zero, but the position did not, so a collapsed content band
         * was reported as sitting inside the footer underneath it.</p>
         */
        public Rect inset(int amount) {
            int inset = Math.max(0, amount);
            return new Rect(x + Math.min(inset, width), y + Math.min(inset, height),
                    Math.max(0, width - inset * 2), Math.max(0, height - inset * 2));
        }

        /**
         * Grows the rectangle on every side.
         *
         * <p>Separate from a negative {@link #inset}, which clamps to zero: a grab area that has to
         * cover a knob's overhang needs to be genuinely larger than what was drawn, and silently
         * getting the drawn rectangle back instead would make the slider's edges unclickable.</p>
         */
        public Rect expand(int amount) {
            int grow = Math.max(0, amount);
            return new Rect(x - grow, y - grow, width + grow * 2, height + grow * 2);
        }

        public boolean contains(Rect other) {
            return other.x >= x && other.y >= y
                    && other.right() <= right() && other.bottom() <= bottom();
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        /** True when this rectangle is empty, so it covers no pixel at all. */
        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        /**
         * True when the two rectangles share at least one pixel.
         *
         * <p>An empty rectangle never intersects anything. Without that rule a band that collapsed to
         * zero height still reported an overlap with whatever sat at the same coordinate, which turned
         * the intended degradation - a control that is simply not drawn - into a false alarm and hid
         * the real collisions among the noise.</p>
         */
        public boolean intersects(Rect other) {
            if (isEmpty() || other.isEmpty()) {
                return false;
            }
            return x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }

    public record Regions(Rect panel, Rect header, Rect tabs, Rect content, Rect footer) {
    }

    public record Columns(Rect first, Rect second) {
    }

    /** Centres a preferred panel and shrinks it to remain inside the logical GUI viewport. */
    public static Rect centeredPanel(int viewportWidth, int viewportHeight,
                                     int preferredWidth, int preferredHeight) {
        int availableWidth = Math.max(1, viewportWidth - VIEWPORT_MARGIN * 2);
        int availableHeight = Math.max(1, viewportHeight - VIEWPORT_MARGIN * 2);
        int panelWidth = Math.min(preferredWidth, availableWidth);
        int panelHeight = Math.min(preferredHeight, availableHeight);
        return new Rect((viewportWidth - panelWidth) / 2, (viewportHeight - panelHeight) / 2,
                panelWidth, panelHeight);
    }

    /** Splits a panel into non-overlapping header, optional tabs, content and footer bands. */
    public static Regions regions(Rect panel, int headerHeight, int tabsHeight, int footerHeight) {
        int header = clamp(headerHeight, 0, panel.height());
        int tabs = clamp(tabsHeight, 0, panel.height() - header);
        int footer = clamp(footerHeight, 0, panel.height() - header - tabs);
        Rect headerRect = new Rect(panel.x(), panel.y(), panel.width(), header);
        Rect tabsRect = new Rect(panel.x(), headerRect.bottom(), panel.width(), tabs);
        Rect footerRect = new Rect(panel.x(), panel.bottom() - footer, panel.width(), footer);
        Rect contentRect = new Rect(panel.x(), tabsRect.bottom(), panel.width(),
                Math.max(0, footerRect.y() - tabsRect.bottom()));
        return new Regions(panel, headerRect, tabsRect, contentRect, footerRect);
    }

    public static Columns columns(Rect area, int gap) {
        int safeGap = clamp(gap, 0, area.width());
        int firstWidth = (area.width() - safeGap) / 2;
        return new Columns(new Rect(area.x(), area.y(), firstWidth, area.height()),
                new Rect(area.x() + firstWidth + safeGap, area.y(),
                        area.width() - safeGap - firstWidth, area.height()));
    }

    /**
     * One column of an evenly split area, with real gaps between the columns.
     *
     * <p>{@link #columns} only ever made two, and {@link #partition} leaves no gap at all. Three or
     * more text columns need both: integer division carried through the same expression for the start
     * and the end edge, so the columns tile the area exactly instead of drifting a pixel apart and
     * letting the last one hang over the panel border.</p>
     */
    public static Rect column(Rect area, int count, int gap, int index) {
        if (count <= 0 || index < 0 || index >= count) {
            return new Rect(area.x(), area.y(), 0, area.height());
        }
        int safeGap = clamp(gap, 0, area.width() / Math.max(1, count));
        int usable = Math.max(0, area.width() - safeGap * (count - 1));
        int start = area.x() + index * usable / count + index * safeGap;
        int end = area.x() + (index + 1) * usable / count + index * safeGap;
        return new Rect(start, area.y(), Math.max(0, end - start), area.height());
    }

    /**
     * One column of an unevenly split area.
     *
     * <p>{@link #column} splits evenly, which is wrong whenever the columns hold different kinds of thing.
     * The account file is the case that forced this: a label-and-figure column needs about half the width
     * of a movement history, and giving all three an equal third meant the history clipped every row while
     * the other two had space to spare. Weights are relative, so {@code {28, 28, 44}} and {@code {7, 7, 11}}
     * lay out identically.</p>
     *
     * <p>Edges are computed by carrying the running weight through the same expression for the start and
     * the end, so the columns tile the area exactly rather than drifting a pixel apart.</p>
     */
    public static Rect weightedColumn(Rect area, int[] weights, int gap, int index) {
        if (weights == null || weights.length == 0 || index < 0 || index >= weights.length) {
            return new Rect(area.x(), area.y(), 0, area.height());
        }
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return column(area, weights.length, gap, index);
        }
        int safeGap = clamp(gap, 0, area.width() / Math.max(1, weights.length));
        int usable = Math.max(0, area.width() - safeGap * (weights.length - 1));
        int before = 0;
        for (int i = 0; i < index; i++) {
            before += Math.max(0, weights[i]);
        }
        int start = area.x() + before * usable / total + index * safeGap;
        int end = area.x() + (before + Math.max(0, weights[index])) * usable / total + index * safeGap;
        return new Rect(start, area.y(), Math.max(0, end - start), area.height());
    }

    public static int visibleRows(Rect area, int rowHeight) {
        return rowHeight <= 0 ? 0 : Math.max(0, area.height() / rowHeight);
    }

    public static int gridCellWidth(Rect area, int columns, int gap) {
        if (columns <= 0) {
            return 0;
        }
        return Math.max(1, (area.width() - Math.max(0, columns - 1) * Math.max(0, gap)) / columns);
    }

    /** Returns one exact, gap-free horizontal partition; drawing and hit-testing can share it. */
    public static Rect partition(Rect area, int parts, int index) {
        if (parts <= 0 || index < 0 || index >= parts) {
            return new Rect(area.x(), area.y(), 0, area.height());
        }
        int start = area.x() + index * area.width() / parts;
        int end = area.x() + (index + 1) * area.width() / parts;
        return new Rect(start, area.y(), Math.max(0, end - start), area.height());
    }

    public static int partitionIndex(Rect area, int parts, double px, double py) {
        if (parts <= 0 || !area.contains(px, py) || area.width() <= 0) {
            return -1;
        }
        for (int i = 0; i < parts; i++) {
            if (partition(area, parts, i).contains(px, py)) {
                return i;
            }
        }
        return -1;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
