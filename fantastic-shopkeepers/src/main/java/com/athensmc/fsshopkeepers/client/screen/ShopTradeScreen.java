package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.layout.Rect;
import com.athensmc.fsshopkeepers.client.layout.TradeWindowGeometry;
import com.athensmc.fsshopkeepers.item.ModItems;
import com.athensmc.fsshopkeepers.menu.ShopTradeMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The window a customer buys from: the vanilla villager trading window, enlarged.
 *
 * <p>Every coordinate and sprite is vanilla's own, read out of {@code MerchantScreen} rather than guessed: the 276-pixel
 * window, seven 88x20 trade rows from {@code topPos + 18}, payments at {@code leftPos + 10} and {@code + 40}, the arrow
 * at u=15 with its barred variant at u=25 on row v=171, the result at {@code leftPos + 73}, the scroller at
 * {@code leftPos + 94} with its disabled form at u=6, and 0x404040 for text.</p>
 *
 * <p>Drawn through a scale transform, because the vanilla merchant window is 276x166 and looks lost on a modern screen.
 * The scale applies to everything, including the slots, so the whole window grows rather than gaining empty margins. All
 * mouse input is divided by the same factor before anything sees it, which is what keeps clicking a slot landing on the
 * slot that was drawn there - {@link AbstractContainerScreen} finds slots by comparing raw mouse coordinates against
 * {@code leftPos} and {@code slot.x}, so those have to agree about which space they are in.</p>
 *
 * <p>Fantastic Cash is a balance, not an object, so the first payment slot shows the mod's note with the amount where a
 * stack count would be. When a trade also asks for an item, that item takes the second slot: Cash and a coin side by
 * side, the way two stacks of payment look in vanilla.</p>
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

    /** An item is 16 pixels. Tooltips must respect that and nothing wider. */
    private static final int ITEM_SIZE = 16;

    private static final int VANILLA_TEXT = 0x404040;

    /** How much larger than vanilla the window is drawn, at most. */
    private static final float MAX_SCALE = 1.75F;

    /** Breathing room left around the window when working out how large it can be. */
    private static final int SCREEN_MARGIN = 20;

    private final Button[] tradeButtons = new Button[NUMBER_OF_OFFER_BUTTONS];

    private float scale = 1.0F;
    private int selected;
    private int scrollOff;
    private boolean isDragging;

    public ShopTradeScreen(ShopTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 276;
        this.inventoryLabelX = 107;
    }

    /**
     * How much to magnify the window.
     *
     * <p>Bounded by the screen so the window can never grow past its edges, and never below 1 so a small window is drawn
     * at vanilla size rather than shrunk.</p>
     */
    private float computeScale() {
        return TradeWindowGeometry.scaleFor(width, height);
    }

    @Override
    protected void init() {
        scale = computeScale();
        super.init();
        // Re-centred in the scaled space the whole screen is drawn in, because super centred it in screen pixels.
        leftPos = TradeWindowGeometry.leftPos(width, scale);
        topPos = TradeWindowGeometry.topPos(height, scale);

        clearWidgets();
        int y = topPos + SCROLL_BAR_TOP_POS_Y;
        for (int index = 0; index < NUMBER_OF_OFFER_BUTTONS; index++) {
            int row = index;
            tradeButtons[index] = addRenderableWidget(Button.builder(Component.empty(),
                    pressed -> selected = row + scrollOff)
                    .bounds(leftPos + TRADE_BUTTON_X, y, TRADE_BUTTON_WIDTH, TRADE_BUTTON_HEIGHT).build());
            y += TRADE_BUTTON_HEIGHT;
        }
    }

    private boolean canScroll(int count) {
        return count > NUMBER_OF_OFFER_BUTTONS;
    }

    private int maxScroll() {
        return Math.max(0, menu.offers().size() - NUMBER_OF_OFFER_BUTTONS);
    }

    // ------------------------------------------------------------ payments

    /** A stack to show in a payment slot, and the text to draw where its count would go. */
    private record Payment(ItemStack stack, String countOverride) {

        boolean isEmpty() {
            return stack.isEmpty();
        }
    }

    /**
     * The first payment: the Cash note when the trade has a price, otherwise the first payment item.
     */
    private Payment paymentA(ShopTradeMenu.OfferView offer) {
        if (offer.priceCents() > 0L) {
            return new Payment(ModItems.noteFor(offer.priceCents()),
                    ModItems.shortAmount(offer.priceCents()));
        }
        return new Payment(offer.cost1(), null);
    }

    /**
     * The second payment.
     *
     * <p>When the trade is priced in Cash, this is the payment item, so a trade that wants money <em>and</em> an item
     * shows both: Cash in the first slot, the item in the second. That was the whole point of having two slots.</p>
     */
    private Payment paymentB(ShopTradeMenu.OfferView offer) {
        if (offer.priceCents() > 0L) {
            return new Payment(offer.cost1(), null);
        }
        return new Payment(offer.cost2(), null);
    }

    // ------------------------------------------------------------ geometry

    private int rowItemY(int slot) {
        return TradeWindowGeometry.rowItemY(topPos, slot);
    }

    private Rect rowPaymentARect(int slot) {
        return TradeWindowGeometry.rowPaymentA(leftPos, topPos, slot);
    }

    private Rect rowPaymentBRect(int slot) {
        return TradeWindowGeometry.rowPaymentB(leftPos, topPos, slot);
    }

    private Rect rowResultRect(int slot) {
        return TradeWindowGeometry.rowResult(leftPos, topPos, slot);
    }

    private Rect slotPaymentARect() {
        return TradeWindowGeometry.slotPaymentA(leftPos, topPos);
    }

    private Rect slotPaymentBRect() {
        return TradeWindowGeometry.slotPaymentB(leftPos, topPos);
    }

    private Rect slotResultRect() {
        return TradeWindowGeometry.slotResult(leftPos, topPos);
    }

    // ------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(VILLAGER_LOCATION, leftPos, topPos, 0, 0.0F, 0.0F, imageWidth, imageHeight,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);

        List<ShopTradeMenu.OfferView> offers = menu.offers();
        if (selected >= 0 && selected < offers.size() && !offers.get(selected).inStock()) {
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
        // The world dim stays unscaled: it covers the screen, not the window.
        renderBackground(graphics);

        int viewX = (int) (mouseX / scale);
        int viewY = (int) (mouseY / scale);

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);

        super.render(graphics, viewX, viewY, partialTick);

        List<ShopTradeMenu.OfferView> offers = menu.offers();
        for (int index = 0; index < NUMBER_OF_OFFER_BUTTONS; index++) {
            tradeButtons[index].visible = index + scrollOff < offers.size();
        }
        if (!offers.isEmpty()) {
            selected = Math.max(0, Math.min(selected, offers.size() - 1));
            scrollOff = Math.max(0, Math.min(scrollOff, maxScroll()));
            renderScroller(graphics, offers.size());
            renderTradeRows(graphics, offers);
            renderSelectedTrade(graphics, offers.get(selected));
        }

        renderTooltip(graphics, viewX, viewY);
        if (!offers.isEmpty()) {
            renderItemTooltips(graphics, viewX, viewY, offers);
        }

        graphics.pose().popPose();
    }

    private void renderTradeRows(GuiGraphics graphics, List<ShopTradeMenu.OfferView> offers) {
        for (int slot = 0; slot < NUMBER_OF_OFFER_BUTTONS; slot++) {
            int index = slot + scrollOff;
            if (index >= offers.size()) {
                break;
            }
            ShopTradeMenu.OfferView offer = offers.get(index);

            graphics.pose().pushPose();
            // Forward of the button faces, the way vanilla lifts its trade items.
            graphics.pose().translate(0.0F, 0.0F, 100.0F);

            drawPayment(graphics, paymentA(offer), rowPaymentARect(slot));
            drawPayment(graphics, paymentB(offer), rowPaymentBRect(slot));

            graphics.blit(VILLAGER_LOCATION, leftPos + TRADE_BUTTON_X + SELL_ITEM_2_X + 20,
                    rowItemY(slot) + 3, 0, offer.inStock() ? 15.0F : 25.0F, 171.0F, 10, 9,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);

            Rect result = rowResultRect(slot);
            graphics.renderFakeItem(offer.result(), result.x(), result.y());
            graphics.renderItemDecorations(font, offer.result(), result.x(), result.y());

            graphics.pose().popPose();
        }
    }

    private void renderSelectedTrade(GuiGraphics graphics, ShopTradeMenu.OfferView offer) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        drawPayment(graphics, paymentA(offer), slotPaymentARect());
        drawPayment(graphics, paymentB(offer), slotPaymentBRect());
        Rect result = slotResultRect();
        if (!offer.result().isEmpty()) {
            graphics.renderFakeItem(offer.result(), result.x(), result.y());
            graphics.renderItemDecorations(font, offer.result(), result.x(), result.y());
        }
        graphics.pose().popPose();
    }

    private void drawPayment(GuiGraphics graphics, Payment payment, Rect where) {
        if (payment.isEmpty()) {
            return;
        }
        graphics.renderFakeItem(payment.stack(), where.x(), where.y());
        if (payment.countOverride() == null) {
            graphics.renderItemDecorations(font, payment.stack(), where.x(), where.y());
        } else {
            graphics.renderItemDecorations(font, payment.stack(), where.x(), where.y(),
                    payment.countOverride());
        }
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

    /**
     * Tooltips for the items this screen draws itself.
     *
     * <p>Each test is against the item's own 16x16 square and nothing larger. Using the whole 88x20 trade row meant the
     * tooltip appeared over the empty half of the row and over the arrow, covering the rest of the list while the cursor
     * was nowhere near an item.</p>
     */
    private void renderItemTooltips(GuiGraphics graphics, int mouseX, int mouseY,
            List<ShopTradeMenu.OfferView> offers) {
        for (int slot = 0; slot < NUMBER_OF_OFFER_BUTTONS; slot++) {
            int index = slot + scrollOff;
            if (index >= offers.size()) {
                break;
            }
            ShopTradeMenu.OfferView offer = offers.get(index);
            if (showTooltip(graphics, mouseX, mouseY, rowPaymentARect(slot), paymentA(offer).stack())
                    || showTooltip(graphics, mouseX, mouseY, rowPaymentBRect(slot), paymentB(offer).stack())
                    || showTooltip(graphics, mouseX, mouseY, rowResultRect(slot), offer.result())) {
                return;
            }
        }

        ShopTradeMenu.OfferView offer = offers.get(selected);
        if (showTooltip(graphics, mouseX, mouseY, slotPaymentARect(), paymentA(offer).stack())
                || showTooltip(graphics, mouseX, mouseY, slotPaymentBRect(), paymentB(offer).stack())) {
            return;
        }
        showTooltip(graphics, mouseX, mouseY, slotResultRect(), offer.result());
    }

    private boolean showTooltip(GuiGraphics graphics, int mouseX, int mouseY, Rect where, ItemStack stack) {
        if (stack.isEmpty() || !where.contains(mouseX, mouseY)) {
            return false;
        }
        graphics.renderTooltip(font, stack, mouseX, mouseY);
        return true;
    }

    // ------------------------------------------------------------ input

    /**
     * Buying, on a click of the result slot.
     *
     * <p>Coordinates are divided by the scale before anything, including {@code super}, sees them. Everything below this
     * point works in the same space the window was drawn in.</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double viewX = mouseX / scale;
        double viewY = mouseY / scale;
        List<ShopTradeMenu.OfferView> offers = menu.offers();

        if (!offers.isEmpty() && slotResultRect().contains(viewX, viewY)) {
            if (offers.get(selected).inStock() && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ShopTradeMenu.buttonFor(selected, hasShiftDown()));
            }
            return true;
        }
        if (canScroll(offers.size())
                && TradeWindowGeometry.scrollTrack(leftPos, topPos).contains(viewX, viewY)) {
            isDragging = true;
            return true;
        }
        return super.mouseClicked(viewX, viewY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        return super.mouseReleased(mouseX / scale, mouseY / scale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double viewY = mouseY / scale;
        int count = menu.offers().size();
        if (isDragging && canScroll(count)) {
            int top = topPos + SCROLL_BAR_TOP_POS_Y;
            int travel = SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT;
            double fraction = (viewY - top - SCROLLER_HEIGHT / 2.0D) / travel;
            scrollOff = Math.max(0, Math.min((int) Math.round(fraction * maxScroll()), maxScroll()));
            return true;
        }
        return super.mouseDragged(mouseX / scale, viewY, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (canScroll(menu.offers().size())) {
            scrollOff = Math.max(0, Math.min(scrollOff - (int) Math.signum(delta), maxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX / scale, mouseY / scale, delta);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX / scale, mouseY / scale);
    }
}
