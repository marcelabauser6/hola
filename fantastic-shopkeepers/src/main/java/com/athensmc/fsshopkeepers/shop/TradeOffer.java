package com.athensmc.fsshopkeepers.shop;

import com.athensmc.fsshopkeepers.money.Money;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One row of a shop: what it hands over, and what it asks for.
 *
 * <p>A shop may charge in two ways and this record carries both, because a server running an economy still
 * wants the occasional barter. {@link #priceCents} is a price in Fantastic Cash; {@link #cost1} and
 * {@link #cost2} are up to two item stacks the buyer must hand over, matching the two input slots of the vanilla
 * trading window. An offer needs at least one of the two forms of payment, and may use both at once - "a diamond
 * and 50 Cash" is a legitimate price.</p>
 *
 * <p>Items are held as full {@link ItemStack}s rather than as registry ids and counts. That is deliberate: a
 * shop selling an enchanted book, a named item or a piece of modded gear with its own NBT has to hand over
 * <em>that</em> item, and reducing it to an id would quietly turn it into a plain one.</p>
 *
 * <p>The record is immutable. Editing a shop replaces its list of offers rather than mutating rows in place, so
 * a half-applied edit cannot be observed by a buyer trading at the same moment.</p>
 */
public record TradeOffer(ItemStack result, long priceCents, ItemStack cost1, ItemStack cost2) {

    private static final String RESULT = "Result";
    private static final String PRICE = "Price";
    private static final String COST1 = "Cost1";
    private static final String COST2 = "Cost2";

    public TradeOffer {
        result = result == null ? ItemStack.EMPTY : result.copy();
        cost1 = cost1 == null ? ItemStack.EMPTY : cost1.copy();
        cost2 = cost2 == null ? ItemStack.EMPTY : cost2.copy();
        priceCents = Money.clamp(priceCents);
    }

    /** An offer that sells an item for a Cash price. */
    public static TradeOffer forCash(ItemStack result, long priceCents) {
        return new TradeOffer(result, priceCents, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    /** An offer that barters items for items. */
    public static TradeOffer forItems(ItemStack result, ItemStack cost1, ItemStack cost2) {
        return new TradeOffer(result, 0L, cost1, cost2);
    }

    /** A blank row, as the editor shows before anything has been chosen. */
    public static TradeOffer empty() {
        return new TradeOffer(ItemStack.EMPTY, 0L, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    public boolean hasCashPrice() {
        return priceCents > 0L;
    }

    public boolean hasItemCost() {
        return !cost1.isEmpty() || !cost2.isEmpty();
    }

    /**
     * True when this row is complete enough to be offered to a buyer.
     *
     * <p>An incomplete row is not an error - it is what a row looks like while an admin is still filling it in -
     * so it is kept in the editor and simply not shown to buyers.</p>
     */
    public boolean isComplete() {
        return !result.isEmpty() && (hasCashPrice() || hasItemCost());
    }

    /** Why this row is not usable yet, in words the editor can print, or null when it is fine. */
    public String incompleteReason() {
        if (result.isEmpty()) {
            return "Elige el articulo que vende";
        }
        if (!hasCashPrice() && !hasItemCost()) {
            return "Pon un precio o un articulo de pago";
        }
        return null;
    }

    /** The price as a plain editable string, e.g. {@code "25.00"}. */
    public String priceText() {
        return Money.plain(priceCents);
    }

    public TradeOffer withResult(ItemStack newResult) {
        return new TradeOffer(newResult, priceCents, cost1, cost2);
    }

    public TradeOffer withPrice(long newPriceCents) {
        return new TradeOffer(result, newPriceCents, cost1, cost2);
    }

    public TradeOffer withCost1(ItemStack newCost) {
        return new TradeOffer(result, priceCents, newCost, cost2);
    }

    public TradeOffer withCost2(ItemStack newCost) {
        return new TradeOffer(result, priceCents, cost1, newCost);
    }

    /** How many the buyer receives per trade. */
    public int resultCount() {
        return result.isEmpty() ? 0 : result.getCount();
    }

    public TradeOffer withResultCount(int count) {
        if (result.isEmpty()) {
            return this;
        }
        ItemStack copy = result.copy();
        copy.setCount(Math.max(1, Math.min(count, result.getMaxStackSize())));
        return withResult(copy);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!result.isEmpty()) {
            tag.put(RESULT, result.save(new CompoundTag()));
        }
        if (priceCents > 0L) {
            tag.putLong(PRICE, priceCents);
        }
        if (!cost1.isEmpty()) {
            tag.put(COST1, cost1.save(new CompoundTag()));
        }
        if (!cost2.isEmpty()) {
            tag.put(COST2, cost2.save(new CompoundTag()));
        }
        return tag;
    }

    /**
     * Reads a row back from the world.
     *
     * <p>An item whose id no longer resolves - a mod removed between restarts - comes back as
     * {@link ItemStack#EMPTY} from vanilla's own loader, which turns the row incomplete rather than throwing.
     * The row is then kept and skipped, so one missing item costs the server one trade instead of one shop.</p>
     */
    public static TradeOffer load(CompoundTag tag) {
        ItemStack result = tag.contains(RESULT)
                ? ItemStack.of(tag.getCompound(RESULT)) : ItemStack.EMPTY;
        ItemStack cost1 = tag.contains(COST1)
                ? ItemStack.of(tag.getCompound(COST1)) : ItemStack.EMPTY;
        ItemStack cost2 = tag.contains(COST2)
                ? ItemStack.of(tag.getCompound(COST2)) : ItemStack.EMPTY;
        return new TradeOffer(result, tag.getLong(PRICE), cost1, cost2);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeItem(result);
        buf.writeVarLong(priceCents);
        buf.writeItem(cost1);
        buf.writeItem(cost2);
    }

    public static TradeOffer read(FriendlyByteBuf buf) {
        ItemStack result = buf.readItem();
        long price = buf.readVarLong();
        ItemStack cost1 = buf.readItem();
        ItemStack cost2 = buf.readItem();
        return new TradeOffer(result, price, cost1, cost2);
    }
}
