package com.athensmc.athenscoins.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "Open the hologram editor for the projector at this position."
 *
 * <p>Just the position. Every other screen-open packet in the mod carries its whole payload, because
 * banks and accounts live in a server-side save file the client has never seen. A hologram is different:
 * its configuration and its figures are already on the client, in the projector's block entity, because
 * that is the only way the hologram gets drawn in the first place.</p>
 *
 * <p>Sending them again would put two copies of the same state on the client and invite them to
 * disagree - the editor would show one thing and the block beside it another, and which one was right
 * would depend on which arrived last.</p>
 */
public class S2COpenHologramPacket {

    private final BlockPos pos;

    public S2COpenHologramPacket(BlockPos pos) {
        this.pos = pos;
    }

    public S2COpenHologramPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    public BlockPos pos() {
        return pos;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.openHologramEditor(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
