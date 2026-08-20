package com.athensmc.athenscoins.config;

import com.athensmc.athenscoins.wallet.CoinType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The slice of the config the client needs in order to draw the GUIs.
 *
 * <p>The config itself is server-side only, so these values travel with the menu when a screen
 * is opened. That way a server admin's choice of symbol, colours and rates is what every player
 * sees, and single-player works through exactly the same path.</p>
 */
public record DisplaySettings(String currencyName,
                              String currencySymbol,
                              int cashColor,
                              int[] coinColors,
                              long[] coinValueCents,
                              int walletTheme) {

    public static DisplaySettings fromConfig() {
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        int[] colors = new int[CoinType.ORDERED.length];
        long[] values = new long[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            colors[type.ordinal()] = settings.coinColor(type);
            values[type.ordinal()] = settings.coinValueCents(type);
        }
        return new DisplaySettings(settings.currencyName, settings.currencySymbol,
                settings.cashColorRgb(), colors, values, settings.walletTheme);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(currencyName, 48);
        buffer.writeUtf(currencySymbol, 8);
        buffer.writeInt(cashColor);
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            buffer.writeInt(coinColors[i]);
            buffer.writeVarLong(coinValueCents[i]);
        }
        buffer.writeByte(walletTheme);
    }

    public static DisplaySettings read(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(48);
        String symbol = buffer.readUtf(8);
        int cash = buffer.readInt();
        int[] colors = new int[CoinType.ORDERED.length];
        long[] values = new long[CoinType.ORDERED.length];
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            colors[i] = buffer.readInt();
            values[i] = buffer.readVarLong();
        }
        int theme = buffer.readByte();
        return new DisplaySettings(name, symbol, cash, colors, values,
                Math.max(1, Math.min(3, theme)));
    }

    public int colorOf(CoinType type) {
        return coinColors[type.ordinal()];
    }

    public long valueOf(CoinType type) {
        return coinValueCents[type.ordinal()];
    }
}
