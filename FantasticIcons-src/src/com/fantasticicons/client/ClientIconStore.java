package com.fantasticicons.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Copia en el cliente de la tabla jugador -> icono. */
public final class ClientIconStore {
   private static volatile Map<UUID, String> icons = Collections.emptyMap();

   private ClientIconStore() {
   }

   public static void receive(Map<UUID, String> incoming) {
      icons = incoming == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(incoming));
   }

   public static String iconOf(UUID player) {
      return player == null ? null : icons.get(player);
   }

   public static Map<UUID, String> snapshot() {
      return new LinkedHashMap<>(icons);
   }

   public static void clear() {
      icons = Collections.emptyMap();
   }
}
