package com.fantasticicons.server;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

/**
 * Resuelve un nombre a UUID: primero jugadores conectados, luego la cache de
 * perfiles y, en servidores offline-mode, el UUID offline (igual que hace
 * Fantastic Claims) para que funcione con gente desconectada.
 */
public final class PlayerResolver {
   private PlayerResolver() {
   }

   public static Target resolve(MinecraftServer server, String rawName) {
      if (server == null || rawName == null || rawName.isBlank()) {
         return null;
      }

      String name = rawName.trim();
      ServerPlayer online = server.getPlayerList().getPlayerByName(name);
      if (online != null) {
         return new Target(online.getUUID(), online.getGameProfile().getName(), online);
      }

      GameProfileCache cache = server.getProfileCache();
      if (cache != null) {
         Optional<GameProfile> profile = cache.get(name);
         if (profile.isPresent() && profile.get().getId() != null) {
            GameProfile found = profile.get();
            String cachedName = found.getName() == null ? name : found.getName();
            return new Target(found.getId(), cachedName, server.getPlayerList().getPlayer(found.getId()));
         }
      }

      if (!server.usesAuthentication()) {
         UUID offline = UUIDUtil.createOfflinePlayerUUID(name);
         return new Target(offline, name, server.getPlayerList().getPlayer(offline));
      }

      return null;
   }

   public static String nameOf(MinecraftServer server, UUID id) {
      if (id == null) {
         return "?";
      }

      if (server != null) {
         ServerPlayer online = server.getPlayerList().getPlayer(id);
         if (online != null) {
            return online.getGameProfile().getName();
         }

         GameProfileCache cache = server.getProfileCache();
         if (cache != null) {
            Optional<GameProfile> profile = cache.get(id);
            if (profile.isPresent() && profile.get().getName() != null) {
               return profile.get().getName();
            }
         }
      }

      return id.toString().substring(0, 8);
   }

   public static record Target(UUID id, String name, ServerPlayer online) {
      public boolean isOnline() {
         return this.online != null;
      }
   }
}
