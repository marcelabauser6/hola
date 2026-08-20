package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.menu.WalletStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-side application of {@link S2CWalletSyncPacket}. Never touched by a dedicated server. */
@OnlyIn(Dist.CLIENT)
final class ClientWalletSync {

    private ClientWalletSync() {
    }

    static void apply(S2CWalletSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu instanceof WalletStateHolder holder) {
            holder.applyState(packet.digital(), packet.cash(), packet.atmNearby());
        }
    }
}
