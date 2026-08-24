package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.bank.LedgerEntry;
import com.athensmc.athenscoins.bank.Loan;
import com.athensmc.athenscoins.client.layout.PanelMetrics;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.widget.AmountField;
import com.athensmc.athenscoins.network.C2STerminalActionPacket;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenAccountPacket;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Responsive account summary and paginated ledger. */
public class AccountDetailScreen extends Screen {
    private static final int ROW_H = 11;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final BlockPos pos;
    private final BankAccount account;
    private final String bankName;
    private final long commissionFee;
    private final int commissionDays;
    private final long loanMax;
    private final int loanDays;
    private final int loanInterest;
    private final long reserve;
    private final List<LedgerEntry> ledger;

    private ScreenLayout.Regions layout;
    private ScreenLayout.Columns columns;
    private ScreenLayout.Rect messageRow;
    private int page;
    private AmountField amountBox;
    private Component message;
    private Component hoverTooltip;

    public AccountDetailScreen(Screen parent, S2COpenAccountPacket packet) {
        super(Component.literal("#" + packet.account().number() + " · " + packet.account().ownerName()));
        this.parent = parent;
        pos = packet.pos();
        account = packet.account();
        bankName = packet.bankName();
        commissionFee = packet.commissionFee();
        commissionDays = packet.commissionDays();
        loanMax = packet.loanMax();
        loanDays = packet.loanDays();
        loanInterest = packet.loanInterest();
        reserve = packet.reserve();
        ledger = account.ledger();
    }

    public Screen parentScreen() {
        return parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // The footer carries three stacked rows: the action row, one line of feedback, and Back.
        layout = PanelMetrics.account(width, height);
        columns = ScreenLayout.columns(layout.content().inset(8), 10);
        ScreenLayout.Rect footer = layout.footer().inset(6);
        int gap = 4;
        int cell = ScreenLayout.gridCellWidth(footer, 4, gap);
        int y = footer.y();

        amountBox = new AmountField(font, footer.x(), y, cell, 16,
                Component.translatable("gui.athens_coins.bank_value"));
        addRenderableWidget(amountBox);

        addActionButton(footer.x() + cell + gap, y, cell, "gui.athens_coins.acct_lend",
                C2STerminalActionPacket.Action.GRANT_LOAN,
                Tooltip.create(Component.translatable("gui.athens_coins.acct_lend_tip",
                        Money.format(loanMax, "$"), loanDays,
                        String.format(java.util.Locale.ROOT, "%.2f", loanInterest / 100.0D))));
        addActionButton(footer.x() + (cell + gap) * 2, y, cell, "gui.athens_coins.acct_repay",
                C2STerminalActionPacket.Action.REPAY_LOAN,
                Tooltip.create(Component.translatable("gui.athens_coins.acct_repay_tip")));
        // Closing an account ignores the amount box on purpose: it liquidates the whole balance.
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.acct_close"),
                        ignored -> act(C2STerminalActionPacket.Action.WITHDRAW_ALL, 0L))
                .bounds(footer.x() + (cell + gap) * 3, y, cell, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.acct_close_tip")))
                .build());

        messageRow = new ScreenLayout.Rect(footer.x(), y + 19, footer.width(), 10);
        int backWidth = Math.min(82, footer.width());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.back"),
                        ignored -> minecraft.setScreen(parent))
                .bounds(footer.right() - backWidth, messageRow.bottom() + 3, backWidth, 18).build());
    }

    private void addActionButton(int x, int y, int width, String key,
                                 C2STerminalActionPacket.Action action, Tooltip tooltip) {
        addRenderableWidget(Button.builder(Component.translatable(key), ignored -> submit(action))
                .bounds(x, y, width, 16).tooltip(tooltip).build());
    }

    /**
     * Sends only when the box holds a real amount.
     *
     * <p>This box used to interpret an entry without a separator as <em>cents</em> and one with a
     * separator as <em>units</em>, so a banker lending "500" moved five dollars while "500.00" moved
     * five hundred. Worse, anything unparseable became {@code 0} and was sent anyway: the server
     * performed a zero-value action and nobody was told. Now the amount means what it reads, and a
     * refusal is shown instead of transmitted.</p>
     */
    private void submit(C2STerminalActionPacket.Action action) {
        long cents = amountBox.cents();
        if (cents < 0L) {
            message = amountBox.error();
            return;
        }
        act(action, cents);
        amountBox.clear();
        message = null;
    }

    private void act(C2STerminalActionPacket.Action action, long value) {
        ModNetwork.toServer(new C2STerminalActionPacket(pos, action, null,
                account.number(), value, ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hoverTooltip = null;
        ScreenLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF0140F0C);
        StatsScreen.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 0xFFC9A227);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, layout.header().bottom() - 2, 0xFF3A2A1E);
        drawFitted(graphics, title.getString(), panel.x() + 8, panel.y() + 6,
                panel.width() - 16, 0xFFFFD98F, mouseX, mouseY);

        renderSummary(graphics, mouseX, mouseY);
        renderLedger(graphics, mouseX, mouseY);
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
        ScreenLayout.Rect area = columns.first();
        int y = area.y() + 2;
        y = line(graphics, area, y, "gui.athens_coins.acct_bank", bankName, 0xFFD8C0A0, mouseX, mouseY);
        y = line(graphics, area, y, "gui.athens_coins.acct_opened",
                STAMP.format(Instant.ofEpochMilli(account.openedAt())), 0xFFB0A090, mouseX, mouseY);
        y = line(graphics, area, y, "gui.athens_coins.acct_balance",
                Money.format(account.balance(), "$"), 0xFF6BE06B, mouseX, mouseY);
        y = line(graphics, area, y, "gui.athens_coins.acct_fee",
                Money.format(commissionFee, "$") + " / " + commissionDays + "d", 0xFFB0A090, mouseX, mouseY);
        if (account.commissionDebt() > 0L) {
            y = line(graphics, area, y, "gui.athens_coins.acct_fee_debt",
                    Money.format(account.commissionDebt(), "$"), 0xFFE06B6B, mouseX, mouseY);
        }
        Loan loan = account.loan();
        if (loan != null && !loan.settled()) {
            int overdue = BankRules.overdueBusinessDays(loan.dueAt(), System.currentTimeMillis());
            y = line(graphics, area, y, "gui.athens_coins.acct_loan",
                    Money.format(loan.owed(), "$"), 0xFFE0C060, mouseX, mouseY);
            String due = overdue > 0
                    ? Component.translatable("gui.athens_coins.acct_overdue", overdue).getString()
                    : STAMP.format(Instant.ofEpochMilli(loan.dueAt()));
            y = line(graphics, area, y, "gui.athens_coins.acct_loan_due", due,
                    overdue > 0 ? 0xFFE06B6B : 0xFFB0A090, mouseX, mouseY);
        } else {
            y = line(graphics, area, y, "gui.athens_coins.acct_loan",
                    Component.translatable("gui.athens_coins.acct_no_loan").getString(),
                    0xFF888888, mouseX, mouseY);
        }
        line(graphics, area, y, "gui.athens_coins.acct_reserve",
                Money.format(reserve, "$"), 0xFF9EC5D8, mouseX, mouseY);
    }

    private int line(GuiGraphics graphics, ScreenLayout.Rect area, int y, String labelKey,
                     String value, int color, int mouseX, int mouseY) {
        int labelWidth = Math.max(38, area.width() * 48 / 100);
        String label = Component.translatable(labelKey).getString();
        drawFitted(graphics, label, area.x(), y, labelWidth - 3, 0xFF8A7A6A, mouseX, mouseY);
        drawFitted(graphics, value, area.x() + labelWidth, y,
                area.width() - labelWidth, color, mouseX, mouseY);
        return y + ROW_H;
    }

    private void renderLedger(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect area = columns.second();
        drawFitted(graphics, Component.translatable("gui.athens_coins.acct_ledger").getString(),
                area.x(), area.y() + 2, area.width(), 0xFFD8B48C, mouseX, mouseY);
        graphics.fill(area.x(), area.y() + 12, area.right(), area.y() + 13, 0x40FFFFFF);
        ScreenLayout.Rect rowsArea = ledgerRows();
        int rows = Math.max(1, ScreenLayout.visibleRows(rowsArea, ROW_H));
        int pages = Math.max(1, (ledger.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int from = page * rows;
        int y = rowsArea.y();
        if (ledger.isEmpty()) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.acct_no_moves").getString(),
                    area.x(), y, area.width(), 0xFF888888, mouseX, mouseY);
        }
        graphics.enableScissor(rowsArea.x(), rowsArea.y(), rowsArea.right(), rowsArea.bottom());
        for (int i = from; i < Math.min(ledger.size(), from + rows); i++) {
            LedgerEntry entry = ledger.get(i);
            String stamp = STAMP.format(Instant.ofEpochMilli(entry.at()));
            String amountText = entry.amount() == 0L ? "" : (entry.amount() > 0 ? "+" : "")
                    + Money.format(entry.amount(), "$");
            // Budgets are carved from the row so they sum to its width. The old code floored the
            // label at 8px, which let it run under the right-aligned amount on a narrow column.
            int stampWidth = Math.min(58, area.width() / 3);
            int remaining = Math.max(0, area.width() - stampWidth - 6);
            int amountWidth = Math.min(remaining / 2, font.width(amountText));
            int labelWidth = Math.max(0, remaining - amountWidth);
            drawFitted(graphics, stamp, area.x(), y, stampWidth, 0xFF7A6A5A, mouseX, mouseY);
            String label = Component.translatable(entry.kind().translationKey()).getString();
            drawFitted(graphics, label, area.x() + stampWidth + 3, y,
                    labelWidth, 0xFFC0B0A0, mouseX, mouseY);
            if (!amountText.isEmpty()) {
                String fittedAmount = ScreenText.fit(font, amountText, amountWidth);
                graphics.drawString(font, fittedAmount, area.right() - font.width(fittedAmount), y,
                        entry.amount() > 0 ? 0xFF6BE06B : 0xFFE0956B, false);
                if (ScreenText.wasTruncated(font, amountText, amountWidth)
                        && rowsArea.contains(mouseX, mouseY)
                        && mouseX >= area.right() - amountWidth && mouseX < area.right()
                        && mouseY >= y && mouseY < y + ROW_H) {
                    hoverTooltip = Component.literal(amountText);
                }
            }
            y += ROW_H;
        }
        graphics.disableScissor();
        String pager = (page + 1) + " / " + pages + " · "
                + Component.translatable("gui.athens_coins.acct_scroll").getString();
        drawFitted(graphics, pager, area.x(), area.bottom() - 10, area.width(),
                0xFF8A7A6A, mouseX, mouseY);
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

    /**
     * The ledger's scrollable band.
     *
     * <p>Drawing and scrolling used to each rebuild this rectangle from the same two magic offsets,
     * so a change to one silently disagreed with the other and the page count stopped matching what
     * was on screen. One definition, two callers.</p>
     */
    private ScreenLayout.Rect ledgerRows() {
        ScreenLayout.Rect area = columns.second();
        return new ScreenLayout.Rect(area.x(), area.y() + 16, area.width(),
                Math.max(0, area.height() - 30));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int rows = Math.max(1, ScreenLayout.visibleRows(ledgerRows(), ROW_H));
        int pages = Math.max(1, (ledger.size() + rows - 1) / rows);
        page = ScreenLayout.clamp(page - (int) Math.signum(delta), 0, pages - 1);
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
