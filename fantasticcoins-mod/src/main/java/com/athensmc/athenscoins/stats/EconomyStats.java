package com.athensmc.athenscoins.stats;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.config.DisplaySettings;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Builds economy statistics from canonical bank accounts and online physical inventories.
 *
 * <p>Two scopes, one body. {@link #compute} covers the whole server; {@link #computeForBank} covers one
 * bank. They share {@link #build} because every figure is defined the same way in both - "average
 * balance" must mean the same thing on a bank's own board as on the central bank's, or two boards in the
 * same square would disagree about arithmetic. The only differences are which accounts are counted, whose
 * exchange rates are quoted, and whether there is a reserve to report at all.</p>
 */
public final class EconomyStats {
    private static final int TOP_ROWS = 6;
    private EconomyStats() {}

    /** The whole server: every account at every bank, and the official rates. */
    public static EconomySnapshot compute(MinecraftServer server) {
        return build(server, null, BankData.get(server).allAccounts());
    }

    /**
     * One bank: its own customers, its own reserve, its own quoted rates.
     *
     * <p>What a bank's board can honestly show is narrower than the central bank's, and the difference is
     * the point of branding a board at all. "Cash in circulation" on a bank board means the deposits it
     * holds, not the money supply - no single bank knows the supply.</p>
     */
    public static EconomySnapshot computeForBank(MinecraftServer server, Bank bank) {
        return build(server, bank, BankData.get(server).accountsOf(bank.id()));
    }

    private static EconomySnapshot build(MinecraftServer server, @Nullable Bank bank,
                                         List<BankAccount> accounts) {
        List<long[]> balances = new ArrayList<>(accounts.size());
        long total = 0L;
        long loansOut = 0L;
        for (BankAccount account : accounts) {
            long cents = wealth(account);
            total = Money.canAdd(total, cents) ? total + cents : Money.MAX_CENTS;
            balances.add(new long[] { cents });
            if (account.loan() != null && !account.loan().settled()) {
                long owed = account.loan().owed();
                loansOut = Money.canAdd(loansOut, owed) ? loansOut + owed : Money.MAX_CENTS;
            }
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

        // Coins are physical items in inventories and belong to no bank, so a bank board counts the coins
        // carried by its own customers rather than everybody's - otherwise every board in the world would
        // quote the same figure and none of them would be about the bank whose name is on it.
        Set<UUID> members = bank == null ? null : new HashSet<>();
        if (members != null) {
            for (BankAccount account : accounts) members.add(account.owner());
        }
        long[] coinCounts = new long[CoinType.ORDERED.length]; long coinValue = 0L;
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        int counted = 0;
        for (ServerPlayer player : online) {
            if (members != null && !members.contains(player.getUUID())) continue;
            counted++;
            for (CoinType type : CoinType.ORDERED) {
                int carried = WalletManager.countCoins(player, type); coinCounts[type.ordinal()] += carried;
                long value = Money.multiply(type.cashValueCents(), carried);
                coinValue = Money.canAdd(coinValue, value) ? coinValue + value : Money.MAX_CENTS;
            }
        }
        long[] rates = new long[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            rates[type.ordinal()] = bank == null ? type.cashValueCents() : bank.rate(type);
        }
        long reserve = bank == null ? BankData.get(server).totalIssued() : bank.reserve();
        return new EconomySnapshot(count, counted, total, reserve, loansOut, average, median, richest,
                share, coinCounts, coinValue, rates, top, DisplaySettings.fromConfig());
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
