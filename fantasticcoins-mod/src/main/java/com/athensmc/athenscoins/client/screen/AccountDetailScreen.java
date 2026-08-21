package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.bank.LedgerEntry;
import com.athensmc.athenscoins.bank.Loan;
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

/**
 * One account in full: balances, terms, any live loan, and the register of movements.
 *
 * <p>Only cash appears here. Physical coins live in inventories and are not the bank's business.</p>
 */
public class AccountDetailScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;
    private static final int ROW_H = 11;
    private static final int LEDGER_ROWS = 9;

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

    private int leftPos;
    private int topPos;
    private int page;
    private EditBox amountBox;

    public AccountDetailScreen(Screen parent, S2COpenAccountPacket packet) {
        super(Component.literal("#" + packet.account().number() + "  "
                + packet.account().ownerName()));
        this.parent = parent;
        this.pos = packet.pos();
        this.account = packet.account();
        this.bankName = packet.bankName();
        this.commissionFee = packet.commissionFee();
        this.commissionDays = packet.commissionDays();
        this.loanMax = packet.loanMax();
        this.loanDays = packet.loanDays();
        this.loanInterest = packet.loanInterest();
        this.reserve = packet.reserve();
        // The account arrives with its newest entries first.
        this.ledger = account.ledger();
    }

    /** Lets a refresh of this screen keep the original back target. */
    public Screen parentScreen() {
        return parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;

        amountBox = new EditBox(font, leftPos + 8, topPos + PANEL_H - 48, 90, 16,
                Component.translatable("gui.athens_coins.bank_value"));
        amountBox.setValue("0");
        amountBox.setMaxLength(12);
        addRenderableWidget(amountBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.acct_lend"),
                        b -> act(C2STerminalActionPacket.Action.GRANT_LOAN, amount()))
                .bounds(leftPos + 102, topPos + PANEL_H - 48, 74, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.acct_lend_tip",
                                Money.format(loanMax, "$"), loanDays,
                                String.format(java.util.Locale.ROOT, "%.2f", loanInterest / 100.0D))))
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.acct_repay"),
                        b -> act(C2STerminalActionPacket.Action.REPAY_LOAN, amount()))
                .bounds(leftPos + 180, topPos + PANEL_H - 48, 74, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.acct_repay_tip")))
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.acct_close"),
                        b -> act(C2STerminalActionPacket.Action.WITHDRAW_ALL, 0L))
                .bounds(leftPos + 258, topPos + PANEL_H - 48, 74, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.acct_close_tip")))
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.back"),
                        b -> minecraft.setScreen(parent))
                .bounds(leftPos + PANEL_W - 90, topPos + PANEL_H - 26, 82, 18)
                .build());
    }

    /** Reads the shared amount box, accepting decimals so cents can be typed. */
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

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, 0xF0140F0C);
        StatsScreen.outline(graphics, leftPos, topPos, PANEL_W, PANEL_H, 0xFFC9A227);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + PANEL_W - 1, topPos + 18, 0xFF3A2A1E);
        graphics.drawString(font, title, leftPos + PANEL_W / 2 - font.width(title) / 2,
                topPos + 6, 0xFFFFD98F, true);

        int y = topPos + 24;
        line(graphics, y, "gui.athens_coins.acct_bank", bankName, 0xFFD8C0A0);
        line(graphics, y += ROW_H, "gui.athens_coins.acct_opened",
                STAMP.format(Instant.ofEpochMilli(account.openedAt())), 0xFFB0A090);
        line(graphics, y += ROW_H, "gui.athens_coins.acct_balance",
                Money.format(account.balance(), "$"), 0xFF6BE06B);
        line(graphics, y += ROW_H, "gui.athens_coins.acct_fee",
                Money.format(commissionFee, "$") + " / " + commissionDays + "d", 0xFFB0A090);
        if (account.commissionDebt() > 0L) {
            line(graphics, y += ROW_H, "gui.athens_coins.acct_fee_debt",
                    Money.format(account.commissionDebt(), "$"), 0xFFE06B6B);
        }

        Loan loan = account.loan();
        if (loan != null && !loan.settled()) {
            long now = System.currentTimeMillis();
            int overdue = BankRules.overdueBusinessDays(loan.dueAt(), now);
            line(graphics, y += ROW_H, "gui.athens_coins.acct_loan",
                    Money.format(loan.owed(), "$"), 0xFFE0C060);
            String due = STAMP.format(Instant.ofEpochMilli(loan.dueAt()));
            line(graphics, y += ROW_H, "gui.athens_coins.acct_loan_due",
                    overdue > 0
                            ? Component.translatable("gui.athens_coins.acct_overdue", overdue).getString()
                            : due,
                    overdue > 0 ? 0xFFE06B6B : 0xFFB0A090);
        } else {
            line(graphics, y += ROW_H, "gui.athens_coins.acct_loan",
                    Component.translatable("gui.athens_coins.acct_no_loan").getString(), 0xFF888888);
        }
        line(graphics, y += ROW_H, "gui.athens_coins.acct_reserve",
                Money.format(reserve, "$"), 0xFF9EC5D8);

        // ---- register
        int ledgerTop = topPos + 24;
        graphics.drawString(font, Component.translatable("gui.athens_coins.acct_ledger"),
                leftPos + 176, ledgerTop, 0xFFD8B48C, false);
        graphics.fill(leftPos + 176, ledgerTop + 10, leftPos + PANEL_W - 8, ledgerTop + 11, 0x40FFFFFF);

        int rowY = ledgerTop + 14;
        int from = page * LEDGER_ROWS;
        if (ledger.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.athens_coins.acct_no_moves"),
                    leftPos + 176, rowY, 0xFF888888, false);
        }
        for (int i = from; i < Math.min(ledger.size(), from + LEDGER_ROWS); i++) {
            LedgerEntry entry = ledger.get(i);
            graphics.drawString(font, STAMP.format(Instant.ofEpochMilli(entry.at())),
                    leftPos + 176, rowY, 0xFF7A6A5A, false);
            String label = Component.translatable(entry.kind().translationKey()).getString();
            graphics.drawString(font, font.plainSubstrByWidth(label, 74),
                    leftPos + 232, rowY, 0xFFC0B0A0, false);
            if (entry.amount() != 0L) {
                String amount = (entry.amount() > 0 ? "+" : "")
                        + Money.format(entry.amount(), "$");
                graphics.drawString(font, amount, leftPos + PANEL_W - 10 - font.width(amount), rowY,
                        entry.amount() > 0 ? 0xFF6BE06B : 0xFFE0956B, false);
            }
            rowY += ROW_H;
        }

        int pages = Math.max(1, (ledger.size() + LEDGER_ROWS - 1) / LEDGER_ROWS);
        String pager = (page + 1) + " / " + pages;
        graphics.drawString(font, pager, leftPos + 176, topPos + PANEL_H - 62, 0xFF8A7A6A, false);
        graphics.drawString(font, Component.translatable("gui.athens_coins.acct_scroll"),
                leftPos + 210, topPos + PANEL_H - 62, 0xFF6A5A4A, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void line(GuiGraphics graphics, int y, String labelKey, String value, int color) {
        graphics.drawString(font, Component.translatable(labelKey), leftPos + 8, y, 0xFF8A7A6A, false);
        graphics.drawString(font, value, leftPos + 96, y, color, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int pages = Math.max(1, (ledger.size() + LEDGER_ROWS - 1) / LEDGER_ROWS);
        page = Math.max(0, Math.min(pages - 1, page - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
