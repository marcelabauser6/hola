/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.item.TooltipFlag$Default
 */
package com.fshop.client.screen;

import com.fshop.client.FShopTextures;
import com.fshop.client.FShopTheme;
import com.fshop.client.Sfx;
import com.fshop.client.ShopWidgets;
import com.fshop.client.screen.PriceInputScreen;
import com.fshop.economy.CoinEconomy;
import com.fshop.network.CollectPacket;
import com.fshop.network.PacketHandler;
import com.fshop.network.RemoveOfferPacket;
import com.fshop.network.StockRequestPacket;
import com.fshop.shop.PlayerShop;
import com.fshop.shop.ShopOffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ShopManageScreen
extends Screen {
    private final PlayerShop shop;
    private int page;
    private int left;
    private int top;

    public ShopManageScreen(PlayerShop shop) {
        super((Component)Component.m_237113_((String)shop.getName()));
        this.shop = shop;
    }

    protected void m_7856_() {
        this.left = (this.f_96543_ - 256) / 2;
        this.top = (this.f_96544_ - 256) / 2;
    }

    private int perPage() {
        return FShopTextures.contentCells();
    }

    private int pageCount() {
        return Math.max(1, (this.shop.getOffers().size() + this.perPage() - 1) / this.perPage());
    }

    private int earnCellX(int coin) {
        return this.left + FShopTextures.earnCellX(coin);
    }

    private int earnCellY() {
        return this.top + FShopTextures.earnCellY();
    }

    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.m_280273_(g);
        FShopTextures.blitPanel(g, FShopTextures.SELL_MENU, this.left, this.top);
        List<ShopOffer> offers = this.shop.getOffers();
        int start = this.page * this.perPage();
        int hovered = -1;
        for (int i = 0; i < this.perPage() && start + i < offers.size(); ++i) {
            int cy;
            int cx = this.left + FShopTextures.contentCellX(i);
            boolean hov = FShopTheme.inside(mouseX, mouseY, cx, cy = this.top + FShopTextures.contentCellY(i), 18, 18);
            if (hov) {
                g.m_280509_(cx, cy, cx + 18, cy + 18, 1728041546);
                hovered = start + i;
            }
            ShopOffer offer = offers.get(start + i);
            int ix = this.left + FShopTextures.contentItemX(i);
            int iy = this.top + FShopTextures.contentItemY(i);
            g.m_280203_(offer.displayStack(1), ix, iy);
            FShopTheme.drawCount(g, this.f_96547_, ix, iy, offer.isInfinite() ? "\u221e" : Integer.toString(offer.getStock()));
        }
        boolean homeHov = FShopTextures.inCell(mouseX, mouseY, this.left, this.top, FShopTextures.HOME_CELL);
        FShopTextures.hoverCell(g, this.left, this.top, FShopTextures.HOME_CELL, homeHov);
        // Four currencies, not three. Stopping at 3 meant a shop whose only pending money was cash
        // drew no cell at all, so the owner had nothing to click and no way to collect it from here.
        int earnHov = -1;
        for (int c = 0; c < CoinEconomy.TYPES; ++c) {
            ItemStack coin;
            if (this.shop.getPendingEarnings(c) <= 0L) continue;
            int cx = this.earnCellX(c);
            int cy = this.earnCellY();
            g.m_280163_(FShopTextures.EMPTY_SLOT, cx + 1, cy + 1, 0.0f, 0.0f, 16, 16, 16, 16);
            if (FShopTheme.inside(mouseX, mouseY, cx, cy, 18, 18)) {
                g.m_280509_(cx, cy, cx + 18, cy + 18, 1719848263);
                earnHov = c;
            }
            if (CoinEconomy.isCash(c)) {
                // No item icon for cash: the symbol stands in, and the amount is formatted with its
                // two decimals rather than printed as a raw count of cents.
                String symbol = CoinEconomy.cashSymbol();
                g.m_280056_(this.f_96547_, symbol,
                        cx + 1 + (16 - this.f_96547_.m_92895_(symbol)) / 2, cy + 3,
                        CoinEconomy.coinColor(c), true);
                FShopTheme.drawCount(g, this.f_96547_, cx + 1, cy + 1,
                        CoinEconomy.formatAmount(c, this.shop.getPendingEarnings(c)));
                continue;
            }
            if ((coin = CoinEconomy.coinIcon(c)).m_41619_()) continue;
            g.m_280203_(coin, cx + 1, cy + 1);
            FShopTheme.drawCount(g, this.f_96547_, cx + 1, cy + 1, Long.toString(this.shop.getPendingEarnings(c)));
        }
        boolean hp = false;
        boolean hn = false;
        if (this.pageCount() > 1) {
            hp = this.page > 0 && FShopTextures.inCell(mouseX, mouseY, this.left, this.top, FShopTextures.PREV_CELL);
            boolean bl = hn = this.page < this.pageCount() - 1 && FShopTextures.inCell(mouseX, mouseY, this.left, this.top, FShopTextures.NEXT_CELL);
            if (this.page > 0) {
                FShopTextures.blitIcon(g, FShopTextures.BACK_BUTTON, this.left, this.top, FShopTextures.PREV_CELL);
                FShopTextures.hoverCell(g, this.left, this.top, FShopTextures.PREV_CELL, hp);
            }
            if (this.page < this.pageCount() - 1) {
                FShopTextures.blitIcon(g, FShopTextures.NEXT_BUTTON, this.left, this.top, FShopTextures.NEXT_CELL);
                FShopTextures.hoverCell(g, this.left, this.top, FShopTextures.NEXT_CELL, hn);
            }
        }
        int hoveredSlot = ShopWidgets.renderInventory(g, this.f_96547_, this.f_96541_.f_91074_.m_150109_(), this.left, this.top, mouseX, mouseY, true);
        // Say when the shop cannot sell. The account number already crossed the wire but nothing on
        // the client looked at it, so an owner whose shop was frozen saw a completely normal screen
        // and no reason why nobody could buy from them.
        if (!this.shop.isMain() && !this.shop.canSell()) {
            Component frozen = Component.m_237115_((String)"fshop.gui.frozen").m_130940_(ChatFormatting.RED);
            g.m_280430_(this.f_96547_, frozen,
                    this.left + 8, this.top + FShopTextures.earnCellY() - 11, -1);
        }
        super.m_88315_(g, mouseX, mouseY, partial);
        if (hovered >= 0) {
            this.offerTooltip(g, offers.get(hovered), mouseX, mouseY);
        } else if (hoveredSlot >= 0) {
            List<Component> t = new ArrayList<Component>();
            t.add(this.f_96541_.f_91074_.m_150109_().m_8020_(hoveredSlot).m_41786_());
            t.add(Component.m_237115_((String)"fshop.gui.click_to_stock").m_130940_(ChatFormatting.GREEN));
            g.m_280666_(this.f_96547_, t, mouseX, mouseY);
        } else if (earnHov >= 0) {
            List<Component> t = new ArrayList<Component>();
            t.add(Component.m_237110_((String)"fshop.gui.manage.earnings", (Object[])new Object[]{this.shop.getPendingEarnings(earnHov), Component.m_237115_((String)CoinEconomy.coinKey(earnHov))}).m_130940_(ChatFormatting.GOLD));
            t.add(Component.m_237115_((String)"fshop.gui.manage.collect_tip").m_130940_(ChatFormatting.GREEN));
            g.m_280666_(this.f_96547_, t, mouseX, mouseY);
        } else if (homeHov) {
            this.singleTip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.close"));
        } else if (hp) {
            this.singleTip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.nav.prev"));
        } else if (hn) {
            this.singleTip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.nav.next"));
        }
    }

    private void singleTip(GuiGraphics g, int mouseX, int mouseY, Component c) {
        List<Component> t = new ArrayList<Component>();
        t.add(c);
        g.m_280666_(this.f_96547_, t, mouseX, mouseY);
    }

    private void appendItemDetails(List<Component> t, ItemStack stack) {
        List lines = stack.m_41651_((Player)this.f_96541_.f_91074_, (TooltipFlag)TooltipFlag.Default.f_256752_);
        for (int i = 1; i < lines.size(); ++i) {
            t.add((Component)lines.get(i));
        }
        if (stack.m_41763_()) {
            int max = stack.m_41776_();
            int remaining = max - stack.m_41773_();
            t.add((Component)Component.m_237110_((String)"fshop.gui.durability", (Object[])new Object[]{remaining, max}).m_130940_(ChatFormatting.GRAY));
        }
    }

    private void offerTooltip(GuiGraphics g, ShopOffer offer, int mouseX, int mouseY) {
        List<Component> t = new ArrayList<Component>();
        ItemStack stack = offer.displayStack(1);
        t.add(stack.m_41786_());
        this.appendItemDetails(t, stack);
        t.add((Component)Component.m_237110_((String)"fshop.gui.buy_price", (Object[])new Object[]{CoinEconomy.formatAmount(offer.getCoin(), offer.getUnitPrice()), Component.m_237115_((String)CoinEconomy.coinKey(offer.getCoin()))}).m_130940_(ChatFormatting.GREEN));
        t.add((Component)Component.m_237110_((String)"fshop.gui.stock", (Object[])new Object[]{offer.getStock()}).m_130940_(ChatFormatting.GRAY));
        t.add((Component)Component.m_237119_());
        t.add((Component)Component.m_237115_((String)"fshop.gui.left_edit_price").m_130940_(ChatFormatting.AQUA));
        t.add((Component)Component.m_237115_((String)"fshop.gui.right_remove").m_130940_(ChatFormatting.RED));
        g.m_280666_(this.f_96547_, t, mouseX, mouseY);
    }

    public boolean m_6375_(double mx, double my, int button) {
        List<ShopOffer> offers = this.shop.getOffers();
        int start = this.page * this.perPage();
        for (int i = 0; i < this.perPage() && start + i < offers.size(); ++i) {
            int cy;
            int cx = this.left + FShopTextures.contentCellX(i);
            if (!FShopTheme.inside(mx, my, cx, cy = this.top + FShopTextures.contentCellY(i), 18, 18)) continue;
            int idx = start + i;
            if (button == 1) {
                Sfx.click();
                PacketHandler.sendToServer(new RemoveOfferPacket(this.shop.getId(), idx));
            } else if (button == 0) {
                Sfx.select();
                this.f_96541_.m_91152_((Screen)new PriceInputScreen(this.shop, PriceInputScreen.Mode.EDIT, idx, offers.get(idx).getUnitPrice(), offers.get(idx).getCoin(), offers.get(idx).getBundle()));
            }
            return true;
        }
        if (button == 0) {
            int slot = ShopWidgets.slotAt(this.f_96541_.f_91074_.m_150109_(), this.left, this.top, mx, my);
            if (slot >= 0) {
                Sfx.select();
                PacketHandler.sendToServer(new StockRequestPacket(this.shop.getId(), slot));
                return true;
            }
            if (FShopTextures.inCell(mx, my, this.left, this.top, FShopTextures.HOME_CELL)) {
                Sfx.click();
                this.m_7379_();
                return true;
            }
            // Must match the render loop's bound, or the cash cell would be drawn but not clickable.
            for (int c = 0; c < CoinEconomy.TYPES; ++c) {
                if (this.shop.getPendingEarnings(c) <= 0L || !FShopTheme.inside(mx, my, this.earnCellX(c), this.earnCellY(), 18, 18)) continue;
                Sfx.success();
                PacketHandler.sendToServer(new CollectPacket(this.shop.getId()));
                return true;
            }
            if (this.page > 0 && FShopTextures.inCell(mx, my, this.left, this.top, FShopTextures.PREV_CELL)) {
                --this.page;
                Sfx.page();
                return true;
            }
            if (this.page < this.pageCount() - 1 && FShopTextures.inCell(mx, my, this.left, this.top, FShopTextures.NEXT_CELL)) {
                ++this.page;
                Sfx.page();
                return true;
            }
        }
        return super.m_6375_(mx, my, button);
    }

    public boolean m_7043_() {
        return false;
    }
}

