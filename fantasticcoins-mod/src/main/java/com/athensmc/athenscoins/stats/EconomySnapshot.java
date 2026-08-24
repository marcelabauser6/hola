package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.wallet.CoinType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * NBT form, for the stats hologram.
     *
     * <p>A hologram is not a screen being opened, so there is no menu payload to ride along on: its
     * values arrive in the projector's {@code getUpdateTag()}. This is the same snapshot the admin
     * screens use, deliberately - a second "values for holograms" type would be a second place for the
     * definition of "total supply" to live, and the two would eventually disagree.</p>
     *
     * <p>Not persisted to disk. The projector writes this into its sync tag only; the numbers are
     * recomputed from the bank data every refresh, so saving them would be storing a stale copy of
     * something already on disk and then having to decide which one to believe on load.</p>
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("accounts", accounts);
        tag.putInt("online", onlinePlayers);
        tag.putLong("cash", totalCashCents);
        tag.putLong("average", averageCents);
        tag.putLong("median", medianCents);
        tag.putLong("richest", richestCents);
        tag.putInt("share", topTenSharePercent);
        tag.putLongArray("coinCounts", onlineCoinCounts.clone());
        tag.putLong("coinValue", onlineCoinValueCents);
        tag.putLongArray("coinRates", coinRates.clone());
        ListTag holders = new ListTag();
        for (Holder holder : top) {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", holder.name());
            entry.putLong("cents", holder.cents());
            holders.add(entry);
        }
        tag.put("top", holders);
        tag.put("display", display.save());
        return tag;
    }

    public static EconomySnapshot load(CompoundTag tag) {
        long[] counts = new long[CoinType.ORDERED.length];
        long[] rates = new long[CoinType.ORDERED.length];
        long[] savedCounts = tag.getLongArray("coinCounts");
        long[] savedRates = tag.getLongArray("coinRates");
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            counts[i] = i < savedCounts.length ? savedCounts[i] : 0L;
            rates[i] = i < savedRates.length ? savedRates[i] : 0L;
        }
        ListTag holders = tag.getList("top", Tag.TAG_COMPOUND);
        List<Holder> top = new ArrayList<>(holders.size());
        for (int i = 0; i < holders.size(); i++) {
            CompoundTag entry = holders.getCompound(i);
            top.add(new Holder(entry.getString("name"), entry.getLong("cents")));
        }
        return new EconomySnapshot(tag.getInt("accounts"), tag.getInt("online"),
                tag.getLong("cash"), tag.getLong("average"), tag.getLong("median"),
                tag.getLong("richest"), tag.getInt("share"), counts, tag.getLong("coinValue"),
                rates, top, DisplaySettings.load(tag.getCompound("display")));
    }

    /** An all-zero snapshot, for a projector the server has not filled in yet. */
    public static EconomySnapshot empty() {
        return new EconomySnapshot(0, 0, 0L, 0L, 0L, 0L, 0,
                new long[CoinType.ORDERED.length], 0L, new long[CoinType.ORDERED.length],
                List.of(), new DisplaySettings("Fantastic Cash", "$", 0x4CD964,
                new int[CoinType.ORDERED.length], new long[CoinType.ORDERED.length], 1));
    }

    /**
     * Value comparison, because {@link #equals} is the wrong answer here.
     *
     * <p>A record's generated {@code equals} calls {@code equals} on each component, and two of these
     * components are arrays - which compare by identity. So a freshly computed snapshot is never
     * "equal" to the previous one even when every figure in it is the same, and a hologram relying on
     * that check would broadcast an update to every nearby player every few seconds forever.</p>
     */
    public boolean sameValues(EconomySnapshot other) {
        return other != null
                && accounts == other.accounts
                && onlinePlayers == other.onlinePlayers
                && totalCashCents == other.totalCashCents
                && averageCents == other.averageCents
                && medianCents == other.medianCents
                && richestCents == other.richestCents
                && topTenSharePercent == other.topTenSharePercent
                && onlineCoinValueCents == other.onlineCoinValueCents
                && Arrays.equals(onlineCoinCounts, other.onlineCoinCounts)
                && Arrays.equals(coinRates, other.coinRates)
                && top.equals(other.top);
    }

    /** Cash plus the value of coins carried by online players. */
    public long totalSupplyCents() {
        return totalCashCents + onlineCoinValueCents;
    }
}
