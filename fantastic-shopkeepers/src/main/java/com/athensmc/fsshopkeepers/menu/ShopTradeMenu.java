package com.athensmc.fsshopkeepers.menu;

import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopType;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.athensmc.fsshopkeepers.shop.TradeOffer;
import com.athensmc.fsshopkeepers.trade.ShopStock;
import com.athensmc.fsshopkeepers.trade.TradeExecutor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The window a customer buys from.
 *
 * <p>A list of offers with the player's own inventory below, and buying is a click on a row rather than the
 * two-slots-and-a-result dance of the vanilla trading window. The reason is Fantastic Cash: vanilla's merchant window
 * decides whether a trade is possible by looking at the items in its input slots, so a price that is a bank balance
 * has nothing to put there. Rather than fake a banknote item and intercept it, the row is clicked and the server
 * charges the balance.</p>
 *
 * <p>Buying goes through {@link #clickMenuButton}, which is vanilla's own menu-button channel. That avoids inventing
 * a packet for it and, more importantly, means the click arrives through a path the server already validates as
 * belonging to the menu this player currently has open.</p>
 *
 * <p>The offers are sent once when the window opens and are only for drawing. Every one of them is looked up again
 * from the registry when a purchase arrives, because a client can say it clicked row seven of a list it invented.</p>
 */
public final class ShopTradeMenu extends AbstractContainerMenu {

    /** Standard inventory geometry, matching the vanilla screens so the player's items are where they expect. */
    private static final int INVENTORY_COLUMNS = 9;
    private static final int MAIN_ROWS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int INVENTORY_X = 108;
    private static final int INVENTORY_Y = 84;
    private static final int HOTBAR_Y = INVENTORY_Y + MAIN_ROWS * SLOT_SIZE + 4;

    /** How many trades a shift-click attempts at once. */
    public static final int BULK_TIMES = 8;

    /** How far a customer may wander before the window closes itself. */
    private static final double REACH = 8.0D;

    /** One row as the client draws it. */
    public record OfferView(ItemStack result, long priceCents, ItemStack cost1, ItemStack cost2, int available) {

        public boolean inStock() {
            return available > 0;
        }

        void write(FriendlyByteBuf buf) {
            buf.writeItem(result);
            buf.writeVarLong(priceCents);
            buf.writeItem(cost1);
            buf.writeItem(cost2);
            buf.writeVarInt(Math.min(available, Integer.MAX_VALUE - 1));
        }

        static OfferView read(FriendlyByteBuf buf) {
            return new OfferView(buf.readItem(), buf.readVarLong(), buf.readItem(), buf.readItem(),
                    buf.readVarInt());
        }
    }

    /**
     * Everything the client needs to draw the window.
     *
     * <p>The money's name and colour travel with it because they are server settings and the client has no copy of the
     * server's config. Without them the note would be labelled with this mod's defaults on every client, whatever the
     * operator chose.</p>
     */
    public record Data(UUID shopId, String shopName, ShopType type, String cashLabel, int cashColour,
            int selectedOffer, List<OfferView> offers) {

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(shopId);
            buf.writeUtf(shopName, 64);
            buf.writeEnum(type);
            buf.writeUtf(cashLabel, 32);
            buf.writeInt(cashColour);
            buf.writeVarInt(selectedOffer);
            buf.writeVarInt(offers.size());
            for (OfferView offer : offers) {
                offer.write(buf);
            }
        }

        public static Data read(FriendlyByteBuf buf) {
            UUID shopId = buf.readUUID();
            String name = buf.readUtf(64);
            ShopType type = buf.readEnum(ShopType.class);
            String cashLabel = buf.readUtf(32);
            int cashColour = buf.readInt();
            int selectedOffer = buf.readVarInt();
            int count = buf.readVarInt();
            List<OfferView> offers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                offers.add(OfferView.read(buf));
            }
            return new Data(shopId, name, type, cashLabel, cashColour, selectedOffer, offers);
        }
    }

    private final UUID shopId;
    private final String shopName;
    private final ShopType shopType;
    private final String cashLabel;
    private final int cashColour;
    private final int selectedOffer;
    private final List<OfferView> offers;

    public ShopTradeMenu(int containerId, Inventory inventory, Data data) {
        super(ModMenus.SHOP_TRADE.get(), containerId);
        this.shopId = data.shopId();
        this.shopName = data.shopName();
        this.shopType = data.type();
        this.cashLabel = data.cashLabel();
        this.cashColour = data.cashColour();
        this.offers = List.copyOf(data.offers());
        this.selectedOffer = this.offers.isEmpty() ? 0
                : Math.max(0, Math.min(data.selectedOffer(), this.offers.size() - 1));
        addInventorySlots(inventory);
    }

    private void addInventorySlots(Inventory inventory) {
        for (int row = 0; row < MAIN_ROWS; row++) {
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                addSlot(new Slot(inventory, INVENTORY_COLUMNS + column + row * INVENTORY_COLUMNS,
                        INVENTORY_X + column * SLOT_SIZE, INVENTORY_Y + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < INVENTORY_COLUMNS; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    public UUID shopId() {
        return shopId;
    }

    public String shopName() {
        return shopName;
    }

    public ShopType shopType() {
        return shopType;
    }

    public List<OfferView> offers() {
        return offers;
    }

    /** The offer the server asks a freshly opened screen to keep selected. */
    public int selectedOffer() {
        return selectedOffer;
    }

    /** What the server calls its money, for the note in the payment slot. */
    public String cashLabel() {
        return cashLabel;
    }

    /** The colour the server chose for that name. */
    public int cashColour() {
        return cashColour;
    }

    /**
     * Builds the view of a shop for one customer.
     *
     * <p>The stock count is worked out here, on the server, so a row a shop cannot honour arrives already greyed
     * out and the customer is not invited to click it.</p>
     */
    public static Data dataFor(ServerPlayer viewer, Shopkeeper shop) {
        return dataFor(viewer, shop, 0);
    }

    /** Builds a refreshed view while preserving the offer the customer just used. */
    public static Data dataFor(ServerPlayer viewer, Shopkeeper shop, int selectedOffer) {
        List<TradeOffer> tradable = shop.tradableOffers();
        List<OfferView> views = new ArrayList<>(tradable.size());
        for (TradeOffer offer : tradable) {
            int available = ShopStock.availableTrades(viewer.serverLevel(), shop, offer);
            views.add(new OfferView(offer.result(), offer.priceCents(), offer.cost1(), offer.cost2(), available));
        }
        com.athensmc.fsshopkeepers.config.ShopConfig config =
                com.athensmc.fsshopkeepers.config.ShopConfig.get();
        int keptSelection = views.isEmpty() ? 0 : Math.max(0, Math.min(selectedOffer, views.size() - 1));
        return new Data(shop.id(), shop.displayName(), shop.type(), config.cashLabel,
                config.cashColorRgb(), keptSelection, views);
    }

    /** Encodes a purchase as a menu button id. */
    public static int buttonFor(int offerIndex, boolean bulk) {
        return offerIndex * 2 + (bulk ? 1 : 0);
    }

    /**
     * Handles a purchase click.
     *
     * <p>Everything is re-derived from the registry: the shop, the offer at that index, the stock and the price. The
     * button id is treated as nothing more than "which row the player says they clicked", and an index that does not
     * exist is refused rather than trusted.</p>
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer buyer) || buttonId < 0) {
            return false;
        }
        int offerIndex = buttonId / 2;
        boolean bulk = (buttonId & 1) == 1;

        ShopRegistry registry = ShopRegistry.get(buyer.server);
        Shopkeeper shop = registry.byId(shopId);
        if (shop == null) {
            buyer.sendSystemMessage(net.minecraft.network.chat.Component.literal("Esta tienda ya no existe."));
            buyer.closeContainer();
            return false;
        }
        List<TradeOffer> tradable = shop.tradableOffers();
        if (offerIndex >= tradable.size()) {
            return false;
        }
        TradeOffer offer = tradable.get(offerIndex);

        int times = 1;
        if (bulk) {
            int affordable = ShopStock.availableTrades(buyer.serverLevel(), shop, offer);
            times = Math.max(1, Math.min(BULK_TIMES, affordable));
        }

        TradeExecutor.Result result = TradeExecutor.trade(buyer, shop, offer, times);
        buyer.sendSystemMessage(result.message());
        if (result.success()) {
            // Reopen so stock counts and the player's inventory redraw from the server's new truth rather than
            // from what the client remembers.
            ShopMenus.openTrade(buyer, shop, offerIndex);
        }
        return result.success();
    }

    /**
     * Moves a stack between the player's inventory halves on shift-click.
     *
     * <p>The menu owns no container of its own, so this only ever shuffles the player's own items, and buying is
     * never a side effect of a shift-click.</p>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack copy = inSlot.copy();
        int mainEnd = MAIN_ROWS * INVENTORY_COLUMNS;
        boolean fromMain = index < mainEnd;
        boolean moved = fromMain
                ? moveItemStackTo(inSlot, mainEnd, slots.size(), false)
                : moveItemStackTo(inSlot, 0, mainEnd, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (inSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /**
     * Whether the window should stay open.
     *
     * <p>A virtual shop has no position to stand near, so distance is not checked for it; anything else closes when
     * the customer walks away, which is also what stops a window staying usable after the shop is broken.</p>
     */
    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        Shopkeeper shop = ShopRegistry.get(serverPlayer.server).byId(shopId);
        if (shop == null) {
            return false;
        }
        if (!shop.objectKind().isPlaced()) {
            return true;
        }
        return player.level().dimension().equals(shop.level())
                && player.distanceToSqr(shop.pos().getX() + 0.5D, shop.pos().getY() + 0.5D,
                        shop.pos().getZ() + 0.5D) <= REACH * REACH;
    }
}
