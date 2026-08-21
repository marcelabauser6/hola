package com.athensmc.athenscoins.bank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every bank and account in the world.
 *
 * <p>Kept in its own saved data file rather than alongside the wallets, so a mistake in the banking
 * layer cannot corrupt the plain cash balances. The owner index is what enforces one account per
 * player.</p>
 */
public class BankData extends SavedData {

    public static final String DATA_NAME = "athens_coins_banks";

    private final Map<UUID, Bank> banks = new HashMap<>();
    private final Map<Integer, BankAccount> accounts = new HashMap<>();
    /** owner -> account number. One entry per player, which is the "one bank each" rule. */
    private final Map<UUID, Integer> byOwner = new HashMap<>();
    /** Money the central bank has issued, for auditing. */
    private long totalIssued;

    public static BankData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(BankData::load, BankData::new, DATA_NAME);
    }

    // ------------------------------------------------------------------ banks

    public Bank createBank(String name, BlockPos terminalPos) {
        Bank bank = new Bank(UUID.randomUUID(), name, terminalPos);
        banks.put(bank.id(), bank);
        setDirty();
        return bank;
    }

    @Nullable
    public Bank bank(UUID id) {
        return id == null ? null : banks.get(id);
    }

    /** The bank whose terminal sits at this position, if any. */
    @Nullable
    public Bank bankAt(BlockPos pos) {
        for (Bank bank : banks.values()) {
            if (pos.equals(bank.terminalPos())) {
                return bank;
            }
        }
        return null;
    }

    public List<Bank> banks() {
        return new ArrayList<>(banks.values());
    }

    public int bankCount() {
        return banks.size();
    }

    /** Detaches a bank from a broken terminal. Accounts survive. */
    public void clearSeat(BlockPos pos) {
        Bank bank = bankAt(pos);
        if (bank != null) {
            bank.setTerminalPos(null);
            setDirty();
        }
    }

    /** Re-seats the first bank left without a terminal, used when an OP places a new one. */
    @Nullable
    public Bank adoptSeat(BlockPos pos) {
        for (Bank bank : banks.values()) {
            if (!bank.hasSeat()) {
                bank.setTerminalPos(pos);
                setDirty();
                return bank;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ accounts

    public boolean numberTaken(int number) {
        return accounts.containsKey(number);
    }

    @Nullable
    public BankAccount account(int number) {
        return accounts.get(number);
    }

    /** The account a player holds, or null if they have none. */
    @Nullable
    public BankAccount accountOf(UUID owner) {
        Integer number = byOwner.get(owner);
        return number == null ? null : accounts.get(number);
    }

    public boolean hasAccount(UUID owner) {
        return byOwner.containsKey(owner);
    }

    public void register(BankAccount account) {
        accounts.put(account.number(), account);
        byOwner.put(account.owner(), account.number());
        setDirty();
    }

    public void unregister(BankAccount account) {
        accounts.remove(account.number());
        byOwner.remove(account.owner());
        setDirty();
    }

    public List<BankAccount> accountsOf(UUID bankId) {
        List<BankAccount> out = new ArrayList<>();
        for (BankAccount account : accounts.values()) {
            if (account.bankId().equals(bankId)) {
                out.add(account);
            }
        }
        out.sort((a, b) -> Long.compare(b.balance(), a.balance()));
        return out;
    }

    public List<BankAccount> allAccounts() {
        return new ArrayList<>(accounts.values());
    }

    public long totalIssued() {
        return totalIssued;
    }

    public void addIssued(long amount) {
        totalIssued += amount;
        setDirty();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag bankList = new ListTag();
        for (Bank bank : banks.values()) {
            bankList.add(bank.save());
        }
        tag.put("Banks", bankList);

        ListTag accountList = new ListTag();
        for (BankAccount account : accounts.values()) {
            accountList.add(account.save());
        }
        tag.put("Accounts", accountList);
        tag.putLong("Issued", totalIssued);
        tag.putInt("DataVersion", 1);
        return tag;
    }

    public static BankData load(CompoundTag tag) {
        BankData data = new BankData();
        ListTag bankList = tag.getList("Banks", Tag.TAG_COMPOUND);
        for (int i = 0; i < bankList.size(); i++) {
            Bank bank = Bank.load(bankList.getCompound(i));
            data.banks.put(bank.id(), bank);
        }
        ListTag accountList = tag.getList("Accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountList.size(); i++) {
            BankAccount account = BankAccount.load(accountList.getCompound(i));
            // Drop orphans whose bank no longer exists rather than keeping unreachable money.
            if (!data.banks.containsKey(account.bankId())) {
                continue;
            }
            data.accounts.put(account.number(), account);
            data.byOwner.put(account.owner(), account.number());
        }
        data.totalIssued = tag.getLong("Issued");
        return data;
    }
}
