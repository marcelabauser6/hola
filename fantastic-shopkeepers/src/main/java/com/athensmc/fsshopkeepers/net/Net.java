package com.athensmc.fsshopkeepers.net;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's own channel, carrying the editor between client and server.
 *
 * <p>Two messages in each direction is the whole protocol. The server sends a shop to be edited; the client sends
 * back what it wants the shop to become, or asks for it to be deleted. Everything else - opening the trading window,
 * buying, spawning the mob - already has a vanilla path, and inventing packets for those would mean re-validating
 * things the server already validates.</p>
 *
 * <p>The protocol version is checked on both sides. A client running a different version of this mod is refused
 * rather than allowed to send an editor payload the server would read with the wrong field order, which is how a
 * mismatch turns into a shop whose prices are its item ids.</p>
 */
public final class Net {

    private static final String VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(FantasticShopkeepers.MOD_ID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    private Net() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenEditorPacket.class,
                OpenEditorPacket::encode, OpenEditorPacket::decode, OpenEditorPacket::handle);
        CHANNEL.registerMessage(id++, SaveShopPacket.class,
                SaveShopPacket::encode, SaveShopPacket::decode, SaveShopPacket::handle);
        CHANNEL.registerMessage(id++, DeleteShopPacket.class,
                DeleteShopPacket::encode, DeleteShopPacket::decode, DeleteShopPacket::handle);
    }

    /** Sends a shop to an administrator's client so the editor can open. */
    public static void openEditor(ServerPlayer editor, Shopkeeper shop) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> editor), new OpenEditorPacket(shop));
    }

    /** Sends the edited shop back to the server. Client side. */
    public static void saveShop(SaveShopPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    /** Asks the server to delete a shop. Client side. */
    public static void deleteShop(DeleteShopPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
