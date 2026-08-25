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
            Rect payA = TradeWindowGeometry.rowPaymentA(leftPos, topPos, slot);
            Rect payB = TradeWindowGeometry.rowPaymentB(leftPos, topPos, slot);
            Rect resultRect = TradeWindowGeometry.rowResult(leftPos, topPos, slot);

            // The two layouts are checked separately, because they never appear together: with one payment there is
            // no second square and the arrow moves left into that space. Checking them as one set flagged an overlap
            // between a payment that is not drawn and an arrow that is.
            Map<String, Rect> onePayment = new LinkedHashMap<>();
            onePayment.put("pagoA", payA);
            onePayment.put("flecha", TradeWindowGeometry.rowArrow(leftPos, topPos, slot, false));
            onePayment.put("resultado", resultRect);
            assertNoOverlaps(onePayment, at + " fila " + slot + " con un pago");

            Map<String, Rect> twoPayments = new LinkedHashMap<>();
            twoPayments.put("pagoA", payA);
            twoPayments.put("mas", TradeWindowGeometry.rowPaymentPlus(leftPos, topPos, slot));
            twoPayments.put("pagoB", payB);
            twoPayments.put("flecha", TradeWindowGeometry.rowArrow(leftPos, topPos, slot, true));
            twoPayments.put("resultado", resultRect);
            assertNoOverlaps(twoPayments, at + " fila " + slot + " con dos pagos");

            Map<String, Rect> everything = new LinkedHashMap<>(twoPayments);
            everything.putAll(onePayment);
            for (Map.Entry<String, Rect> piece : everything.entrySet()) {
                Rect rect = piece.getValue();
                if (rect.isEmpty()) {
                    failures.add(at + ": falta " + piece.getKey() + " en la fila " + slot);
                    continue;
                }
                if (!rect.within(window)) {
                    failures.add(at + ": " + piece.getKey() + " de la fila " + slot
                            + " se sale de la ventana " + rect);
                }
                // Every item hit area must be exactly one item, never the whole row.
                boolean isItem = piece.getKey().startsWith("pago") || piece.getKey().equals("resultado");
                if (isItem && (rect.width() != TradeWindowGeometry.ITEM
                        || rect.height() != TradeWindowGeometry.ITEM)) {
                    failures.add(at + ": " + piece.getKey() + " de la fila " + slot + " mide "
                            + rect.width() + "x" + rect.height() + " y debe medir 16x16");
                }
                if (rect.width() >= TradeWindowGeometry.TRADE_BUTTON_WIDTH) {
                    failures.add(at + ": " + piece.getKey() + " abarca toda la fila");
                }
            }

            // The plus has to land between the two payments, not on either of them.
            Rect plus = TradeWindowGeometry.rowPaymentPlus(leftPos, topPos, slot);
            if (!plus.isEmpty() && (plus.x() < payA.right() || plus.right() > payB.x())) {
                failures.add(at + ": el mas de la fila " + slot + " se sale del hueco entre los pagos");
            }

            // The arrow must be centred in whatever gap is actually left, in both layouts.
            for (boolean two : new boolean[] {false, true}) {
                Rect arrow = TradeWindowGeometry.rowArrow(leftPos, topPos, slot, two);
                Rect lastPayment = two ? payB : payA;
                if (arrow.x() < lastPayment.right() || arrow.right() > resultRect.x()) {
                    failures.add(at + ": la flecha de la fila " + slot + " con "
                            + (two ? "dos pagos" : "un pago") + " se sale del hueco");
                    continue;
                }
                int before = arrow.x() - lastPayment.right();
                int after = resultRect.x() - arrow.right();
                if (Math.abs(before - after) > 1) {
                    failures.add(at + ": la flecha de la fila " + slot + " con "
                            + (two ? "dos pagos" : "un pago") + " no queda centrada ("
                            + before + " antes, " + after + " despues)");
                }
            }

            if (slot > 0 && TradeWindowGeometry.row(leftPos, topPos, slot - 1).overlaps(row)) {
                failures.add(at + ": las filas " + (slot - 1) + " y " + slot + " se solapan");
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

        // The lone payment must sit between the two vanilla positions, closer to the arrow than the first one, and
        // its well must stay inside the window.
        Rect single = TradeWindowGeometry.slotPaymentSingle(leftPos, topPos);
        Rect slotA = TradeWindowGeometry.slotPaymentA(leftPos, topPos);
        Rect slotB = TradeWindowGeometry.slotPaymentB(leftPos, topPos);
        Rect result = TradeWindowGeometry.slotResult(leftPos, topPos);
        // The lone payment must be the slot nearest the arrow, never the far one, and never past the result.
        if (single.x() <= slotA.x()) {
            failures.add(at + ": el hueco unico quedo en el sitio lejano en vez del cercano a la flecha");
        }
        if (single.x() != slotB.x()) {
            failures.add(at + ": el hueco unico no coincide con el hueco que vanilla ya dibuja");
        }
        if (single.right() >= result.x()) {
            failures.add(at + ": el hueco unico se pasa hasta el resultado");
        }
        if (single.overlaps(result)) {
            failures.add(at + ": el hueco unico se solapa con el resultado");
        }
        if (single.width() != TradeWindowGeometry.ITEM || single.height() != TradeWindowGeometry.ITEM) {
            failures.add(at + ": el hueco unico no mide 16x16");
        }
        Rect well = TradeWindowGeometry.wellAround(single);
        if (!well.within(window)) {
            failures.add(at + ": el marco del hueco unico se sale de la ventana");
        }
        if (well.width() != TradeWindowGeometry.ITEM + 2) {
            failures.add(at + ": el marco del hueco no rodea al item por un pixel");
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
