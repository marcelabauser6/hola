package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.LoanRequest;
import com.athensmc.athenscoins.client.layout.PanelMetrics;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.widget.AmountField;
import com.athensmc.athenscoins.client.widget.BankPalette;
import com.athensmc.athenscoins.client.widget.CountField;
import com.athensmc.athenscoins.client.widget.FormList;
import com.athensmc.athenscoins.network.C2SBankConfigPacket;
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

/**
 * The bank terminal.
 *
 * <p>Two things about this screen are deliberate rather than incidental.</p>
 *
 * <p><b>Settings come first.</b> A bank that has never been through the settings form opens here with
 * every other tab locked. Configuring a bank last meant opening accounts against a default fee, a
 * default wallet ceiling and default exchange rates, and then changing the terms under customers who
 * had already signed up.</p>
 *
 * <p><b>The form is a list, not a grid of buttons.</b> Settings used to be eight buttons sharing two
 * entry boxes: you typed a number, worked out which button it belonged to, pressed it, and repeated,
 * with nothing on screen telling you what the bank's current values were. Now every field has its own
 * labelled box, pre-filled with what the bank actually has, and one Save submits the lot as a single
 * {@link C2SBankConfigPacket}.</p>
 */
public class BankTerminalScreen extends Screen {

    private static final int ROW_H = PanelMetrics.ROW_H;
    private static final int PAD = PanelMetrics.CONTENT_PAD;
    private static final int HINT_H = 12;

    private static final int TEXT_TITLE = 0xFFFFFFFF;
    private static final int TEXT_HINT = 0xFFB0A090;
    private static final int TEXT_MUTED = 0xFF8A7A6A;
    private static final int TEXT_HEADING = 0xFFE0C060;
    private static final int TEXT_VALUE = 0xFFFFFFFF;
    private static final int TEXT_GOOD = 0xFF6BE06B;
    private static final int TEXT_WARN = 0xFFE0C060;
    private static final int TEXT_BAD = 0xFFE06B6B;

    /**
     * The colour a credit standing is drawn in.
     *
     * <p>Shared with the account detail screen so a number never means green in one place and amber in
     * the other: the thresholds live here, once.</p>
     */
    public static int scoreColor(int score) {
        if (score >= 80) return TEXT_GOOD;
        if (score >= 50) return TEXT_WARN;
        return TEXT_BAD;
    }

    /** The label for a standing, so the number is never presented without a reading. */
    public static Component scoreLabel(int score) {
        String key = score >= 80 ? "gui.athens_coins.risk_good"
                : score >= 50 ? "gui.athens_coins.risk_fair" : "gui.athens_coins.risk_bad";
        return Component.translatable(key);
    }

    private enum Tab {
        SETTINGS("gui.athens_coins.tab_settings", true),
        USERS("gui.athens_coins.tab_users", true),
        OPEN("gui.athens_coins.tab_open", false),
        ACCOUNTS("gui.athens_coins.tab_accounts", false),
        /** Loan applications waiting to be approved or refused. */
        REQUESTS("gui.athens_coins.tab_requests", false),
        ATMS("gui.athens_coins.tab_atms", false);

        final String key;
        final boolean operatorOnly;

        Tab(String key, boolean operatorOnly) {
            this.key = key;
            this.operatorOnly = operatorOnly;
        }

        /** Every tab except Settings needs the bank configured first. */
        boolean needsConfigured() {
            return this != SETTINGS;
        }
    }

    private final Bank bank;
    private final BlockPos pos;
    private final boolean operator;
    private final List<S2COpenTerminalPacket.AccountRow> accounts;
    private final List<S2COpenTerminalPacket.PlayerRow> players;
    private final List<LoanRequest> requests;

    private Tab tab;
    private ScreenLayout.Regions layout;
    private ScreenLayout.Rect listArea;
    private ScreenLayout.Rect messageRow;
    private int scroll;
    private Component message;
    private boolean messageIsError;
    private Component hoverTooltip;

    /** Raised by the destructive row actions; modal while it is up. */
    private final ConfirmOverlay confirm = new ConfirmOverlay();

    // ---- settings form
    private final FormList form = new FormList();
    private EditBox nameBox;
    private AmountField walletLimitBox;
    private AmountField feeBox;
    private CountField feeDaysBox;
    private final AmountField[] rateBoxes = new AmountField[CoinType.ORDERED.length];
    private AmountField loanMaxBox;
    private CountField loanDaysBox;
    private AmountField loanInterestBox;
    private boolean loansEnabled;
    private int colorIndex;

    public BankTerminalScreen(S2COpenTerminalPacket packet) {
        super(Component.literal(packet.bank().name()));
        bank = packet.bank();
        pos = packet.pos();
        operator = packet.operator();
        accounts = packet.accounts();
        players = packet.players();
        requests = packet.requests();
        loansEnabled = bank.loansEnabled();
        colorIndex = BankPalette.indexOf(bank.themeColor());
        tab = firstUsableTab();
    }

    /**
     * Where the terminal opens.
     *
     * <p>An unconfigured bank always opens on Settings, and only an operator can get there - a banker
     * looking at an unconfigured bank is told to fetch one rather than shown four dead tabs.</p>
     */
    private Tab firstUsableTab() {
        if (!bank.configured()) {
            return Tab.SETTINGS;
        }
        return operator ? Tab.USERS : Tab.OPEN;
    }

    private int accent() {
        return 0xFF000000 | BankPalette.at(colorIndex).rgb();
    }

    private boolean locked(Tab candidate) {
        return candidate.needsConfigured() && !bank.configured();
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
        form.clear();
        nameBox = null;
        messageRow = null;

        buildTabs();

        ScreenLayout.Rect content = layout.content().inset(PAD);
        // Every tab reserves one line at the top for its explanation, so no list ever starts flush
        // against the tab strip with no idea of what it is.
        listArea = new ScreenLayout.Rect(content.x(), content.y() + HINT_H + 4,
                content.width(), Math.max(0, content.height() - HINT_H - 4));

        switch (tab) {
            case SETTINGS -> buildSettings();
            case ATMS -> buildAtms(content);
            default -> { }
        }

        buildFooter();
    }

    private void buildTabs() {
        long visibleTabs = java.util.Arrays.stream(Tab.values())
                .filter(candidate -> operator || !candidate.operatorOnly).count();
        ScreenLayout.Rect tabs = layout.tabs().inset(4);
        int gap = 2;
        int tabWidth = ScreenLayout.gridCellWidth(tabs, (int) visibleTabs, gap);
        int height = Math.min(20, tabs.height());
        int x = tabs.x();
        for (Tab candidate : Tab.values()) {
            if (candidate.operatorOnly && !operator) {
                continue;
            }
            Tab target = candidate;
            Component label = Component.translatable(candidate.key);
            boolean isLocked = locked(candidate);
            Component tip = isLocked
                    ? Component.translatable("gui.athens_coins.tab_locked")
                    : label;
            Button button = Button.builder(label, ignored -> {
                        tab = target;
                        scroll = 0;
                        rebuild();
                    })
                    .bounds(x, tabs.y(), tabWidth, height)
                    .tooltip(Tooltip.create(tip))
                    .build();
            // A locked tab and the current tab are both inactive; the tooltip is what tells them apart.
            button.active = candidate != tab && !isLocked;
            addRenderableWidget(button);
            x += tabWidth + gap;
        }
    }

    private void buildFooter() {
        ScreenLayout.Rect footer = layout.footer().inset(6);
        int buttonHeight = Math.min(20, footer.height());
        int closeWidth = Math.min(90, footer.width() / 3);
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"),
                        ignored -> onClose())
                .bounds(footer.right() - closeWidth, footer.y(), closeWidth, buttonHeight)
                .build());

        if (tab == Tab.SETTINGS) {
            int saveWidth = Math.min(150, Math.max(60, footer.width() / 3));
            addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.cfg_save"),
                            ignored -> submitConfig())
                    .bounds(footer.x(), footer.y(), saveWidth, buttonHeight)
                    .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.cfg_save_tip")))
                    .build());
            messageRow = new ScreenLayout.Rect(footer.x() + saveWidth + 6, footer.y() + 5,
                    Math.max(20, footer.width() - saveWidth - closeWidth - 12), 10);
        } else {
            messageRow = new ScreenLayout.Rect(footer.x(), footer.y() + 5,
                    Math.max(20, footer.width() - closeWidth - 8), 10);
        }
    }

    // ------------------------------------------------------------------ settings form

    private AmountField money(long cents) {
        AmountField field = new AmountField(font, 0, 0, 100, 16,
                Component.translatable("gui.athens_coins.bank_value"));
        field.setCents(cents);
        addRenderableWidget(field);
        return field;
    }

    private CountField count(int value) {
        CountField field = new CountField(font, 0, 0, 100, 16,
                Component.translatable("gui.athens_coins.bank_days"));
        field.setValue(Integer.toString(Math.max(1, value)));
        field.setHint(Component.translatable("gui.athens_coins.unit_days_hint"));
        addRenderableWidget(field);
        return field;
    }

    /**
     * A field label with its unit after it.
     *
     * <p>Eight boxes in a column, all accepting bare numbers, and nothing on screen saying which of them
     * wanted money, which wanted days and which wanted a percentage. The unit goes in the label rather than
     * only in the hint below it, because the hint disappears the moment there is a value in the box - which
     * is exactly when somebody is checking whether they typed the right kind of number.</p>
     */
    private static Component withUnit(Component label, String unitKey) {
        return label.copy().append(Component.literal(" "))
                .append(Component.translatable(unitKey));
    }

    /**
     * Builds the form. Every box starts holding the bank's current value, which is the difference
     * between a form and a set of blind inputs.
     */
    private void buildSettings() {
        nameBox = new EditBox(font, 0, 0, 100, 16, Component.translatable("gui.athens_coins.cfg_name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(bank.name());
        addRenderableWidget(nameBox);

        Button colorButton = Button.builder(Component.translatable(BankPalette.at(colorIndex).key()),
                        button -> {
                            colorIndex = (colorIndex + 1) % BankPalette.count();
                            button.setMessage(Component.translatable(BankPalette.at(colorIndex).key()));
                        })
                .bounds(0, 0, 100, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.cfg_color_tip")))
                .build();
        addRenderableWidget(colorButton);

        walletLimitBox = money(bank.walletLimit());
        feeBox = money(bank.commissionFee());
        feeDaysBox = count(bank.commissionPeriodDays());
        for (CoinType type : CoinType.ORDERED) {
            rateBoxes[type.ordinal()] = money(bank.syncedRate(type));
        }
        loanMaxBox = money(bank.loanMaxAmount());
        loanDaysBox = count(bank.loanDays());
        // Basis points shown as a percentage with two decimals: 250 reads as 2.50, which is what the
        // rate actually is. Nobody should have to think in basis points to set a late fee.
        loanInterestBox = money(bank.loanInterestBasisPoints());

        Button loansButton = Button.builder(loansLabel(), button -> {
                    loansEnabled = !loansEnabled;
                    button.setMessage(loansLabel());
                })
                .bounds(0, 0, 100, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.cfg_loans_tip")))
                .build();
        addRenderableWidget(loansButton);

        form.add(FormList.Row.heading(Component.translatable("gui.athens_coins.cfg_group_identity")));
        form.add(FormList.Row.of(Component.translatable("gui.athens_coins.cfg_name"), nameBox,
                Component.translatable("gui.athens_coins.cfg_name_hint")));
        form.add(FormList.Row.swatched(Component.translatable("gui.athens_coins.cfg_color"), colorButton,
                Component.translatable("gui.athens_coins.cfg_color_hint"),
                () -> BankPalette.at(colorIndex).rgb()));

        form.add(FormList.Row.heading(Component.translatable("gui.athens_coins.cfg_group_wallet")));
        form.add(FormList.Row.of(withUnit(Component.translatable("gui.athens_coins.cfg_wallet_limit"), "gui.athens_coins.unit_money"), walletLimitBox,
                Component.translatable("gui.athens_coins.cfg_wallet_limit_hint")));
        form.add(FormList.Row.of(withUnit(Component.translatable("gui.athens_coins.cfg_fee"), "gui.athens_coins.unit_money"), feeBox,
                Component.translatable("gui.athens_coins.cfg_fee_hint")));
        form.add(FormList.Row.of(withUnit(Component.translatable("gui.athens_coins.cfg_fee_days"), "gui.athens_coins.unit_days"), feeDaysBox,
                Component.translatable("gui.athens_coins.cfg_fee_days_hint")));

        form.add(FormList.Row.heading(Component.translatable("gui.athens_coins.cfg_group_rates")));
        for (CoinType type : CoinType.ORDERED) {
            form.add(FormList.Row.of(withUnit(type.shortName(), "gui.athens_coins.unit_money"), rateBoxes[type.ordinal()],
                    Component.translatable("gui.athens_coins.cfg_rate_hint")));
        }

        form.add(FormList.Row.heading(Component.translatable("gui.athens_coins.cfg_group_loans")));
        form.add(FormList.Row.of(Component.translatable("gui.athens_coins.cfg_loans"), loansButton,
                Component.translatable("gui.athens_coins.cfg_loans_hint")));
        form.add(FormList.Row.of(withUnit(Component.translatable("gui.athens_coins.cfg_loan_max"), "gui.athens_coins.unit_money"), loanMaxBox,
                Component.translatable("gui.athens_coins.cfg_loan_max_hint")));
        form.add(FormList.Row.of(withUnit(Component.translatable("gui.athens_coins.cfg_loan_days"), "gui.athens_coins.unit_days"), loanDaysBox,
                Component.translatable("gui.athens_coins.cfg_loan_days_hint")));
        form.add(FormList.Row.of(withUnit(Component.translatable("gui.athens_coins.cfg_loan_interest"), "gui.athens_coins.unit_percent"), loanInterestBox,
                Component.translatable("gui.athens_coins.cfg_loan_interest_hint")));

        layoutForm();
    }

    private void layoutForm() {
        ScreenLayout.Rect band = listArea;
        int controlWidth = ScreenLayout.clamp(band.width() * 45 / 100, 60, 200);
        form.layout(band, controlWidth);
    }

    private Component loansLabel() {
        return Component.translatable(loansEnabled
                ? "gui.athens_coins.bank_loans_on" : "gui.athens_coins.bank_loans_off");
    }

    /** Reads every field, refuses the whole form on the first bad one, and names the field. */
    private void submitConfig() {
        long walletLimit = walletLimitBox.cents();
        if (walletLimit < 0L) {
            fail("gui.athens_coins.cfg_wallet_limit", walletLimitBox.error());
            return;
        }
        long fee = feeBox.cents();
        if (fee < 0L) {
            fail("gui.athens_coins.cfg_fee", feeBox.error());
            return;
        }
        int feeDays = feeDaysBox.count();
        if (feeDays < 0) {
            fail("gui.athens_coins.cfg_fee_days", feeDaysBox.error());
            return;
        }
        long[] rates = new long[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            long rate = rateBoxes[type.ordinal()].cents();
            if (rate < 0L) {
                fail("gui.athens_coins.cfg_group_rates", rateBoxes[type.ordinal()].error());
                return;
            }
            rates[type.ordinal()] = rate;
        }
        long loanMax = loanMaxBox.cents();
        if (loanMax < 0L) {
            fail("gui.athens_coins.cfg_loan_max", loanMaxBox.error());
            return;
        }
        int loanDays = loanDaysBox.count();
        if (loanDays < 0) {
            fail("gui.athens_coins.cfg_loan_days", loanDaysBox.error());
            return;
        }
        long interest = loanInterestBox.cents();
        if (interest < 0L) {
            fail("gui.athens_coins.cfg_loan_interest", loanInterestBox.error());
            return;
        }

        ModNetwork.toServer(new C2SBankConfigPacket(pos, nameBox.getValue(),
                BankPalette.at(colorIndex).rgb(), walletLimit, fee, feeDays, rates,
                loansEnabled, loanMax, loanDays, (int) interest));
        message = Component.translatable("gui.athens_coins.cfg_sent");
        messageIsError = false;
    }

    private void fail(String fieldKey, Component reason) {
        message = Component.translatable("gui.athens_coins.cfg_bad_field",
                Component.translatable(fieldKey),
                reason == null ? Component.translatable("message.athens_coins.amount_invalid") : reason);
        messageIsError = true;
    }

    // ------------------------------------------------------------------ other tabs

    private ScreenLayout.Columns atmColumns(ScreenLayout.Rect content) {
        return ScreenLayout.columns(content, 8);
    }

    private void buildAtms(ScreenLayout.Rect content) {
        ScreenLayout.Rect column = atmColumns(listArea).first();
        int buttonWidth = Math.min(170, column.width());
        int y = listArea.y() + 4;
        for (int count : new int[] {1, 4}) {
            int amount = count;
            addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.issue_atm", amount),
                            ignored -> send(C2STerminalActionPacket.Action.ISSUE_ATM, amount, ""))
                    .bounds(column.x(), y, buttonWidth, 20).build());
            y += 24;
        }
        // A stats board for this bank, issued the same way and for the same reason: what it reports has to
        // be decided by whoever handed it over, not by whoever stands it up.
        y += 6;
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.issue_board"),
                        ignored -> send(C2STerminalActionPacket.Action.ISSUE_HOLOGRAM, 1L, ""))
                .bounds(column.x(), y, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.issue_board_tip")))
                .build());
    }

    private void send(C2STerminalActionPacket.Action action, long value, String text) {
        ModNetwork.toServer(new C2STerminalActionPacket(pos, action, null, value, text));
    }

    private void sendFor(C2STerminalActionPacket.Action action, UUID target) {
        ModNetwork.toServer(new C2STerminalActionPacket(pos, action, target, 0L, ""));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hoverTooltip = null;
        ScreenLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF0140F0C);
        Panels.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), accent());
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1,
                layout.header().bottom() - 2, accent());

        renderHeader(graphics, mouseX, mouseY);

        if (locked(tab)) {
            // Should not be reachable, since a locked tab's button is inactive; drawn defensively so
            // a future tab added without a lock check fails visibly rather than silently.
            drawFitted(graphics, Component.translatable("gui.athens_coins.cfg_required").getString(),
                    listArea.x(), listArea.y(), listArea.width(), TEXT_WARN, mouseX, mouseY);
        } else {
            switch (tab) {
                case SETTINGS -> renderSettings(graphics, mouseX, mouseY);
                case USERS -> renderPlayers(graphics, mouseX, mouseY, true);
                case OPEN -> renderPlayers(graphics, mouseX, mouseY, false);
                case ACCOUNTS -> renderAccounts(graphics, mouseX, mouseY);
                case REQUESTS -> renderRequests(graphics, mouseX, mouseY);
                case ATMS -> renderAtms(graphics, mouseX, mouseY);
            }
        }

        if (message != null && messageRow != null) {
            drawFitted(graphics, message.getString(), messageRow.x(), messageRow.y(),
                    messageRow.width(), messageIsError ? TEXT_BAD : TEXT_GOOD, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null && !confirm.consumesInput()) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
        }
        // Last, so it covers the widgets rather than being covered by them.
        confirm.render(graphics, font, panel, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect header = layout.header();
        String reserve = Component.translatable("gui.athens_coins.bank_reserve").getString()
                + " " + Money.format(bank.reserve(), "$");
        // Two budgets that sum to the bar, so the name and the reserve can never meet.
        int reserveBudget = Math.min(header.width() / 2, font.width(reserve));
        int nameBudget = Math.max(20, header.width() - reserveBudget - PAD * 3);
        drawFitted(graphics, bank.name(), header.x() + PAD, header.y() + 7, nameBudget,
                TEXT_TITLE, mouseX, mouseY);
        drawRight(graphics, reserve, header.right() - PAD, header.y() + 7, reserveBudget,
                TEXT_GOOD, mouseX, mouseY);
    }

    /** The one-line explanation every tab shows above its content. */
    private void renderHint(GuiGraphics graphics, String key, int mouseX, int mouseY) {
        ScreenLayout.Rect content = layout.content().inset(PAD);
        drawFitted(graphics, Component.translatable(key).getString(), content.x(), content.y() + 2,
                content.width(), TEXT_HINT, mouseX, mouseY);
    }

    private void renderSettings(GuiGraphics graphics, int mouseX, int mouseY) {
        renderHint(graphics, bank.configured()
                ? "gui.athens_coins.cfg_hint" : "gui.athens_coins.cfg_required", mouseX, mouseY);
        Component hovered = form.render(graphics, font, mouseX, mouseY,
                TEXT_VALUE, TEXT_HEADING, TEXT_MUTED);
        if (hovered != null) {
            hoverTooltip = hovered;
        }
    }

    private void renderPlayers(GuiGraphics graphics, int mouseX, int mouseY, boolean bankerMode) {
        renderHint(graphics, bankerMode
                ? "gui.athens_coins.users_hint" : "gui.athens_coins.open_hint", mouseX, mouseY);
        if (players.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.nobody_online").getString(),
                    listArea.x(), listArea.y() + 2, listArea.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = listArea.y();
        graphics.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        for (int i = scroll; i < Math.min(players.size(), scroll + rows); i++) {
            S2COpenTerminalPacket.PlayerRow row = players.get(i);
            boolean hovered = listArea.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) {
                graphics.fill(listArea.x(), y, listArea.right(), y + ROW_H - 1, 0x40FFFFFF);
            }
            String state = Component.translatable(bankerMode
                    ? (row.banker() ? "gui.athens_coins.is_banker" : "gui.athens_coins.not_banker")
                    : (row.hasAccount() ? "gui.athens_coins.has_account" : "gui.athens_coins.no_account")).getString();
            int color = bankerMode ? (row.banker() ? TEXT_GOOD : TEXT_MUTED)
                    : (row.hasAccount() ? TEXT_WARN : TEXT_GOOD);
            int stateWidth = Math.min(listArea.width() / 2, font.width(state));
            int nameWidth = Math.max(8, listArea.width() - stateWidth - 16);
            drawFitted(graphics, row.name(), listArea.x() + 6, y + 3, nameWidth,
                    TEXT_VALUE, mouseX, mouseY);
            drawRight(graphics, state, listArea.right() - 6, y + 3, stateWidth, color, mouseX, mouseY);
            // Say what a click will do, on the row the cursor is on. Clicking a name used to be an
            // unlabelled action with no indication that the row was even clickable.
            if (hovered) {
                boolean actionable = bankerMode || !row.hasAccount();
                hoverTooltip = Component.translatable(bankerMode
                        ? (row.banker() ? "gui.athens_coins.click_revoke_banker"
                        : "gui.athens_coins.click_grant_banker")
                        : (actionable ? "gui.athens_coins.click_open_account"
                        : "gui.athens_coins.click_already_has"), row.name());
            }
            y += ROW_H;
        }
        graphics.disableScissor();
    }

    private void renderAccounts(GuiGraphics graphics, int mouseX, int mouseY) {
        renderHint(graphics, "gui.athens_coins.accounts_hint2", mouseX, mouseY);
        if (accounts.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.no_accounts").getString(),
                    listArea.x(), listArea.y() + 2, listArea.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = listArea.y();
        graphics.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        for (int i = scroll; i < Math.min(accounts.size(), scroll + rows); i++) {
            S2COpenTerminalPacket.AccountRow row = accounts.get(i);
            boolean hovered = listArea.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) {
                graphics.fill(listArea.x(), y, listArea.right(), y + ROW_H - 1, 0x40FFFFFF);
            }
            String number = "#" + row.number();
            String balance = Money.format(row.balance(), "$");
            String loan = row.hasLoan()
                    ? Component.translatable("gui.athens_coins.has_loan").getString() : "";
            // The risk desk, one glyph wide: the standing every row carries, so a banker can spot a
            // bad customer from the list instead of opening all of them.
            String score = String.valueOf(row.score());
            // Budgets carved from the row so they sum to its width.
            int numberWidth = Math.min(52, listArea.width() / 5);
            int scoreWidth = Math.min(28, font.width("100") + 2);
            int balanceWidth = Math.min(90, font.width(balance) + 2);
            int loanWidth = loan.isEmpty() ? 0 : Math.min(70, font.width(loan) + 6);
            int nameWidth = Math.max(8, listArea.width() - numberWidth - scoreWidth
                    - balanceWidth - loanWidth - 24);
            graphics.drawString(font, ScreenText.fit(font, number, numberWidth),
                    listArea.x() + 6, y + 3, TEXT_HEADING, false);
            drawFitted(graphics, row.owner(), listArea.x() + 6 + numberWidth, y + 3, nameWidth,
                    TEXT_VALUE, mouseX, mouseY);
            int right = listArea.right() - 6;
            if (!loan.isEmpty()) {
                drawRight(graphics, loan, right, y + 3, loanWidth, TEXT_BAD, mouseX, mouseY);
                right -= loanWidth + 4;
            }
            drawRight(graphics, balance, right, y + 3, balanceWidth, TEXT_GOOD, mouseX, mouseY);
            right -= balanceWidth + 4;
            drawRight(graphics, score, right, y + 3, scoreWidth, scoreColor(row.score()),
                    mouseX, mouseY);
            if (hovered) {
                hoverTooltip = Component.translatable("gui.athens_coins.click_account_detail",
                        row.owner(), row.score());
            }
            y += ROW_H;
        }
        graphics.disableScissor();
    }

    /**
     * The loan queue.
     *
     * <p>Left click approves, right click refuses, and the row says so while the cursor is on it.
     * Both go through a chat confirmation, because lending the bank's reserve is not something a
     * stray click should do.</p>
     */
    private void renderRequests(GuiGraphics graphics, int mouseX, int mouseY) {
        renderHint(graphics, "gui.athens_coins.requests_hint", mouseX, mouseY);
        if (!bank.loansEnabled()) {
            drawFitted(graphics, Component.translatable("message.athens_coins.bank_loans_disabled").getString(),
                    listArea.x(), listArea.y() + 2, listArea.width(), TEXT_WARN, mouseX, mouseY);
            return;
        }
        if (requests.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.requests_none").getString(),
                    listArea.x(), listArea.y() + 2, listArea.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        int rows = visibleRows();
        int y = listArea.y();
        graphics.enableScissor(listArea.x(), listArea.y(), listArea.right(), listArea.bottom());
        for (int i = scroll; i < Math.min(requests.size(), scroll + rows); i++) {
            LoanRequest request = requests.get(i);
            boolean hovered = listArea.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) {
                graphics.fill(listArea.x(), y, listArea.right(), y + ROW_H - 1, 0x40FFFFFF);
            }
            String amount = Money.format(request.amount(), "$");
            String number = "#" + request.account();
            int numberWidth = Math.min(52, listArea.width() / 5);
            int amountWidth = Math.min(100, font.width(amount) + 2);
            int nameWidth = Math.max(8, listArea.width() - numberWidth - amountWidth - 20);
            graphics.drawString(font, ScreenText.fit(font, number, numberWidth),
                    listArea.x() + 6, y + 3, TEXT_HEADING, false);
            drawFitted(graphics, request.borrowerName(), listArea.x() + 6 + numberWidth, y + 3,
                    nameWidth, TEXT_VALUE, mouseX, mouseY);
            drawRight(graphics, amount, listArea.right() - 6, y + 3, amountWidth,
                    TEXT_WARN, mouseX, mouseY);
            if (hovered) {
                hoverTooltip = Component.translatable("gui.athens_coins.click_request",
                        request.borrowerName(), amount);
            }
            y += ROW_H;
        }
        graphics.disableScissor();
    }

    private void renderAtms(GuiGraphics graphics, int mouseX, int mouseY) {
        renderHint(graphics, "gui.athens_coins.atms_hint", mouseX, mouseY);
        ScreenLayout.Rect rates = atmColumns(listArea).second();
        int y = listArea.y() + 4;
        drawFitted(graphics, Component.translatable("gui.athens_coins.atms_rates").getString(),
                rates.x(), y, rates.width(), TEXT_HEADING, mouseX, mouseY);
        y += 14;
        for (CoinType type : CoinType.ORDERED) {
            String line = type.shortName().getString() + ": " + Money.format(bank.syncedRate(type), "$");
            drawFitted(graphics, line, rates.x(), y, rates.width(), 0xFF9EC5D8, mouseX, mouseY);
            y += 12;
        }
        drawFitted(graphics, Component.translatable("gui.athens_coins.atms_note").getString(),
                listArea.x(), listArea.bottom() - 12, listArea.width(), TEXT_MUTED, mouseX, mouseY);
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

    private int visibleRows() {
        return Math.max(1, ScreenLayout.visibleRows(listArea, ROW_H));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While a question is up nothing else in the screen is live. The row that raised it is still
        // drawn underneath, so without this a second click would queue a second action behind the first.
        if (confirm.consumesInput()) {
            return confirm.mouseClicked(mouseX, mouseY);
        }
        if (locked(tab)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int rows = visibleRows();
        if (tab == Tab.USERS || tab == Tab.OPEN) {
            int index = rowAt(mouseX, mouseY, players.size(), rows);
            if (index >= 0) {
                S2COpenTerminalPacket.PlayerRow row = players.get(index);
                if (tab == Tab.USERS) {
                    boolean revoke = row.banker();
                    confirm.ask(Component.translatable(revoke
                                    ? "message.athens_coins.ask_revoke_banker"
                                    : "message.athens_coins.ask_grant_banker", row.name()),
                            Component.translatable(revoke
                                    ? "gui.athens_coins.ask_revoke_detail"
                                    : "gui.athens_coins.ask_grant_detail"),
                            () -> sendFor(revoke ? C2STerminalActionPacket.Action.REMOVE_BANKER
                                    : C2STerminalActionPacket.Action.ADD_BANKER, row.id()));
                } else if (!row.hasAccount()) {
                    // Confirmed here like every other action. It was briefly an offer the customer
                    // accepted from chat, which needed a command to back the buttons - and a command
                    // whose visibility depends on having a pending offer is a command that is missing
                    // exactly when you need it. The dialog is where the click is.
                    confirm.ask(Component.translatable("message.athens_coins.ask_open_account",
                                    row.name()),
                            Component.translatable("gui.athens_coins.ask_open_detail"),
                            () -> sendFor(C2STerminalActionPacket.Action.OPEN_ACCOUNT, row.id()));
                } else {
                    message = Component.translatable("gui.athens_coins.click_already_has", row.name());
                    messageIsError = true;
                }
                return true;
            }
        } else if (tab == Tab.REQUESTS && (button == 0 || button == 1)) {
            int index = rowAt(mouseX, mouseY, requests.size(), rows);
            if (index >= 0) {
                LoanRequest request = requests.get(index);
                boolean approve = button == 0;
                String amount = Money.format(request.amount(), "$");
                confirm.ask(Component.translatable(approve
                                ? "message.athens_coins.ask_approve_loan"
                                : "message.athens_coins.ask_reject_loan",
                                request.borrowerName(), amount),
                        Component.translatable(approve
                                ? "gui.athens_coins.ask_approve_detail"
                                : "gui.athens_coins.ask_reject_detail"),
                        // Amount 0 means "the amount they asked for"; the server clamps against policy.
                        () -> ModNetwork.toServer(new C2STerminalActionPacket(pos,
                                approve ? C2STerminalActionPacket.Action.APPROVE_LOAN
                                        : C2STerminalActionPacket.Action.REJECT_LOAN,
                                null, request.id(), 0L, "")));
                return true;
            }
        } else if (tab == Tab.ACCOUNTS && (button == 0 || button == 1)) {
            int index = rowAt(mouseX, mouseY, accounts.size(), rows);
            if (index >= 0) {
                S2COpenTerminalPacket.AccountRow row = accounts.get(index);
                if (button == 0) {
                    ModNetwork.toServer(new com.athensmc.athenscoins.network.C2SRequestAccountPacket(
                            pos, row.number()));
                } else {
                    confirm.ask(Component.translatable("message.athens_coins.ask_close_account",
                                    "#" + row.number() + " " + row.owner()),
                            Component.translatable("gui.athens_coins.ask_close_detail"),
                            () -> ModNetwork.toServer(new C2STerminalActionPacket(pos,
                                    C2STerminalActionPacket.Action.WITHDRAW_ALL, null,
                                    row.number(), 0L, "")));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirm.consumesInput()) {
            // Escape backs out of the question, not out of the whole terminal.
            if (keyCode == 256) {
                confirm.cancel();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (confirm.consumesInput()) {
            return true;
        }
        if (tab == Tab.SETTINGS) {
            if (form.contains(mouseX, mouseY)) {
                form.scrollBy((int) -Math.signum(delta) * 12);
                layoutForm();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (tab != Tab.ACCOUNTS && tab != Tab.USERS && tab != Tab.OPEN && tab != Tab.REQUESTS) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int size = switch (tab) {
            case ACCOUNTS -> accounts.size();
            case REQUESTS -> requests.size();
            default -> players.size();
        };
        int max = Math.max(0, size - visibleRows());
        scroll = ScreenLayout.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }
}
