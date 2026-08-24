package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.client.layout.PanelMetrics;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.widget.AmountField;
import com.athensmc.athenscoins.client.widget.CountField;
import com.athensmc.athenscoins.network.C2STerminalActionPacket;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenTerminalPacket;
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
import java.util.UUID;

/** Responsive bank terminal. Server-side permission and accounting checks remain authoritative. */
public class BankTerminalScreen extends Screen {
    private static final int ROW_H = 14;
    private static final int PAD = 8;

    private enum Tab {
        USERS("gui.athens_coins.tab_users", true),
        OPEN("gui.athens_coins.tab_open", false),
        ACCOUNTS("gui.athens_coins.tab_accounts", false),
        ATMS("gui.athens_coins.tab_atms", false),
        SETTINGS("gui.athens_coins.tab_settings", true);

        final String key;
        final boolean operatorOnly;

        Tab(String key, boolean operatorOnly) {
            this.key = key;
            this.operatorOnly = operatorOnly;
        }
    }

    private final Bank bank;
    private final BlockPos pos;
    private final boolean operator;
    private final List<S2COpenTerminalPacket.AccountRow> accounts;
    private final List<S2COpenTerminalPacket.PlayerRow> players;

    /** Three entry rows above the button grid: name, money, days. */
    private static final int SETTINGS_FIELD_ROWS = 3;
    private static final int SETTING_ROW_H = 18;

    private Tab tab;
    private ScreenLayout.Regions layout;
    private ScreenLayout.Rect listArea;
    private ScreenLayout.Rect messageRow;
    private int scroll;
    private EditBox nameBox;
    private AmountField amountField;
    private CountField daysField;
    private Component message;
    private Component hoverTooltip;

    public BankTerminalScreen(S2COpenTerminalPacket packet) {
        super(Component.literal(packet.bank().name()));
        bank = packet.bank();
        pos = packet.pos();
        operator = packet.operator();
        accounts = packet.accounts();
        players = packet.players();
        tab = operator ? Tab.USERS : Tab.OPEN;
    }

    private int accent() {
        return 0xFF000000 | bank.themeColor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        layout = PanelMetrics.terminal(width, height);
        scroll = 0;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        nameBox = null;
        amountField = null;
        daysField = null;
        message = null;
        messageRow = null;

        long visibleTabs = java.util.Arrays.stream(Tab.values())
                .filter(candidate -> operator || !candidate.operatorOnly).count();
        ScreenLayout.Rect tabs = layout.tabs().inset(4);
        int gap = 2;
        int tabWidth = ScreenLayout.gridCellWidth(tabs, (int) visibleTabs, gap);
        int x = tabs.x();
        for (Tab candidate : Tab.values()) {
            if (candidate.operatorOnly && !operator) {
                continue;
            }
            Tab target = candidate;
            Component label = Component.translatable(candidate.key);
            Button button = Button.builder(label, ignored -> {
                        tab = target;
                        scroll = 0;
                        rebuild();
                    })
                    .bounds(x, tabs.y(), tabWidth, Math.min(18, tabs.height()))
                    .tooltip(Tooltip.create(label))
                    .build();
            button.active = candidate != tab;
            addRenderableWidget(button);
            x += tabWidth + gap;
        }

        ScreenLayout.Rect content = layout.content().inset(PAD);
        listArea = new ScreenLayout.Rect(content.x(), content.y() + 16,
                content.width(), Math.max(0, content.height() - 18));
        if (tab == Tab.SETTINGS) {
            buildSettings(content);
        } else if (tab == Tab.ATMS) {
            buildAtms(content);
        }

        ScreenLayout.Rect footer = layout.footer().inset(6);
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"),
                        ignored -> onClose())
                .bounds(footer.right() - Math.min(82, footer.width()), footer.y(),
                        Math.min(82, footer.width()), Math.min(18, footer.height()))
                .build());
    }

    /**
     * The settings tab: a name, a money field, a days field, and the buttons that consume them.
     *
     * <p>There used to be a single value box behind all eight settings, and it could not express
     * both kinds of number. Its helper read an entry with a separator as units and one without as
     * cents, so a fee of "50" meant fifty cents while "50.00" meant fifty currency units - and the
     * same box also fed the two settings measured in <em>days</em>, where "5" happened to mean five
     * days and "5.0" meant five hundred, clamped down to the 30-day maximum. Money and days are
     * different quantities, so they now have different fields with different filters, and each
     * button reads the one that matches it.</p>
     */
    private void buildSettings(ScreenLayout.Rect content) {
        int labelWidth = Math.min(78, Math.max(54, content.width() / 4));
        int saveWidth = Math.min(60, Math.max(44, content.width() / 5));
        int fieldX = content.x() + labelWidth + 4;
        // Reserve the Save button first, then give the name box what is left: flooring the box at
        // 44px instead could push it underneath the button on a narrow panel.
        int fieldWidth = Math.max(0, content.right() - fieldX - saveWidth - 4);

        nameBox = new EditBox(font, fieldX, content.y(), fieldWidth, 16,
                Component.translatable("gui.athens_coins.bank_name"));
        nameBox.setValue(bank.name());
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.theme_save"),
                        ignored -> send(C2STerminalActionPacket.Action.SET_NAME, 0L, nameBox.getValue()))
                .bounds(content.right() - saveWidth, content.y(), saveWidth, 16).build());

        amountField = new AmountField(font, fieldX, content.y() + 20,
                Math.max(0, content.right() - fieldX), 16,
                Component.translatable("gui.athens_coins.bank_value"));
        addRenderableWidget(amountField);

        int daysWidth = Math.min(64, Math.max(0, content.right() - fieldX));
        daysField = new CountField(font, fieldX, content.y() + 40, daysWidth, 16,
                Component.translatable("gui.athens_coins.bank_days"));
        addRenderableWidget(daysField);
        // The loans toggle shares the days row, which is what frees the bottom of the band for the
        // feedback line without the button grid and the toggle colliding.
        int toggleX = fieldX + daysWidth + 4;
        addRenderableWidget(Button.builder(Component.translatable(bank.loansEnabled()
                                ? "gui.athens_coins.bank_loans_on"
                                : "gui.athens_coins.bank_loans_off"),
                        ignored -> send(C2STerminalActionPacket.Action.SET_LOANS_ENABLED,
                                bank.loansEnabled() ? 0L : 1L, ""))
                .bounds(toggleX, content.y() + 40, Math.max(0, content.right() - toggleX), 16).build());

        String[] labels = {
                "gui.athens_coins.bank_wallet_limit", "gui.athens_coins.bank_fee",
                "gui.athens_coins.bank_rate_bronze", "gui.athens_coins.bank_rate_silver",
                "gui.athens_coins.bank_rate_gold", "gui.athens_coins.bank_loan_max",
                "gui.athens_coins.bank_fee_days", "gui.athens_coins.bank_loan_days"
        };
        C2STerminalActionPacket.Action[] actions = {
                C2STerminalActionPacket.Action.SET_WALLET_LIMIT,
                C2STerminalActionPacket.Action.SET_FEE,
                C2STerminalActionPacket.Action.SET_RATE_BRONZE,
                C2STerminalActionPacket.Action.SET_RATE_SILVER,
                C2STerminalActionPacket.Action.SET_RATE_GOLD,
                C2STerminalActionPacket.Action.SET_LOAN_MAX,
                C2STerminalActionPacket.Action.SET_FEE_DAYS,
                C2STerminalActionPacket.Action.SET_LOAN_DAYS
        };
        // Whether a button spends the money field or the days field.
        boolean[] inDays = { false, false, false, false, false, false, true, true };
        String[] hints = {
                "gui.athens_coins.hint_money", "gui.athens_coins.hint_money",
                "gui.athens_coins.hint_rate", "gui.athens_coins.hint_rate",
                "gui.athens_coins.hint_rate", "gui.athens_coins.hint_money",
                "gui.athens_coins.hint_days", "gui.athens_coins.hint_days"
        };
        ScreenLayout.Rect grid = settingsGrid(content);
        int gap = 4;
        int cellWidth = ScreenLayout.gridCellWidth(grid, 2, gap);
        for (int i = 0; i < labels.length; i++) {
            int column = i / 4;
            int row = i % 4;
            int bx = grid.x() + column * (cellWidth + gap);
            int by = grid.y() + row * SETTING_ROW_H;
            addSetting(bx, by, cellWidth, labels[i], actions[i], hints[i], inDays[i]);
        }
        messageRow = new ScreenLayout.Rect(content.x(), grid.y() + 4 * SETTING_ROW_H + 2,
                content.width(), 10);
    }

    /** The 2x4 button grid, positioned below the three entry rows. */
    private ScreenLayout.Rect settingsGrid(ScreenLayout.Rect content) {
        int top = content.y() + SETTINGS_FIELD_ROWS * 20;
        return new ScreenLayout.Rect(content.x(), top, content.width(),
                Math.max(0, content.bottom() - top - 12));
    }

    /**
     * The ATMs tab splits into a button column and a rates column.
     *
     * <p>Both used to be positioned by their own formula - the buttons by
     * {@code max(90, width/2 - 4)} and the rate text by {@code min(158, width/2 + 4)} - so below a
     * certain panel width the text was drawn on top of the buttons. One split, two callers.</p>
     */
    private ScreenLayout.Columns atmColumns(ScreenLayout.Rect content) {
        return ScreenLayout.columns(content, 8);
    }

    private void buildAtms(ScreenLayout.Rect content) {
        ScreenLayout.Rect column = atmColumns(content).first();
        int buttonWidth = Math.min(150, column.width());
        int y = content.y() + 18;
        for (int count : new int[] {1, 4, 8}) {
            int amount = count;
            addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.issue_atm", amount),
                            ignored -> send(C2STerminalActionPacket.Action.ISSUE_ATM, amount, ""))
                    .bounds(column.x(), y, buttonWidth, 18).build());
            y += 22;
        }
    }

    private void addSetting(int x, int y, int width, String labelKey,
                            C2STerminalActionPacket.Action action, String hintKey, boolean days) {
        Component label = Component.translatable(labelKey);
        addRenderableWidget(Button.builder(label, ignored -> submit(action, days))
                .bounds(x, y, width, 16)
                .tooltip(Tooltip.create(label.copy().append("\n").append(Component.translatable(hintKey))))
                .build());
    }

    /** Reads the field this setting is measured in, and refuses rather than sending a zero. */
    private void submit(C2STerminalActionPacket.Action action, boolean days) {
        if (days) {
            int count = daysField == null ? -1 : daysField.count();
            if (count < 0) {
                message = daysField == null ? null : daysField.error();
                return;
            }
            send(action, count, "");
            daysField.clear();
        } else {
            long cents = amountField == null ? -1L : amountField.cents();
            if (cents < 0L) {
                message = amountField == null ? null : amountField.error();
                return;
            }
            send(action, cents, "");
            amountField.clear();
        }
        message = null;
    }

    private void send(C2STerminalActionPacket.Action action, long value, String text) {
        ModNetwork.toServer(new C2STerminalActionPacket(pos, action, null, value, text));
    }

    private void sendFor(C2STerminalActionPacket.Action action, UUID target) {
        ModNetwork.toServer(new C2STerminalActionPacket(pos, action, target, 0L, ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hoverTooltip = null;
        ScreenLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF0140F0C);
        StatsScreen.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), accent());
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, layout.header().bottom() - 2, accent());

        String reserveLabel = Component.translatable("gui.athens_coins.bank_reserve").getString();
        String header = bank.name() + " · " + reserveLabel + " " + Money.format(bank.reserve(), "$");
        drawFitted(graphics, header, panel.x() + PAD, panel.y() + 6,
                panel.width() - PAD * 2, 0xFFFFFFFF, mouseX, mouseY);

        switch (tab) {
            case USERS -> renderPlayers(graphics, mouseX, mouseY, true);
            case OPEN -> renderPlayers(graphics, mouseX, mouseY, false);
            case ACCOUNTS -> renderAccounts(graphics, mouseX, mouseY);
            case ATMS -> renderAtms(graphics, mouseX, mouseY);
            case SETTINGS -> renderSettings(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
        }
    }

    private void renderPlayers(GuiGraphics graphics, int mouseX, int mouseY, boolean bankerMode) {
        Component hint = Component.translatable(bankerMode
                ? "gui.athens_coins.users_hint" : "gui.athens_coins.open_hint");
        drawFitted(graphics, hint.getString(), listArea.x(), layout.content().y() + PAD,
                listArea.width(), 0xFFB0A090, mouseX, mouseY);
        if (players.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.nobody_online").getString(),
                    listArea.x(), listArea.y() + 2, listArea.width(), 0xFF888888, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = listArea.y();
        // visibleRows() floors at 1, so on a very short panel one row would otherwise be drawn
        // straight over the footer. Clip to the band, as the other list screens already do.
        graphics.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        for (int i = scroll; i < Math.min(players.size(), scroll + rows); i++) {
            S2COpenTerminalPacket.PlayerRow row = players.get(i);
            if (listArea.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H) {
                graphics.fill(listArea.x(), y, listArea.right(), y + ROW_H - 1, 0x30FFFFFF);
            }
            String state = Component.translatable(bankerMode
                    ? (row.banker() ? "gui.athens_coins.is_banker" : "gui.athens_coins.not_banker")
                    : (row.hasAccount() ? "gui.athens_coins.has_account" : "gui.athens_coins.no_account")).getString();
            int color = bankerMode ? (row.banker() ? 0xFF6BE06B : 0xFF888888)
                    : (row.hasAccount() ? 0xFFE0C060 : 0xFF6BE06B);
            int stateWidth = Math.min(listArea.width() / 2, font.width(state));
            drawFitted(graphics, row.name(), listArea.x() + 4, y + 3,
                    Math.max(8, listArea.width() - stateWidth - 12), 0xFFFFFFFF, mouseX, mouseY);
            String fittedState = ScreenText.fit(font, state, stateWidth);
            graphics.drawString(font, fittedState, listArea.right() - 4 - font.width(fittedState), y + 3, color, false);
            y += ROW_H;
        }
        graphics.disableScissor();
    }

    private void renderAccounts(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFitted(graphics, Component.translatable("gui.athens_coins.accounts_hint2").getString(),
                listArea.x(), layout.content().y() + PAD, listArea.width(), 0xFFB0A090, mouseX, mouseY);
        if (accounts.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.no_accounts").getString(),
                    listArea.x(), listArea.y() + 2, listArea.width(), 0xFF888888, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = listArea.y();
        graphics.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        for (int i = scroll; i < Math.min(accounts.size(), scroll + rows); i++) {
            S2COpenTerminalPacket.AccountRow row = accounts.get(i);
            String number = "#" + row.number();
            String balance = Money.format(row.balance(), "$");
            String loan = row.hasLoan() ? Component.translatable("gui.athens_coins.has_loan").getString() : "";
            int rightWidth = Math.min(listArea.width() / 2,
                    font.width(balance) + (loan.isEmpty() ? 0 : font.width(loan) + 6));
            int loanBudget = loan.isEmpty() ? 0 : Math.max(20, (rightWidth - 6) / 2);
            int balanceBudget = loan.isEmpty() ? rightWidth : Math.max(20, rightWidth - loanBudget - 6);
            graphics.drawString(font, ScreenText.fit(font, number, 48), listArea.x() + 4, y + 3,
                    0xFFE0C060, false);
            drawFitted(graphics, row.owner(), listArea.x() + 54, y + 3,
                    Math.max(8, listArea.width() - 58 - rightWidth), 0xFFFFFFFF, mouseX, mouseY);
            int right = listArea.right() - 4;
            if (!loan.isEmpty()) {
                String fitted = ScreenText.fit(font, loan, loanBudget);
                graphics.drawString(font, fitted, right - font.width(fitted), y + 3, 0xFFE06B6B, false);
                if (ScreenText.wasTruncated(font, loan, loanBudget)
                        && mouseX >= right - loanBudget && mouseX < right
                        && mouseY >= y && mouseY < y + ROW_H) {
                    hoverTooltip = Component.literal(loan);
                }
                right -= loanBudget + 6;
            }
            String fittedBalance = ScreenText.fit(font, balance, balanceBudget);
            graphics.drawString(font, fittedBalance, right - font.width(fittedBalance), y + 3,
                    0xFF6BE06B, false);
            if (ScreenText.wasTruncated(font, balance, balanceBudget)
                    && mouseX >= right - balanceBudget && mouseX < right
                    && mouseY >= y && mouseY < y + ROW_H) {
                hoverTooltip = Component.literal(balance);
            }
            y += ROW_H;
        }
        graphics.disableScissor();
    }

    private void renderAtms(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect content = layout.content().inset(PAD);
        drawFitted(graphics, Component.translatable("gui.athens_coins.atms_hint").getString(),
                content.x(), content.y(), content.width(), 0xFFB0A090, mouseX, mouseY);
        ScreenLayout.Rect rates = atmColumns(content).second();
        int y = content.y() + 20;
        for (CoinType type : CoinType.ORDERED) {
            String line = type.shortName().getString() + ": " + Money.format(bank.syncedRate(type), "$");
            drawFitted(graphics, line, rates.x(), y, rates.width(), 0xFF9EC5D8, mouseX, mouseY);
            y += 14;
        }
        drawFitted(graphics, Component.translatable("gui.athens_coins.atms_note").getString(),
                content.x(), content.bottom() - 12, content.width(), 0xFF8A7A6A, mouseX, mouseY);
    }

    private void renderSettings(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect content = layout.content().inset(PAD);
        int labelWidth = Math.min(78, Math.max(54, content.width() / 4));
        drawFitted(graphics, Component.translatable("gui.athens_coins.bank_name").getString(),
                content.x(), content.y() + 4, labelWidth, 0xFFB0A090, mouseX, mouseY);
        drawFitted(graphics, Component.translatable("gui.athens_coins.bank_value").getString(),
                content.x(), content.y() + 24, labelWidth, 0xFFB0A090, mouseX, mouseY);
        drawFitted(graphics, Component.translatable("gui.athens_coins.bank_days").getString(),
                content.x(), content.y() + 44, labelWidth, 0xFFB0A090, mouseX, mouseY);
        if (message != null && messageRow != null) {
            drawFitted(graphics, message.getString(), messageRow.x(), messageRow.y(),
                    messageRow.width(), 0xFFE06B6B, mouseX, mouseY);
        }
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

    private int visibleRows() {
        return Math.max(1, ScreenLayout.visibleRows(listArea, ROW_H));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rows = visibleRows();
        if (tab == Tab.USERS || tab == Tab.OPEN) {
            int index = rowAt(mouseX, mouseY, players.size(), rows);
            if (index >= 0) {
                S2COpenTerminalPacket.PlayerRow row = players.get(index);
                if (tab == Tab.USERS) {
                    sendFor(row.banker() ? C2STerminalActionPacket.Action.REMOVE_BANKER
                            : C2STerminalActionPacket.Action.ADD_BANKER, row.id());
                } else if (!row.hasAccount()) {
                    sendFor(C2STerminalActionPacket.Action.OPEN_ACCOUNT, row.id());
                }
                return true;
            }
        } else if (tab == Tab.ACCOUNTS && (button == 0 || button == 1)) {
            int index = rowAt(mouseX, mouseY, accounts.size(), rows);
            if (index >= 0) {
                if (button == 0) {
                    ModNetwork.toServer(new com.athensmc.athenscoins.network.C2SRequestAccountPacket(
                            pos, accounts.get(index).number()));
                } else {
                    ModNetwork.toServer(new C2STerminalActionPacket(pos,
                            C2STerminalActionPacket.Action.WITHDRAW_ALL, null,
                            accounts.get(index).number(), 0L, ""));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int rowAt(double mouseX, double mouseY, int size, int rows) {
        if (!listArea.contains(mouseX, mouseY)) {
            return -1;
        }
        int index = scroll + (int) ((mouseY - listArea.y()) / ROW_H);
        return index < Math.min(size, scroll + rows) ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab != Tab.ACCOUNTS && tab != Tab.USERS && tab != Tab.OPEN) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int size = tab == Tab.ACCOUNTS ? accounts.size() : players.size();
        int max = Math.max(0, size - visibleRows());
        scroll = ScreenLayout.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }
}
