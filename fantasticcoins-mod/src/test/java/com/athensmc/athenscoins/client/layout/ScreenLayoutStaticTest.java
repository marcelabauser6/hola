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
            verifyHologramEditorLook(viewport[0], viewport[1], true);
        }
        // Same invariants, minus the "usable" requirements, at sizes nothing has to support.
        for (int[] viewport : CRAMPED_VIEWPORTS) {
            verifyStandardScreens(viewport[0], viewport[1], false);
            verifyAtm(viewport[0], viewport[1], false);
            verifyHologramEditorLook(viewport[0], viewport[1], false);
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
        // The central bank's footer is two rows now - controls, then the feedback line with Close on its
        // right - because the content moved into tabs and the fourth row of buttons went with it.
        ScreenLayout.Rect centralFooter = central.footer().inset(6);
        ScreenLayout.Rect centralMessage = new ScreenLayout.Rect(centralFooter.x(),
                centralFooter.y() + 22, centralFooter.width(), 10);
        int centralButton = Math.min(18, centralFooter.height());
        ScreenLayout.Rect centralClose = new ScreenLayout.Rect(
                centralFooter.right() - Math.min(82, centralFooter.width()),
                centralMessage.bottom() + 2, Math.min(82, centralFooter.width()), centralButton);
        ScreenLayout.Rect centralControls = new ScreenLayout.Rect(centralFooter.x(),
                centralFooter.y(), centralFooter.width(), centralButton);
        require(!centralControls.intersects(centralClose),
                "central controls overlap the close button" + at);
        if (usable) {
            require(central.footer().contains(centralClose),
                    "central close button escapes the footer band" + at);
            // Three tabs across the strip, and the content band has to hold a list worth reading.
            ScreenLayout.Rect centralTabs = central.tabs().inset(4);
            int centralTabCell = ScreenLayout.gridCellWidth(centralTabs, 3, 4);
            ScreenLayout.Rect lastCentralTab = new ScreenLayout.Rect(
                    centralTabs.x() + (centralTabCell + 4) * 2, centralTabs.y(),
                    centralTabCell, Math.min(18, centralTabs.height()));
            require(centralTabs.contains(lastCentralTab), "central tab strip overflows" + at);
            require(ScreenLayout.visibleRows(
                            central.content().inset(PanelMetrics.CONTENT_PAD), 14) >= 5,
                    "central content too short for a bank list" + at);
        }

        // The hologram editor. Save, Reset and Close are three cells of one grid, and the feedback line
        // sits between that row and the bottom of the band.
        ScreenLayout.Regions editor = PanelMetrics.stats(width, height);
        verifyRegions("hologram editor" + at, editor);
        ScreenLayout.Rect editorFooter = editor.footer().inset(6);
        int cell = ScreenLayout.gridCellWidth(editorFooter, 3, 4);
        ScreenLayout.Rect save = new ScreenLayout.Rect(editorFooter.x(), editorFooter.y(), cell, 18);
        ScreenLayout.Rect reset = new ScreenLayout.Rect(editorFooter.x() + cell + 4,
                editorFooter.y(), cell, 18);
        int closeWidth = Math.min(82, cell);
        ScreenLayout.Rect close = new ScreenLayout.Rect(editorFooter.right() - closeWidth,
                editorFooter.y(), closeWidth, 18);
        require(!save.intersects(reset), "editor Save and Reset overlap" + at);
        require(!reset.intersects(close), "editor Reset and Close overlap" + at);

        // Three columns: lines, metrics and the live preview. They must tile the body without touching,
        // or the preview would sit on top of the palette it is supposed to be showing the result of.
        ScreenLayout.Rect body = editor.content().inset(PanelMetrics.CONTENT_PAD);
        ScreenLayout.Rect first = ScreenLayout.column(body, 3, 10, 0);
        ScreenLayout.Rect second = ScreenLayout.column(body, 3, 10, 1);
        ScreenLayout.Rect third = ScreenLayout.column(body, 3, 10, 2);
        require(!first.intersects(second) && !second.intersects(third),
                "editor columns overlap" + at);
        require(body.contains(first) && body.contains(third),
                "editor columns escape the content band" + at);

        if (usable) {
            require(ScreenLayout.visibleRows(body, 12) >= 10, "editor content too short" + at);
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

    // ------------------------------------------------------------------ hologram editor

    /**
     * The appearance column of the hologram editor.
     *
     * <p>Inherited from the colour editor this replaced, and kept for the same reason: that screen once
     * shipped with its opacity slider drawn on top of the buttons under it, with both reacting to one
     * click, at any window shorter than the developer's. The bands are handed out by a cursor that never
     * moves backwards, and this is what proves it.</p>
     */
    private static void verifyHologramEditorLook(int width, int height, boolean usable) {
        String at = " at " + width + "x" + height;
        // Five colour slots: the real count, which is what decides how much room is left for the picker.
        ScreenLayout.Regions regions = PanelMetrics.stats(width, height);
        ScreenLayout.Rect body = regions.content().inset(PanelMetrics.CONTENT_PAD);
        ScreenLayout.Rect leftColumn = ScreenLayout.column(body, 3, 10, 0);
        HologramEditorLayout layout = HologramEditorLayout.of(leftColumn, 5);

        ScreenLayout.Rect[] bands = {
                layout.colorList(), layout.pickerSquare(), layout.hexRow(), layout.alphaBar()
        };
        String[] names = { "colour list", "picker square", "hex readout", "opacity bar" };
        for (int i = 0; i < bands.length; i++) {
            require(leftColumn.contains(bands[i]),
                    "hologram " + names[i] + " escapes the appearance column" + at);
            for (int j = i + 1; j < bands.length; j++) {
                require(!bands[i].intersects(bands[j]),
                        "hologram " + names[i] + " overlaps " + names[j] + at);
            }
        }

        // The picker draws its hue strip below its square, at square bottom + GAP. Nothing allocated
        // afterwards may land on it.
        ScreenLayout.Rect hueStrip = new ScreenLayout.Rect(layout.pickerSquare().x(),
                layout.pickerSquare().bottom() + HologramEditorLayout.GAP,
                layout.pickerSquare().width(), layout.hueHeight());
        require(!hueStrip.intersects(layout.hexRow()), "hologram hue strip overlaps the hex row" + at);
        require(!hueStrip.intersects(layout.alphaBar()),
                "hologram hue strip overlaps the opacity bar" + at);

        // The slider's grab area is larger than the bar, so check the grown rectangle too - but only
        // where both bands are actually drawn. A collapsed band has nothing to click, so a grab area
        // reaching over it is the intended degradation at a size nothing has to support, not a bug.
        if (!layout.alphaBar().isEmpty() && !layout.hexRow().isEmpty()) {
            require(!layout.alphaBar().expand(2).intersects(layout.hexRow()),
                    "hologram opacity grab area overlaps the hex row" + at);
        }

        // Drawing and hit-testing must agree on which colour row a point belongs to.
        for (int i = 0; i < Math.min(5, layout.visibleSlots()); i++) {
            ScreenLayout.Rect slot = layout.slot(i);
            require(layout.colorList().contains(slot),
                    "hologram colour slot " + i + " escapes its list" + at);
            int resolved = (int) ((slot.y() + 1 - layout.colorList().y())
                    / HologramEditorLayout.SLOT_H);
            require(resolved == i, "hologram colour slot " + i + " resolves to " + resolved + at);
        }

        if (usable) {
            require(layout.visibleSlots() >= 5,
                    "hologram editor hides colour slots at a supported size" + at);
            require(layout.pickerSquare().height() >= HologramEditorLayout.PICKER_MIN,
                    "hologram colour picker collapsed at a supported size" + at);
        }
    }

    // ------------------------------------------------------------------ wallet

    private static void verifyWallet(int width, int height) {
        String at = " at " + width + "x" + height;
        ScreenLayout.Rect viewport = new ScreenLayout.Rect(0, 0, width, height);
        // The wallet is the drawing and nothing else - no panel, no title bar, no footer - so the only
        // geometric claim to check is that the artwork lands inside the window.
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

    /**
     * The footer controls a screen actually draws stay inside the band and clear of each other.
     *
     * <p>The height is clamped the way every screen clamps it - {@code Math.min(height, band)} - rather
     * than asserted at a fixed 16. Asserting the unclamped height tested a control none of the screens
     * would draw: on a band too short for it they all shrink the button, so the fixed number turned a
     * graceful degradation into a failure the moment a tab strip took a few pixels off the footer.</p>
     */
    private static void verifyFooterGrid(String name, ScreenLayout.Rect footer,
                                         int columns, int gap, int height) {
        if (footer.isEmpty()) {
            return;
        }
        int drawn = Math.min(height, footer.height());
        int width = ScreenLayout.gridCellWidth(footer, columns, gap);
        ScreenLayout.Rect previous = null;
        for (int i = 0; i < columns; i++) {
            ScreenLayout.Rect control = new ScreenLayout.Rect(
                    footer.x() + i * (width + gap), footer.y(), width, drawn);
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
