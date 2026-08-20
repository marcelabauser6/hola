package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.menu.AtmMenu;
import com.athensmc.athenscoins.wallet.CoinFormat;
import com.athensmc.athenscoins.wallet.CoinType;
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
 * The ATM screen: three denominations, four amount presets, one direction toggle.
 */
public class AtmScreen extends AbstractContainerScreen<AtmMenu> {

    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(AthensCoinsMod.MOD_ID, "textures/gui/atm.png");

    private static final int PANEL_W = 248;
    private static final int PANEL_H = 150;

    private static final int ROW_START_Y = 54;
    private static final int ROW_PITCH = 20;
    private static final int BUTTON_W = 20;
    private static final int BUTTON_H = 16;
    private static final int[] BUTTON_X = { 156, 178, 200, 222 };

    /** Right edges of the two numeric columns. */
    private static final int CASH_RIGHT = 116;
    private static final int DIGITAL_RIGHT = 152;

    private static final String[] AMOUNT_LABELS = { "1", "10", "64", "M" };

    private int mode = AtmMenu.MODE_DEPOSIT;

    public AtmScreen(AtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        addRenderableWidget(Button.builder(modeLabel(), button -> {
                    mode = mode == AtmMenu.MODE_DEPOSIT ? AtmMenu.MODE_WITHDRAW : AtmMenu.MODE_DEPOSIT;
                    button.setMessage(modeLabel());
                })
                .bounds(leftPos + 8, topPos + 20, 116, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.mode_tooltip")))
                .build());

        for (CoinType type : CoinType.ORDERED) {
            int rowY = topPos + ROW_START_Y + type.ordinal() * ROW_PITCH;
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
    }

    private Component modeLabel() {
        return Component.translatable(mode == AtmMenu.MODE_DEPOSIT
                ? "gui.athens_coins.mode_deposit"
                : "gui.athens_coins.mode_withdraw");
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
        int id = AtmMenu.buttonId(mode, type, amountIndex);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, PANEL_W, PANEL_H, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, title, PANEL_W / 2, 7, 0xFFE9B8);

        // Column headers
        graphics.drawString(font, Component.translatable("gui.athens_coins.column_coin"),
                26, 42, 0xB8A0A0, false);
        drawRight(graphics, Component.translatable("gui.athens_coins.column_cash"),
                CASH_RIGHT, 42, 0xB8A0A0);
        drawRight(graphics, Component.translatable("gui.athens_coins.column_digital"),
                DIGITAL_RIGHT, 42, 0xB8A0A0);
        graphics.drawCenteredString(font, Component.translatable("gui.athens_coins.column_amount"),
                (BUTTON_X[0] + BUTTON_X[3] + BUTTON_W) / 2, 42, 0xB8A0A0);

        for (CoinType type : CoinType.ORDERED) {
            int y = ROW_START_Y + type.ordinal() * ROW_PITCH + 4;
            graphics.drawString(font, type.shortName(), 26, y, type.color(), false);
            drawRight(graphics, Component.literal(CoinFormat.compact(menu.cash(type))),
                    CASH_RIGHT, y, 0xFFFFFF);
            drawRight(graphics, Component.literal(CoinFormat.compact(menu.digital(type))),
                    DIGITAL_RIGHT, y, 0x8FE3FF);
        }

        // Direction indicator next to the mode button.
        graphics.drawString(font, Component.translatable(mode == AtmMenu.MODE_DEPOSIT
                        ? "gui.athens_coins.flow_deposit"
                        : "gui.athens_coins.flow_withdraw"),
                130, 25, mode == AtmMenu.MODE_DEPOSIT ? 0x8FE3FF : 0xFFD98F, false);

        graphics.drawString(font, Component.translatable("gui.athens_coins.atm_hint"),
                8, PANEL_H - 26, 0x9A8080, false);
        graphics.drawString(font, Component.translatable("gui.athens_coins.atm_rate"),
                8, PANEL_H - 15, 0x9A8080, false);
    }

    private void drawRight(GuiGraphics graphics, Component text, int rightX, int y, int color) {
        graphics.drawString(font, text, rightX - font.width(text), y, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // Coin icons next to each row.
        for (CoinType type : CoinType.ORDERED) {
            int y = topPos + ROW_START_Y + type.ordinal() * ROW_PITCH;
            graphics.renderItem(new ItemStack(type.item()), leftPos + 6, y);
        }

        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Extra context when hovering a coin icon. */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (CoinType type : CoinType.ORDERED) {
            int x = leftPos + 6;
            int y = topPos + ROW_START_Y + type.ordinal() * ROW_PITCH;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                List<Component> lines = new ArrayList<>();
                lines.add(type.displayName().copy().withStyle(style -> style.withColor(type.color())));
                lines.add(Component.translatable("tooltip.athens_coins.cash",
                                Component.literal(CoinFormat.plain(menu.cash(type)))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.athens_coins.digital",
                                Component.literal(CoinFormat.plain(menu.digital(type)))
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.AQUA));
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
