package com.athensmc.fsshopkeepers.client;

import com.athensmc.fsshopkeepers.client.screen.ShopEditorScreen;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.client.Minecraft;

/**
 * The client's entry point into the editor.
 *
 * <p>A single method behind which every client-only class sits. Packet handlers reach the editor through here and
 * through {@link net.minecraftforge.fml.DistExecutor}, so no class that names a {@code Screen} is ever loaded on a
 * dedicated server - which is what would otherwise crash a server the moment a packet class was initialised.</p>
 */
public final class EditorClient {

    private EditorClient() {
    }

    /** Opens the editor on a shop the server has just sent. */
    public static void open(Shopkeeper.EditorView view) {
        if (view == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new ShopEditorScreen(view));
    }
}
