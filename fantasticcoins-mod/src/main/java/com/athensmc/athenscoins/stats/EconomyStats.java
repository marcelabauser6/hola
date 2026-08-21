package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** Builds economy statistics from canonical bank accounts and online physical inventories. */
public final class EconomyStats {
    private static final int TOP_ROWS = 6;
    private EconomyStats() {}

    public static EconomySnapshot compute(MinecraftServer server) {
        List<BankAccount> accounts = BankData.get(server).allAccounts();
        List<long[]> balances = new ArrayList<>(accounts.size());
        long total = 0L;
        for (BankAccount account : accounts) {
            long cents = Money.canAdd(account.balance(), account.walletBalance())
                    ? account.balance() + account.walletBalance() : Money.MAX_CENTS;
            total = Money.canAdd(total, cents) ? total + cents : Money.MAX_CENTS;
            balances.add(new long[] { cents });
        }
        balances.sort(Comparator.comparingLong(value -> -value[0]));
        int count = balances.size();
        long average = count == 0 ? 0L : total / count;
        long median = count == 0 ? 0L : count % 2 == 1 ? balances.get(count / 2)[0]
                : safeAverage(balances.get(count / 2 - 1)[0], balances.get(count / 2)[0]);
        long richest = count == 0 ? 0L : balances.get(0)[0];
        long topTen = 0L;
        for (int i = 0; i < Math.min(10, count); i++)
            topTen = Money.canAdd(topTen, balances.get(i)[0]) ? topTen + balances.get(i)[0] : Money.MAX_CENTS;
        int share = total <= 0L ? 0 : (int) Math.round(100.0D * topTen / total);

        List<EconomySnapshot.Holder> top = new ArrayList<>();
        accounts.stream().sorted(Comparator.comparingLong(EconomyStats::wealth).reversed()).limit(TOP_ROWS)
                .forEach(account -> top.add(new EconomySnapshot.Holder(nameOf(server, account.owner()), wealth(account))));

        long[] coinCounts = new long[CoinType.ORDERED.length]; long coinValue = 0L;
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        for (ServerPlayer player : online) for (CoinType type : CoinType.ORDERED) {
            int carried = WalletManager.countCoins(player, type); coinCounts[type.ordinal()] += carried;
            long value = Money.multiply(type.cashValueCents(), carried);
            coinValue = Money.canAdd(coinValue, value) ? coinValue + value : Money.MAX_CENTS;
        }
        long[] rates = new long[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) rates[type.ordinal()] = type.cashValueCents();
        return new EconomySnapshot(count, online.size(), total, average, median, richest, share,
                coinCounts, coinValue, rates, top, DisplaySettings.fromConfig());
    }

    private static long wealth(BankAccount account) {
        return Money.canAdd(account.balance(), account.walletBalance())
                ? account.balance() + account.walletBalance() : Money.MAX_CENTS;
    }
    private static long safeAverage(long left, long right) { return left / 2L + right / 2L + (left % 2L + right % 2L) / 2L; }
    private static String nameOf(MinecraftServer server, UUID id) {
        Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(id);
        if (profile.isPresent() && profile.get().getName() != null) return profile.get().getName();
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player != null ? player.getGameProfile().getName() : id.toString().substring(0, 8);
    }
}
