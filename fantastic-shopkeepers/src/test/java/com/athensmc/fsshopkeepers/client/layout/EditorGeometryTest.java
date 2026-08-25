package com.athensmc.fsshopkeepers.client.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The overlap guard for the editor.
 *
 * <p>Runs on a plain JVM as part of {@code check}, so a layout that would draw the footer over the last list row, push a
 * column off the panel, or let the two trade columns collide fails the build instead of being found in a screenshot.
 * Checked at fourteen window sizes, including several far smaller than anyone plays at, because a layout that survives
 * 320x240 survives everything above it.</p>
 *
 * <p>A {@code main} rather than a JUnit test, so it needs no test framework on the classpath of a mod that has no other
 * reason to carry one.</p>
 */
public final class EditorGeometryTest {

    private static final int[][] SIZES = {
            {320, 240}, {360, 200}, {400, 300}, {427, 240}, {480, 360}, {640, 480}, {854, 480},
            {1024, 768}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160},
    };

    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        for (int[] size : SIZES) {
            check(size[0], size[1]);
        }
        checkTabsFillBand();
        checkPanelIsCapped();

        if (!failures.isEmpty()) {
            System.err.println("La geometria del editor tiene " + failures.size() + " problemas:");
            for (String failure : failures) {
                System.err.println("  - " + failure);
            }
            throw new AssertionError("El editor solapa controles o los deja fuera del panel.");
        }
        System.out.println("Geometria del editor verificada en " + SIZES.length
                + " tamanos de ventana: sin solapamientos.");
    }

    private static void check(int width, int height) {
        String at = width + "x" + height;
        EditorGeometry geometry = new EditorGeometry(width, height);
        Rect screen = new Rect(0, 0, width, height);
        Rect panel = geometry.panel();

        if (!panel.within(screen)) {
            failures.add(at + ": el panel se sale de la pantalla " + panel);
        }

        // The bands must stack without touching.
        Map<String, Rect> bands = new LinkedHashMap<>();
        bands.put("titulo", geometry.titleBar());
        bands.put("pestanas", geometry.tabBand());
        bands.put("ayuda", geometry.helpLine());
        bands.put("cuerpo", geometry.body());
        bands.put("pie", geometry.footer());
        for (Map.Entry<String, Rect> band : bands.entrySet()) {
            if (band.getValue().isEmpty()) {
                continue;
            }
            if (!band.getValue().within(panel)) {
                failures.add(at + ": la banda " + band.getKey() + " se sale del panel " + band.getValue());
            }
        }
        assertNoOverlaps(bands, at + " bandas");

        // Tabs must tile their band.
        for (int count = 1; count <= 4; count++) {
            Map<String, Rect> tabs = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                tabs.put("pestana" + i, geometry.tab(i, count));
            }
            assertNoOverlaps(tabs, at + " " + count + " pestanas");
            for (Map.Entry<String, Rect> tab : tabs.entrySet()) {
                if (!tab.getValue().isEmpty() && !tab.getValue().within(geometry.tabBand())) {
                    failures.add(at + ": " + tab.getKey() + " se sale de su banda");
                }
            }
        }

        // The two trade columns must not collide, and both must stay in the body.
        Rect list = geometry.tradeListColumn();
        Rect detail = geometry.tradeDetailColumn();
        if (list.overlaps(detail)) {
            failures.add(at + ": las columnas de tratos se solapan (" + list + " / " + detail + ")");
        }
        if (!list.isEmpty() && !list.within(geometry.body())) {
            failures.add(at + ": la lista de tratos se sale del cuerpo");
        }
        if (!detail.isEmpty() && !detail.within(geometry.body())) {
            failures.add(at + ": el detalle del trato se sale del cuerpo");
        }
        if (detail.width() < 80) {
            failures.add(at + ": la columna de detalle queda inutilizable (" + detail.width() + "px)");
        }

        // Trade rows must not overlap each other, their icons, or the add/remove buttons.
        int visible = geometry.tradeVisibleRows();
        Map<String, Rect> rows = new LinkedHashMap<>();
        for (int slot = 0; slot < visible; slot++) {
            rows.put("fila" + slot, geometry.tradeRow(slot));
            rows.put("icono" + slot, geometry.tradeRowIcon(slot));
        }
        assertNoOverlaps(rows, at + " filas de tratos");
        Rect add = geometry.addTradeButton();
        Rect remove = geometry.removeTradeButton();
        if (add.overlaps(remove)) {
            failures.add(at + ": anadir y quitar se solapan");
        }
        for (int slot = 0; slot < visible; slot++) {
            Rect row = geometry.tradeRow(slot);
            if (row.overlaps(add) || row.overlaps(remove)) {
                failures.add(at + ": la fila " + slot + " se solapa con los botones de la lista");
            }
            if (!row.isEmpty() && !row.within(geometry.body())) {
                failures.add(at + ": la fila " + slot + " se sale del cuerpo");
            }
        }

        // The footer's three buttons.
        Map<String, Rect> footerButtons = new LinkedHashMap<>();
        footerButtons.put("guardar", geometry.saveButton());
        footerButtons.put("cerrar", geometry.closeButton());
        footerButtons.put("borrar", geometry.deleteButton());
        assertNoOverlaps(footerButtons, at + " botones del pie");
        for (Map.Entry<String, Rect> b : footerButtons.entrySet()) {
            if (!b.getValue().within(panel)) {
                failures.add(at + ": el boton " + b.getKey() + " se sale del panel " + b.getValue());
            }
        }
        // The footer must never sit on the body.
        for (Map.Entry<String, Rect> b : footerButtons.entrySet()) {
            if (b.getValue().overlaps(geometry.body())) {
                failures.add(at + ": el boton " + b.getKey() + " invade el cuerpo");
            }
        }

        // The appearance tab's columns.
        Rect mobs = geometry.mobListColumn();
        Rect variants = geometry.variantsColumn();
        if (mobs.overlaps(variants)) {
            failures.add(at + ": la lista de mobs se solapa con las variantes");
        }
        if (!mobs.isEmpty() && !mobs.within(geometry.body())) {
            failures.add(at + ": la lista de mobs se sale del cuerpo");
        }
        if (!variants.isEmpty() && !variants.within(geometry.body())) {
            failures.add(at + ": la columna de variantes se sale del cuerpo");
        }
        Rect nameField = geometry.nameField();
        Rect firstKind = geometry.kindButton(0, 3);
        if (nameField.overlaps(firstKind)) {
            failures.add(at + ": el campo de nombre se solapa con los botones de cuerpo");
        }
        if (firstKind.overlaps(mobs) || firstKind.overlaps(variants)) {
            failures.add(at + ": los botones de cuerpo invaden las columnas");
        }
        Map<String, Rect> kinds = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            kinds.put("cuerpo" + i, geometry.kindButton(i, 3));
        }
        assertNoOverlaps(kinds, at + " botones de cuerpo");
        Map<String, Rect> mobRows = new LinkedHashMap<>();
        for (int slot = 0; slot < geometry.mobVisibleRows(); slot++) {
            mobRows.put("mob" + slot, geometry.mobRow(slot));
        }
        assertNoOverlaps(mobRows, at + " filas de mobs");
        for (Map.Entry<String, Rect> row : mobRows.entrySet()) {
            if (!row.getValue().within(mobs)) {
                failures.add(at + ": " + row.getKey() + " se sale de su columna");
            }
            if (row.getValue().overlaps(geometry.mobScrollbar())) {
                failures.add(at + ": " + row.getKey() + " queda bajo la barra de desplazamiento");
            }
        }
        Map<String, Rect> variantRows = new LinkedHashMap<>();
        variantRows.put("cabecera", geometry.variantsHeader());
        for (int slot = 0; slot < geometry.variantVisibleRows(); slot++) {
            variantRows.put("variante" + slot, geometry.variantRow(slot));
        }
        assertNoOverlaps(variantRows, at + " filas de variantes");

        checkDetailBlocks(at, geometry);
        checkSettingBlocks(at, geometry);
    }

    /**
     * The detail column, block by block.
     *
     * <p>This is the part that was actually broken: the item icons, the choose buttons and the quantity steppers were
     * positioned by three different formulas and ended up on top of each other. Every piece of every block is checked
     * against every other piece here.</p>
     */
    private static void checkDetailBlocks(String at, EditorGeometry geometry) {
        Rect column = geometry.tradeDetailColumn();
        Map<String, Rect> pieces = new LinkedHashMap<>();
        for (int block = 0; block < 5; block++) {
            Rect label = geometry.detailLabel(block);
            Rect control = geometry.detailControl(block);
            if (label.overlaps(control)) {
                failures.add(at + ": en el bloque " + block + " la etiqueta se solapa con su control");
            }
            pieces.put("etiqueta" + block, label);
            pieces.put("icono" + block, geometry.detailIcon(block));
            pieces.put("boton" + block, geometry.detailControlAfterIconBeforeStepper(block));
            pieces.put("menos" + block, geometry.stepperMinus(block));
            pieces.put("cuenta" + block, geometry.stepperCount(block));
            pieces.put("mas" + block, geometry.stepperPlus(block));

            for (Rect piece : List.of(label, control, geometry.detailIcon(block),
                    geometry.detailControlAfterIcon(block), geometry.stepperPlus(block))) {
                if (!piece.isEmpty() && !piece.within(column)) {
                    failures.add(at + ": una pieza del bloque " + block + " se sale de la columna " + piece);
                }
            }
            // The choose button must survive: without it an admin cannot pick an item at all.
            if (!control.isEmpty() && geometry.detailControlAfterIconBeforeStepper(block).isEmpty()) {
                failures.add(at + ": el boton del bloque " + block + " no cabe junto a su stepper");
            }
        }
        assertNoOverlaps(pieces, at + " piezas de los bloques de detalle");

        Rect status = geometry.detailStatus(5);
        if (!status.isEmpty() && !status.within(column)) {
            failures.add(at + ": la linea de estado se sale de la columna");
        }
        if (status.overlaps(geometry.detailControl(4))) {
            failures.add(at + ": la linea de estado se solapa con el ultimo bloque");
        }
    }

    private static void checkSettingBlocks(String at, EditorGeometry geometry) {
        Map<String, Rect> pieces = new LinkedHashMap<>();
        for (int block = 0; block < 5; block++) {
            Rect label = geometry.settingLabel(block);
            Rect control = geometry.settingControl(block);
            if (label.overlaps(control)) {
                failures.add(at + ": en el ajuste " + block + " la etiqueta se solapa con su control");
            }
            pieces.put("etiqueta" + block, label);
            pieces.put("control" + block, control);
            for (Rect piece : List.of(label, control)) {
                if (!piece.isEmpty() && !piece.within(geometry.body())) {
                    failures.add(at + ": una pieza del ajuste " + block + " se sale del cuerpo");
                }
            }
        }
        assertNoOverlaps(pieces, at + " piezas de los ajustes");
    }

    /** Tabs should finish flush with their band, so the row does not look ragged. */
    private static void checkTabsFillBand() {
        for (int[] size : SIZES) {
            EditorGeometry geometry = new EditorGeometry(size[0], size[1]);
            for (int count = 1; count <= 4; count++) {
                Rect last = geometry.tab(count - 1, count);
                if (last.isEmpty()) {
                    continue;
                }
                if (last.right() != geometry.tabBand().right()) {
                    failures.add(size[0] + "x" + size[1] + ": con " + count
                            + " pestanas la ultima no llega al borde");
                }
            }
        }
    }

    /**
     * The panel must stop growing, and must stay centred.
     *
     * <p>Checked because the first version of this screen was full-screen, which is what made it look stretched and
     * unlike the rest of the Fantastic family on a large monitor.</p>
     */
    private static void checkPanelIsCapped() {
        EditorGeometry huge = new EditorGeometry(3840, 2160);
        if (huge.panelWidth() != EditorGeometry.MAX_PANEL_WIDTH) {
            failures.add("en 4K el panel no se queda en " + EditorGeometry.MAX_PANEL_WIDTH
                    + "px (mide " + huge.panelWidth() + ")");
        }
        if (huge.panelHeight() != EditorGeometry.MAX_PANEL_HEIGHT) {
            failures.add("en 4K el panel no se queda en " + EditorGeometry.MAX_PANEL_HEIGHT
                    + "px de alto (mide " + huge.panelHeight() + ")");
        }
        int expectedLeft = (3840 - EditorGeometry.MAX_PANEL_WIDTH) / 2;
        if (huge.leftPos() != expectedLeft) {
            failures.add("el panel no queda centrado en 4K");
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
