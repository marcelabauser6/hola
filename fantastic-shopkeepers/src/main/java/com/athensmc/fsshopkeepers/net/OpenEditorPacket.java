package com.athensmc.fsshopkeepers.net;

import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: open the editor on this shop.
 *
 * <p>Carries the shop rather than just its id, so the editor draws a filled-in form the instant it opens. Sending an
 * id and letting the client ask for the details would show an empty editor first, and an admin who starts typing into
 * that empty form loses what they typed when the real values arrive.</p>
 */
public final class OpenEditorPacket {

    /** Set on the server, where the packet is built from the live shop. */
    private final Shopkeeper shop;

    /** Set on the client, where the packet has been read off the wire. */
    private final Shopkeeper.EditorView view;

    public OpenEditorPacket(Shopkeeper shop) {
        this.shop = shop;
        this.view = null;
    }

    private OpenEditorPacket(Shopkeeper.EditorView view) {
        this.shop = null;
        this.view = view;
    }

    public void encode(FriendlyByteBuf buf) {
        shop.writeForEditor(buf);
    }

    public static OpenEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenEditorPacket(Shopkeeper.EditorView.read(buf));
    }

    /**
     * Opens the screen, on the client's own thread.
     *
     * <p>The screen is reached through {@link DistExecutor} so that the class naming it is never loaded on a
     * dedicated server. Referring to a screen class directly from a packet handler is the usual way a server crashes
     * on startup with a missing client class.</p>
     */
    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.athensmc.fsshopkeepers.client.EditorClient.open(view)));
        ctx.setPacketHandled(true);
    }
}
