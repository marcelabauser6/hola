package com.athensmc.athenscoins.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;
import java.util.UUID;

/** One auditable movement, with enough context to correlate both sides of a transaction. */
public record LedgerEntry(long at, Kind kind, long amount, long balanceBefore, long balanceAfter,
                          String correlationId, String actor, String source, String note) {

    /** Ordinals are persisted, so append only. */
    public enum Kind {
        OPENED,
        COIN_DEPOSIT,
        COIN_WITHDRAW,
        WALLET_OUT,
        WALLET_IN,
        SHOP_SALE,
        SHOP_PURCHASE,
        TRANSFER_SENT,
        TRANSFER_RECEIVED,
        COMMISSION,
        LOAN_GRANTED,
        LOAN_REPAID,
        LOAN_INTEREST,
        CENTRAL_INJECTION,
        CLOSED;

        private static final Kind[] VALUES = values();

        public static Kind byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : COMMISSION;
        }

        public String translationKey() {
            return "ledger.athens_coins." + name().toLowerCase(Locale.ROOT);
        }
    }

    public LedgerEntry {
        correlationId = correlationId == null ? "" : correlationId;
        actor = actor == null ? "" : actor;
        source = source == null ? "" : source;
        note = note == null ? "" : note;
    }

    /** Creates a new event correlation id. */
    public static String correlation() {
        return UUID.randomUUID().toString();
    }

    /** Compatibility constructor for old callers. */
    public LedgerEntry(long at, Kind kind, long amount, long balanceAfter, String note) {
        this(at, kind, amount, balanceAfter - amount, balanceAfter,
                correlation(), "system", "legacy", note);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("at", at);
        tag.putInt("kind", kind.ordinal());
        tag.putLong("amount", amount);
        tag.putLong("before", balanceBefore);
        tag.putLong("after", balanceAfter);
        if (!correlationId.isEmpty()) tag.putString("correlation", correlationId);
        if (!actor.isEmpty()) tag.putString("actor", actor);
        if (!source.isEmpty()) tag.putString("source", source);
        if (!note.isEmpty()) tag.putString("note", note);
        return tag;
    }

    public static LedgerEntry load(CompoundTag tag) {
        long amount = tag.getLong("amount");
        long after = tag.getLong("after");
        long before = tag.contains("before") ? tag.getLong("before") : after - amount;
        return new LedgerEntry(tag.getLong("at"), Kind.byOrdinal(tag.getInt("kind")), amount,
                before, after, tag.getString("correlation"), tag.getString("actor"),
                tag.getString("source"), tag.getString("note"));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(at);
        buffer.writeByte(kind.ordinal());
        buffer.writeVarLong(amount);
        buffer.writeVarLong(balanceBefore);
        buffer.writeVarLong(balanceAfter);
        buffer.writeUtf(correlationId, 64);
        buffer.writeUtf(actor, 64);
        buffer.writeUtf(source, 48);
        buffer.writeUtf(note, 96);
    }

    public static LedgerEntry read(FriendlyByteBuf buffer) {
        return new LedgerEntry(buffer.readLong(), Kind.byOrdinal(buffer.readByte()),
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(48), buffer.readUtf(96));
    }
}
