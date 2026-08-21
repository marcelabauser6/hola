package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.wallet.CoinType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Pushes a fresh cash balance and coin counts to whichever wallet screen the player has open. */
public class S2CWalletSyncPacket {

    private final long cashCents;
    private final int[] coinCounts;
    private final boolean atmNearby;

    public S2CWalletSyncPacket(long cashCents, int[] coinCounts, boolean atmNearby) {
        this.cashCents = cashCents;
        this.coinCounts = coinCounts.clone();
        this.atmNearby = atmNearby;
    }

    public S2CWalletSyncPacket(FriendlyByteBuf buffer) {
        this.cashCents = buffer.readVarLong();
        this.coinCounts = new int[CoinType.ORDERED.length];
        for (int i = 0; i < coinCounts.length; i++) {
            this.coinCounts[i] = buffer.readVarInt();
        }
        this.atmNearby = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarLong(cashCents);
        for (int count : coinCounts) {
            buffer.writeVarInt(count);
        }
        buffer.writeBoolean(atmNearby);
    }

    public long cashCents() {
        return cashCents;
    }

    public int[] coinCounts() {
        return coinCounts;
    }

    public boolean atmNearby() {
        return atmNearby;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            // Client-only code lives in a separate class so a dedicated server never loads it.
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.apply(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
