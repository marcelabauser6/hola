package com.athensmc.shopeditor.shop;

import com.athensmc.shopeditor.ShopEditor;
import com.athensmc.shopeditor.trade.TradeLine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shops this mod owns, saved with the world.
 *
 * <p>Keyed by the villager's UUID, so a shop is whatever NPC it is attached to and nothing else has to be
 * tracked. Stored as world data rather than a file of our own, which means it is backed up, copied and rolled
 * back along with everything else in the save - a shop surviving a world restore is not something worth
 * hand-rolling.</p>
 *
 * <p>Deliberately its own store rather than a live view onto Shopkeepers. Reading the plugin's data at runtime
 * would tie every trade to the plugin still being installed and still storing things the same way; importing it
 * once and owning it afterwards is what lets the plugin be removed later without the shops going with it.</p>
 */
public final class ShopData extends SavedData {

    /** The file this ends up in, inside the world's data folder. */
    public static final String FILE_ID = ShopEditor.MOD_ID + "_shops";

    private static final String SHOPS = "Shops";
    private static final String OWNER = "Villager";
    private static final String NAME = "Name";
    private static final String TRADES = "Trades";
    private static final String ITEM = "Item";
    private static final String AMOUNT = "Amount";
    private static final String PRICE = "Price";

    /** NBT type id for a compound, for the typed list getter. */
    private static final int TAG_COMPOUND = 10;

    private final Map<UUID, Shop> shops = new LinkedHashMap<>();

    /** One NPC's shop: a display name and what it sells. */
    public record Shop(String name, List<TradeLine> trades) {

        public Shop {
            name = name == null ? "" : name;
            trades = List.copyOf(trades);
        }

        public static Shop empty() {
            return new Shop("", List.of());
        }
    }

    public ShopData() {
    }

    /** Loads the store for a level, creating it the first time. */
    public static ShopData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(ShopData::load, ShopData::new, FILE_ID);
    }

    public Shop shopOf(UUID villager) {
        return shops.get(villager);
    }

    public boolean isShop(UUID villager) {
        return shops.containsKey(villager);
    }

    /** Every shop, for the admin listing. */
    public Map<UUID, Shop> all() {
        return Map.copyOf(shops);
    }

    public int count() {
        return shops.size();
    }

    /**
     * Writes an NPC's shop.
     *
     * <p>Marked dirty on every change, because {@link SavedData} only writes when told to and a shop that was
     * edited but not marked is a shop that quietly reverts on restart.</p>
     */
    public void put(UUID villager, Shop shop) {
        shops.put(villager, shop);
        setDirty();
    }

    public void remove(UUID villager) {
        if (shops.remove(villager) != null) {
            setDirty();
        }
    }

    /** Replaces one shop's trades, keeping its name. */
    public void setTrades(UUID villager, List<TradeLine> trades) {
        Shop existing = shops.getOrDefault(villager, Shop.empty());
        put(villager, new Shop(existing.name(), trades));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Shop> entry : shops.entrySet()) {
            CompoundTag shopTag = new CompoundTag();
            shopTag.putUUID(OWNER, entry.getKey());
            shopTag.putString(NAME, entry.getValue().name());

            ListTag trades = new ListTag();
            for (TradeLine trade : entry.getValue().trades()) {
                CompoundTag tradeTag = new CompoundTag();
                tradeTag.putString(ITEM, trade.itemId());
                tradeTag.putInt(AMOUNT, trade.amount());
                tradeTag.putLong(PRICE, trade.priceCents());
                trades.add(tradeTag);
            }
            shopTag.put(TRADES, trades);
            list.add(shopTag);
        }
        tag.put(SHOPS, list);
        return tag;
    }

    /**
     * Reads the store back.
     *
     * <p>A row that no longer makes sense is dropped with a line in the log rather than aborting the load. The
     * alternative is that one bad entry - an item removed by a mod update, say - takes every shop on the server
     * with it, which is a much worse failure than losing the one shop that referenced it.</p>
     */
    public static ShopData load(CompoundTag tag) {
        ShopData data = new ShopData();
        ListTag list = tag.getList(SHOPS, TAG_COMPOUND);
        for (Tag element : list) {
            if (!(element instanceof CompoundTag shopTag)) {
                continue;
            }
            try {
                UUID villager = shopTag.getUUID(OWNER);
                String name = shopTag.getString(NAME);
                List<TradeLine> trades = new ArrayList<>();
                for (Tag tradeElement : shopTag.getList(TRADES, TAG_COMPOUND)) {
                    if (!(tradeElement instanceof CompoundTag tradeTag)) {
                        continue;
                    }
                    try {
                        trades.add(new TradeLine(tradeTag.getString(ITEM), tradeTag.getInt(AMOUNT),
                                tradeTag.getLong(PRICE)));
                    } catch (IllegalArgumentException badTrade) {
                        ShopEditor.LOGGER.warn("Trato descartado al cargar: {}", badTrade.getMessage());
                    }
                }
                data.shops.put(villager, new Shop(name, trades));
            } catch (RuntimeException badShop) {
                ShopEditor.LOGGER.warn("Tienda descartada al cargar: {}", badShop.toString());
            }
        }
        ShopEditor.LOGGER.info("Cargadas {} tiendas.", data.shops.size());
        return data;
    }
}
