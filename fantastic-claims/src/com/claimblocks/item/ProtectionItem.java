/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.level.Level
 */
package com.claimblocks.item;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.ClaimTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ProtectionItem
extends Item {
    public final ClaimTier tier;

    public ProtectionItem(ClaimTier tier) {
        super(new Item.Properties().m_41487_(64));
        this.tier = tier;
    }

    public boolean m_5812_(ItemStack stack) {
        return true;
    }

    public void m_7373_(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ChatFormatting color = ClaimBlocks.colorForTier(this.tier);
        tooltip.add((Component)Component.m_237113_((String)("Radio: " + this.tier.radius + " \u00b7 Altura: \u00b1" + this.tier.height)).m_130940_(ChatFormatting.GRAY));
        tooltip.add((Component)Component.m_237113_((String)"Coloca para crear una protecci\u00f3n").m_130940_(color));
    }
}

