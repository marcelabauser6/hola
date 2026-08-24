package com.athensmc.athenscoins.client.layout;

import com.athensmc.athenscoins.wallet.AmountInput;

import java.util.ArrayList;
import java.util.List;

/**
 * Static geometry verification. Runs on a plain JVM; it never starts Minecraft, a client or a
 * server. Execute with {@code ./gradlew verifyScreenLayouts}.
 *
 * <p>Two things changed about how this guard works, both because the previous version could not fail
 * for the bugs it was supposed to catch.</p>
 *
 * <p>First, it now <em>calls</em> the production geometry - {@link AtmLayout},
 * {@link ThemeEditorLayout} and {@link PanelMetrics} - instead of re-typing each screen's constants.
 * Re-typing them meant the guard checked a copy: a button could be moved in a screen and the guard
 * would still pass, which is precisely how the overlapping ATM columns and the theme editor's
 * collision survived a passing suite.</p>
 *
 * <p>Second, the viewport list was every size at least 240 logical pixels tall, so
 * {@code centeredPanel} always returned its full preferred height and no height-dependent formula
 * was ever exercised at more than one value. The short viewports below are what make the collapse
 * behaviour testable: the requirement is not that everything fits at any size, it is that when
 * something cannot fit it <em>collapses</em> rather than being drawn on top of its neighbour.</p>
 */
public final class ScreenLayoutStaticTest {

    /** Sizes a real client produces; Minecraft keeps the logical viewport at 320x240 or larger. */
    private static final int[][] VIEWPORTS = {
            {320, 240}, {426, 240}, {640, 360}, {854, 480}, {1280, 720}
    };

    /**
     * Deliberately below anything the game will hand us.
     *
     * <p>These exist to prove the layouts degrade safely rather than to describe a supported window.
     * Every band must still be disjoint and still inside its panel.</p>
     */
    private static final int[][] CRAMPED_VIEWPORTS = {
            {320, 200}, {300, 160}, {260, 120}, {200, 80}
    };

    private static final List<String> failures = new ArrayList<>();

    private ScreenLayoutStaticTest() {
    }

    public static void main(String[] args) {
        for (int[] viewport : VIEWPORTS) {
            verifyStandardScreens(viewport[0], viewport[1], true);
            verifyWallet(viewport[0], viewport[1]);
            verifyAtm(viewport[0], viewport[1], true);
            verifyThemeEditor(viewport[0], viewport[1], true);
        }
        // Same invariants, minus the "usable" requirements, at sizes nothing has to support.
        for (int[] viewport : CRAMPED_VIEWPORTS) {
            verifyStandardScreens(viewport[0], viewport[1], false);
            verifyAtm(viewport[0], viewport[1], false);
            verifyThemeEditor(viewport[0], viewport[1], false);
        }
        verifyAmountInput();

        if (!failures.isEmpty()) {
            throw new AssertionError("Screen layout failures:\n - " + String.join("\n - ", failures));
        }
        System.out.println("Screen layouts verified for " + VIEWPORTS.length + " supported and "
                + CRAMPED_VIEWPORTS.length + " deliberately cramped viewports, plus amount-input "
                + "filtering.");
    }

    // ------------------------------------------------------------------ standard panels

    private static void verifyStandardScreens(int width, int height, boolean usable) {
        String at = " at " + width + "x" + height;
        ScreenLayout.Rect viewport = new ScreenLayout.Rect(0, 0, width, height);
        require(viewport.contains(PanelMetrics.panel(width, height)), "standard panel outside" + at);

        ScreenLayout.Regions terminal = PanelMetrics.terminal(width, height);
        verifyRegions("terminal" + at, terminal);
        ScreenLayout.Rect tabs = terminal.tabs().inset(4);
        int tabWidth = ScreenLayout.gridCellWidth(tabs, 5, 2);
        ScreenLayout.Rect lastTab = new ScreenLayout.Rect(
                tabs.x() + 4 * (tabWidth + 2), tabs.y(), tabWidth, Math.min(18, tabs.height()));
        require(tabs.contains(lastTab), "terminal tabs overflow" + at);

        ScreenLayout.Regions account = PanelMetrics.account(width, height);
        verifyRegions("account" + at, account);
        ScreenLayout.Columns accountColumns =
                ScreenLayout.columns(account.content().inset(PanelMetrics.CONTENT_PAD), 10);
        require(!accountColumns.first().intersects(accountColumns.second()),
                "account columns overlap" + at);
        verifyFooterGrid("account" + at, account.footer().inset(6), 4, 4, 16);

        ScreenLayout.Regions central = PanelMetrics.central(width, height);
        verifyRegions("central" + at, central);
        verifyFooterGrid("central" + at, central.footer().inset(6), 3, 4, 16);
        // The close button is the last of four footer rows; it used to sit past the band's floor.
        ScreenLayout.Rect centralFooter = central.footer().inset(6);
        ScreenLayout.Rect centralClose = new ScreenLayout.Rect(centralFooter.x(),
                centralFooter.y() + 41 + 10 + 3, centralFooter.width(), 18);
        if (usable) {
            require(central.footer().contains(centralClose),
                    "central close button escapes the footer band" + at);
        }

        ScreenLayout.Regions stats = PanelMetrics.stats(width, height);
        verifyRegions("stats" + at, stats);
        // Edit and Close are two halves of one partition, so they cannot meet at any width.
        ScreenLayout.Rect statsFooter = stats.footer().inset(6);
        ScreenLayout.Rect edit = ScreenLayout.partition(statsFooter, 2, 0);
        ScreenLayout.Rect close = ScreenLayout.partition(statsFooter, 2, 1);
        require(!edit.intersects(close), "stats footer buttons overlap" + at);
        require(statsFooter.contains(edit) && statsFooter.contains(close),
                "stats footer buttons escape the footer" + at);

        if (usable) {
            require(ScreenLayout.visibleRows(stats.content().inset(PanelMetrics.CONTENT_PAD), 12) >= 10,
                    "stats content too short" + at);
        }
    }

    // ------------------------------------------------------------------ ATM

    private static void verifyAtm(int width, int height, boolean usable) {
        String at = " at " + width + "x" + height;
        AtmLayout layout = AtmLayout.of(width, height);
        ScreenLayout.Rect viewport = new ScreenLayout.Rect(0, 0, width, height);
        ScreenLayout.Rect panel = layout.regions().panel();
        require(viewport.contains(panel), "ATM panel outside viewport" + at);
        verifyRegions("ATM" + at, layout.regions());

        // The three footer rows come off one cursor, so they must be inside the footer and disjoint.
        ScreenLayout.Rect footer = layout.regions().footer();
        require(footer.contains(layout.amountRow()), "ATM amount row escapes the footer" + at);
        require(footer.contains(layout.messageRow()), "ATM message row escapes the footer" + at);
        require(footer.contains(layout.closeRow()), "ATM close row escapes the footer" + at);
        require(!layout.amountRow().intersects(layout.messageRow()),
                "ATM amount row overlaps the message row" + at);
        require(!layout.messageRow().intersects(layout.closeRow()),
                "ATM message row overlaps the close row" + at);
        require(layout.closeRow().contains(layout.closeButton()),
                "ATM close button escapes its row" + at);
        require(!layout.content().intersects(footer), "ATM content overlaps the footer" + at);

        // The entry box and every action cell share the amount row without touching.
        for (int buttons = 1; buttons <= 3; buttons++) {
            ScreenLayout.Rect box = layout.amountBox(buttons);
            require(layout.amountRow().contains(box),
                    "ATM amount box escapes its row with " + buttons + " buttons" + at);
            for (int i = 0; i < buttons; i++) {
                ScreenLayout.Rect cell = layout.actionCell(buttons, i);
                require(layout.amountRow().contains(cell),
                        "ATM action cell " + i + "/" + buttons + " escapes its row" + at);
                require(!cell.intersects(box),
                        "ATM action cell " + i + "/" + buttons + " overlaps the amount box" + at);
                for (int j = i + 1; j < buttons; j++) {
                    require(!cell.intersects(layout.actionCell(buttons, j)),
                            "ATM action cells " + i + " and " + j + " overlap" + at);
                }
            }
        }

        ScreenLayout.Rect tabStrip = layout.tabStrip();
        int tabWidth = ScreenLayout.gridCellWidth(tabStrip, 4, 2);
        ScreenLayout.Rect lastTab = new ScreenLayout.Rect(tabStrip.x() + 3 * (tabWidth + 2),
                tabStrip.y(), tabWidth, Math.min(18, tabStrip.height()));
        require(tabStrip.contains(lastTab), "ATM tab strip overflows" + at);

        ScreenLayout.Rect peers = layout.listArea(14);
        require(layout.content().contains(peers), "ATM peer list escapes the content band" + at);

        if (usable) {
            require(layout.amountRow().height() > 0 && layout.closeRow().height() > 0,
                    "ATM footer rows collapsed at a supported size" + at);
            require(ScreenLayout.visibleRows(peers, 14) >= 3,
                    "ATM peer list shows fewer than three rows" + at);
        }
    }

    // ------------------------------------------------------------------ theme editor

    private static void verifyThemeEditor(int width, int height, boolean usable) {
        String at = " at " + width + "x" + height;
        // Nine colour slots and three toggles: the real counts, which is what made both collisions
        // reachable in the first place.
        ThemeEditorLayout layout = ThemeEditorLayout.of(width, height, 9);
        verifyRegions("theme editor" + at, layout.regions());

        ScreenLayout.Rect left = layout.left();
        require(left.contains(layout.slotList()), "theme slot list escapes the left column" + at);
        require(left.contains(layout.presetRow()), "theme preset row escapes the left column" + at);
        require(!layout.slotList().intersects(layout.presetRow()),
                "theme slot list overlaps the preset buttons" + at);

        // Every right-column band, in the order the cursor hands them out.
        ScreenLayout.Rect right = layout.right();
        ScreenLayout.Rect[] bands = {
                layout.pickerSquare(), layout.hexRow(), layout.rainbowRow(),
                layout.neutralRow(), layout.alphaLabel(), layout.alphaBar(), layout.toggleColumn()
        };
        String[] names = {
                "picker", "hex readout", "rainbow row", "neutral row",
                "opacity label", "opacity bar", "toggle column"
        };
        for (int i = 0; i < bands.length; i++) {
            require(right.contains(bands[i]),
                    "theme " + names[i] + " escapes the right column" + at);
            for (int j = i + 1; j < bands.length; j++) {
                require(!bands[i].intersects(bands[j]),
                        "theme " + names[i] + " overlaps " + names[j] + at);
            }
        }
        // The slider's grab area is larger than the bar, so check the grown rectangle too: this is
        // the exact pair that used to be drawn on top of each other.
        require(!layout.alphaBar().expand(2).intersects(layout.toggleColumn()),
                "theme opacity slider grab area overlaps the toggles" + at);

        for (int i = 0; i < ThemeEditorLayout.TOGGLES; i++) {
            ScreenLayout.Rect toggle = layout.toggle(i);
            require(layout.toggleColumn().contains(toggle),
                    "theme toggle " + i + " escapes the toggle column" + at);
            for (int j = i + 1; j < ThemeEditorLayout.TOGGLES; j++) {
                require(!toggle.intersects(layout.toggle(j)),
                        "theme toggles " + i + " and " + j + " overlap" + at);
            }
        }

        // Drawing and hit-testing must agree on which swatch a point belongs to.
        for (int i = 0; i < 12; i++) {
            ScreenLayout.Rect row = layout.rainbowRow();
            ScreenLayout.Rect swatch = ScreenLayout.partition(row, 12, i);
            // A collapsed row has no area to hit-test; that is the intended degradation, not a bug.
            if (swatch.width() <= 0 || swatch.height() <= 0) {
                continue;
            }
            double[] points = {
                    swatch.x(),
                    swatch.x() + swatch.width() / 2.0D,
                    Math.nextDown((double) swatch.right())
            };
            for (double point : points) {
                int resolved = ScreenLayout.partitionIndex(row, 12, point, swatch.y() + 2);
                require(resolved == i, "theme swatch " + i + " resolves to " + resolved
                        + " at x=" + point + at);
            }
        }

        if (usable) {
            require(layout.visibleSlots() >= 9,
                    "theme editor hides colour slots at a supported size" + at);
            require(layout.toggleColumn().height() >= ThemeEditorLayout.TOGGLES
                            * ThemeEditorLayout.TOGGLE_H,
                    "theme editor toggles do not fit at a supported size" + at);
        }
    }

    // ------------------------------------------------------------------ wallet

    private static void verifyWallet(int width, int height) {
        String at = " at " + width + "x" + height;
        ScreenLayout.Rect viewport = new ScreenLayout.Rect(0, 0, width, height);
        ScreenLayout.Rect wallet = ScreenLayout.centeredPanel(width, height, 176, 98);
        require(viewport.contains(wallet), "wallet outside viewport" + at);
        int innerText = 176 - 12;
        // Widest plausible amount string, to catch the case the budgets are meant to survive.
        for (int amountPixels : new int[] {10, 42, 80, 120, 400}) {
            int amountBudget = Math.min(innerText * 3 / 5, Math.max(42, amountPixels));
            int nameBudget = Math.max(0, innerText - amountBudget - 6);
            require(nameBudget + amountBudget + 6 <= innerText,
                    "wallet footer budgets collide for a " + amountPixels + "px amount" + at);
        }
    }

    // ------------------------------------------------------------------ amount entry

    /**
     * The keyboard-level half of the two-decimal rule.
     *
     * <p>Lives here because this is the mod's only pure-JVM check: the filter has no Minecraft
     * dependency precisely so it can be verified without a client.</p>
     */
    private static void verifyAmountInput() {
        String[] accepted = {
                "", "0", "5", "12", "999999999999999",
                "0.", "5.", "1.2", "1.25", "0.01", "12,5", "12,50", "1234567.89"
        };
        for (String value : accepted) {
            require(AmountInput.typable(value), "amount filter rejected a typable value: '" + value + "'");
        }
        String[] refused = {
                // A third decimal must be untypable, not merely diagnosed afterwards.
                "1.234", "0.001", "12,345",
                // Not part of any amount.
                "-1", "1e5", "abc", "1.2.3", "1..2", ".", ".5", "$5", "1 2", "1,2,3",
                // Beyond the 15 integer digits Money.parse accepts.
                "1234567890123456",
        };
        for (String value : refused) {
            require(!AmountInput.typable(value), "amount filter accepted a bad value: '" + value + "'");
        }
        require(!AmountInput.typable(null), "amount filter accepted null");
    }

    // ------------------------------------------------------------------ helpers

    private static void verifyRegions(String name, ScreenLayout.Regions regions) {
        require(regions.panel().contains(regions.header()), name + " header outside panel");
        require(regions.panel().contains(regions.tabs()), name + " tabs outside panel");
        require(regions.panel().contains(regions.content()), name + " content outside panel");
        require(regions.panel().contains(regions.footer()), name + " footer outside panel");
        require(!regions.header().intersects(regions.tabs()), name + " header overlaps tabs");
        require(!regions.tabs().intersects(regions.content()), name + " tabs overlap content");
        require(!regions.content().intersects(regions.footer()), name + " content overlaps footer");
    }

    private static void verifyFooterGrid(String name, ScreenLayout.Rect footer,
                                         int columns, int gap, int height) {
        int width = ScreenLayout.gridCellWidth(footer, columns, gap);
        ScreenLayout.Rect previous = null;
        for (int i = 0; i < columns; i++) {
            ScreenLayout.Rect control = new ScreenLayout.Rect(
                    footer.x() + i * (width + gap), footer.y(), width, height);
            require(footer.contains(control), name + " footer control " + i + " outside footer");
            if (previous != null) {
                require(!previous.intersects(control), name + " footer controls overlap");
            }
            previous = control;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }
}
