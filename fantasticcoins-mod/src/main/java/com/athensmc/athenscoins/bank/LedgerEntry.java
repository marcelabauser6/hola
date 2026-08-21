package com.athensmc.athenscoins.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** One movement on an account, kept so the banker can audit what happened. */
public record LedgerEntry(long at, Kind kind, long amount, long balanceAfter, String note) {

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
            return "ledger.athens_coins." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("at", at);
        tag.putInt("kind", kind.ordinal());
        tag.putLong("amount", amount);
        tag.putLong("after", balanceAfter);
        if (!note.isEmpty()) {
            tag.putString("note", note);
        }
        return tag;
    }

    public static LedgerEntry load(CompoundTag tag) {
        return new LedgerEntry(tag.getLong("at"), Kind.byOrdinal(tag.getInt("kind")),
                tag.getLong("amount"), tag.getLong("after"), tag.getString("note"));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(at);
        buffer.writeByte(kind.ordinal());
        buffer.writeVarLong(amount);
        buffer.writeVarLong(balanceAfter);
        buffer.writeUtf(note, 48);
    }

    public static LedgerEntry read(FriendlyByteBuf buffer) {
        return new LedgerEntry(buffer.readLong(), Kind.byOrdinal(buffer.readByte()),
                buffer.readVarLong(), buffer.readVarLong(), buffer.readUtf(48));
    }
}
