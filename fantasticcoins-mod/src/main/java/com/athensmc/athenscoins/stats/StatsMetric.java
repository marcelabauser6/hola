package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;

import javax.annotation.Nullable;

/**
 * One thing a stats hologram can show on a line.
 *
 * <p>This is the vocabulary of the hologram editor: every entry is a label the player can pick from a
 * list, plus the rule for turning a snapshot into the text on the right of that line. Keeping the two
 * together is the point - the alternative was the stats screen's arrangement, where each row's label
 * and its value were written out by hand in a render method, so the set of available figures existed
 * only as a sequence of calls and could not be offered as a choice.</p>
 *
 * <p><b>Persisted by name, never by ordinal.</b> {@link #byName} is what reads a saved hologram back.
 * Ordinals would mean that inserting a metric in the middle of this enum silently rewrote every
 * hologram already built in the world - the line that said "cash in circulation" would come back as
 * something else. Names cost a few bytes and make the order here free to change.</p>
 */
public enum StatsMetric {

    /** A blank line, for grouping. */
    BLANK("blank", Kind.SPACER),
    /** Free text: the line is whatever label the player typed, with no value. */
    TEXT("text", Kind.LABEL_ONLY),

    ACCOUNTS("accounts", Kind.COUNT),
    ONLINE_PLAYERS("online_players", Kind.COUNT),

    TOTAL_CASH("total_cash", Kind.MONEY, true),
    COIN_VALUE("coin_value", Kind.MONEY),
    TOTAL_SUPPLY("total_supply", Kind.MONEY, true),

    AVERAGE("average", Kind.MONEY),
    MEDIAN("median", Kind.MONEY),
    RICHEST("richest", Kind.MONEY),
    TOP_TEN_SHARE("top_ten_share", Kind.PERCENT),

    RATE_BRONZE("rate_bronze", Kind.MONEY, CoinType.BRONZE),
    RATE_SILVER("rate_silver", Kind.MONEY, CoinType.SILVER),
    RATE_GOLD("rate_gold", Kind.MONEY, CoinType.GOLD),

    COINS_BRONZE("coins_bronze", Kind.COUNT, CoinType.BRONZE),
    COINS_SILVER("coins_silver", Kind.COUNT, CoinType.SILVER),
    COINS_GOLD("coins_gold", Kind.COUNT, CoinType.GOLD),

    /** Expands into several lines: the richest accounts, as many as the hologram is set to show. */
    TOP_HOLDERS("top_holders", Kind.TABLE);

    /** How a metric's value is rendered, and whether it has one at all. */
    public enum Kind {
        MONEY, COUNT, PERCENT, SPACER, LABEL_ONLY, TABLE
    }

    private final String id;
    private final Kind kind;
    private final boolean accent;
    @Nullable
    private final CoinType coin;

    StatsMetric(String id, Kind kind) {
        this(id, kind, false, null);
    }

    StatsMetric(String id, Kind kind, boolean accent) {
        this(id, kind, accent, null);
    }

    StatsMetric(String id, Kind kind, CoinType coin) {
        this(id, kind, false, coin);
    }

    StatsMetric(String id, Kind kind, boolean accent, @Nullable CoinType coin) {
        this.id = id;
        this.kind = kind;
        this.accent = accent;
        this.coin = coin;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    /** True for the headline figures, which the hologram draws in its accent colour. */
    public boolean accent() {
        return accent;
    }

    /** The denomination this metric belongs to, so its line can take that coin's colour. */
    @Nullable
    public CoinType coin() {
        return coin;
    }

    public String labelKey() {
        return "metric.athens_coins." + id;
    }

    /** True when the line shows a label and nothing else. */
    public boolean labelOnly() {
        return kind == Kind.SPACER || kind == Kind.LABEL_ONLY;
    }

    /**
     * The value for this metric, already formatted.
     *
     * <p>Formatted here rather than on the way out of the server so the hologram keeps using
     * {@link Money#format}: the currency symbol and the strict two decimals are the same rule the
     * rest of the mod follows, and a hologram that rounded differently from the ATM beside it would
     * be a bug nobody would think to look for.</p>
     */
    public String format(EconomySnapshot snapshot) {
        String symbol = snapshot.display().currencySymbol();
        return switch (this) {
            case ACCOUNTS -> String.valueOf(snapshot.accounts());
            case ONLINE_PLAYERS -> String.valueOf(snapshot.onlinePlayers());
            case TOTAL_CASH -> Money.format(snapshot.totalCashCents(), symbol);
            case COIN_VALUE -> Money.format(snapshot.onlineCoinValueCents(), symbol);
            case TOTAL_SUPPLY -> Money.format(snapshot.totalSupplyCents(), symbol);
            case AVERAGE -> Money.format(snapshot.averageCents(), symbol);
            case MEDIAN -> Money.format(snapshot.medianCents(), symbol);
            case RICHEST -> Money.format(snapshot.richestCents(), symbol);
            case TOP_TEN_SHARE -> snapshot.topTenSharePercent() + "%";
            case RATE_BRONZE, RATE_SILVER, RATE_GOLD ->
                    Money.format(snapshot.coinRates()[coinOrdinal()], symbol);
            case COINS_BRONZE, COINS_SILVER, COINS_GOLD ->
                    String.valueOf(snapshot.onlineCoinCounts()[coinOrdinal()]);
            case BLANK, TEXT, TOP_HOLDERS -> "";
        };
    }

    private int coinOrdinal() {
        return coin == null ? 0 : coin.ordinal();
    }

    /** The saved form. Unknown ids fall back to a blank line rather than dropping out silently. */
    public static StatsMetric byName(String name) {
        for (StatsMetric metric : values()) {
            if (metric.id.equalsIgnoreCase(name)) {
                return metric;
            }
        }
        return BLANK;
    }

    /** Everything an editor can offer, in the order it should be listed. */
    public static StatsMetric[] selectable() {
        return values();
    }
}
