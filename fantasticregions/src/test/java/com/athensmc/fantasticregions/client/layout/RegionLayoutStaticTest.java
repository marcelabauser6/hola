package com.athensmc.fantasticregions.client.layout;

import com.athensmc.fantasticregions.client.layout.RegionLayout.HelpSplit;
import com.athensmc.fantasticregions.client.layout.ScreenLayout.Rect;
import com.athensmc.fantasticregions.client.layout.ScreenLayout.Regions;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the region screens' geometry at a spread of window sizes, without launching a client.
 *
 * <p>The sizes are the logical GUI resolutions a real client produces: 320x240 is a small window at
 * GUI scale 4, and 960x540 is a large one at scale 2. Both ends matter, because the failure modes are
 * opposite - the small end is where bands collapse into each other and the large end is where a
 * fixed-size element stops reaching the panel edge.</p>
 */
public final class RegionLayoutStaticTest {

    private static final int[][] VIEWPORTS = {
            {320, 240}, {400, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540},
            {1280, 720},
    };

    private static final List<String> FAILURES = new ArrayList<>();

    private RegionLayoutStaticTest() {
    }

    public static void main(String[] args) {
        for (int[] viewport : VIEWPORTS) {
            int width = viewport[0];
            int height = viewport[1];
            checkBandsDoNotOverlap("lista", RegionLayout.list(width, height), width, height);
            checkBandsDoNotOverlap("ajustes", RegionLayout.config(width, height), width, height);
            checkHelpNeverOverlapsContent(width, height);
            checkRowsAndClicksAgree(width, height);
        }
        checkHelpBecomesAColumnOnlyWhenItFits();

        if (!FAILURES.isEmpty()) {
            System.err.println("Screen layout check failed:");
            FAILURES.forEach(failure -> System.err.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("Screen layouts OK: " + VIEWPORTS.length
                + " window sizes, no band or help overlap.");
    }

    private static void checkBandsDoNotOverlap(String screen, Regions regions,
                                               int width, int height) {
        String at = " (" + screen + " @ " + width + "x" + height + ")";
        Rect panel = regions.panel();
        if (panel.right() > width || panel.bottom() > height) {
            FAILURES.add("panel leaves the viewport" + at);
        }

        Rect[] bands = {regions.header(), regions.tabs(), regions.content(), regions.footer()};
        String[] names = {"header", "tabs", "content", "footer"};
        for (int i = 0; i < bands.length; i++) {
            if (!bands[i].isEmpty() && !panel.contains(bands[i])) {
                FAILURES.add(names[i] + " leaves the panel" + at);
            }
            for (int j = i + 1; j < bands.length; j++) {
                if (bands[i].intersects(bands[j])) {
                    FAILURES.add(names[i] + " overlaps " + names[j] + at);
                }
            }
        }
        if (regions.content().isEmpty()) {
            FAILURES.add("content band collapsed to nothing" + at);
        }
    }

    private static void checkHelpNeverOverlapsContent(int width, int height) {
        String at = " (@ " + width + "x" + height + ")";
        for (Regions regions : new Regions[]{RegionLayout.list(width, height),
                RegionLayout.config(width, height)}) {
            HelpSplit split = RegionLayout.withHelp(regions.content());
            if (split.main().intersects(split.help())) {
                FAILURES.add("the help area overlaps the content" + at);
            }
            if (!split.main().isEmpty() && !regions.content().contains(split.main())) {
                FAILURES.add("the content area leaves its band" + at);
            }
            if (!split.help().isEmpty() && !regions.content().contains(split.help())) {
                FAILURES.add("the help area leaves its band" + at);
            }
            if (split.help().isEmpty()) {
                FAILURES.add("no room was reserved for the explanations" + at);
            }
            if (split.main().isEmpty()) {
                FAILURES.add("no room was left for the content itself" + at);
            }
        }
    }

    /**
     * A click must never resolve to a row that was not drawn.
     *
     * <p>The row under the last full one is the case that catches it: the area rarely divides exactly
     * by the row height, and the leftover strip at the bottom belongs to no row.</p>
     */
    private static void checkRowsAndClicksAgree(int width, int height) {
        String at = " (@ " + width + "x" + height + ")";
        HelpSplit split = RegionLayout.withHelp(RegionLayout.config(width, height).content());
        Rect area = split.main();
        int fit = RegionLayout.rowsIn(area, RegionLayout.ROW_H);
        if (fit <= 0) {
            FAILURES.add("no list row fits in the content area" + at);
            return;
        }

        for (int i = 0; i < fit; i++) {
            Rect row = RegionLayout.row(area, i, RegionLayout.ROW_H);
            if (!area.contains(row)) {
                FAILURES.add("row " + i + " leaves the content area" + at);
            }
            int hit = RegionLayout.rowIndexAt(area, RegionLayout.ROW_H, fit,
                    row.x() + 1, row.y() + 1);
            if (hit != i) {
                FAILURES.add("a click in row " + i + " resolved to " + hit + at);
            }
        }

        // A click in the slack below the last row, and one past the right edge, both miss.
        double belowLast = area.y() + (double) fit * RegionLayout.ROW_H + 0.5;
        if (belowLast < area.bottom()) {
            int hit = RegionLayout.rowIndexAt(area, RegionLayout.ROW_H, fit, area.x() + 1, belowLast);
            if (hit != -1) {
                FAILURES.add("a click below the last row selected row " + hit + at);
            }
        }
        if (RegionLayout.rowIndexAt(area, RegionLayout.ROW_H, fit, area.right() + 4, area.y() + 1)
                != -1) {
            FAILURES.add("a click outside the content area selected a row" + at);
        }

        // Fewer rows of data than fit: the empty space below them belongs to nobody.
        if (fit >= 2) {
            int hit = RegionLayout.rowIndexAt(area, RegionLayout.ROW_H, 1,
                    area.x() + 1, area.y() + RegionLayout.ROW_H + 1);
            if (hit != -1) {
                FAILURES.add("a click past the end of a short list selected row " + hit + at);
            }
        }
    }

    private static void checkHelpBecomesAColumnOnlyWhenItFits() {
        HelpSplit wide = RegionLayout.withHelp(new Rect(0, 0, 900, 300));
        if (!wide.helpIsColumn()) {
            FAILURES.add("a 900px content band should put the help in a column");
        }
        if (wide.help().width() > RegionLayout.HELP_COLUMN_MAX) {
            FAILURES.add("the help column grew past its cap: " + wide.help().width());
        }
        if (wide.main().width() < wide.help().width()) {
            FAILURES.add("the help column is wider than the content beside it");
        }

        HelpSplit narrow = RegionLayout.withHelp(new Rect(0, 0, 200, 300));
        if (narrow.helpIsColumn()) {
            FAILURES.add("a 200px content band is too narrow for a help column");
        }
        if (narrow.help().width() != narrow.main().width()) {
            FAILURES.add("the help band should span the full width when it is not a column");
        }
        if (narrow.main().intersects(narrow.help())) {
            FAILURES.add("the help band overlaps the content above it");
        }

        // A band with almost no height at all degrades rather than producing negative rectangles.
        HelpSplit tiny = RegionLayout.withHelp(new Rect(0, 0, 40, 10));
        if (tiny.main().intersects(tiny.help())) {
            FAILURES.add("a collapsed content band produced overlapping areas");
        }
    }
}
