/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.StringTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.Component$Serializer
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 */
package com.claimblocks;

import com.claimblocks.data.ClaimTier;
import com.claimblocks.item.ClaimItems;
import com.claimblocks.item.ProtectionItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ClaimBlocks {
    public static final String NBT_KEY = "claimblocks";
    public static final String NBT_TIER_FIELD = "tier";

    private ClaimBlocks() {
    }

    public static Block blockForTier(ClaimTier tier) {
        String var1;
        if (tier == null) {
            return Blocks.f_50542_;
        }
        return switch (var1 = tier.id) {
            case "claimstone_10x10" -> Blocks.f_50542_;
            case "claimstone_25x25" -> Blocks.f_50498_;
            case "claimstone_40x40" -> Blocks.f_50499_;
            case "claimstone_64x64" -> Blocks.f_50545_;
            case "claimstone_80x80" -> Blocks.f_50495_;
            case "claimstone_100x100" -> Blocks.f_50494_;
            case "claimstone_150x150" -> Blocks.f_50543_;
            case "claimstone_250x250" -> Blocks.f_50496_;
            case "claimstone_300x300" -> Blocks.f_50544_;
            case "claimstone_500x500" -> Blocks.f_50500_;
            default -> Blocks.f_50542_;
        };
    }

    public static Item itemForTier(ClaimTier tier) {
        return ClaimBlocks.blockForTier(tier).m_5456_();
    }

    public static boolean isClaimConcreteForTier(Block block, ClaimTier tier) {
        return block == ClaimBlocks.blockForTier(tier);
    }

    public static boolean isAnyClaimConcrete(Block block) {
        for (ClaimTier t : ClaimTier.VALUES) {
            if (block != ClaimBlocks.blockForTier(t)) continue;
            return true;
        }
        return false;
    }

    public static ChatFormatting colorForTier(ClaimTier tier) {
        String var1;
        if (tier == null) {
            return ChatFormatting.WHITE;
        }
        return switch (var1 = tier.id) {
            case "claimstone_10x10" -> ChatFormatting.WHITE;
            case "claimstone_25x25" -> ChatFormatting.GRAY;
            case "claimstone_40x40" -> ChatFormatting.AQUA;
            case "claimstone_64x64" -> ChatFormatting.BLUE;
            case "claimstone_80x80" -> ChatFormatting.GREEN;
            case "claimstone_100x100" -> ChatFormatting.YELLOW;
            case "claimstone_150x150" -> ChatFormatting.GOLD;
            case "claimstone_250x250" -> ChatFormatting.LIGHT_PURPLE;
            case "claimstone_300x300" -> ChatFormatting.LIGHT_PURPLE;
            case "claimstone_500x500" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    public static ItemStack createTierItem(ClaimTier tier, int amount) {
        Item registered = ClaimItems.itemFor(tier);
        ChatFormatting color = ClaimBlocks.colorForTier(tier);
        MutableComponent name = Component.m_237113_((String)("Protecci\u00f3n " + tier.label())).m_6270_(Style.f_131099_.m_131140_(color).m_131136_(Boolean.valueOf(true)).m_131155_(Boolean.valueOf(false)));
        if (registered != null) {
            ItemStack stack = new ItemStack((ItemLike)registered, amount);
            stack.m_41714_((Component)name);
            return stack;
        }
        ItemStack stack = new ItemStack((ItemLike)ClaimBlocks.itemForTier(tier), amount);
        CompoundTag tag = stack.m_41784_();
        CompoundTag root = new CompoundTag();
        root.m_128359_(NBT_TIER_FIELD, tier.id);
        tag.m_128365_(NBT_KEY, (Tag)root);
        ListTag ench = new ListTag();
        CompoundTag e = new CompoundTag();
        e.m_128359_("id", "minecraft:unbreaking");
        e.m_128405_("lvl", 1);
        ench.add(e);
        tag.m_128365_("Enchantments", (Tag)ench);
        tag.m_128405_("HideFlags", 1);
        stack.m_41714_((Component)name);
        ArrayList<Component> lore = new ArrayList<Component>();
        lore.add((Component)Component.m_237113_((String)("Radio: " + tier.radius + " \u00b7 Altura: \u00b1" + tier.height)).m_130940_(ChatFormatting.GRAY));
        lore.add((Component)Component.m_237113_((String)"Coloca para crear una protecci\u00f3n").m_130940_(color));
        ClaimBlocks.setLore(stack, lore);
        return stack;
    }

    public static void setLore(ItemStack stack, List<Component> lore) {
        CompoundTag display = stack.m_41698_("display");
        ListTag loreList = new ListTag();
        for (Component line : lore) {
            loreList.add(StringTag.m_129297_((String)Component.Serializer.m_130703_((Component)line)));
        }
        display.m_128365_("Lore", (Tag)loreList);
    }

    public static String readTierId(ItemStack stack) {
        if (stack != null && !stack.m_41619_()) {
            Item item = stack.m_41720_();
            if (item instanceof ProtectionItem) {
                ProtectionItem pi = (ProtectionItem)item;
                return pi.tier.id;
            }
            CompoundTag tag = stack.m_41783_();
            if (tag != null && tag.m_128425_(NBT_KEY, 10)) {
                CompoundTag root = tag.m_128469_(NBT_KEY);
                return !root.m_128425_(NBT_TIER_FIELD, 8) ? null : root.m_128461_(NBT_TIER_FIELD);
            }
            return null;
        }
        return null;
    }

    public static ClaimTier readTier(ItemStack stack) {
        String id = ClaimBlocks.readTierId(stack);
        return id == null ? null : ClaimTier.byId(id);
    }
}

