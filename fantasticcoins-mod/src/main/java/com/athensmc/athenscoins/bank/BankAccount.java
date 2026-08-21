package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The principal balance held by a bank. Wallet cash is only a limited electronic withdrawal. */
public class BankAccount {
    private int number;
    private UUID owner;
    private String ownerName;
    private UUID bankId;
    private long balance;
    /** Limited electronic cash withdrawn from this principal; persisted in the same transaction domain. */
    private long walletBalance;
    private long openedAt;
    private long lastChargeAt;
    private long commissionDebt;
    @Nullable private Loan loan;
    private final List<LedgerEntry> ledger = new ArrayList<>();
    /** Catch-up budget already consumed in this server session; deliberately not persisted. */
    private transient int commissionCatchUpUsed;
    private transient long commissionSessionStartedAt;
    private transient int commissionCurrentCyclesCharged;

    public BankAccount(int number, UUID owner, String ownerName, UUID bankId, long now) {
        this.number = number;
        this.owner = owner;
        this.ownerName = ownerName;
        this.bankId = bankId;
        this.openedAt = now;
        this.lastChargeAt = now;
    }

    private BankAccount() {}

    public int number() { return number; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public void setOwnerName(String name) { ownerName = name; }
    public UUID bankId() { return bankId; }
    public long balance() { return balance; }
    public long walletBalance() { return walletBalance; }
    public boolean canCreditWallet(long amount) { return amount > 0L && Money.canAdd(walletBalance, amount); }
    public boolean creditWallet(long amount) { if (!canCreditWallet(amount)) return false; walletBalance += amount; return true; }
    public boolean debitWallet(long amount) { if (amount <= 0L || walletBalance < amount) return false; walletBalance -= amount; return true; }
    public void clearWallet() { walletBalance = 0L; }
    public long openedAt() { return openedAt; }
    public long lastChargeAt() { return lastChargeAt; }
    public void setLastChargeAt(long value) { lastChargeAt = Math.max(openedAt, value); }
    public long commissionDebt() { return commissionDebt; }
    public void setCommissionDebt(long value) { commissionDebt = Money.clampBalance(value); }
    public boolean addCommissionDebt(long amount) {
        if (amount <= 0L) return true;
        if (!Money.canAdd(commissionDebt, amount)) return false;
        commissionDebt += amount;
        return true;
    }
    public int commissionCatchUpUsed() { return commissionCatchUpUsed; }
    public void useCommissionCatchUp(int count) { commissionCatchUpUsed += Math.max(0, count); }
    public long commissionSessionStartedAt() { return commissionSessionStartedAt; }
    public void beginCommissionSession(long now) { if (commissionSessionStartedAt <= 0L) commissionSessionStartedAt = now; }
    public int commissionCurrentCyclesCharged() { return commissionCurrentCyclesCharged; }
    public void chargeCurrentCommissionCycles(int count) { commissionCurrentCyclesCharged += Math.max(0, count); }
    public void resetCommissionSession() { commissionCatchUpUsed = 0; commissionSessionStartedAt = 0L; commissionCurrentCyclesCharged = 0; }
    @Nullable public Loan loan() { return loan; }
    public void setLoan(@Nullable Loan value) { loan = value; }
    public List<LedgerEntry> ledger() { return ledger; }

    public boolean canCredit(long amount) { return amount > 0L && Money.canAdd(balance, amount); }

    public boolean credit(long amount, LedgerEntry.Kind kind, String note, long now) {
        return credit(amount, kind, note, now, LedgerEntry.correlation(), owner.toString(), "account");
    }

    public boolean credit(long amount, LedgerEntry.Kind kind, String note, long now,
                          String correlation, String actor, String source) {
        if (!canCredit(amount)) return false;
        long before = balance;
        balance += amount;
        record(kind, amount, before, balance, note, now, correlation, actor, source);
        return true;
    }

    public boolean debit(long amount, LedgerEntry.Kind kind, String note, long now) {
        return debit(amount, kind, note, now, LedgerEntry.correlation(), owner.toString(), "account");
    }

    public boolean debit(long amount, LedgerEntry.Kind kind, String note, long now,
                         String correlation, String actor, String source) {
        if (amount <= 0L || balance < amount) return false;
        long before = balance;
        balance -= amount;
        record(kind, -amount, before, balance, note, now, correlation, actor, source);
        return true;
    }

    public long debitPartial(long amount, LedgerEntry.Kind kind, String note, long now,
                             String correlation, String actor, String source) {
        long taken = Math.max(0L, Math.min(amount, balance));
        if (taken <= 0L) return 0L;
        long before = balance;
        balance -= taken;
        record(kind, -taken, before, balance, note, now, correlation, actor, source);
        return taken;
    }

    public long debitPartial(long amount, LedgerEntry.Kind kind, String note, long now) {
        return debitPartial(amount, kind, note, now, LedgerEntry.correlation(), owner.toString(), "account");
    }

    public void record(LedgerEntry.Kind kind, long amount, String note, long now) {
        record(kind, amount, balance - amount, balance, note, now,
                LedgerEntry.correlation(), owner.toString(), "account");
    }

    public void recordExternal(LedgerEntry.Kind kind, long amount, long before, long after,
                               String note, long now, String correlation, String actor, String source) {
        record(kind, amount, before, after, note, now, correlation, actor, source);
    }

    private void record(LedgerEntry.Kind kind, long amount, long before, long after, String note,
                        long now, String correlation, String actor, String source) {
        ledger.add(new LedgerEntry(now, kind, amount, before, after, correlation, actor, source, note));
        while (ledger.size() > Bank.LEDGER_LIMIT) ledger.remove(0);
    }

    public List<LedgerEntry> recent(int count) {
        int from = Math.max(0, ledger.size() - count);
        List<LedgerEntry> out = new ArrayList<>(ledger.subList(from, ledger.size()));
        java.util.Collections.reverse(out);
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("number", number);
        tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName == null ? "" : ownerName);
        tag.putUUID("bank", bankId);
        tag.putLong("balance", balance);
        tag.putLong("walletBalance", walletBalance);
        tag.putLong("openedAt", openedAt);
        tag.putLong("lastCharge", lastChargeAt);
        tag.putLong("feeDebt", commissionDebt);
        if (loan != null && !loan.settled()) tag.put("loan", loan.save());
        ListTag list = new ListTag();
        for (LedgerEntry entry : ledger) list.add(entry.save());
        tag.put("ledger", list);
        return tag;
    }

    public static BankAccount load(CompoundTag tag) {
        BankAccount account = new BankAccount();
        account.number = tag.getInt("number");
        account.owner = tag.getUUID("owner");
        account.ownerName = tag.getString("ownerName");
        account.bankId = tag.getUUID("bank");
        account.balance = Math.max(0L, tag.getLong("balance"));
        account.walletBalance = Math.max(0L, tag.getLong("walletBalance"));
        account.openedAt = tag.getLong("openedAt");
        account.lastChargeAt = tag.contains("lastCharge") ? tag.getLong("lastCharge") : account.openedAt;
        account.commissionDebt = Math.max(0L, tag.getLong("feeDebt"));
        if (tag.contains("loan")) account.loan = Loan.load(tag.getCompound("loan"));
        ListTag list = tag.getList("ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) account.ledger.add(LedgerEntry.load(list.getCompound(i)));
        return account;
    }

    public void write(FriendlyByteBuf buffer, int ledgerCount) {
        buffer.writeVarInt(number); buffer.writeUUID(owner); buffer.writeUtf(ownerName == null ? "" : ownerName, 32);
        buffer.writeUUID(bankId); buffer.writeVarLong(balance); buffer.writeVarLong(walletBalance); buffer.writeLong(openedAt);
        buffer.writeLong(lastChargeAt); buffer.writeVarLong(commissionDebt);
        buffer.writeBoolean(loan != null && !loan.settled());
        if (loan != null && !loan.settled()) loan.write(buffer);
        List<LedgerEntry> entries = recent(ledgerCount);
        buffer.writeVarInt(entries.size());
        for (LedgerEntry entry : entries) entry.write(buffer);
    }

    public static BankAccount read(FriendlyByteBuf buffer) {
        BankAccount account = new BankAccount();
        account.number = buffer.readVarInt(); account.owner = buffer.readUUID(); account.ownerName = buffer.readUtf(32);
        account.bankId = buffer.readUUID(); account.balance = buffer.readVarLong(); account.walletBalance = buffer.readVarLong(); account.openedAt = buffer.readLong();
        account.lastChargeAt = buffer.readLong(); account.commissionDebt = buffer.readVarLong();
        if (buffer.readBoolean()) account.loan = Loan.read(buffer);
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) account.ledger.add(LedgerEntry.read(buffer));
        return account;
    }
}
