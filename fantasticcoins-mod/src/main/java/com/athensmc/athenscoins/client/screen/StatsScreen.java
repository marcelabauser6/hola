package com.athensmc.athenscoins.client.screen;

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
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;
    private static final int ROW_H = 12;

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
        ScreenLayout.Rect panel = ScreenLayout.centeredPanel(width, height, PANEL_W, PANEL_H);
        layout = ScreenLayout.regions(panel, 20, 22, 28);
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

        ScreenLayout.Rect footer = layout.footer().inset(6);
        int editWidth = Math.min(150, Math.max(90, footer.width() / 2));
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.stats_edit"),
                        ignored -> minecraft.setScreen(new StatsThemeEditorScreen(this)))
                .bounds(footer.x(), footer.y(), editWidth, Math.min(18, footer.height())).build());
        int closeWidth = Math.min(82, footer.width());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"), ignored -> onClose())
                .bounds(footer.right() - closeWidth, footer.y(), closeWidth, Math.min(18, footer.height())).build());
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
        int valueWidth = Math.min(area.width() * 48 / 100, Math.max(30, font.width(value)));
        int labelWidth = Math.max(20, area.width() - valueWidth - 8);
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

    private int totalRows() {
        return page == 0 ? 17 : 6 + snapshot.top().size();
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
