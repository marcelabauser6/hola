package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletData;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Atomic server-thread operations for principal accounts, limited wallets, fees and loans. */
public final class BankManager {
    public static final String TAG_ACCOUNT = "FcAccount";
    public static final String TAG_OWNER = "FcOwner";
    public static final String TAG_BANK = "FcBank";
    private static final Random RANDOM = new Random();
    private BankManager() {}

    public static BankData data(MinecraftServer server) { return BankData.get(server); }

    /** One-time v3 migration: old wallet balances become principal funds or retained quarantine. */
    public static void migrateLegacyWallets(MinecraftServer server) {
        BankData banks = data(server); WalletData wallets = WalletData.get(server);
        synchronized (banks) {
            if (!wallets.migrationPending()) return;
            boolean complete = true;
            for (var entry : List.copyOf(wallets.all().entrySet())) {
                UUID owner = entry.getKey(); Wallet wallet = entry.getValue(); long amount = wallet.balance();
                BankAccount account = banks.accountOf(owner);
                // Presence in any legacy wallet file means the one-time starting grant was already decided.
                wallets.markInitialized(owner);
                if (amount <= 0L) continue;
                String correlation = LedgerEntry.correlation();
                if (account != null && account.credit(amount, LedgerEntry.Kind.WALLET_IN, "migracion wallet v3",
                        System.currentTimeMillis(), correlation, owner.toString(), "migration")) {
                    wallet.set(0L);
                } else if (wallets.quarantine(owner, amount)) {
                    wallet.set(0L);
                } else {
                    complete = false; // leave the old version pending; never discard an unmoved balance
                }
            }
            if (complete) wallets.finishMigration();
            wallets.setDirty(); banks.setDirty();
        }
    }

    /** Recovers quarantined legacy cash into an existing principal account when capacity permits. */
    public static boolean recoverQuarantined(MinecraftServer server, UUID owner) {
        BankData banks = data(server); WalletData wallets = WalletData.get(server);
        synchronized (banks) {
            BankAccount account = banks.accountOf(owner); long held = wallets.quarantined(owner);
            if (account == null || held <= 0L || !account.canCredit(held)) return false;
            String correlation = LedgerEntry.correlation();
            if (!account.credit(held, LedgerEntry.Kind.WALLET_IN, "recuperacion de cuarentena",
                    System.currentTimeMillis(), correlation, owner.toString(), "migration_recovery")) return false;
            wallets.takeQuarantined(owner); wallets.setDirty(); banks.setDirty(); return true;
        }
    }

    @Nullable public static BankAccount accountOf(ServerPlayer player) { migrateLegacyWallets(player.server); recoverQuarantined(player.server, player.getUUID()); return data(player.server).accountOf(player.getUUID()); }
    @Nullable public static Bank bankOf(ServerPlayer player) { BankAccount a=accountOf(player); return a==null?null:data(player.server).bank(a.bankId()); }
    public static boolean hasAccount(ServerPlayer player) { return accountOf(player) != null; }
    public static long walletCeiling(ServerPlayer player) { Bank bank=bankOf(player); return bank==null?-1L:bank.walletLimit(); }
    public static boolean ownsBank(ServerPlayer player, Bank bank) { BankAccount a=accountOf(player); return a!=null && bank!=null && a.bankId().equals(bank.id()); }

    public record OpenResult(boolean ok,String messageKey,int number){static OpenResult fail(String key){return new OpenResult(false,key,0);}}

    public static OpenResult openAccount(MinecraftServer server, Bank bank, UUID owner, String ownerName, long carriedOver) {
        migrateLegacyWallets(server); BankData data=data(server); WalletData wallets=WalletData.get(server);
        synchronized (data) {
            if (bank==null || data.bank(bank.id())==null || carriedOver<0L) return OpenResult.fail("message.athens_coins.bank_no_such_account");
            if (data.hasAccount(owner)) return OpenResult.fail("message.athens_coins.bank_already_has_account");
            int number=BankRules.allocateAccountNumber(data::numberTaken,RANDOM);
            if(number<0)return OpenResult.fail("message.athens_coins.bank_numbers_exhausted");
            Wallet wallet=wallets.account(owner);
            long retained=wallets.quarantined(owner);
            long starting=(wallets.isInitialized(owner)||data.hasEverHeldAccount(owner))
                    ?0L:wallets.startingBalanceIfUninitialized(owner);
            long initial=0L;
            for(long part:new long[]{carriedOver,retained,wallet.balance(),starting}){
                if(!Money.canAdd(initial,part))return OpenResult.fail("message.athens_coins.amount_too_big");
                initial+=part;
            }
            long now=System.currentTimeMillis(); String correlation=LedgerEntry.correlation();
            BankAccount account=new BankAccount(number,owner,ownerName,bank.id(),now);
            account.recordExternal(LedgerEntry.Kind.OPENED,0L,0L,0L,"apertura",now,correlation,owner.toString(),"bank_terminal");
            if(initial>0L && !account.credit(initial,LedgerEntry.Kind.WALLET_IN,"fondos de apertura",now,correlation,owner.toString(),"account_open"))
                return OpenResult.fail("message.athens_coins.amount_too_big");
            wallet.set(0L); wallets.takeQuarantined(owner); wallets.markInitialized(owner); wallets.setDirty(); data.register(account);
            return new OpenResult(true,"message.athens_coins.bank_account_opened",number);
        }
    }

    /** Atomically opens a principal account and consumes one registered transfer-card token. */
    public static OpenResult redeemCardAccount(MinecraftServer server, Bank bank, UUID owner,
                                               String ownerName, UUID token, long amount) {
        BankData data = data(server);
        synchronized (data) {
            BankData.CardRecord card = data.card(token);
            if (card == null || card.redeemed() || !card.owner().equals(owner) || card.amount() != amount) {
                return OpenResult.fail("message.athens_coins.card_invalid");
            }
            OpenResult opened = openAccount(server, bank, owner, ownerName, amount);
            if (!opened.ok()) return opened;
            if (!data.redeemCard(token, owner, amount, System.currentTimeMillis())) {
                throw new IllegalStateException("validated card redemption changed inside transaction");
            }
            return opened;
        }
    }

    public record CloseResult(boolean ok,String messageKey,long payout,BankData.CardRecord card) {
        static CloseResult fail(String key){return new CloseResult(false,key,0L,null);}
    }

    /** Liquidation explicitly settles fee debt and loan before archiving and paying a signed card. */
    public static CloseResult closeAccount(MinecraftServer server, BankAccount account, boolean liquidate, UUID actor) {
        BankData data=data(server);
        synchronized(data){
            if(account==null || data.account(account.number())!=account)return CloseResult.fail("message.athens_coins.bank_no_such_account");
            Loan loan=account.loan(); long loanOwed=loan==null?0L:loan.owed(); long feeDebt=account.commissionDebt();
            if(!liquidate && (loanOwed>0L||feeDebt>0L))return CloseResult.fail("message.athens_coins.bank_close_has_debt");
            long walletBalance=account.walletBalance();
            if(!Money.canAdd(account.balance(),walletBalance))return CloseResult.fail("message.athens_coins.amount_too_big");
            long total=account.balance()+walletBalance;
            if(!Money.canAdd(feeDebt,loanOwed) || total<feeDebt+loanOwed)return CloseResult.fail("message.athens_coins.bank_close_insufficient");
            Bank bank=data.bank(account.bankId()); if(bank==null)return CloseResult.fail("message.athens_coins.bank_no_such_account");
            long liabilities=feeDebt+loanOwed;
            if(!Money.canAdd(bank.reserve(),liabilities))return CloseResult.fail("message.athens_coins.amount_too_big");
            long payout = total - liabilities;
            // Register the unique claim before any balance is removed; it shares BankData's lock/save.
            BankData.CardRecord card = data.issueCard(account.owner(), payout, account.number(), bank.name());
            long now=System.currentTimeMillis();String correlation=LedgerEntry.correlation();String who=actor==null?"system":actor.toString();
            long walletBefore=account.walletBalance();
            if(walletBefore>0L){ if(!account.credit(walletBefore,LedgerEntry.Kind.WALLET_IN,"barrido por cierre",now,correlation,who,"close"))return CloseResult.fail("message.athens_coins.amount_too_big"); account.clearWallet(); account.recordExternal(LedgerEntry.Kind.WALLET_IN,-walletBefore,walletBefore,0L,"wallet cerrada",now,correlation,who,"wallet"); }
            if(feeDebt>0L){account.debit(feeDebt,LedgerEntry.Kind.COMMISSION,"deuda liquidada",now,correlation,who,"close");account.setCommissionDebt(0L);bank.addReserve(feeDebt);}
            if(loanOwed>0L){account.debit(loanOwed,LedgerEntry.Kind.LOAN_REPAID,"prestamo liquidado",now,correlation,who,"close");loan.repay(loanOwed);account.setLoan(null);bank.addReserve(loanOwed);}
            if (account.balance() != payout) throw new IllegalStateException("close payout changed after prevalidation");
            if(payout>0L)account.debit(payout,LedgerEntry.Kind.CLOSED,"cierre",now,correlation,who,"close"); else account.recordExternal(LedgerEntry.Kind.CLOSED,0L,0L,0L,"cierre",now,correlation,who,"close");
            data.archiveAndUnregister(account);return new CloseResult(true,"message.athens_coins.bank_closed_account",payout,card);
        }
    }

    /** Compatibility: refuses liabilities rather than silently forgiving them. */
    public static long closeAccount(MinecraftServer server,BankAccount account){CloseResult r=closeAccount(server,account,false,null);return r.ok()?r.payout():-1L;}

    public static ItemStack accountTag(BankAccount account,Bank bank){ItemStack stack=new ItemStack(Items.NAME_TAG);stack.setHoverName(Component.translatable("item.athens_coins.account_tag",account.number()));CompoundTag tag=stack.getOrCreateTag();tag.putInt(TAG_ACCOUNT,account.number());tag.putString(TAG_OWNER,account.ownerName()==null?"":account.ownerName());tag.putString(TAG_BANK,bank.name());return stack;}

    public static long toWallet(ServerPlayer player, long requested) {
        BankData data = data(player.server);
        synchronized (data) {
            BankAccount account = accountOf(player); Bank bank = bankOf(player);
            if (account == null || bank == null || requested <= 0L) return 0L;
            long amount = Math.min(BankRules.fitIntoWallet(requested, account.walletBalance(), bank.walletLimit()), account.balance());
            if (amount <= 0L || !account.canCreditWallet(amount)) return 0L;
            String correlation = LedgerEntry.correlation(); long now = System.currentTimeMillis(); long before = account.walletBalance();
            if (!account.debit(amount, LedgerEntry.Kind.WALLET_OUT, "a wallet", now, correlation, player.getUUID().toString(), "atm")) return 0L;
            if (!account.creditWallet(amount)) throw new IllegalStateException("prevalidated wallet credit failed");
            account.recordExternal(LedgerEntry.Kind.WALLET_OUT, amount, before, account.walletBalance(), "wallet acreditada", now, correlation, player.getUUID().toString(), "wallet");
            data.setDirty(); WalletManager.pushBalance(player); return amount;
        }
    }

    public static long toAccount(ServerPlayer player, long requested) {
        BankData data = data(player.server);
        synchronized (data) {
            BankAccount account = accountOf(player);
            if (account == null || requested <= 0L) return 0L;
            long amount = Math.min(requested, account.walletBalance());
            if (amount <= 0L || !account.canCredit(amount)) return 0L;
            String correlation = LedgerEntry.correlation(); long now = System.currentTimeMillis(); long before = account.walletBalance();
            if (!account.debitWallet(amount)) return 0L;
            if (!account.credit(amount, LedgerEntry.Kind.WALLET_IN, "desde wallet", now, correlation, player.getUUID().toString(), "atm")) throw new IllegalStateException("prevalidated account credit failed");
            account.recordExternal(LedgerEntry.Kind.WALLET_IN, -amount, before, account.walletBalance(), "wallet debitada", now, correlation, player.getUUID().toString(), "wallet");
            data.setDirty(); WalletManager.pushBalance(player); return amount;
        }
    }

    /** Default external credit always goes to the validated principal account. */
    public static boolean creditAccount(MinecraftServer server,UUID owner,long amount,LedgerEntry.Kind kind,String note,String source){migrateLegacyWallets(server);BankData data=data(server);synchronized(data){BankAccount account=data.accountOf(owner);if(account==null||amount<=0L)return false;boolean ok=account.credit(amount,kind,note,System.currentTimeMillis(),LedgerEntry.correlation(),owner.toString(),source);if(ok)data.setDirty();return ok;}}
    public static long credit(ServerPlayer player,long amount,LedgerEntry.Kind kind,String note){boolean ok=creditAccount(player.server,player.getUUID(),amount,kind,note,"atm");if(ok)WalletManager.pushBalance(player);return ok?amount:0L;}

    /** Debits the principal account, all-or-nothing. */
    public static boolean debitAccount(MinecraftServer server,UUID owner,long amount,LedgerEntry.Kind kind,String note,String source){migrateLegacyWallets(server);BankData data=data(server);synchronized(data){BankAccount account=data.accountOf(owner);if(account==null)return false;if(amount<=0L)return true;boolean ok=account.debit(amount,kind,note,System.currentTimeMillis(),LedgerEntry.correlation(),owner.toString(),source);if(ok)data.setDirty();return ok;}}

    /**
     * Admin-only building block: replaces a principal balance with a fully ledgered delta.
     *
     * <p>This mints or destroys money, so it has to move {@code totalIssued} with it. It did not, and
     * the omission silently broke the issued-versus-existing invariant that the economy statistics
     * and the central bank dashboard both report - every admin balance edit made those figures
     * disagree with reality, with no way to tell from the numbers that an edit was the cause.</p>
     */
    public static boolean setAccountBalance(MinecraftServer server,UUID owner,long balance,String source){
        if(balance<0L||balance>Money.MAX_CENTS)return false;
        BankData data=data(server);
        synchronized(data){
            BankAccount account=data.accountOf(owner);
            if(account==null)return false;
            long current=account.balance();
            if(current==balance)return true;
            boolean minting=balance>current;
            long delta=minting?balance-current:current-balance;
            if(minting&&!Money.canAdd(data.totalIssued(),delta))return false;
            boolean ok=minting
                    ?creditAccount(server,owner,delta,LedgerEntry.Kind.CENTRAL_INJECTION,"ajuste de saldo",source)
                    :debitAccount(server,owner,delta,LedgerEntry.Kind.CENTRAL_INJECTION,"ajuste de saldo",source);
            if(ok){data.addIssued(minting?delta:-delta);data.setDirty();}
            return ok;
        }
    }

    /** Explicit wallet credit; rejects overflow and any amount beyond this bank's limit. */
    public static boolean creditWallet(MinecraftServer server,UUID owner,long amount,LedgerEntry.Kind kind,String note,String source){migrateLegacyWallets(server);BankData data=data(server);synchronized(data){BankAccount account=data.accountOf(owner);if(account==null||amount<=0L)return false;Bank bank=data.bank(account.bankId());if(bank==null||BankRules.fitIntoWallet(amount,account.walletBalance(),bank.walletLimit())!=amount||!account.creditWallet(amount))return false;account.recordExternal(kind,amount,account.walletBalance()-amount,account.walletBalance(),note,System.currentTimeMillis(),LedgerEntry.correlation(),owner.toString(),source);data.setDirty();return true;}}

    /**
     * Shop-compatible wallet charge: account required, all-or-nothing.
     *
     * <p>The guard used to include {@code walletBalance() > walletLimit()}, which read as "refuse if
     * this wallet is over its ceiling". Combined with the surrounding {@code ||} it locked a player
     * out of <em>spending</em> whenever their card sat above the limit - a state a lowered ceiling can
     * produce - so having too much money on the card meant being unable to buy anything, with no
     * message explaining it. A ceiling constrains what goes <em>into</em> a wallet; taking money out
     * only ever moves it back inside the limit, so there is nothing to check here beyond having the
     * funds.</p>
     */
    public static boolean chargeWallet(MinecraftServer server,UUID owner,long amount,LedgerEntry.Kind kind,String note,String source){migrateLegacyWallets(server);BankData data=data(server);synchronized(data){BankAccount account=data.accountOf(owner);if(account==null)return false;if(amount<=0L)return true;Bank bank=data.bank(account.bankId());if(bank==null||!account.debitWallet(amount))return false;long after=account.walletBalance();account.recordExternal(kind,-amount,after+amount,after,note,System.currentTimeMillis(),LedgerEntry.correlation(),owner.toString(),source);data.setDirty();return true;}}

    public static boolean transferWallet(MinecraftServer server,UUID from,UUID to,long amount,String source){BankData data=data(server);synchronized(data){BankAccount a=data.accountOf(from),b=data.accountOf(to);if(a==null||b==null||amount<=0L||from.equals(to))return false;Bank dest=data.bank(b.bankId());if(dest==null||BankRules.fitIntoWallet(amount,b.walletBalance(),dest.walletLimit())!=amount||!b.canCreditWallet(amount)||a.walletBalance()<amount)return false;String c=LedgerEntry.correlation();long now=System.currentTimeMillis();long ab=a.walletBalance(),bb=b.walletBalance();a.debitWallet(amount);b.creditWallet(amount);a.recordExternal(LedgerEntry.Kind.TRANSFER_SENT,-amount,ab,a.walletBalance(),"transferencia",now,c,from.toString(),source);b.recordExternal(LedgerEntry.Kind.TRANSFER_RECEIVED,amount,bb,b.walletBalance(),"transferencia",now,c,from.toString(),source);data.setDirty();return true;}}

    /** Moves all above a lowered ceiling back to principal accounts, atomically. */
    public static boolean setWalletLimit(MinecraftServer server, Bank bank, long newLimit, UUID actor) {
        BankData data = data(server); long limit = Math.max(0L, newLimit);
        synchronized (data) {
            for (BankAccount account : data.accountsOf(bank.id())) {
                long excess = limit <= 0L ? 0L : Math.max(0L, account.walletBalance() - limit);
                if (excess > 0L && !account.canCredit(excess)) return false;
            }
            String correlation = LedgerEntry.correlation(); long now = System.currentTimeMillis();
            for (BankAccount account : data.accountsOf(bank.id())) {
                long excess = limit <= 0L ? 0L : Math.max(0L, account.walletBalance() - limit);
                if (excess > 0L) {
                    long before = account.walletBalance(); account.debitWallet(excess);
                    account.credit(excess, LedgerEntry.Kind.WALLET_IN, "normalizacion de limite", now, correlation, actor.toString(), "policy");
                    account.recordExternal(LedgerEntry.Kind.WALLET_IN, -excess, before, account.walletBalance(), "exceso transferido", now, correlation, actor.toString(), "wallet");
                }
            }
            bank.setWalletLimit(limit); data.setDirty(); return true;
        }
    }

    public static long collectCommission(MinecraftServer server, BankAccount account, long now) {
        BankData data = data(server);
        synchronized (data) {
            Bank bank = data.bank(account.bankId());
            if (bank == null || bank.commissionFee() <= 0L) return 0L;
            account.beginCommissionSession(now);
            int cap = CurrencyConfig.get().commissionMaxCatchUp;
            int historical = account.commissionCatchUpUsed() == 0
                    ? BankRules.commissionsDue(account.lastChargeAt(),
                    account.commissionSessionStartedAt(), bank.commissionPeriodDays(), cap)
                    : 0;
            int currentTotal = BankRules.commissionsDue(account.commissionSessionStartedAt(), now,
                    bank.commissionPeriodDays(), Integer.MAX_VALUE);
            int current = Math.max(0, currentTotal - account.commissionCurrentCyclesCharged());
            if (current > Integer.MAX_VALUE - historical) return 0L;
            int due = historical + current;
            if (due <= 0) return 0L;
            if (account.commissionDebt() > Money.MAX_CENTS
                    || bank.commissionFee() > Money.MAX_CENTS / due) {
                return 0L; // exact amount is not representable: preserve debt and do not consume cycles
            }
            long fresh = bank.commissionFee() * due;
            if (!Money.canAdd(account.commissionDebt(), fresh)) return 0L;
            long owed = account.commissionDebt() + fresh;
            long payable = Math.min(Math.min(owed, account.balance()), Money.MAX_CENTS - bank.reserve());
            String correlation = LedgerEntry.correlation();
            long taken = account.debitPartial(payable, LedgerEntry.Kind.COMMISSION, due + " ciclos",
                    now, correlation, "system", "commission");
            account.setCommissionDebt(owed - taken);
            if (taken > 0L) bank.addReserve(taken);
            account.setLastChargeAt(BankRules.advanceChargeClock(account.lastChargeAt(),
                    bank.commissionPeriodDays(), due));
            if (historical > 0) account.useCommissionCatchUp(historical);
            if (current > 0) account.chargeCurrentCommissionCycles(current);
            data.setDirty();
            return taken;
        }
    }

    public static void resetCommissionPolicyClock(MinecraftServer server,Bank bank,long now){BankData data=data(server);for(BankAccount a:data.accountsOf(bank.id())){a.setLastChargeAt(now);a.resetCommissionSession();}data.setDirty();}

    /**
     * Applies penalty interest for the business days that have gone by since the last charge.
     *
     * <p>Three things differ from the original. The base is the <em>principal</em>, not the running
     * balance, so the penalty is simple as documented instead of compounding once per overdue day.
     * The number of days is capped like commissions are, so a long outage cannot present one enormous
     * charge. And the total is capped as a share of the principal, because nothing in the mod ever
     * forces collection or writes a loan off - without a ceiling, an abandoned debt grows forever.</p>
     *
     * <p>The no-double-charge anchor is unchanged: {@code lastInterestAt} advances by exactly the
     * days billed, so the next tick a second later measures zero.</p>
     */
    public static long accrueInterest(MinecraftServer server, BankAccount account, long now) {
        BankData data = data(server);
        synchronized (data) {
            Loan loan = account.loan();
            if (loan == null || loan.settled() || !loan.overdue(now)) return 0L;
            Bank bank = data.bank(account.bankId());
            if (bank == null) return 0L;
            CurrencyConfig.Settings settings = CurrencyConfig.get();
            int elapsed = BankRules.overdueBusinessDays(loan.lastInterestAt(), now);
            int days = Math.min(elapsed, Math.max(1, settings.loanMaxCatchUp));
            if (days <= 0) return 0L;
            long interest = BankRules.overdueInterest(loan.principal(), days,
                    bank.loanInterestBasisPoints());
            interest = Math.min(interest, loan.interestHeadroom(settings.loanMaxInterestPercent));
            if (interest <= 0L) {
                // At the ceiling: still advance the anchor, or every tick recomputes the same
                // uncollectable charge forever.
                loan.setLastInterestAt(BankRules.addBusinessDays(loan.lastInterestAt(), days));
                data.setDirty();
                return 0L;
            }
            long before = loan.owed();
            if (!loan.addInterest(interest)) return 0L;
            loan.setLastInterestAt(BankRules.addBusinessDays(loan.lastInterestAt(), days));
            account.recordExternal(LedgerEntry.Kind.LOAN_INTEREST, interest, before, loan.owed(),
                    days + " dias de mora", now, LedgerEntry.correlation(), "system", "loan");
            data.setDirty();
            return interest;
        }
    }

    /**
     * The outcome of a loan request.
     *
     * @param amount    what was actually lent, which the caller must show: the bank clamps a request
     *                  to its reserve and its policy, so this is often less than what was asked for
     * @param requested what was asked for, so a caller can tell a partial grant from a full one
     */
    public record LoanResult(boolean ok, String messageKey, long amount, long requested) {
        static LoanResult fail(String key) {
            return new LoanResult(false, key, 0L, 0L);
        }

        /** True when the bank could only lend part of the request. */
        public boolean partial() {
            return ok && amount < requested;
        }
    }

    public static LoanResult grantLoan(MinecraftServer server, BankAccount account, long requested) {
        BankData data = data(server);
        synchronized (data) {
            Bank bank = data.bank(account.bankId());
            if (bank == null || !bank.loansEnabled())
                return LoanResult.fail("message.athens_coins.bank_loans_disabled");
            if (account.loan() != null && !account.loan().settled())
                return LoanResult.fail("message.athens_coins.bank_loan_active");
            // Count what this customer already owes the bank. maxLoan has always taken an
            // "already owed" argument and it was hard-coded to zero, so the headroom rule the rules
            // engine tests was dead in production: someone ten fee periods behind could still borrow
            // the full policy maximum.
            long alreadyOwed = account.commissionDebt()
                    + (account.loan() == null ? 0L : account.loan().owed());
            long amount = Math.min(requested,
                    BankRules.maxLoan(bank.reserve(), bank.loanMaxAmount(), alreadyOwed));
            if (amount <= 0L || !account.canCredit(amount))
                return LoanResult.fail("message.athens_coins.bank_reserve_empty");
            long now = System.currentTimeMillis();
            String c = LedgerEntry.correlation();
            if (!bank.drawReserve(amount))
                return LoanResult.fail("message.athens_coins.bank_reserve_empty");
            Loan loan = new Loan(amount, now, BankRules.addBusinessDays(now, bank.loanDays()));
            account.setLoan(loan);
            if (!account.credit(amount, LedgerEntry.Kind.LOAN_GRANTED,
                    bank.loanDays() + " dias habiles", now, c, "bank:" + bank.id(), "loan"))
                throw new IllegalStateException("prevalidated loan credit failed");
            data.setDirty();
            return new LoanResult(true, "message.athens_coins.bank_loan_granted", amount, requested);
        }
    }

    /**
     * Pays down a loan, taking the money from the account and then from the wallet card.
     *
     * <p>Repayment used to come out of the principal balance only. A player who kept their money on
     * the card - which is exactly what the ATM encourages - looked insolvent to the bank and could
     * not clear a debt they had the funds for. The card is the same account's money, so it counts.</p>
     */
    public static long repayLoan(MinecraftServer server, BankAccount account, long requested) {
        BankData data = data(server);
        synchronized (data) {
            Loan loan = account.loan();
            Bank bank = data.bank(account.bankId());
            if (loan == null || loan.settled() || bank == null) return 0L;
            long available = account.balance() + account.walletBalance();
            long amount = Math.min(Math.min(requested, loan.owed()), available);
            amount = Math.min(amount, Money.MAX_CENTS - bank.reserve());
            if (amount <= 0L) return 0L;
            String c = LedgerEntry.correlation();
            long now = System.currentTimeMillis();
            long fromAccount = Math.min(amount, account.balance());
            long fromWallet = amount - fromAccount;
            if (fromAccount > 0L && !account.debit(fromAccount, LedgerEntry.Kind.LOAN_REPAID,
                    "pago de prestamo", now, c, account.owner().toString(), "loan")) return 0L;
            if (fromWallet > 0L) {
                long walletBefore = account.walletBalance();
                if (!account.debitWallet(fromWallet)) {
                    // Put back the principal share rather than leaving a partial debit committed.
                    if (fromAccount > 0L) account.credit(fromAccount, LedgerEntry.Kind.LOAN_REPAID,
                            "reverso de pago", now, c, account.owner().toString(), "loan");
                    return 0L;
                }
                account.recordExternal(LedgerEntry.Kind.LOAN_REPAID, -fromWallet, walletBefore,
                        account.walletBalance(), "pago desde tarjeta", now, c,
                        account.owner().toString(), "wallet");
            }
            loan.repay(amount);
            bank.addReserve(amount);
            if (loan.settled()) account.setLoan(null);
            data.setDirty();
            return amount;
        }
    }

    public static void inject(MinecraftServer server,Bank bank,long amount){BankData data=data(server);synchronized(data){if(amount<=0L||!Money.canAdd(bank.reserve(),amount)||!Money.canAdd(data.totalIssued(),amount))return;bank.addReserve(amount);data.addIssued(amount);data.setDirty();}}
    public static void realignAllRates(MinecraftServer server){List<Bank> banks=data(server).banks();for(Bank bank:banks)bank.realignRates();data(server).setDirty();}
    public static Bank seatTerminal(MinecraftServer server,BlockPos pos,String defaultName){BankData data=data(server);Bank existing=data.bankAt(pos);if(existing!=null)return existing;Bank adopted=data.adoptSeat(pos);return adopted!=null?adopted:data.createBank(defaultName+" "+(data.bankCount()+1),pos);}
}
