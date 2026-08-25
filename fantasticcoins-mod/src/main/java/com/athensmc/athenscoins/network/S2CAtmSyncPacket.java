package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.menu.AtmState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Pushes a whole fresh {@link AtmState} to an open ATM screen.
 *
 * <p>{@link S2CWalletSyncPacket} only carries cash and coin counts, which was all the ATM used to
 * show. Now that the screen also displays the account balance, the wallet ceiling's remaining room
 * and the live loan, a partial refresh would leave those stale right after the action that changed
 * them - a player paying off a loan would watch their balance drop while the debt still read the old
 * figure. This sends the lot.</p>
 */
public class S2CAtmSyncPacket {

    private final AtmState state;

    public S2CAtmSyncPacket(AtmState state) {
        this.state = state;
    }

    public S2CAtmSyncPacket(FriendlyByteBuf buffer) {
        this.state = AtmState.read(buffer);
    }

    public void encode(FriendlyByteBuf buffer) {
        state.write(buffer);
    }

    public AtmState state() {
        return state;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            // Client-only code lives in a separate class so a dedicated server never loads it.
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.applyAtm(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
