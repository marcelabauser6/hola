package com.athensmc.fsshopkeepers.client.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The guard on the buying window.
 *
 * <p>Checked because both were reported as broken by hand. That the item hit areas are the item's own 16 pixels and
 * nothing wider, so a tooltip cannot cover the screen when the cursor merely crosses a row. And that the plus drawn
 * between two payments sits in the gap between them rather than on top of either.</p>
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
        int leftPos = TradeWindowGeometry.leftPos(screenWidth);
        int topPos = TradeWindowGeometry.topPos(screenHeight);

        // Centred, at vanilla size, and on screen wherever the screen is big enough to hold it.
        if (screenWidth >= TradeWindowGeometry.WINDOW_WIDTH && leftPos < 0) {
            failures.add(at + ": la ventana empieza fuera de la pantalla por la izquierda");
        }
        if (screenHeight >= TradeWindowGeometry.WINDOW_HEIGHT && topPos < 0) {
            failures.add(at + ": la ventana empieza fuera de la pantalla por arriba");
        }

        Rect window = new Rect(leftPos, topPos, TradeWindowGeometry.WINDOW_WIDTH,
                TradeWindowGeometry.WINDOW_HEIGHT);

        for (int slot = 0; slot < TradeWindowGeometry.VISIBLE_TRADES; slot++) {
            Rect row = TradeWindowGeometry.row(leftPos, topPos, slot);
            Map<String, Rect> pieces = new LinkedHashMap<>();
            pieces.put("pagoA", TradeWindowGeometry.rowPaymentA(leftPos, topPos, slot));
            pieces.put("pagoB", TradeWindowGeometry.rowPaymentB(leftPos, topPos, slot));
            pieces.put("mas", TradeWindowGeometry.rowPaymentPlus(leftPos, topPos, slot));
            pieces.put("flecha", TradeWindowGeometry.rowArrow(leftPos, topPos, slot));
            pieces.put("resultado", TradeWindowGeometry.rowResult(leftPos, topPos, slot));
            assertNoOverlaps(pieces, at + " fila " + slot);

            // The plus has to exist and land between the two payments, not on either of them.
            Rect plus = TradeWindowGeometry.rowPaymentPlus(leftPos, topPos, slot);
            Rect payA = TradeWindowGeometry.rowPaymentA(leftPos, topPos, slot);
            Rect payB = TradeWindowGeometry.rowPaymentB(leftPos, topPos, slot);
            if (plus.isEmpty()) {
                failures.add(at + ": la fila " + slot + " no tiene sitio para el mas entre los pagos");
            } else if (plus.x() < payA.right() || plus.right() > payB.x()) {
                failures.add(at + ": el mas de la fila " + slot + " se sale del hueco entre los pagos");
            }

            for (Map.Entry<String, Rect> piece : pieces.entrySet()) {
                Rect rect = piece.getValue();
                if (!rect.within(window)) {
                    failures.add(at + ": " + piece.getKey() + " de la fila " + slot
                            + " se sale de la ventana " + rect);
                }
                // Every item hit area must be exactly one item, never the whole row.
                if (!piece.getKey().equals("flecha") && !piece.getKey().equals("mas")
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
        slots.put("slotMas", TradeWindowGeometry.slotPaymentPlus(leftPos, topPos));
        assertNoOverlaps(slots, at + " slots");
        Rect slotPlus = TradeWindowGeometry.slotPaymentPlus(leftPos, topPos);
        if (slotPlus.isEmpty()) {
            failures.add(at + ": no hay sitio para el mas entre los dos huecos de pago");
        }
        for (Map.Entry<String, Rect> slot : slots.entrySet()) {
            if (!slot.getValue().within(window)) {
                failures.add(at + ": " + slot.getKey() + " se sale de la ventana");
            }
            if (!slot.getKey().equals("slotMas") && slot.getValue().width() != TradeWindowGeometry.ITEM) {
                failures.add(at + ": " + slot.getKey() + " no mide 16 de ancho");
            }
        }
    }

    /**
     * The window stays exactly vanilla size.
     *
     * <p>Asserted rather than assumed, because an earlier version magnified it and that turned out to clash with every
     * other window on screen. If someone reaches for a scale factor again, this fails.</p>
     */
    private static void checkScaleBounds() {
        if (TradeWindowGeometry.WINDOW_WIDTH != 276 || TradeWindowGeometry.WINDOW_HEIGHT != 166) {
            failures.add("la ventana ya no mide lo que la de vanilla ("
                    + TradeWindowGeometry.WINDOW_WIDTH + "x" + TradeWindowGeometry.WINDOW_HEIGHT + ")");
        }
        // Centred on a 1080p screen, to the pixel.
        if (TradeWindowGeometry.leftPos(1920) != (1920 - 276) / 2) {
            failures.add("la ventana no queda centrada horizontalmente");
        }
        if (TradeWindowGeometry.topPos(1080) != (1080 - 166) / 2) {
            failures.add("la ventana no queda centrada verticalmente");
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
