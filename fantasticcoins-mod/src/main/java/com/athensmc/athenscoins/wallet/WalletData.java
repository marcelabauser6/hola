package com.athensmc.athenscoins.wallet;

import com.athensmc.athenscoins.config.CurrencyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-persistent Fantastic Cash accounts, keyed by player UUID.
 *
 * <p>Saved as part of the world in {@code <world>/data/athens_coins_wallets.dat}, so balances
 * survive restarts and remain readable for offline players. Keying on UUID rather than name
 * means a player rename does not lose their money.</p>
 */
public class WalletData extends SavedData {

    public static final String DATA_NAME = "athens_coins_wallets";

    private final Map<UUID, Wallet> wallets = new HashMap<>();

    public static WalletData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WalletData::load, WalletData::new, DATA_NAME);
    }

    /** Returns the account for a player, opening one with the configured starting balance. */
    public Wallet account(UUID owner) {
        return wallets.computeIfAbsent(owner,
                id -> new Wallet(CurrencyConfig.get().startingCents()));
    }

    public boolean hasAccount(UUID owner) {
        return wallets.containsKey(owner);
    }

    public Map<UUID, Wallet> all() {
        return wallets;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        wallets.forEach((owner, wallet) -> {
            if (wallet.isEmpty()) {
                return;
            }
            CompoundTag entry = wallet.save();
            entry.putUUID("Owner", owner);
            list.add(entry);
        });
        tag.put("Wallets", list);
        tag.putInt("DataVersion", 2);
        return tag;
    }

    public static WalletData load(CompoundTag tag) {
        WalletData data = new WalletData();
        ListTag list = tag.getList("Wallets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Owner")) {
                continue;
            }
            data.wallets.put(entry.getUUID("Owner"), Wallet.load(entry));
        }
        return data;
    }
}
