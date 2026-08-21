package com.athensmc.athenscoins.item;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A bearer card holding the balance of a closed account, used to move banks.
 *
 * <h2>Signed, not encrypted</h2>
 * Encrypting the amount would need the key on the server, so it would protect nothing: anyone who
 * can read the world can read the key. The real attack is editing the NBT to inflate the figure, so
 * the card is signed with an HMAC keyed on the world seed. The amount stays readable, because it is
 * the holder's own money, but a forged card fails verification and is refused.
 */
public class BankCardItem extends Item {

    public static final String TAG_AMOUNT = "FcAmount";
    public static final String TAG_HOLDER = "FcHolder";
    public static final String TAG_PREV_ACCOUNT = "FcPrevAccount";
    public static final String TAG_PREV_BANK = "FcPrevBank";
    public static final String TAG_SIGNATURE = "FcSig";

    public BankCardItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------ creation

    public static ItemStack create(MinecraftServer server, long amount, String holder,
                                   int previousAccount, String previousBank) {
        ItemStack stack = new ItemStack(ModItems.BANK_CARD.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(TAG_AMOUNT, Math.max(0L, amount));
        tag.putString(TAG_HOLDER, holder == null ? "" : holder);
        tag.putInt(TAG_PREV_ACCOUNT, previousAccount);
        tag.putString(TAG_PREV_BANK, previousBank == null ? "" : previousBank);
        tag.putString(TAG_SIGNATURE, sign(server, tag));
        return stack;
    }

    /** Amount on a card, or -1 when it is missing, malformed or forged. */
    public static long amountOf(MinecraftServer server, ItemStack stack) {
        if (!stack.is(ModItems.BANK_CARD.get())) {
            return -1L;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SIGNATURE)) {
            return -1L;
        }
        String expected = sign(server, tag);
        if (!expected.equals(tag.getString(TAG_SIGNATURE))) {
            return -1L;
        }
        return Math.max(0L, tag.getLong(TAG_AMOUNT));
    }

    public static String holderOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TAG_HOLDER);
    }

    // ------------------------------------------------------------------ signing

    /** HMAC over the card's fields, keyed on the world seed. */
    private static String sign(MinecraftServer server, CompoundTag tag) {
        String payload = tag.getLong(TAG_AMOUNT) + "|" + tag.getString(TAG_HOLDER) + "|"
                + tag.getInt(TAG_PREV_ACCOUNT) + "|" + tag.getString(TAG_PREV_BANK);
        long seed = server.overworld().getSeed();
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    ("fantasticcurrency:" + seed).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.GeneralSecurityException exception) {
            // Without HMAC there is no safe way to accept a card, so make every check fail.
            return "unavailable";
        }
    }

    // ------------------------------------------------------------------ display

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        String symbol = CurrencyConfig.get().currencySymbol;
        tooltip.add(Component.translatable("tooltip.athens_coins.card_amount",
                        Component.literal(Money.format(tag.getLong(TAG_AMOUNT), symbol))
                                .withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.athens_coins.card_holder",
                        tag.getString(TAG_HOLDER))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.athens_coins.card_from",
                        tag.getString(TAG_PREV_BANK), tag.getInt(TAG_PREV_ACCOUNT))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.athens_coins.card_use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
