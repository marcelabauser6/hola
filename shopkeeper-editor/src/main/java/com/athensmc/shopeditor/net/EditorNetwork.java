package com.athensmc.shopeditor.net;

import com.athensmc.shopeditor.ShopEditor;
import com.athensmc.shopeditor.trade.TradeLine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * The channel the editor talks over.
 *
 * <p>Forge's own channel rather than a Bukkit plugin message, which is what this used to need. Now that the
 * editor is a mod on both sides, both ends speak the same protocol and there is no framing to reverse-engineer -
 * one fewer thing to get subtly wrong.</p>
 *
 * <p>Two messages, both small. The server sends the shop's contents when an admin asks to edit it, and the
 * client sends back the whole list when they save. Sending the whole list rather than a stream of edits is
 * deliberate: a save is then one atomic thing that either applied or did not, instead of a sequence that can
 * half-apply if the player disconnects in the middle of it.</p>
 */
public final class EditorNetwork {

    private static final String VERSION = "1";

    /** Sanity bound on a shop's size, so a malformed packet cannot ask for a billion trades. */
    public static final int MAX_TRADES = 256;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ShopEditor.MOD_ID, "editor"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    private EditorNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(OpenEditorMessage.class, id++)
                .encoder(OpenEditorMessage::write)
                .decoder(OpenEditorMessage::read)
                .consumerMainThread(OpenEditorMessage::handle)
                .add();
        CHANNEL.messageBuilder(SaveTradesMessage.class, id++)
                .encoder(SaveTradesMessage::write)
                .decoder(SaveTradesMessage::read)
                .consumerMainThread(SaveTradesMessage::handle)
                .add();
    }

    /** Sends a shop's contents to the admin who asked to edit it. */
    public static void sendEditor(ServerPlayer player, OpenEditorMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Sends an edited shop back to the server. */
    public static void sendSave(SaveTradesMessage message) {
        CHANNEL.sendToServer(message);
    }

    // ---- shared encoding ------------------------------------------------------------------------

    static void writeTrades(FriendlyByteBuf buffer, List<TradeLine> trades) {
        int count = Math.min(trades.size(), MAX_TRADES);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            TradeLine trade = trades.get(i);
            buffer.writeUtf(trade.itemId(), 256);
            buffer.writeVarInt(trade.amount());
            buffer.writeVarLong(trade.priceCents());
        }
    }

    /**
     * Reads a list of trades, dropping any entry that is not valid.
     *
     * <p>Dropped rather than refused wholesale: a single bad row - an item this server does not have, an amount
     * of zero - should cost that row and not the whole shop. Anything malformed enough to throw is caught by the
     * caller, which is what stops a hand-crafted packet from taking the server down.</p>
     */
    static List<TradeLine> readTrades(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_TRADES);
        List<TradeLine> trades = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            String itemId = buffer.readUtf(256);
            int amount = buffer.readVarInt();
            long price = buffer.readVarLong();
            try {
                trades.add(new TradeLine(itemId, amount, price));
            } catch (IllegalArgumentException invalidRow) {
                ShopEditor.LOGGER.debug("Fila de trato descartada: {} x{} a {}", itemId, amount, price);
            }
        }
        return trades;
    }
}
