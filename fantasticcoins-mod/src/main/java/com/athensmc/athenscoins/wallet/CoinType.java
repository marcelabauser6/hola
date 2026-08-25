package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The three physical Fantastic Coins denominations.
 *
 * <p>These stay exactly what they always were — bronze, silver and gold items carried in the
 * inventory. What each one is <em>worth</em> in Fantastic Cash is not baked in here; it comes
 * from the config so it can be rebalanced without recompiling.</p>
 */
public enum CoinType {
    BRONZE("bronze", () -> ModItems.BRONZE_COIN.get()),
    SILVER("silver", () -> ModItems.SILVER_COIN.get()),
    GOLD("gold", () -> ModItems.GOLD_COIN.get());

    public static final CoinType[] ORDERED = { BRONZE, SILVER, GOLD };

    private final String id;
    private final Supplier<Item> item;

    CoinType(String id, Supplier<Item> item) {
        this.id = id;
        this.item = item;
    }

    public String id() {
        return id;
    }

    /** The physical item for this denomination. */
    public Item item() {
        return item.get();
    }

    /** Full item name, e.g. "Fantastic de Bronce". */
    public Component displayName() {
        return Component.translatable("item.athens_coins." + id + "_coin");
    }

    /** Short name for tight GUI columns, e.g. "Bronce". */
    public Component shortName() {
        return Component.translatable("coin.athens_coins." + id);
    }

    /** Server-side value lookup. Clients use the synced DisplaySettings instead. */
    public long cashValueCents() {
        return CurrencyConfig.get().coinValueCents(this);
    }

    /** Server-side colour lookup. Clients use the synced DisplaySettings instead. */
    public int color() {
        return CurrencyConfig.get().coinColor(this);
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
}
