package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletData;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds an {@link EconomySnapshot} from the saved accounts and the online players. */
public final class EconomyStats {

    private static final int TOP_ROWS = 6;

    private EconomyStats() {
    }

    public static EconomySnapshot compute(MinecraftServer server) {
        Map<UUID, Wallet> accounts = WalletData.get(server).all();

        List<long[]> balances = new ArrayList<>(accounts.size());
        long total = 0L;
        for (Map.Entry<UUID, Wallet> entry : accounts.entrySet()) {
            long cents = entry.getValue().balance();
            total += cents;
            balances.add(new long[] { cents });
        }
        balances.sort(Comparator.comparingLong(value -> -value[0]));

        int count = balances.size();
        long average = count == 0 ? 0L : total / count;
        long median = 0L;
        if (count > 0) {
            median = count % 2 == 1
                    ? balances.get(count / 2)[0]
                    : (balances.get(count / 2 - 1)[0] + balances.get(count / 2)[0]) / 2L;
        }
        long richest = count == 0 ? 0L : balances.get(0)[0];

        // Wealth concentration: what share of all cash the ten largest accounts hold.
        long topTen = 0L;
        for (int i = 0; i < Math.min(10, count); i++) {
            topTen += balances.get(i)[0];
        }
        int share = total <= 0L ? 0 : (int) Math.round(100.0D * topTen / total);

        // Named leaderboard, resolving UUIDs through the profile cache so offline players show up.
        List<EconomySnapshot.Holder> top = new ArrayList<>();
        accounts.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> -entry.getValue().balance()))
                .limit(TOP_ROWS)
                .forEach(entry -> top.add(new EconomySnapshot.Holder(
                        nameOf(server, entry.getKey()), entry.getValue().balance())));

        // Coins are only countable for loaded inventories, so this covers online players only.
        long[] coinCounts = new long[CoinType.ORDERED.length];
        long coinValue = 0L;
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        for (ServerPlayer player : online) {
            for (CoinType type : CoinType.ORDERED) {
                int carried = WalletManager.countCoins(player, type);
                coinCounts[type.ordinal()] += carried;
                coinValue += Money.multiply(type.cashValueCents(), carried);
            }
        }

        long[] rates = new long[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            rates[type.ordinal()] = type.cashValueCents();
        }

        return new EconomySnapshot(count, online.size(), total, average, median, richest, share,
                coinCounts, coinValue, rates, top, DisplaySettings.fromConfig());
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache() == null
                ? Optional.empty()
                : server.getProfileCache().get(id);
        if (profile.isPresent() && profile.get().getName() != null) {
            return profile.get().getName();
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        return id.toString().substring(0, 8);
    }
}
