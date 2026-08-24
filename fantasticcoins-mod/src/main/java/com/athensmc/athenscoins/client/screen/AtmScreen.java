package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.client.layout.AtmLayout;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.widget.AmountField;
import com.athensmc.athenscoins.client.widget.CountField;
import com.athensmc.athenscoins.menu.AtmMenu;
import com.athensmc.athenscoins.menu.AtmState;
import com.athensmc.athenscoins.network.C2SAtmActionPacket;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The ATM: exchange coins for cash, move cash between the account and the wallet card, send a
 * transfer, and take or repay a loan.
 *
 * <p>This screen used to be a fixed 248x198 texture with every coordinate hardcoded against it, and
 * it was the only screen in the mod that never went through {@link ScreenLayout}. Two consequences
 * were visible in game: the column headers and footer hints were drawn with no width budget, so a
 * slightly longer string ran straight into the next column, and the cash controls could only move
 * <em>everything</em>, because vanilla's container-button channel carries no amount.</p>
 *
 * <p>Both are addressed here. Geometry comes from {@link AtmLayout}, which the layout guard also
 * calls, and every string is measured before it is drawn. Amounts are typed into an
 * {@link AmountField} and travel as {@link C2SAtmActionPacket}, so "withdraw 12.50" is expressible.</p>
 */
public class AtmScreen extends AbstractContainerScreen<AtmMenu> {

    private static final DateTimeFormatter DUE_STAMP =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private static final int GLFW_KEY_ESCAPE = 256;

    private static final int ROW_H = 24;
    private static final int LIST_ROW_H = 14;
    private static final int LINE_H = 11;

    private static final int TEXT_MUTED = 0xFF8A9AA6;
    private static final int TEXT_BODY = 0xFFD8C0A0;
    private static final int TEXT_VALUE = 0xFFFFFFFF;
    private static final int TEXT_CASH = 0xFF6BE06B;
    private static final int TEXT_WARN = 0xFFE0C060;
    private static final int TEXT_BAD = 0xFFE06B6B;

    private enum Tab {
        EXCHANGE("gui.athens_coins.atm_tab_exchange"),
        CASH("gui.athens_coins.atm_tab_cash"),
        TRANSFER("gui.athens_coins.atm_tab_transfer"),
        LOAN("gui.athens_coins.atm_tab_loan");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private final BlockPos pos;

    private Tab tab = Tab.EXCHANGE;
    private AtmLayout layout;
    private int mode = AtmMenu.MODE_TO_CASH;
    private int exchangeCoin;
    private int peerScroll;
    private int selectedPeer;

    @Nullable
    private AmountField amountField;
    @Nullable
    private CountField countField;
    @Nullable
    private Component message;
    private boolean messageIsError;
    @Nullable
    private Component hoverTooltip;

    public AtmScreen(AtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.pos = menu.state().atmPos();
    }

    private AtmState state() {
        return menu.state();
    }

    private int accent() {
        return 0xFF000000 | state().themeColor();
    }

    private String symbol() {
        return "$";
    }

    // ------------------------------------------------------------------ construction

    @Override
    protected void init() {
        layout = AtmLayout.of(width, height);
        // AbstractContainerScreen derives leftPos/topPos from these, landing on the same rectangle
        // AtmLayout centred, so the two coordinate systems agree.
        imageWidth = layout.regions().panel().width();
        imageHeight = layout.regions().panel().height();
        super.init();
        rebuild();
    }

    /** Called when a fresh server state arrives: the widgets are built from it, so redo them. */
    public void onStateChanged() {
        if (layout != null) {
            rebuild();
        }
    }

    private void rebuild() {
        clearWidgets();
        amountField = null;
        countField = null;

        buildTabs();
        switch (tab) {
            case EXCHANGE -> buildExchange();
            case CASH -> buildCash();
            case TRANSFER -> buildTransfer();
            case LOAN -> buildLoan();
        }
        ScreenLayout.Rect close = layout.closeButton();
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"),
                        ignored -> onClose())
                .bounds(close.x(), close.y(), close.width(), close.height())
                .build());
    }

    private void buildTabs() {
        ScreenLayout.Rect strip = layout.tabStrip();
        Tab[] tabs = Tab.values();
        int gap = 2;
        int cellWidth = ScreenLayout.gridCellWidth(strip, tabs.length, gap);
        int height = Math.min(18, strip.height());
        int x = strip.x();
        for (Tab candidate : tabs) {
            Component label = Component.translatable(candidate.key);
            Button button = Button.builder(label, ignored -> switchTo(candidate))
                    .bounds(x, strip.y(), cellWidth, height)
                    .tooltip(Tooltip.create(label))
                    .build();
            button.active = candidate != tab;
            addRenderableWidget(button);
            x += cellWidth + gap;
        }
    }

    private void switchTo(Tab target) {
        tab = target;
        message = null;
        peerScroll = 0;
        rebuild();
    }

    /** The shared entry box, placed in the footer band so every tab has identical geometry. */
    private AmountField addAmountField(int buttons) {
        ScreenLayout.Rect box = layout.amountBox(buttons);
        AmountField field = new AmountField(font, box.x(), box.y(), box.width(), box.height(),
                Component.translatable("gui.athens_coins.amount_label"));
        addRenderableWidget(field);
        amountField = field;
        return field;
    }

    private void addAction(int buttons, int index, String labelKey, String tipKey, Runnable action) {
        ScreenLayout.Rect cell = layout.actionCell(buttons, index);
        addRenderableWidget(Button.builder(Component.translatable(labelKey), ignored -> action.run())
                .bounds(cell.x(), cell.y(), cell.width(), cell.height())
                .tooltip(Tooltip.create(Component.translatable(tipKey)))
                .build());
    }

    // ------------------------------------------------------------------ tabs

    private void buildExchange() {
        ScreenLayout.Rect content = layout.content();
        // The direction toggle sits on its own line above the table.
        int toggleWidth = Math.min(150, Math.max(90, content.width() / 2));
        addRenderableWidget(Button.builder(modeLabel(), button -> {
                    mode = mode == AtmMenu.MODE_TO_CASH ? AtmMenu.MODE_TO_COINS : AtmMenu.MODE_TO_CASH;
                    button.setMessage(modeLabel());
                })
                .bounds(content.x(), content.y(), toggleWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.mode_tooltip")))
                .build());

        // Preset buttons, right-aligned so they cannot grow into the text columns.
        int presets = AtmMenu.AMOUNTS.length;
        int presetWidth = 20;
        int presetGap = 2;
        int stripWidth = presets * presetWidth + (presets - 1) * presetGap;
        int stripX = Math.max(content.x(), content.right() - stripWidth);
        for (CoinType type : CoinType.ORDERED) {
            int rowY = content.y() + 22 + type.ordinal() * ROW_H;
            for (int index = 0; index < presets; index++) {
                CoinType coin = type;
                int preset = index;
                addRenderableWidget(Button.builder(Component.literal(presetLabel(index)),
                                ignored -> sendPreset(coin, preset))
                        .bounds(stripX + index * (presetWidth + presetGap), rowY + 3, presetWidth, 18)
                        .tooltip(Tooltip.create(presetTooltip(index)))
                        .build());
            }
        }

        // Manual count entry, in the shared footer row.
        ScreenLayout.Rect box = layout.amountBox(2);
        CountField field = new CountField(font, box.x(), box.y(), box.width(), box.height(),
                Component.translatable("gui.athens_coins.count_label"));
        addRenderableWidget(field);
        countField = field;
        addAction(2, 0, coinCycleLabelKey(), "gui.athens_coins.atm_coin_pick_tip", this::cycleCoin);
        addAction(2, 1, "gui.athens_coins.atm_do_exchange", "gui.athens_coins.atm_do_exchange_tip",
                this::sendManualExchange);
    }

    private void buildCash() {
        AmountField field = addAmountField(3);
        addAction(3, 0, "gui.athens_coins.atm_withdraw", "gui.athens_coins.atm_withdraw_tip",
                () -> sendAmount(field, C2SAtmActionPacket.Action.WITHDRAW));
        addAction(3, 1, "gui.athens_coins.atm_deposit", "gui.athens_coins.atm_deposit_tip",
                () -> sendAmount(field, C2SAtmActionPacket.Action.DEPOSIT));
        // "Max" fills the box rather than acting, so the player still sees and confirms the figure.
        addAction(3, 2, "gui.athens_coins.atm_max", "gui.athens_coins.atm_max_tip", this::fillMaxCash);
    }

    private void buildTransfer() {
        AtmState state = state();
        if (state.peers().isEmpty()) {
            return;
        }
        if (selectedPeer >= state.peers().size()) {
            selectedPeer = 0;
        }
        AmountField field = addAmountField(1);
        addAction(1, 0, "gui.athens_coins.atm_send", "gui.athens_coins.atm_send_tip",
                () -> sendTransfer(field));
    }

    private void buildLoan() {
        AtmState state = state();
        if (!state.loansEnabled() && !state.hasLoan()) {
            return;
        }
        if (state.hasLoan()) {
            AmountField field = addAmountField(2);
            addAction(2, 0, "gui.athens_coins.atm_repay", "gui.athens_coins.atm_repay_tip",
                    () -> sendAmount(field, C2SAtmActionPacket.Action.LOAN_REPAY));
            addAction(2, 1, "gui.athens_coins.atm_repay_all", "gui.athens_coins.atm_repay_all_tip",
                    () -> field.setCents(state.loanOwed()));
            return;
        }
        // An application already waiting: nothing to fill in, so no field either.
        if (state.pendingRequest() > 0L) {
            return;
        }
        AmountField field = addAmountField(2);
        addAction(2, 0, "gui.athens_coins.atm_apply", "gui.athens_coins.atm_apply_tip",
                () -> sendAmount(field, C2SAtmActionPacket.Action.LOAN_REQUEST));
        addAction(2, 1, "gui.athens_coins.atm_borrow_max", "gui.athens_coins.atm_borrow_max_tip",
                () -> field.setCents(state.loanMax()));
    }

    // ------------------------------------------------------------------ actions

    /**
     * Reads the box and sends, or shows why it will not send.
     *
     * <p>The point of the check is that nothing leaves the client on a bad amount. The old staff
     * boxes turned an unparseable string into {@code 0} and sent it anyway, so the server performed a
     * no-op and the player was told nothing at all.</p>
     */
    private void sendAmount(AmountField field, C2SAtmActionPacket.Action action) {
        long cents = field.cents();
        if (cents < 0L) {
            fail(field.error());
            return;
        }
        ModNetwork.toServer(new C2SAtmActionPacket(pos, action, cents));
        field.clear();
        message = null;
    }

    private void sendTransfer(AmountField field) {
        AtmState state = state();
        if (state.peers().isEmpty()) {
            fail(Component.translatable("gui.athens_coins.atm_no_peers"));
            return;
        }
        long cents = field.cents();
        if (cents < 0L) {
            fail(field.error());
            return;
        }
        AtmState.Peer peer = state.peers().get(Math.min(selectedPeer, state.peers().size() - 1));
        ModNetwork.toServer(new C2SAtmActionPacket(pos, C2SAtmActionPacket.Action.TRANSFER,
                cents, peer.id(), -1));
        field.clear();
        message = null;
    }

    private void sendManualExchange() {
        if (countField == null) {
            return;
        }
        int count = countField.count();
        if (count < 0) {
            fail(countField.error());
            return;
        }
        ModNetwork.toServer(new C2SAtmActionPacket(pos,
                mode == AtmMenu.MODE_TO_CASH
                        ? C2SAtmActionPacket.Action.EXCHANGE_TO_CASH
                        : C2SAtmActionPacket.Action.EXCHANGE_TO_COINS,
                count, null, exchangeCoin));
        countField.clear();
        message = null;
    }

    private void sendPreset(CoinType type, int amountIndex) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    AtmMenu.buttonId(mode, type, amountIndex));
        }
    }

    private void cycleCoin() {
        exchangeCoin = (exchangeCoin + 1) % CoinType.ORDERED.length;
        rebuild();
    }

    /** Pre-fills the largest amount the current direction could actually move. */
    private void fillMaxCash() {
        AtmState state = state();
        if (amountField == null) {
            return;
        }
        long room = state.walletRoom();
        long withdrawable = room < 0L ? state.accountBalance()
                : Math.min(state.accountBalance(), room);
        long best = Math.max(withdrawable, state.cash());
        if (best <= 0L) {
            fail(Component.translatable("message.athens_coins.atm_nothing_to_move"));
            return;
        }
        amountField.setCents(best);
        message = null;
    }

    private void fail(@Nullable Component reason) {
        message = reason == null ? Component.translatable("message.athens_coins.amount_invalid") : reason;
        messageIsError = true;
    }

    private Component modeLabel() {
        return Component.translatable(mode == AtmMenu.MODE_TO_CASH
                ? "gui.athens_coins.mode_to_cash"
                : "gui.athens_coins.mode_to_coins");
    }

    private String coinCycleLabelKey() {
        return switch (CoinType.ORDERED[exchangeCoin]) {
            case BRONZE -> "gui.athens_coins.atm_coin_bronze";
            case SILVER -> "gui.athens_coins.atm_coin_silver";
            case GOLD -> "gui.athens_coins.atm_coin_gold";
        };
    }

    private String presetLabel(int index) {
        int preset = AtmMenu.AMOUNTS[index];
        return preset < 0 ? "M" : Integer.toString(preset);
    }

    private Component presetTooltip(int index) {
        int preset = AtmMenu.AMOUNTS[index];
        return preset < 0
                ? Component.translatable("gui.athens_coins.amount_all")
                : Component.translatable("gui.athens_coins.amount", preset);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        hoverTooltip = null;
        ScreenLayout.Rect panel = layout.regions().panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF0140F0C);
        StatsScreen.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), accent());
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1,
                layout.regions().header().bottom() - 2, accent());

        renderHeader(graphics, mouseX, mouseY);
        switch (tab) {
            case EXCHANGE -> renderExchange(graphics, mouseX, mouseY);
            case CASH -> renderCash(graphics, mouseX, mouseY);
            case TRANSFER -> renderTransfer(graphics, mouseX, mouseY);
            case LOAN -> renderLoan(graphics, mouseX, mouseY);
        }
        renderMessage(graphics, mouseX, mouseY);
    }

    /** The vanilla container labels would draw a second, offset title on top of ours. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        AtmState state = state();
        ScreenLayout.Rect header = layout.regions().header();
        String bank = state.bankName().isEmpty()
                ? Component.translatable("gui.athens_coins.atm").getString() : state.bankName();
        String card = Component.translatable("gui.athens_coins.atm_card").getString()
                + " " + Money.format(state.cash(), symbol());
        // Split the bar into two budgets that sum to its width, so the two can never meet.
        int cardBudget = Math.min(header.width() / 2, font.width(card));
        int bankBudget = Math.max(20, header.width() - cardBudget - 16);
        drawFitted(graphics, bank, header.x() + 6, header.y() + 6, bankBudget, TEXT_VALUE, mouseX, mouseY);
        drawRight(graphics, card, header.right() - 6, header.y() + 6, cardBudget, TEXT_CASH, mouseX, mouseY);
    }

    private void renderExchange(GuiGraphics graphics, int mouseX, int mouseY) {
        AtmState state = state();
        ScreenLayout.Rect content = layout.content();
        int headerY = content.y() + 20;

        // Column budgets are carved out of the row, right to left, so each has an explicit share.
        int presetsWidth = AtmMenu.AMOUNTS.length * 20 + (AtmMenu.AMOUNTS.length - 1) * 2;
        int textWidth = Math.max(40, content.width() - presetsWidth - 6);
        int priceRight = content.x() + textWidth;
        int priceWidth = Math.max(30, textWidth * 34 / 100);
        int haveWidth = Math.max(24, textWidth * 22 / 100);
        int haveRight = priceRight - priceWidth - 4;
        int nameWidth = Math.max(20, haveRight - haveWidth - content.x() - 22);

        drawFitted(graphics, Component.translatable("gui.athens_coins.column_coin").getString(),
                content.x() + 20, headerY, nameWidth, TEXT_MUTED, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.column_have").getString(),
                haveRight, headerY, haveWidth, TEXT_MUTED, mouseX, mouseY);
        drawRight(graphics, Component.translatable("gui.athens_coins.column_price").getString(),
                priceRight, headerY, priceWidth, TEXT_MUTED, mouseX, mouseY);

        for (CoinType type : CoinType.ORDERED) {
            int rowY = content.y() + 22 + type.ordinal() * ROW_H;
            int textY = rowY + 7;
            graphics.renderItem(new ItemStack(type.item()), content.x(), rowY + 2);
            drawFitted(graphics, type.shortName().getString(), content.x() + 20, textY,
                    nameWidth, TEXT_BODY, mouseX, mouseY);
            drawRight(graphics, Money.compactCount(state.coinCount(type)), haveRight, textY,
                    haveWidth, TEXT_VALUE, mouseX, mouseY);
            drawRight(graphics, Money.format(state.rate(type), symbol()), priceRight, textY,
                    priceWidth, TEXT_CASH, mouseX, mouseY);
        }
    }

    private void renderCash(GuiGraphics graphics, int mouseX, int mouseY) {
        AtmState state = state();
        ScreenLayout.Rect content = layout.content();
        int y = content.y() + 2;
        y = line(graphics, content, y, "gui.athens_coins.atm_in_account",
                Money.format(state.accountBalance(), symbol()), TEXT_CASH, mouseX, mouseY);
        y = line(graphics, content, y, "gui.athens_coins.atm_on_card",
                Money.format(state.cash(), symbol()), TEXT_CASH, mouseX, mouseY);
        y = line(graphics, content, y, "gui.athens_coins.atm_ceiling",
                state.walletLimit() <= 0L
                        ? Component.translatable("gui.athens_coins.atm_no_ceiling").getString()
                        : Money.format(state.walletLimit(), symbol()),
                TEXT_BODY, mouseX, mouseY);
        long room = state.walletRoom();
        y = line(graphics, content, y, "gui.athens_coins.atm_room",
                room < 0L ? Component.translatable("gui.athens_coins.atm_no_ceiling").getString()
                        : Money.format(room, symbol()),
                room == 0L ? TEXT_WARN : TEXT_BODY, mouseX, mouseY);
        if (state.commissionDebt() > 0L) {
            y = line(graphics, content, y, "gui.athens_coins.acct_fee_debt",
                    Money.format(state.commissionDebt(), symbol()), TEXT_BAD, mouseX, mouseY);
        }
        drawFitted(graphics, Component.translatable("gui.athens_coins.atm_cash_hint").getString(),
                content.x(), y + 4, content.width(), TEXT_MUTED, mouseX, mouseY);
    }

    private void renderTransfer(GuiGraphics graphics, int mouseX, int mouseY) {
        AtmState state = state();
        ScreenLayout.Rect content = layout.content();
        drawFitted(graphics, Component.translatable("gui.athens_coins.atm_pick_peer").getString(),
                content.x(), content.y() + 2, content.width(), TEXT_MUTED, mouseX, mouseY);
        ScreenLayout.Rect list = peerList();
        if (state.peers().isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.atm_no_peers").getString(),
                    content.x(), list.y() + 2, content.width(), TEXT_WARN, mouseX, mouseY);
            return;
        }
        int rows = ScreenLayout.visibleRows(list, LIST_ROW_H);
        graphics.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        int y = list.y();
        for (int i = peerScroll; i < Math.min(state.peers().size(), peerScroll + rows); i++) {
            AtmState.Peer peer = state.peers().get(i);
            if (i == selectedPeer) {
                graphics.fill(list.x(), y, list.right(), y + LIST_ROW_H - 1, 0x400090FF);
            } else if (list.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + LIST_ROW_H) {
                graphics.fill(list.x(), y, list.right(), y + LIST_ROW_H - 1, 0x28FFFFFF);
            }
            drawFitted(graphics, peer.name(), list.x() + 4, y + 3, list.width() - 8,
                    i == selectedPeer ? TEXT_VALUE : TEXT_BODY, mouseX, mouseY);
            y += LIST_ROW_H;
        }
        graphics.disableScissor();
    }

    private void renderLoan(GuiGraphics graphics, int mouseX, int mouseY) {
        AtmState state = state();
        ScreenLayout.Rect content = layout.content();
        int y = content.y() + 2;
        if (!state.loansEnabled() && !state.hasLoan()) {
            drawFitted(graphics, Component.translatable("message.athens_coins.bank_loans_disabled").getString(),
                    content.x(), y, content.width(), TEXT_WARN, mouseX, mouseY);
            return;
        }
        if (state.hasLoan()) {
            y = line(graphics, content, y, "gui.athens_coins.acct_loan",
                    Money.format(state.loanOwed(), symbol()), TEXT_WARN, mouseX, mouseY);
            y = line(graphics, content, y, "gui.athens_coins.atm_loan_principal",
                    Money.format(state.loanPrincipal(), symbol()), TEXT_BODY, mouseX, mouseY);
            y = line(graphics, content, y, "gui.athens_coins.acct_loan_due",
                    DUE_STAMP.format(Instant.ofEpochMilli(state.loanDueAt())),
                    state.overdueDays() > 0 ? TEXT_BAD : TEXT_BODY, mouseX, mouseY);
            // Say "overdue by N business days" or "N business days left" - never a bare 0.
            y = line(graphics, content, y, "gui.athens_coins.atm_loan_status",
                    state.overdueDays() > 0
                            ? Component.translatable("gui.athens_coins.atm_loan_overdue",
                            state.overdueDays()).getString()
                            : Component.translatable("gui.athens_coins.atm_loan_on_time").getString(),
                    state.overdueDays() > 0 ? TEXT_BAD : TEXT_CASH, mouseX, mouseY);
            y = line(graphics, content, y, "gui.athens_coins.atm_loan_penalty",
                    String.format(java.util.Locale.ROOT, "%.2f%%",
                            state.loanInterestBasisPoints() / 100.0D),
                    TEXT_MUTED, mouseX, mouseY);
            drawFitted(graphics, Component.translatable("gui.athens_coins.atm_repay_hint").getString(),
                    content.x(), y + 4, content.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        // An application already filed: say so instead of offering to file another.
        if (state.pendingRequest() > 0L) {
            y = line(graphics, content, y, "gui.athens_coins.atm_request_pending",
                    Money.format(state.pendingRequest(), symbol()), TEXT_WARN, mouseX, mouseY);
            drawFitted(graphics, Component.translatable("gui.athens_coins.atm_request_wait").getString(),
                    content.x(), y + 4, content.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        y = line(graphics, content, y, "gui.athens_coins.atm_loan_available",
                Money.format(state.loanMax(), symbol()), TEXT_CASH, mouseX, mouseY);
        y = line(graphics, content, y, "gui.athens_coins.atm_loan_term",
                Component.translatable("gui.athens_coins.atm_business_days", state.loanDays()).getString(),
                TEXT_BODY, mouseX, mouseY);
        y = line(graphics, content, y, "gui.athens_coins.atm_loan_penalty",
                String.format(java.util.Locale.ROOT, "%.2f%%", state.loanInterestBasisPoints() / 100.0D),
                TEXT_MUTED, mouseX, mouseY);
        // Says the bank has to agree, so the button is not mistaken for instant money.
        drawFitted(graphics, Component.translatable("gui.athens_coins.atm_apply_hint").getString(),
                content.x(), y + 4, content.width(), TEXT_MUTED, mouseX, mouseY);
    }

    /** One line of feedback, in its own reserved band so it cannot land on the buttons. */
    private void renderMessage(GuiGraphics graphics, int mouseX, int mouseY) {
        if (message == null) {
            return;
        }
        ScreenLayout.Rect row = layout.messageRow();
        drawFitted(graphics, message.getString(), row.x(), row.y(), row.width(),
                messageIsError ? TEXT_BAD : TEXT_CASH, mouseX, mouseY);
    }

    private int line(GuiGraphics graphics, ScreenLayout.Rect area, int y, String labelKey,
                     String value, int color, int mouseX, int mouseY) {
        int labelWidth = Math.max(30, area.width() * 46 / 100);
        drawFitted(graphics, Component.translatable(labelKey).getString(), area.x(), y,
                labelWidth - 4, TEXT_MUTED, mouseX, mouseY);
        drawFitted(graphics, value, area.x() + labelWidth, y,
                Math.max(10, area.width() - labelWidth), color, mouseX, mouseY);
        return y + LINE_H;
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
            return;
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    private ScreenLayout.Rect peerList() {
        return layout.listArea(14);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.TRANSFER) {
            ScreenLayout.Rect list = peerList();
            List<AtmState.Peer> peers = state().peers();
            if (list.contains(mouseX, mouseY) && !peers.isEmpty()) {
                int rows = ScreenLayout.visibleRows(list, LIST_ROW_H);
                int index = peerScroll + (int) ((mouseY - list.y()) / LIST_ROW_H);
                if (index >= 0 && index < Math.min(peers.size(), peerScroll + rows)) {
                    selectedPeer = index;
                    message = null;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.TRANSFER) {
            ScreenLayout.Rect list = peerList();
            int max = Math.max(0, state().peers().size() - ScreenLayout.visibleRows(list, LIST_ROW_H));
            peerScroll = ScreenLayout.clamp(peerScroll - (int) Math.signum(delta), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * Lets the entry boxes keep keystrokes that vanilla would treat as hotkeys.
     *
     * <p>Without this, typing a digit while an {@link net.minecraft.client.gui.components.EditBox} has
     * focus in a container screen closes the screen or swaps a hotbar slot.</p>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape always leaves, even mid-entry, so a focused box cannot trap the player.
        if (keyCode == GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        net.minecraft.client.gui.components.EditBox box = focused();
        if (box != null && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        net.minecraft.client.gui.components.EditBox box = focused();
        if (box != null && box.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Nullable
    private net.minecraft.client.gui.components.EditBox focused() {
        if (amountField != null && amountField.isFocused()) {
            return amountField;
        }
        if (countField != null && countField.isFocused()) {
            return countField;
        }
        return null;
    }
}
