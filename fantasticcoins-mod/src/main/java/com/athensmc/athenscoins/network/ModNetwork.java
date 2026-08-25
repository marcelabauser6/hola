package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.menu.AtmState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    /**
     * Bumped to 6 for the ATM rework: the menu-open buffer now carries the whole {@link AtmState}
     * (loan, lending policy and transfer peers) instead of a handful of loose fields. A 5-era client
     * would read those extra bytes as garbage, so the mismatch has to be refused at handshake rather
     * than surface as nonsensical balances.
     *
     * <p>7 added the bank's configured flag, the loan-request queue and the account credit record.
     * 8 adds the stats hologram's two packets, which also shift the ids of nothing before them but
     * would leave an older client with no decoder for the two at the end.</p>
     */
    private static final String PROTOCOL_VERSION = "10";

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

        channel.messageBuilder(S2COpenTerminalPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenTerminalPacket::encode)
                .decoder(S2COpenTerminalPacket::new)
                .consumerMainThread(S2COpenTerminalPacket::handle)
                .add();

        channel.messageBuilder(C2STerminalActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2STerminalActionPacket::encode)
                .decoder(C2STerminalActionPacket::new)
                .consumerMainThread(C2STerminalActionPacket::handle)
                .add();

        channel.messageBuilder(S2COpenAccountPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenAccountPacket::encode)
                .decoder(S2COpenAccountPacket::new)
                .consumerMainThread(S2COpenAccountPacket::handle)
                .add();

        channel.messageBuilder(C2SRequestAccountPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestAccountPacket::encode)
                .decoder(C2SRequestAccountPacket::new)
                .consumerMainThread(C2SRequestAccountPacket::handle)
                .add();

        channel.messageBuilder(S2COpenCentralPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenCentralPacket::encode)
                .decoder(S2COpenCentralPacket::new)
                .consumerMainThread(S2COpenCentralPacket::handle)
                .add();

        channel.messageBuilder(C2SCentralActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCentralActionPacket::encode)
                .decoder(C2SCentralActionPacket::new)
                .consumerMainThread(C2SCentralActionPacket::handle)
                .add();

        channel.messageBuilder(C2SAtmActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAtmActionPacket::encode)
                .decoder(C2SAtmActionPacket::new)
                .consumerMainThread(C2SAtmActionPacket::handle)
                .add();

        channel.messageBuilder(S2CAtmSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CAtmSyncPacket::encode)
                .decoder(S2CAtmSyncPacket::new)
                .consumerMainThread(S2CAtmSyncPacket::handle)
                .add();

        channel.messageBuilder(C2SBankConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBankConfigPacket::encode)
                .decoder(C2SBankConfigPacket::new)
                .consumerMainThread(C2SBankConfigPacket::handle)
                .add();

        channel.messageBuilder(S2COpenHologramPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenHologramPacket::encode)
                .decoder(S2COpenHologramPacket::new)
                .consumerMainThread(S2COpenHologramPacket::handle)
                .add();

        channel.messageBuilder(C2SHologramConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SHologramConfigPacket::encode)
                .decoder(C2SHologramConfigPacket::new)
                .consumerMainThread(C2SHologramConfigPacket::handle)
                .add();
    }

    /** Sends a packet from the client to the server. */
    public static void toServer(Object message) {
        if (channel != null) {
            channel.sendToServer(message);
        }
    }

    public static void toPlayer(ServerPlayer player, Object message) {
        if (channel == null) {
            return;
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
