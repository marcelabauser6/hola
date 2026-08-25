package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.item.ModItems;
import com.athensmc.fsshopkeepers.menu.ShopTradeMenu;
import com.athensmc.fsshopkeepers.money.Cash;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The window a customer buys from: the vanilla villager trading window.
 *
 * <p>Every coordinate, sprite and colour here is copied from vanilla's own {@code MerchantScreen}, read out of the
 * game's code rather than guessed: the 276-pixel window, the seven 88x20 trade buttons down the left starting at
 * {@code topPos + 18}, the payment items at {@code leftPos + 10} and {@code + 40}, the arrow sprite at u=15 (u=25 when
 * out of stock) on row v=171, the result at {@code leftPos + 73}, the scroller at {@code leftPos + 94} with its
 * disabled variant at u=6, and the 0x404040 grey vanilla uses for text on tan backgrounds.</p>
 *
 * <p>The trade rows are real vanilla {@link Button} widgets, which is what gives the list its familiar column-of-buttons
 * look, and the items are drawn over them afterwards exactly as vanilla does.</p>
 *
 * <p>The one thing that cannot be copied is the payment. A villager wants emeralds; a Fantastic Cash price is a bank
 * balance with no object to put in the slot. So the slot shows the mod's Cash note with the amount in the corner, which
 * is the same shape of information a stack of emeralds carried, and the money is taken from the balance when the result
 * is clicked.</p>
 */
public final class ShopTradeScreen extends AbstractContainerScreen<ShopTradeMenu> {

    private static final ResourceLocation VILLAGER_LOCATION =
            new ResourceLocation("textures/gui/container/villager2.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int MERCHANT_MENU_PART_X = 99;
    private static final int SELL_ITEM_1_X = 5;
    private static final int SELL_ITEM_2_X = 35;
    private static final int BUY_ITEM_X = 68;
    private static final int LABEL_Y = 6;
    private static final int NUMBER_OF_OFFER_BUTTONS = 7;
    private static final int TRADE_BUTTON_X = 5;
    private static final int TRADE_BUTTON_HEIGHT = 20;
    private static final int TRADE_BUTTON_WIDTH = 88;
    private static final int SCROLLER_HEIGHT = 27;
    private static final int SCROLLER_WIDTH = 6;
    private static final int SCROLL_BAR_HEIGHT = 139;
    private static final int SCROLL_BAR_TOP_POS_Y = 18;
    private static final int SCROLL_BAR_START_X = 94;

    /** Vanilla's slot positions, matching {@code MerchantMenu}. */
    private static final int COST_1_X = 136;
    private static final int COST_2_X = 162;
    private static final int RESULT_X = 220;
    private static final int SLOT_Y = 37;

    /** The grey vanilla writes with on its tan container art. */
    private static final int VANILLA_TEXT = 0x404040;

    private final Button[] tradeButtons = new Button[NUMBER_OF_OFFER_BUTTONS];

    private int selected;
    private int scrollOff;
    private boolean isDragging;

    public ShopTradeScreen(ShopTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 276;
        this.inventoryLabelX = 107;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + SCROLL_BAR_TOP_POS_Y;
        for (int index = 0; index < NUMBER_OF_OFFER_BUTTONS; index++) {
            int row = index;
            tradeButtons[index] = addRenderableWidget(Button.builder(Component.empty(), pressed -> {
                selected = row + scrollOff;
            }).bounds(leftPos + TRADE_BUTTON_X, y, TRADE_BUTTON_WIDTH, TRADE_BUTTON_HEIGHT).build());
            y += TRADE_BUTTON_HEIGHT;
        }
    }

    private boolean canScroll(int count) {
        return count > NUMBER_OF_OFFER_BUTTONS;
    }

    private int maxScroll() {
        return Math.max(0, menu.offers().size() - NUMBER_OF_OFFER_BUTTONS);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(VILLAGER_LOCATION, leftPos, topPos, 0, 0.0F, 0.0F, imageWidth, imageHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);

        var offers = menu.offers();
        if (selected >= 0 && selected < offers.size() && !offers.get(selected).inStock()) {
            // Vanilla's barred-arrow overlay over the big arrow between the slots.
            graphics.blit(VILLAGER_LOCATION, leftPos + 83 + MERCHANT_MENU_PART_X, topPos + 35, 0,
                    311.0F, 0.0F, 28, 21, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String shopName = font.plainSubstrByWidth(menu.shopName(), imageWidth - 120);
        graphics.drawString(font, shopName, 49 + imageWidth / 2 - font.width(shopName) / 2, LABEL_Y,
                VANILLA_TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, VANILLA_TEXT, false);

        Component tradesLabel = Component.translatable("fsshopkeepers.trade.trades");
        graphics.drawString(font, tradesLabel, TRADE_BUTTON_X - font.width(tradesLabel) / 2 + 48, LABEL_Y,
                VANILLA_TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        var offers = menu.offers();
        // Buttons past the end of the list are hidden rather than drawn empty, as vanilla does.
        for (int index = 0; index < NUMBER_OF_OFFER_BUTTONS; index++) {
            tradeButtons[index].visible = index + scrollOff < offers.size();
        }
        if (offers.isEmpty()) {
            renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        selected = Math.max(0, Math.min(selected, offers.size() - 1));
        scrollOff = Math.max(0, Math.min(scrollOff, maxScroll()));

        renderScroller(graphics, offers.size());
        renderTradeRows(graphics, offers);
        renderSelectedTrade(graphics, offers.get(selected));

        renderTooltip(graphics, mouseX, mouseY);
        renderTradeTooltips(graphics, mouseX, mouseY, offers);
    }

    /**
     * The items on each trade row.
     *
     * <p>Pushed forward 100 in the matrix, the way vanilla does it, so the icons sit above the button faces rather than
     * being hidden behind them.</p>
     */
    private void renderTradeRows(GuiGraphics graphics, java.util.List<ShopTradeMenu.OfferView> offers) {
        int rowY = topPos + 16 + 1;
        int costX = leftPos + TRADE_BUTTON_X + 5;
        int shown = 0;
        for (int index = 0; index < offers.size(); index++) {
            if (canScroll(offers.size()) && (index < scrollOff || index >= NUMBER_OF_OFFER_BUTTONS + scrollOff)) {
                continue;
            }
            ShopTradeMenu.OfferView offer = offers.get(index);
            int itemY = rowY + 2;

            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 100.0F);

            renderCost(graphics, offer, costX, itemY);
            if (!offer.cost2().isEmpty()) {
                graphics.renderFakeItem(offer.cost2(), leftPos + TRADE_BUTTON_X + SELL_ITEM_2_X, itemY);
                graphics.renderItemDecorations(font, offer.cost2(),
                        leftPos + TRADE_BUTTON_X + SELL_ITEM_2_X, itemY);
            }
            graphics.blit(VILLAGER_LOCATION, leftPos + TRADE_BUTTON_X + SELL_ITEM_2_X + 20, itemY + 3, 0,
                    offer.inStock() ? 15.0F : 25.0F, 171.0F, 10, 9, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.renderFakeItem(offer.result(), leftPos + TRADE_BUTTON_X + BUY_ITEM_X, itemY);
            graphics.renderItemDecorations(font, offer.result(), leftPos + TRADE_BUTTON_X + BUY_ITEM_X, itemY);

            graphics.pose().popPose();

            rowY += TRADE_BUTTON_HEIGHT;
            if (++shown >= NUMBER_OF_OFFER_BUTTONS) {
                break;
            }
        }
    }

    /**
     * The payment side of a row.
     *
     * <p>A Cash price becomes the note with a short amount in the corner where a stack count would be. An item price is
     * drawn as the item, exactly as vanilla draws emeralds.</p>
     */
    private void renderCost(GuiGraphics graphics, ShopTradeMenu.OfferView offer, int x, int y) {
        if (offer.priceCents() > 0L) {
            ItemStack note = ModItems.noteFor(offer.priceCents());
            graphics.renderFakeItem(note, x, y);
            graphics.renderItemDecorations(font, note, x, y, ModItems.shortAmount(offer.priceCents()));
            return;
        }
        if (!offer.cost1().isEmpty()) {
            graphics.renderFakeItem(offer.cost1(), x, y);
            graphics.renderItemDecorations(font, offer.cost1(), x, y);
        }
    }

    /** The selected trade in the three slots on the right. */
    private void renderSelectedTrade(GuiGraphics graphics, ShopTradeMenu.OfferView offer) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);

        if (offer.priceCents() > 0L) {
            ItemStack note = ModItems.noteFor(offer.priceCents());
            graphics.renderFakeItem(note, leftPos + COST_1_X, topPos + SLOT_Y);
            graphics.renderItemDecorations(font, note, leftPos + COST_1_X, topPos + SLOT_Y,
                    ModItems.shortAmount(offer.priceCents()));
        } else if (!offer.cost1().isEmpty()) {
            graphics.renderFakeItem(offer.cost1(), leftPos + COST_1_X, topPos + SLOT_Y);
            graphics.renderItemDecorations(font, offer.cost1(), leftPos + COST_1_X, topPos + SLOT_Y);
        }
        if (!offer.cost2().isEmpty()) {
            graphics.renderFakeItem(offer.cost2(), leftPos + COST_2_X, topPos + SLOT_Y);
            graphics.renderItemDecorations(font, offer.cost2(), leftPos + COST_2_X, topPos + SLOT_Y);
        }
        if (!offer.result().isEmpty()) {
            graphics.renderFakeItem(offer.result(), leftPos + RESULT_X, topPos + SLOT_Y);
            graphics.renderItemDecorations(font, offer.result(), leftPos + RESULT_X, topPos + SLOT_Y);
        }
        graphics.pose().popPose();
    }

    private void renderScroller(GuiGraphics graphics, int count) {
        if (!canScroll(count)) {
            graphics.blit(VILLAGER_LOCATION, leftPos + SCROLL_BAR_START_X, topPos + SCROLL_BAR_TOP_POS_Y, 0,
                    6.0F, 199.0F, SCROLLER_WIDTH, SCROLLER_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            return;
        }
        int travel = SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT;
        int offset = maxScroll() == 0 ? 0 : travel * scrollOff / maxScroll();
        graphics.blit(VILLAGER_LOCATION, leftPos + SCROLL_BAR_START_X,
                topPos + SCROLL_BAR_TOP_POS_Y + offset, 0, 0.0F, 199.0F, SCROLLER_WIDTH, SCROLLER_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /** Tooltips for the drawn items, which are pictures rather than real slots. */
    private void renderTradeTooltips(GuiGraphics graphics, int mouseX, int mouseY,
            java.util.List<ShopTradeMenu.OfferView> offers) {
        for (int index = 0; index < NUMBER_OF_OFFER_BUTTONS; index++) {
            int offerIndex = index + scrollOff;
            if (offerIndex >= offers.size()) {
                break;
            }
            if (!tradeButtons[index].isHoveredOrFocused()) {
                continue;
            }
            ShopTradeMenu.OfferView offer = offers.get(offerIndex);
            ItemStack hovered = hoveredRowItem(offer, mouseX, index);
            if (!hovered.isEmpty()) {
                graphics.renderTooltip(font, hovered, mouseX, mouseY);
            }
            return;
        }

        ShopTradeMenu.OfferView offer = offers.get(selected);
        if (overResultSlot(mouseX, mouseY) && !offer.result().isEmpty()) {
            graphics.renderTooltip(font, offer.result(), mouseX, mouseY);
        }
    }

    /** Which of a row's three item positions the cursor is over. */
    private ItemStack hoveredRowItem(ShopTradeMenu.OfferView offer, int mouseX, int row) {
        int costX = leftPos + TRADE_BUTTON_X + 5;
        int resultX = leftPos + TRADE_BUTTON_X + BUY_ITEM_X;
        if (mouseX >= resultX && mouseX < resultX + 16) {
            return offer.result();
        }
        if (mouseX >= costX && mouseX < costX + 16) {
            return offer.priceCents() > 0L ? ModItems.noteFor(offer.priceCents()) : offer.cost1();
        }
        return offer.result();
    }

    private boolean overResultSlot(double mouseX, double mouseY) {
        int x = leftPos + RESULT_X;
        int y = topPos + SLOT_Y;
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }

    /**
     * Buying, on a click of the result slot.
     *
     * <p>Where vanilla has the customer take the result out of a real slot, this sends the purchase to the server, which
     * charges the balance and hands the goods over. The gesture is the same one; only what happens behind it differs.</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var offers = menu.offers();
        if (!offers.isEmpty() && overResultSlot(mouseX, mouseY)) {
            if (offers.get(selected).inStock() && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ShopTradeMenu.buttonFor(selected, hasShiftDown()));
            }
            return true;
        }
        if (canScroll(offers.size()) && mouseX >= leftPos + SCROLL_BAR_START_X
                && mouseX < leftPos + SCROLL_BAR_START_X + SCROLLER_WIDTH
                && mouseY >= topPos + SCROLL_BAR_TOP_POS_Y
                && mouseY < topPos + SCROLL_BAR_TOP_POS_Y + SCROLL_BAR_HEIGHT) {
            isDragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int count = menu.offers().size();
        if (isDragging && canScroll(count)) {
            int top = topPos + SCROLL_BAR_TOP_POS_Y;
            int travel = SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT;
            double fraction = (mouseY - top - SCROLLER_HEIGHT / 2.0D) / travel;
            scrollOff = Math.max(0, Math.min((int) Math.round(fraction * maxScroll()), maxScroll()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (canScroll(menu.offers().size())) {
            scrollOff = Math.max(0, Math.min(scrollOff - (int) Math.signum(delta), maxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
