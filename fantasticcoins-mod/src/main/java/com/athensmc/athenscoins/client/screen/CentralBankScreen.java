package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.network.C2SCentralActionPacket;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenCentralPacket;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The central bank: every commercial bank at a glance, cash injections, and the official rates.
 *
 * <p>Moving an official rate shifts the band all the banks have to price inside, so this screen
 * shows the resulting floor and ceiling next to each one.</p>
 */
public class CentralBankScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;
    private static final int ROW_H = 14;
    private static final int LIST_ROWS = 8;

    private final BlockPos pos;
    private final List<S2COpenCentralPacket.BankRow> banks;
    private final S2COpenCentralPacket data;

    private int leftPos;
    private int topPos;
    private int scroll;
    private int selected;
    private EditBox amountBox;

    public CentralBankScreen(S2COpenCentralPacket packet) {
        super(Component.translatable("gui.athens_coins.central_title"));
        this.pos = packet.pos();
        this.banks = packet.banks();
        this.data = packet;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;
        if (selected >= banks.size()) {
            selected = 0;
        }

        amountBox = new EditBox(font, leftPos + 8, topPos + PANEL_H - 70, 90, 16,
                Component.translatable("gui.athens_coins.bank_value"));
        amountBox.setValue("0");
        amountBox.setMaxLength(12);
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.central_inject"),
                        b -> act(C2SCentralActionPacket.Action.INJECT))
                .bounds(leftPos + 102, topPos + PANEL_H - 70, 108, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_inject_tip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.central_drain"),
                        b -> act(C2SCentralActionPacket.Action.DRAIN))
                .bounds(leftPos + 214, topPos + PANEL_H - 70, 108, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_drain_tip")))
                .build());

        int y = topPos + PANEL_H - 48;
        int x = leftPos + 8;
        for (CoinType type : CoinType.ORDERED) {
            C2SCentralActionPacket.Action action = switch (type) {
                case BRONZE -> C2SCentralActionPacket.Action.SET_OFFICIAL_BRONZE;
                case SILVER -> C2SCentralActionPacket.Action.SET_OFFICIAL_SILVER;
                case GOLD -> C2SCentralActionPacket.Action.SET_OFFICIAL_GOLD;
            };
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.athens_coins.central_set_rate", type.shortName()),
                            b -> act(action))
                    .bounds(x, y, 104, 16)
                    .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_rate_tip",
                            data.marginPercent())))
                    .build());
            x += 106;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"),
                        b -> onClose())
                .bounds(leftPos + PANEL_W - 90, topPos + PANEL_H - 26, 82, 18)
                .build());
    }

    private long amount() {
        String raw = amountBox == null ? "0" : amountBox.getValue().trim();
        if (raw.contains(".") || raw.contains(",")) {
            try {
                return Money.parse(raw);
            } catch (Money.InvalidAmountException ignored) {
                return 0L;
            }
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void act(C2SCentralActionPacket.Action action) {
        java.util.UUID target = banks.isEmpty() ? null : banks.get(selected).id();
        ModNetwork.toServer(new C2SCentralActionPacket(pos, action, target, amount()));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, 0xF00E1018);
        StatsScreen.outline(graphics, leftPos, topPos, PANEL_W, PANEL_H, 0xFF7FB2E5);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + PANEL_W - 1, topPos + 18, 0xFF1B2A44);
        graphics.drawString(font, title, leftPos + PANEL_W / 2 - font.width(title) / 2, topPos + 6,
                0xFFBFD8FF, true);

        // Official rates and the band they impose.
        int y = topPos + 22;
        graphics.drawString(font, Component.translatable("gui.athens_coins.central_official"),
                leftPos + 8, y, 0xFF7FB2E5, false);
        y += 11;
        for (CoinType type : CoinType.ORDERED) {
            String line = type.shortName().getString() + ": "
                    + Money.format(data.official(type), "$")
                    + "   [" + Money.format(data.floor(type), "$")
                    + " - " + Money.format(data.ceiling(type), "$") + "]";
            graphics.drawString(font, line, leftPos + 14, y, 0xFFB8C8DC, false);
            y += 10;
        }
        String issued = Component.translatable("gui.athens_coins.central_issued",
                Money.format(data.totalIssued(), "$")).getString();
        graphics.drawString(font, issued, leftPos + 180, topPos + 33, 0xFF8FE3FF, false);
        String count = Component.translatable("gui.athens_coins.central_banks",
                banks.size()).getString();
        graphics.drawString(font, count, leftPos + 180, topPos + 44, 0xFFB8C8DC, false);

        // Bank list.
        int listTop = topPos + 74;
        graphics.drawString(font, Component.translatable("gui.athens_coins.central_list"),
                leftPos + 8, listTop, 0xFF7FB2E5, false);
        graphics.fill(leftPos + 8, listTop + 10, leftPos + PANEL_W - 8, listTop + 11, 0x40FFFFFF);

        if (banks.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.athens_coins.central_no_banks"),
                    leftPos + 8, listTop + 16, 0xFF888888, false);
        }
        int rowY = listTop + 15;
        for (int i = scroll; i < Math.min(banks.size(), scroll + LIST_ROWS); i++) {
            S2COpenCentralPacket.BankRow row = banks.get(i);
            if (i == selected) {
                graphics.fill(leftPos + 8, rowY - 1, leftPos + PANEL_W - 8, rowY + ROW_H - 2,
                        0x400090FF);
            }
            graphics.fill(leftPos + 10, rowY + 1, leftPos + 16, rowY + 8, 0xFF000000 | row.color());
            String name = row.seated() ? row.name()
                    : row.name() + " " + Component.translatable("gui.athens_coins.central_seatless")
                    .getString();
            graphics.drawString(font, font.plainSubstrByWidth(name, 110), leftPos + 20, rowY + 2,
                    row.seated() ? 0xFFFFFFFF : 0xFFE0956B, false);

            String reserve = Money.format(row.reserve(), "$");
            graphics.drawString(font, reserve, leftPos + 206 - font.width(reserve), rowY + 2,
                    0xFF6BE06B, false);
            String deposits = Money.format(row.deposits(), "$");
            graphics.drawString(font, deposits, leftPos + 280 - font.width(deposits), rowY + 2,
                    0xFF8FE3FF, false);
            String accounts = String.valueOf(row.accounts());
            graphics.drawString(font, accounts, leftPos + PANEL_W - 14 - font.width(accounts),
                    rowY + 2, 0xFFB8C8DC, false);
            rowY += ROW_H;
        }

        // Column legend, so the three numbers are not a mystery.
        graphics.drawString(font, Component.translatable("gui.athens_coins.central_columns"),
                leftPos + 8, listTop + 15 + LIST_ROWS * ROW_H, 0xFF60708A, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = topPos + 74 + 15;
        for (int i = scroll; i < Math.min(banks.size(), scroll + LIST_ROWS); i++) {
            if (mouseY >= rowY && mouseY < rowY + ROW_H
                    && mouseX >= leftPos + 8 && mouseX < leftPos + PANEL_W - 8) {
                selected = i;
                return true;
            }
            rowY += ROW_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, banks.size() - LIST_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }
}
