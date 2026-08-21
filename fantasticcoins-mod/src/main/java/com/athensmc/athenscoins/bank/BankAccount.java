package com.athensmc.athenscoins.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player's account at one bank. This is where the money actually lives; the wallet is a limited
 * card drawn against it.
 */
public class BankAccount {

    private int number;
    private UUID owner;
    private String ownerName;
    private UUID bankId;
    private long balance;
    private long openedAt;
    /** Anchor for commission accrual; advances by exactly what has been collected. */
    private long lastChargeAt;
    /** Fees that could not be taken because the account was short. */
    private long commissionDebt;
    @Nullable
    private Loan loan;
    private final List<LedgerEntry> ledger = new ArrayList<>();

    public BankAccount(int number, UUID owner, String ownerName, UUID bankId, long now) {
        this.number = number;
        this.owner = owner;
        this.ownerName = ownerName;
        this.bankId = bankId;
        this.openedAt = now;
        this.lastChargeAt = now;
    }

    private BankAccount() {
    }

    // ------------------------------------------------------------------ accessors

    public int number() {
        return number;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwnerName(String name) {
        this.ownerName = name;
    }

    public UUID bankId() {
        return bankId;
    }

    public long balance() {
        return balance;
    }

    public long openedAt() {
        return openedAt;
    }

    public long lastChargeAt() {
        return lastChargeAt;
    }

    public void setLastChargeAt(long value) {
        this.lastChargeAt = value;
    }

    public long commissionDebt() {
        return commissionDebt;
    }

    public void addCommissionDebt(long amount) {
        commissionDebt = Math.max(0L, commissionDebt + amount);
    }

    @Nullable
    public Loan loan() {
        return loan;
    }

    public void setLoan(@Nullable Loan value) {
        this.loan = value;
    }

    public List<LedgerEntry> ledger() {
        return ledger;
    }

    // ------------------------------------------------------------------ money

    public void credit(long amount, LedgerEntry.Kind kind, String note, long now) {
        if (amount <= 0L) {
            return;
        }
        balance += amount;
        record(kind, amount, note, now);
    }

    /** Debits only if the balance covers it. */
    public boolean debit(long amount, LedgerEntry.Kind kind, String note, long now) {
        if (amount <= 0L || balance < amount) {
            return false;
        }
        balance -= amount;
        record(kind, -amount, note, now);
        return true;
    }

    /** Takes what it can, returning how much was actually taken. Used for fees. */
    public long debitPartial(long amount, LedgerEntry.Kind kind, String note, long now) {
        long taken = Math.max(0L, Math.min(amount, balance));
        if (taken <= 0L) {
            return 0L;
        }
        balance -= taken;
        record(kind, -taken, note, now);
        return taken;
    }

    public void record(LedgerEntry.Kind kind, long amount, String note, long now) {
        ledger.add(new LedgerEntry(now, kind, amount, balance, note == null ? "" : note));
        while (ledger.size() > Bank.LEDGER_LIMIT) {
            ledger.remove(0);
        }
    }

    /** Most recent entries first, for display. */
    public List<LedgerEntry> recent(int count) {
        int from = Math.max(0, ledger.size() - count);
        List<LedgerEntry> out = new ArrayList<>(ledger.subList(from, ledger.size()));
        java.util.Collections.reverse(out);
        return out;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("number", number);
        tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName == null ? "" : ownerName);
        tag.putUUID("bank", bankId);
        tag.putLong("balance", balance);
        tag.putLong("openedAt", openedAt);
        tag.putLong("lastCharge", lastChargeAt);
        tag.putLong("feeDebt", commissionDebt);
        if (loan != null && !loan.settled()) {
            tag.put("loan", loan.save());
        }
        ListTag list = new ListTag();
        for (LedgerEntry entry : ledger) {
            list.add(entry.save());
        }
        tag.put("ledger", list);
        return tag;
    }

    public static BankAccount load(CompoundTag tag) {
        BankAccount account = new BankAccount();
        account.number = tag.getInt("number");
        account.owner = tag.getUUID("owner");
        account.ownerName = tag.getString("ownerName");
        account.bankId = tag.getUUID("bank");
        account.balance = tag.getLong("balance");
        account.openedAt = tag.getLong("openedAt");
        account.lastChargeAt = tag.getLong("lastCharge");
        account.commissionDebt = tag.getLong("feeDebt");
        if (tag.contains("loan")) {
            account.loan = Loan.load(tag.getCompound("loan"));
        }
        ListTag list = tag.getList("ledger", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            account.ledger.add(LedgerEntry.load(list.getCompound(i)));
        }
        return account;
    }

    /** Full detail for the banker's account view. */
    public void write(FriendlyByteBuf buffer, int ledgerCount) {
        buffer.writeVarInt(number);
        buffer.writeUUID(owner);
        buffer.writeUtf(ownerName == null ? "" : ownerName, 32);
        buffer.writeUUID(bankId);
        buffer.writeVarLong(balance);
        buffer.writeLong(openedAt);
        buffer.writeLong(lastChargeAt);
        buffer.writeVarLong(commissionDebt);
        buffer.writeBoolean(loan != null && !loan.settled());
        if (loan != null && !loan.settled()) {
            loan.write(buffer);
        }
        List<LedgerEntry> entries = recent(ledgerCount);
        buffer.writeVarInt(entries.size());
        for (LedgerEntry entry : entries) {
            entry.write(buffer);
        }
    }

    public static BankAccount read(FriendlyByteBuf buffer) {
        BankAccount account = new BankAccount();
        account.number = buffer.readVarInt();
        account.owner = buffer.readUUID();
        account.ownerName = buffer.readUtf(32);
        account.bankId = buffer.readUUID();
        account.balance = buffer.readVarLong();
        account.openedAt = buffer.readLong();
        account.lastChargeAt = buffer.readLong();
        account.commissionDebt = buffer.readVarLong();
        if (buffer.readBoolean()) {
            account.loan = Loan.read(buffer);
        }
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            account.ledger.add(LedgerEntry.read(buffer));
        }
        return account;
    }
}
