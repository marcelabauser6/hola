package com.fantasticicons.client;

import com.fantasticicons.icon.IconRegistry;
import com.fantasticicons.icon.NameDecorator;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Decora la entrada FINAL de la lista TAB del cliente.
 *
 * El plugin TAB reemplaza el tabListDisplayName desde Bukkit despues de que
 * Forge calcula PlayerEvent.TabListNameFormat. Por eso el icono del servidor se
 * pierde. PlayerTabOverlay#getNameForDisplay es el ultimo punto comun antes de
 * medir y dibujar cada entrada: aqui ya estan aplicados prefijo, colores y
 * formato de TAB, y solo agregamos el glifo correspondiente al UUID.
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
