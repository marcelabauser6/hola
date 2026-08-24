package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Turns a hologram's configuration and a snapshot into the exact rows to draw.
 *
 * <p>One builder, two callers: the in-world renderer and the editor's live preview. That is the whole
 * point of the class. A preview that laid its rows out with its own copy of these rules would drift
 * from the hologram beside it, and the drift would be invisible in review - both would look plausible,
 * and only someone comparing the screen to the block would notice that the editor was lying about what
 * it was about to produce.</p>
 *
 * <p>No Minecraft client types here, so the rules can be exercised from a plain JVM.</p>
 */
public final class HologramLines {

    /** Ceiling on the rows a hologram can produce, once the top-holders table has expanded. */
    public static final int MAX_ROWS = HologramConfig.MAX_LINES + HologramConfig.MAX_TOP_ROWS;

    /**
     * One drawn row.
     *
     * @param label what goes on the left; empty for a spacer, or when labels are switched off
     * @param value what goes on the right; empty for a spacer or a free-text line
     * @param rgb   the value's colour, already resolved through the accent and per-coin rules
     */
    public record Row(Component label, String value, int rgb) {

        public boolean isSpacer() {
            return label.getString().isEmpty() && value.isEmpty();
        }

        /** True when there is nothing on the left, so the value should be centred instead. */
        public boolean valueOnly() {
            return label.getString().isEmpty() && !value.isEmpty();
        }
    }

    /** Gap between a label and its value, so the two columns never touch on a narrow board. */
    public static final int COLUMN_GAP = 8;

    private HologramLines() {
    }

    /**
     * The width of the widest row, which is what the panel is sized from.
     *
     * <p>Shared by the in-world renderer and the editor's preview, and it has to be: those two draw
     * with different APIs but must agree on how wide the board comes out, or the preview would show a
     * board that fits and the hologram would clip. The two text measurers are passed in because
     * {@code Font} is a client type and this class deliberately is not.</p>
     *
     * <p>A label and a value on the same row are measured <em>together</em>, with the gap between them.
     * Measuring the two columns independently and adding the maxima looked equivalent and was not: it
     * over-sizes the board whenever the longest label and the longest value are on different rows.</p>
     */
    public static int panelWidth(List<Row> rows, Component title,
                                 ToIntFunction<Component> componentWidth,
                                 ToIntFunction<String> stringWidth) {
        int widest = title == null ? 0 : componentWidth.applyAsInt(title);
        for (Row row : rows) {
            int width = row.valueOnly()
                    ? stringWidth.applyAsInt(row.value())
                    : componentWidth.applyAsInt(row.label())
                            + (row.value().isEmpty()
                                    ? 0 : COLUMN_GAP + stringWidth.applyAsInt(row.value()));
            widest = Math.max(widest, width);
        }
        return Math.max(1, widest);
    }

    public static List<Row> build(HologramConfig config, EconomySnapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        for (HologramConfig.Line line : config.lines()) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            StatsMetric metric = line.metric();
            switch (metric.kind()) {
                case SPACER -> rows.add(new Row(Component.empty(), "", config.labelColor()));
                case LABEL_ONLY -> rows.add(new Row(Component.literal(line.label()), "",
                        config.labelColor()));
                case TABLE -> appendTopHolders(rows, config, snapshot);
                default -> rows.add(new Row(labelOf(config, line), metric.format(snapshot),
                        valueColorOf(config, snapshot, metric)));
            }
        }
        return rows;
    }

    /**
     * The richest accounts, as many rows as the hologram is set to show.
     *
     * <p>Expanded here rather than being a fixed set of "top 1", "top 2" metrics: the number of places
     * is a setting, and six separate metrics would mean an admin wanting a top three had to add three
     * lines and remember not to leave a gap in the numbering.</p>
     */
    private static void appendTopHolders(List<Row> rows, HologramConfig config,
                                        EconomySnapshot snapshot) {
        if (snapshot.top().isEmpty()) {
            rows.add(new Row(Component.translatable("gui.athens_coins.stats_no_data"), "",
                    config.labelColor()));
            return;
        }
        String symbol = snapshot.display().currencySymbol();
        int places = Math.min(config.topRows(), snapshot.top().size());
        for (int i = 0; i < places && rows.size() < MAX_ROWS; i++) {
            EconomySnapshot.Holder holder = snapshot.top().get(i);
            Component label = config.showLabels()
                    ? Component.literal((i + 1) + ". " + holder.name())
                    : Component.empty();
            String value = config.showLabels()
                    ? Money.format(holder.cents(), symbol)
                    : (i + 1) + ". " + holder.name() + "  " + Money.format(holder.cents(), symbol);
            rows.add(new Row(label, value, config.valueColor()));
        }
    }

    private static Component labelOf(HologramConfig config, HologramConfig.Line line) {
        if (!config.showLabels()) {
            return Component.empty();
        }
        return line.hasCustomLabel()
                ? Component.literal(line.label())
                : Component.translatable(line.metric().labelKey());
    }

    /**
     * A value's colour.
     *
     * <p>Coin figures take the denomination's own colour from the server config, so a hologram agrees
     * with every other screen about which colour bronze is. A coin colour that has never been set comes
     * back as zero, which would draw as black on a dark panel - so that case falls through to the
     * hologram's own value colour rather than being trusted.</p>
     */
    private static int valueColorOf(HologramConfig config, EconomySnapshot snapshot,
                                    StatsMetric metric) {
        CoinType coin = metric.coin();
        if (coin != null) {
            int coinColor = snapshot.display().colorOf(coin) & 0xFFFFFF;
            if (coinColor != 0) {
                return coinColor;
            }
        }
        return metric.accent() ? config.accentColor() : config.valueColor();
    }
}
