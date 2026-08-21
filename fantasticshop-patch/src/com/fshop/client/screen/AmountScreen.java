/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.gui.GuiGraphics
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
import com.fshop.economy.CoinEconomy;
import com.fshop.network.BuyPacket;
import com.fshop.network.OpenShopRequestPacket;
import com.fshop.network.PacketHandler;
import com.fshop.shop.PlayerShop;
import com.fshop.shop.ShopOffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class AmountScreen
extends Screen {
    private static final int MAX_AMOUNT = 99999;
    private final PlayerShop shop;
    private final int offerIndex;
    private final long[] balances;
    private final ShopOffer offer;
    private int amount = 1;
    private int left;
    private int top;
    private int heldMinus = -1;
    private int heldPlus = -1;
    private int holdTicks;

    public AmountScreen(PlayerShop shop, int offerIndex, long[] balances) {
        super((Component)Component.m_237115_((String)"fshop.gui.amount.title"));
        this.shop = shop;
        this.offerIndex = offerIndex;
        this.balances = balances;
        this.offer = shop.getOffers().get(offerIndex);
    }

    protected void m_7856_() {
        this.left = (this.f_96543_ - 256) / 2;
        this.top = (this.f_96544_ - 256) / 2;
        this.amount = this.clamp(1);
    }

    private int bundle() {
        return Math.max(1, this.offer.getBundle());
    }

    private int step(int i) {
        return switch (i) {
            case 0 -> 1;
            case 1 -> 32;
            default -> 64;
        };
    }

    private int maxAmount() {
        if (this.offer.isInfinite()) {
            return 99999;
        }
        return Math.max(1, this.offer.getStock() / this.bundle());
    }

    private int clamp(int v) {
        return Math.max(1, Math.min(v, this.maxAmount()));
    }

    private long totalItems() {
        return (long)this.bundle() * (long)this.amount;
    }

    private long total() {
        return this.offer.getUnitPrice() * (long)this.amount;
    }

    private boolean canAfford() {
        return this.total() <= this.balances[this.offer.getCoin()];
    }

    private boolean inStock() {
        return this.offer.isInfinite() || (long)this.offer.getStock() >= this.totalItems();
    }

    private boolean canBuy() {
        return this.canAfford() && this.inStock();
    }

    private boolean inBox(double mx, double my, int[] box) {
        return FShopTheme.inside(mx, my, this.left + box[0], this.top + box[1], box[2] - box[0], box[3] - box[1]);
    }

    private void hoverBox(GuiGraphics g, int mouseX, int mouseY, int[] box) {
        if (this.inBox(mouseX, mouseY, box)) {
            g.m_280509_(this.left + box[0], this.top + box[1], this.left + box[2], this.top + box[3], 0x55FFFFFF);
        }
    }

    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.m_280273_(g);
        FShopTextures.blitPanel(g, FShopTextures.CONFIRMATION, this.left, this.top);
        for (int[] box : FShopTextures.MINUS_CELLS) {
            this.hoverBox(g, mouseX, mouseY, box);
        }
        for (int[] box : FShopTextures.PLUS_CELLS) {
            this.hoverBox(g, mouseX, mouseY, box);
        }
        this.hoverBox(g, mouseX, mouseY, FShopTextures.NO_BOX);
        if (this.canBuy()) {
            this.hoverBox(g, mouseX, mouseY, FShopTextures.YES_BOX);
        } else {
            int[] y = FShopTextures.YES_BOX;
            g.m_280509_(this.left + y[0], this.top + y[1], this.left + y[2], this.top + y[3], 0x77000000);
        }
        this.hoverBox(g, mouseX, mouseY, FShopTextures.SET_STACK_BOX);
        int coinHov = ShopWidgets.renderCoins(g, this.f_96547_, (Player)this.f_96541_.f_91074_, this.left, this.top, mouseX, mouseY, this.offer.getCoin());
        ItemStack stack = this.offer.displayStack(1);
        int ix = this.left + 128 - 8;
        int iy = this.top + 96 - 8;
        g.m_280203_(stack, ix, iy);
        FShopTheme.drawCount(g, this.f_96547_, ix, iy, Long.toString(this.totalItems()));
        super.m_88315_(g, mouseX, mouseY, partial);
        this.renderTooltips(g, mouseX, mouseY, coinHov);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int coinHov) {
        if (this.inBox(mouseX, mouseY, FShopTextures.ITEM_FRAME)) {
            this.itemBreakdownTooltip(g, mouseX, mouseY);
            return;
        }
        for (int i = 0; i < 3; ++i) {
            if (this.inBox(mouseX, mouseY, FShopTextures.MINUS_CELLS[i])) {
                this.tip(g, mouseX, mouseY, (Component)Component.m_237110_((String)"fshop.gui.amount.remove", (Object[])new Object[]{this.step(i)}));
                return;
            }
            if (!this.inBox(mouseX, mouseY, FShopTextures.PLUS_CELLS[i])) continue;
            this.tip(g, mouseX, mouseY, (Component)Component.m_237110_((String)"fshop.gui.amount.add", (Object[])new Object[]{this.step(i)}));
            return;
        }
        if (this.inBox(mouseX, mouseY, FShopTextures.SET_STACK_BOX)) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.amount.set64"));
            return;
        }
        if (this.inBox(mouseX, mouseY, FShopTextures.NO_BOX)) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.amount.tip_no"));
            return;
        }
        if (this.inBox(mouseX, mouseY, FShopTextures.YES_BOX)) {
            this.tip(g, mouseX, mouseY, (Component)(this.canBuy() ? Component.m_237115_((String)"fshop.gui.amount.tip_yes") : Component.m_237115_((String)(this.canAfford() ? "fshop.msg.out_of_stock" : "fshop.msg.cannot_afford"))));
            return;
        }
        if (coinHov >= 0) {
            List<Component> wt = new ArrayList<Component>();
            wt.add(Component.m_237110_((String)"fshop.gui.wallet", (Object[])new Object[]{this.balances[coinHov], Component.m_237115_((String)CoinEconomy.coinKey(coinHov))}));
            wt.add(Component.m_237115_((String)"fshop.gui.wallet_hint").m_130940_(ChatFormatting.DARK_GRAY));
            g.m_280666_(this.f_96547_, wt, mouseX, mouseY);
        }
    }

    private void tip(GuiGraphics g, int mouseX, int mouseY, Component c) {
        List<Component> t = new ArrayList<Component>();
        t.add(c);
        g.m_280666_(this.f_96547_, t, mouseX, mouseY);
    }

    private void itemBreakdownTooltip(GuiGraphics g, int mouseX, int mouseY) {
        MutableComponent coin = Component.m_237115_((String)CoinEconomy.coinKey(this.offer.getCoin()));
        List<Component> t = new ArrayList<Component>();
        ItemStack stack = this.offer.displayStack(1);
        t.add(stack.m_41786_());
        List lines = stack.m_41651_((Player)this.f_96541_.f_91074_, (TooltipFlag)TooltipFlag.Default.f_256752_);
        for (int i = 1; i < lines.size(); ++i) {
            t.add((Component)lines.get(i));
        }
        if (stack.m_41763_()) {
            int max = stack.m_41776_();
            int remaining = max - stack.m_41773_();
            t.add(Component.m_237110_((String)"fshop.gui.durability", (Object[])new Object[]{remaining, max}).m_130940_(ChatFormatting.GRAY));
        }
        t.add(Component.m_237110_((String)"fshop.gui.amount.quantity", (Object[])new Object[]{this.totalItems()}).m_130940_(ChatFormatting.GRAY));
        if (this.bundle() > 1) {
            t.add(Component.m_237110_((String)"fshop.gui.amount.packs", (Object[])new Object[]{this.amount, this.bundle()}).m_130940_(ChatFormatting.DARK_GRAY));
        }
        int cc = CoinEconomy.coinColor(this.offer.getCoin());
        t.add(Component.m_237110_((String)"fshop.gui.buy_price", (Object[])new Object[]{CoinEconomy.formatAmount(this.offer.getCoin(), this.offer.getUnitPrice()), coin}).m_130938_(s -> s.m_131148_(TextColor.m_131266_((int)cc))));
        t.add(Component.m_237110_((String)"fshop.gui.total_n", (Object[])new Object[]{this.total()}).m_130938_(s -> s.m_131148_(TextColor.m_131266_((int)(this.canAfford() ? cc : -2150856)))));
        t.add(Component.m_237110_((String)"fshop.gui.your_balance", (Object[])new Object[]{this.balances[this.offer.getCoin()], coin}).m_130940_(this.canAfford() ? ChatFormatting.DARK_GRAY : ChatFormatting.RED));
        if (!this.inStock()) {
            t.add(Component.m_237119_());
            t.add(Component.m_237115_((String)"fshop.msg.out_of_stock").m_130940_(ChatFormatting.RED));
        } else if (!this.canAfford()) {
            t.add(Component.m_237119_());
            t.add(Component.m_237115_((String)"fshop.msg.cannot_afford").m_130940_(ChatFormatting.RED));
        }
        g.m_280666_(this.f_96547_, t, mouseX, mouseY);
    }

    public boolean m_6375_(double mx, double my, int button) {
        if (button != 0) {
            return super.m_6375_(mx, my, button);
        }
        for (int i = 0; i < 3; ++i) {
            if (this.inBox(mx, my, FShopTextures.MINUS_CELLS[i])) {
                this.amount = this.clamp(this.amount - this.step(i));
                this.heldMinus = i;
                this.heldPlus = -1;
                this.holdTicks = 0;
                Sfx.step();
                return true;
            }
            if (!this.inBox(mx, my, FShopTextures.PLUS_CELLS[i])) continue;
            this.amount = this.clamp(this.amount + this.step(i));
            this.heldPlus = i;
            this.heldMinus = -1;
            this.holdTicks = 0;
            Sfx.step();
            return true;
        }
        if (this.inBox(mx, my, FShopTextures.SET_STACK_BOX)) {
            this.amount = this.clamp(64);
            Sfx.click();
            return true;
        }
        if (this.inBox(mx, my, FShopTextures.NO_BOX)) {
            Sfx.click();
            PacketHandler.sendToServer(new OpenShopRequestPacket(this.shop.getId()));
            return true;
        }
        if (this.canBuy() && this.inBox(mx, my, FShopTextures.YES_BOX)) {
            Sfx.success();
            PacketHandler.sendToServer(new BuyPacket(this.shop.getId(), this.offerIndex, this.amount));
            return true;
        }
        return super.m_6375_(mx, my, button);
    }

    public boolean m_6348_(double mx, double my, int button) {
        this.heldMinus = -1;
        this.heldPlus = -1;
        this.holdTicks = 0;
        return super.m_6348_(mx, my, button);
    }

    public void m_86600_() {
        int interval;
        int held;
        super.m_86600_();
        int n = held = this.heldMinus >= 0 ? this.heldMinus : this.heldPlus;
        if (held < 0) {
            this.holdTicks = 0;
            return;
        }
        ++this.holdTicks;
        if (this.holdTicks < 6) {
            return;
        }
        int t = this.holdTicks - 6;
        int n2 = interval = t > 40 ? 1 : 2;
        if (this.holdTicks % interval != 0) {
            return;
        }
        int mult = t > 70 ? 16 : (t > 40 ? 4 : 1);
        int delta = this.step(held) * mult;
        this.amount = this.heldMinus >= 0 ? this.clamp(this.amount - delta) : this.clamp(this.amount + delta);
    }

    public boolean m_7043_() {
        return false;
    }
}

