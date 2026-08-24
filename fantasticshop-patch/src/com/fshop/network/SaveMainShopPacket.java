/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package com.fshop.network;

import com.fshop.Perms;
import com.fshop.data.FShopSavedData;
import com.fshop.economy.CoinEconomy;
import com.fshop.shop.PlayerShop;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class SaveMainShopPacket {
    private final PlayerShop shop;

    public SaveMainShopPacket(PlayerShop shop) {
        this.shop = shop;
    }

    public static void encode(SaveMainShopPacket packet, FriendlyByteBuf buf) {
        packet.shop.toBuf(buf);
    }

    public static SaveMainShopPacket decode(FriendlyByteBuf buf) {
        return new SaveMainShopPacket(PlayerShop.fromBuf(buf));
    }

    public static void handle(SaveMainShopPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            FShopSavedData data;
            PlayerShop existing;
            ServerPlayer sender = context.getSender();
            if (sender == null || !Perms.isAdmin(sender)) {
                return;
            }
            PlayerShop incoming = packet.shop;
            if (!incoming.getId().equals(FShopSavedData.MAIN_SHOP_ID)) {
                return;
            }
            incoming.setMain(true);
            if (incoming.getOwner() == null) {
                incoming.setOwner(sender.m_20148_());
            }
            if ((existing = (data = FShopSavedData.get(sender.m_284548_())).getMainShop()) != null) {
                // Every currency, not the first three. The incoming shop is a fresh object built by
                // the editor with no earnings, so a currency skipped here is money deleted by the act
                // of saving the shop - and cash was the one being skipped.
                for (int c = 0; c < CoinEconomy.TYPES; ++c) {
                    incoming.addEarnings(c, existing.getPendingEarnings(c));
                }
            }
            data.putShop(incoming);
            sender.m_213846_((Component)Component.m_237113_((String)("[FShop] Tienda principal guardada: \"" + incoming.getName() + "\" (" + incoming.getOffers().size() + " items).")).m_130940_(ChatFormatting.GREEN));
        });
        context.setPacketHandled(true);
    }
}

