package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.stats.EconomySnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Carries an economy snapshot to an admin and opens the stats screen. */
public class S2COpenStatsPacket {

    private final EconomySnapshot snapshot;

    public S2COpenStatsPacket(EconomySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public S2COpenStatsPacket(FriendlyByteBuf buffer) {
        this.snapshot = EconomySnapshot.read(buffer);
    }

    public void encode(FriendlyByteBuf buffer) {
        snapshot.write(buffer);
    }

    public EconomySnapshot snapshot() {
        return snapshot;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.openStats(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
