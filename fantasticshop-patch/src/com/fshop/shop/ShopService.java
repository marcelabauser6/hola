/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 */
package com.fshop.shop;

import com.fshop.config.FShopConfig;
import com.fshop.data.FShopSavedData;
import com.fshop.economy.CoinEconomy;
import com.fshop.shop.PlayerShop;
import com.fshop.shop.ShopOffer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ShopService {
    private ShopService() {
    }

    public static Result buy(ServerPlayer buyer, PlayerShop shop, int offerIndex, int amount) {
        if (shop == null) {
            return Result.NO_SHOP;
        }
        if (!shop.isMain() && shop.getOwner().equals(buyer.m_20148_())) {
            return Result.OWN_SHOP;
        }
        if (offerIndex < 0 || offerIndex >= shop.getOffers().size()) {
            return Result.NO_OFFER;
        }
        if (amount <= 0) {
            return Result.INVALID;
        }
        if (!CoinEconomy.available()) {
            return Result.NO_CURRENCY;
        }
        if (!ShopService.sellerBanked(buyer, shop)) {
            return Result.NO_BANK_ACCOUNT;
        }
        ShopOffer offer = shop.getOffers().get(offerIndex);
        int items = offer.getBundle() * amount;
        if (!offer.isInfinite() && offer.getStock() < items) {
            return Result.OUT_OF_STOCK;
        }
        long total = offer.getUnitPrice() * (long)amount;
        if (CoinEconomy.balance((Player)buyer, offer.getCoin()) < total) {
            return Result.CANNOT_AFFORD;
        }
        if (!ShopService.hasRoomFor(buyer, offer, items)) {
            return Result.INVENTORY_FULL;
        }
        if (!CoinEconomy.withdraw((Player)buyer, offer.getCoin(), total)) {
            return Result.CANNOT_AFFORD;
        }
        ShopService.giveItems(buyer, offer, items);
        if (!offer.isInfinite()) {
            offer.addStock(-items);
        }
        shop.addEarnings(offer.getCoin(), total);
        FShopSavedData.get(buyer.m_284548_()).m_77762_();
        return Result.OK;
    }

    public static Result addOrRestock(ServerPlayer owner, PlayerShop shop, int slot, long unitPrice, int coin, int bundle) {
        if (shop == null) {
            return Result.NO_SHOP;
        }
        if (!shop.getOwner().equals(owner.m_20148_())) {
            return Result.NOT_OWNER;
        }
        if (!ShopService.refreshAccount(owner, shop)) {
            return Result.NO_BANK_ACCOUNT;
        }
        Inventory inv = owner.m_150109_();
        if (slot < 0 || slot >= inv.m_6643_()) {
            return Result.INVALID;
        }
        ItemStack stack = inv.m_8020_(slot);
        if (stack.m_41619_() || ShopService.isCoin(stack)) {
            return Result.INVALID;
        }
        long price = ShopService.clampPrice(unitPrice);
        int count = stack.m_41613_();
        ShopOffer existing = ShopService.findMatching(shop, stack);
        if (existing != null) {
            existing.addStock(count);
        } else {
            if (shop.getOffers().size() >= (Integer)FShopConfig.MAX_OFFERS_PER_SHOP.get()) {
                return Result.LIMIT_REACHED;
            }
            ShopOffer offer = new ShopOffer(stack, price, coin, count);
            offer.setBundle(bundle);
            shop.getOffers().add(offer);
        }
        inv.m_6836_(slot, ItemStack.f_41583_);
        ShopOffer.mergeDuplicates(shop.getOffers());
        FShopSavedData.get(owner.m_284548_()).m_77762_();
        return Result.OK;
    }

    public static StockOutcome stock(ServerPlayer owner, PlayerShop shop, int slot) {
        if (shop == null) {
            return StockOutcome.NO_SHOP;
        }
        if (!shop.getOwner().equals(owner.m_20148_())) {
            return StockOutcome.NOT_OWNER;
        }
        Inventory inv = owner.m_150109_();
        if (slot < 0 || slot >= inv.m_6643_()) {
            return StockOutcome.INVALID;
        }
        ItemStack stack = inv.m_8020_(slot);
        if (stack.m_41619_() || ShopService.isCoin(stack)) {
            return StockOutcome.INVALID;
        }
        // Re-sync the account here too, so a shop that was frozen unfreezes as soon as its owner has
        // an account again. Restocking itself is deliberately not blocked: nothing can be bought from
        // a frozen shop anyway, and refusing to let an owner move stock while they sort out a bank
        // account would strand their items for no gain.
        ShopService.refreshAccount(owner, shop);
        ShopOffer existing = ShopService.findMatching(shop, stack);
        if (existing != null) {
            existing.addStock(stack.m_41613_());
            inv.m_6836_(slot, ItemStack.f_41583_);
            ShopOffer.mergeDuplicates(shop.getOffers());
            FShopSavedData.get(owner.m_284548_()).m_77762_();
            return StockOutcome.RESTOCKED;
        }
        if (shop.getOffers().size() >= (Integer)FShopConfig.MAX_OFFERS_PER_SHOP.get()) {
            return StockOutcome.LIMIT;
        }
        return StockOutcome.NEEDS_PRICE;
    }

    public static Result setPrice(ServerPlayer owner, PlayerShop shop, int offerIndex, long unitPrice, int coin, int bundle) {
        if (shop == null) {
            return Result.NO_SHOP;
        }
        if (!shop.getOwner().equals(owner.m_20148_())) {
            return Result.NOT_OWNER;
        }
        if (offerIndex < 0 || offerIndex >= shop.getOffers().size()) {
            return Result.NO_OFFER;
        }
        // Same reasoning as stock(): re-sync but do not block. Editing a price on a frozen shop is
        // harmless, and it is exactly what an owner will want to do while getting an account.
        ShopService.refreshAccount(owner, shop);
        ShopOffer offer = shop.getOffers().get(offerIndex);
        offer.setUnitPrice(ShopService.clampPrice(unitPrice));
        offer.setCoin(coin);
        offer.setBundle(bundle);
        FShopSavedData.get(owner.m_284548_()).m_77762_();
        return Result.OK;
    }

    public static Result removeOffer(ServerPlayer owner, PlayerShop shop, int offerIndex) {
        if (shop == null) {
            return Result.NO_SHOP;
        }
        if (!shop.getOwner().equals(owner.m_20148_())) {
            return Result.NOT_OWNER;
        }
        if (offerIndex < 0 || offerIndex >= shop.getOffers().size()) {
            return Result.NO_OFFER;
        }
        ShopOffer offer = shop.getOffers().remove(offerIndex);
        ShopService.giveItems(owner, offer, offer.getStock());
        FShopSavedData.get(owner.m_284548_()).m_77762_();
        return Result.OK;
    }

    public static Result collect(ServerPlayer owner, PlayerShop shop) {
        if (shop == null) {
            return Result.NO_SHOP;
        }
        if (!shop.getOwner().equals(owner.m_20148_())) {
            return Result.NOT_OWNER;
        }
        if (!CoinEconomy.available()) {
            return Result.NO_CURRENCY;
        }
        if (shop.totalPendingEarnings() <= 0L) {
            return Result.INVALID;
        }
        // Take each currency out, pay it, and put it back if the payout failed. Clearing everything
        // after the loop destroyed any amount that had not actually been delivered - and the cash
        // payout can fail, because it needs a bank account that may have closed since the sale.
        boolean paidAnything = false;
        boolean heldBack = false;
        for (int coin = 0; coin < CoinEconomy.TYPES; ++coin) {
            long amount = shop.takeEarnings(coin);
            if (amount <= 0L) {
                continue;
            }
            if (CoinEconomy.depositSale((Player)owner, coin, amount,
                    shop.isMain() ? 0 : shop.getAccountNumber())) {
                paidAnything = true;
            } else {
                shop.restoreEarnings(coin, amount);
                heldBack = true;
            }
        }
        FShopSavedData.get(owner.m_284548_()).m_77762_();
        if (!paidAnything) {
            return heldBack ? Result.NO_BANK_ACCOUNT : Result.INVALID;
        }
        return Result.OK;
    }

    private static long clampPrice(long price) {
        return Math.max(1L, Math.min(price, (Long)FShopConfig.MAX_UNIT_PRICE.get()));
    }

    /**
     * Re-reads the owner's account number onto the shop.
     *
     * <p>Called before listing so a shop picks up a new number by itself after its owner moves
     * banks, and drops to frozen if they no longer have an account at all.</p>
     */
    private static boolean refreshAccount(ServerPlayer owner, PlayerShop shop) {
        if (shop.isMain()) {
            return true;        // the server shop is not anybody's account
        }
        int number = com.athensmc.athenscoins.api.FantasticCurrencyAPI.accountNumber(owner.f_8924_, owner.m_20148_());
        shop.setAccountNumber(number);
        return number > 0;
    }

    /** True when the shop can accept money: the server shop always can. */
    private static boolean sellerBanked(ServerPlayer buyer, PlayerShop shop) {
        if (shop.isMain()) {
            return true;
        }
        return shop.canSell() && com.athensmc.athenscoins.api.FantasticCurrencyAPI.ownsAccount(
                buyer.f_8924_, shop.getOwner(), shop.getAccountNumber());
    }

    private static boolean isCoin(ItemStack s) {
        return s.m_41720_() == CoinEconomy.coinItem(0) || s.m_41720_() == CoinEconomy.coinItem(1) || s.m_41720_() == CoinEconomy.coinItem(2);
    }

    private static ShopOffer findMatching(PlayerShop shop, ItemStack stack) {
        for (ShopOffer offer : shop.getOffers()) {
            if (!ShopOffer.matchesForMerge(offer.getItem(), stack)) continue;
            return offer;
        }
        return null;
    }

    private static boolean hasRoomFor(ServerPlayer player, ShopOffer offer, int amount) {
        Inventory inv = player.m_150109_();
        int maxStack = offer.getItem().m_41741_();
        int free = 0;
        for (int i = 0; i < inv.f_35974_.size(); ++i) {
            ItemStack s = (ItemStack)inv.f_35974_.get(i);
            if (s.m_41619_()) {
                free += maxStack;
                continue;
            }
            if (!ItemStack.m_150942_((ItemStack)s, (ItemStack)offer.getItem())) continue;
            free += Math.max(0, maxStack - s.m_41613_());
        }
        return free >= amount;
    }

    private static void giveItems(ServerPlayer player, ShopOffer offer, int amount) {
        int n;
        int maxStack = offer.getItem().m_41741_();
        for (int remaining = amount; remaining > 0; remaining -= n) {
            n = Math.min(maxStack, remaining);
            ItemStack give = offer.displayStack(n);
            if (player.m_150109_().m_36054_(give)) continue;
            player.m_36176_(give, false);
        }
    }

    public static enum Result {
        OK,
        NO_SHOP,
        NO_OFFER,
        NOT_OWNER,
        NO_BANK_ACCOUNT,
        OUT_OF_STOCK,
        CANNOT_AFFORD,
        INVENTORY_FULL,
        INVALID,
        NO_CURRENCY,
        LIMIT_REACHED,
        OWN_SHOP;

    }

    public static enum StockOutcome {
        RESTOCKED,
        NEEDS_PRICE,
        LIMIT,
        INVALID,
        NOT_OWNER,
        NO_SHOP;

    }
}

