package com.fantasticicons.client;

import com.fantasticicons.icon.IconRegistry;
import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Compatibilidad con formateadores Bukkit (EssentialsChat en Mohist).
 *
 * EssentialsChat reconstruye el componente final usando Bukkit y, aun con
 * {DISPLAYNAME}, Mohist no copia el nombre que Forge modifico mediante
 * PlayerEvent.NameFormat. Este decorador trabaja sobre el componente FINAL que
 * recibe el cliente: localiza el nombre del emisor y coloca el glifo justo
 * despues, sin aplanar ni perder colores, hover o clics del prefijo.
 */
public final class ClientChatDecorator {
   private static final UUID NIL_UUID = new UUID(0L, 0L);

   private ClientChatDecorator() {
   }

   public static Component decorate(Component message, UUID sender, boolean systemMessage) {
      if (message == null) {
         return null;
      }

      String plain = message.getString();
      if (plain.isEmpty()) {
         return message;
      }

      ClientPacketListener connection = Minecraft.getInstance().getConnection();
      if (connection == null) {
         return message;
      }

      // Camino fiable: el paquete conserva el UUID del jugador aunque
      // EssentialsChat haya reemplazado el contenido visual.
      if (sender != null && !NIL_UUID.equals(sender)) {
         String iconId = ClientIconStore.iconOf(sender);
         PlayerInfo info = connection.getPlayerInfo(sender);
         if (iconId != null && info != null) {
            return decorateFor(message, plain, info.getProfile().getName(), iconId, false);
         }
      }

      // Algunos builds de Mohist convierten el chat Bukkit en mensaje de
      // sistema y pierden el UUID. Respaldo: busca, entre los jugadores online
      // que tienen icono, el primer nombre-token dentro del prefijo del chat.
      // Exigimos contenido antes del nombre para no tocar mensajes vanilla como
      // "Pewez777 joined the game".
      if (systemMessage) {
         Match match = findFormattedSender(plain, connection.getOnlinePlayers(), ClientIconStore.snapshot());
         if (match != null) {
            return insertAfter(message, match.index + match.name.length(), match.iconId);
         }
      }

      return message;
   }

   private static Component decorateFor(Component message, String plain, String playerName, String iconId, boolean requirePrefix) {
      if (playerName == null || iconId == null || !IconRegistry.exists(iconId) || alreadyContains(plain, iconId)) {
         return message;
      }

      int index = indexOfToken(plain, playerName);
      if (index < 0 || requirePrefix && index == 0) {
         return message;
      }

      return insertAfter(message, index + playerName.length(), iconId);
   }

   private static Match findFormattedSender(String plain, Collection<PlayerInfo> online, Map<UUID, String> icons) {
      Match best = null;

      for (PlayerInfo info : online) {
         GameProfile profile = info.getProfile();
         String iconId = icons.get(profile.getId());
         if (iconId == null || !IconRegistry.exists(iconId) || alreadyContains(plain, iconId)) {
            continue;
         }

         String name = profile.getName();
         int index = indexOfToken(plain, name);
         if (index > 0 && (best == null || index < best.index)) {
            best = new Match(index, name, iconId);
         }
      }

      return best;
   }

   /**
    * Inserta " + glifo" en una posicion del texto plano, reconstruyendo desde
    * toFlatList(). Cada fragmento conserva su Style efectivo, de modo que los
    * colores de LuckPerms/Essentials, hover y click sobreviven intactos.
    */
   static Component insertAfter(Component original, int insertionIndex, String iconId) {
      if (insertionIndex < 0 || insertionIndex > original.getString().length() || !IconRegistry.exists(iconId)) {
         return original;
      }

      MutableComponent result = Component.empty();
      int cursor = 0;
      boolean inserted = false;

      for (Component flat : original.toFlatList()) {
         String text = flat.getString();
         Style style = flat.getStyle();
         int end = cursor + text.length();

         if (!inserted && insertionIndex >= cursor && insertionIndex <= end) {
            int local = insertionIndex - cursor;
            appendStyled(result, text.substring(0, local), style);
            result.append(Component.literal(" ").setStyle(style));
            result.append(IconRegistry.glyph(iconId));
            appendStyled(result, text.substring(local), style);
            inserted = true;
         } else {
            appendStyled(result, text, style);
         }

         cursor = end;
      }

      return inserted ? result : original;
   }

   private static void appendStyled(MutableComponent target, String text, Style style) {
      if (!text.isEmpty()) {
         target.append(Component.literal(text).setStyle(style));
      }
   }

   private static boolean alreadyContains(String text, String iconId) {
      IconRegistry.Icon icon = IconRegistry.get(iconId);
      return icon != null && text.indexOf(icon.character()) >= 0;
   }

   /** Coincidencia de nombre completa, no dentro de otro nombre o palabra. */
   private static int indexOfToken(String text, String token) {
      if (text == null || token == null || token.isEmpty()) {
         return -1;
      }

      String haystack = text.toLowerCase(Locale.ROOT);
      String needle = token.toLowerCase(Locale.ROOT);
      int from = 0;

      while (from <= haystack.length() - needle.length()) {
         int index = haystack.indexOf(needle, from);
         if (index < 0) {
            return -1;
         }

         int end = index + needle.length();
         boolean leftOk = index == 0 || !isNameCharacter(haystack.charAt(index - 1));
         boolean rightOk = end == haystack.length() || !isNameCharacter(haystack.charAt(end));
         if (leftOk && rightOk) {
            return index;
         }

         from = index + 1;
      }

      return -1;
   }

   private static boolean isNameCharacter(char value) {
      return Character.isLetterOrDigit(value) || value == '_';
   }

   private static final class Match {
      private final int index;
      private final String name;
      private final String iconId;

      private Match(int index, String name, String iconId) {
         this.index = index;
         this.name = name;
         this.iconId = iconId;
      }
   }
}
