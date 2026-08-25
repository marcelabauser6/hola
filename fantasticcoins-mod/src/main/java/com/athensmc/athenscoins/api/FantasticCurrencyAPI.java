package com.athensmc.athenscoins.api;

import com.athensmc.athenscoins.bank.*;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.UUID;

/** Stable, server-thread API. Amounts are exact cents. Principal funds live in BankAccount. */
public final class FantasticCurrencyAPI {
    private FantasticCurrencyAPI() {}

    public static String currencyName(){return CurrencyConfig.get().currencyName;}
    public static String currencySymbol(){return CurrencyConfig.get().currencySymbol;}
    public static String format(long cents){return Money.format(cents,currencySymbol());}
    public static String formatPlain(long cents){return Money.plain(cents);}
    public static long parseAmount(String text)throws Money.InvalidAmountException{return Money.parse(text);}
    public static long cents(long units,int cents){if(units<0L||cents<0||cents>=100)return 0L;return Money.canAdd(Money.multiply(units,Money.SCALE),cents)?Money.multiply(units,Money.SCALE)+cents:0L;}

    private static BankAccount bankAccount(MinecraftServer server,UUID owner){BankManager.migrateLegacyWallets(server);BankManager.recoverQuarantined(server,owner);return BankData.get(server).accountOf(owner);}
    private static void push(MinecraftServer server,UUID owner){ServerPlayer online=server.getPlayerList().getPlayer(owner);if(online!=null)WalletManager.pushBalance(online);}

    /** Spendable wallet cash. Without a valid bank account it is always zero. */
    public static long getBalance(MinecraftServer server,UUID playerId){BankAccount account=bankAccount(server,playerId);return account==null?0L:account.walletBalance();}
    public static long getBalance(ServerPlayer player){return getBalance(player.server,player.getUUID());}
    /** Principal balance held by the bank. */
    public static long getAccountBalance(MinecraftServer server,UUID playerId){BankAccount a=bankAccount(server,playerId);return a==null?0L:a.balance();}
    public static boolean has(MinecraftServer server,UUID playerId,long cents){return cents>0L&&getBalance(server,playerId)>=cents;}
    public static boolean has(ServerPlayer player,long cents){return has(player.server,player.getUUID(),cents);}

    /** Default payout path: credits the validated principal account, never the wallet. */
    public static void deposit(MinecraftServer server,UUID playerId,long cents){depositToAccount(server,playerId,cents);}
    public static void deposit(ServerPlayer player,long cents){deposit(player.server,player.getUUID(),cents);}
    public static boolean depositToAccount(MinecraftServer server,UUID playerId,long cents){boolean ok=BankManager.creditAccount(server,playerId,cents,LedgerEntry.Kind.SHOP_SALE,"credito API","api");if(ok)push(server,playerId);return ok;}
    /** Explicit payout to spendable wallet; the complete amount must fit under the bank ceiling. */
    public static boolean depositToWallet(MinecraftServer server,UUID playerId,long cents){boolean ok=BankManager.creditWallet(server,playerId,cents,LedgerEntry.Kind.SHOP_SALE,"credito API wallet","api");if(ok)push(server,playerId);return ok;}

    /** Shop contract: consumes wallet cash, requires a bank and rejects over-limit legacy state. */
    public static boolean charge(MinecraftServer server,UUID playerId,long cents){boolean ok=BankManager.chargeWallet(server,playerId,cents,LedgerEntry.Kind.SHOP_PURCHASE,"cargo API","api");if(ok)push(server,playerId);return ok;}
    public static boolean charge(ServerPlayer player,long cents){return charge(player.server,player.getUUID(),cents);}

    /** Replaces the principal balance; retained for admin integrations and fully ledgered. */
    public static void setBalance(MinecraftServer server,UUID playerId,long cents){if(BankManager.setAccountBalance(server,playerId,cents,"api_admin"))push(server,playerId);}

    /** Wallet-to-wallet transfer, atomic and limited by the recipient's bank ceiling. */
    public static boolean transfer(MinecraftServer server,UUID from,UUID to,long cents){boolean ok=BankManager.transferWallet(server,from,to,cents,"api");if(ok){push(server,from);push(server,to);}return ok;}

    public static long getBalanceUnits(MinecraftServer server,UUID playerId){return getBalance(server,playerId)/Money.SCALE;}
    public static boolean chargeUnits(MinecraftServer server,UUID playerId,long units){return charge(server,playerId,Money.multiply(Money.SCALE,units));}
    public static void depositUnits(MinecraftServer server,UUID playerId,long units){deposit(server,playerId,Money.multiply(Money.SCALE,units));}

    public static long getDisplayBalance(Player player){if(player instanceof ServerPlayer p)return getBalance(p);if(FMLEnvironment.dist==Dist.CLIENT)return ClientBalanceAccess.get();return 0L;}
    public static long getDisplayBalanceUnits(Player player){return getDisplayBalance(player)/Money.SCALE;}
    private static final class ClientBalanceAccess{static long get(){return com.athensmc.athenscoins.client.ClientCashCache.get();}}

    public static int accountNumber(MinecraftServer server,UUID playerId){BankAccount a=bankAccount(server,playerId);return a==null?0:a.number();}
    public static boolean ownsAccount(MinecraftServer server,UUID playerId,int number){if(number<=0)return false;BankAccount a=BankData.get(server).account(number);return a!=null&&a.owner().equals(playerId);}
    /** Explicit alias for shop account-link validation. */
    public static boolean isAccountHolder(MinecraftServer server,UUID playerId,int number){return ownsAccount(server,playerId,number);}
    public static String bankNameOf(MinecraftServer server,int number){BankData d=BankData.get(server);BankAccount a=d.account(number);if(a==null)return "";Bank b=d.bank(a.bankId());return b==null?"":b.name();}
    /** Safe sale settlement only when the shop's stored account still belongs to the seller. */
    public static boolean creditSaleToAccount(MinecraftServer server,UUID seller,int linkedAccount,long cents){return ownsAccount(server,seller,linkedAccount)&&depositToAccount(server,seller,cents);}
    public static boolean creditSaleToWallet(MinecraftServer server,UUID seller,int linkedAccount,long cents){return ownsAccount(server,seller,linkedAccount)&&depositToWallet(server,seller,cents);}

    public static long coinValue(CoinType type){return CurrencyConfig.get().coinValueCents(type);}
    public static int countCoins(Player player,CoinType type){return WalletManager.countCoins(player,type);}
    public static long inventoryCoinValue(Player player){return WalletManager.inventoryValueCents(player);}
    public static long netWorth(ServerPlayer player){long coins=inventoryCoinValue(player);BankAccount a=bankAccount(player.server,player.getUUID());if(a==null)return coins;long wallet=getBalance(player);long total=Money.canAdd(a.balance(),wallet)?a.balance()+wallet:Money.MAX_CENTS;return Money.canAdd(total,coins)?total+coins:Money.MAX_CENTS;}
}
