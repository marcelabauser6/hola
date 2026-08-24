package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.client.layout.PanelMetrics;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.theme.StatsTheme;
import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.stats.EconomySnapshot;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Admin economy dashboard split into bounded, scrollable summary and ranking pages. */
public class StatsScreen extends Screen {
    private static final int ROW_H = 12;
    /** Blank space between the summary page's sections. */
    private static final int SPACER_H = 5;
    /** The closing note under the summary page. */
    private static final int NOTE_H = 13;
    /** Section heading plus the two column headings above the ranking rows. */
    private static final int RANKING_HEADER_ROWS = 6;

    private final EconomySnapshot snapshot;
    private ScreenLayout.Regions layout;
    private int page;
    private int scroll;
    private boolean clippingContent;
    private Component hoverTooltip;

    public StatsScreen(EconomySnapshot snapshot) {
        super(Component.translatable("gui.athens_coins.stats"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        layout = PanelMetrics.stats(width, height);
        ScreenLayout.Rect tabs = layout.tabs().inset(4);
        int gap = 4;
        int tabWidth = ScreenLayout.gridCellWidth(tabs, 2, gap);
        Component summary = Component.translatable("gui.athens_coins.stats_supply");
        Component ranking = Component.translatable("gui.athens_coins.stats_top");
        Button summaryButton = Button.builder(summary, ignored -> selectPage(0))
                .bounds(tabs.x(), tabs.y(), tabWidth, Math.min(18, tabs.height()))
                .tooltip(Tooltip.create(summary)).build();
        summaryButton.active = page != 0;
        addRenderableWidget(summaryButton);
        Button rankingButton = Button.builder(ranking, ignored -> selectPage(1))
                .bounds(tabs.x() + tabWidth + gap, tabs.y(), tabWidth, Math.min(18, tabs.height()))
                .tooltip(Tooltip.create(ranking)).build();
        rankingButton.active = page != 1;
        addRenderableWidget(rankingButton);

        // Two halves of one partition rather than two independent widths. The edit button used to be
        // floored at 90px while Close was pinned to the right edge at up to 82px, so any footer
        // narrower than 172px had them overlapping - and the one on top won the clicks.
        ScreenLayout.Rect footer = layout.footer().inset(6);
        int buttonHeight = Math.min(18, footer.height());
        ScreenLayout.Rect editCell = ScreenLayout.partition(footer, 2, 0);
        ScreenLayout.Rect closeCell = ScreenLayout.partition(footer, 2, 1);
        int editWidth = Math.max(0, Math.min(150, editCell.width() - gap));
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.stats_edit"),
                        ignored -> minecraft.setScreen(new StatsThemeEditorScreen(this)))
                .bounds(editCell.x(), footer.y(), editWidth, buttonHeight).build());
        int closeWidth = Math.min(82, closeCell.width());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"), ignored -> onClose())
                .bounds(closeCell.right() - closeWidth, footer.y(), closeWidth, buttonHeight).build());
    }

    private void selectPage(int target) {
        page = target;
        scroll = 0;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hoverTooltip = null;
        StatsTheme theme = StatsTheme.get();
        ScreenLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), theme.background());
        outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), theme.border());
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, layout.header().bottom() - 2, theme.titleBar());
        drawFitted(graphics, title.getString(), panel.x() + 8, panel.y() + 6,
                panel.width() - 16, theme.titleTextColor, theme, mouseX, mouseY);

        ScreenLayout.Rect content = layout.content().inset(8);
        clippingContent = true;
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        if (page == 0) {
            renderSummary(graphics, content, theme, mouseX, mouseY);
        } else {
            renderRanking(graphics, content, theme, mouseX, mouseY);
        }
        graphics.disableScissor();
        clippingContent = false;
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
        }
    }

    private void renderSummary(GuiGraphics graphics, ScreenLayout.Rect area, StatsTheme theme,
                               int mouseX, int mouseY) {
        DisplaySettings display = snapshot.display();
        String symbol = display.currencySymbol();
        int y = area.y() - scroll * ROW_H;
        y = section(graphics, Component.translatable("gui.athens_coins.stats_supply"), area, y, theme, mouseX, mouseY);
        int row = 0;
        y = kv(graphics, area, y, row++, Component.translatable("gui.athens_coins.stats_accounts"),
                String.valueOf(snapshot.accounts()), theme, theme.valueColor, mouseX, mouseY);
        y = kv(graphics, area, y, row++, Component.translatable("gui.athens_coins.stats_cash_total"),
                Money.format(snapshot.totalCashCents(), symbol), theme, theme.accentColor, mouseX, mouseY);
        y = kv(graphics, area, y, row++, Component.translatable("gui.athens_coins.stats_coin_value"),
                Money.format(snapshot.onlineCoinValueCents(), symbol), theme, theme.valueColor, mouseX, mouseY);
        y = kv(graphics, area, y, row, Component.translatable("gui.athens_coins.stats_supply_total"),
                Money.format(snapshot.totalSupplyCents(), symbol), theme, theme.accentColor, mouseX, mouseY);
        y += 5;

        y = section(graphics, Component.translatable("gui.athens_coins.stats_distribution"), area, y, theme, mouseX, mouseY);
        row = 0;
        y = kv(graphics, area, y, row++, Component.translatable("gui.athens_coins.stats_average"),
                Money.format(snapshot.averageCents(), symbol), theme, theme.valueColor, mouseX, mouseY);
        y = kv(graphics, area, y, row++, Component.translatable("gui.athens_coins.stats_median"),
                Money.format(snapshot.medianCents(), symbol), theme, theme.valueColor, mouseX, mouseY);
        y = kv(graphics, area, y, row++, Component.translatable("gui.athens_coins.stats_richest"),
                Money.format(snapshot.richestCents(), symbol), theme, theme.valueColor, mouseX, mouseY);
        y = kv(graphics, area, y, row, Component.translatable("gui.athens_coins.stats_concentration"),
                snapshot.topTenSharePercent() + "%", theme,
                snapshot.topTenSharePercent() >= 80 ? 0xFF6B6B : theme.valueColor, mouseX, mouseY);
        y += 5;

        y = section(graphics, Component.translatable("gui.athens_coins.stats_rates"), area, y, theme, mouseX, mouseY);
        row = 0;
        for (CoinType type : CoinType.ORDERED) {
            y = kv(graphics, area, y, row++,
                    Component.translatable("gui.athens_coins.stats_rate_line", type.shortName()),
                    Money.format(snapshot.coinRates()[type.ordinal()], symbol), theme,
                    display.colorOf(type), mouseX, mouseY);
        }
        drawFitted(graphics, Component.translatable("gui.athens_coins.stats_note").getString(),
                area.x(), y + 3, area.width(), theme.labelColor, theme, mouseX, mouseY);
    }

    private void renderRanking(GuiGraphics graphics, ScreenLayout.Rect area, StatsTheme theme,
                               int mouseX, int mouseY) {
        DisplaySettings display = snapshot.display();
        String symbol = display.currencySymbol();
        int y = area.y() - scroll * ROW_H;
        y = section(graphics, Component.translatable("gui.athens_coins.stats_coins_online",
                snapshot.onlinePlayers()), area, y, theme, mouseX, mouseY);
        int row = 0;
        for (CoinType type : CoinType.ORDERED) {
            y = kv(graphics, area, y, row++, type.shortName(),
                    Long.toString(snapshot.onlineCoinCounts()[type.ordinal()]), theme,
                    display.colorOf(type), mouseX, mouseY);
        }
        y += 5;
        y = section(graphics, Component.translatable("gui.athens_coins.stats_top"),
                area, y, theme, mouseX, mouseY);
        row = 0;
        if (snapshot.top().isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.stats_no_data").getString(),
                    area.x(), y + 2, area.width(), theme.labelColor, theme, mouseX, mouseY);
        }
        for (EconomySnapshot.Holder holder : snapshot.top()) {
            int rank = row + 1;
            y = kv(graphics, area, y, row++, Component.literal(rank + ". " + holder.name()),
                    Money.format(holder.cents(), symbol), theme, theme.valueColor, mouseX, mouseY);
        }
    }

    private int section(GuiGraphics graphics, Component label, ScreenLayout.Rect area, int y,
                        StatsTheme theme, int mouseX, int mouseY) {
        MutableComponent text = label.copy();
        if (theme.boldSections) {
            text = text.withStyle(ChatFormatting.BOLD);
        }
        drawFittedComponent(graphics, text, area.x(), y, area.width(), theme.sectionColor,
                theme, mouseX, mouseY);
        graphics.fill(area.x(), y + 10, area.right(), y + 11, 0x40FFFFFF);
        return y + ROW_H + 2;
    }

    private int kv(GuiGraphics graphics, ScreenLayout.Rect area, int y, int index,
                   Component label, String value, StatsTheme theme, int valueColor,
                   int mouseX, int mouseY) {
        if (index % 2 == 0) {
            graphics.fill(area.x() - 2, y - 1, area.right(), y + ROW_H - 2, theme.row());
        }
        // The label takes what the value leaves. Flooring it at 20px instead let it run under the
        // right-aligned value on a narrow panel; at zero it simply disappears, which is visible.
        int valueWidth = Math.min(area.width() * 48 / 100, Math.max(30, font.width(value)));
        int labelWidth = Math.max(0, area.width() - valueWidth - 8);
        MutableComponent styledLabel = label.copy();
        if (theme.italicLabels) {
            styledLabel = styledLabel.withStyle(ChatFormatting.ITALIC);
        }
        drawFittedComponent(graphics, styledLabel, area.x(), y, labelWidth,
                theme.labelColor, theme, mouseX, mouseY);
        String fittedValue = ScreenText.fit(font, value, valueWidth);
        graphics.drawString(font, fittedValue, area.right() - 4 - font.width(fittedValue), y,
                0xFF000000 | valueColor, theme.textShadow);
        if (ScreenText.wasTruncated(font, value, valueWidth)
                && hoverAllowed(mouseX, mouseY)
                && mouseX >= area.right() - valueWidth && mouseX < area.right()
                && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = Component.literal(value);
        }
        return y + ROW_H;
    }

    private void drawFittedComponent(GuiGraphics graphics, MutableComponent text, int x, int y,
                                     int maxWidth, int rgb, StatsTheme theme, int mouseX, int mouseY) {
        boolean truncated = font.width(text) > Math.max(0, maxWidth);
        if (!truncated) {
            graphics.drawString(font, text, x, y, 0xFF000000 | rgb, theme.textShadow);
        } else {
            int bodyWidth = Math.max(1, maxWidth - font.width("…"));
            java.util.List<FormattedCharSequence> lines = font.split(text, bodyWidth);
            FormattedCharSequence first = lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
            graphics.drawString(font, first, x, y, 0xFF000000 | rgb, theme.textShadow);
            graphics.drawString(font, "…", x + font.width(first), y,
                    0xFF000000 | rgb, theme.textShadow);
        }
        if (truncated && hoverAllowed(mouseX, mouseY)
                && mouseX >= x && mouseX < x + maxWidth && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = text;
        }
    }

    private void drawFitted(GuiGraphics graphics, String text, int x, int y, int maxWidth,
                            int rgb, StatsTheme theme, int mouseX, int mouseY) {
        String fitted = ScreenText.fit(font, text, maxWidth);
        graphics.drawString(font, fitted, x, y, 0xFF000000 | rgb, theme.textShadow);
        if (ScreenText.wasTruncated(font, text, maxWidth) && hoverAllowed(mouseX, mouseY)
                && mouseX >= x && mouseX < x + maxWidth && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = Component.literal(text);
        }
    }

    private boolean hoverAllowed(int mouseX, int mouseY) {
        return !clippingContent || layout.content().inset(8).contains(mouseX, mouseY);
    }

    /**
     * How far the summary page can scroll, derived from what it actually draws.
     *
     * <p>This was the literal {@code 17}. It happened to be right, but it was right by coincidence:
     * adding a single row or a section heading would have made the bottom of the page unreachable,
     * with nothing to point at the cause. Counting the parts means the bound moves with the content.</p>
     */
    private int summaryHeight() {
        int sections = 3;
        int keyValueRows = 4 + 4 + CoinType.ORDERED.length;
        int spacers = 2;
        return sections * (ROW_H + 2) + keyValueRows * ROW_H + spacers * SPACER_H + NOTE_H;
    }

    private int totalRows() {
        if (page != 0) {
            return RANKING_HEADER_ROWS + snapshot.top().size();
        }
        return (summaryHeight() + ROW_H - 1) / ROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ScreenLayout.Rect content = layout.content().inset(8);
        int visible = Math.max(1, ScreenLayout.visibleRows(content, ROW_H));
        int max = Math.max(0, totalRows() - visible);
        scroll = ScreenLayout.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }

    static void outline(GuiGraphics graphics, int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + 1, argb);
        graphics.fill(x, y + h - 1, x + w, y + h, argb);
        graphics.fill(x, y, x + 1, y + h, argb);
        graphics.fill(x + w - 1, y, x + w, y + h, argb);
    }
}
