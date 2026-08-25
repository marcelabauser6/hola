package com.fantasticicons.server;

import com.fantasticicons.FantasticIcons;
import net.minecraft.server.level.ServerPlayer;

/**
 * Invalida exclusivamente el nombre general que Forge usa para chat vanilla.
 *
 * No se debe tocar refreshTabListName ni enviar UPDATE_DISPLAY_NAME: un nombre
 * TAB explicito hace que PlayerTabOverlay omita el formato del equipo creado
 * por TAB/LuckPerms y desaparezca el rango. La lista se decora unicamente en el
 * cliente, despues de que TAB ya haya aplicado prefijo, color y nombre final.
 */
public final class ServerNames {
   private ServerNames() {
   }

   public static void refresh(ServerPlayer player) {
      if (player != null) {
         invoke(player, "refreshDisplayName");
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
