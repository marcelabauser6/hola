package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;

/** Persistent banks, principal accounts, closed audit records and anti-replay card registry. */
public class BankData extends SavedData {
    public static final String DATA_NAME = "athens_coins_banks";
    public static final int CURRENT_VERSION = 2;

    public record CardRecord(UUID token, UUID owner, long amount, long issuedAt, long redeemedAt,
                             int previousAccount, String previousBank) {
        public boolean redeemed() { return redeemedAt > 0L; }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putUUID("token", token); tag.putUUID("owner", owner);
            tag.putLong("amount", amount); tag.putLong("issuedAt", issuedAt); tag.putLong("redeemedAt", redeemedAt);
            tag.putInt("previousAccount", previousAccount); tag.putString("previousBank", previousBank == null ? "" : previousBank);
            return tag;
        }
        static CardRecord load(CompoundTag tag) {
            return new CardRecord(tag.getUUID("token"), tag.getUUID("owner"), Money.clampBalance(tag.getLong("amount")),
                    tag.getLong("issuedAt"), tag.getLong("redeemedAt"), tag.getInt("previousAccount"), tag.getString("previousBank"));
        }
    }

    private final Map<UUID, Bank> banks = new HashMap<>();
    private final Map<Integer, BankAccount> accounts = new HashMap<>();
    private final Map<UUID, Integer> byOwner = new HashMap<>();
    private final List<BankAccount> closedAccounts = new ArrayList<>();
    /** Invalid/duplicate/missing-bank accounts are retained, never silently destroyed. */
    private final List<BankAccount> orphanAccounts = new ArrayList<>();
    private final Map<UUID, CardRecord> cards = new HashMap<>();
    private long totalIssued;

    public static BankData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BankData::load, BankData::new, DATA_NAME);
    }

    public Bank createBank(String name, BlockPos terminalPos) { Bank bank = new Bank(UUID.randomUUID(), name, terminalPos); banks.put(bank.id(), bank); setDirty(); return bank; }
    @Nullable public Bank bank(UUID id) { return id == null ? null : banks.get(id); }
    @Nullable public Bank bankAt(BlockPos pos) { for (Bank bank : banks.values()) if (pos.equals(bank.terminalPos())) return bank; return null; }
    public List<Bank> banks() { return new ArrayList<>(banks.values()); }
    public int bankCount() { return banks.size(); }
    public void clearSeat(BlockPos pos) { Bank bank = bankAt(pos); if (bank != null) { bank.setTerminalPos(null); setDirty(); } }
    @Nullable public Bank adoptSeat(BlockPos pos) { for (Bank bank : banks.values()) if (!bank.hasSeat()) { bank.setTerminalPos(pos); setDirty(); return bank; } return null; }

    public boolean numberTaken(int number) { return accounts.containsKey(number); }
    @Nullable public BankAccount account(int number) { return accounts.get(number); }
    @Nullable public BankAccount accountOf(UUID owner) { Integer number = byOwner.get(owner); return number == null ? null : accounts.get(number); }
    public boolean hasAccount(UUID owner) { return accountOf(owner) != null; }
    public void register(BankAccount account) {
        if (accounts.containsKey(account.number()) || byOwner.containsKey(account.owner())) throw new IllegalStateException("duplicate bank account");
        accounts.put(account.number(), account); byOwner.put(account.owner(), account.number()); setDirty();
    }
    public void archiveAndUnregister(BankAccount account) {
        closedAccounts.add(account); accounts.remove(account.number()); byOwner.remove(account.owner()); setDirty();
    }
    /** Legacy alias; closing code should archive instead. */
    public void unregister(BankAccount account) { archiveAndUnregister(account); }
    public List<BankAccount> accountsOf(UUID bankId) { List<BankAccount> out = new ArrayList<>(); for (BankAccount a : accounts.values()) if (a.bankId().equals(bankId)) out.add(a); out.sort((a,b)->Long.compare(b.balance(),a.balance())); return out; }
    public List<BankAccount> allAccounts() { return new ArrayList<>(accounts.values()); }
    public List<BankAccount> closedAccounts() { return List.copyOf(closedAccounts); }
    public List<BankAccount> orphanAccounts() { return List.copyOf(orphanAccounts); }
    public boolean hasEverHeldAccount(UUID owner) {
        if (byOwner.containsKey(owner)) return true;
        for (BankAccount account : closedAccounts) if (account.owner().equals(owner)) return true;
        for (BankAccount account : orphanAccounts) if (account.owner().equals(owner)) return true;
        return false;
    }

    public long totalIssued() { return totalIssued; }
    public boolean addIssued(long amount) { if (amount <= 0L || !Money.canAdd(totalIssued, amount)) return false; totalIssued += amount; setDirty(); return true; }

    public CardRecord issueCard(UUID owner, long amount, int previousAccount, String previousBank) {
        UUID token; do { token = UUID.randomUUID(); } while (cards.containsKey(token));
        CardRecord record = new CardRecord(token, owner, amount, System.currentTimeMillis(), 0L, previousAccount, previousBank);
        cards.put(token, record); setDirty(); return record;
    }
    @Nullable public CardRecord card(UUID token) { return cards.get(token); }
    public List<CardRecord> unredeemedCards(UUID owner) {
        List<CardRecord> out = new ArrayList<>();
        for (CardRecord card : cards.values()) if (!card.redeemed() && card.owner().equals(owner)) out.add(card);
        out.sort(Comparator.comparingLong(CardRecord::issuedAt));
        return out;
    }
    public boolean redeemCard(UUID token, UUID owner, long amount, long now) {
        CardRecord current = cards.get(token);
        if (current == null || current.redeemed() || !current.owner().equals(owner) || current.amount() != amount) return false;
        cards.put(token, new CardRecord(token, owner, amount, current.issuedAt(), now, current.previousAccount(), current.previousBank()));
        setDirty(); return true;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag bankList = new ListTag(); for (Bank bank : banks.values()) bankList.add(bank.save()); tag.put("Banks", bankList);
        ListTag accountList = new ListTag(); for (BankAccount account : accounts.values()) accountList.add(account.save()); tag.put("Accounts", accountList);
        ListTag closed = new ListTag(); for (BankAccount account : closedAccounts) closed.add(account.save()); tag.put("ClosedAccounts", closed);
        ListTag orphans = new ListTag(); for (BankAccount account : orphanAccounts) orphans.add(account.save()); tag.put("OrphanAccounts", orphans);
        ListTag cardList = new ListTag(); for (CardRecord card : cards.values()) cardList.add(card.save()); tag.put("Cards", cardList);
        tag.putLong("Issued", totalIssued); tag.putInt("DataVersion", CURRENT_VERSION); return tag;
    }

    public static BankData load(CompoundTag tag) {
        BankData data = new BankData();
        ListTag bankList = tag.getList("Banks", Tag.TAG_COMPOUND);
        for (int i=0;i<bankList.size();i++) { Bank bank=Bank.load(bankList.getCompound(i)); data.banks.put(bank.id(),bank); }
        ListTag accountList = tag.getList("Accounts", Tag.TAG_COMPOUND);
        for (int i=0;i<accountList.size();i++) {
            BankAccount account=BankAccount.load(accountList.getCompound(i));
            if (!data.banks.containsKey(account.bankId()) || data.accounts.containsKey(account.number()) || data.byOwner.containsKey(account.owner())) { data.orphanAccounts.add(account); continue; }
            data.accounts.put(account.number(),account); data.byOwner.put(account.owner(),account.number());
        }
        loadAccounts(tag.getList("ClosedAccounts", Tag.TAG_COMPOUND), data.closedAccounts);
        loadAccounts(tag.getList("OrphanAccounts", Tag.TAG_COMPOUND), data.orphanAccounts);
        ListTag cardList=tag.getList("Cards",Tag.TAG_COMPOUND);
        for(int i=0;i<cardList.size();i++){ CompoundTag c=cardList.getCompound(i); if(c.hasUUID("token")&&c.hasUUID("owner")){CardRecord r=CardRecord.load(c);data.cards.put(r.token(),r);} }
        data.totalIssued=Money.clampBalance(tag.getLong("Issued")); return data;
    }
    private static void loadAccounts(ListTag list,List<BankAccount> target){for(int i=0;i<list.size();i++)target.add(BankAccount.load(list.getCompound(i)));}
}
