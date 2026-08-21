package com.athensmc.athenscoins.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** An outstanding loan. Amounts in cents. */
public class Loan {

    private long principal;
    private long owed;
    private long takenAt;
    private long dueAt;
    /** Start of the last business day interest was applied for, so it is never applied twice. */
    private long lastInterestAt;

    public Loan(long principal, long takenAt, long dueAt) {
        this.principal = Math.max(0L, principal);
        this.owed = this.principal;
        this.takenAt = takenAt;
        this.dueAt = dueAt;
        this.lastInterestAt = dueAt;
    }

    private Loan() {
    }

    public long principal() {
        return principal;
    }

    public long owed() {
        return owed;
    }

    public long takenAt() {
        return takenAt;
    }

    public long dueAt() {
        return dueAt;
    }

    public long lastInterestAt() {
        return lastInterestAt;
    }

    public void setLastInterestAt(long value) {
        this.lastInterestAt = value;
    }

    public boolean addInterest(long amount) {
        if (amount <= 0L || !com.athensmc.athenscoins.wallet.Money.canAdd(owed, amount)) {
            return false;
        }
        owed += amount;
        return true;
    }

    /** Pays what it can and returns how much was actually applied. */
    public long repay(long amount) {
        long applied = Math.max(0L, Math.min(amount, owed));
        owed -= applied;
        return applied;
    }

    public boolean settled() {
        return owed <= 0L;
    }

    public boolean overdue(long now) {
        return now > dueAt && !settled();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("principal", principal);
        tag.putLong("owed", owed);
        tag.putLong("takenAt", takenAt);
        tag.putLong("dueAt", dueAt);
        tag.putLong("lastInterestAt", lastInterestAt);
        return tag;
    }

    public static Loan load(CompoundTag tag) {
        Loan loan = new Loan();
        loan.principal = tag.getLong("principal");
        loan.owed = tag.getLong("owed");
        loan.takenAt = tag.getLong("takenAt");
        loan.dueAt = tag.getLong("dueAt");
        loan.lastInterestAt = tag.getLong("lastInterestAt");
        return loan;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarLong(principal);
        buffer.writeVarLong(owed);
        buffer.writeLong(takenAt);
        buffer.writeLong(dueAt);
    }

    public static Loan read(FriendlyByteBuf buffer) {
        Loan loan = new Loan();
        loan.principal = buffer.readVarLong();
        loan.owed = buffer.readVarLong();
        loan.takenAt = buffer.readLong();
        loan.dueAt = buffer.readLong();
        loan.lastInterestAt = loan.dueAt;
        return loan;
    }
}
