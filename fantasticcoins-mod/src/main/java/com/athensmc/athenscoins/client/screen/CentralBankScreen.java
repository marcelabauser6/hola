package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.client.layout.PanelMetrics;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.widget.AmountField;
import com.athensmc.athenscoins.network.C2SCentralActionPacket;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenCentralPacket;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Responsive central-bank dashboard with a bounded, scrollable commercial-bank list. */
public class CentralBankScreen extends Screen {
    private static final int ROW_H = 14;

    /** Summary geometry, shared by the layout in {@code init} and the drawing in {@code render}. */
    private static final int SUMMARY_TOP = 13;
    private static final int SUMMARY_LINE_H = 11;
    private static final int LIST_TITLE_H = 14;
    private static final int LIST_FOOTER_H = 12;

    private final BlockPos pos;
    private final List<S2COpenCentralPacket.BankRow> banks;
    private final S2COpenCentralPacket data;

    private ScreenLayout.Regions layout;
    private ScreenLayout.Rect listArea;
    private ScreenLayout.Rect messageRow;
    private int scroll;
    private int selected;
    private AmountField amountBox;
    private Component message;
    private Component hoverTooltip;

    public CentralBankScreen(S2COpenCentralPacket packet) {
        super(Component.translatable("gui.athens_coins.central_title"));
        pos = packet.pos();
        banks = packet.banks();
        data = packet;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // 82, not 70: the footer holds four rows (amounts, rates, feedback, close) and the close
        // button used to be positioned 2px past the band that was supposed to contain it.
        layout = PanelMetrics.central(width, height);
        ScreenLayout.Rect content = layout.content().inset(8);
        // The summary block is three rate lines plus its heading; derive the list top from that
        // instead of a magic +58, which left the third line touching the list title.
        int summaryHeight = SUMMARY_TOP + CoinType.ORDERED.length * SUMMARY_LINE_H + 6;
        int listTop = content.y() + summaryHeight + LIST_TITLE_H;
        listArea = new ScreenLayout.Rect(content.x(), Math.min(listTop, content.bottom()),
                content.width(), Math.max(0, content.bottom() - listTop - LIST_FOOTER_H));
        if (selected >= banks.size()) {
            selected = 0;
        }

        ScreenLayout.Rect footer = layout.footer().inset(6);
        int gap = 4;
        int firstRowCell = ScreenLayout.gridCellWidth(footer, 3, gap);
        amountBox = new AmountField(font, footer.x(), footer.y(), firstRowCell, 16,
                Component.translatable("gui.athens_coins.bank_value"));
        addRenderableWidget(amountBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.central_inject"),
                        ignored -> act(C2SCentralActionPacket.Action.INJECT))
                .bounds(footer.x() + firstRowCell + gap, footer.y(), firstRowCell, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_inject_tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.central_drain"),
                        ignored -> act(C2SCentralActionPacket.Action.DRAIN))
                .bounds(footer.x() + (firstRowCell + gap) * 2, footer.y(), firstRowCell, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_drain_tip"))).build());

        int rateWidth = ScreenLayout.gridCellWidth(footer, 3, gap);
        int x = footer.x();
        for (CoinType type : CoinType.ORDERED) {
            C2SCentralActionPacket.Action action = switch (type) {
                case BRONZE -> C2SCentralActionPacket.Action.SET_OFFICIAL_BRONZE;
                case SILVER -> C2SCentralActionPacket.Action.SET_OFFICIAL_SILVER;
                case GOLD -> C2SCentralActionPacket.Action.SET_OFFICIAL_GOLD;
            };
            Component label = Component.translatable("gui.athens_coins.central_set_rate", type.shortName());
            addRenderableWidget(Button.builder(label, ignored -> act(action))
                    .bounds(x, footer.y() + 20, rateWidth, 16)
                    .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_rate_tip",
                            data.marginPercent()))).build());
            x += rateWidth + gap;
        }
        messageRow = new ScreenLayout.Rect(footer.x(), footer.y() + 41, footer.width(), 10);
        int closeWidth = Math.min(82, footer.width());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"), ignored -> onClose())
                .bounds(footer.right() - closeWidth, messageRow.bottom() + 3, closeWidth, 18).build());
    }

    /**
     * Sends only on a real amount, and says so otherwise.
     *
     * <p>The old helper read an entry with no separator as cents and one with a separator as units,
     * so injecting "1000" put ten dollars into a reserve while "1000.00" put a thousand. An invalid
     * entry became {@code 0}, which the server silently ignored.</p>
     */
    private void act(C2SCentralActionPacket.Action action) {
        long cents = amountBox.cents();
        if (cents < 0L) {
            message = amountBox.error();
            return;
        }
        if (banks.isEmpty()) {
            message = Component.translatable("gui.athens_coins.central_no_banks");
            return;
        }
        UUID target = banks.get(selected).id();
        ModNetwork.toServer(new C2SCentralActionPacket(pos, action, target, cents));
        amountBox.clear();
        message = null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hoverTooltip = null;
        ScreenLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF00E1018);
        StatsScreen.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 0xFF7FB2E5);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, layout.header().bottom() - 2, 0xFF1B2A44);
        drawFitted(graphics, title.getString(), panel.x() + 8, panel.y() + 6,
                panel.width() - 16, 0xFFBFD8FF, mouseX, mouseY);

        renderSummary(graphics, mouseX, mouseY);
        renderBanks(graphics, mouseX, mouseY);
        if (message != null) {
            drawFitted(graphics, message.getString(), messageRow.x(), messageRow.y(),
                    messageRow.width(), 0xFFE06B6B, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
        }
    }

    private void renderSummary(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect content = layout.content().inset(8);
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_official").getString(),
                content.x(), content.y() + 1, content.width(), 0xFF7FB2E5, mouseX, mouseY);
        // Split into two columns that sum to the content width, so neither can reach the other or
        // run past the right edge. The old code floored the right block at 20px, which overflowed.
        ScreenLayout.Columns split = ScreenLayout.columns(content, 6);
        ScreenLayout.Rect left = split.first();
        ScreenLayout.Rect right = split.second();
        int y = content.y() + SUMMARY_TOP;
        for (CoinType type : CoinType.ORDERED) {
            String line = type.shortName().getString() + ": " + Money.format(data.official(type), "$")
                    + "  [" + Money.format(data.floor(type), "$") + "–"
                    + Money.format(data.ceiling(type), "$") + "]";
            drawFitted(graphics, line, left.x() + 4, y, left.width() - 4, 0xFFB8C8DC, mouseX, mouseY);
            y += SUMMARY_LINE_H;
        }
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_issued",
                        Money.format(data.totalIssued(), "$")).getString(),
                right.x(), content.y() + SUMMARY_TOP, right.width(), 0xFF8FE3FF, mouseX, mouseY);
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_banks", banks.size()).getString(),
                right.x(), content.y() + SUMMARY_TOP + SUMMARY_LINE_H * 2, right.width(),
                0xFFB8C8DC, mouseX, mouseY);
    }

    private void renderBanks(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleY = listArea.y() - LIST_TITLE_H;
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_list").getString(),
                listArea.x(), titleY, listArea.width(), 0xFF7FB2E5, mouseX, mouseY);
        graphics.fill(listArea.x(), titleY + 10, listArea.right(), titleY + 11, 0x40FFFFFF);
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_columns").getString(),
                listArea.x(), listArea.bottom() + 2, listArea.width(), 0xFF60708A, mouseX, mouseY);
        if (banks.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.central_no_banks").getString(),
                    listArea.x(), listArea.y() + 2, listArea.width(), 0xFF888888, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = listArea.y();
        int nameWidth = Math.max(60, listArea.width() * 45 / 100);
        int numericWidth = Math.max(72, listArea.width() - nameWidth - 14);
        int accountsWidth = Math.min(30, Math.max(20, numericWidth / 5));
        int moneyWidth = Math.max(24, (numericWidth - accountsWidth - 6) / 2);
        graphics.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        for (int i = scroll; i < Math.min(banks.size(), scroll + rows); i++) {
            S2COpenCentralPacket.BankRow row = banks.get(i);
            if (i == selected) {
                graphics.fill(listArea.x(), y, listArea.right(), y + ROW_H - 1, 0x400090FF);
            }
            graphics.fill(listArea.x() + 2, y + 3, listArea.x() + 8, y + 10, 0xFF000000 | row.color());
            String name = row.seated() ? row.name() : row.name() + " · "
                    + Component.translatable("gui.athens_coins.central_seatless").getString();
            drawFitted(graphics, name, listArea.x() + 12, y + 3, nameWidth,
                    row.seated() ? 0xFFFFFFFF : 0xFFE0956B, mouseX, mouseY);

            int right = listArea.right();
            drawRightFitted(graphics, String.valueOf(row.accounts()), right, y + 3,
                    accountsWidth, 0xFFB8C8DC, mouseX, mouseY);
            right -= accountsWidth + 3;
            drawRightFitted(graphics, Money.format(row.deposits(), "$"), right, y + 3,
                    moneyWidth, 0xFF8FE3FF, mouseX, mouseY);
            right -= moneyWidth + 3;
            drawRightFitted(graphics, Money.format(row.reserve(), "$"), right, y + 3,
                    moneyWidth, 0xFF6BE06B, mouseX, mouseY);
            y += ROW_H;
        }
        graphics.disableScissor();
    }

    private void drawRightFitted(GuiGraphics graphics, String text, int right, int y, int maxWidth,
                                 int color, int mouseX, int mouseY) {
        String fitted = ScreenText.fit(font, text, maxWidth);
        graphics.drawString(font, fitted, right - font.width(fitted), y, color, false);
        if (ScreenText.wasTruncated(font, text, maxWidth)
                && listArea.contains(mouseX, mouseY)
                && mouseX >= right - maxWidth && mouseX < right && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = Component.literal(text);
        }
    }

    private int visibleRows() {
        return Math.max(1, ScreenLayout.visibleRows(listArea, ROW_H));
    }

    private void drawFitted(GuiGraphics graphics, String text, int x, int y, int maxWidth,
                            int color, int mouseX, int mouseY) {
        String fitted = ScreenText.fit(font, text, maxWidth);
        graphics.drawString(font, fitted, x, y, color, false);
        if (ScreenText.wasTruncated(font, text, maxWidth)
                && mouseX >= x && mouseX < x + maxWidth && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = Component.literal(text);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listArea.contains(mouseX, mouseY)) {
            int index = scroll + (int) ((mouseY - listArea.y()) / ROW_H);
            if (index >= 0 && index < Math.min(banks.size(), scroll + visibleRows())) {
                selected = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, banks.size() - visibleRows());
        scroll = ScreenLayout.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }
}
