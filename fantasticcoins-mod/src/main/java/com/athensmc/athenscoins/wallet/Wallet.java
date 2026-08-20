package com.athensmc.athenscoins.wallet;

import net.minecraft.nbt.CompoundTag;

/**
 * A player's <em>digital</em> coin balance.
 *
 * <p>This is deliberately a completely separate pool from the physical coins carried in the
 * inventory: depositing at an ATM removes cash from the inventory and credits it here, and
 * withdrawing does the reverse. The two numbers are independent.</p>
 */
public class Wallet {
    private final long[] amounts = new long[CoinType.ORDERED.length];

    public long get(CoinType type) {
        return amounts[type.ordinal()];
    }

    public void set(CoinType type, long value) {
        amounts[type.ordinal()] = Math.max(0L, value);
    }

    /** Adds (or removes, for negative deltas) an amount, clamped at zero. */
    public void add(CoinType type, long delta) {
        set(type, get(type) + delta);
    }

    public boolean isEmpty() {
        for (long amount : amounts) {
            if (amount > 0L) {
                return false;
            }
        }
        return true;
    }

    /** Total digital worth expressed in bronze units. */
    public long totalInBronze() {
        long total = 0L;
        for (CoinType type : CoinType.ORDERED) {
            total += get(type) * (long) type.bronzeValue();
        }
        return total;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (CoinType type : CoinType.ORDERED) {
            tag.putLong(type.nbtKey(), get(type));
        }
        return tag;
    }

    public static Wallet load(CompoundTag tag) {
        Wallet wallet = new Wallet();
        for (CoinType type : CoinType.ORDERED) {
            wallet.set(type, tag.getLong(type.nbtKey()));
        }
        return wallet;
    }
}
