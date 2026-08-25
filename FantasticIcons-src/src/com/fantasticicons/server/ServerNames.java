package com.fantasticicons.server;

import com.fantasticicons.FantasticIcons;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Forge cachea el nombre para mostrar (Player.displayname y
 * ServerPlayer.hasTabListName), asi que al cambiar un icono hay que invalidar
 * ambos caches y reenviar la entrada de la lista de tab. Los metodos de refresco
 * los añade Forge por parche, no estan en el jar de Minecraft, asi que se
 * invocan por reflexion: sus nombres no se ofuscan.
 */
public final class ServerNames {
   private ServerNames() {
   }

   public static void refresh(ServerPlayer player) {
      if (player == null) {
         return;
      }

      invoke(player, "refreshDisplayName");
      invoke(player, "refreshTabListName");
      MinecraftServer server = player.getServer();
      if (server != null && server.getPlayerList() != null) {
         try {
            server.getPlayerList()
               .broadcastAll(
                  new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(player))
               );
         } catch (Throwable error) {
            FantasticIcons.LOGGER.warn("[FantasticIcons] No se pudo refrescar la lista de tab", error);
         }
      }
   }

   private static void invoke(ServerPlayer player, String method) {
      try {
         player.getClass().getMethod(method).invoke(player);
      } catch (Throwable error) {
         FantasticIcons.LOGGER.debug("[FantasticIcons] " + method + " no disponible: " + error);
      }
   }
}
