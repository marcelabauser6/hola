package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.menu.ShopTradeMenu;
import com.athensmc.fsshopkeepers.money.Cash;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The window a customer buys from.
 *
 * <p>The vanilla villager trading window, drawn from vanilla's own {@code villager2.png}: the same frame, the same list
 * of trades down the left, the same two payment slots and result slot on the right, the same scroller. A customer who
 * has traded with a villager already knows how to use it, and that familiarity is worth more than anything a custom
 * design would add. The screens worth redesigning are the ones an administrator uses.</p>
 *
 * <p>What differs from vanilla is only what has to. A price in Fantastic Cash is money rather than an item, so where
 * vanilla shows a stack of emeralds this shows the amount, and buying is a click on the result slot rather than a drag
 * of payment into the input slots. The geometry, the texture and the colours are vanilla's.</p>
 */
public final class ShopTradeScreen extends AbstractContainerScreen<ShopTradeMenu> {

    /** Vanilla's merchant texture and its real dimensions. */
    private static final ResourceLocation VILLAGER_TEXTURE =
            new ResourceLocation("textures/gui/container/villager2.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;

    /** Vanilla merchant window size. Matching it is what makes the inventory slots land correctly. */
    private static final int WINDOW_WIDTH = 276;
    private static final int WINDOW_HEIGHT = 166;

    /** Vanilla's trade list geometry. */
    private static final int TRADE_LIST_X = 5;
    private static final int TRADE_LIST_Y = 16;
    private static final int TRADE_ROW_WIDTH = 89;
    private static final int TRADE_ROW_HEIGHT = 20;
    private static final int VISIBLE_TRADES = 7;

    /** Vanilla's scroller track and sprite. */
    private static final int SCROLLER_X = 94;
    private static final int SCROLLER_TRACK_Y = 18;
    private static final int SCROLLER_TRACK_HEIGHT = 139;
    private static final int SCROLLER_WIDTH = 6;
    private static final int SCROLLER_HEIGHT = 27;
    private static final int SCROLLER_U = 0;
    private static final int SCROLLER_V = 199;

    /** Vanilla's slot positions for the two payments and the result. */
    private static final int COST_1_X = 136;
    private static final int COST_2_X = 162;
    private static final int RESULT_X = 220;
    private static final int SLOT_Y = 37;

    /** The dark grey vanilla uses for text on its light container backgrounds. */
    private static final int VANILLA_TEXT = 0x404040;

    private int scroll;
    private int selected;

    public ShopTradeScreen(ShopTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // Vanilla's merchant window puts its own labels in different places from a generic container.
        this.inventoryLabelX = 107;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private int maxScroll() {
        return Math.max(0, menu.offers().size() - VISIBLE_TRADES);
    }

    private int rowX() {
        return leftPos + TRADE_LIST_X;
    }

    private int rowY(int slot) {
        return topPos + TRADE_LIST_Y + slot * TRADE_ROW_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(VILLAGER_TEXTURE, leftPos, topPos, 0, 0.0F, 0.0F, imageWidth, imageHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);

        drawTradeList(graphics, mouseX, mouseY);
        drawSelectedTrade(graphics);
        drawScroller(graphics);
    }

    /**
     * The list of trades down the left.
     *
     * <p>A Cash-priced row shows the item and its price, because there is no cost item to show. A bartering row shows
     * cost and result the way vanilla does.</p>
     */
    private void drawTradeList(GuiGraphics graphics, int mouseX, int mouseY) {
        var offers = menu.offers();
        scroll = Math.min(scroll, maxScroll());
        for (int slot = 0; slot < VISIBLE_TRADES; slot++) {
            int index = scroll + slot;
            if (index >= offers.size()) {
                break;
            }
            ShopTradeMenu.OfferView offer = offers.get(index);
            int x = rowX();
            int y = rowY(slot);
            boolean hovered = mouseX >= x && mouseX < x + TRADE_ROW_WIDTH
                    && mouseY >= y && mouseY < y + TRADE_ROW_HEIGHT;

            if (index == selected) {
                graphics.fill(x, y, x + TRADE_ROW_WIDTH, y + TRADE_ROW_HEIGHT, 0x55FFFFFF);
            } else if (hovered) {
                graphics.fill(x, y, x + TRADE_ROW_WIDTH, y + TRADE_ROW_HEIGHT, 0x33FFFFFF);
            }

            ItemStack shown = offer.result().isEmpty() ? offer.cost1() : offer.result();
            if (!shown.isEmpty()) {
                graphics.renderItem(shown, x + 2, y + 2);
                graphics.renderItemDecorations(font, shown, x + 2, y + 2);
            }

            if (offer.priceCents() > 0L) {
                String price = Cash.format(offer.priceCents());
                String fitted = font.plainSubstrByWidth(price, TRADE_ROW_WIDTH - 24);
                graphics.drawString(font, fitted, x + 22, y + 6,
                        offer.inStock() ? VANILLA_TEXT : 0x909090, false);
            } else if (!offer.cost1().isEmpty() && !offer.result().isEmpty()) {
                graphics.renderItem(offer.cost1(), x + 2, y + 2);
                graphics.drawString(font, "\u2192", x + 24, y + 6, VANILLA_TEXT, false);
                graphics.renderItem(offer.result(), x + 40, y + 2);
                graphics.renderItemDecorations(font, offer.result(), x + 40, y + 2);
            }

            if (!offer.inStock()) {
                graphics.drawString(font, "\u00d7", x + TRADE_ROW_WIDTH - 12, y + 6, 0xAA3030, false);
            }
        }
    }

    /** The selected trade laid into vanilla's payment and result slots. */
    private void drawSelectedTrade(GuiGraphics graphics) {
        var offers = menu.offers();
        if (selected < 0 || selected >= offers.size()) {
            return;
        }
        ShopTradeMenu.OfferView offer = offers.get(selected);

        if (!offer.cost1().isEmpty()) {
            graphics.renderItem(offer.cost1(), leftPos + COST_1_X, topPos + SLOT_Y);
            graphics.renderItemDecorations(font, offer.cost1(), leftPos + COST_1_X, topPos + SLOT_Y);
        }
        if (!offer.cost2().isEmpty()) {
            graphics.renderItem(offer.cost2(), leftPos + COST_2_X, topPos + SLOT_Y);
            graphics.renderItemDecorations(font, offer.cost2(), leftPos + COST_2_X, topPos + SLOT_Y);
        }
        if (!offer.result().isEmpty()) {
            graphics.renderItem(offer.result(), leftPos + RESULT_X, topPos + SLOT_Y);
            graphics.renderItemDecorations(font, offer.result(), leftPos + RESULT_X, topPos + SLOT_Y);
        }

        // The price sits where vanilla's emerald cost would be, so the eye finds it in the same place.
        if (offer.priceCents() > 0L && offer.cost1().isEmpty()) {
            String price = Cash.format(offer.priceCents());
            graphics.drawString(font, price, leftPos + COST_1_X - 2, topPos + SLOT_Y + 22,
                    VANILLA_TEXT, false);
        }
    }

    private void drawScroller(GuiGraphics graphics) {
        int max = maxScroll();
        int travel = SCROLLER_TRACK_HEIGHT - SCROLLER_HEIGHT;
        int offset = max == 0 ? 0 : travel * scroll / max;
        graphics.blit(VILLAGER_TEXTURE, leftPos + SCROLLER_X, topPos + SCROLLER_TRACK_Y + offset, 0,
                (float) SCROLLER_U, (float) (max == 0 ? SCROLLER_V + SCROLLER_HEIGHT : SCROLLER_V),
                SCROLLER_WIDTH, SCROLLER_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String name = font.plainSubstrByWidth(menu.shopName(), TRADE_ROW_WIDTH + 4);
        graphics.drawString(font, name, TRADE_LIST_X, 6, VANILLA_TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, VANILLA_TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderTradeTooltip(graphics, mouseX, mouseY);
    }

    /** Tooltips for the trade rows and the result slot, which are drawn items rather than real slots. */
    private void renderTradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        var offers = menu.offers();
        for (int slot = 0; slot < VISIBLE_TRADES; slot++) {
            int index = scroll + slot;
            if (index >= offers.size()) {
                break;
            }
            int x = rowX();
            int y = rowY(slot);
            if (mouseX < x || mouseX >= x + TRADE_ROW_WIDTH || mouseY < y || mouseY >= y + TRADE_ROW_HEIGHT) {
                continue;
            }
            ShopTradeMenu.OfferView offer = offers.get(index);
            ItemStack shown = offer.result().isEmpty() ? offer.cost1() : offer.result();
            if (!shown.isEmpty()) {
                graphics.renderTooltip(font, shown, mouseX, mouseY);
            }
            return;
        }

        if (selected >= 0 && selected < offers.size()) {
            int rx = leftPos + RESULT_X;
            int ry = topPos + SLOT_Y;
            if (mouseX >= rx && mouseX < rx + 16 && mouseY >= ry && mouseY < ry + 16) {
                ItemStack result = offers.get(selected).result();
                if (!result.isEmpty()) {
                    graphics.renderTooltip(font, result, mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var offers = menu.offers();

        for (int slot = 0; slot < VISIBLE_TRADES; slot++) {
            int index = scroll + slot;
            if (index >= offers.size()) {
                break;
            }
            int x = rowX();
            int y = rowY(slot);
            if (mouseX >= x && mouseX < x + TRADE_ROW_WIDTH && mouseY >= y && mouseY < y + TRADE_ROW_HEIGHT) {
                selected = index;
                return true;
            }
        }

        // Clicking the result slot is the purchase, exactly as taking from the result slot is in vanilla.
        if (selected >= 0 && selected < offers.size()) {
            int rx = leftPos + RESULT_X;
            int ry = topPos + SLOT_Y;
            if (mouseX >= rx - 1 && mouseX < rx + 17 && mouseY >= ry - 1 && mouseY < ry + 17) {
                if (offers.get(selected).inStock() && minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            ShopTradeMenu.buttonFor(selected, hasShiftDown()));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = maxScroll();
        if (max > 0) {
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), max));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
