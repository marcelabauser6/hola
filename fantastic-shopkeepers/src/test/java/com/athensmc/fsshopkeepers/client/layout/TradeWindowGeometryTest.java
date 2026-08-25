package com.athensmc.fsshopkeepers.client.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The guard on the buying window.
 *
 * <p>Two things are checked, and both were reported as broken by hand before this existed. That the item hit areas are the
 * item's own 16 pixels and nothing wider, so a tooltip cannot cover the screen when the cursor merely crosses a row. And
 * that the magnified window always fits on the screen it is drawn on, so making it bigger cannot push it off an edge.</p>
 */
public final class TradeWindowGeometryTest {

    private static final int[][] SIZES = {
            {320, 240}, {427, 240}, {480, 360}, {640, 480}, {854, 480}, {1024, 768}, {1280, 720},
            {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160},
    };

    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        for (int[] size : SIZES) {
            check(size[0], size[1]);
        }
        checkScaleBounds();

        if (!failures.isEmpty()) {
            System.err.println("La ventana de compra tiene " + failures.size() + " problemas:");
            for (String failure : failures) {
                System.err.println("  - " + failure);
            }
            throw new AssertionError("La ventana de compra solapa zonas o se sale de la pantalla.");
        }
        System.out.println("Ventana de compra verificada en " + SIZES.length
                + " tamanos: hitboxes exactas y ventana dentro de pantalla.");
    }

    private static void check(int screenWidth, int screenHeight) {
        String at = screenWidth + "x" + screenHeight;
        float scale = TradeWindowGeometry.scaleFor(screenWidth, screenHeight);
        int leftPos = TradeWindowGeometry.leftPos(screenWidth, scale);
        int topPos = TradeWindowGeometry.topPos(screenHeight, scale);

        // The window, once magnified, must still be on screen.
        float drawnLeft = leftPos * scale;
        float drawnTop = topPos * scale;
        float drawnRight = (leftPos + TradeWindowGeometry.WINDOW_WIDTH) * scale;
        float drawnBottom = (topPos + TradeWindowGeometry.WINDOW_HEIGHT) * scale;
        if (drawnLeft < -0.5F || drawnTop < -0.5F) {
            failures.add(at + ": la ventana empieza fuera de la pantalla (" + drawnLeft + ", " + drawnTop + ")");
        }
        if (drawnRight > screenWidth + 0.5F || drawnBottom > screenHeight + 0.5F) {
            failures.add(at + ": la ventana se sale por abajo o por la derecha ("
                    + drawnRight + " > " + screenWidth + " o " + drawnBottom + " > " + screenHeight + ")");
        }

        Rect window = new Rect(leftPos, topPos, TradeWindowGeometry.WINDOW_WIDTH,
                TradeWindowGeometry.WINDOW_HEIGHT);

        for (int slot = 0; slot < TradeWindowGeometry.VISIBLE_TRADES; slot++) {
            Rect row = TradeWindowGeometry.row(leftPos, topPos, slot);
            Map<String, Rect> pieces = new LinkedHashMap<>();
            pieces.put("pagoA", TradeWindowGeometry.rowPaymentA(leftPos, topPos, slot));
            pieces.put("pagoB", TradeWindowGeometry.rowPaymentB(leftPos, topPos, slot));
            pieces.put("flecha", TradeWindowGeometry.rowArrow(leftPos, topPos, slot));
            pieces.put("resultado", TradeWindowGeometry.rowResult(leftPos, topPos, slot));
            assertNoOverlaps(pieces, at + " fila " + slot);

            for (Map.Entry<String, Rect> piece : pieces.entrySet()) {
                Rect rect = piece.getValue();
                if (!rect.within(window)) {
                    failures.add(at + ": " + piece.getKey() + " de la fila " + slot
                            + " se sale de la ventana " + rect);
                }
                // Every item hit area must be exactly one item, never the whole row.
                if (!piece.getKey().equals("flecha")
                        && (rect.width() != TradeWindowGeometry.ITEM
                        || rect.height() != TradeWindowGeometry.ITEM)) {
                    failures.add(at + ": " + piece.getKey() + " de la fila " + slot + " mide "
                            + rect.width() + "x" + rect.height() + " y debe medir 16x16");
                }
                if (rect.width() >= TradeWindowGeometry.TRADE_BUTTON_WIDTH) {
                    failures.add(at + ": " + piece.getKey() + " abarca toda la fila");
                }
            }

            // Rows must not overlap each other.
            if (slot > 0) {
                Rect previous = TradeWindowGeometry.row(leftPos, topPos, slot - 1);
                if (previous.overlaps(row)) {
                    failures.add(at + ": las filas " + (slot - 1) + " y " + slot + " se solapan");
                }
            }
            if (!row.within(window)) {
                failures.add(at + ": la fila " + slot + " se sale de la ventana");
            }
            if (row.overlaps(TradeWindowGeometry.scrollTrack(leftPos, topPos))) {
                failures.add(at + ": la fila " + slot + " queda bajo la barra de desplazamiento");
            }
        }

        // The three slots on the right.
        Map<String, Rect> slots = new LinkedHashMap<>();
        slots.put("slotPagoA", TradeWindowGeometry.slotPaymentA(leftPos, topPos));
        slots.put("slotPagoB", TradeWindowGeometry.slotPaymentB(leftPos, topPos));
        slots.put("slotResultado", TradeWindowGeometry.slotResult(leftPos, topPos));
        assertNoOverlaps(slots, at + " slots");
        for (Map.Entry<String, Rect> slot : slots.entrySet()) {
            if (!slot.getValue().within(window)) {
                failures.add(at + ": " + slot.getKey() + " se sale de la ventana");
            }
            if (slot.getValue().width() != TradeWindowGeometry.ITEM) {
                failures.add(at + ": " + slot.getKey() + " no mide 16 de ancho");
            }
        }
    }

    /** The scale must stay between one and its cap, and must actually grow on a normal screen. */
    private static void checkScaleBounds() {
        for (int[] size : SIZES) {
            float scale = TradeWindowGeometry.scaleFor(size[0], size[1]);
            if (scale < 1.0F || scale > TradeWindowGeometry.MAX_SCALE) {
                failures.add(size[0] + "x" + size[1] + ": escala fuera de rango (" + scale + ")");
            }
        }
        // On anything from 1080p up the window should be at full magnification, which is the point of the change.
        float big = TradeWindowGeometry.scaleFor(1920, 1080);
        if (big < TradeWindowGeometry.MAX_SCALE - 0.001F) {
            failures.add("en 1920x1080 la ventana no llega a la ampliacion maxima (" + big + ")");
        }
        // On a screen too small for the window it must clamp to vanilla size rather than shrink below it.
        // Minecraft's own containers overflow at these sizes too; shrinking would only make the text unreadable
        // as well as cut off.
        float tooSmall = TradeWindowGeometry.scaleFor(200, 150);
        if (tooSmall != 1.0F) {
            failures.add("en una pantalla mas pequena que la ventana la escala deberia quedarse en 1 y es "
                    + tooSmall);
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
