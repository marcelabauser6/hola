package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.network.FantasticNetwork;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reglas de transicion entre modos de juego.
 *
 * <p>Cambiar de modo tiene que dejar la sala en un estado limpio: si una sala pasa a Meccha, nadie
 * puede quedarse disfrazado de bloque, y si pasa a Prop Hunt conviene empezar sin disfraz previo
 * para que la primera captura sea la que manda.
 */
public final class PropHuntRules {

   private PropHuntRules() {
   }

   /** Limpia el disfraz de prop de todos los miembros y les avisa del modo nuevo. */
   public static void onGameModeChanged(MinecraftServer server, Room room, int mode) {
      if (server == null || room == null) {
         return;
      }

      Component label = Component.m_237115_(PropHunt.nameKey(mode));

      for (UUID id : room.roster()) {
         ServerPlayer member = server.m_6846_().m_11259_(id);
         if (member != null) {
            FantasticNetwork.clearProp(member);
            member.m_240418_(Component.m_237110_("fantastic.gamemode.switched", new Object[]{label}).m_130940_(ChatFormatting.AQUA), true);
         }
      }
   }
}
