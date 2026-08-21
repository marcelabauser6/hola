/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 */
package com.fshop.shop;

import com.fshop.shop.ShopService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ResultMessages {
    private ResultMessages() {
    }

    public static Component of(ShopService.Result result) {
        String key;
        ChatFormatting color = ChatFormatting.RED;
        switch (result) {
            case OK: {
                key = "fshop.msg.ok";
                color = ChatFormatting.GREEN;
                break;
            }
            case NO_SHOP: {
                key = "fshop.msg.no_shop";
                break;
            }
            case NO_OFFER: {
                key = "fshop.msg.no_offer";
                break;
            }
            case NOT_OWNER: {
                key = "fshop.msg.not_owner";
                break;
            }
            case NO_BANK_ACCOUNT: {
                key = "fshop.msg.no_bank_account";
                break;
            }
            case OUT_OF_STOCK: {
                key = "fshop.msg.out_of_stock";
                break;
            }
            case CANNOT_AFFORD: {
                key = "fshop.msg.cannot_afford";
                break;
            }
            case INVENTORY_FULL: {
                key = "fshop.msg.inventory_full";
                break;
            }
            case NO_CURRENCY: {
                key = "fshop.msg.no_currency";
                break;
            }
            case LIMIT_REACHED: {
                key = "fshop.msg.limit_reached";
                break;
            }
            case OWN_SHOP: {
                key = "fshop.msg.own_shop";
                break;
            }
            default: {
                key = "fshop.msg.invalid";
            }
        }
        return Component.m_237113_((String)"[FShop] ").m_130940_(ChatFormatting.GOLD).m_7220_((Component)Component.m_237115_((String)key).m_130940_(color));
    }
}

