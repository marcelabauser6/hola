package com.fantasticicons.client;

import com.fantasticicons.icon.IconRegistry;
import com.fantasticicons.icon.NameDecorator;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Decora la entrada FINAL de la lista TAB del cliente.
 *
 * TAB/LuckPerms construyen el componente del jugador en servidor, ya sea como
 * tabListDisplayName o mediante el prefijo del equipo. PlayerTabOverlay#getNameForDisplay
 * es el ultimo punto comun antes de medir y dibujar cada entrada: aqui ya estan
 * aplicados rango, prefijo, colores y nombre final, y solo agregamos el glifo
 * correspondiente al UUID sin escribir de vuelta sobre los datos de TAB.
 */
public final class ClientTabDecorator {
   private ClientTabDecorator() {
   }

   public static Component decorate(PlayerInfo playerInfo, Component finalName) {
      if (playerInfo == null || finalName == null || playerInfo.getProfile() == null) {
         return finalName;
      }

      String iconId = ClientIconStore.iconOf(playerInfo.getProfile().getId());
      IconRegistry.Icon icon = IconRegistry.get(iconId);
      if (icon == null || finalName.getString().indexOf(icon.character()) >= 0) {
         return finalName;
      }

      return NameDecorator.decorate(finalName, iconId);
   }
}
