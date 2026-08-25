/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.TextColor
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
import com.fshop.client.screen.AmountScreen;
import com.fshop.economy.CoinEconomy;
import com.fshop.network.PacketHandler;
import com.fshop.network.RequestBrowsePacket;
import com.fshop.shop.PlayerShop;
import com.fshop.shop.ShopOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class ShopViewScreen
extends Screen {
    private static final int SEARCH_W = 92;
    private final PlayerShop shop;
    private final long[] balances;
    private int page;
    private int left;
    private int top;
    private EditBox searchBox;
    private String query = "";
    private final List<Integer> filtered = new ArrayList<Integer>();
    private int openTick;

    /**
     * The player's balance in one currency.
     *
     * <p>{@code balances} comes from the shop-view packet, which still carries only the three coin
     * currencies, so indexing it with the cash id (3) crashed the screen. Cash is read client-side
     * from the value the currency mod pushes, and the coin path is clamped defensively.</p>
     */
    private long balanceOf(int coin) {
        if (CoinEconomy.isCash(coin)) {
            return this.f_96541_ == null || this.f_96541_.f_91074_ == null
                    ? 0L
                    : CoinEconomy.balance((net.minecraft.world.entity.player.Player) this.f_96541_.f_91074_,
                    CoinEconomy.CASH);
        }
        if (this.balances == null || this.balances.length == 0) {
            return 0L;
        }
        return this.balances[Math.max(0, Math.min(this.balances.length - 1, coin))];
    }

    public ShopViewScreen(PlayerShop shop, long[] balances) {
        super((Component)Component.m_237113_((String)shop.getName()));
        this.shop = shop;
        this.balances = balances;
    }

    protected void m_7856_() {
        this.left = (this.f_96543_ - 256) / 2;
        this.top = (this.f_96544_ - 256) / 2;
        this.rebuildFilter();
        if (this.shop.isMain()) {
            int sx = this.left + 256 + 6;
            int sy = this.top + 65;
            this.searchBox = new EditBox(this.f_96547_, sx, sy, 92, 14, (Component)Component.m_237113_((String)"Buscar"));
            this.searchBox.m_94199_(48);
            this.searchBox.m_94144_(this.query);
            this.searchBox.m_257771_((Component)Component.m_237113_((String)"Buscar..."));
            this.searchBox.m_94182_(false);
            this.searchBox.m_94202_(-661816);
            this.searchBox.m_94151_(s -> {
                this.query = s;
                this.page = 0;
                this.rebuildFilter();
            });
            this.m_142416_(this.searchBox);
        } else {
            this.searchBox = null;
        }
    }

    private void rebuildFilter() {
        this.filtered.clear();
        List<ShopOffer> offers = this.shop.getOffers();
        String q = this.query == null ? "" : this.query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < offers.size(); ++i) {
            if (!q.isEmpty() && !offers.get(i).getItem().m_41786_().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            this.filtered.add(i);
        }
    }

    private int perPage() {
        return FShopTextures.contentCells();
    }

    private boolean isOwnShop() {
        return !this.shop.isMain() && this.f_96541_ != null && this.f_96541_.f_91074_ != null && this.shop.getOwner().equals(this.f_96541_.f_91074_.m_20148_());
    }

    private int visibleCount() {
        return this.filtered.size();
    }

    private int pageCount() {
        return Math.max(1, (this.visibleCount() + this.perPage() - 1) / this.perPage());
    }

    private boolean hasPrev() {
        return this.page > 0;
    }

    private boolean hasNext() {
        return this.page < this.pageCount() - 1;
    }

    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
        boolean hn;
        this.m_280273_(g);
        FShopTextures.blitPanel(g, FShopTextures.ITEM_DISPLAY, this.left, this.top);
        if (this.searchBox != null) {
            this.renderSearchPanel(g);
        }
        int coinHov = ShopWidgets.renderCoins(g, this.f_96547_, (Player)this.f_96541_.f_91074_, this.left, this.top, mouseX, mouseY, -1);
        List<ShopOffer> offers = this.shop.getOffers();
        int start = this.page * this.perPage();
        int hovered = -1;
        for (int i = 0; i < this.perPage() && start + i < this.visibleCount(); ++i) {
            int cx = this.left + FShopTextures.contentCellX(i);
            int cy = this.top + FShopTextures.contentCellY(i);
            boolean hov = FShopTheme.inside(mouseX, mouseY, cx, cy, 18, 18);
            int realIdx = this.filtered.get(start + i);
            if (hov) {
                g.m_280509_(cx, cy, cx + 18, cy + 18, 1719848263);
                hovered = realIdx;
            }
            ShopOffer offer = offers.get(realIdx);
            int ix = this.left + FShopTextures.contentItemX(i);
            int iy = this.top + FShopTextures.contentItemY(i);
            g.m_280203_(offer.displayStack(1), ix, iy);
            if (offer.getBundle() > 1) {
                FShopTheme.drawCount(g, this.f_96547_, ix, iy, Integer.toString(offer.getBundle()));
            }
            if (offer.isInfinite() || offer.getStock() >= offer.getBundle()) continue;
            g.m_280509_(ix, iy, ix + 16, iy + 16, -1713426888);
        }
        boolean homeHov = FShopTextures.inCell(mouseX, mouseY, this.left, this.top, FShopTextures.HOME_CELL);
        FShopTextures.hoverCell(g, this.left, this.top, FShopTextures.HOME_CELL, homeHov);
        boolean hp = this.hasPrev() && FShopTextures.inCell(mouseX, mouseY, this.left, this.top, FShopTextures.PAGE_PREV_CELL);
        boolean bl = hn = this.hasNext() && FShopTextures.inCell(mouseX, mouseY, this.left, this.top, FShopTextures.PAGE_NEXT_CELL);
        if (this.hasPrev()) {
            FShopTextures.blitIcon(g, FShopTextures.BACK_BUTTON, this.left, this.top, FShopTextures.PAGE_PREV_CELL);
            FShopTextures.hoverCell(g, this.left, this.top, FShopTextures.PAGE_PREV_CELL, hp);
        }
        if (this.hasNext()) {
            FShopTextures.blitIcon(g, FShopTextures.NEXT_BUTTON, this.left, this.top, FShopTextures.PAGE_NEXT_CELL);
            FShopTextures.hoverCell(g, this.left, this.top, FShopTextures.PAGE_NEXT_CELL, hn);
        }
        if (this.pageCount() > 1) {
            FShopTheme.drawPageBadge(g, this.f_96547_, this.left + 128, this.top + 240, this.page + 1 + "/" + this.pageCount());
        }
        if (this.searchBox != null && this.visibleCount() == 0) {
            g.m_280137_(this.f_96547_, "Sin resultados", this.left + 128, this.top + 105, -3559286);
        }
        super.m_88315_(g, mouseX, mouseY, partial);
        if (hovered >= 0) {
            this.offerTooltip(g, offers.get(hovered), mouseX, mouseY);
        } else if (coinHov >= 0) {
            this.walletTip(g, mouseX, mouseY, coinHov);
        } else if (homeHov) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.nav.back_to_list"));
        } else if (hp) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.nav.prev"));
        } else if (hn) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.nav.next"));
        }
    }

    private void renderSearchPanel(GuiGraphics g) {
        int px = this.left + 256 + 2;
        int py = this.top + 63;
        int pw = 100;
        int ph = 18;
        g.m_280509_(px, py, px + pw, py + ph, -1306256364);
        g.m_280509_(px, py, px + pw, py + 1, 1728046768);
        g.m_280509_(px, py + ph - 1, px + pw, py + ph, 0x66000000);
        g.m_280509_(px, py, px + 1, py + ph, 1728046768);
        g.m_280509_(px + pw - 1, py, px + pw, py + ph, 0x66000000);
    }

    private void tip(GuiGraphics g, int mouseX, int mouseY, Component c) {
        List<Component> t = new ArrayList<Component>();
        t.add(c);
        g.m_280666_(this.f_96547_, t, mouseX, mouseY);
    }

    private void walletTip(GuiGraphics g, int mouseX, int mouseY, int coin) {
        List<Component> t = new ArrayList<Component>();
        t.add(Component.m_237110_((String)"fshop.gui.wallet", (Object[])new Object[]{this.balanceOf(coin), Component.m_237115_((String)CoinEconomy.coinKey(coin))}));
        t.add(Component.m_237115_((String)"fshop.gui.wallet_hint").m_130940_(ChatFormatting.DARK_GRAY));
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
        if (offer.getBundle() > 1) {
            t.add((Component)Component.m_237110_((String)"fshop.gui.per_pack", (Object[])new Object[]{offer.getBundle()}).m_130940_(ChatFormatting.AQUA));
        }
        int cc = CoinEconomy.coinColor(offer.getCoin());
        t.add((Component)Component.m_237110_((String)(offer.getBundle() > 1 ? "fshop.gui.price_pack" : "fshop.gui.buy_price"), (Object[])new Object[]{CoinEconomy.formatAmount(offer.getCoin(), offer.getUnitPrice()), Component.m_237115_((String)CoinEconomy.coinKey(offer.getCoin()))}).m_130938_(s -> s.m_131148_(TextColor.m_131266_((int)cc))));
        t.add((Component)Component.m_237110_((String)"fshop.gui.your_balance", (Object[])new Object[]{this.balanceOf(offer.getCoin()), Component.m_237115_((String)CoinEconomy.coinKey(offer.getCoin()))}).m_130940_(ChatFormatting.GRAY));
        if (offer.isInfinite()) {
            t.add((Component)Component.m_237115_((String)"fshop.gui.stock_inf").m_130940_(ChatFormatting.AQUA));
        } else {
            t.add((Component)Component.m_237110_((String)"fshop.gui.stock", (Object[])new Object[]{offer.getStock()}).m_130940_(offer.getStock() > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        t.add((Component)Component.m_237119_());
        if (this.isOwnShop()) {
            t.add((Component)Component.m_237115_((String)"fshop.gui.own_shop").m_130940_(ChatFormatting.RED));
        } else {
            t.add((Component)Component.m_237115_((String)"fshop.gui.click_to_buy").m_130940_(ChatFormatting.GREEN));
        }
        g.m_280666_(this.f_96547_, t, mouseX, mouseY);
    }

    public boolean m_6375_(double mx, double my, int button) {
        List<ShopOffer> offers = this.shop.getOffers();
        int start = this.page * this.perPage();
        if (button == 0) {
            for (int i = 0; i < this.perPage() && start + i < this.visibleCount(); ++i) {
                int cy;
                int cx = this.left + FShopTextures.contentCellX(i);
                if (!FShopTheme.inside(mx, my, cx, cy = this.top + FShopTextures.contentCellY(i), 18, 18)) continue;
                int idx = this.filtered.get(start + i);
                ShopOffer o = offers.get(idx);
                if (this.isOwnShop()) {
                    Sfx.click();
                } else if (o.isInfinite() || o.getStock() >= o.getBundle()) {
                    Sfx.select();
                    this.f_96541_.m_91152_((Screen)new AmountScreen(this.shop, idx, this.balances));
                }
                return true;
            }
            if (FShopTextures.inCell(mx, my, this.left, this.top, FShopTextures.HOME_CELL)) {
                Sfx.click();
                PacketHandler.sendToServer(new RequestBrowsePacket());
                return true;
            }
            if (this.hasPrev() && FShopTextures.inCell(mx, my, this.left, this.top, FShopTextures.PAGE_PREV_CELL)) {
                --this.page;
                Sfx.page();
                return true;
            }
            if (this.hasNext() && FShopTextures.inCell(mx, my, this.left, this.top, FShopTextures.PAGE_NEXT_CELL)) {
                ++this.page;
                Sfx.page();
                return true;
            }
        }
        return super.m_6375_(mx, my, button);
    }

    public boolean m_6050_(double mx, double my, double delta) {
        if (delta < 0.0 && this.hasNext()) {
            ++this.page;
            Sfx.page();
            return true;
        }
        if (delta > 0.0 && this.hasPrev()) {
            --this.page;
            Sfx.page();
            return true;
        }
        return super.m_6050_(mx, my, delta);
    }

    public void m_86600_() {
        super.m_86600_();
        if (this.openTick == 0) {
            Sfx.spark(1.0f);
        }
        ++this.openTick;
    }

    public boolean m_7043_() {
        return false;
    }
}

