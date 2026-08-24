package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * An outstanding loan. Amounts in cents.
 *
 * <p>Penalty interest is <em>simple</em>: every charge is a percentage of {@link #principal}, never
 * of the running {@link #owed}. That distinction was the difference between a penalty a player can
 * plan for and one that runs away from them. {@code BankRules.overdueInterest} was already
 * documented as simple and is simple within a single call, but the ticker calls it once a second
 * against the outstanding amount, so yesterday's penalty silently became part of today's base and
 * the debt compounded daily at the full penalty rate.</p>
 *
 * <p>{@link #interestAccrued} exists so the total penalty can be capped. Without a running total
 * there is nothing to compare a ceiling against, and an overdue loan nobody collects grows without
 * bound - the mod has no default, write-off or bankruptcy path, so an uncapped debt is permanent.</p>
 */
public class Loan {

    private long principal;
    private long owed;
    private long takenAt;
    private long dueAt;
    /** Start of the last business day interest was applied for, so it is never applied twice. */
    private long lastInterestAt;
    /** Total penalty charged so far, which is what the interest ceiling is measured against. */
    private long interestAccrued;

    public Loan(long principal, long takenAt, long dueAt) {
        this.principal = Math.max(0L, principal);
        this.owed = this.principal;
        this.takenAt = takenAt;
        this.dueAt = dueAt;
        this.lastInterestAt = dueAt;
        this.interestAccrued = 0L;
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

    public long interestAccrued() {
        return interestAccrued;
    }

    public void setLastInterestAt(long value) {
        this.lastInterestAt = value;
    }

    /**
     * How much more penalty this loan may still take on.
     *
     * @param maxPercentOfPrincipal ceiling as a percentage of the principal; {@code <= 0} means the
     *                              penalty is uncapped
     */
    public long interestHeadroom(int maxPercentOfPrincipal) {
        if (maxPercentOfPrincipal <= 0) {
            return Money.MAX_CENTS;
        }
        long ceiling = principal / 100L * maxPercentOfPrincipal
                + principal % 100L * maxPercentOfPrincipal / 100L;
        return Math.max(0L, ceiling - interestAccrued);
    }

    public boolean addInterest(long amount) {
        if (amount <= 0L || !Money.canAdd(owed, amount)
                || !Money.canAdd(interestAccrued, amount)) {
            return false;
        }
        owed += amount;
        interestAccrued += amount;
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
        tag.putLong("interestAccrued", interestAccrued);
        return tag;
    }

    public static Loan load(CompoundTag tag) {
        Loan loan = new Loan();
        // Clamp on the way in: a corrupt or hand-edited value used to survive untouched, and a
        // negative or oversized amount then poisoned every later comparison.
        loan.principal = Money.clampBalance(tag.getLong("principal"));
        loan.owed = Money.clampBalance(tag.getLong("owed"));
        loan.takenAt = tag.getLong("takenAt");
        loan.dueAt = tag.getLong("dueAt");
        loan.lastInterestAt = tag.contains("lastInterestAt")
                ? tag.getLong("lastInterestAt") : loan.dueAt;
        // Loans written before the cap existed have no recorded total. Reconstructing it as
        // owed - principal is exact for an untouched loan and conservative for a partly repaid one.
        loan.interestAccrued = tag.contains("interestAccrued")
                ? Money.clampBalance(tag.getLong("interestAccrued"))
                : Math.max(0L, loan.owed - loan.principal);
        return loan;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarLong(principal);
        buffer.writeVarLong(owed);
        buffer.writeLong(takenAt);
        buffer.writeLong(dueAt);
        buffer.writeLong(lastInterestAt);
        buffer.writeVarLong(interestAccrued);
    }

    public static Loan read(FriendlyByteBuf buffer) {
        Loan loan = new Loan();
        loan.principal = buffer.readVarLong();
        loan.owed = buffer.readVarLong();
        loan.takenAt = buffer.readLong();
        loan.dueAt = buffer.readLong();
        // Previously reconstructed as dueAt, which told a viewing client that no interest had been
        // charged yet however long the loan had been overdue.
        loan.lastInterestAt = buffer.readLong();
        loan.interestAccrued = buffer.readVarLong();
        return loan;
    }
}
