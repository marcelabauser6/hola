package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.menu.AtmMenu;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The ATM screen: exchange Fantastic Coins for Fantastic Cash and back.
 *
 * <p>Layout constants line up with the panel drawn by {@code tools/gen_atm_gui.py}; the title bar,
 * balance card, table bands and footer are all painted into that texture, and this class only
 * places text and widgets inside them.</p>
 */
public class AtmScreen extends AbstractContainerScreen<AtmMenu> {

    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/atm.png");

    private static final int PANEL_W = 248;
    private static final int PANEL_H = 178;

    /** Title bar spans y 4..20 in the texture. */
    private static final int TITLE_Y = 8;

    /** Mode button, vertically centred against the balance card. */
    private static final int MODE_X = 8;
    private static final int MODE_Y = 25;
    private static final int MODE_W = 118;
    private static final int MODE_H = 20;

    /** Balance card spans x 132..240, y 23..47. */
    private static final int CARD_X = 132;
    private static final int CARD_RIGHT = 240;
    private static final int CARD_LABEL_Y = 27;
    private static final int CARD_VALUE_Y = 37;

    /** Header band spans y 54..70; rows are 22px tall bands starting at these offsets. */
    private static final int HEADER_Y = 58;
    private static final int[] ROW_Y = { 74, 98, 122 };
    private static final int ICON_DY = 3;
    private static final int TEXT_DY = 7;
    private static final int BUTTON_DY = 2;

    private static final int ICON_X = 10;
    private static final int NAME_X = 32;
    /** Right edges of the numeric columns, spaced so the two headers do not crowd. */
    private static final int HAVE_RIGHT = 102;
    private static final int PRICE_RIGHT = 150;

    private static final int BUTTON_W = 18;
    private static final int BUTTON_H = 18;
    private static final int[] BUTTON_X = { 160, 181, 202, 223 };

    /** Footer band spans y 152..172, with room for two separated lines. */
    private static final int FOOTER_LINE_1 = 154;
    private static final int FOOTER_LINE_2 = 163;

    private static final int TITLE_COLOR = 0xFFD98F;
    /** Rows for moving cash between the wallet card and the account behind it. */
    private static final int CARD_ROW_Y = 152;
    private static final int COL_HEADER_COLOR = 0x9EC5D8;
    private static final int CARD_LABEL_COLOR = 0x7FA0B4;
    private static final int FOOTER_COLOR = 0x7A96A6;

    private static final String[] AMOUNT_LABELS = { "1", "10", "64", "M" };

    private int mode = AtmMenu.MODE_TO_CASH;

    public AtmScreen(AtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(modeLabel(), button -> {
                    mode = mode == AtmMenu.MODE_TO_CASH ? AtmMenu.MODE_TO_COINS : AtmMenu.MODE_TO_CASH;
                    button.setMessage(modeLabel());
                })
                .bounds(leftPos + MODE_X, topPos + MODE_Y, MODE_W, MODE_H)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.mode_tooltip")))
                .build());

        for (CoinType type : CoinType.ORDERED) {
            int rowY = topPos + ROW_Y[type.ordinal()] + BUTTON_DY;
            for (int amountIndex = 0; amountIndex < AtmMenu.AMOUNTS.length; amountIndex++) {
                final CoinType coin = type;
                final int index = amountIndex;
                addRenderableWidget(Button.builder(
                                Component.literal(AMOUNT_LABELS[amountIndex]),
                                button -> sendAction(coin, index))
                        .bounds(leftPos + BUTTON_X[amountIndex], rowY, BUTTON_W, BUTTON_H)
                        .tooltip(Tooltip.create(amountTooltip(amountIndex)))
                        .build());
            }
        }
        buildTransferButtons();
    }

    private void buildTransferButtons() {
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.atm_to_wallet"),
                        b -> click(AtmMenu.BUTTON_TO_WALLET))
                .bounds(leftPos + 8, topPos + CARD_ROW_Y, 112, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.atm_to_wallet_tip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.atm_to_account"),
                        b -> click(AtmMenu.BUTTON_TO_ACCOUNT))
                .bounds(leftPos + 124, topPos + CARD_ROW_Y, 112, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.atm_to_account_tip")))
                .build());
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private Component modeLabel() {
        return Component.translatable(mode == AtmMenu.MODE_TO_CASH
                ? "gui.athens_coins.mode_to_cash"
                : "gui.athens_coins.mode_to_coins");
    }

    private Component amountTooltip(int amountIndex) {
        int preset = AtmMenu.AMOUNTS[amountIndex];
        return preset < 0
                ? Component.translatable("gui.athens_coins.amount_all")
                : Component.translatable("gui.athens_coins.amount", preset);
    }

    private void sendAction(CoinType type, int amountIndex) {
        if (minecraft == null || minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                AtmMenu.buttonId(mode, type, amountIndex));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, PANEL_W, PANEL_H, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String symbol = "$";
        int accent = 0xFF000000 | menu.themeColor();

        graphics.drawCenteredString(font, title, PANEL_W / 2, TITLE_Y, TITLE_COLOR);

        // Balance card: what is on the card now, and what the ceiling is.
        String label = trimToWidth(Component.translatable("gui.athens_coins.atm_card").getString(),
                CARD_RIGHT - CARD_X - 10);
        graphics.drawString(font, label, CARD_X + 5, CARD_LABEL_Y, CARD_LABEL_COLOR, false);
        String balance = Money.format(menu.cashCents(), symbol)
                + (menu.walletLimit() > 0L ? " / " + Money.format(menu.walletLimit(), symbol) : "");
        graphics.drawString(font, balance, CARD_RIGHT - 5 - font.width(balance),
                CARD_VALUE_Y, 0xFF4CD964, true);

        // Column headers, inside the header band.
        graphics.drawString(font, Component.translatable("gui.athens_coins.column_coin"),
                NAME_X, HEADER_Y, COL_HEADER_COLOR, false);
        drawRight(graphics, Component.translatable("gui.athens_coins.column_have"),
                HAVE_RIGHT, HEADER_Y, COL_HEADER_COLOR);
        drawRight(graphics, Component.translatable("gui.athens_coins.column_price"),
                PRICE_RIGHT, HEADER_Y, COL_HEADER_COLOR);
        graphics.drawCenteredString(font, Component.translatable("gui.athens_coins.column_amount"),
                (BUTTON_X[0] + BUTTON_X[3] + BUTTON_W) / 2, HEADER_Y, COL_HEADER_COLOR);

        // One row per denomination.
        for (CoinType type : CoinType.ORDERED) {
            int y = ROW_Y[type.ordinal()] + TEXT_DY;
            graphics.drawString(font, type.shortName(), NAME_X, y, 0xFFD8C0A0, false);
            drawRight(graphics, Component.literal(Money.compactCount(menu.coinCount(type))),
                    HAVE_RIGHT, y, 0xFFFFFF);
            drawRight(graphics, Component.literal(Money.format(menu.rate(type), symbol)),
                    PRICE_RIGHT, y, 0xFF4CD964);
        }

        // Footer: what the buttons do, and which way the exchange currently runs.
        graphics.drawString(font, Component.translatable("gui.athens_coins.atm_hint"),
                9, FOOTER_LINE_1, FOOTER_COLOR, false);
        graphics.drawString(font, Component.translatable(mode == AtmMenu.MODE_TO_CASH
                        ? "gui.athens_coins.flow_to_cash"
                        : "gui.athens_coins.flow_to_coins"),
                9, FOOTER_LINE_2, FOOTER_COLOR, false);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }

    private void drawRight(GuiGraphics graphics, Component text, int rightX, int y, int color) {
        graphics.drawString(font, text, rightX - font.width(text), y, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        for (CoinType type : CoinType.ORDERED) {
            graphics.renderItem(new ItemStack(type.item()),
                    leftPos + ICON_X, topPos + ROW_Y[type.ordinal()] + ICON_DY);
        }

        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Extra detail when hovering a coin icon. */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        String symbol = "$";
        for (CoinType type : CoinType.ORDERED) {
            int x = leftPos + ICON_X;
            int y = topPos + ROW_Y[type.ordinal()] + ICON_DY;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                int count = menu.coinCount(type);
                long unit = menu.rate(type);
                List<Component> lines = new ArrayList<>();
                lines.add(type.displayName().copy()
                        .withStyle(style -> style.withColor(0xD8C0A0)));
                lines.add(Component.translatable("tooltip.athens_coins.carried",
                                Component.literal(Money.compactCount(count)).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.unit_value",
                                Component.literal(Money.format(unit, symbol)).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.stack_value",
                                Component.literal(Money.format(Money.multiply(unit, count), symbol))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.DARK_GRAY));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
