package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A financial entity. Several can coexist and players pick whichever suits them.
 *
 * <p>Its reserve is real money: loans come out of it and commissions go into it, so a bank that
 * charges nothing eventually cannot lend anything. The central bank can top it up.</p>
 */
public class Bank {

    /** Cap on ledger history per account, so the world file cannot grow without bound. */
    public static final int LEDGER_LIMIT = 100;

    private UUID id;
    private String name;
    /** Identity colour, used by its GUIs and stamped onto the ATMs it issues. */
    private int themeColor;
    private long reserve;
    /** Ceiling on electronic cash a holder may carry. 0 means unlimited. */
    private long walletLimit;
    /** Fixed fee, in cents, charged every commissionPeriodDays. */
    private long commissionFee;
    private int commissionPeriodDays;
    /** This bank's own coin rates in cents, always kept inside the official band. */
    private final long[] rates = new long[CoinType.ORDERED.length];
    private boolean loansEnabled;
    private long loanMaxAmount;
    private int loanDays;
    private int loanInterestBasisPoints;
    private final Set<UUID> bankers = new HashSet<>();
    @Nullable
    private BlockPos terminalPos;

    public Bank(UUID id, String name, BlockPos terminalPos) {
        this.id = id;
        this.name = name;
        this.terminalPos = terminalPos;
        this.themeColor = 0x2E4756;
        this.walletLimit = 25_000_00L;
        this.commissionFee = 50L;
        this.commissionPeriodDays = 1;
        this.loansEnabled = true;
        this.loanMaxAmount = 5_000_00L;
        this.loanDays = 5;
        this.loanInterestBasisPoints = 250;
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        for (CoinType type : CoinType.ORDERED) {
            rates[type.ordinal()] = settings.coinValueCents(type);
        }
    }

    private Bank() {
    }

    // ------------------------------------------------------------------ accessors

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String value) {
        this.name = value == null || value.isBlank() ? "Banco" : value.substring(0, Math.min(32, value.length()));
    }

    public int themeColor() {
        return themeColor;
    }

    public void setThemeColor(int value) {
        this.themeColor = value & 0xFFFFFF;
    }

    public long reserve() {
        return reserve;
    }

    /** Adds to reserves only when the full amount fits. */
    public boolean addReserve(long amount) {
        if (amount <= 0L || !com.athensmc.athenscoins.wallet.Money.canAdd(reserve, amount)) {
            return false;
        }
        reserve += amount;
        return true;
    }

    /** Takes from the reserve only if it covers the amount. */
    public boolean drawReserve(long amount) {
        if (amount <= 0L || reserve < amount) {
            return false;
        }
        reserve -= amount;
        return true;
    }

    public long walletLimit() {
        return walletLimit;
    }

    public void setWalletLimit(long value) {
        this.walletLimit = Math.max(0L, value);
    }

    public long commissionFee() {
        return commissionFee;
    }

    public void setCommissionFee(long value) {
        this.commissionFee = Math.max(0L, value);
    }

    public int commissionPeriodDays() {
        return commissionPeriodDays;
    }

    public void setCommissionPeriodDays(int value) {
        this.commissionPeriodDays = Math.max(1, Math.min(30, value));
    }

    /** This bank's rate for a coin, forced into the official band on the way out. */
    public long rate(CoinType type) {
        long official = CurrencyConfig.get().coinValueCents(type);
        int margin = CurrencyConfig.get().rateMarginPercent;
        return BankRules.clampRate(rates[type.ordinal()], official, margin);
    }

    public long storedRate(CoinType type) {
        return rates[type.ordinal()];
    }

    public void setRate(CoinType type, long cents) {
        long official = CurrencyConfig.get().coinValueCents(type);
        int margin = CurrencyConfig.get().rateMarginPercent;
        rates[type.ordinal()] = BankRules.clampRate(cents, official, margin);
    }

    /** Pulls every rate back inside the band, used after the central bank moves an official rate. */
    public void realignRates() {
        for (CoinType type : CoinType.ORDERED) {
            rates[type.ordinal()] = rate(type);
        }
    }

    public boolean loansEnabled() {
        return loansEnabled;
    }

    public void setLoansEnabled(boolean value) {
        this.loansEnabled = value;
    }

    public long loanMaxAmount() {
        return loanMaxAmount;
    }

    public void setLoanMaxAmount(long value) {
        this.loanMaxAmount = Math.max(0L, value);
    }

    public int loanDays() {
        return loanDays;
    }

    public void setLoanDays(int value) {
        this.loanDays = Math.max(1, Math.min(60, value));
    }

    public int loanInterestBasisPoints() {
        return loanInterestBasisPoints;
    }

    public void setLoanInterestBasisPoints(int value) {
        this.loanInterestBasisPoints = Math.max(0, Math.min(10_000, value));
    }

    public Set<UUID> bankers() {
        return bankers;
    }

    public boolean isBanker(UUID player) {
        return bankers.contains(player);
    }

    @Nullable
    public BlockPos terminalPos() {
        return terminalPos;
    }

    public void setTerminalPos(@Nullable BlockPos pos) {
        this.terminalPos = pos;
    }

    /** A bank whose terminal was broken survives but cannot be operated. */
    public boolean hasSeat() {
        return terminalPos != null;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putInt("color", themeColor);
        tag.putLong("reserve", reserve);
        tag.putLong("walletLimit", walletLimit);
        tag.putLong("fee", commissionFee);
        tag.putInt("feeDays", commissionPeriodDays);
        tag.putLongArray("rates", rates.clone());
        tag.putBoolean("loans", loansEnabled);
        tag.putLong("loanMax", loanMaxAmount);
        tag.putInt("loanDays", loanDays);
        tag.putInt("loanBp", loanInterestBasisPoints);
        ListTag list = new ListTag();
        for (UUID banker : bankers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", banker);
            list.add(entry);
        }
        tag.put("bankers", list);
        if (terminalPos != null) {
            tag.put("terminal", NbtUtils.writeBlockPos(terminalPos));
        }
        return tag;
    }

    public static Bank load(CompoundTag tag) {
        Bank bank = new Bank();
        bank.id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        bank.name = tag.getString("name");
        bank.themeColor = tag.getInt("color");
        // Clamp the money fields on the way in, as BankAccount.load already did. Read raw, a
        // negative reserve from a corrupt or hand-edited save made drawReserve refuse everything and
        // maxLoan return zero, so lending stopped permanently with no error anywhere to explain it.
        bank.reserve = Money.clampBalance(tag.getLong("reserve"));
        bank.walletLimit = Math.max(0L, tag.getLong("walletLimit"));
        bank.commissionFee = Money.clampBalance(tag.getLong("fee"));
        bank.commissionPeriodDays = Math.max(1, tag.getInt("feeDays"));
        long[] stored = tag.getLongArray("rates");
        for (int i = 0; i < bank.rates.length; i++) {
            bank.rates[i] = i < stored.length ? stored[i] : 0L;
        }
        // getBoolean returns false for a missing key, so a bank saved before this flag existed came
        // back with lending switched off. Absent means "keep the default", which is on.
        bank.loansEnabled = !tag.contains("loans") || tag.getBoolean("loans");
        bank.loanMaxAmount = Money.clampBalance(tag.getLong("loanMax"));
        // Same bounds the setters enforce, so a save can never carry an out-of-policy value.
        bank.loanDays = Math.max(1, Math.min(60, tag.getInt("loanDays")));
        bank.loanInterestBasisPoints = Math.max(0, Math.min(10_000, tag.getInt("loanBp")));
        ListTag list = tag.getList("bankers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("id")) {
                bank.bankers.add(entry.getUUID("id"));
            }
        }
        bank.terminalPos = tag.contains("terminal")
                ? NbtUtils.readBlockPos(tag.getCompound("terminal"))
                : null;
        return bank;
    }

    /** Everything a client needs to render this bank's identity and terms. */
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeUtf(name, 32);
        buffer.writeInt(themeColor);
        buffer.writeVarLong(reserve);
        buffer.writeVarLong(walletLimit);
        buffer.writeVarLong(commissionFee);
        buffer.writeVarInt(commissionPeriodDays);
        for (CoinType type : CoinType.ORDERED) {
            buffer.writeVarLong(rate(type));
        }
        buffer.writeBoolean(loansEnabled);
        buffer.writeVarLong(loanMaxAmount);
        buffer.writeVarInt(loanDays);
        buffer.writeVarInt(loanInterestBasisPoints);
        buffer.writeVarInt(bankers.size());
        for (UUID banker : bankers) {
            buffer.writeUUID(banker);
        }
    }

    public static Bank read(FriendlyByteBuf buffer) {
        Bank bank = new Bank();
        bank.id = buffer.readUUID();
        bank.name = buffer.readUtf(32);
        bank.themeColor = buffer.readInt();
        bank.reserve = buffer.readVarLong();
        bank.walletLimit = buffer.readVarLong();
        bank.commissionFee = buffer.readVarLong();
        bank.commissionPeriodDays = buffer.readVarInt();
        for (int i = 0; i < bank.rates.length; i++) {
            bank.rates[i] = buffer.readVarLong();
        }
        bank.loansEnabled = buffer.readBoolean();
        bank.loanMaxAmount = buffer.readVarLong();
        bank.loanDays = buffer.readVarInt();
        bank.loanInterestBasisPoints = buffer.readVarInt();
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            bank.bankers.add(buffer.readUUID());
        }
        return bank;
    }

    /** Client-side rate accessor: no config available, so read what was sent. */
    public long syncedRate(CoinType type) {
        return rates[type.ordinal()];
    }
}
