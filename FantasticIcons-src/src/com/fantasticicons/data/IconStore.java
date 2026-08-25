package com.fantasticicons.data;

import com.fantasticicons.FantasticIcons;
import com.fantasticicons.icon.IconRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Guarda que icono lleva cada jugador. Un JSON por mundo, plano y sin dramas. */
public final class IconStore {
   private static final IconStore INSTANCE = new IconStore();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Map<UUID, String> icons = new ConcurrentHashMap<>();
   private MinecraftServer server;

   private IconStore() {
   }

   public static IconStore get() {
      return INSTANCE;
   }

   public void load(MinecraftServer server) {
      this.server = server;
      this.icons.clear();
      Path file = this.file();
      if (file == null || !Files.exists(file)) {
         FantasticIcons.LOGGER.info("[FantasticIcons] Sin datos previos, empezando de cero.");
         return;
      }

      try {
         String raw = Files.readString(file, StandardCharsets.UTF_8);
         JsonObject root = GSON.fromJson(raw, JsonObject.class);
         if (root != null && root.has("iconos") && root.get("iconos").isJsonObject()) {
            JsonObject map = root.getAsJsonObject("iconos");

            for (String key : map.keySet()) {
               try {
                  String iconId = map.get(key).getAsString();
                  if (IconRegistry.exists(iconId)) {
                     this.icons.put(UUID.fromString(key), iconId);
                  } else {
                     FantasticIcons.LOGGER.warn("[FantasticIcons] Icono desconocido '" + iconId + "', se ignora.");
                  }
               } catch (Exception entryError) {
                  FantasticIcons.LOGGER.warn("[FantasticIcons] Entrada corrupta '" + key + "', se ignora.");
               }
            }
         }

         FantasticIcons.LOGGER.info("[FantasticIcons] " + this.icons.size() + " iconos asignados cargados.");
      } catch (Exception error) {
         FantasticIcons.LOGGER.error("[FantasticIcons] No se pudieron cargar los iconos", error);
      }
   }

   public void saveNow() {
      Path file = this.file();
      if (file == null) {
         return;
      }

      try {
         Files.createDirectories(file.getParent());
         JsonObject map = new JsonObject();

         for (Map.Entry<UUID, String> entry : this.icons.entrySet()) {
            map.addProperty(entry.getKey().toString(), entry.getValue());
         }

         JsonObject root = new JsonObject();
         root.add("iconos", map);
         Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
      } catch (IOException error) {
         FantasticIcons.LOGGER.error("[FantasticIcons] No se pudieron guardar los iconos", error);
      }
   }

   private Path file() {
      return this.server == null ? null : this.server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("fantasticicons.json");
   }

   public String iconOf(UUID player) {
      return player == null ? null : this.icons.get(player);
   }

   public boolean has(UUID player) {
      return this.iconOf(player) != null;
   }

   /** @return true si el valor cambio realmente. */
   public boolean set(UUID player, String iconId) {
      if (player == null || !IconRegistry.exists(iconId)) {
         return false;
      }

      String previous = this.icons.put(player, iconId);
      this.saveNow();
      return !iconId.equals(previous);
   }

   /** @return true si tenia icono y se quito. */
   public boolean remove(UUID player) {
      if (player == null || this.icons.remove(player) == null) {
         return false;
      }

      this.saveNow();
      return true;
   }

   public Map<UUID, String> snapshot() {
      return new LinkedHashMap<>(this.icons);
   }

   public int size() {
      return this.icons.size();
   }
}
