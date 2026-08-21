package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.config.CurrencyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent limited wallet cash plus safe migration/quarantine metadata. */
public class WalletData extends SavedData {
    public static final String DATA_NAME = "athens_coins_wallets";
    public static final int CURRENT_VERSION = 3;

    private final Map<UUID, Wallet> wallets = new HashMap<>();
    /** Legacy balances without a bank are retained here until the owner opens an account. */
    private final Map<UUID, Long> quarantined = new HashMap<>();
    /** Prevents repeating startingBalance by draining/closing a zero wallet. */
    private final Set<UUID> initialized = new HashSet<>();
    private int loadedVersion = CURRENT_VERSION;

    public static WalletData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WalletData::load, WalletData::new, DATA_NAME);
    }

    /** Returns a zero wallet and persists its initialization immediately. */
    public Wallet account(UUID owner) {
        Wallet existing = wallets.get(owner);
        if (existing != null) return existing;
        Wallet created = new Wallet(0L);
        wallets.put(owner, created);
        setDirty();
        return created;
    }

    @Nullable public Wallet existing(UUID owner) { return wallets.get(owner); }
    public boolean hasAccount(UUID owner) { return wallets.containsKey(owner); }
    public Map<UUID, Wallet> all() { return wallets; }
    public boolean migrationPending() { return loadedVersion < CURRENT_VERSION; }

    public boolean isInitialized(UUID owner) { return initialized.contains(owner); }

    /** Value eligible for a first successful account opening; this method does not consume it. */
    public long startingBalanceIfUninitialized(UUID owner) {
        return initialized.contains(owner) ? 0L : CurrencyConfig.get().startingCents();
    }

    /** Legacy helper retained for compatibility. New account flow marks only after commit. */
    public long claimStartingBalance(UUID owner) {
        if (!initialized.add(owner)) return 0L;
        setDirty();
        return CurrencyConfig.get().startingCents();
    }

    public void markInitialized(UUID owner) {
        if (initialized.add(owner)) setDirty();
    }

    public long quarantined(UUID owner) { return quarantined.getOrDefault(owner, 0L); }

    public boolean quarantine(UUID owner, long amount) {
        if (amount <= 0L) return true;
        long current = quarantined(owner);
        final long combined;
        try {
            combined = Math.addExact(current, amount);
        } catch (ArithmeticException overflow) {
            return false;
        }
        quarantined.put(owner, combined);
        setDirty();
        return true;
    }

    /** Removes and returns retained legacy value after the caller has prevalidated its destination. */
    public long takeQuarantined(UUID owner) {
        Long amount = quarantined.remove(owner);
        if (amount != null) setDirty();
        return amount == null ? 0L : amount;
    }

    public void finishMigration() {
        loadedVersion = CURRENT_VERSION;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        wallets.forEach((owner, wallet) -> {
            CompoundTag entry = wallet.save();
            entry.putUUID("Owner", owner);
            list.add(entry); // zero entries are intentional initialization records
        });
        tag.put("Wallets", list);
        ListTag held = new ListTag();
        quarantined.forEach((owner, amount) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("Owner", owner); entry.putLong("cash", amount); held.add(entry);
        });
        tag.put("Quarantine", held);
        ListTag init = new ListTag();
        for (UUID owner : initialized) { CompoundTag entry = new CompoundTag(); entry.putUUID("Owner", owner); init.add(entry); }
        tag.put("Initialized", init);
        tag.putInt("DataVersion", loadedVersion);
        return tag;
    }

    public static WalletData load(CompoundTag tag) {
        WalletData data = new WalletData();
        data.loadedVersion = tag.contains("DataVersion") ? tag.getInt("DataVersion") : 0;
        ListTag list = tag.getList("Wallets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Owner")) {
                UUID owner = entry.getUUID("Owner");
                long raw = entry.contains("cash") ? entry.getLong("cash") : -1L;
                data.wallets.put(owner, Wallet.load(entry));
                if (raw > Money.MAX_CENTS) {
                    data.quarantined.put(owner, raw - Money.MAX_CENTS);
                }
            }
        }
        ListTag held = tag.getList("Quarantine", Tag.TAG_COMPOUND);
        for (int i = 0; i < held.size(); i++) {
            CompoundTag entry = held.getCompound(i);
            if (entry.hasUUID("Owner")) {
                UUID owner = entry.getUUID("Owner");
                long amount = Math.max(0L, entry.getLong("cash"));
                long current = data.quarantined.getOrDefault(owner, 0L);
                try { data.quarantined.put(owner, Math.addExact(current, amount)); }
                catch (ArithmeticException overflow) { data.quarantined.put(owner, Long.MAX_VALUE); }
            }
        }
        ListTag init = tag.getList("Initialized", Tag.TAG_COMPOUND);
        for (int i = 0; i < init.size(); i++) if (init.getCompound(i).hasUUID("Owner")) data.initialized.add(init.getCompound(i).getUUID("Owner"));
        return data;
    }
}
