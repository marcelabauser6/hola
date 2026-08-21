package com.fshop.client;

import com.fshop.economy.CoinEconomy;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Shared shop widgets.
 *
 * <p>The wallet strip now has a fourth cell for Fantastic Cash alongside the three coin cells.
 * Cash has no item to draw, so that cell shows the currency symbol with the account balance
 * beside it, in the same corner position an item stack count would occupy.</p>
 */
public final class ShopWidgets {

    private ShopWidgets() {
    }

    /** Number of wallet cells, dropping the cash cell when the currency mod is absent. */
    private static int cellCount() {
        return CoinEconomy.cashAvailable() ? CoinEconomy.TYPES : 3;
    }

    /** Compact form so a large balance still fits a 16px cell. */
    private static String shortAmount(long value) {
        if (value < 10_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return (value / 1_000L) + "k";
        }
        return (value / 1_000_000L) + "M";
    }

    public static int renderCoins(GuiGraphics g, Font font, Player player, int left, int top,
                                  int mouseX, int mouseY, int highlight) {
        int hovered = -1;
        int cells = cellCount();
        for (int c = 0; c < cells; ++c) {
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
                // No item icon exists for cash: draw the symbol, then the balance where a stack
                // count would sit.
                String symbol = CoinEconomy.cashSymbol();
                g.m_280056_(font, symbol,
                        cx + 1 + (16 - font.m_92895_(symbol)) / 2, cy + 3,
                        CoinEconomy.coinColor(c), true);
                String amount = shortAmount(bal);
                g.m_280056_(font, amount,
                        cx + 17 - font.m_92895_(amount), cy + 11, 0xFFFFFFFF, true);
                continue;
            }

            ItemStack coin = CoinEconomy.coinIcon(c);
            if (coin.m_41619_()) {
                continue;
            }
            g.m_280203_(coin, cx + 1, cy + 1);
            g.m_280302_(font, coin, cx + 1, cy + 1, shortAmount(bal));
        }
        return hovered;
    }

    public static int coinAt(int left, int top, double mx, double my) {
        int cells = cellCount();
        for (int c = 0; c < cells; ++c) {
            int cx = left + FShopTextures.coinCellX(c);
            int cy = top + FShopTextures.coinCellY();
            if (!FShopTheme.inside(mx, my, cx, cy, 18, 18)) continue;
            return c;
        }
        return -1;
    }
}
