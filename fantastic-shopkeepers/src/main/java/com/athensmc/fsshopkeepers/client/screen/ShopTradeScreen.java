package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.layout.Rect;
import com.athensmc.fsshopkeepers.client.theme.FantasticTheme;
import com.athensmc.fsshopkeepers.menu.ShopTradeMenu;
import com.athensmc.fsshopkeepers.money.Cash;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The window a customer buys from.
 *
 * <p>Left is the shop's list of offers, right is the customer's own inventory. A row is a purchase: left-click buys
 * one, shift-click buys up to {@link ShopTradeMenu#BULK_TIMES}. A row the shop cannot currently honour is drawn
 * greyed and says why, so a customer is never invited to click something that will refuse them.</p>
 *
 * <p>Deliberately not a redesign of the buying experience: it is the vanilla arrangement of a trade list beside your
 * inventory, with the price written as money because the price <em>is</em> money. The screens worth redesigning are
 * the ones an administrator uses.</p>
 */
public final class ShopTradeScreen extends AbstractContainerScreen<ShopTradeMenu> {

    private static final int WINDOW_WIDTH = 278;
    private static final int WINDOW_HEIGHT = 170;

    /** The offer list's column, in window-relative pixels. */
    private static final int LIST_X = 7;
    private static final int LIST_Y = 18;
    private static final int LIST_WIDTH = 96;

    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 2;

    private int scroll;
    private String hint = "";

    public ShopTradeScreen(ShopTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
        // Moved off the default position, which would put the label on top of the offer list.
        this.inventoryLabelX = 101;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelX = 101;
        this.inventoryLabelY = 74;
    }

    private Rect listArea() {
        return new Rect(leftPos + LIST_X, topPos + LIST_Y, LIST_WIDTH,
                WINDOW_HEIGHT - LIST_Y - 8);
    }

    private int visibleRows() {
        return Math.max(0, (listArea().height() + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
    }

    private Rect row(int slot) {
        Rect list = listArea();
        if (slot < 0 || slot >= visibleRows()) {
            return Rect.EMPTY;
        }
        return new Rect(list.x(), list.y() + slot * (ROW_HEIGHT + ROW_GAP), list.width(), ROW_HEIGHT);
    }

    /**
     * Draws the window's own chrome.
     *
     * <p>Panels rather than a background texture, matching the editor, so the two screens read as one family and the
     * mod ships without an atlas that could fail to load.</p>
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Rect window = new Rect(leftPos, topPos, WINDOW_WIDTH, WINDOW_HEIGHT);
        FantasticTheme.panel(graphics, window, FantasticTheme.PANEL);

        Rect list = listArea();
        FantasticTheme.panel(graphics, list, FantasticTheme.PANEL_DARK);

        // Wells behind the player's inventory slots, so the slots read as slots.
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            FantasticTheme.slot(graphics, new Rect(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18), false);
        }

        drawOffers(graphics, mouseX, mouseY);
    }

    private void drawOffers(GuiGraphics graphics, int mouseX, int mouseY) {
        var offers = menu.offers();
        int visible = visibleRows();
        int max = Math.max(0, offers.size() - visible);
        scroll = Math.max(0, Math.min(scroll, max));

        if (offers.isEmpty()) {
            FantasticTheme.leftText(graphics, font,
                    new Rect(listArea().x() + 4, listArea().y() + 6, listArea().width() - 8, 10),
                    "Sin tratos", FantasticTheme.TEXT_MUTED);
            return;
        }

        for (int slot = 0; slot < visible; slot++) {
            int index = scroll + slot;
            if (index >= offers.size()) {
                break;
            }
            ShopTradeMenu.OfferView offer = offers.get(index);
            Rect rect = row(slot);
            boolean hovered = rect.contains(mouseX, mouseY);
            boolean available = offer.inStock();

            FantasticTheme.panel(graphics, rect,
                    !available ? FantasticTheme.PANEL_DARK
                            : hovered ? FantasticTheme.PANEL_LIGHT : FantasticTheme.PANEL);

            Rect icon = new Rect(rect.x() + 2, rect.y() + 2, 18, 18);
            if (!offer.result().isEmpty()) {
                graphics.renderItem(offer.result(), icon.x(), icon.y());
                graphics.renderItemDecorations(font, offer.result(), icon.x(), icon.y());
            } else if (!offer.cost1().isEmpty()) {
                // A buying shop shows what it wants, since it hands over money rather than goods.
                graphics.renderItem(offer.cost1(), icon.x(), icon.y());
                graphics.renderItemDecorations(font, offer.cost1(), icon.x(), icon.y());
            }

            Rect priceRect = new Rect(icon.right() + 3, rect.y() + 3, rect.right() - icon.right() - 5, 9);
            String price = offer.priceCents() > 0L ? Cash.format(offer.priceCents()) : "trueque";
            FantasticTheme.leftText(graphics, font, priceRect, price,
                    available ? FantasticTheme.ACCENT : FantasticTheme.TEXT_DISABLED);

            Rect stockRect = new Rect(icon.right() + 3, rect.y() + 12, rect.right() - icon.right() - 5, 9);
            String stock = !available ? "agotado"
                    : offer.available() == Integer.MAX_VALUE ? "sin limite" : "x" + offer.available();
            FantasticTheme.leftText(graphics, font, stockRect, stock,
                    available ? FantasticTheme.TEXT_MUTED : FantasticTheme.NEGATIVE);

            if (hovered) {
                hint = !available ? "Este trato no esta disponible ahora."
                        : "Clic para comprar uno. Mayus+clic para comprar varios.";
            }
        }

        if (offers.size() > visible) {
            Rect track = new Rect(listArea().right() - 4, listArea().y(), 3, listArea().height());
            FantasticTheme.fill(graphics, track, FantasticTheme.PANEL);
            int handleHeight = Math.max(8, track.height() * visible / offers.size());
            int travel = track.height() - handleHeight;
            int offset = max == 0 ? 0 : travel * scroll / max;
            FantasticTheme.fill(graphics, new Rect(track.x(), track.y() + offset, track.width(), handleHeight),
                    FantasticTheme.ACCENT_DIM);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        FantasticTheme.leftText(graphics, font, new Rect(LIST_X, 6, WINDOW_WIDTH - LIST_X - 6, 10),
                menu.shopName(), FantasticTheme.ACCENT);
        FantasticTheme.leftText(graphics, font,
                new Rect(101, WINDOW_HEIGHT - 12, WINDOW_WIDTH - 107, 10), hint, FantasticTheme.TEXT_MUTED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hint = menu.shopType().description();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderOfferTooltip(graphics, mouseX, mouseY);
    }

    /** Shows the full item under the cursor, since the row only has room for a short label. */
    private void renderOfferTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        var offers = menu.offers();
        int visible = visibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int index = scroll + slot;
            if (index >= offers.size()) {
                break;
            }
            Rect rect = row(slot);
            Rect icon = new Rect(rect.x() + 2, rect.y() + 2, 18, 18);
            if (!icon.contains(mouseX, mouseY)) {
                continue;
            }
            ShopTradeMenu.OfferView offer = offers.get(index);
            ItemStack shown = offer.result().isEmpty() ? offer.cost1() : offer.result();
            if (!shown.isEmpty()) {
                graphics.renderTooltip(font, shown, mouseX, mouseY);
            }
            return;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int visible = visibleRows();
        var offers = menu.offers();
        for (int slot = 0; slot < visible; slot++) {
            int index = scroll + slot;
            if (index >= offers.size()) {
                break;
            }
            if (!row(slot).contains(mouseX, mouseY)) {
                continue;
            }
            if (!offers.get(index).inStock()) {
                return true;
            }
            boolean bulk = hasShiftDown();
            // Sent through the vanilla menu-button channel, which the server already ties to the menu this
            // player has open.
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        ShopTradeMenu.buttonFor(index, bulk));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (listArea().contains(mouseX, mouseY)) {
            scroll = Math.max(0, scroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
