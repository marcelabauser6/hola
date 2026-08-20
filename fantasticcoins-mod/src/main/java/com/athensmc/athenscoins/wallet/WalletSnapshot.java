package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.config.DisplaySettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Everything the wallet screen needs, captured at the moment it is opened.
 *
 * <p>The wallet is a read-only report, so it travels as a plain snapshot rather than through a
 * container menu. That keeps it completely detached from the player's inventory state.</p>
 */
public record WalletSnapshot(long cashCents,
                             int[] coinCounts,
                             UUID ownerId,
                             String ownerName,
                             boolean atmNearby,
                             DisplaySettings display) {

    public static WalletSnapshot of(ServerPlayer target) {
        return new WalletSnapshot(
                WalletManager.accountOf(target).balance(),
                WalletManager.countAllCoins(target),
                target.getUUID(),
                target.getGameProfile().getName(),
                WalletManager.findNearbyAtm(target) != null,
                DisplaySettings.fromConfig());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarLong(cashCents);
        for (int count : coinCounts) {
            buffer.writeVarInt(count);
        }
        buffer.writeUUID(ownerId);
        buffer.writeUtf(ownerName, 32);
        buffer.writeBoolean(atmNearby);
        display.write(buffer);
    }

    public static WalletSnapshot read(FriendlyByteBuf buffer) {
        long cash = buffer.readVarLong();
        int[] counts = new int[CoinType.ORDERED.length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = buffer.readVarInt();
        }
        UUID owner = buffer.readUUID();
        String name = buffer.readUtf(32);
        boolean atm = buffer.readBoolean();
        return new WalletSnapshot(cash, counts, owner, name, atm, DisplaySettings.read(buffer));
    }

    public int coinCount(CoinType type) {
        return coinCounts[type.ordinal()];
    }

    /** Cash value of every coin carried in the inventory. */
    public long coinsValueCents() {
        long total = 0L;
        for (CoinType type : CoinType.ORDERED) {
            total += Money.multiply(display.valueOf(type), coinCounts[type.ordinal()]);
        }
        return total;
    }

    /** Cash plus the value of the carried coins. */
    public long netWorthCents() {
        return Money.clampBalance(cashCents + coinsValueCents());
    }
}
