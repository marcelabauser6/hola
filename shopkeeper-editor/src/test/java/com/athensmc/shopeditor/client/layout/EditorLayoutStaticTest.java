package com.athensmc.shopeditor.client.layout;

import com.athensmc.shopeditor.client.layout.EditorLayout.HelpSplit;
import com.athensmc.shopeditor.client.layout.EditorLayout.Toolbar;
import com.athensmc.shopeditor.client.layout.ScreenLayout.Rect;
import com.athensmc.shopeditor.client.layout.ScreenLayout.Regions;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the editor's geometry at a spread of window sizes, without launching a client.
 *
 * <p>The sizes are logical GUI resolutions a real client produces: 320x240 is a small window at GUI scale 4,
 * 1280x720 a large one at scale 2. Both ends matter because the failure modes are opposite - the small end is
 * where bands collapse into each other, and the large end is where something fixed stops reaching the edge.</p>
 *
 * <p>"No overlaps" is a requirement here, not a nicety, so it is asserted rather than eyeballed: every band
 * against every other band, the help area against the content, and every click resolved back to the row or cell
 * it was drawn in.</p>
 */
public final class EditorLayoutStaticTest {

    private static final int[][] VIEWPORTS = {
            {320, 240}, {400, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540},
            {1280, 720}, {1920, 1080},
    };

    private static final List<String> FAILURES = new ArrayList<>();

    private EditorLayoutStaticTest() {
    }

    public static void main(String[] args) {
        for (int[] viewport : VIEWPORTS) {
            int width = viewport[0];
            int height = viewport[1];
            checkBands("lista de tratos", EditorLayout.tradeList(width, height), width, height);
            checkBands("selector de items", EditorLayout.itemPicker(width, height), width, height);
            checkHelpNeverOverlaps(width, height);
            checkTradeRowClicks(width, height);
            checkItemGridClicks(width, height);
            checkToolbarSplit(width, height);
        }
        checkHelpBecomesAColumnOnlyWhenItFits();

        if (!FAILURES.isEmpty()) {
            System.err.println("Editor layout check failed:");
            FAILURES.forEach(failure -> System.err.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("Editor layout OK: " + VIEWPORTS.length
                + " window sizes, no band, help, row or cell overlap.");
    }

    private static void checkBands(String screen, Regions regions, int width, int height) {
        String at = " (" + screen + " @ " + width + "x" + height + ")";
        Rect panel = regions.panel();
        if (panel.right() > width || panel.bottom() > height) {
            FAILURES.add("el panel se sale de la ventana" + at);
        }

        Rect[] bands = {regions.header(), regions.tabs(), regions.content(), regions.footer()};
        String[] names = {"cabecera", "barra", "contenido", "pie"};
        for (int i = 0; i < bands.length; i++) {
            if (!bands[i].isEmpty() && !panel.contains(bands[i])) {
                FAILURES.add(names[i] + " se sale del panel" + at);
            }
            for (int j = i + 1; j < bands.length; j++) {
                if (bands[i].intersects(bands[j])) {
                    FAILURES.add(names[i] + " se solapa con " + names[j] + at);
                }
            }
        }
        if (regions.content().isEmpty()) {
            FAILURES.add("el contenido se quedó sin altura" + at);
        }
    }

    private static void checkHelpNeverOverlaps(int width, int height) {
        String at = " (@ " + width + "x" + height + ")";
        for (Regions regions : new Regions[]{EditorLayout.tradeList(width, height),
                EditorLayout.itemPicker(width, height)}) {
            HelpSplit split = EditorLayout.withHelp(regions.content());
            if (split.main().intersects(split.help())) {
                FAILURES.add("la ayuda se solapa con el contenido" + at);
            }
            if (!split.main().isEmpty() && !regions.content().contains(split.main())) {
                FAILURES.add("el área de trabajo se sale de su banda" + at);
            }
            if (!split.help().isEmpty() && !regions.content().contains(split.help())) {
                FAILURES.add("la ayuda se sale de su banda" + at);
            }
            if (split.help().isEmpty()) {
                FAILURES.add("no se reservó sitio para las explicaciones" + at);
            }
            if (split.main().isEmpty()) {
                FAILURES.add("no quedó sitio para el contenido" + at);
            }
        }
    }

    /** A click must never resolve to a trade row that was not drawn. */
    private static void checkTradeRowClicks(int width, int height) {
        String at = " (@ " + width + "x" + height + ")";
        Rect area = EditorLayout.withHelp(EditorLayout.tradeList(width, height).content()).main();
        int fit = EditorLayout.tradeRows(area);
        if (fit <= 0) {
            FAILURES.add("no cabe ni una fila de trato" + at);
            return;
        }

        for (int i = 0; i < fit; i++) {
            Rect row = EditorLayout.tradeRow(area, i);
            if (!area.contains(row)) {
                FAILURES.add("la fila " + i + " se sale del área" + at);
            }
            if (EditorLayout.tradeRowAt(area, fit, row.x() + 1, row.y() + 1) != i) {
                FAILURES.add("un clic en la fila " + i + " no la selecciona" + at);
            }
            if (i > 0 && EditorLayout.tradeRow(area, i - 1).intersects(row)) {
                FAILURES.add("las filas " + (i - 1) + " y " + i + " se solapan" + at);
            }
        }

        double belowLast = area.y() + (double) fit * EditorLayout.TRADE_ROW_H + 0.5;
        if (belowLast < area.bottom()
                && EditorLayout.tradeRowAt(area, fit, area.x() + 1, belowLast) != -1) {
            FAILURES.add("un clic bajo la última fila selecciona algo" + at);
        }
        if (fit >= 2 && EditorLayout.tradeRowAt(area, 1, area.x() + 1,
                area.y() + EditorLayout.TRADE_ROW_H + 1) != -1) {
            FAILURES.add("un clic más allá de una lista corta selecciona algo" + at);
        }
    }

    /** The same for the item grid, where the slack at the right edge belongs to no cell. */
    private static void checkItemGridClicks(int width, int height) {
        String at = " (@ " + width + "x" + height + ")";
        Rect area = EditorLayout.withHelp(EditorLayout.itemPicker(width, height).content()).main();
        int perPage = EditorLayout.itemsPerPage(area);
        if (perPage <= 0) {
            FAILURES.add("no cabe ni una casilla de item" + at);
            return;
        }

        for (int index = 0; index < perPage; index++) {
            Rect cell = EditorLayout.gridCell(area, index);
            if (!area.contains(cell)) {
                FAILURES.add("la casilla " + index + " se sale del área" + at);
            }
            if (EditorLayout.gridCellAt(area, perPage, cell.x() + 1, cell.y() + 1) != index) {
                FAILURES.add("un clic en la casilla " + index + " no la selecciona" + at);
            }
            for (int other = 0; other < index; other++) {
                if (EditorLayout.gridCell(area, other).intersects(cell)) {
                    FAILURES.add("las casillas " + other + " y " + index + " se solapan" + at);
                }
            }
        }

        // Fewer items than the page holds: the empty cells belong to nobody.
        if (perPage >= 2 && EditorLayout.gridCellAt(area, 1,
                EditorLayout.gridCell(area, 1).x() + 1,
                EditorLayout.gridCell(area, 1).y() + 1) != -1) {
            FAILURES.add("un clic en una casilla vacía selecciona un item" + at);
        }
    }

    private static void checkToolbarSplit(int width, int height) {
        String at = " (@ " + width + "x" + height + ")";
        Rect band = EditorLayout.itemPicker(width, height).tabs();
        Toolbar toolbar = EditorLayout.toolbar(band, 120);

        if (toolbar.sourceSwitch().intersects(toolbar.search())) {
            FAILURES.add("el interruptor de origen se solapa con el buscador" + at);
        }
        if (!toolbar.sourceSwitch().isEmpty() && !band.contains(toolbar.sourceSwitch())) {
            FAILURES.add("el interruptor se sale de la barra" + at);
        }
        if (!toolbar.search().isEmpty() && !band.contains(toolbar.search())) {
            FAILURES.add("el buscador se sale de la barra" + at);
        }
        if (toolbar.search().width() <= 0) {
            FAILURES.add("el buscador se quedó sin ancho" + at);
        }
    }

    private static void checkHelpBecomesAColumnOnlyWhenItFits() {
        HelpSplit wide = EditorLayout.withHelp(new Rect(0, 0, 900, 300));
        if (!wide.helpIsColumn()) {
            FAILURES.add("una banda de 900px debería poner la ayuda en columna");
        }
        if (wide.help().width() > EditorLayout.HELP_COLUMN_MAX) {
            FAILURES.add("la columna de ayuda pasó su tope: " + wide.help().width());
        }
        if (wide.main().width() < wide.help().width()) {
            FAILURES.add("la ayuda es más ancha que el contenido que acompaña");
        }

        HelpSplit narrow = EditorLayout.withHelp(new Rect(0, 0, 200, 300));
        if (narrow.helpIsColumn()) {
            FAILURES.add("una banda de 200px es demasiado estrecha para una columna");
        }
        if (narrow.help().width() != narrow.main().width()) {
            FAILURES.add("como banda, la ayuda debería ocupar todo el ancho");
        }
        if (narrow.main().intersects(narrow.help())) {
            FAILURES.add("la banda de ayuda se solapa con el contenido");
        }

        // A band with almost no height degrades instead of producing negative rectangles.
        HelpSplit tiny = EditorLayout.withHelp(new Rect(0, 0, 40, 10));
        if (tiny.main().intersects(tiny.help())) {
            FAILURES.add("una banda colapsada produjo áreas solapadas");
        }
    }
}
