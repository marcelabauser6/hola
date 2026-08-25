package com.athensmc.fsshopkeepers.trade;

import java.util.UUID;

/**
 * Decides how much of a completed sale may be credited to its shop owner.
 *
 * <p>A player is allowed to buy from their own shop, but that must still be a real purchase. Crediting the sale back
 * to the same UUID that was just charged restores the entire balance and creates an infinite-purchase exploit. Such a
 * purchase therefore remains charged while its seller credit is intentionally zero.</p>
 */
public final class SaleSettlement {

    private SaleSettlement() {
    }

    /**
     * Returns the amount to credit to the seller after a buyer has paid.
     *
     * @param buyer buyer whose account was charged
     * @param seller owner who would normally receive the sale
     * @param netSale amount remaining after tax
     */
    public static long sellerCredit(UUID buyer, UUID seller, long netSale) {
        if (seller == null || netSale <= 0L || seller.equals(buyer)) {
            return 0L;
        }
        return netSale;
    }
}
