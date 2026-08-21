package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.wallet.CoinType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only picture of the server economy, taken when an admin asks for it.
 *
 * <p>Everything is in cents. The coin figures only cover players who are online, because coins
 * physically live in inventories and an offline player's inventory is not loaded - the screen
 * says so rather than pretending the number is the whole supply.</p>
 */
public record EconomySnapshot(int accounts,
                              int onlinePlayers,
                              long totalCashCents,
                              long averageCents,
                              long medianCents,
                              long richestCents,
                              int topTenSharePercent,
                              long[] onlineCoinCounts,
                              long onlineCoinValueCents,
                              long[] coinRates,
                              List<Holder> top,
                              DisplaySettings display) {

    /** One line of the "richest accounts" table. */
    public record Holder(String name, long cents) {
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(accounts);
        buffer.writeVarInt(onlinePlayers);
        buffer.writeVarLong(totalCashCents);
        buffer.writeVarLong(averageCents);
        buffer.writeVarLong(medianCents);
        buffer.writeVarLong(richestCents);
        buffer.writeVarInt(topTenSharePercent);
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            buffer.writeVarLong(onlineCoinCounts[i]);
            buffer.writeVarLong(coinRates[i]);
        }
        buffer.writeVarLong(onlineCoinValueCents);
        buffer.writeVarInt(top.size());
        for (Holder holder : top) {
            buffer.writeUtf(holder.name(), 32);
            buffer.writeVarLong(holder.cents());
        }
        display.write(buffer);
    }

    public static EconomySnapshot read(FriendlyByteBuf buffer) {
        int accounts = buffer.readVarInt();
        int online = buffer.readVarInt();
        long total = buffer.readVarLong();
        long average = buffer.readVarLong();
        long median = buffer.readVarLong();
        long richest = buffer.readVarLong();
        int share = buffer.readVarInt();
        long[] counts = new long[CoinType.ORDERED.length];
        long[] rates = new long[CoinType.ORDERED.length];
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            counts[i] = buffer.readVarLong();
            rates[i] = buffer.readVarLong();
        }
        long coinValue = buffer.readVarLong();
        int topCount = buffer.readVarInt();
        List<Holder> top = new ArrayList<>(topCount);
        for (int i = 0; i < topCount; i++) {
            top.add(new Holder(buffer.readUtf(32), buffer.readVarLong()));
        }
        return new EconomySnapshot(accounts, online, total, average, median, richest, share,
                counts, coinValue, rates, top, DisplaySettings.read(buffer));
    }

    /** Cash plus the value of coins carried by online players. */
    public long totalSupplyCents() {
        return totalCashCents + onlineCoinValueCents;
    }
}
