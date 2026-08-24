package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.bank.Loan;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything the ATM screen shows, captured on the server in one place.
 *
 * <p>The ATM used to sync a handful of loose fields written inline in two spots that had to be kept
 * in step by hand. Now that the machine also settles loans and starts transfers it needs the loan,
 * the bank's lending policy and the list of people the player could pay, so the state is a single
 * record with one writer and one reader. Getting the order wrong is a compile error rather than a
 * stream of garbled numbers.</p>
 *
 * <p>The snapshot is taken when the screen opens and again after every action, so what the player
 * reads is what the server had a moment ago. It is only ever advisory: the server revalidates every
 * amount when the action arrives, because a client can send anything.</p>
 */
public record AtmState(
        BlockPos atmPos,
        long cash,
        long accountBalance,
        long walletLimit,
        int[] coinCounts,
        long[] rates,
        String bankName,
        int themeColor,
        boolean loansEnabled,
        long loanMax,
        int loanDays,
        int loanInterestBasisPoints,
        boolean hasLoan,
        long loanPrincipal,
        long loanOwed,
        long loanDueAt,
        int overdueDays,
        long commissionDebt,
        List<Peer> peers) {

    /** Someone online the player could transfer money to. */
    public record Peer(UUID id, String name) {
    }

    /** Sized so a full server's player list still fits comfortably in one packet. */
    private static final int MAX_PEERS = 96;

    /** The state of an ATM whose bank vanished; the screen renders it as "unavailable". */
    public static AtmState empty() {
        return new AtmState(BlockPos.ZERO, 0L, 0L, 0L,
                new int[CoinType.ORDERED.length], new long[CoinType.ORDERED.length],
                "", 0x2E4756, false, 0L, 0, 0,
                false, 0L, 0L, 0L, 0, 0L, List.of());
    }

    /**
     * Reads the player's live position at this bank.
     *
     * <p>Peers are filtered to players who actually hold an account, because a transfer to someone
     * without one cannot succeed: offering them as a target would only produce a refusal after the
     * fact.</p>
     */
    public static AtmState capture(ServerPlayer player, Bank bank, BlockPos atmPos) {
        BankAccount account = BankManager.accountOf(player);
        long now = System.currentTimeMillis();
        Loan loan = account == null ? null : account.loan();
        boolean live = loan != null && !loan.settled();

        int[] counts = WalletManager.countAllCoins(player);
        long[] rates = new long[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            rates[type.ordinal()] = bank.rate(type);
        }

        List<Peer> peers = new ArrayList<>();
        for (ServerPlayer candidate : player.server.getPlayerList().getPlayers()) {
            if (peers.size() >= MAX_PEERS) {
                break;
            }
            if (candidate.getUUID().equals(player.getUUID()) || !BankManager.hasAccount(candidate)) {
                continue;
            }
            peers.add(new Peer(candidate.getUUID(), candidate.getGameProfile().getName()));
        }
        peers.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));

        return new AtmState(
                atmPos,
                WalletManager.accountOf(player).balance(),
                account == null ? 0L : account.balance(),
                bank.walletLimit(),
                counts,
                rates,
                bank.name(),
                bank.themeColor(),
                bank.loansEnabled(),
                bank.loanMaxAmount(),
                bank.loanDays(),
                bank.loanInterestBasisPoints(),
                live,
                live ? loan.principal() : 0L,
                live ? loan.owed() : 0L,
                live ? loan.dueAt() : 0L,
                live ? BankRules.overdueBusinessDays(loan.dueAt(), now) : 0,
                account == null ? 0L : account.commissionDebt(),
                List.copyOf(peers));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(atmPos);
        buffer.writeVarLong(cash);
        buffer.writeVarLong(accountBalance);
        buffer.writeVarLong(walletLimit);
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            buffer.writeVarInt(coinCounts[i]);
            buffer.writeVarLong(rates[i]);
        }
        buffer.writeUtf(bankName, 32);
        buffer.writeInt(themeColor);
        buffer.writeBoolean(loansEnabled);
        buffer.writeVarLong(loanMax);
        buffer.writeVarInt(loanDays);
        buffer.writeVarInt(loanInterestBasisPoints);
        buffer.writeBoolean(hasLoan);
        buffer.writeVarLong(loanPrincipal);
        buffer.writeVarLong(loanOwed);
        buffer.writeLong(loanDueAt);
        buffer.writeVarInt(overdueDays);
        buffer.writeVarLong(commissionDebt);
        buffer.writeVarInt(peers.size());
        for (Peer peer : peers) {
            buffer.writeUUID(peer.id());
            buffer.writeUtf(peer.name(), 16);
        }
    }

    public static AtmState read(FriendlyByteBuf buffer) {
        BlockPos atmPos = buffer.readBlockPos();
        long cash = buffer.readVarLong();
        long accountBalance = buffer.readVarLong();
        long walletLimit = buffer.readVarLong();
        int[] counts = new int[CoinType.ORDERED.length];
        long[] rates = new long[CoinType.ORDERED.length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = buffer.readVarInt();
            rates[i] = buffer.readVarLong();
        }
        String bankName = buffer.readUtf(32);
        int themeColor = buffer.readInt();
        boolean loansEnabled = buffer.readBoolean();
        long loanMax = buffer.readVarLong();
        int loanDays = buffer.readVarInt();
        int loanInterest = buffer.readVarInt();
        boolean hasLoan = buffer.readBoolean();
        long loanPrincipal = buffer.readVarLong();
        long loanOwed = buffer.readVarLong();
        long loanDueAt = buffer.readLong();
        int overdueDays = buffer.readVarInt();
        long commissionDebt = buffer.readVarLong();
        int peerCount = Math.min(MAX_PEERS, buffer.readVarInt());
        List<Peer> peers = new ArrayList<>(peerCount);
        for (int i = 0; i < peerCount; i++) {
            peers.add(new Peer(buffer.readUUID(), buffer.readUtf(16)));
        }
        return new AtmState(atmPos, cash, accountBalance, walletLimit, counts, rates, bankName,
                themeColor, loansEnabled, loanMax, loanDays, loanInterest, hasLoan, loanPrincipal,
                loanOwed, loanDueAt, overdueDays, commissionDebt, List.copyOf(peers));
    }

    public int coinCount(CoinType type) {
        return coinCounts[type.ordinal()];
    }

    public long rate(CoinType type) {
        return rates[type.ordinal()];
    }

    /** How much more the wallet card can still hold, or {@code -1} when the bank sets no ceiling. */
    public long walletRoom() {
        return walletLimit <= 0L ? -1L : Math.max(0L, walletLimit - cash);
    }

    /** Replaces just the two figures the lightweight wallet sync carries. */
    public AtmState withCash(long newCash, int[] newCounts) {
        return new AtmState(atmPos, newCash, accountBalance, walletLimit, newCounts.clone(), rates,
                bankName, themeColor, loansEnabled, loanMax, loanDays, loanInterestBasisPoints,
                hasLoan, loanPrincipal, loanOwed, loanDueAt, overdueDays, commissionDebt, peers);
    }
}
