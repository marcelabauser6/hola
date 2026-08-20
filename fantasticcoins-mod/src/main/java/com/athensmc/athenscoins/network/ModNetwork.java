package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.AthensCoinsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "2";

    private static SimpleChannel channel;

    private ModNetwork() {
    }

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(AthensCoinsMod.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals);

        int id = 0;
        channel.messageBuilder(S2CWalletSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CWalletSyncPacket::encode)
                .decoder(S2CWalletSyncPacket::new)
                .consumerMainThread(S2CWalletSyncPacket::handle)
                .add();

        channel.messageBuilder(S2COpenWalletPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenWalletPacket::encode)
                .decoder(S2COpenWalletPacket::new)
                .consumerMainThread(S2COpenWalletPacket::handle)
                .add();
    }

    public static void toPlayer(ServerPlayer player, Object message) {
        if (channel == null) {
            return;
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
