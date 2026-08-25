package com.athensmc.fsshopkeepers.client.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The overlap guard for the editor.
 *
 * <p>Runs on a plain JVM as part of {@code check}, so a screen that would draw a price field over a delete button, or
 * push the footer off the bottom of the window, fails the build instead of being discovered by looking at it. Every
 * band, every tab, every row, every cell within a row, the mob grid, the settings fields and the footer are checked at
 * fourteen window sizes, including sizes far narrower and shorter than anyone would play at.</p>
 *
 * <p>A {@code main} rather than a JUnit test, so it needs no test framework on the classpath of a mod that has no
 * other reason to carry one.</p>
 */
public final class EditorLayoutTest {

    /**
     * Window sizes to check.
     *
     * <p>The small ones are the point. 320x240 is smaller than Minecraft's minimum window, and a layout that behaves
     * there behaves at every real size; the large ones catch the opposite mistake of controls that stretch apart and
     * leave a row unclickable.</p>
     */
    private static final int[][] SIZES = {
            {320, 240}, {360, 200}, {400, 300}, {427, 240}, {480, 360}, {640, 480}, {854, 480},
            {1024, 768}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160},
    };

    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        for (int[] size : SIZES) {
            check(size[0], size[1]);
        }
        checkRowCellsShrinkGracefully();
        checkTabsFillBand();
        checkScrollHandleStaysInTrack();

        if (!failures.isEmpty()) {
            System.err.println("El layout del editor tiene " + failures.size() + " problemas:");
            for (String failure : failures) {
                System.err.println("  - " + failure);
            }
            throw new AssertionError("El layout del editor solapa controles o los deja fuera de pantalla.");
        }
        System.out.println("Layout del editor verificado en " + SIZES.length
                + " tamanos de ventana: sin solapamientos.");
    }

    private static void check(int width, int height) {
        String at = width + "x" + height;
        EditorLayout layout = new EditorLayout(width, height);

        Map<String, Rect> bands = new LinkedHashMap<>();
        bands.put("cabecera", layout.header());
        bands.put("pestanas", layout.tabs());
        bands.put("barra-acciones", layout.actionBar());
        bands.put("lista", layout.rowsArea());
        bands.put("ayuda", layout.help());
        bands.put("pie", layout.footer());
        assertNoOverlaps(bands, at + " bandas");

        Rect screen = new Rect(0, 0, width, height);
        for (Map.Entry<String, Rect> band : bands.entrySet()) {
            if (!band.getValue().isEmpty() && !band.getValue().within(screen)) {
                failures.add(at + ": la banda " + band.getKey() + " se sale de la pantalla " + band.getValue());
            }
        }

        // Tabs: three for a player shop, and they must tile their band without touching.
        for (int count : new int[] {1, 2, 3, 4, 5}) {
            Map<String, Rect> tabRects = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                tabRects.put("pestana" + i, layout.tab(i, count));
            }
            assertNoOverlaps(tabRects, at + " " + count + " pestanas");
            for (Map.Entry<String, Rect> tab : tabRects.entrySet()) {
                if (!tab.getValue().isEmpty() && !tab.getValue().within(layout.tabs())) {
                    failures.add(at + ": " + tab.getKey() + " se sale de su banda");
                }
            }
        }

        // Rows must not overlap each other and must stay inside the list.
        int visible = layout.visibleRows();
        Map<String, Rect> rows = new LinkedHashMap<>();
        for (int i = 0; i < visible; i++) {
            rows.put("fila" + i, layout.row(i));
        }
        assertNoOverlaps(rows, at + " filas");
        for (Map.Entry<String, Rect> row : rows.entrySet()) {
            if (!row.getValue().isEmpty() && !row.getValue().within(layout.rowsArea())) {
                failures.add(at + ": " + row.getKey() + " se sale de la lista");
            }
        }

        // The cells inside a row are where overlaps actually happen.
        if (visible > 0) {
            Rect row = layout.row(0);
            EditorLayout.TradeRowCells cells = layout.tradeRow(row);
            Map<String, Rect> cellRects = new LinkedHashMap<>();
            cellRects.put("icono", cells.icon());
            cellRects.put("nombre", cells.name());
            cellRects.put("menos", cells.minus());
            cellRects.put("cantidad", cells.count());
            cellRects.put("mas", cells.plus());
            cellRects.put("precio", cells.price());
            cellRects.put("pago1", cells.pay1());
            cellRects.put("pago2", cells.pay2());
            cellRects.put("borrar", cells.delete());
            assertNoOverlaps(cellRects, at + " celdas de fila");
            for (Map.Entry<String, Rect> cell : cellRects.entrySet()) {
                if (!cell.getValue().isEmpty() && !cell.getValue().within(row)) {
                    failures.add(at + ": la celda " + cell.getKey() + " se sale de su fila "
                            + cell.getValue() + " vs " + row);
                }
            }
            // The price field is the one control that must never disappear, however narrow the window.
            if (cells.price().isEmpty()) {
                failures.add(at + ": el campo de precio desaparecio");
            }
            if (cells.delete().isEmpty()) {
                failures.add(at + ": el boton de borrar desaparecio");
            }

            // A row must not run under the scrollbar.
            Rect scrollbar = layout.scrollbar(visible + 10);
            if (!scrollbar.isEmpty() && scrollbar.overlaps(row)) {
                failures.add(at + ": la barra de desplazamiento tapa la fila");
            }
        }

        // The mob grid.
        Map<String, Rect> mobCells = new LinkedHashMap<>();
        int mobTotal = layout.mobColumns() * layout.mobRows();
        for (int i = 0; i < mobTotal; i++) {
            mobCells.put("mob" + i, layout.mobCell(i));
        }
        assertNoOverlaps(mobCells, at + " rejilla de mobs");
        for (Map.Entry<String, Rect> cell : mobCells.entrySet()) {
            if (!cell.getValue().isEmpty() && !cell.getValue().within(layout.rowsArea())) {
                failures.add(at + ": " + cell.getKey() + " se sale de la lista");
            }
        }

        // Settings rows: label and control must not collide.
        for (int i = 0; i < Math.min(visible, 6); i++) {
            EditorLayout.SettingRow setting = layout.settingRow(i);
            if (setting.label().overlaps(setting.control())) {
                failures.add(at + ": la etiqueta y el control del ajuste " + i + " se solapan");
            }
            Rect row = layout.row(i);
            if (!setting.control().isEmpty() && !setting.control().within(row)) {
                failures.add(at + ": el control del ajuste " + i + " se sale de su fila");
            }
        }

        // Footer buttons.
        EditorLayout.FooterButtons buttons = layout.footerButtons();
        Map<String, Rect> footerRects = new LinkedHashMap<>();
        footerRects.put("guardar", buttons.save());
        footerRects.put("cancelar", buttons.cancel());
        footerRects.put("borrar", buttons.delete());
        footerRects.put("saldo", buttons.balance());
        assertNoOverlaps(footerRects, at + " pie");
        if (buttons.save().isEmpty()) {
            failures.add(at + ": el boton de guardar desaparecio");
        }

        // The action bar's two controls.
        if (!layout.actionBar().isEmpty()) {
            Rect add = layout.addTradeButton();
            Rect countLabel = layout.tradeCountLabel();
            if (add.overlaps(countLabel)) {
                failures.add(at + ": anadir-trato se solapa con el contador");
            }
        }
    }

    /**
     * The price field must survive being squeezed, and the name must be what gives way.
     *
     * <p>Checked as a rule of its own rather than inferred from the overlap results, because "nothing overlaps" is
     * also true of a layout that has quietly dropped half its controls.</p>
     */
    private static void checkRowCellsShrinkGracefully() {
        boolean sawNameDropped = false;
        for (int width = 200; width <= 900; width += 10) {
            EditorLayout layout = new EditorLayout(width, 600);
            if (layout.visibleRows() == 0) {
                continue;
            }
            Rect row = layout.row(0);
            EditorLayout.TradeRowCells cells = layout.tradeRow(row);
            if (cells.price().isEmpty()) {
                failures.add("ancho " + width + ": el campo de precio se perdio al estrechar");
            }
            if (cells.name().isEmpty()) {
                sawNameDropped = true;
            }
            if (!cells.name().isEmpty() && cells.name().overlaps(cells.minus())) {
                failures.add("ancho " + width + ": el nombre invade el boton menos");
            }
        }
        if (!sawNameDropped) {
            failures.add("el nombre nunca se oculta: la fila no se adapta a ventanas estrechas");
        }
    }

    /** Tabs should end flush with their band, so the row does not look ragged. */
    private static void checkTabsFillBand() {
        for (int[] size : SIZES) {
            EditorLayout layout = new EditorLayout(size[0], size[1]);
            for (int count = 1; count <= 4; count++) {
                Rect last = layout.tab(count - 1, count);
                if (last.isEmpty()) {
                    continue;
                }
                if (last.right() != layout.tabs().right()) {
                    failures.add(size[0] + "x" + size[1] + ": con " + count
                            + " pestanas la ultima no llega al borde (" + last.right() + " vs "
                            + layout.tabs().right() + ")");
                }
            }
        }
    }

    /** The scroll handle must stay inside its track at both ends of the scroll range. */
    private static void checkScrollHandleStaysInTrack() {
        EditorLayout layout = new EditorLayout(854, 480);
        int total = layout.visibleRows() + 25;
        Rect track = layout.scrollbar(total);
        if (track.isEmpty()) {
            failures.add("con 25 filas extra no aparecio la barra de desplazamiento");
            return;
        }
        for (int first = 0; first <= total - layout.visibleRows(); first++) {
            Rect handle = layout.scrollHandle(total, first);
            if (!handle.within(track)) {
                failures.add("el tirador de la barra se sale del carril en la posicion " + first);
                return;
            }
        }
    }

    private static void assertNoOverlaps(Map<String, Rect> rects, String context) {
        List<Map.Entry<String, Rect>> entries = new ArrayList<>(rects.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Rect a = entries.get(i).getValue();
                Rect b = entries.get(j).getValue();
                if (a.overlaps(b)) {
                    failures.add(context + ": " + entries.get(i).getKey() + " se solapa con "
                            + entries.get(j).getKey() + " (" + a + " / " + b + ")");
                }
            }
        }
    }
}
