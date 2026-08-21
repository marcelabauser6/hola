package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.wallet.WalletSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client to open the wallet screen.
 *
 * <p>The wallet is opened this way instead of through {@code NetworkHooks.openScreen} on purpose.
 * Opening a container menu makes the server swap {@code ServerPlayer.containerMenu} and send
 * open/close container packets, which drags the player's inventory screen into the flow. The
 * wallet is a read-only report with no slots, so it has no business being a container at all.</p>
 */
public class S2COpenWalletPacket {

    private final WalletSnapshot snapshot;

    public S2COpenWalletPacket(WalletSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public S2COpenWalletPacket(FriendlyByteBuf buffer) {
        this.snapshot = WalletSnapshot.read(buffer);
    }

    public void encode(FriendlyByteBuf buffer) {
        snapshot.write(buffer);
    }

    public WalletSnapshot snapshot() {
        return snapshot;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.openWallet(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
