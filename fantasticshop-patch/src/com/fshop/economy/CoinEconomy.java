package com.fshop.economy;

import com.athensmc.athenscoins.api.FantasticCurrencyAPI;
import com.fshop.config.FShopConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * FShop currency layer.
 *
 * <p>Originally three physical coin items behind an {@code int type} (0 bronze, 1 silver,
 * 2 gold). Extended with {@link #CASH} (3), the digital Fantastic Cash balance from the currency
 * mod, so an offer can be priced in coins or in cash.</p>
 *
 * <h2>Cash is counted in whole units</h2>
 * Accounts keep two decimals, but FShop prices are plain {@code long}s. Cash here is therefore
 * measured in whole units: a price of {@code 10} means 10.00, and a player holding 9.99 reads as
 * 9 and cannot afford it. Their remaining cents are never touched. This keeps every existing
 * price field, packet and comparison working untouched.
 */
public final class CoinEconomy {
    public static final int BRONZE = 0;
    public static final int SILVER = 1;
    public static final int GOLD = 2;
    /** Digital Fantastic Cash. */
    public static final int CASH = 3;

    /** Number of selectable currencies, used by the UI loops. */
    public static final int TYPES = 4;

    /** Whether the currency mod is installed at all; every cash path checks this first. */
    private static final boolean CASH_MOD = ModList.get().isLoaded("athens_coins");

    private CoinEconomy() {
    }

    public static boolean isCash(int type) {
        return type == CASH;
    }

    /** True when cash can actually be used as a currency. */
    public static boolean cashAvailable() {
        return CASH_MOD;
    }

    /** Clamps a currency id to something valid, dropping cash if the mod is missing. */
    public static int sanitize(int type) {
        if (type == CASH) {
            return CASH_MOD ? CASH : GOLD;
        }
        return Math.max(0, Math.min(GOLD, type));
    }

    private static Item byId(String id) {
        Item i = (Item) ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return i == null ? Items.f_41852_ : i;
    }

    public static Item coinItem(int type) {
        return switch (type) {
            case CASH -> Items.f_41852_;   // cash has no physical item
            case GOLD -> CoinEconomy.byId((String) FShopConfig.GOLD_COIN_ID.get());
            case SILVER -> CoinEconomy.byId((String) FShopConfig.SILVER_COIN_ID.get());
            default -> CoinEconomy.byId((String) FShopConfig.BRONZE_COIN_ID.get());
        };
    }

    public static ItemStack coinIcon(int type) {
        if (type == CASH) {
            return ItemStack.f_41583_;     // drawn as a currency symbol instead of an item
        }
        Item i = CoinEconomy.coinItem(type);
        return i == Items.f_41852_ ? ItemStack.f_41583_ : new ItemStack((ItemLike) i);
    }

    public static String coinKey(int type) {
        return switch (type) {
            case CASH -> "fshop.coin.cash";
            case GOLD -> "fshop.coin.gold";
            case SILVER -> "fshop.coin.silver";
            default -> "fshop.coin.bronze";
        };
    }

    public static int coinColor(int type) {
        return switch (type) {
            case CASH -> 0xFF4CD964;
            case GOLD -> -11702;
            case SILVER -> -3288358;
            default -> -2065640;
        };
    }

    public static String coinColorCode(int type) {
        return switch (type) {
            case CASH -> "\u00a7a";
            case GOLD -> "\u00a7e";
            case SILVER -> "\u00a77";
            default -> "\u00a76";
        };
    }

    /** Symbol shown where cash has no item icon, e.g. "$". */
    public static String cashSymbol() {
        return CASH_MOD ? FantasticCurrencyAPI.currencySymbol() : "$";
    }

    /** Display name of the digital currency. */
    public static String cashName() {
        return CASH_MOD ? FantasticCurrencyAPI.currencyName() : "Cash";
    }

    public static boolean available() {
        if (CASH_MOD) {
            return true;
        }
        return CoinEconomy.coinItem(BRONZE) != Items.f_41852_
                || CoinEconomy.coinItem(SILVER) != Items.f_41852_
                || CoinEconomy.coinItem(GOLD) != Items.f_41852_;
    }

    /**
     * How much of a currency the player has.
     *
     * <p>Coins are counted from the inventory, which works on both sides. Cash is read from the
     * account on the server and from the value the currency mod pushed to the client otherwise,
     * so the shop GUIs can display it while rendering.</p>
     */
    public static long balance(Player player, int type) {
        if (type == CASH) {
            return CASH_MOD ? FantasticCurrencyAPI.getDisplayBalanceUnits(player) : 0L;
        }
        Item coin = CoinEconomy.coinItem(type);
        if (coin == Items.f_41852_) {
            return 0L;
        }
        long total = 0L;
        Inventory inv = player.m_150109_();
        for (int i = 0; i < inv.m_6643_(); ++i) {
            ItemStack s = inv.m_8020_(i);
            if (s.m_41720_() != coin) continue;
            total += (long) s.m_41613_();
        }
        return total;
    }

    /** Takes payment. All or nothing. */
    public static boolean withdraw(Player player, int type, long count) {
        if (count <= 0L) {
            return true;
        }
        if (type == CASH) {
            if (!CASH_MOD || !(player instanceof ServerPlayer serverPlayer)) {
                return false;
            }
            return FantasticCurrencyAPI.chargeUnits(serverPlayer.f_8924_,
                    serverPlayer.m_20148_(), count);
        }
        if (CoinEconomy.balance(player, type) < count) {
            return false;
        }
        Item coin = CoinEconomy.coinItem(type);
        long remaining = count;
        Inventory inv = player.m_150109_();
        for (int i = 0; i < inv.m_6643_() && remaining > 0L; ++i) {
            ItemStack s = inv.m_8020_(i);
            if (s.m_41720_() != coin) continue;
            int take = (int) Math.min((long) s.m_41613_(), remaining);
            s.m_41774_(take);
            remaining -= (long) take;
        }
        return true;
    }

    /** Pays out. */
    public static void deposit(Player player, int type, long count) {
        if (count <= 0L) {
            return;
        }
        if (type == CASH) {
            if (CASH_MOD && player instanceof ServerPlayer serverPlayer) {
                FantasticCurrencyAPI.depositUnits(serverPlayer.f_8924_,
                        serverPlayer.m_20148_(), count);
            }
            return;
        }
        Item coin = CoinEconomy.coinItem(type);
        if (coin == Items.f_41852_) {
            return;
        }
        int max = coin.m_41459_();
        while (count > 0L) {
            int n = (int) Math.min((long) max, count);
            ItemStack stack = new ItemStack((ItemLike) coin, n);
            if (!player.m_150109_().m_36054_(stack)) {
                player.m_36176_(stack, false);
            }
            count -= (long) n;
        }
    }
}
