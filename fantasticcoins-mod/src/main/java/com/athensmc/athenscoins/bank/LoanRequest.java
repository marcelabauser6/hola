package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * A customer asking their bank to lend them money.
 *
 * <p>Borrowing used to be self-service: the ATM had a "borrow" button that called
 * {@code BankManager.grantLoan} directly, so any customer could walk up at any hour and draw the
 * bank's reserve down to nothing without anybody at the bank agreeing to it. A loan is an agreement
 * between two parties, so it needs both of them: the customer files this, and a banker approves or
 * refuses it at the terminal.</p>
 *
 * <p>Persisted rather than kept in memory like a transfer request. A transfer is a conversation
 * between two people who are both online right now; a loan application waits for whenever a banker
 * next logs in, and losing the queue on restart would mean customers silently having to ask again.</p>
 */
public record LoanRequest(int id,
                          UUID bankId,
                          UUID borrower,
                          String borrowerName,
                          int account,
                          long amount,
                          long requestedAt,
                          String note) {

    /** Longest note a customer can attach, matching the packet's string cap. */
    public static final int MAX_NOTE = 48;

    public static LoanRequest of(int id, UUID bankId, BankAccount account, String borrowerName,
                                long amount, String note, long now) {
        return new LoanRequest(id, bankId, account.owner(), borrowerName, account.number(),
                Money.clampBalance(amount), now, trim(note));
    }

    private static String trim(String note) {
        if (note == null) {
            return "";
        }
        String cleaned = note.trim();
        return cleaned.length() <= MAX_NOTE ? cleaned : cleaned.substring(0, MAX_NOTE);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putUUID("bank", bankId);
        tag.putUUID("borrower", borrower);
        tag.putString("name", borrowerName == null ? "" : borrowerName);
        tag.putInt("account", account);
        tag.putLong("amount", amount);
        tag.putLong("at", requestedAt);
        tag.putString("note", note == null ? "" : note);
        return tag;
    }

    public static LoanRequest load(CompoundTag tag) {
        return new LoanRequest(tag.getInt("id"), tag.getUUID("bank"), tag.getUUID("borrower"),
                tag.getString("name"), tag.getInt("account"),
                Money.clampBalance(tag.getLong("amount")), tag.getLong("at"), tag.getString("note"));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeUUID(borrower);
        buffer.writeUtf(borrowerName == null ? "" : borrowerName, 32);
        buffer.writeVarInt(account);
        buffer.writeVarLong(amount);
        buffer.writeLong(requestedAt);
        buffer.writeUtf(note == null ? "" : note, MAX_NOTE);
    }

    /** Reads the client-facing form; {@code bankId} is implied by the terminal that sent it. */
    public static LoanRequest read(FriendlyByteBuf buffer, UUID bankId) {
        return new LoanRequest(buffer.readVarInt(), bankId, buffer.readUUID(), buffer.readUtf(32),
                buffer.readVarInt(), buffer.readVarLong(), buffer.readLong(),
                buffer.readUtf(MAX_NOTE));
    }
}
