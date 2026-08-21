/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 */
package com.fshop.client;

import com.fshop.client.FShopTextures;
import com.fshop.client.FShopTheme;
import com.fshop.economy.CoinEconomy;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ShopWidgets {
    private ShopWidgets() {
    }

    public static int renderInventory(GuiGraphics g, Font font, Inventory inv, int left, int top, int mouseX, int mouseY, boolean interactive) {
        int hovered = -1;
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                boolean hov;
                int cx = left + FShopTextures.invCellX(col);
                int cy = top + FShopTextures.invCellY(row);
                int slot = FShopTextures.invSlot(row, col);
                ItemStack st = inv.m_8020_(slot);
                boolean bl = hov = interactive && !st.m_41619_() && FShopTheme.inside(mouseX, mouseY, cx, cy, 18, 18);
                if (hov) {
                    g.m_280509_(cx, cy, cx + 18, cy + 18, 1719848263);
                    hovered = slot;
                }
                if (st.m_41619_()) continue;
                g.m_280203_(st, cx + 1, cy + 1);
                g.m_280370_(font, st, cx + 1, cy + 1);
            }
        }
        return hovered;
    }

    public static int slotAt(Inventory inv, int left, int top, double mx, double my) {
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                int cy;
                int cx = left + FShopTextures.invCellX(col);
                if (!FShopTheme.inside(mx, my, cx, cy = top + FShopTextures.invCellY(row), 18, 18)) continue;
                int slot = FShopTextures.invSlot(row, col);
                return inv.m_8020_(slot).m_41619_() ? -1 : slot;
            }
        }
        return -1;
    }

    /** Wallet cells: the three coins, plus a cash cell when the currency mod is present. */
    private static int cellCount() {
        return CoinEconomy.cashAvailable() ? CoinEconomy.TYPES : 3;
    }

    /** Keeps a large balance inside a 16px cell. */
    private static String shortCount(long value) {
        if (value < 10000L) {
            return Long.toString(value);
        }
        if (value < 1000000L) {
            return value / 1000L + "k";
        }
        return value / 1000000L + "M";
    }

    public static int renderCoins(GuiGraphics g, Font font, Player player, int left, int top, int mouseX, int mouseY, int highlight) {
        int hovered = -1;
        for (int c = 0; c < ShopWidgets.cellCount(); ++c) {
            ItemStack coin;
            int cx = left + FShopTextures.coinCellX(c);
            int cy = top + FShopTextures.coinCellY();
            g.m_280163_(FShopTextures.EMPTY_SLOT, cx + 1, cy + 1, 0.0f, 0.0f, 16, 16, 16, 16);
            boolean hov = FShopTheme.inside(mouseX, mouseY, cx, cy, 18, 18);
            if (c == highlight) {
                g.m_280509_(cx, cy, cx + 18, cy + 18, 1728041546);
            }
            if (hov) {
                g.m_280509_(cx, cy, cx + 18, cy + 18, 1719848263);
                hovered = c;
            }
            long bal = CoinEconomy.balance(player, c);
            if (CoinEconomy.isCash(c)) {
                // Cash has no item to draw: show the symbol, then the balance where a stack
                // count would sit. The balance is in cents, so print whole units here.
                String symbol = CoinEconomy.cashSymbol();
                g.m_280056_(font, symbol, cx + 1 + (16 - font.m_92895_(symbol)) / 2, cy + 3,
                        CoinEconomy.coinColor(c), true);
                String amount = ShopWidgets.shortCount(bal / CoinEconomy.CASH_SCALE);
                g.m_280056_(font, amount, cx + 17 - font.m_92895_(amount), cy + 11, -1, true);
                continue;
            }
            if ((coin = CoinEconomy.coinIcon(c)).m_41619_()) continue;
            g.m_280203_(coin, cx + 1, cy + 1);
            g.m_280302_(font, coin, cx + 1, cy + 1, ShopWidgets.shortCount(bal));
        }
        return hovered;
    }

    public static int coinAt(int left, int top, double mx, double my) {
        for (int c = 0; c < ShopWidgets.cellCount(); ++c) {
            int cy;
            int cx = left + FShopTextures.coinCellX(c);
            if (!FShopTheme.inside(mx, my, cx, cy = top + FShopTextures.coinCellY(), 18, 18)) continue;
            return c;
        }
        return -1;
    }
}

