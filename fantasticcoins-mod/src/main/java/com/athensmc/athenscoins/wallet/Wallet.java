package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.config.CurrencyConfig;
import net.minecraft.nbt.CompoundTag;

/**
 * A player's Fantastic Cash account.
 *
 * <p>The balance is a count of cents, never a decimal, so it can only ever represent values with
 * two decimal places.</p>
 */
public class Wallet {

    private static final String KEY_CASH = "cash";

    private long cents;

    public Wallet() {
        this(0L);
    }

    public Wallet(long cents) {
        this.cents = Money.clampBalance(cents);
    }

    public long balance() {
        return cents;
    }

    public void set(long newCents) {
        this.cents = Money.clampBalance(newCents);
    }

    /** Adds only when the complete amount fits; no saturation and no partial mutation. */
    public boolean tryAdd(long delta) {
        if (delta <= 0L || !Money.canAdd(cents, delta)) {
            return false;
        }
        cents += delta;
        return true;
    }

    /** Compatibility helper. Prefer {@link #tryAdd(long)} in transactional code. */
    public void add(long delta) {
        tryAdd(delta);
    }

    public boolean canAfford(long amount) {
        return amount > 0L && cents >= amount;
    }

    /** Debits the account only if the full amount is available. */
    public boolean withdraw(long amount) {
        if (!canAfford(amount)) {
            return false;
        }
        cents -= amount;
        return true;
    }

    public boolean isEmpty() {
        return cents <= 0L;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_CASH, cents);
        return tag;
    }

    public static Wallet load(CompoundTag tag) {
        if (tag.contains(KEY_CASH)) {
            return new Wallet(tag.getLong(KEY_CASH));
        }
        // Migration: the first wallet build stored three separate digital coin pools.
        // Convert them into cash at the configured rates so nobody loses their balance.
        long migrated = 0L;
        boolean legacy = false;
        for (CoinType type : CoinType.ORDERED) {
            if (!tag.contains(type.id())) {
                continue;
            }
            legacy = true;
            migrated += Money.multiply(type.cashValueCents(), tag.getLong(type.id()));
        }
        if (legacy) {
            return new Wallet(migrated);
        }
        return new Wallet(CurrencyConfig.get().startingCents());
    }
}
