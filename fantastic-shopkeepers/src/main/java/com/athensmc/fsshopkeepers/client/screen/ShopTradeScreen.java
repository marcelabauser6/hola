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
 * <p>Drawn at vanilla size, with no magnification. An earlier version scaled the whole window up to fill more of the
 * screen, and it looked wrong as soon as anything else was visible: the creative inventory and JEI draw at the game's own
 * GUI scale, so a window scaled on top of them sat at a different size from everything around it. If the window should be
 * larger, the game's GUI scale setting is the place that makes every window larger together.</p>
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

    /**
     * Vanilla's container background and the three tones it shades a slot well with.
     *
     * <p>Needed because a well has to be hidden and another drawn somewhere vanilla never put one, and the texture only
     * contains wells at vanilla's own positions.</p>
     */
    private static final int GUI_BACKGROUND = 0xFFC6C6C6;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;

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
            return new Payment(ModItems.noteFor(offer.priceCents(), menu.cashLabel(), menu.cashColour()),
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
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

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

        renderTooltip(graphics, mouseX, mouseY);
        if (!offers.isEmpty()) {
            renderItemTooltips(graphics, mouseX, mouseY, offers);
        }
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

            Payment costA = paymentA(offer);
            Payment costB = paymentB(offer);
            drawPayment(graphics, costA, rowPaymentARect(slot));
            drawPayment(graphics, costB, rowPaymentBRect(slot));
            if (!costA.isEmpty() && !costB.isEmpty()) {
                drawPlus(graphics, TradeWindowGeometry.rowPaymentPlus(leftPos, topPos, slot));
            }

            Rect arrow = TradeWindowGeometry.rowArrow(leftPos, topPos, slot,
                    !costA.isEmpty() && !costB.isEmpty());
            graphics.blit(VILLAGER_LOCATION, arrow.x(), arrow.y(), 0,
                    offer.inStock() ? 15.0F : 25.0F, 171.0F, arrow.width(), arrow.height(),
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);

            Rect result = rowResultRect(slot);
            graphics.renderFakeItem(offer.result(), result.x(), result.y());
            graphics.renderItemDecorations(font, offer.result(), result.x(), result.y());

            graphics.pose().popPose();
        }
    }

    private void renderSelectedTrade(GuiGraphics graphics, ShopTradeMenu.OfferView offer) {
        Payment costA = paymentA(offer);
        Payment costB = paymentB(offer);
        boolean bothPayments = !costA.isEmpty() && !costB.isEmpty();

        // With a single payment the first well is painted out and the payment goes in the second, which is the one
        // nearest the arrow. An empty well beside the price read as a slot that had failed to fill, and it left the
        // price stranded at the far left.
        if (!bothPayments) {
            paintOverWell(graphics, TradeWindowGeometry.wellAround(slotPaymentARect()));
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        if (bothPayments) {
            drawPayment(graphics, costA, slotPaymentARect());
            drawPayment(graphics, costB, slotPaymentBRect());
            drawPlus(graphics, TradeWindowGeometry.slotPaymentPlus(leftPos, topPos));
        } else {
            Payment only = costA.isEmpty() ? costB : costA;
            drawPayment(graphics, only, TradeWindowGeometry.slotPaymentSingle(leftPos, topPos));
        }
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

    /** Paints vanilla's container grey over a slot well, to hide one this trade does not use. */
    private void paintOverWell(GuiGraphics graphics, Rect well) {
        if (!well.isEmpty()) {
            graphics.fill(well.x(), well.y(), well.right(), well.bottom(), GUI_BACKGROUND);
        }
    }

    /**
     * Draws a slot well the way vanilla shades one.
     *
     * <p>Dark along the top and left, light along the bottom and right, mid grey inside. Redrawn rather than blitted from
     * the texture because the well is being moved, and the texture only has wells where vanilla put them.</p>
     */
    private void drawWell(GuiGraphics graphics, Rect well) {
        if (well.isEmpty()) {
            return;
        }
        graphics.fill(well.x(), well.y(), well.right(), well.bottom(), SLOT_FILL);
        graphics.fill(well.x(), well.y(), well.right() - 1, well.y() + 1, SLOT_SHADOW);
        graphics.fill(well.x(), well.y(), well.x() + 1, well.bottom() - 1, SLOT_SHADOW);
        graphics.fill(well.x() + 1, well.bottom() - 1, well.right(), well.bottom(), SLOT_HIGHLIGHT);
        graphics.fill(well.right() - 1, well.y() + 1, well.right(), well.bottom(), SLOT_HIGHLIGHT);
    }

    /**
     * The plus between two payments.
     *
     * <p>Drawn as two bars rather than as the font's "+", so it lines up on the pixel grid and reads at the same weight
     * whatever language pack is loaded. Its job is to say the two costs are both required, not either-or.</p>
     */
    private void drawPlus(GuiGraphics graphics, Rect where) {
        if (where.isEmpty()) {
            return;
        }
        int midY = where.y() + where.height() / 2;
        int midX = where.x() + where.width() / 2;
        graphics.fill(where.x(), midY - 1, where.right(), midY + 1, VANILLA_TEXT);
        graphics.fill(midX - 1, where.y() + 1, midX + 1, where.bottom() - 1, VANILLA_TEXT);
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
        Payment costA = paymentA(offer);
        Payment costB = paymentB(offer);
        if (!costA.isEmpty() && !costB.isEmpty()) {
            if (showTooltip(graphics, mouseX, mouseY, slotPaymentARect(), costA.stack())
                    || showTooltip(graphics, mouseX, mouseY, slotPaymentBRect(), costB.stack())) {
                return;
            }
        } else {
            Payment only = costA.isEmpty() ? costB : costA;
            if (showTooltip(graphics, mouseX, mouseY,
                    TradeWindowGeometry.slotPaymentSingle(leftPos, topPos), only.stack())) {
                return;
            }
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
     * <p>Checked against the result square alone, so a click anywhere else in the window still reaches the slots and
     * widgets underneath.</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<ShopTradeMenu.OfferView> offers = menu.offers();

        if (!offers.isEmpty() && slotResultRect().contains(mouseX, mouseY)) {
            if (offers.get(selected).inStock() && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ShopTradeMenu.buttonFor(selected, hasShiftDown()));
            }
            return true;
        }
        if (canScroll(offers.size())
                && TradeWindowGeometry.scrollTrack(leftPos, topPos).contains(mouseX, mouseY)) {
            isDragging = true;
            scrollBarTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Moves the list to where the scrollbar was grabbed, thumb centred on the cursor. */
    private void scrollBarTo(double mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int travel = SCROLL_BAR_HEIGHT - SCROLLER_HEIGHT;
        double fraction = (mouseY - (topPos + SCROLL_BAR_TOP_POS_Y) - SCROLLER_HEIGHT / 2.0D) / travel;
        scrollOff = Math.max(0, Math.min((int) Math.round(fraction * max), max));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging && canScroll(menu.offers().size())) {
            scrollBarTo(mouseY);
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
