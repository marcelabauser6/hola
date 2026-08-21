package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.bank.Bank;
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

/**
 * The bank terminal.
 *
 * <p>Tabs are gated by role: staff can open accounts and read the register, but only an operator
 * can appoint bankers or change the bank's terms. The server re-checks all of it.</p>
 */
public class BankTerminalScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;
    private static final int ROW_H = 14;
    private static final int LIST_ROWS = 9;

    private enum Tab {
        USERS("gui.athens_coins.tab_users", true),
        OPEN("gui.athens_coins.tab_open", false),
        ACCOUNTS("gui.athens_coins.tab_accounts", false),
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

    private Tab tab;
    private int leftPos;
    private int topPos;
    private int scroll;
    private EditBox nameBox;
    private EditBox valueBox;

    public BankTerminalScreen(S2COpenTerminalPacket packet) {
        super(Component.literal(packet.bank().name()));
        this.bank = packet.bank();
        this.pos = packet.pos();
        this.operator = packet.operator();
        this.accounts = packet.accounts();
        this.players = packet.players();
        this.tab = operator ? Tab.USERS : Tab.OPEN;
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
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;
        scroll = 0;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        int x = leftPos + 8;
        for (Tab candidate : Tab.values()) {
            if (candidate.operatorOnly && !operator) {
                continue;
            }
            final Tab target = candidate;
            Button button = Button.builder(Component.translatable(candidate.key), b -> {
                        tab = target;
                        scroll = 0;
                        rebuild();
                    })
                    .bounds(x, topPos + 22, 78, 16)
                    .build();
            button.active = candidate != tab;
            addRenderableWidget(button);
            x += 80;
        }

        if (tab == Tab.SETTINGS) {
            buildSettings();
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"),
                        b -> onClose())
                .bounds(leftPos + PANEL_W - 90, topPos + PANEL_H - 26, 82, 18)
                .build());
    }

    private void buildSettings() {
        nameBox = new EditBox(font, leftPos + 90, topPos + 48, 150, 16,
                Component.translatable("gui.athens_coins.bank_name"));
        nameBox.setValue(bank.name());
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.theme_save"),
                        b -> send(C2STerminalActionPacket.Action.SET_NAME, 0L, nameBox.getValue()))
                .bounds(leftPos + 244, topPos + 48, 60, 16).build());

        // One numeric box reused by whichever setting the operator presses, which keeps the panel
        // readable instead of stacking eight fields.
        valueBox = new EditBox(font, leftPos + 90, topPos + 70, 150, 16,
                Component.translatable("gui.athens_coins.bank_value"));
        valueBox.setValue("0");
        valueBox.setMaxLength(12);
        addRenderableWidget(valueBox);

        int y = topPos + 92;
        addSetting(y, "gui.athens_coins.bank_wallet_limit", C2STerminalActionPacket.Action.SET_WALLET_LIMIT,
                "gui.athens_coins.hint_cents");
        addSetting(y += 18, "gui.athens_coins.bank_fee", C2STerminalActionPacket.Action.SET_FEE,
                "gui.athens_coins.hint_cents");
        addSetting(y += 18, "gui.athens_coins.bank_fee_days", C2STerminalActionPacket.Action.SET_FEE_DAYS,
                "gui.athens_coins.hint_days");
        addSetting(y += 18, "gui.athens_coins.bank_rate_bronze", C2STerminalActionPacket.Action.SET_RATE_BRONZE,
                "gui.athens_coins.hint_rate");
        addSetting(y += 18, "gui.athens_coins.bank_rate_silver", C2STerminalActionPacket.Action.SET_RATE_SILVER,
                "gui.athens_coins.hint_rate");
        addSetting(y += 18, "gui.athens_coins.bank_rate_gold", C2STerminalActionPacket.Action.SET_RATE_GOLD,
                "gui.athens_coins.hint_rate");
        addSetting(y += 18, "gui.athens_coins.bank_loan_max", C2STerminalActionPacket.Action.SET_LOAN_MAX,
                "gui.athens_coins.hint_cents");
        addSetting(y += 18, "gui.athens_coins.bank_loan_days", C2STerminalActionPacket.Action.SET_LOAN_DAYS,
                "gui.athens_coins.hint_days");

        addRenderableWidget(Button.builder(Component.translatable(bank.loansEnabled()
                                ? "gui.athens_coins.bank_loans_on"
                                : "gui.athens_coins.bank_loans_off"),
                        b -> send(C2STerminalActionPacket.Action.SET_LOANS_ENABLED,
                                bank.loansEnabled() ? 0L : 1L, ""))
                .bounds(leftPos + 90, y + 20, 150, 16).build());
    }

    private void addSetting(int y, String labelKey, C2STerminalActionPacket.Action action, String hintKey) {
        addRenderableWidget(Button.builder(Component.translatable(labelKey),
                        b -> send(action, parseValue(), ""))
                .bounds(leftPos + 90, y, 214, 16)
                .tooltip(Tooltip.create(Component.translatable(hintKey)))
                .build());
    }

    /** Reads the shared numeric box, tolerating decimals so a rate can be typed as 0.17. */
    private long parseValue() {
        String raw = valueBox == null ? "0" : valueBox.getValue().trim();
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

    private void send(C2STerminalActionPacket.Action action, long value, String text) {
        ModNetwork.toServer(new C2STerminalActionPacket(pos, action, null, value, text));
    }

    private void sendFor(C2STerminalActionPacket.Action action, java.util.UUID target) {
        ModNetwork.toServer(new C2STerminalActionPacket(pos, action, target, 0L, ""));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, 0xF0140F0C);
        StatsScreen.outline(graphics, leftPos, topPos, PANEL_W, PANEL_H, accent());
        graphics.fill(leftPos + 1, topPos + 1, leftPos + PANEL_W - 1, topPos + 18, accent());

        String header = bank.name() + "   " + Component
                .translatable("gui.athens_coins.bank_reserve").getString() + " "
                + Money.format(bank.reserve(), "$");
        graphics.drawString(font, header, leftPos + PANEL_W / 2 - font.width(header) / 2,
                topPos + 6, 0xFFFFFFFF, true);

        switch (tab) {
            case USERS -> renderPlayers(graphics, mouseX, mouseY, true);
            case OPEN -> renderPlayers(graphics, mouseX, mouseY, false);
            case ACCOUNTS -> renderAccounts(graphics);
            case SETTINGS -> renderSettings(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayers(GuiGraphics graphics, int mouseX, int mouseY, boolean bankerMode) {
        graphics.drawString(font, Component.translatable(bankerMode
                        ? "gui.athens_coins.users_hint"
                        : "gui.athens_coins.open_hint"),
                leftPos + 8, topPos + 44, 0xFFB0A090, false);
        if (players.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.athens_coins.nobody_online"),
                    leftPos + 8, topPos + 60, 0xFF888888, false);
            return;
        }
        int y = topPos + 58;
        for (int i = scroll; i < Math.min(players.size(), scroll + LIST_ROWS); i++) {
            S2COpenTerminalPacket.PlayerRow row = players.get(i);
            boolean hovered = mouseY >= y && mouseY < y + ROW_H
                    && mouseX >= leftPos + 8 && mouseX < leftPos + PANEL_W - 8;
            if (hovered) {
                graphics.fill(leftPos + 8, y, leftPos + PANEL_W - 8, y + ROW_H - 1, 0x30FFFFFF);
            }
            graphics.drawString(font, row.name(), leftPos + 12, y + 3, 0xFFFFFFFF, false);
            String state;
            int color;
            if (bankerMode) {
                state = Component.translatable(row.banker()
                        ? "gui.athens_coins.is_banker"
                        : "gui.athens_coins.not_banker").getString();
                color = row.banker() ? 0xFF6BE06B : 0xFF888888;
            } else {
                state = Component.translatable(row.hasAccount()
                        ? "gui.athens_coins.has_account"
                        : "gui.athens_coins.no_account").getString();
                color = row.hasAccount() ? 0xFFE0C060 : 0xFF6BE06B;
            }
            graphics.drawString(font, state, leftPos + PANEL_W - 12 - font.width(state), y + 3,
                    color, false);
            y += ROW_H;
        }
    }

    private void renderAccounts(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.athens_coins.accounts_hint"),
                leftPos + 8, topPos + 44, 0xFFB0A090, false);
        if (accounts.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.athens_coins.no_accounts"),
                    leftPos + 8, topPos + 60, 0xFF888888, false);
            return;
        }
        int y = topPos + 58;
        for (int i = scroll; i < Math.min(accounts.size(), scroll + LIST_ROWS); i++) {
            S2COpenTerminalPacket.AccountRow row = accounts.get(i);
            graphics.drawString(font, "#" + row.number(), leftPos + 12, y + 3, 0xFFE0C060, false);
            graphics.drawString(font, row.owner(), leftPos + 62, y + 3, 0xFFFFFFFF, false);
            String balance = Money.format(row.balance(), "$");
            graphics.drawString(font, balance, leftPos + 250 - font.width(balance), y + 3,
                    0xFF6BE06B, false);
            if (row.hasLoan()) {
                graphics.drawString(font, Component.translatable("gui.athens_coins.has_loan"),
                        leftPos + 258, y + 3, 0xFFE06B6B, false);
            }
            y += ROW_H;
        }
    }

    private void renderSettings(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.athens_coins.bank_name"),
                leftPos + 8, topPos + 52, 0xFFB0A090, false);
        graphics.drawString(font, Component.translatable("gui.athens_coins.bank_value"),
                leftPos + 8, topPos + 74, 0xFFB0A090, false);

        // Show the current terms and the band the rates have to sit inside.
        int y = topPos + PANEL_H - 46;
        String terms = Component.translatable("gui.athens_coins.bank_terms",
                Money.format(bank.walletLimit(), "$"),
                Money.format(bank.commissionFee(), "$"),
                bank.commissionPeriodDays()).getString();
        graphics.drawString(font, terms, leftPos + 8, y, 0xFFD8C0A0, false);

        StringBuilder band = new StringBuilder();
        for (CoinType type : CoinType.ORDERED) {
            long own = bank.syncedRate(type);
            band.append(type.shortName().getString()).append(" ")
                    .append(Money.format(own, "$")).append("   ");
        }
        graphics.drawString(font, band.toString(), leftPos + 8, y + 10, 0xFF9EC5D8, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.USERS || tab == Tab.OPEN) {
            int y = topPos + 58;
            List<S2COpenTerminalPacket.PlayerRow> rows = players;
            for (int i = scroll; i < Math.min(rows.size(), scroll + LIST_ROWS); i++) {
                if (mouseY >= y && mouseY < y + ROW_H
                        && mouseX >= leftPos + 8 && mouseX < leftPos + PANEL_W - 8) {
                    S2COpenTerminalPacket.PlayerRow row = rows.get(i);
                    if (tab == Tab.USERS) {
                        sendFor(row.banker()
                                ? C2STerminalActionPacket.Action.REMOVE_BANKER
                                : C2STerminalActionPacket.Action.ADD_BANKER, row.id());
                    } else if (!row.hasAccount()) {
                        sendFor(C2STerminalActionPacket.Action.OPEN_ACCOUNT, row.id());
                    }
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int size = tab == Tab.ACCOUNTS ? accounts.size() : players.size();
        int max = Math.max(0, size - LIST_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

}
