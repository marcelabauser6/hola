package com.athensmc.shopeditor.server;

import com.athensmc.shopeditor.ShopEditor;
import com.athensmc.shopeditor.net.SaveTradesMessage;
import com.athensmc.shopeditor.shop.ShopData;
import com.athensmc.shopeditor.trade.TradeLine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * The server half of the editor: who may edit, and writing what they saved.
 *
 * <p>Administrators only. The check lives here rather than only where the editor is opened, because a client can
 * send a save message whenever it likes - trusting the earlier check would let anyone who installed the mod
 * rewrite every shop on the server.</p>
 */
public final class EditorServer {

    /** Operator level required to edit a shop. */
    public static final int PERMISSION_LEVEL = 2;

    /** Which shop each admin currently has open, so a save cannot be aimed at a different one. */
    private static final java.util.Map<UUID, UUID> editing = new java.util.HashMap<>();

    private EditorServer() {
    }

    public static boolean mayEdit(ServerPlayer player) {
        return player.hasPermissions(PERMISSION_LEVEL);
    }

    /** Records that an admin is editing a shop, so the save that follows can be matched to it. */
    public static void beginEditing(ServerPlayer admin, UUID villager) {
        editing.put(admin.getUUID(), villager);
    }

    public static void stopEditing(ServerPlayer admin) {
        editing.remove(admin.getUUID());
    }

    /**
     * Applies a saved shop.
     *
     * <p>The shop written to is the one the server recorded when the editor was opened, not one named in the
     * message. A client-supplied target would be a way to edit any shop on the server by sending the right
     * number, whether or not its editor was ever opened.</p>
     */
    public static void save(ServerPlayer admin, SaveTradesMessage message) {
        if (!mayEdit(admin)) {
            ShopEditor.LOGGER.warn("{} intentó guardar una tienda sin permiso.",
                    admin.getGameProfile().getName());
            admin.sendSystemMessage(Component.literal("No tienes permiso para editar tiendas.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        UUID villager = editing.get(admin.getUUID());
        if (villager == null) {
            admin.sendSystemMessage(Component.literal(
                            "No tenías ninguna tienda abierta, así que no he guardado nada.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!(admin.level() instanceof ServerLevel level)) {
            return;
        }

        List<TradeLine> trades = message.trades();
        ShopData.get(level).setTrades(villager, trades);
        stopEditing(admin);

        admin.sendSystemMessage(Component.literal("Tienda guardada: " + trades.size()
                        + (trades.size() == 1 ? " trato." : " tratos."))
                .withStyle(ChatFormatting.GREEN));
        ShopEditor.LOGGER.info("{} guardó {} tratos en la tienda {}.",
                admin.getGameProfile().getName(), trades.size(), villager);
    }
}
