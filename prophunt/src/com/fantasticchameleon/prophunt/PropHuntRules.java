package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import java.util.Arrays;
import java.util.Random;
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
   private static final Random RNG = new Random();

   private PropHuntRules() {
   }

   /**
    * Disfraza a un bot de prop al azar, para que en Prop Hunt no se queden como muñecos posando.
    *
    * <p>La textura se rellena de un color plano porque el servidor no tiene acceso a los pixeles de
    * los bloques (eso solo existe en el cliente). Da props de colores, que se leen claramente como
    * objetos y no como jugadores.
    */
   public static void dressAsProp(ServerPlayer dummy) {
      int prop = RNG.nextInt(PropShapes.PROPS.length);
      int variant = RNG.nextInt(Math.max(1, PropShapes.variantCount(prop)));
      FantasticNetwork.applyProp(dummy, prop, variant);

      // applyProp deja el lienzo en blanco, asi que el color va despues.
      int side = 64;
      int[] px = new int[side * side];
      Arrays.fill(px, 0xFF000000 | RNG.nextInt(16777216));
      Services.PLATFORM.set(dummy, PaintAttachments.PROP_CANVAS, new BodyCanvas(px));

      // Los props de bloque se alinean con el mundo; los de criatura conservan su giro.
      if (!PropShapes.followsLook(prop)) {
         Services.PLATFORM.set(dummy, PaintAttachments.LOCK_YAW, 0.0F);
      }

      Services.PLATFORM.set(dummy, PaintAttachments.LOCKED, true);
      PropGridSnap.snapToCell(dummy, Services.PLATFORM.get(dummy, PaintAttachments.LOCK_YAW));
      dummy.m_6210_();
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
