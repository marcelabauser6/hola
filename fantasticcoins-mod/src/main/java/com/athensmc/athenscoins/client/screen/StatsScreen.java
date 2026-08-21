package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.client.theme.StatsTheme;
import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.stats.EconomySnapshot;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Admin-only view of the server economy.
 *
 * <p>Everything is drawn with plain fills so {@link StatsTheme} can control the whole look:
 * colours, panel opacity and text formatting.</p>
 */
public class StatsScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;
    private static final int ROW_H = 12;

    private final EconomySnapshot snapshot;
    private int leftPos;
    private int topPos;

    public StatsScreen(EconomySnapshot snapshot) {
        super(Component.translatable("gui.athens_coins.stats"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.athens_coins.stats_edit"),
                        button -> minecraft.setScreen(new StatsThemeEditorScreen(this)))
                .bounds(leftPos + 8, topPos + PANEL_H - 26, 150, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.athens_coins.close"),
                        button -> onClose())
                .bounds(leftPos + PANEL_W - 90, topPos + PANEL_H - 26, 82, 18)
                .build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        StatsTheme theme = StatsTheme.get();
        DisplaySettings display = snapshot.display();
        String symbol = display.currencySymbol();

        // panel
        graphics.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, theme.background());
        outline(graphics, leftPos, topPos, PANEL_W, PANEL_H, theme.border());
        graphics.fill(leftPos + 1, topPos + 1, leftPos + PANEL_W - 1, topPos + 18, theme.titleBar());

        drawText(graphics, title.getString(),
                leftPos + PANEL_W / 2 - font.width(title.getString()) / 2, topPos + 6,
                theme.titleTextColor, theme);

        int leftX = leftPos + 10;
        int rightX = leftPos + 178;
        int y = topPos + 24;

        // ---- money supply
        section(graphics, Component.translatable("gui.athens_coins.stats_supply"), leftX, y, theme);
        y += ROW_H + 2;
        int row = 0;
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_accounts"),
                String.valueOf(snapshot.accounts()), theme, theme.valueColor);
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_cash_total"),
                Money.format(snapshot.totalCashCents(), symbol), theme, theme.accentColor);
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_coin_value"),
                Money.format(snapshot.onlineCoinValueCents(), symbol), theme, theme.valueColor);
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_supply_total"),
                Money.format(snapshot.totalSupplyCents(), symbol), theme, theme.accentColor);

        y += 6;
        section(graphics, Component.translatable("gui.athens_coins.stats_distribution"), leftX, y, theme);
        y += ROW_H + 2;
        row = 0;
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_average"),
                Money.format(snapshot.averageCents(), symbol), theme, theme.valueColor);
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_median"),
                Money.format(snapshot.medianCents(), symbol), theme, theme.valueColor);
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_richest"),
                Money.format(snapshot.richestCents(), symbol), theme, theme.valueColor);
        y = kv(graphics, leftX, y, row++, 158,
                Component.translatable("gui.athens_coins.stats_concentration"),
                snapshot.topTenSharePercent() + "%", theme,
                snapshot.topTenSharePercent() >= 80 ? 0xFF6B6B : theme.valueColor);

        y += 6;
        section(graphics, Component.translatable("gui.athens_coins.stats_rates"), leftX, y, theme);
        y += ROW_H + 2;
        row = 0;
        for (CoinType type : CoinType.ORDERED) {
            y = kv(graphics, leftX, y, row++, 158,
                    Component.translatable("gui.athens_coins.stats_rate_line", type.shortName()),
                    Money.format(snapshot.coinRates()[type.ordinal()], symbol),
                    theme, display.colorOf(type));
        }

        // ---- right column: coins in hand and the leaderboard
        int ry = topPos + 24;
        section(graphics, Component.translatable("gui.athens_coins.stats_coins_online",
                snapshot.onlinePlayers()), rightX, ry, theme);
        ry += ROW_H + 2;
        row = 0;
        for (CoinType type : CoinType.ORDERED) {
            ry = kv(graphics, rightX, ry, row++, 152, type.shortName(),
                    Long.toString(snapshot.onlineCoinCounts()[type.ordinal()]),
                    theme, display.colorOf(type));
        }

        ry += 6;
        section(graphics, Component.translatable("gui.athens_coins.stats_top"), rightX, ry, theme);
        ry += ROW_H + 2;
        row = 0;
        if (snapshot.top().isEmpty()) {
            drawText(graphics, Component.translatable("gui.athens_coins.stats_no_data").getString(),
                    rightX, ry + 2, theme.labelColor, theme);
        }
        for (EconomySnapshot.Holder holder : snapshot.top()) {
            ry = kv(graphics, rightX, ry, row++, 152,
                    Component.literal((row) + ". " + holder.name()),
                    Money.format(holder.cents(), symbol), theme, theme.valueColor);
        }

        // caveat about coin counts
        drawText(graphics, Component.translatable("gui.athens_coins.stats_note").getString(),
                leftPos + 10, topPos + PANEL_H - 40, theme.labelColor, theme);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void section(GuiGraphics graphics, Component label, int x, int y, StatsTheme theme) {
        MutableComponent text = label.copy();
        if (theme.boldSections) {
            text = text.withStyle(ChatFormatting.BOLD);
        }
        graphics.drawString(font, text, x, y, 0xFF000000 | theme.sectionColor, theme.textShadow);
        graphics.fill(x, y + 10, x + 152, y + 11, 0x40FFFFFF);
    }

    /** Draws one banded "label ....... value" row and returns the next y. */
    private int kv(GuiGraphics graphics, int x, int y, int index, int width,
                   Component label, String value, StatsTheme theme, int valueColor) {
        if (index % 2 == 0) {
            graphics.fill(x - 2, y - 1, x + width, y + ROW_H - 2, theme.row());
        }
        MutableComponent text = label.copy();
        if (theme.italicLabels) {
            text = text.withStyle(ChatFormatting.ITALIC);
        }
        graphics.drawString(font, text, x, y, 0xFF000000 | theme.labelColor, theme.textShadow);
        graphics.drawString(font, value, x + width - 4 - font.width(value), y,
                0xFF000000 | valueColor, theme.textShadow);
        return y + ROW_H;
    }

    private void drawText(GuiGraphics graphics, String text, int x, int y, int rgb, StatsTheme theme) {
        graphics.drawString(font, text, x, y, 0xFF000000 | rgb, theme.textShadow);
    }

    static void outline(GuiGraphics graphics, int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + 1, argb);
        graphics.fill(x, y + h - 1, x + w, y + h, argb);
        graphics.fill(x, y, x + 1, y + h, argb);
        graphics.fill(x + w - 1, y, x + w, y + h, argb);
    }
}
