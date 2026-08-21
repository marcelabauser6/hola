package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/** Pure-JVM persistence and transaction regression checks; it never starts Minecraft. */
public final class FinancialPersistenceStaticTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        long now = 1_700_000_000_000L;

        BankAccount account = new BankAccount(12345, owner, "holder", bankId, now);
        String correlation = LedgerEntry.correlation();
        check(account.credit(500L, LedgerEntry.Kind.SHOP_SALE, "sale", now,
                correlation, owner.toString(), "test"), "principal credit");
        check(account.debit(125L, LedgerEntry.Kind.SHOP_PURCHASE, "purchase", now + 1,
                correlation, owner.toString(), "test"), "principal debit");
        check(account.creditWallet(200L) && account.debitWallet(50L), "wallet mutation");
        LedgerEntry last = account.ledger().get(1);
        check(last.balanceBefore() == 500L && last.balanceAfter() == 375L, "ledger before/after");
        check(last.correlationId().equals(correlation) && last.source().equals("test"), "ledger context");
        BankAccount roundTrip = BankAccount.load(account.save());
        check(roundTrip.balance() == 375L && roundTrip.walletBalance() == 150L
                && roundTrip.ledger().size() == 2, "account + wallet atomic NBT round trip");

        CompoundTag legacyLedger = new CompoundTag();
        legacyLedger.putLong("at", now); legacyLedger.putInt("kind", LedgerEntry.Kind.WALLET_IN.ordinal());
        legacyLedger.putLong("amount", 25L); legacyLedger.putLong("after", 100L);
        LedgerEntry migratedLedger = LedgerEntry.load(legacyLedger);
        check(migratedLedger.balanceBefore() == 75L && migratedLedger.balanceAfter() == 100L,
                "old ledger reconstructs before balance");

        WalletData wallets = new WalletData();
        wallets.account(owner); // zero wallet must still be persisted as initialized storage
        CompoundTag walletTag = wallets.save(new CompoundTag());
        check(walletTag.getList("Wallets", Tag.TAG_COMPOUND).size() == 1,
                "zero wallet is persisted");
        wallets.markInitialized(owner);
        check(wallets.claimStartingBalance(owner) == 0L, "initialization cannot repeat");
        check(wallets.quarantine(owner, Money.MAX_CENTS + 7L), "large legacy value is quarantined");
        WalletData walletRoundTrip = WalletData.load(wallets.save(new CompoundTag()));
        check(walletRoundTrip.quarantined(owner) == Money.MAX_CENTS + 7L, "quarantine round trip");

        CompoundTag v2 = new CompoundTag(); v2.putInt("DataVersion", 2);
        ListTag oldWallets = new ListTag(); CompoundTag oldWallet = new CompoundTag();
        oldWallet.putUUID("Owner", UUID.randomUUID()); oldWallet.putLong("cash", 123L); oldWallets.add(oldWallet);
        v2.put("Wallets", oldWallets);
        WalletData pendingMigration = WalletData.load(v2);
        check(pendingMigration.migrationPending(), "v2 wallet migration remains pending");
        check(pendingMigration.save(new CompoundTag()).getInt("DataVersion") == 2,
                "pending migration does not falsely persist current version");

        BankData data = new BankData();
        BankData.CardRecord card = data.issueCard(owner, 900L, 12345, "Old Bank");
        check(!card.redeemed(), "new card is unredeemed");
        check(data.redeemCard(card.token(), owner, 900L, now), "first redemption succeeds");
        check(!data.redeemCard(card.token(), owner, 900L, now + 1), "replay is rejected");
        BankData cardRoundTrip = BankData.load(data.save(new CompoundTag()));
        check(cardRoundTrip.card(card.token()) != null && cardRoundTrip.card(card.token()).redeemed(),
                "redemption persists");

        CompoundTag orphanRoot = new CompoundTag();
        ListTag orphanList = new ListTag(); CompoundTag orphan = account.save();
        orphan.putLong("balance", Long.MAX_VALUE); orphanList.add(orphan); orphanRoot.put("Accounts", orphanList);
        BankData orphanData = BankData.load(orphanRoot);
        check(orphanData.orphanAccounts().size() == 1, "missing-bank account is retained");
        check(orphanData.orphanAccounts().get(0).balance() == Long.MAX_VALUE,
                "out-of-policy legacy balance is not truncated");

        System.out.println("Financial persistence verified: " + checks + " checks.");
    }
}
