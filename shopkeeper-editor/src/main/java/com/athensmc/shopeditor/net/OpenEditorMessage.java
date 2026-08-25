package com.athensmc.shopeditor.net;

import com.athensmc.shopeditor.client.EditorClient;
import com.athensmc.shopeditor.trade.TradeLine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client: here is the shop you asked to edit.
 *
 * <p>Carries everything the screens need to be useful without asking again: the shop's name, its trades, the
 * currency symbol so amounts read the way they do in the wallet, and the dearest price this server can actually
 * express. That last one is not decoration - Shopkeepers has only two payment slots, so there is a real ceiling,
 * and the editor can grey out an impossible price as it is typed instead of rejecting it on save.</p>
 *
 * @param shopId     the shop being edited, as Shopkeepers identifies it.
 * @param shopName   what to put in the title.
 * @param trades     what the shop currently sells.
 * @param symbol     the currency symbol, e.g. the Cash sign.
 * @param maxCents   the dearest price that can be expressed, in cents.
 */
public record OpenEditorMessage(int shopId, String shopName, List<TradeLine> trades, String symbol,
                                long maxCents) {

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(shopId);
        buffer.writeUtf(shopName, 128);
        buffer.writeUtf(symbol, 16);
        buffer.writeVarLong(maxCents);
        EditorNetwork.writeTrades(buffer, trades);
    }

    public static OpenEditorMessage read(FriendlyByteBuf buffer) {
        int shopId = buffer.readVarInt();
        String shopName = buffer.readUtf(128);
        String symbol = buffer.readUtf(16);
        long maxCents = buffer.readVarLong();
        return new OpenEditorMessage(shopId, shopName, EditorNetwork.readTrades(buffer), symbol,
                maxCents);
    }

    /**
     * Opens the editor on the client.
     *
     * <p>Guarded on the dist rather than trusted: this message only ever travels to a client, but a class that
     * touches a screen must not be reachable on a dedicated server, or loading it there is a crash.</p>
     */
    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                EditorClient.open(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
