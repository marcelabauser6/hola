package com.athensmc.fsshopkeepers.trade;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.money.Cash;
import com.athensmc.fsshopkeepers.shop.ShopType;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.athensmc.fsshopkeepers.shop.TradeOffer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Carries out one trade.
 *
 * <p>The whole of this class is about ordering. Every check happens before anything is taken, and money moves before
 * goods do, because the failure that matters is the one where a buyer is charged and receives nothing, or receives
 * goods and is not charged. Both are permanent once they touch a saved world.</p>
 *
 * <p>What {@link TradeOffer#priceCents()} means depends on which way the shop trades. For a shop that
 * {@linkplain ShopType.Direction#SELLS sells} it is what the buyer pays; for one that
 * {@linkplain ShopType.Direction#BUYS buys} it is what the buyer is paid. Keeping one field for both, rather than a
 * separate price and payout, means there is one number on a row in the editor and one number to get right here.</p>
 */
public final class TradeExecutor {

    private TradeExecutor() {
    }

    /**
     * What happened, and what to tell the player.
     *
     * <p>A refusal always carries a reason. "Trade failed" with no explanation is the single most common complaint
     * about shop plugins, and the reason is always known at the point the check fails.</p>
     */
    public record Result(boolean success, Component message) {

        static Result ok(Component message) {
            return new Result(true, message);
        }

        static Result no(String reason) {
            return new Result(false, Component.literal(reason).withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Runs a trade, once, for one buyer.
     *
     * <p>Must be called on the server thread: it reads and writes a chest, a player's inventory and a bank balance,
     * none of which tolerate concurrent access.</p>
     */
    public static Result trade(ServerPlayer buyer, Shopkeeper shop, TradeOffer offer, int times) {
        ShopConfig config = ShopConfig.get();
        if (times <= 0) {
            return Result.no("Cantidad invalida.");
        }
        if (!offer.isComplete()) {
            return Result.no("Ese trato esta incompleto.");
        }
        if (config.preventTradingWithOwnShop && shop.isOwner(buyer.getUUID())) {
            return Result.no("No puedes comerciar con tu propia tienda.");
        }
        if (!shop.tradePermission().isBlank()
                && !FantasticShopkeepers.hasPermission(buyer, shop.tradePermission())) {
            return Result.no("No tienes permiso para comerciar en esta tienda.");
        }

        ServerLevel level = buyer.serverLevel();
        return switch (shop.type().direction()) {
            case SELLS -> sell(buyer, level, shop, offer, times);
            case BUYS -> buy(buyer, level, shop, offer, times);
            case BARTERS -> barter(buyer, level, shop, offer, times);
        };
    }

    /** The shop hands over goods and the buyer pays in Cash, items, or both. */
    private static Result sell(ServerPlayer buyer, ServerLevel level, Shopkeeper shop, TradeOffer offer,
            int times) {
        ItemStack goods = offer.result().copy();
        int perTrade = goods.getCount();
        long price = offer.priceCents() * times;
        if (offer.hasCashPrice() && price / times != offer.priceCents()) {
            return Result.no("El precio total es demasiado grande.");
        }

        Container container = shop.type().needsContainer() ? ShopStock.container(level, shop) : null;
        if (shop.type().needsContainer() && container == null) {
            return Result.no("Esta tienda no tiene cofre. Avisa al dueño.");
        }

        boolean copiesGoods = shop.type() == ShopType.PLAYER_BOOK;
        int wanted = perTrade * times;
        if (container != null && !copiesGoods && ShopStock.count(container, offer.result()) < wanted) {
            return Result.no("La tienda no tiene existencias suficientes.");
        }
        if (container != null && copiesGoods && ShopStock.count(container, offer.result()) <= 0) {
            return Result.no("La tienda no tiene el libro original.");
        }

        ItemStack payment1 = scaled(offer.cost1(), times);
        ItemStack payment2 = scaled(offer.cost2(), times);
        if (!hasItems(buyer, payment1) || !hasItems(buyer, payment2)) {
            return Result.no("No tienes los articulos de pago.");
        }

        ItemStack delivered = goods.copy();
        delivered.setCount(wanted);
        if (!hasRoomFor(buyer, delivered)) {
            return Result.no("No tienes espacio en el inventario.");
        }

        if (offer.hasCashPrice()) {
            if (!Cash.available()) {
                return Result.no("El sistema de dinero no esta disponible ahora mismo.");
            }
            if (Cash.balance(buyer.server, buyer.getUUID()) < price) {
                return Result.no("No tienes suficiente " + Cash.currencyName() + ". Cuesta "
                        + Cash.format(price) + ".");
            }
            if (!Cash.charge(buyer.server, buyer.getUUID(), price)) {
                return Result.no("No se pudo cobrar el importe. Revisa tu cuenta bancaria.");
            }
        }

        // Past this point the buyer has paid, so every remaining step must succeed or be undone.
        if (!takeItems(buyer, payment1) || !takeItems(buyer, payment2)) {
            refund(buyer, shop, price);
            return Result.no("No se pudo retirar el pago. No se te ha cobrado.");
        }
        if (container != null && !copiesGoods && !ShopStock.remove(container, offer.result(), wanted)) {
            refund(buyer, shop, price);
            giveBack(buyer, payment1);
            giveBack(buyer, payment2);
            return Result.no("Las existencias cambiaron mientras comprabas. No se te ha cobrado.");
        }

        give(buyer, delivered);
        payOwner(buyer, shop, price);
        // A selling player shop receives the item payment into its chest, so a barter-plus-cash price is not lost.
        if (container != null) {
            storeIfPresent(container, payment1);
            storeIfPresent(container, payment2);
        }
        log(buyer, shop, offer, times, price);
        notifyOwner(buyer, shop, delivered, price);
        return Result.ok(Component.literal("Has comprado " + wanted + " x " + delivered.getHoverName().getString()
                + (offer.hasCashPrice() ? " por " + Cash.format(price) : "")).withStyle(ChatFormatting.GREEN));
    }

    /** The buyer hands in goods and the shop pays them. */
    private static Result buy(ServerPlayer buyer, ServerLevel level, Shopkeeper shop, TradeOffer offer,
            int times) {
        ItemStack wanted = offer.cost1().isEmpty() ? offer.cost2() : offer.cost1();
        if (wanted.isEmpty()) {
            return Result.no("Ese trato no dice que articulo compra.");
        }
        ItemStack handedIn = scaled(wanted, times);
        long payout = offer.priceCents() * times;
        if (offer.hasCashPrice() && payout / times != offer.priceCents()) {
            return Result.no("El importe total es demasiado grande.");
        }
        if (!Cash.available()) {
            return Result.no("El sistema de dinero no esta disponible ahora mismo.");
        }

        Container container = ShopStock.container(level, shop);
        if (container == null) {
            return Result.no("Esta tienda no tiene cofre. Avisa al dueño.");
        }
        if (!hasItems(buyer, handedIn)) {
            return Result.no("No tienes los articulos que pide.");
        }
        if (ShopStock.room(container, handedIn) < handedIn.getCount()) {
            return Result.no("El cofre de la tienda esta lleno.");
        }
        if (shop.owner() == null) {
            return Result.no("Esta tienda no tiene dueño que pague.");
        }
        if (Cash.balance(buyer.server, shop.owner()) < payout) {
            return Result.no("El dueño no tiene saldo para pagarte.");
        }
        // The owner pays first: if that fails there is nothing to undo.
        if (!Cash.charge(buyer.server, shop.owner(), payout)) {
            return Result.no("No se pudo cobrar al dueño. Intentalo mas tarde.");
        }
        if (!takeItems(buyer, handedIn)) {
            Cash.deposit(buyer.server, shop.owner(), payout);
            return Result.no("No se pudieron retirar tus articulos. No se ha movido dinero.");
        }
        if (!ShopStock.add(container, handedIn)) {
            Cash.deposit(buyer.server, shop.owner(), payout);
            giveBack(buyer, handedIn);
            return Result.no("El cofre se lleno mientras vendias. No se ha movido dinero.");
        }
        long net = payout - ShopConfig.get().taxOn(payout);
        if (!Cash.deposit(buyer.server, buyer.getUUID(), net)) {
            FantasticShopkeepers.LOGGER.error(
                    "No se pudo pagar {} a {} por la tienda {}: los articulos ya se guardaron.",
                    net, buyer.getGameProfile().getName(), shop.id());
            return Result.no("Tus articulos se entregaron pero el pago fallo. Avisa a un administrador.");
        }
        log(buyer, shop, offer, times, payout);
        notifyOwner(buyer, shop, handedIn, payout);
        return Result.ok(Component.literal("Has vendido " + handedIn.getCount() + " x "
                + handedIn.getHoverName().getString() + " por " + Cash.format(net))
                .withStyle(ChatFormatting.GREEN));
    }

    /** Items for items, with no money on either side. */
    private static Result barter(ServerPlayer buyer, ServerLevel level, Shopkeeper shop, TradeOffer offer,
            int times) {
        Container container = ShopStock.container(level, shop);
        if (container == null) {
            return Result.no("Esta tienda no tiene cofre. Avisa al dueño.");
        }
        ItemStack payment1 = scaled(offer.cost1(), times);
        ItemStack payment2 = scaled(offer.cost2(), times);
        ItemStack goods = offer.result().copy();
        goods.setCount(offer.result().getCount() * times);

        if (!hasItems(buyer, payment1) || !hasItems(buyer, payment2)) {
            return Result.no("No tienes los articulos de pago.");
        }
        if (ShopStock.count(container, offer.result()) < goods.getCount()) {
            return Result.no("La tienda no tiene existencias suficientes.");
        }
        if (!hasRoomFor(buyer, goods)) {
            return Result.no("No tienes espacio en el inventario.");
        }
        if (!payment1.isEmpty() && ShopStock.room(container, payment1) < payment1.getCount()) {
            return Result.no("El cofre de la tienda esta lleno.");
        }
        if (!payment2.isEmpty() && ShopStock.room(container, payment2) < payment2.getCount()) {
            return Result.no("El cofre de la tienda esta lleno.");
        }
        if (!takeItems(buyer, payment1) || !takeItems(buyer, payment2)) {
            giveBack(buyer, payment1);
            giveBack(buyer, payment2);
            return Result.no("No se pudo retirar el pago.");
        }
        if (!ShopStock.remove(container, offer.result(), goods.getCount())) {
            giveBack(buyer, payment1);
            giveBack(buyer, payment2);
            return Result.no("Las existencias cambiaron mientras cambiabas.");
        }
        storeIfPresent(container, payment1);
        storeIfPresent(container, payment2);
        give(buyer, goods);
        log(buyer, shop, offer, times, 0L);
        notifyOwner(buyer, shop, goods, 0L);
        return Result.ok(Component.literal("Has cambiado por " + goods.getCount() + " x "
                + goods.getHoverName().getString()).withStyle(ChatFormatting.GREEN));
    }

    private static ItemStack scaled(ItemStack stack, int times) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(stack.getCount() * times);
        return copy;
    }

    private static boolean hasItems(ServerPlayer player, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        int found = 0;
        for (ItemStack inSlot : player.getInventory().items) {
            if (ShopStock.matches(inSlot, wanted)) {
                found += inSlot.getCount();
                if (found >= wanted.getCount()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean takeItems(ServerPlayer player, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        if (!hasItems(player, wanted)) {
            return false;
        }
        int left = wanted.getCount();
        for (int slot = 0; slot < player.getInventory().items.size() && left > 0; slot++) {
            ItemStack inSlot = player.getInventory().items.get(slot);
            if (!ShopStock.matches(inSlot, wanted)) {
                continue;
            }
            int taken = Math.min(left, inSlot.getCount());
            inSlot.shrink(taken);
            left -= taken;
        }
        player.getInventory().setChanged();
        return left == 0;
    }

    /**
     * Whether the buyer can hold what they are about to receive.
     *
     * <p>Checked before the trade rather than dropping the overflow on the floor. Items on the floor in a shop
     * district get picked up by whoever is standing closest, which is how a purchase becomes someone else's.</p>
     */
    private static boolean hasRoomFor(ServerPlayer player, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return true;
        }
        int space = 0;
        int max = incoming.getMaxStackSize();
        for (ItemStack inSlot : player.getInventory().items) {
            if (inSlot.isEmpty()) {
                space += max;
            } else if (ShopStock.matches(inSlot, incoming)) {
                space += Math.max(0, max - inSlot.getCount());
            }
            if (space >= incoming.getCount()) {
                return true;
            }
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack left = stack.copy();
        // Room was checked first, so a leftover here means the inventory changed under us; dropping at the
        // player's feet is then better than deleting goods they paid for.
        if (!player.getInventory().add(left) && !left.isEmpty()) {
            player.drop(left, false);
        }
        player.getInventory().setChanged();
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        give(player, stack);
    }

    private static void storeIfPresent(Container container, ItemStack stack) {
        if (!stack.isEmpty()) {
            ShopStock.add(container, stack);
        }
    }

    private static void refund(ServerPlayer buyer, Shopkeeper shop, long price) {
        if (price > 0L) {
            Cash.deposit(buyer.server, buyer.getUUID(), price);
        }
    }

    /**
     * Pays the shop's owner, minus the server's cut.
     *
     * <p>Admin shops have no owner, so their income leaves the economy entirely. That is deliberate: an admin shop
     * is a money sink, and paying nobody is what makes it one.</p>
     */
    private static void payOwner(ServerPlayer buyer, Shopkeeper shop, long price) {
        if (price <= 0L || shop.owner() == null) {
            return;
        }
        long net = price - ShopConfig.get().taxOn(price);
        if (net <= 0L) {
            return;
        }
        if (!Cash.creditSale(buyer.server, shop.owner(), shop.linkedAccount(), net)) {
            FantasticShopkeepers.LOGGER.warn(
                    "No se pudo abonar {} al dueño de la tienda {}. La venta ya se completo.", net, shop.id());
        }
    }

    private static void notifyOwner(ServerPlayer buyer, Shopkeeper shop, ItemStack goods, long price) {
        if (!ShopConfig.get().notifyOwnerOfTrades || shop.owner() == null) {
            return;
        }
        ServerPlayer owner = buyer.server.getPlayerList().getPlayer(shop.owner());
        if (owner == null || owner.getUUID().equals(buyer.getUUID())) {
            return;
        }
        String amount = price > 0L ? " por " + Cash.format(price) : "";
        owner.sendSystemMessage(Component.literal("[Tienda] " + buyer.getGameProfile().getName() + " comercio "
                + goods.getCount() + " x " + goods.getHoverName().getString() + amount)
                .withStyle(ChatFormatting.AQUA));
    }

    private static void log(ServerPlayer buyer, Shopkeeper shop, TradeOffer offer, int times, long price) {
        if (!ShopConfig.get().logTrades) {
            return;
        }
        FantasticShopkeepers.LOGGER.info("TRADE player={} shop={} type={} item={} times={} amount={}",
                buyer.getGameProfile().getName(), shop.id(), shop.type().id(),
                offer.result().isEmpty() ? offer.cost1().getHoverName().getString()
                        : offer.result().getHoverName().getString(),
                times, price);
    }
}
