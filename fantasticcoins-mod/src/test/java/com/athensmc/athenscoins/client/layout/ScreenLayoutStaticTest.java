package com.athensmc.athenscoins.client.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Static geometry verification. Runs on a plain JVM; it never starts Minecraft, a client or a
 * server. Execute with {@code ./gradlew verifyScreenLayouts}.
 */
public final class ScreenLayoutStaticTest {
    private static final int[][] VIEWPORTS = {
            {320, 240}, {426, 240}, {640, 360}, {854, 480}
    };
    private static final List<String> failures = new ArrayList<>();

    private ScreenLayoutStaticTest() {
    }

    public static void main(String[] args) {
        for (int[] viewport : VIEWPORTS) {
            verifyStandardScreens(viewport[0], viewport[1]);
            verifyWallet(viewport[0], viewport[1]);
            verifyAtm(viewport[0], viewport[1]);
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Screen layout failures:\n - " + String.join("\n - ", failures));
        }
        System.out.println("Screen layouts verified for " + VIEWPORTS.length
                + " logical GUI resolutions (including common GUI-scale viewports).");
    }

    private static void verifyStandardScreens(int width, int height) {
        ScreenLayout.Rect viewport = new ScreenLayout.Rect(0, 0, width, height);
        ScreenLayout.Rect panel = ScreenLayout.centeredPanel(width, height, 340, 232);
        require(viewport.contains(panel), "standard panel outside " + width + "x" + height);

        ScreenLayout.Regions terminal = ScreenLayout.regions(panel, 20, 22, 28);
        verifyRegions("terminal", terminal);
        ScreenLayout.Rect tabs = terminal.tabs().inset(4);
        int tabWidth = ScreenLayout.gridCellWidth(tabs, 5, 2);
        ScreenLayout.Rect lastTab = new ScreenLayout.Rect(
                tabs.x() + 4 * (tabWidth + 2), tabs.y(), tabWidth, Math.min(18, tabs.height()));
        require(tabs.contains(lastTab), "terminal tabs overflow at " + width + "x" + height);
        ScreenLayout.Rect terminalContent = terminal.content().inset(8);
        ScreenLayout.Rect settingsGrid = new ScreenLayout.Rect(terminalContent.x(),
                terminalContent.y() + 40, terminalContent.width(), terminalContent.height() - 60);
        int settingWidth = ScreenLayout.gridCellWidth(settingsGrid, 2, 4);
        ScreenLayout.Rect lastSetting = new ScreenLayout.Rect(settingsGrid.x() + settingWidth + 4,
                settingsGrid.y() + 3 * 18, settingWidth, 16);
        require(terminalContent.contains(lastSetting), "terminal setting outside content");
        require(!lastSetting.intersects(terminal.footer()), "terminal setting overlaps footer");

        ScreenLayout.Regions account = ScreenLayout.regions(panel, 20, 0, 54);
        verifyRegions("account", account);
        ScreenLayout.Columns accountColumns = ScreenLayout.columns(account.content().inset(8), 10);
        require(!accountColumns.first().intersects(accountColumns.second()), "account columns overlap");
        verifyFooterGrid("account", account.footer().inset(6), 4, 4, 16);

        ScreenLayout.Regions central = ScreenLayout.regions(panel, 20, 0, 70);
        verifyRegions("central", central);
        ScreenLayout.Rect centralContent = central.content().inset(8);
        ScreenLayout.Rect bankList = new ScreenLayout.Rect(centralContent.x(), centralContent.y() + 58,
                centralContent.width(), Math.max(0, centralContent.height() - 72));
        require(centralContent.contains(bankList), "central list outside content");
        require(!bankList.intersects(central.footer()), "central list overlaps controls");
        require(ScreenLayout.visibleRows(bankList, 14) >= 3,
                "central list has fewer than three visible rows at " + width + "x" + height);
        verifyFooterGrid("central", central.footer().inset(6), 3, 4, 16);

        ScreenLayout.Regions stats = ScreenLayout.regions(panel, 20, 22, 28);
        verifyRegions("stats", stats);
        require(ScreenLayout.visibleRows(stats.content().inset(8), 12) >= 10,
                "stats content too short at " + width + "x" + height);

        ScreenLayout.Regions editor = ScreenLayout.regions(panel, 20, 0, 28);
        verifyRegions("theme editor", editor);
        ScreenLayout.Columns editorColumns = ScreenLayout.columns(editor.content().inset(8), 10);
        ScreenLayout.Rect right = editorColumns.second();
        int pickerHeight = Math.max(36, Math.min(48, right.height() - 120));
        ScreenLayout.Rect picker = new ScreenLayout.Rect(right.x(), right.y() + 1,
                right.width(), pickerHeight + 4 + 8);
        int rainbowY = picker.bottom() + 14;
        ScreenLayout.Rect rainbow = new ScreenLayout.Rect(right.x(), rainbowY, right.width(), 8);
        ScreenLayout.Rect neutrals = new ScreenLayout.Rect(right.x(), rainbowY + 10, right.width(), 8);
        ScreenLayout.Rect alpha = new ScreenLayout.Rect(right.x(), rainbowY + 30, right.width(), 8);
        ScreenLayout.Rect toggles = new ScreenLayout.Rect(right.x(), right.bottom() - 47,
                right.width(), 47);
        require(right.contains(picker) && right.contains(rainbow) && right.contains(neutrals)
                        && right.contains(alpha) && right.contains(toggles),
                "theme editor controls escape right column");
        require(!picker.intersects(rainbow) && !rainbow.intersects(neutrals)
                        && !neutrals.intersects(alpha) && !alpha.intersects(toggles),
                "theme editor picker/swatches/alpha/toggles overlap");
        for (int i = 0; i < 12; i++) {
            ScreenLayout.Rect swatch = ScreenLayout.partition(rainbow, 12, i);
            double[] points = {
                    swatch.x(),
                    swatch.x() + swatch.width() / 2.0D,
                    Math.nextDown((double) swatch.right())
            };
            for (double point : points) {
                int resolved = ScreenLayout.partitionIndex(rainbow, 12, point, swatch.y() + 4);
                require(resolved == i, "theme editor swatch " + i + " resolves to " + resolved
                        + " at x=" + point);
            }
        }
    }

    private static void verifyWallet(int width, int height) {
        ScreenLayout.Rect viewport = new ScreenLayout.Rect(0, 0, width, height);
        ScreenLayout.Rect wallet = ScreenLayout.centeredPanel(width, height, 176, 98);
        require(viewport.contains(wallet), "wallet outside viewport");
        int innerText = 176 - 12;
        int amountBudget = Math.min(innerText * 3 / 5, Math.max(42, 80));
        int nameBudget = Math.max(24, innerText - amountBudget - 6);
        require(nameBudget + amountBudget + 6 <= innerText,
                "wallet footer text budgets collide");
    }

    private static void verifyAtm(int width, int height) {
        ScreenLayout.Rect viewport = new ScreenLayout.Rect(0, 0, width, height);
        ScreenLayout.Rect atm = ScreenLayout.centeredPanel(width, height, 248, 198);
        require(viewport.contains(atm), "ATM outside viewport");
        ScreenLayout.Rect transferBand = new ScreenLayout.Rect(atm.x() + 5, atm.y() + 152, 237, 20);
        ScreenLayout.Rect infoFooter = new ScreenLayout.Rect(atm.x() + 5, atm.y() + 175, 237, 20);
        ScreenLayout.Rect transferButtons = new ScreenLayout.Rect(atm.x() + 8, atm.y() + 152, 228, 16);
        ScreenLayout.Rect footerText = new ScreenLayout.Rect(atm.x() + 9, atm.y() + 177, 230, 18);
        require(transferBand.contains(transferButtons), "ATM transfer buttons leave their band");
        require(infoFooter.contains(footerText), "ATM footer text leaves information band");
        require(!transferButtons.intersects(footerText), "ATM transfer buttons overlap footer text");
    }

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
        for (int i = 0; i < columns; i++) {
            ScreenLayout.Rect control = new ScreenLayout.Rect(
                    footer.x() + i * (width + gap), footer.y(), width, height);
            require(footer.contains(control), name + " footer control outside panel");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }
}
