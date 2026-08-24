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

/**
 * The central bank's dashboard.
 *
 * <p>The figures used to sit in a loose block at the top: three rate lines carrying their own bracketed
 * floor and ceiling, with the issued total and the bank count dropped at unrelated vertical offsets
 * beside them. Nothing lined up and nothing said what it was, which is the "numbers in a mess up
 * there" this rewrite answers.</p>
 *
 * <p>Now it is two titled columns. On the left, the official rates as a real table with a header row -
 * coin, official value, floor, ceiling - followed by the system totals, each labelled. On the right,
 * the commercial banks as a table with its own header, a hover highlight, and a tooltip saying that a
 * click selects the bank the footer buttons will act on. The selected bank is named under the list, so
 * pressing Inject is never a guess about which reserve it lands in.</p>
 */
public class CentralBankScreen extends Screen {
    private static final int ROW_H = 14;
    private static final int LINE_H = 11;
    private static final int PAD = 8;
    private static final int HINT_H = 12;
    private static final int LIST_FOOTER_H = 12;

    private static final int TEXT_TITLE = 0xFFBFD8FF;
    private static final int TEXT_HEADING = 0xFF7FB2E5;
    private static final int TEXT_LABEL = 0xFF60708A;
    private static final int TEXT_VALUE = 0xFFB8C8DC;
    private static final int TEXT_MONEY = 0xFF8FE3FF;
    private static final int TEXT_GOOD = 0xFF6BE06B;
    private static final int TEXT_WARN = 0xFFE0956B;
    private static final int TEXT_BAD = 0xFFE06B6B;
    private static final int TEXT_MUTED = 0xFF888888;
    private static final int TEXT_HINT = 0xFF9FB4CC;

    private final BlockPos pos;
    private final List<S2COpenCentralPacket.BankRow> banks;
    private final S2COpenCentralPacket data;

    private ScreenLayout.Regions layout;
    private ScreenLayout.Rect hintRow;
    private ScreenLayout.Rect leftColumn;
    private ScreenLayout.Rect rightColumn;
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
        // 86, not 70: the footer holds four rows (amounts, rates, feedback, close) and the close
        // button used to be positioned past the band that was supposed to contain it.
        layout = PanelMetrics.central(width, height);
        ScreenLayout.Rect content = layout.content().inset(PAD);
        hintRow = new ScreenLayout.Rect(content.x(), content.y(), content.width(), HINT_H);
        ScreenLayout.Rect columnsArea = new ScreenLayout.Rect(content.x(), hintRow.bottom(),
                content.width(), Math.max(0, content.height() - HINT_H));
        ScreenLayout.Columns split = ScreenLayout.columns(columnsArea, 12);
        leftColumn = split.first();
        rightColumn = split.second();
        // The list starts below its own heading and its column header, and stops above the line that
        // names the selection. Derived, not a magic offset, so a taller header cannot eat a row.
        int listTop = rightColumn.y() + 16 + LINE_H;
        listArea = new ScreenLayout.Rect(rightColumn.x(), Math.min(listTop, rightColumn.bottom()),
                rightColumn.width(),
                Math.max(0, rightColumn.bottom() - listTop - LIST_FOOTER_H));
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
        Panels.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 0xFF7FB2E5);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, layout.header().bottom() - 2, 0xFF1B2A44);
        renderHeader(graphics, mouseX, mouseY);

        drawFitted(graphics, Component.translatable("gui.athens_coins.central_hint").getString(),
                hintRow.x(), hintRow.y() + 1, hintRow.width(), TEXT_HINT, mouseX, mouseY);

        renderRates(graphics, mouseX, mouseY);
        renderBanks(graphics, mouseX, mouseY);
        if (message != null) {
            drawFitted(graphics, message.getString(), messageRow.x(), messageRow.y(),
                    messageRow.width(), TEXT_BAD, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
        }
    }

    /** Title on the left, the money supply on the right: the one number this screen exists to move. */
    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect header = layout.header();
        String issued = Component.translatable("gui.athens_coins.central_issued",
                Money.format(data.totalIssued(), "$")).getString();
        int issuedBudget = Math.min(header.width() / 2, font.width(issued) + 2);
        int titleBudget = Math.max(20, header.width() - issuedBudget - PAD * 3);
        drawFitted(graphics, title.getString(), header.x() + PAD, header.y() + 6, titleBudget,
                TEXT_TITLE, mouseX, mouseY);
        drawRight(graphics, issued, header.right() - PAD, header.y() + 6, issuedBudget,
                TEXT_MONEY, mouseX, mouseY);
    }

    /** The official rate table, then the system totals, both in the left column. */
    private void renderRates(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect area = leftColumn;
        int y = heading(graphics, area, area.y(), "gui.athens_coins.central_official", mouseX, mouseY);

        // Four cells that sum to the column: a name on the left and three right-aligned figures.
        int nameWidth = Math.max(28, area.width() * 28 / 100);
        int cell = Math.max(20, (area.width() - nameWidth) / 3);
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_col_coin").getString(),
                area.x(), y, nameWidth, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_official").getString(),
                area.x() + nameWidth + cell, y, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_min").getString(),
                area.x() + nameWidth + cell * 2, y, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_max").getString(),
                area.x() + nameWidth + cell * 3, y, cell, TEXT_LABEL, mouseX, mouseY);
        y += LINE_H;
        for (CoinType type : CoinType.ORDERED) {
            drawFitted(graphics, type.shortName().getString(), area.x(), y, nameWidth,
                    TEXT_VALUE, mouseX, mouseY);
            drawRight(graphics, Money.format(data.official(type), "$"),
                    area.x() + nameWidth + cell, y, cell, TEXT_MONEY, mouseX, mouseY);
            drawRight(graphics, Money.format(data.floor(type), "$"),
                    area.x() + nameWidth + cell * 2, y, cell, TEXT_LABEL, mouseX, mouseY);
            drawRight(graphics, Money.format(data.ceiling(type), "$"),
                    area.x() + nameWidth + cell * 3, y, cell, TEXT_LABEL, mouseX, mouseY);
            y += LINE_H;
        }

        y = heading(graphics, area, y + 4, "gui.athens_coins.central_system", mouseX, mouseY);
        long reserves = 0L;
        long deposits = 0L;
        long loans = 0L;
        int accounts = 0;
        for (S2COpenCentralPacket.BankRow row : banks) {
            reserves += row.reserve();
            deposits += row.deposits();
            loans += row.loansOut();
            accounts += row.accounts();
        }
        y = total(graphics, area, y, "gui.athens_coins.central_banks_count",
                String.valueOf(banks.size()), TEXT_VALUE, mouseX, mouseY);
        y = total(graphics, area, y, "gui.athens_coins.central_accounts_count",
                String.valueOf(accounts), TEXT_VALUE, mouseX, mouseY);
        y = total(graphics, area, y, "gui.athens_coins.central_reserve_total",
                Money.format(reserves, "$"), TEXT_GOOD, mouseX, mouseY);
        y = total(graphics, area, y, "gui.athens_coins.central_deposits_total",
                Money.format(deposits, "$"), TEXT_MONEY, mouseX, mouseY);
        y = total(graphics, area, y, "gui.athens_coins.central_loans_total",
                Money.format(loans, "$"), loans > 0L ? TEXT_WARN : TEXT_MUTED, mouseX, mouseY);
        total(graphics, area, y, "gui.athens_coins.central_margin",
                data.marginPercent() + "%", TEXT_VALUE, mouseX, mouseY);
    }

    /** A label on the left and its figure right-aligned to the column edge. */
    private int total(GuiGraphics graphics, ScreenLayout.Rect area, int y, String key,
                      String value, int color, int mouseX, int mouseY) {
        int valueWidth = Math.min(area.width() / 2, font.width(value) + 2);
        drawFitted(graphics, Component.translatable(key).getString(), area.x(), y,
                area.width() - valueWidth - 4, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, value, area.right(), y, valueWidth, color, mouseX, mouseY);
        return y + LINE_H;
    }

    private int heading(GuiGraphics graphics, ScreenLayout.Rect area, int y, String key,
                        int mouseX, int mouseY) {
        drawFitted(graphics, Component.translatable(key).getString(), area.x(), y + 2, area.width(),
                TEXT_HEADING, mouseX, mouseY);
        graphics.fill(area.x(), y + 12, area.right(), y + 13, 0x40FFFFFF);
        return y + 16;
    }

    private void renderBanks(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect area = listArea;
        heading(graphics, rightColumn, rightColumn.y(), "gui.athens_coins.central_list", mouseX, mouseY);

        int nameWidth = Math.max(50, area.width() * 40 / 100);
        int cell = Math.max(18, (area.width() - nameWidth - 14) / 3);
        int accountsWidth = Math.max(16, area.width() - nameWidth - cell * 3 - 14);
        int headerY = area.y() - LINE_H;
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_col_bank").getString(),
                area.x() + 12, headerY, nameWidth, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_reserve").getString(),
                area.right() - accountsWidth - cell * 2 - 6, headerY, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_deposits").getString(),
                area.right() - accountsWidth - cell - 3, headerY, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_loans").getString(),
                area.right() - accountsWidth, headerY, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_accounts").getString(),
                area.right(), headerY, accountsWidth, TEXT_LABEL, mouseX, mouseY);

        if (banks.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.central_no_banks").getString(),
                    area.x(), area.y() + 2, area.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = area.y();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        for (int i = scroll; i < Math.min(banks.size(), scroll + rows); i++) {
            S2COpenCentralPacket.BankRow row = banks.get(i);
            boolean hovered = area.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
            if (i == selected) {
                graphics.fill(area.x(), y, area.right(), y + ROW_H - 1, 0x600090FF);
            } else if (hovered) {
                graphics.fill(area.x(), y, area.right(), y + ROW_H - 1, 0x30FFFFFF);
            }
            graphics.fill(area.x() + 2, y + 3, area.x() + 8, y + 10, 0xFF000000 | row.color());
            String name = row.seated() ? row.name() : row.name() + " · "
                    + Component.translatable("gui.athens_coins.central_seatless").getString();
            drawFitted(graphics, name, area.x() + 12, y + 3, nameWidth,
                    row.seated() ? 0xFFFFFFFF : TEXT_WARN, mouseX, mouseY);
            drawRight(graphics, Money.format(row.reserve(), "$"),
                    area.right() - accountsWidth - cell * 2 - 6, y + 3, cell, TEXT_GOOD, mouseX, mouseY);
            drawRight(graphics, Money.format(row.deposits(), "$"),
                    area.right() - accountsWidth - cell - 3, y + 3, cell, TEXT_MONEY, mouseX, mouseY);
            drawRight(graphics, Money.format(row.loansOut(), "$"), area.right() - accountsWidth,
                    y + 3, cell, row.loansOut() > 0L ? TEXT_WARN : TEXT_MUTED, mouseX, mouseY);
            drawRight(graphics, String.valueOf(row.accounts()), area.right(), y + 3,
                    accountsWidth, TEXT_VALUE, mouseX, mouseY);
            if (hovered) {
                hoverTooltip = Component.translatable("gui.athens_coins.central_click_bank", row.name());
            }
            y += ROW_H;
        }
        graphics.disableScissor();

        // Name the selection: the footer buttons act on it, and there is no other way to tell.
        String selectedName = Component.translatable("gui.athens_coins.central_selected",
                banks.get(selected).name()).getString();
        drawFitted(graphics, selectedName, area.x(), area.bottom() + 2, area.width(),
                TEXT_HEADING, mouseX, mouseY);
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

    private void drawRight(GuiGraphics graphics, String text, int right, int y, int maxWidth,
                           int color, int mouseX, int mouseY) {
        String fitted = ScreenText.fit(font, text, maxWidth);
        graphics.drawString(font, fitted, right - font.width(fitted), y, color, false);
        if (ScreenText.wasTruncated(font, text, maxWidth)
                && mouseX >= right - maxWidth && mouseX < right && mouseY >= y && mouseY < y + 10) {
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
