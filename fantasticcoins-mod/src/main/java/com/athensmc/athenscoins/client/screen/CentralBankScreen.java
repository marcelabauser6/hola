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
 * <p>Three tabs, and they exist because two columns were not enough. This screen used to draw the official
 * rate table, the system totals, the list of banks and the name of the selection all at once; below a very
 * tall window the totals ran under the footer and the bottom rows were drawn over the amount box, while the
 * bank list's four money columns clipped to {@code Reser…} {@code Depós…} {@code Prest…}. Splitting the
 * content is what makes each part readable, rather than shrinking all of it until none of it is.</p>
 *
 * <p>The footer is contextual: each tab shows the controls that act on what it is showing. A rate button on
 * the licensing tab, or an inject button with no bank list in sight, is a control whose target you have to
 * remember rather than see.</p>
 */
public class CentralBankScreen extends Screen {
    private static final int ROW_H = 14;
    private static final int LINE_H = 11;
    private static final int PAD = 8;
    private static final int HINT_H = 12;
    private static final int GAP = 4;

    private static final int TEXT_TITLE = 0xFFBFD8FF;
    private static final int TEXT_HEADING = 0xFF7FB2E5;
    private static final int TEXT_LABEL = 0xFF7C8CA4;
    private static final int TEXT_VALUE = 0xFFB8C8DC;
    private static final int TEXT_MONEY = 0xFF8FE3FF;
    private static final int TEXT_GOOD = 0xFF6BE06B;
    private static final int TEXT_WARN = 0xFFE0956B;
    private static final int TEXT_BAD = 0xFFE06B6B;
    private static final int TEXT_MUTED = 0xFF888888;
    private static final int TEXT_HINT = 0xFF9FB4CC;

    private enum Tab {
        RATES("gui.athens_coins.central_tab_rates"),
        BANKS("gui.athens_coins.central_tab_banks"),
        LICENCES("gui.athens_coins.central_tab_licences");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private final BlockPos pos;
    private final List<S2COpenCentralPacket.BankRow> banks;
    private final List<S2COpenCentralPacket.PlayerRow> players;
    private final boolean operator;
    private final S2COpenCentralPacket data;

    private Tab tab = Tab.BANKS;
    private ScreenLayout.Regions layout;
    private ScreenLayout.Rect hintRow;
    private ScreenLayout.Rect body;
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
        players = packet.players();
        operator = packet.operator();
        data = packet;
    }

    /**
     * Carries the view across a refresh.
     *
     * <p>Same trap as the bank terminal: every central-bank action ends with the server re-sending the
     * screen, and a fresh one opens on Bancos with the first bank selected. Granting a licence therefore
     * threw you off the Licencias tab, and losing the selection meant the next Inject would have gone to a
     * different bank than the one you were looking at.</p>
     */
    public CentralBankScreen restoreFrom(CentralBankScreen previous) {
        tab = previous.tab;
        scroll = previous.scroll;
        selected = Math.min(previous.selected, Math.max(0, banks.size() - 1));
        message = previous.message;
        return this;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        layout = PanelMetrics.central(width, height);
        ScreenLayout.Rect content = layout.content().inset(PAD);
        hintRow = new ScreenLayout.Rect(content.x(), content.y(), content.width(), HINT_H);
        body = new ScreenLayout.Rect(content.x(), hintRow.bottom(), content.width(),
                Math.max(0, content.height() - HINT_H));
        // Lists start below their column header and stop above the line that names the selection.
        int listTop = body.y() + 14 + LINE_H;
        listArea = new ScreenLayout.Rect(body.x(), Math.min(listTop, body.bottom()), body.width(),
                Math.max(0, body.bottom() - listTop - 12));
        if (selected >= banks.size()) {
            selected = 0;
        }

        buildTabs();
        buildFooter();
    }

    private void buildTabs() {
        ScreenLayout.Rect tabs = layout.tabs().inset(GAP);
        int cell = ScreenLayout.gridCellWidth(tabs, Tab.values().length, GAP);
        int x = tabs.x();
        for (Tab candidate : Tab.values()) {
            Button button = Button.builder(Component.translatable(candidate.key), ignored -> {
                        tab = candidate;
                        scroll = 0;
                        message = null;
                        rebuildWidgets();
                    })
                    .bounds(x, tabs.y(), cell, Math.min(18, tabs.height())).build();
            button.active = tab != candidate;
            addRenderableWidget(button);
            x += cell + GAP;
        }
    }

    /**
     * The controls for whatever tab is open.
     *
     * <p>Rebuilt on every tab change rather than shown and hidden, so a button that does not apply cannot
     * be clicked through an inactive state.</p>
     */
    private void buildFooter() {
        ScreenLayout.Rect footer = layout.footer().inset(6);
        int buttonHeight = Math.min(18, footer.height());
        messageRow = new ScreenLayout.Rect(footer.x(), footer.y() + 22, footer.width(), 10);

        if (tab != Tab.LICENCES) {
            int cell = ScreenLayout.gridCellWidth(footer, 3, GAP);
            amountBox = new AmountField(font, footer.x(), footer.y(), cell, buttonHeight,
                    Component.translatable("gui.athens_coins.central_amount"));
            addRenderableWidget(amountBox);
            if (tab == Tab.BANKS) {
                addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.central_inject"),
                                ignored -> act(C2SCentralActionPacket.Action.INJECT))
                        .bounds(footer.x() + cell + GAP, footer.y(), cell, buttonHeight)
                        .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_inject_tip")))
                        .build());
                addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.central_drain"),
                                ignored -> act(C2SCentralActionPacket.Action.DRAIN))
                        .bounds(footer.x() + (cell + GAP) * 2, footer.y(), cell, buttonHeight)
                        .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_drain_tip")))
                        .build());
            } else {
                int rateCell = ScreenLayout.gridCellWidth(
                        new ScreenLayout.Rect(footer.x() + cell + GAP, footer.y(),
                                footer.width() - cell - GAP, buttonHeight), 3, GAP);
                int x = footer.x() + cell + GAP;
                for (CoinType type : CoinType.ORDERED) {
                    C2SCentralActionPacket.Action action = switch (type) {
                        case BRONZE -> C2SCentralActionPacket.Action.SET_OFFICIAL_BRONZE;
                        case SILVER -> C2SCentralActionPacket.Action.SET_OFFICIAL_SILVER;
                        case GOLD -> C2SCentralActionPacket.Action.SET_OFFICIAL_GOLD;
                    };
                    addRenderableWidget(Button.builder(
                                    Component.translatable("gui.athens_coins.central_set_rate",
                                            type.shortName()), ignored -> act(action))
                            .bounds(x, footer.y(), rateCell, buttonHeight)
                            .tooltip(Tooltip.create(Component.translatable(
                                    "gui.athens_coins.central_rate_tip", data.marginPercent())))
                            .build());
                    x += rateCell + GAP;
                }
            }
        } else {
            // Boards are issued from here, not taken from the creative tab, so this is where the button
            // for a server-wide one belongs.
            int cell = ScreenLayout.gridCellWidth(footer, 3, GAP);
            addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.central_issue_board"),
                            ignored -> {
                                ModNetwork.toServer(new C2SCentralActionPacket(pos,
                                        C2SCentralActionPacket.Action.ISSUE_HOLOGRAM, null, 1L));
                                message = Component.translatable("gui.athens_coins.done_board");
                            })
                    .bounds(footer.x(), footer.y(), cell * 2 + GAP, buttonHeight)
                    .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.central_issue_board_tip")))
                    .build());
        }

        int closeWidth = Math.min(82, footer.width());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"),
                        ignored -> onClose())
                .bounds(footer.right() - closeWidth, messageRow.bottom() + 2, closeWidth, buttonHeight)
                .build());
    }

    /**
     * Sends only on a real amount, and says so otherwise.
     *
     * <p>The old helper read an entry with no separator as cents and one with a separator as units, so
     * injecting "1000" put ten dollars into a reserve while "1000.00" put a thousand.</p>
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
        message = Component.translatable("gui.athens_coins.done_sent");
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

        drawFitted(graphics, Component.translatable(switch (tab) {
                    case RATES -> "gui.athens_coins.central_hint_rates";
                    case BANKS -> "gui.athens_coins.central_hint_banks";
                    case LICENCES -> "gui.athens_coins.central_hint_licences";
                }).getString(),
                hintRow.x(), hintRow.y() + 1, hintRow.width(), TEXT_HINT, mouseX, mouseY);

        switch (tab) {
            case RATES -> renderRates(graphics, mouseX, mouseY);
            case BANKS -> renderBanks(graphics, mouseX, mouseY);
            case LICENCES -> renderLicences(graphics, mouseX, mouseY);
        }

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

    /** The official rate table on the left, the system totals on the right. Both fit now. */
    private void renderRates(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect left = ScreenLayout.column(body, 2, 12, 0);
        ScreenLayout.Rect right = ScreenLayout.column(body, 2, 12, 1);

        int y = heading(graphics, left, left.y(), "gui.athens_coins.central_official", mouseX, mouseY);
        int nameWidth = Math.max(28, left.width() * 28 / 100);
        int cell = Math.max(18, (left.width() - nameWidth) / 3);
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_col_coin").getString(),
                left.x(), y, nameWidth, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_official").getString(),
                left.x() + nameWidth + cell, y, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_min").getString(),
                left.x() + nameWidth + cell * 2, y, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_max").getString(),
                left.x() + nameWidth + cell * 3, y, cell, TEXT_LABEL, mouseX, mouseY);
        y += LINE_H;
        for (CoinType type : CoinType.ORDERED) {
            drawFitted(graphics, type.shortName().getString(), left.x(), y, nameWidth,
                    TEXT_VALUE, mouseX, mouseY);
            drawRight(graphics, Money.format(data.official(type), "$"),
                    left.x() + nameWidth + cell, y, cell, TEXT_MONEY, mouseX, mouseY);
            drawRight(graphics, Money.format(data.floor(type), "$"),
                    left.x() + nameWidth + cell * 2, y, cell, TEXT_LABEL, mouseX, mouseY);
            drawRight(graphics, Money.format(data.ceiling(type), "$"),
                    left.x() + nameWidth + cell * 3, y, cell, TEXT_LABEL, mouseX, mouseY);
            y += LINE_H;
        }
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_band",
                        data.marginPercent()).getString(),
                left.x(), y + 4, left.width(), TEXT_MUTED, mouseX, mouseY);

        int ry = heading(graphics, right, right.y(), "gui.athens_coins.central_system", mouseX, mouseY);
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
        ry = total(graphics, right, ry, "gui.athens_coins.central_banks_count",
                String.valueOf(banks.size()), TEXT_VALUE, mouseX, mouseY);
        ry = total(graphics, right, ry, "gui.athens_coins.central_accounts_count",
                String.valueOf(accounts), TEXT_VALUE, mouseX, mouseY);
        ry = total(graphics, right, ry, "gui.athens_coins.central_reserve_total",
                Money.format(reserves, "$"), TEXT_GOOD, mouseX, mouseY);
        ry = total(graphics, right, ry, "gui.athens_coins.central_deposits_total",
                Money.format(deposits, "$"), TEXT_MONEY, mouseX, mouseY);
        total(graphics, right, ry, "gui.athens_coins.central_loans_total",
                Money.format(loans, "$"), loans > 0L ? TEXT_WARN : TEXT_MUTED, mouseX, mouseY);
    }

    /** A label on the left and its figure right-aligned to the column edge. */
    private int total(GuiGraphics graphics, ScreenLayout.Rect area, int y, String key,
                      String value, int color, int mouseX, int mouseY) {
        int valueWidth = Math.min(area.width() * 2 / 3, font.width(value));
        drawFitted(graphics, Component.translatable(key).getString(), area.x(), y,
                Math.max(0, area.width() - valueWidth - 4), TEXT_LABEL, mouseX, mouseY);
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

    /**
     * The banks, full width.
     *
     * <p>Full width is the fix for the clipped column headings: the four money columns were sharing half
     * the panel with the rate table, and no amount of shortening a heading makes {@code $2,335,632.00} fit
     * in thirty pixels.</p>
     */
    private void renderBanks(GuiGraphics graphics, int mouseX, int mouseY) {
        heading(graphics, body, body.y(), "gui.athens_coins.central_list", mouseX, mouseY);
        ScreenLayout.Rect area = listArea;

        int nameWidth = Math.max(60, area.width() * 32 / 100);
        int accountsWidth = 28;
        int cell = Math.max(24, (area.width() - nameWidth - accountsWidth - 20) / 3);
        int headerY = area.y() - LINE_H;
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_col_bank").getString(),
                area.x() + 12, headerY, nameWidth, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_reserve").getString(),
                area.right() - accountsWidth - cell * 2 - 8, headerY, cell, TEXT_LABEL, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.central_col_deposits").getString(),
                area.right() - accountsWidth - cell - 4, headerY, cell, TEXT_LABEL, mouseX, mouseY);
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
                    area.right() - accountsWidth - cell * 2 - 8, y + 3, cell, TEXT_GOOD, mouseX, mouseY);
            drawRight(graphics, Money.format(row.deposits(), "$"),
                    area.right() - accountsWidth - cell - 4, y + 3, cell, TEXT_MONEY, mouseX, mouseY);
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
        drawFitted(graphics, Component.translatable("gui.athens_coins.central_selected",
                        banks.get(selected).name()).getString(),
                area.x(), area.bottom() + 2, area.width(), TEXT_HEADING, mouseX, mouseY);
    }

    /**
     * Who may found a bank.
     *
     * <p>This is the tab that makes staff possible without handing out operator. Only a real operator can
     * change it - otherwise the first licensed founder could license a second, and the list would stop
     * being something the server's owner controls.</p>
     */
    private void renderLicences(GuiGraphics graphics, int mouseX, int mouseY) {
        heading(graphics, body, body.y(), "gui.athens_coins.central_licences", mouseX, mouseY);
        ScreenLayout.Rect area = listArea;
        if (players.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.nobody_online").getString(),
                    area.x(), area.y() + 2, area.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = area.y();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        for (int i = scroll; i < Math.min(players.size(), scroll + rows); i++) {
            S2COpenCentralPacket.PlayerRow row = players.get(i);
            boolean hovered = area.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) {
                graphics.fill(area.x(), y, area.right(), y + ROW_H - 1, 0x30FFFFFF);
            }
            String state = Component.translatable(row.founder()
                    ? "gui.athens_coins.is_founder" : "gui.athens_coins.not_founder").getString();
            int stateWidth = Math.min(area.width() / 2, font.width(state));
            drawFitted(graphics, row.name(), area.x() + 6, y + 3,
                    Math.max(8, area.width() - stateWidth - 16), TEXT_VALUE, mouseX, mouseY);
            drawRight(graphics, state, area.right() - 6, y + 3, stateWidth,
                    row.founder() ? TEXT_GOOD : TEXT_MUTED, mouseX, mouseY);
            if (hovered) {
                hoverTooltip = Component.translatable(operator
                        ? (row.founder() ? "gui.athens_coins.click_unlicense"
                        : "gui.athens_coins.click_license")
                        : "gui.athens_coins.licence_read_only", row.name());
            }
            y += ROW_H;
        }
        graphics.disableScissor();
        if (!operator) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.licence_read_only_note").getString(),
                    area.x(), area.bottom() + 2, area.width(), TEXT_MUTED, mouseX, mouseY);
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
            int size = tab == Tab.LICENCES ? players.size() : banks.size();
            int index = scroll + (int) ((mouseY - listArea.y()) / ROW_H);
            if (index >= 0 && index < Math.min(size, scroll + visibleRows())) {
                if (tab == Tab.BANKS) {
                    selected = index;
                    return true;
                }
                if (tab == Tab.LICENCES) {
                    if (!operator) {
                        message = Component.translatable("gui.athens_coins.licence_read_only_note");
                        return true;
                    }
                    S2COpenCentralPacket.PlayerRow row = players.get(index);
                    ModNetwork.toServer(new C2SCentralActionPacket(pos,
                            row.founder() ? C2SCentralActionPacket.Action.REMOVE_FOUNDER
                                    : C2SCentralActionPacket.Action.ADD_FOUNDER, row.id(), 0L));
                    message = Component.translatable(row.founder()
                            ? "gui.athens_coins.done_unlicensed"
                            : "gui.athens_coins.done_licensed", row.name());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.RATES) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int size = tab == Tab.LICENCES ? players.size() : banks.size();
        int max = Math.max(0, size - visibleRows());
        scroll = ScreenLayout.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }
}
