package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import net.minecraft.server.level.ServerPlayer;

/**
 * Modos de juego de Fantastic Chameleon.
 *
 * <p>El mod nacio como un juego de camuflaje por pintura (Meccha Chameleon): el jugador se pinta el
 * cuerpo para imitar el entorno. Este helper separa ese modo clasico del nuevo modo Prop Hunt, donde
 * el jugador se convierte directamente en el bloque o la entidad que toca con click derecho.
 *
 * <p>El modo se guarda por sala en {@link Room.Config#gameMode} y viaja al cliente en el array de
 * config de {@code RoomsPayload} en la posicion {@link #CFG_INDEX}.
 */
public final class PropHunt {
   /** Modo clasico: pintura corporal, sin transformacion en bloques. */
   public static final int MODE_MECCHA = 0;
   /** Modo Prop Hunt: transformacion en el bloque/entidad tocado, sin GUI de pintura. */
   public static final int MODE_PROP_HUNT = 1;

   /** Posicion del modo dentro del array de config que se sincroniza al cliente. */
   public static final int CFG_INDEX = 21;

   private PropHunt() {
   }

   /** Modo de la sala del jugador, o {@link #MODE_MECCHA} si no esta en ninguna sala. */
   public static int modeOf(ServerPlayer player) {
      Room room = Rooms.roomOf(player);
      return room == null ? MODE_MECCHA : normalize(room.config().gameMode);
   }

   public static int normalize(int mode) {
      return mode == MODE_PROP_HUNT ? MODE_PROP_HUNT : MODE_MECCHA;
   }

   /** True si el jugador esta en una sala configurada como Prop Hunt. */
   public static boolean isPropHunt(ServerPlayer player) {
      return modeOf(player) == MODE_PROP_HUNT;
   }

   /**
    * True si el jugador esta jugando el modo clasico dentro de una sala. Los jugadores que no estan
    * en ninguna sala no cuentan como Meccha para no bloquear las herramientas de staff.
    */
   public static boolean isMecchaRoom(ServerPlayer player) {
      Room room = Rooms.roomOf(player);
      return room != null && normalize(room.config().gameMode) == MODE_MECCHA;
   }

   /** True si el jugador esta en una sala (de cualquier modo). */
   public static boolean inRoom(ServerPlayer player) {
      return Rooms.roomOf(player) != null;
   }

   public static String nameKey(int mode) {
      return normalize(mode) == MODE_PROP_HUNT ? "fantastic.gamemode.prophunt" : "fantastic.gamemode.meccha";
   }
}
