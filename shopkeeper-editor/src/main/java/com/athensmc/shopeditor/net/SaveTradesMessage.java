package com.athensmc.shopeditor.net;

import com.athensmc.shopeditor.server.EditorServer;
import com.athensmc.shopeditor.trade.TradeLine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client to server: save this shop.
 *
 * <p>The whole list, not a diff. A save that either applied or did not is far easier to reason about than a
 * stream of edits that can half-apply, and a shop left half-edited is a shop selling at the wrong price.</p>
 *
 * @param shopId the shop to write to.
 * @param trades what it should sell from now on.
 */
public record SaveTradesMessage(int shopId, List<TradeLine> trades) {

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(shopId);
        EditorNetwork.writeTrades(buffer, trades);
    }

    public static SaveTradesMessage read(FriendlyByteBuf buffer) {
        return new SaveTradesMessage(buffer.readVarInt(), EditorNetwork.readTrades(buffer));
    }

    /**
     * Applies the save, if the sender is allowed to.
     *
     * <p>The permission is checked here and not only when the editor was opened. A client can send this message
     * whenever it likes, so trusting an earlier check would let anyone with the mod installed rewrite every
     * shop on the server.</p>
     */
    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                EditorServer.save(sender, this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
