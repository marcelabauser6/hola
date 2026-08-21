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
                             DisplaySettings display,
                             BankInfo bank) {

    /** The holder's bank, or {@link BankInfo#NONE} when they have no account. */
    public record BankInfo(boolean hasAccount, String name, int accountNumber, long accountCents,
                           long walletLimit, long commissionFee, int commissionDays,
                           long loanOwed, long loanDueAt, int themeColor) {

        public static final BankInfo NONE =
                new BankInfo(false, "", 0, 0L, 0L, 0L, 0, 0L, 0L, 0x2E4756);

        void write(net.minecraft.network.FriendlyByteBuf buffer) {
            buffer.writeBoolean(hasAccount);
            buffer.writeUtf(name, 32);
            buffer.writeVarInt(accountNumber);
            buffer.writeVarLong(accountCents);
            buffer.writeVarLong(walletLimit);
            buffer.writeVarLong(commissionFee);
            buffer.writeVarInt(commissionDays);
            buffer.writeVarLong(loanOwed);
            buffer.writeLong(loanDueAt);
            buffer.writeInt(themeColor);
        }

        static BankInfo read(net.minecraft.network.FriendlyByteBuf buffer) {
            return new BankInfo(buffer.readBoolean(), buffer.readUtf(32), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readVarLong(), buffer.readLong(),
                    buffer.readInt());
        }
    }

    public static WalletSnapshot of(ServerPlayer target) {
        return new WalletSnapshot(
                WalletManager.accountOf(target).balance(),
                WalletManager.countAllCoins(target),
                target.getUUID(),
                target.getGameProfile().getName(),
                WalletManager.findNearbyAtm(target) != null,
                DisplaySettings.fromConfig(),
                bankInfoOf(target));
    }

    private static BankInfo bankInfoOf(ServerPlayer target) {
        com.athensmc.athenscoins.bank.BankAccount account =
                com.athensmc.athenscoins.bank.BankManager.accountOf(target);
        com.athensmc.athenscoins.bank.Bank bank =
                com.athensmc.athenscoins.bank.BankManager.bankOf(target);
        if (account == null || bank == null) {
            return BankInfo.NONE;
        }
        com.athensmc.athenscoins.bank.Loan loan = account.loan();
        return new BankInfo(true, bank.name(), account.number(), account.balance(),
                bank.walletLimit(), bank.commissionFee(), bank.commissionPeriodDays(),
                loan == null ? 0L : loan.owed(), loan == null ? 0L : loan.dueAt(),
                bank.themeColor());
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
        bank.write(buffer);
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
        DisplaySettings settings = DisplaySettings.read(buffer);
        return new WalletSnapshot(cash, counts, owner, name, atm, settings, BankInfo.read(buffer));
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
