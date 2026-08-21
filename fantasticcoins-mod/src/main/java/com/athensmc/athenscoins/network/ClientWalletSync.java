package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.client.screen.BankTerminalScreen;
import com.athensmc.athenscoins.client.screen.StatsScreen;
import com.athensmc.athenscoins.client.screen.WalletScreen;
import com.athensmc.athenscoins.client.ClientCashCache;
import com.athensmc.athenscoins.menu.WalletStateHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-side handling for the mod's packets. A dedicated server never loads this class. */
@OnlyIn(Dist.CLIENT)
final class ClientWalletSync {

    private ClientWalletSync() {
    }

    /** Refreshes whichever wallet-aware container screen is open (currently the ATM). */
    static void apply(S2CWalletSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // Keep the shared cache current so other mods' GUIs can read the balance client-side.
        ClientCashCache.set(packet.cashCents());

        AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu instanceof WalletStateHolder holder) {
            holder.applyState(packet.cashCents(), packet.coinCounts(), packet.atmNearby());
        }
    }

    static void openStats(S2COpenStatsPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new StatsScreen(packet.snapshot()));
        }
    }

    static void openTerminal(S2COpenTerminalPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new BankTerminalScreen(packet));
        }
    }

    static void openWallet(S2COpenWalletPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new WalletScreen(packet.snapshot()));
    }
}
