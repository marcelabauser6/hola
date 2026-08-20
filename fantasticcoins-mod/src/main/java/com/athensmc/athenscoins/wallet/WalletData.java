package com.athensmc.athenscoins.wallet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-persistent storage for every player's digital wallet.
 *
 * <p>Stored once per save in the overworld's data storage
 * ({@code <world>/data/athens_coins_wallets.dat}) so balances survive restarts and work even
 * for offline players.</p>
 */
public class WalletData extends SavedData {
    public static final String DATA_NAME = "athens_coins_wallets";

    private final Map<UUID, Wallet> wallets = new HashMap<>();

    public static WalletData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(WalletData::load, WalletData::new, DATA_NAME);
    }

    /** Returns the wallet for the given player, creating an empty one on first use. */
    public Wallet wallet(UUID owner) {
        return wallets.computeIfAbsent(owner, id -> new Wallet());
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
