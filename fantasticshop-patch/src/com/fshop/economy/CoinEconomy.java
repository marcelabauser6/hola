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
 * <h2>Cash is counted in cents</h2>
 * FShop prices are plain {@code long}s, so a cash price is stored as a count of cents: {@code 150}
 * means 1.50. Nothing in the shop needs to know that - totals, balance checks and earnings all stay
 * plain integer arithmetic - but anywhere a cash figure reaches the screen it goes through
 * {@link #formatAmount(int, long)} so the player reads 1.50 rather than 150.
 */
public final class CoinEconomy {
    public static final int BRONZE = 0;
    public static final int SILVER = 1;
    public static final int GOLD = 2;
    /** Digital Fantastic Cash. */
    public static final int CASH = 3;

    /** Cents in one unit of cash. */
    public static final long CASH_SCALE = 100L;

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

    /**
     * Formats a raw price or total for display.
     *
     * <p>Coin amounts are plain counts. Cash amounts are stored in cents, so they are rendered
     * with the currency symbol and two decimals.</p>
     */
    public static String formatAmount(int type, long raw) {
        if (type != CASH) {
            return Long.toString(raw);
        }
        long abs = Math.abs(raw);
        String digits = String.format(java.util.Locale.ROOT, "%d.%02d", abs / CASH_SCALE, abs % CASH_SCALE);
        return (raw < 0 ? "-" : "") + cashSymbol() + digits;
    }

    /** Suffix used in compact lists: the symbol for cash, a short letter for coins. */
    public static String amountSuffix(int type) {
        return type == CASH ? "" : "";
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
            return CASH_MOD ? FantasticCurrencyAPI.getDisplayBalance(player) : 0L;
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
            return FantasticCurrencyAPI.charge(serverPlayer.f_8924_,
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

    /**
     * Pays out.
     *
     * @return whether the money actually reached the player. Cash can genuinely fail - it needs a
     *         live bank account, which may have been closed since the sale - and the caller has to
     *         know, because it is about to clear the pending balance. This used to return
     *         {@code void} and discard the currency mod's answer, so a failed cash payout looked
     *         exactly like a successful one and the earnings were wiped either way.
     */
    public static boolean deposit(Player player, int type, long count) {
        if (count <= 0L) {
            return true;
        }
        if (type == CASH) {
            if (CASH_MOD && player instanceof ServerPlayer serverPlayer) {
                // depositToAccount, not deposit: the void overload discards exactly the answer we
                // need, which is whether the money landed anywhere.
                return FantasticCurrencyAPI.depositToAccount(serverPlayer.f_8924_,
                        serverPlayer.m_20148_(), count);
            }
            return false;
        }
        Item coin = CoinEconomy.coinItem(type);
        if (coin == Items.f_41852_) {
            return false;
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
        return true;
    }

    /**
     * Settles a sale, routing cash through the shop's linked bank account.
     *
     * <p>Cash payouts went to whatever account the seller happened to have at collect time, which
     * ignored the number the shop actually stored - the whole point of linking a shop to an account.
     * The currency mod already exposes a settlement call that refuses unless the linked account still
     * belongs to the seller; this is what uses it. Coins are physical items and have no account, so
     * they take the ordinary path.</p>
     *
     * @param linkedAccount the shop's stored account number, or 0 for the server shop
     */
    public static boolean depositSale(Player player, int type, long count, int linkedAccount) {
        if (count <= 0L) {
            return true;
        }
        if (type != CASH) {
            return CoinEconomy.deposit(player, type, count);
        }
        if (!CASH_MOD || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (linkedAccount <= 0) {
            // The server shop has no account of its own; credit the operator collecting it.
            return FantasticCurrencyAPI.depositToAccount(serverPlayer.f_8924_,
                    serverPlayer.m_20148_(), count);
        }
        return FantasticCurrencyAPI.creditSaleToAccount(serverPlayer.f_8924_,
                serverPlayer.m_20148_(), linkedAccount, count);
    }
}
