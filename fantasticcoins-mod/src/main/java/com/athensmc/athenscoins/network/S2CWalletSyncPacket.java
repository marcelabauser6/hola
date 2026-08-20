package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.wallet.CoinType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Pushes fresh wallet + pocket figures to whichever wallet-aware screen the player has open. */
public class S2CWalletSyncPacket {

    private final long[] digital;
    private final int[] cash;
    private final boolean atmNearby;

    public S2CWalletSyncPacket(long[] digital, int[] cash, boolean atmNearby) {
        this.digital = digital.clone();
        this.cash = cash.clone();
        this.atmNearby = atmNearby;
    }

    public S2CWalletSyncPacket(FriendlyByteBuf buffer) {
        this.digital = new long[CoinType.ORDERED.length];
        this.cash = new int[CoinType.ORDERED.length];
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            this.digital[i] = buffer.readVarLong();
            this.cash[i] = buffer.readVarInt();
        }
        this.atmNearby = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        for (int i = 0; i < CoinType.ORDERED.length; i++) {
            buffer.writeVarLong(digital[i]);
            buffer.writeVarInt(cash[i]);
        }
        buffer.writeBoolean(atmNearby);
    }

    public long[] digital() {
        return digital;
    }

    public int[] cash() {
        return cash;
    }

    public boolean atmNearby() {
        return atmNearby;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            // Client-only code lives in a separate class so the dedicated server never loads it.
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.apply(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
