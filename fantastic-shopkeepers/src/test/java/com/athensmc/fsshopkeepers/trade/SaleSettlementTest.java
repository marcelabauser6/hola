package com.athensmc.fsshopkeepers.trade;

import java.util.UUID;

/** Regression guard for the self-purchase refund that made Cash purchases effectively free. */
public final class SaleSettlementTest {

    private static int failures;

    public static void main(String[] args) {
        UUID buyer = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID anotherSeller = UUID.fromString("22222222-2222-2222-2222-222222222222");

        equalsLong("una compra propia no devuelve el precio", SaleSettlement.sellerCredit(buyer, buyer, 20_000L), 0L);
        equalsLong("una venta a otro jugador si paga al dueño",
                SaleSettlement.sellerCredit(buyer, anotherSeller, 20_000L), 20_000L);
        equalsLong("una tienda sin dueño no recibe nada", SaleSettlement.sellerCredit(buyer, null, 20_000L), 0L);
        equalsLong("un importe cero no crea saldo", SaleSettlement.sellerCredit(buyer, anotherSeller, 0L), 0L);
        equalsLong("un importe negativo no crea saldo", SaleSettlement.sellerCredit(buyer, anotherSeller, -1L), 0L);

        // Reproduce el fallo de campo completo: cobrar 200 y liquidar una venta propia debe dejar el saldo 200 abajo.
        long initialBalance = 50_000L;
        long price = 20_000L;
        long finalBalance = initialBalance - price + SaleSettlement.sellerCredit(buyer, buyer, price);
        equalsLong("el saldo baja en una compra propia", finalBalance, 30_000L);

        if (failures > 0) {
            throw new AssertionError(failures + " comprobaciones de liquidacion fallaron.");
        }
        System.out.println("Liquidacion verificada: una compra propia se cobra y no se reembolsa.");
    }

    private static void equalsLong(String what, long actual, long expected) {
        if (actual != expected) {
            failures++;
            System.err.println("  - " + what + ": dio " + actual + " y se esperaba " + expected);
        }
    }
}
