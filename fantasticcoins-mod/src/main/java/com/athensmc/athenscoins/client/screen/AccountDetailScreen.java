package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.bank.LedgerEntry;
import com.athensmc.athenscoins.bank.Loan;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.network.C2STerminalActionPacket;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenAccountPacket;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;
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
    private int page;
    private EditBox amountBox;
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
        ScreenLayout.Rect panel = ScreenLayout.centeredPanel(width, height, PANEL_W, PANEL_H);
        layout = ScreenLayout.regions(panel, 20, 0, 54);
        columns = ScreenLayout.columns(layout.content().inset(8), 10);
        ScreenLayout.Rect footer = layout.footer().inset(6);
        int gap = 4;
        int cell = ScreenLayout.gridCellWidth(footer, 4, gap);
        int y = footer.y();

        amountBox = new EditBox(font, footer.x(), y, cell, 16,
                Component.translatable("gui.athens_coins.bank_value"));
        amountBox.setValue("0");
        amountBox.setMaxLength(12);
        addRenderableWidget(amountBox);

        addActionButton(footer.x() + cell + gap, y, cell, "gui.athens_coins.acct_lend",
                C2STerminalActionPacket.Action.GRANT_LOAN,
                Tooltip.create(Component.translatable("gui.athens_coins.acct_lend_tip",
                        Money.format(loanMax, "$"), loanDays,
                        String.format(java.util.Locale.ROOT, "%.2f", loanInterest / 100.0D))));
        addActionButton(footer.x() + (cell + gap) * 2, y, cell, "gui.athens_coins.acct_repay",
                C2STerminalActionPacket.Action.REPAY_LOAN,
                Tooltip.create(Component.translatable("gui.athens_coins.acct_repay_tip")));
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.acct_close"),
                        ignored -> act(C2STerminalActionPacket.Action.WITHDRAW_ALL, 0L))
                .bounds(footer.x() + (cell + gap) * 3, y, cell, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.acct_close_tip")))
                .build());
        int backWidth = Math.min(82, footer.width());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.back"),
                        ignored -> minecraft.setScreen(parent))
                .bounds(footer.right() - backWidth, footer.y() + 24, backWidth, 18).build());
    }

    private void addActionButton(int x, int y, int width, String key,
                                 C2STerminalActionPacket.Action action, Tooltip tooltip) {
        addRenderableWidget(Button.builder(Component.translatable(key), ignored -> act(action, amount()))
                .bounds(x, y, width, 16).tooltip(tooltip).build());
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
        ScreenLayout.Rect rowsArea = new ScreenLayout.Rect(area.x(), area.y() + 16,
                area.width(), Math.max(0, area.height() - 30));
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
            int amountWidth = Math.min(area.width() / 2, font.width(amountText));
            int stampWidth = Math.min(58, area.width() / 3);
            drawFitted(graphics, stamp, area.x(), y, stampWidth, 0xFF7A6A5A, mouseX, mouseY);
            String label = Component.translatable(entry.kind().translationKey()).getString();
            drawFitted(graphics, label, area.x() + stampWidth + 3, y,
                    Math.max(8, area.width() - stampWidth - amountWidth - 8), 0xFFC0B0A0, mouseX, mouseY);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ScreenLayout.Rect area = columns.second();
        int rows = Math.max(1, ScreenLayout.visibleRows(
                new ScreenLayout.Rect(area.x(), area.y() + 16, area.width(), Math.max(0, area.height() - 30)), ROW_H));
        int pages = Math.max(1, (ledger.size() + rows - 1) / rows);
        page = ScreenLayout.clamp(page - (int) Math.signum(delta), 0, pages - 1);
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
