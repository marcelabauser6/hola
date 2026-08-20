package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The three FantasticCoins denominations.
 *
 * <p>Values follow the exchange rate the mod already shipped with in its crafting recipes:
 * 9 bronze = 1 silver, 9 silver = 1 gold. Everything is therefore expressible in
 * "bronze units", which is what {@link #bronzeValue()} is for.</p>
 */
public enum CoinType {
    BRONZE("bronze", 1, 0xC87137, () -> ModItems.BRONZE_COIN.get()),
    SILVER("silver", 9, 0xDCDCDC, () -> ModItems.SILVER_COIN.get()),
    GOLD("gold", 81, 0xFFC93C, () -> ModItems.GOLD_COIN.get());

    public static final CoinType[] ORDERED = { BRONZE, SILVER, GOLD };

    private final String id;
    private final int bronzeValue;
    private final int color;
    private final Supplier<Item> item;

    CoinType(String id, int bronzeValue, int color, Supplier<Item> item) {
        this.id = id;
        this.bronzeValue = bronzeValue;
        this.color = color;
        this.item = item;
    }

    public String id() {
        return id;
    }

    /** How many bronze coins this denomination is worth. */
    public int bronzeValue() {
        return bronzeValue;
    }

    /** Colour used for this denomination in GUIs and chat. */
    public int color() {
        return color;
    }

    /** The physical ("cash") item that represents this denomination. */
    public Item item() {
        return item.get();
    }

    /** Display name, reusing the existing item translation keys. */
    public Component displayName() {
        return Component.translatable("item.athens_coins." + id + "_coin");
    }

    /** Short name ("Bronze"/"Bronce") for tight GUI columns. */
    public Component shortName() {
        return Component.translatable("coin.athens_coins." + id);
    }

    public String nbtKey() {
        return id;
    }

    @Nullable
    public static CoinType byId(String id) {
        for (CoinType type : ORDERED) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Splits a raw bronze-unit amount into gold/silver/bronze, largest denomination first.
     *
     * @return an array of {gold, silver, bronze}
     */
    public static long[] breakDown(long bronzeUnits) {
        long remaining = Math.max(0L, bronzeUnits);
        long gold = remaining / GOLD.bronzeValue;
        remaining -= gold * GOLD.bronzeValue;
        long silver = remaining / SILVER.bronzeValue;
        remaining -= silver * SILVER.bronzeValue;
        return new long[] { gold, silver, remaining };
    }
}
