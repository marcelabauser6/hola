package com.athensmc.athenscoins.item;

import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/** Signed, owner-bound, uniquely registered transfer card with persistent anti-replay state. */
public class BankCardItem extends Item {
    public static final String TAG_AMOUNT="FcAmount", TAG_HOLDER="FcHolder", TAG_OWNER="FcOwnerUuid",
            TAG_TOKEN="FcToken", TAG_PREV_ACCOUNT="FcPrevAccount", TAG_PREV_BANK="FcPrevBank", TAG_SIGNATURE="FcSig";
    public BankCardItem(Properties properties){super(properties);}

    public record ValidatedCard(UUID token,UUID owner,long amount){}

    public static ItemStack create(MinecraftServer server,long amount,UUID owner,String holder,int previousAccount,String previousBank){
        long safe=Money.clampBalance(amount); BankData.CardRecord record=BankData.get(server).issueCard(owner,safe,previousAccount,previousBank);
        return create(server, record, holder);
    }

    /** Materializes an already registered close claim without issuing a second token. */
    public static ItemStack create(MinecraftServer server, BankData.CardRecord record, String holder) {
        ItemStack stack=new ItemStack(ModItems.BANK_CARD.get());CompoundTag tag=stack.getOrCreateTag();
        tag.putLong(TAG_AMOUNT,record.amount());tag.putUUID(TAG_OWNER,record.owner());tag.putUUID(TAG_TOKEN,record.token());tag.putString(TAG_HOLDER,holder==null?"":holder);
        tag.putInt(TAG_PREV_ACCOUNT,record.previousAccount());tag.putString(TAG_PREV_BANK,record.previousBank()==null?"":record.previousBank());tag.putString(TAG_SIGNATURE,sign(server,tag));return stack;
    }

    /** Compatibility overload cannot safely invent ownership and therefore must not be used. */
    @Deprecated public static ItemStack create(MinecraftServer server,long amount,String holder,int previousAccount,String previousBank){throw new IllegalArgumentException("owner UUID required");}

    /** Validates signature, persistent issue record, anti-replay state and current holder UUID. */
    @Nullable public static ValidatedCard validateFor(MinecraftServer server,ServerPlayer player,ItemStack stack){
        if(!stack.is(ModItems.BANK_CARD.get()))return null;CompoundTag tag=stack.getTag();if(tag==null)return null;
        if(!tag.hasUUID(TAG_OWNER)||!tag.hasUUID(TAG_TOKEN))return null; // legacy cards require audited manual recovery
        UUID owner=tag.getUUID(TAG_OWNER),token=tag.getUUID(TAG_TOKEN);long amount=tag.getLong(TAG_AMOUNT);
        if(!owner.equals(player.getUUID())||amount<0L||amount>Money.MAX_CENTS)return null;
        byte[] expected=sign(server,tag).getBytes(StandardCharsets.UTF_8), actual=tag.getString(TAG_SIGNATURE).getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,actual))return null;
        BankData.CardRecord record=BankData.get(server).card(token);
        if(record==null||record.redeemed()||!record.owner().equals(owner)||record.amount()!=amount)return null;
        return new ValidatedCard(token,owner,amount);
    }

    /** Marks an already-validated card redeemed. Replays return false and never mint twice. */
    public static boolean markRedeemed(MinecraftServer server,ValidatedCard card){return BankData.get(server).redeemCard(card.token(),card.owner(),card.amount(),System.currentTimeMillis());}

    /** Registry-aware amount query for diagnostics. Owner-bound redemption must use validateFor. */
    public static long amountOf(MinecraftServer server,ItemStack stack){CompoundTag tag=stack.getTag();if(tag==null||!tag.hasUUID(TAG_TOKEN)||!tag.hasUUID(TAG_OWNER))return -1L;BankData.CardRecord r=BankData.get(server).card(tag.getUUID(TAG_TOKEN));if(r==null||r.redeemed())return -1L;String expected=sign(server,tag);return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),tag.getString(TAG_SIGNATURE).getBytes(StandardCharsets.UTF_8))?r.amount():-1L;}
    public static String holderOf(ItemStack stack){CompoundTag tag=stack.getTag();return tag==null?"":tag.getString(TAG_HOLDER);}

    private static String sign(MinecraftServer server,CompoundTag tag){return hmac(server,tag.getLong(TAG_AMOUNT)+"|"+tag.getUUID(TAG_OWNER)+"|"+tag.getUUID(TAG_TOKEN)+"|"+tag.getString(TAG_HOLDER)+"|"+tag.getInt(TAG_PREV_ACCOUNT)+"|"+tag.getString(TAG_PREV_BANK));}
    private static String hmac(MinecraftServer server,String payload){try{javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(("fantasticcurrency:"+server.overworld().getSeed()).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] digest=mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder(digest.length*2);for(byte b:digest){hex.append(Character.forDigit((b>>4)&15,16));hex.append(Character.forDigit(b&15,16));}return hex.toString();}catch(java.security.GeneralSecurityException e){return "unavailable";}}

    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> tooltip,TooltipFlag flag){CompoundTag tag=stack.getTag();if(tag==null)return;String symbol=CurrencyConfig.get().currencySymbol;tooltip.add(Component.translatable("tooltip.athens_coins.card_amount",Component.literal(Money.format(tag.getLong(TAG_AMOUNT),symbol)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN));tooltip.add(Component.translatable("tooltip.athens_coins.card_holder",tag.getString(TAG_HOLDER)).withStyle(ChatFormatting.GRAY));tooltip.add(Component.translatable("tooltip.athens_coins.card_from",tag.getString(TAG_PREV_BANK),tag.getInt(TAG_PREV_ACCOUNT)).withStyle(ChatFormatting.DARK_GRAY));tooltip.add(Component.translatable("tooltip.athens_coins.card_use").withStyle(ChatFormatting.DARK_GRAY));}
    @Override public boolean isFoil(ItemStack stack){return true;}
}
