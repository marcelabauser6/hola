package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.block.StatsHologramBlockEntity;
import com.athensmc.athenscoins.client.screen.AccountDetailScreen;
import com.athensmc.athenscoins.client.screen.AtmScreen;
import com.athensmc.athenscoins.client.screen.BankTerminalScreen;
import com.athensmc.athenscoins.client.screen.CentralBankScreen;
import com.athensmc.athenscoins.client.screen.StatsHologramEditorScreen;
import com.athensmc.athenscoins.client.screen.WalletScreen;
import com.athensmc.athenscoins.client.ClientCashCache;
import com.athensmc.athenscoins.menu.AtmMenu;
import com.athensmc.athenscoins.menu.WalletStateHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

    /** Replaces the open ATM's whole state, including the bank-side figures and the live loan. */
    static void applyAtm(S2CAtmSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ClientCashCache.set(packet.state().cash());
        if (minecraft.player.containerMenu instanceof AtmMenu menu) {
            menu.applyState(packet.state());
        }
        // Widgets are built from the state, so the screen has to rebuild them to match.
        if (minecraft.screen instanceof AtmScreen screen) {
            screen.onStateChanged();
        }
    }

    /**
     * Opens the hologram editor from the projector the client already has.
     *
     * <p>Unlike every other open handler here, there is no payload to unpack: the configuration and the
     * figures live in the block entity, because that is what the world renderer draws from. If the
     * projector has gone - broken between the click and the packet landing - say so rather than opening
     * an editor pointed at nothing.</p>
     */
    static void openHologramEditor(S2COpenHologramPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.level.getBlockEntity(packet.pos()) instanceof StatsHologramBlockEntity projector) {
            minecraft.setScreen(StatsHologramEditorScreen.forProjector(projector));
        } else {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.athens_coins.hologram_gone")
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    /**
     * Opens the terminal, keeping the view if one is already open.
     *
     * <p>Every terminal action ends with the server re-sending the whole screen, which is how the lists stay
     * current. Replacing it outright meant the new screen opened on its first tab, so acting on a row threw
     * you off the tab you were working on and the confirmation you had just given looked like a navigation
     * mistake. The refresh now inherits the tab, the scroll position and the feedback line.</p>
     */
    static void openTerminal(S2COpenTerminalPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        BankTerminalScreen screen = new BankTerminalScreen(packet);
        if (minecraft.screen instanceof BankTerminalScreen open) {
            screen.restoreFrom(open);
        }
        minecraft.setScreen(screen);
    }

    static void openAccount(S2COpenAccountPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // Keep whatever is open as the back target, unless it is already a detail screen.
        net.minecraft.client.gui.screens.Screen current = minecraft.screen;
        if (current instanceof AccountDetailScreen detail) {
            current = detail.parentScreen();
        }
        AccountDetailScreen screen = new AccountDetailScreen(current, packet);
        // Lending or collecting re-pushes this screen; without carrying the page, the ledger jumped back
        // to the top every time, which on a long history looks like the button reset the account.
        if (minecraft.screen instanceof AccountDetailScreen open) {
            screen.restoreFrom(open);
        }
        minecraft.setScreen(screen);
    }

    static void openCentral(S2COpenCentralPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        CentralBankScreen screen = new CentralBankScreen(packet);
        if (minecraft.screen instanceof CentralBankScreen open) {
            screen.restoreFrom(open);
        }
        minecraft.setScreen(screen);
    }

    static void openWallet(S2COpenWalletPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new WalletScreen(packet.snapshot()));
    }
}
