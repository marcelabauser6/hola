package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.client.layout.ScreenLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * "Are you sure?", drawn over the screen that asked.
 *
 * <p>This replaces a chat prompt. Handing over banker access, closing an account and deciding a loan all
 * used to send the actor a question in chat with two clickable words in it, which had three problems: the
 * question appeared several lines away from the click that caused it, three clicks in a row left three
 * near-identical prompts stacked up with no way to tell which was which, and the whole thing was invisible
 * to anyone who had chat closed. A dialog over the tab you were working in has none of those.</p>
 *
 * <p><b>Modal by construction.</b> The owning screen asks {@link #consumesInput()} before it does anything
 * with a click or a key, so while a question is up nothing else in the screen can be pressed. That matters
 * more than it looks: the row that opened the dialog is still on screen underneath it, and without the
 * guard a second click would queue a second action behind the first.</p>
 *
 * <p>Not a widget. Vanilla widgets are laid out and hit-tested by the screen's own list, so an overlay
 * built from them would be clicked <em>through</em> by whatever was registered after it.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ConfirmOverlay {

    private static final int WIDTH = 220;
    private static final int PAD = 8;
    private static final int BUTTON_H = 18;
    private static final int LINE_H = 10;

    private static final int SCRIM = 0xC0000000;
    private static final int PANEL = 0xFF1B1410;
    private static final int BORDER = 0xFFC9A227;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DETAIL = 0xFFB0A090;
    private static final int CONFIRM = 0xFF2E5E36;
    private static final int CONFIRM_HOVER = 0xFF3F7F49;
    private static final int CANCEL = 0xFF5E2E2E;
    private static final int CANCEL_HOVER = 0xFF7F3F3F;

    @Nullable
    private Component question;
    @Nullable
    private Component detail;
    @Nullable
    private Runnable onConfirm;

    private ScreenLayout.Rect confirmButton = new ScreenLayout.Rect(0, 0, 0, 0);
    private ScreenLayout.Rect cancelButton = new ScreenLayout.Rect(0, 0, 0, 0);

    /**
     * Puts a question up.
     *
     * @param detail the consequence, on its own line - "the balance is paid out on a card", "the money
     *               leaves the reserve now". The question names what is about to happen; this says what it
     *               costs, which is the part a person about to click Confirm actually needs.
     */
    public void ask(Component question, @Nullable Component detail, Runnable onConfirm) {
        this.question = question;
        this.detail = detail;
        this.onConfirm = onConfirm;
    }

    public void cancel() {
        question = null;
        detail = null;
        onConfirm = null;
    }

    public boolean consumesInput() {
        return question != null;
    }

    /** Draws the dialog centred on the given panel. Call last, so it sits over the widgets. */
    public void render(GuiGraphics graphics, Font font, ScreenLayout.Rect panel,
                       int mouseX, int mouseY) {
        if (question == null) {
            return;
        }
        int width = Math.min(WIDTH, Math.max(120, panel.width() - 40));
        int textWidth = width - PAD * 2;
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(question, textWidth);
        List<net.minecraft.util.FormattedCharSequence> detailLines =
                detail == null ? List.of() : font.split(detail, textWidth);
        int height = PAD + lines.size() * LINE_H
                + (detailLines.isEmpty() ? 0 : 4 + detailLines.size() * LINE_H)
                + 6 + BUTTON_H + PAD;
        int x = panel.x() + (panel.width() - width) / 2;
        int y = panel.y() + (panel.height() - height) / 2;

        // Dim the whole panel, so it reads as "nothing else is live right now".
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), SCRIM);
        graphics.fill(x, y, x + width, y + height, PANEL);
        Panels.outline(graphics, x, y, width, height, BORDER);

        int textY = y + PAD;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x + PAD, textY, TEXT, false);
            textY += LINE_H;
        }
        if (!detailLines.isEmpty()) {
            textY += 4;
            for (net.minecraft.util.FormattedCharSequence line : detailLines) {
                graphics.drawString(font, line, x + PAD, textY, TEXT_DETAIL, false);
                textY += LINE_H;
            }
        }

        int gap = 6;
        int cell = (width - PAD * 2 - gap) / 2;
        int buttonY = y + height - PAD - BUTTON_H;
        confirmButton = new ScreenLayout.Rect(x + PAD, buttonY, cell, BUTTON_H);
        cancelButton = new ScreenLayout.Rect(x + PAD + cell + gap, buttonY, cell, BUTTON_H);
        drawButton(graphics, font, confirmButton, Component.translatable("gui.athens_coins.confirm"),
                CONFIRM, CONFIRM_HOVER, mouseX, mouseY);
        drawButton(graphics, font, cancelButton, Component.translatable("gui.athens_coins.cancel"),
                CANCEL, CANCEL_HOVER, mouseX, mouseY);
    }

    private void drawButton(GuiGraphics graphics, Font font, ScreenLayout.Rect area,
                            Component label, int base, int hover, int mouseX, int mouseY) {
        boolean over = area.contains(mouseX, mouseY);
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), over ? hover : base);
        Panels.outline(graphics, area.x(), area.y(), area.width(), area.height(), 0xFF000000);
        String text = label.getString();
        graphics.drawString(font, text, area.x() + (area.width() - font.width(text)) / 2,
                area.y() + (area.height() - 8) / 2, TEXT, false);
    }

    /**
     * @return true when the click belonged to the dialog, which is always while one is up
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (question == null) {
            return false;
        }
        if (confirmButton.contains(mouseX, mouseY)) {
            Runnable action = onConfirm;
            // Cleared before running, so an action that opens another dialog is not immediately closed
            // again by its own confirmation.
            cancel();
            if (action != null) {
                action.run();
            }
            return true;
        }
        if (cancelButton.contains(mouseX, mouseY)) {
            cancel();
            return true;
        }
        // Anywhere else: swallowed. A click outside the dialog must not reach the row underneath it.
        return true;
    }
}
