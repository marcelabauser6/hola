package com.athensmc.athenscoins.config;

import com.athensmc.athenscoins.wallet.CoinType;
import net.minecraft.nbt.CompoundTag;
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

    /**
     * NBT form, for the pieces of the mod that travel as a block entity rather than as a menu.
     *
     * <p>The stats hologram needs the currency symbol and the coin colours in order to draw anything,
     * and it is synced through {@code getUpdateTag()} - there is no screen being opened to carry them.
     * Same fields, same clamps, same class: a parallel "hologram display settings" type would have been
     * free to disagree with this one about what the currency is called.</p>
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", currencyName);
        tag.putString("symbol", currencySymbol);
        tag.putInt("cashColor", cashColor);
        tag.putIntArray("coinColors", coinColors.clone());
        tag.putLongArray("coinValues", coinValueCents.clone());
        tag.putByte("walletTheme", (byte) walletTheme);
        return tag;
    }

    public static DisplaySettings load(CompoundTag tag) {
        int[] colors = new int[CoinType.ORDERED.length];
        long[] values = new long[CoinType.ORDERED.length];
        int[] savedColors = tag.getIntArray("coinColors");
        long[] savedValues = tag.getLongArray("coinValues");
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            // Length-checked rather than trusted: an array from a different denomination count would
            // otherwise throw while a chunk is being read, which fails the whole chunk, not one block.
            colors[i] = i < savedColors.length ? savedColors[i] : 0xFFFFFF;
            values[i] = i < savedValues.length ? savedValues[i] : 0L;
        }
        return new DisplaySettings(tag.getString("name"), tag.getString("symbol"),
                tag.getInt("cashColor"), colors, values,
                Math.max(1, Math.min(3, tag.getByte("walletTheme"))));
    }

    public int colorOf(CoinType type) {
        return coinColors[type.ordinal()];
    }

    public long valueOf(CoinType type) {
        return coinValueCents[type.ordinal()];
    }
}
