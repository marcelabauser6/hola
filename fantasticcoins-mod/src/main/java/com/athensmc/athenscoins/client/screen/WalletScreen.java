package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.item.ModItems;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The wallet.
 *
 * <p>A plain {@link Screen}, not a container screen: the wallet holds no slots and only reports
 * figures, so it deliberately has no menu, no container id and no interaction with the player's
 * inventory. Opening it cannot disturb anything the player is carrying.</p>
 *
 * <p>All artwork is shipped under the mod's own namespace, so no vanilla model, font or texture
 * is overridden.</p>
 */
public class WalletScreen extends Screen {

    /** The artwork lives inside a 256x256 sheet at this offset and size. */
    private static final int ART_U = 40;
    private static final int ART_V = 86;
    private static final int ART_W = 176;
    private static final int ART_H = 76;

    /** Footer bar drawn under the artwork, carrying the cash balance. */
    private static final int FOOTER_Y = ART_H + 2;
    private static final int FOOTER_H = 20;
    private static final int TOTAL_H = FOOTER_Y + FOOTER_H;

    /** The "USER" panel the player's face fills. */
    private static final int FACE_X = 18;
    private static final int FACE_Y = 26;
    private static final int FACE_SIZE = 32;

    /** The six 16x16 cells of the "INFO" grid. */
    private static final int[][] CELLS = {
            { 80, 36 }, { 98, 36 }, { 116, 36 },
            { 80, 53 }, { 98, 53 }, { 116, 53 },
    };

    /** Per-theme {frame, fill, highlight, label} colours sampled from the wallet artwork. */
    private static final int[][] THEME_COLORS = {
            { 0x3C1D23, 0x633A3A, 0x835656, 0xC9AFAF },
            { 0x171516, 0x282C3C, 0x434859, 0x9AA2BC },
            { 0x792F14, 0xC98A53, 0xF6BFA0, 0x5E2A10 },
    };

    private static final ResourceLocation ICON_BALANCE =
            new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/icons/balance.png");
    private static final ResourceLocation ICON_DOCUMENTS =
            new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/icons/documents.png");

    private final WalletSnapshot snapshot;
    private int theme;
    private int leftPos;
    private int topPos;
    private String footerTooltip;

    public WalletScreen(WalletSnapshot snapshot) {
        super(Component.translatable("gui.athens_coins.wallet"));
        this.snapshot = snapshot;
        this.theme = snapshot.display().walletTheme();
    }

    @Override
    protected void init() {
        ScreenLayout.Rect panel = ScreenLayout.centeredPanel(width, height, ART_W, TOTAL_H);
        this.leftPos = panel.x();
        this.topPos = panel.y();
    }

    /** Keep the world running underneath, like any inventory-style screen. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ResourceLocation walletTexture() {
        return new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/wallet_" + theme + ".png");
    }

    private int themeColor(int index) {
        return THEME_COLORS[Math.max(0, Math.min(2, theme - 1))][index];
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.blit(walletTexture(), leftPos, topPos, ART_U, ART_V, ART_W, ART_H);
        renderFooter(graphics);
        renderFace(graphics);
        renderCells(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCellTooltip(graphics, mouseX, mouseY);
    }

    private void renderFooter(GuiGraphics graphics) {
        DisplaySettings display = snapshot.display();
        int x0 = leftPos;
        int y0 = topPos + FOOTER_Y;
        int x1 = leftPos + ART_W;
        int y1 = y0 + FOOTER_H;

        graphics.fill(x0, y0, x1, y1, 0xFF000000 | themeColor(0));
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF000000 | themeColor(1));
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0xFF000000 | themeColor(2));

        int textY = y0 + (FOOTER_H - 8) / 2;
        String fullName = display.currencyName();
        String fullAmount = snapshot.bank().hasAccount()
                ? Money.format(snapshot.cashCents(), display.currencySymbol())
                : Component.translatable("gui.athens_coins.no_bank_short").getString();
        int innerWidth = ART_W - 12;
        int amountBudget = Math.min(innerWidth * 3 / 5, Math.max(42, font.width(fullAmount)));
        int nameBudget = Math.max(24, innerWidth - amountBudget - 6);
        String name = ScreenText.fit(font, fullName, nameBudget);
        String amount = ScreenText.fit(font, fullAmount, amountBudget);
        footerTooltip = ScreenText.wasTruncated(font, fullName, nameBudget)
                || ScreenText.wasTruncated(font, fullAmount, amountBudget)
                ? fullName + ": " + fullAmount : null;
        graphics.drawString(font, name, x0 + 6, textY, themeColor(3), false);
        graphics.drawString(font, amount, x1 - 6 - font.width(amount), textY, display.cashColor(), true);
    }

    private void renderFace(GuiGraphics graphics) {
        ResourceLocation skin = resolveSkin();
        int x = leftPos + FACE_X;
        int y = topPos + FACE_Y;
        RenderSystem.enableBlend();
        // Base head (u=8,v=8) then the hat overlay (u=40,v=8) of the 64x64 skin.
        graphics.blit(skin, x, y, FACE_SIZE, FACE_SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(skin, x, y, FACE_SIZE, FACE_SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private ResourceLocation resolveSkin() {
        if (minecraft != null && minecraft.level != null) {
            Player owner = minecraft.level.getPlayerByUUID(snapshot.ownerId());
            if (owner instanceof AbstractClientPlayer clientPlayer) {
                return clientPlayer.getSkinTextureLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(snapshot.ownerId());
    }

    private void renderCells(GuiGraphics graphics) {
        // Row 1: the coins actually carried in the inventory.
        for (CoinType type : CoinType.ORDERED) {
            int[] cell = CELLS[type.ordinal()];
            int x = leftPos + cell[0];
            int y = topPos + cell[1];
            graphics.renderItem(new ItemStack(type.item()), x, y);
            drawCount(graphics, Money.compactCount(snapshot.coinCount(type)), x, y);
        }

        // Row 2: cash balance, account details, bank.
        graphics.blit(ICON_BALANCE, leftPos + CELLS[3][0], topPos + CELLS[3][1],
                0.0F, 0.0F, 16, 16, 16, 16);
        graphics.blit(ICON_DOCUMENTS, leftPos + CELLS[4][0], topPos + CELLS[4][1],
                0.0F, 0.0F, 16, 16, 16, 16);
        graphics.renderItem(new ItemStack(ModItems.ATM_ITEM.get()),
                leftPos + CELLS[5][0], topPos + CELLS[5][1]);
    }

    private void drawCount(GuiGraphics graphics, String text, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.drawString(font, text, x + 17 - font.width(text), y + 9, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    // ------------------------------------------------------------------ tooltips

    private void renderCellTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int hovered = cellAt(mouseX, mouseY);
        if (hovered >= 0) {
            graphics.renderComponentTooltip(font, tooltipFor(hovered), mouseX, mouseY);
            return;
        }
        if (footerTooltip != null && mouseX >= leftPos && mouseX < leftPos + ART_W
                && mouseY >= topPos + FOOTER_Y && mouseY < topPos + TOTAL_H) {
            graphics.renderTooltip(font, Component.literal(footerTooltip), mouseX, mouseY);
        }
    }

    private int cellAt(int mouseX, int mouseY) {
        for (int i = 0; i < CELLS.length; i++) {
            int x = leftPos + CELLS[i][0];
            int y = topPos + CELLS[i][1];
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return i;
            }
        }
        return -1;
    }

    private List<Component> tooltipFor(int index) {
        DisplaySettings display = snapshot.display();
        String symbol = display.currencySymbol();
        List<Component> lines = new ArrayList<>();

        if (index < 3) {
            CoinType type = CoinType.ORDERED[index];
            int count = snapshot.coinCount(type);
            long unit = display.valueOf(type);
            lines.add(type.displayName().copy()
                    .withStyle(style -> style.withColor(display.colorOf(type))));
            lines.add(Component.translatable("tooltip.athens_coins.carried",
                            Component.literal(Money.compactCount(count)).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
            // Coin values in cash only mean something once a bank is quoting you a rate.
            if (snapshot.bank().hasAccount()) {
                lines.add(Component.translatable("tooltip.athens_coins.unit_value",
                                Component.literal(Money.format(unit, symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.stack_value",
                                Component.literal(Money.format(Money.multiply(unit, count), symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("tooltip.athens_coins.no_rates")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            return lines;
        }

        switch (index) {
            case 3 -> {
                lines.add(Component.literal(display.currencyName())
                        .withStyle(style -> style.withColor(display.cashColor())));
                lines.add(Component.translatable("tooltip.athens_coins.cash_balance",
                                Component.literal(Money.format(snapshot.cashCents(), symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.coins_value",
                                Component.literal(Money.format(snapshot.coinsValueCents(), symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.net_worth",
                                Component.literal(Money.format(snapshot.netWorthCents(), symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.YELLOW));
            }
            case 4 -> {
                WalletSnapshot.BankInfo info = snapshot.bank();
                if (!info.hasAccount()) {
                    lines.add(Component.translatable("tooltip.athens_coins.bank_none")
                            .withStyle(ChatFormatting.RED));
                    lines.add(Component.translatable("tooltip.athens_coins.bank_none_how")
                            .withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("tooltip.athens_coins.bank_none_note")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                lines.add(Component.translatable("tooltip.athens_coins.my_bank",
                                Component.literal(info.name()).withStyle(ChatFormatting.WHITE))
                        .withStyle(style -> style.withColor(info.themeColor())));
                lines.add(Component.translatable("tooltip.athens_coins.bank_account_no",
                                Component.literal("#" + info.accountNumber())
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.bank_held",
                                Component.literal(Money.format(info.accountCents(), symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("tooltip.athens_coins.bank_wallet_cap",
                                Component.literal(info.walletLimit() <= 0L
                                        ? "\u221e"
                                        : Money.format(info.walletLimit(), symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.bank_fee_line",
                                Component.literal(Money.format(info.commissionFee(), symbol))
                                        .withStyle(ChatFormatting.WHITE),
                                info.commissionDays())
                        .withStyle(ChatFormatting.GRAY));
                if (info.loanOwed() > 0L) {
                    long daysLeft = Math.max(0L,
                            (info.loanDueAt() - System.currentTimeMillis()) / 86_400_000L);
                    lines.add(Component.translatable("tooltip.athens_coins.bank_loan_line",
                                    Component.literal(Money.format(info.loanOwed(), symbol))
                                            .withStyle(ChatFormatting.WHITE), daysLeft)
                            .withStyle(ChatFormatting.RED));
                }
                lines.add(Component.translatable("tooltip.athens_coins.account_theme")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            default -> {
                lines.add(Component.translatable("tooltip.athens_coins.bank")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("tooltip.athens_coins.bank_how")
                        .withStyle(ChatFormatting.GRAY));
                lines.add(snapshot.atmNearby()
                        ? Component.translatable("tooltip.athens_coins.bank_near")
                        .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("tooltip.athens_coins.bank_far")
                                .withStyle(ChatFormatting.RED));
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking the wallet body (but not a cell) flips through the three colour schemes.
        boolean insideWallet = mouseX >= leftPos && mouseX < leftPos + ART_W
                && mouseY >= topPos && mouseY < topPos + ART_H;
        if (insideWallet && cellAt((int) mouseX, (int) mouseY) < 0) {
            theme = theme % 3 + 1;
            if (minecraft != null) {
                minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
