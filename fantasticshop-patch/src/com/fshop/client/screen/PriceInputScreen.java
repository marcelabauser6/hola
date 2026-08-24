/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.Tooltip
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.resources.language.I18n
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.world.item.ItemStack
 */
package com.fshop.client.screen;

import java.util.List;

import com.fshop.client.FShopTextures;
import com.fshop.client.FShopTheme;
import com.fshop.client.Sfx;
import com.fshop.economy.CoinEconomy;
import com.fshop.network.AddOfferPacket;
import com.fshop.network.PacketHandler;
import com.fshop.network.RequestManagePacket;
import com.fshop.network.SetPricePacket;
import com.fshop.shop.PlayerShop;
import com.fshop.shop.ShopOffer;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;

public final class PriceInputScreen
extends Screen {
    private static final long CAP = 999999999L;
    private static final int[] STEPS = new int[]{1, 10, 100};
    private final PlayerShop shop;
    private final Mode mode;
    private final int ref;
    private int coin;
    private ItemStack itemStack = ItemStack.f_41583_;
    private String buf;
    private String bundleBuf;
    private int left;
    private int top;
    private int heldMinus = -1;
    private int heldPlus = -1;
    private int holdTicks;
    private EditBox bundleBox;
    private int bundleW;
    private int bundleLabelH;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int titleY;
    private int hintY;

    public PriceInputScreen(PlayerShop shop, Mode mode, int ref, long initial, int coin, int bundle) {
        super((Component)Component.m_237115_((String)(mode == Mode.ADD ? "fshop.gui.price.add" : "fshop.gui.price.edit")));
        this.shop = shop;
        this.mode = mode;
        this.ref = ref;
        this.coin = coin;
        // Show an existing cash price the way it will be typed, not as a raw count of cents.
        this.buf = CoinEconomy.isCash(coin)
                ? PriceInputScreen.unitsOf(Math.max(1L, initial))
                : Long.toString(Math.max(1L, initial));
        this.bundleBuf = Integer.toString(Math.max(1, bundle));
    }

    protected void m_7856_() {
        int py;
        this.left = (this.f_96543_ - 256) / 2;
        this.top = (this.f_96544_ - 256) / 2;
        if (this.mode == Mode.ADD) {
            this.itemStack = this.f_96541_.f_91074_.m_150109_().m_8020_(this.ref).m_41777_();
        } else if (this.ref >= 0 && this.ref < this.shop.getOffers().size()) {
            this.itemStack = this.shop.getOffers().get(this.ref).displayStack(1);
        }
        if (!this.itemStack.m_41619_()) {
            this.itemStack.m_41764_(1);
        }
        String label = I18n.m_118938_((String)"fshop.gui.price.bundle_label", (Object[])new Object[0]);
        String oneTxt = I18n.m_118938_((String)"fshop.gui.price.bundle_one", (Object[])new Object[0]);
        String stackTxt = I18n.m_118938_((String)"fshop.gui.price.bundle_stack", (Object[])new Object[0]);
        String hint = I18n.m_118938_((String)"fshop.gui.price.bundle_hint", (Object[])new Object[0]);
        int longestButton = Math.max(this.f_96547_.m_92895_(oneTxt), this.f_96547_.m_92895_(stackTxt)) + 12;
        this.bundleW = Math.max(74, Math.max(this.f_96547_.m_92895_(label), longestButton));
        int px = this.left + 256 + 6;
        this.titleY = py = this.top + 72;
        this.hintY = py + 11;
        this.bundleLabelH = this.f_96547_.m_92920_(hint, this.bundleW);
        int fieldY = this.hintY + this.bundleLabelH + 4;
        this.panelX = px - 5;
        this.panelY = py - 5;
        this.panelW = this.bundleW + 10;
        this.panelH = fieldY + 18 + 18 + 16 + 5 - this.panelY;
        this.bundleBox = new EditBox(this.f_96547_, px, fieldY, this.bundleW, 14, (Component)Component.m_237115_((String)"fshop.gui.price.bundle_field"));
        this.bundleBox.m_94199_(5);
        this.bundleBox.m_94144_(this.bundleBuf);
        this.bundleBox.m_94182_(true);
        this.bundleBox.m_94202_(-661816);
        this.bundleBox.m_257771_((Component)Component.m_237115_((String)"fshop.gui.price.bundle_field"));
        this.bundleBox.m_94151_(s -> {
            this.bundleBuf = s;
        });
        this.bundleBox.m_257544_(Tooltip.m_257550_((Component)Component.m_237115_((String)"fshop.gui.price.bundle_tip")));
        this.m_142416_(this.bundleBox);
        this.m_142416_(Button.m_253074_((Component)Component.m_237115_((String)"fshop.gui.price.bundle_one"), b -> {
            this.bundleBuf = "1";
            this.bundleBox.m_94144_("1");
            Sfx.click();
        }).m_257505_(Tooltip.m_257550_((Component)Component.m_237115_((String)"fshop.gui.price.bundle_one_tip"))).m_252987_(px, fieldY + 18, this.bundleW, 16).m_253136_());
        this.m_142416_(Button.m_253074_((Component)Component.m_237115_((String)"fshop.gui.price.bundle_stack"), b -> {
            int max = ShopOffer.fullStack(this.itemStack);
            this.bundleBuf = Integer.toString(max);
            this.bundleBox.m_94144_(this.bundleBuf);
            Sfx.click();
        }).m_257505_(Tooltip.m_257550_((Component)Component.m_237115_((String)"fshop.gui.price.bundle_stack_tip"))).m_252987_(px, fieldY + 36, this.bundleW, 16).m_253136_());
    }

    /**
     * The price the offer will be saved with, always in the currency's raw unit.
     *
     * <p>Coins are counted, so the buffer is a plain integer. Cash is stored in cents, and the player
     * types it in <em>units</em> - {@code 12.50} - because typing a price of twelve fifty as the
     * integer {@code 1250} is not something anyone should have to work out. So for cash the buffer
     * holds what was typed and this converts it, which is also why the buffer is the only place the
     * decimal point ever exists: everything downstream stays integer arithmetic.</p>
     */
    private long price() {
        if (CoinEconomy.isCash(this.coin)) {
            return PriceInputScreen.centsOf(this.buf);
        }
        try {
            return Math.max(1L, Math.min(CAP, Long.parseLong(this.buf)));
        }
        catch (NumberFormatException e) {
            return 1L;
        }
    }

    /** Parses a typed units string such as {@code "12"}, {@code "12."} or {@code "12.5"} into cents. */
    private static long centsOf(String text) {
        String cleaned = text == null ? "" : text.replace(',', '.').trim();
        if (cleaned.isEmpty()) {
            return 1L;
        }
        int dot = cleaned.indexOf('.');
        String whole = dot < 0 ? cleaned : cleaned.substring(0, dot);
        String fraction = dot < 0 ? "" : cleaned.substring(dot + 1);
        if (whole.isEmpty()) {
            whole = "0";
        }
        // Pad so "5.5" is fifty cents past five, not five and five cents.
        while (fraction.length() < 2) {
            fraction = fraction + "0";
        }
        if (fraction.length() > 2) {
            fraction = fraction.substring(0, 2);
        }
        try {
            long units = Long.parseLong(whole);
            long cents = Long.parseLong(fraction);
            long total = units * CoinEconomy.CASH_SCALE + cents;
            return Math.max(1L, Math.min(CAP, total));
        }
        catch (NumberFormatException e) {
            return 1L;
        }
    }

    /** Renders cents back into the typed form, so the +/- buttons and typing agree. */
    private static String unitsOf(long cents) {
        long safe = Math.max(1L, Math.min(CAP, cents));
        return String.format(java.util.Locale.ROOT, "%d.%02d",
                safe / CoinEconomy.CASH_SCALE, safe % CoinEconomy.CASH_SCALE);
    }

    private int bundle() {
        int max = this.itemStack.m_41619_() ? 64 : ShopOffer.bundleCap(this.itemStack);
        try {
            return Math.max(1, Math.min(max, Integer.parseInt(this.bundleBuf.trim())));
        }
        catch (NumberFormatException e) {
            return 1;
        }
    }

    private void setPrice(long v) {
        long clamped = Math.max(1L, Math.min(CAP, v));
        this.buf = CoinEconomy.isCash(this.coin)
                ? PriceInputScreen.unitsOf(clamped)
                : Long.toString(clamped);
    }

    private boolean inBox(double mx, double my, int[] box) {
        return FShopTheme.inside(mx, my, this.left + box[0], this.top + box[1], box[2] - box[0], box[3] - box[1]);
    }

    private void hoverBox(GuiGraphics g, int mouseX, int mouseY, int[] box) {
        if (this.inBox(mouseX, mouseY, box)) {
            g.m_280509_(this.left + box[0], this.top + box[1], this.left + box[2], this.top + box[3], 0x55FFFFFF);
        }
    }

    /** Selectable currencies, dropping cash when the currency mod is not installed. */
    private static int currencyCount() {
        return CoinEconomy.cashAvailable() ? CoinEconomy.TYPES : 3;
    }

    private int coinCellX(int c) {
        return this.left + FShopTextures.coinCellX(c);
    }

    private int coinCellY() {
        return this.top + FShopTextures.coinCellY();
    }

    private void confirm() {
        long price = this.price();
        int bundle = this.bundle();
        if (this.mode == Mode.ADD) {
            PacketHandler.sendToServer(new AddOfferPacket(this.shop.getId(), this.ref, price, this.coin, bundle));
        } else {
            PacketHandler.sendToServer(new SetPricePacket(this.shop.getId(), this.ref, price, this.coin, bundle));
        }
    }

    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.m_280273_(g);
        FShopTextures.blitPanel(g, FShopTextures.CONFIRMATION, this.left, this.top);
        this.renderBundlePanel(g);
        for (int[] box : FShopTextures.MINUS_CELLS) {
            this.hoverBox(g, mouseX, mouseY, box);
        }
        for (int[] box : FShopTextures.PLUS_CELLS) {
            this.hoverBox(g, mouseX, mouseY, box);
        }
        this.hoverBox(g, mouseX, mouseY, FShopTextures.NO_BOX);
        this.hoverBox(g, mouseX, mouseY, FShopTextures.YES_BOX);
        this.hoverBox(g, mouseX, mouseY, FShopTextures.SET_STACK_BOX);
        if (!this.itemStack.m_41619_()) {
            g.m_280203_(this.itemStack, this.left + 128 - 8, this.top + 96 - 8);
            if (this.bundle() > 1) {
                FShopTheme.drawCount(g, this.f_96547_, this.left + 128 - 8, this.top + 96 - 8, Integer.toString(this.bundle()));
            }
        }
        g.m_280137_(this.f_96547_, CoinEconomy.coinColorCode(this.coin)
                + CoinEconomy.formatAmount(this.coin, this.price()),
                this.left + 128, this.top + 110, CoinEconomy.coinColor(this.coin));
        int coinHov = -1;
        for (int c = 0; c < PriceInputScreen.currencyCount(); ++c) {
            int cx = this.coinCellX(c);
            int cy = this.coinCellY();
            g.m_280163_(FShopTextures.EMPTY_SLOT, cx + 1, cy + 1, 0.0f, 0.0f, 16, 16, 16, 16);
            if (c == this.coin) {
                g.m_280509_(cx, cy, cx + 18, cy + 18, 1728041546);
            }
            if (FShopTheme.inside(mouseX, mouseY, cx, cy, 18, 18)) {
                g.m_280509_(cx, cy, cx + 18, cy + 18, 1719848263);
                coinHov = c;
            }
            if (CoinEconomy.isCash(c)) {
                // Cash has no item, so the cell shows the currency symbol.
                String symbol = CoinEconomy.cashSymbol();
                g.m_280056_(this.f_96547_, symbol,
                        cx + 1 + (16 - this.f_96547_.m_92895_(symbol)) / 2, cy + 6,
                        CoinEconomy.coinColor(c), true);
            } else {
                g.m_280203_(CoinEconomy.coinIcon(c), cx + 1, cy + 1);
            }
        }
        super.m_88315_(g, mouseX, mouseY, partial);
        for (int i = 0; i < 3; ++i) {
            if (this.inBox(mouseX, mouseY, FShopTextures.MINUS_CELLS[i])) {
                this.tip(g, mouseX, mouseY, (Component)Component.m_237110_((String)"fshop.gui.price.minus", (Object[])new Object[]{STEPS[i]}));
                return;
            }
            if (!this.inBox(mouseX, mouseY, FShopTextures.PLUS_CELLS[i])) continue;
            this.tip(g, mouseX, mouseY, (Component)Component.m_237110_((String)"fshop.gui.price.plus", (Object[])new Object[]{STEPS[i]}));
            return;
        }
        if (this.inBox(mouseX, mouseY, FShopTextures.SET_STACK_BOX)) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.price.set64"));
        } else if (coinHov >= 0) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237110_((String)"fshop.gui.price.coin_tip", (Object[])new Object[]{Component.m_237115_((String)CoinEconomy.coinKey(coinHov))}));
        } else if (CoinEconomy.isCash(this.coin)
                && FShopTheme.inside(mouseX, mouseY, this.left + 96, this.top + 106, 64, 14)) {
            // Tell the player the price accepts decimals; nothing else on this screen does.
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.cash_price_hint"));
        } else if (this.inBox(mouseX, mouseY, FShopTextures.NO_BOX)) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)"fshop.gui.price.cancel_tip"));
        } else if (this.inBox(mouseX, mouseY, FShopTextures.YES_BOX)) {
            this.tip(g, mouseX, mouseY, (Component)Component.m_237115_((String)(this.mode == Mode.ADD ? "fshop.gui.price.confirm_tip_add" : "fshop.gui.price.confirm_tip_edit")));
        }
    }

    private void renderBundlePanel(GuiGraphics g) {
        g.m_280509_(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, -433841132);
        g.m_280509_(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 1, -1996495184);
        g.m_280509_(this.panelX, this.panelY + this.panelH - 1, this.panelX + this.panelW, this.panelY + this.panelH, -2013265920);
        g.m_280509_(this.panelX, this.panelY, this.panelX + 1, this.panelY + this.panelH, -1996495184);
        g.m_280509_(this.panelX + this.panelW - 1, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, -2013265920);
        int tx = this.panelX + 5;
        g.m_280614_(this.f_96547_, (Component)Component.m_237115_((String)"fshop.gui.price.bundle_label"), tx, this.titleY, -1320530, false);
        g.m_280554_(this.f_96547_, FormattedText.m_130775_((String)I18n.m_118938_((String)"fshop.gui.price.bundle_hint", (Object[])new Object[0])), tx, this.hintY, this.bundleW, -4609654);
    }

    private void tip(GuiGraphics g, int mouseX, int mouseY, Component c) {
        List<Component> t = new ArrayList<Component>();
        t.add(c);
        g.m_280666_(this.f_96547_, t, mouseX, mouseY);
    }

    public boolean m_6375_(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < 3; ++i) {
                if (this.inBox(mx, my, FShopTextures.MINUS_CELLS[i])) {
                    this.setPrice(this.price() - (long)STEPS[i]);
                    this.heldMinus = i;
                    this.heldPlus = -1;
                    this.holdTicks = 0;
                    Sfx.step();
                    return true;
                }
                if (!this.inBox(mx, my, FShopTextures.PLUS_CELLS[i])) continue;
                this.setPrice(this.price() + (long)STEPS[i]);
                this.heldPlus = i;
                this.heldMinus = -1;
                this.holdTicks = 0;
                Sfx.step();
                return true;
            }
            if (this.inBox(mx, my, FShopTextures.SET_STACK_BOX)) {
                // The button is labelled 64, so it should mean 64 of whatever is on screen. In cash
                // that is 64 units, not 64 cents - the raw reading would put $0.64 behind a button
                // that says 64.
                this.setPrice(CoinEconomy.isCash(this.coin) ? 64L * CoinEconomy.CASH_SCALE : 64L);
                Sfx.click();
                return true;
            }
            for (int c = 0; c < PriceInputScreen.currencyCount(); ++c) {
                if (!FShopTheme.inside(mx, my, this.coinCellX(c), this.coinCellY(), 18, 18)) continue;
                // Keep the numeric value and re-render it in the new currency's notation. Switching
                // between coins and cash changes whether the buffer means units or a raw count, so
                // leaving the text alone would silently reinterpret the price by a factor of a hundred.
                long carried = this.price();
                this.coin = c;
                this.setPrice(carried);
                Sfx.click();
                return true;
            }
            if (this.inBox(mx, my, FShopTextures.NO_BOX)) {
                Sfx.click();
                PacketHandler.sendToServer(new RequestManagePacket(this.shop.getId()));
                return true;
            }
            if (this.inBox(mx, my, FShopTextures.YES_BOX)) {
                Sfx.success();
                this.confirm();
                return true;
            }
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
        long delta = (long)STEPS[held] * (long)mult;
        long before = this.price();
        this.setPrice(this.heldMinus >= 0 ? before - delta : before + delta);
    }

    public boolean m_5534_(char c, int mods) {
        if (this.bundleBox != null && this.bundleBox.m_93696_()) {
            return super.m_5534_(c, mods);
        }
        if (c >= '0' && c <= '9') {
            String next = (this.buf.equals("0") ? "" : this.buf) + c;
            // Cap the decimals at two rather than accepting a precision the price cannot hold.
            if (PriceInputScreen.decimalsOf(next) > 2) {
                return true;
            }
            if (next.length() <= 12) {
                this.buf = next;
            }
            return true;
        }
        // A decimal point only means something for cash, and only once.
        if ((c == '.' || c == ',') && CoinEconomy.isCash(this.coin)
                && this.buf.indexOf('.') < 0 && this.buf.indexOf(',') < 0) {
            this.buf = (this.buf.isEmpty() ? "0" : this.buf) + '.';
            return true;
        }
        return super.m_5534_(c, mods);
    }

    /** How many digits follow the separator, or 0 when there is none. */
    private static int decimalsOf(String text) {
        int dot = Math.max(text.indexOf('.'), text.indexOf(','));
        return dot < 0 ? 0 : text.length() - dot - 1;
    }

    public boolean m_7933_(int key, int scan, int mods) {
        if (this.bundleBox != null && this.bundleBox.m_93696_()) {
            return super.m_7933_(key, scan, mods);
        }
        if (key == 259) {
            this.buf = this.buf.length() > 1 ? this.buf.substring(0, this.buf.length() - 1) : "1";
            return true;
        }
        if (key == 257 || key == 335) {
            this.confirm();
            return true;
        }
        return super.m_7933_(key, scan, mods);
    }

    public boolean m_7043_() {
        return false;
    }

    public static enum Mode {
        ADD,
        EDIT;

    }
}

