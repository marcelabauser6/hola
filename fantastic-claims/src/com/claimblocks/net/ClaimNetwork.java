/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraftforge.network.NetworkRegistry
 *  net.minecraftforge.network.PacketDistributor
 *  net.minecraftforge.network.simple.SimpleChannel
 */
package com.claimblocks.net;

import com.claimblocks.net.ClaimBordersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ClaimNetwork {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    private ClaimNetwork() {
    }

    public static void init() {
        // Canal OPCIONAL. Antes se pasaba PROTOCOL::equals como predicado en ambos lados, lo que
        // devuelve false para los valores especiales ABSENT y ACCEPTVANILLA y convertia el canal
        // en obligatorio: cualquier cliente sin el mod (o a traves de un proxy) era rechazado.
        // Con acceptMissingOr el borde por paquete es un extra y no un requisito para conectarse.
        CHANNEL = NetworkRegistry.newSimpleChannel((ResourceLocation)new ResourceLocation("claimblocks", "main"), () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
        CHANNEL.registerMessage(0, ClaimBordersPacket.class, ClaimBordersPacket::encode, ClaimBordersPacket::decode, ClaimBordersPacket::handle);
    }

    public static void sendTo(ServerPlayer player, Object message) {
        if (CHANNEL != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }
}

