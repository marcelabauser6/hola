package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.game.WorldPick;
import com.fantasticchameleon.game.Perms;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Captura de props en el modo Prop Hunt: convertirse en el bloque o la entidad que tocas.
 *
 * <p>Es la pieza que faltaba en el mod. Ya existia todo el motor de disfraces (formas, hitbox, render,
 * sincronizacion), pero la unica forma de elegir un prop era abrir la rueda y escogerlo a mano. Aqui
 * el mundo es el catalogo: apuntas, click derecho y te convertis en eso.
 *
 * <p>El servidor es autoritativo sobre la forma y la posicion; la textura la sube el cliente
 * ({@link PropHuntClient}), porque los pixeles solo existen ahi.
 */
public final class PropHuntCapture {

   private PropHuntCapture() {
   }

   /** Convierte al jugador en el bloque indicado. Devuelve true si la transformacion se aplico. */
   public static boolean captureBlock(ServerPlayer player, BlockState state) {
      if (!ready(player)) {
         return false;
      }

      BlockPropMapper.Match match = BlockPropMapper.ofBlock(state);
      apply(player, match);
      player.m_240418_(
         Component.m_237110_("fantastic.prophunt.became", new Object[]{state.m_60734_().m_49954_()}).m_130940_(ChatFormatting.GREEN), true
      );
      return true;
   }

   /**
    * Convierte al jugador en la entidad indicada, si el mod tiene una forma para ella.
    *
    * @return true si se aplico, false si la entidad no tiene forma o el jugador no puede transformarse
    */
   public static boolean captureEntity(ServerPlayer player, Entity target) {
      BlockPropMapper.Match match = BlockPropMapper.ofEntity(target);
      if (match == null) {
         if (canTransform(player)) {
            player.m_240418_(
               Component.m_237110_("fantastic.prophunt.no_shape", new Object[]{target.m_7755_()}).m_130940_(ChatFormatting.RED), true
            );
         }

         return false;
      }

      if (!ready(player)) {
         return false;
      }

      apply(player, match);
      player.m_240418_(Component.m_237110_("fantastic.prophunt.became", new Object[]{target.m_7755_()}).m_130940_(ChatFormatting.GREEN), true);
      return true;
   }

   /**
    * Aplica forma y orientacion.
    *
    * <p>Los props de bloque se alinean con los ejes del mundo: la orientacion real ya va dentro del
    * variant (el modelo se construye rotado), asi que el cuerpo del jugador tiene que quedar a yaw 0
    * para que las caras del prop queden paralelas al grid. Los props de criatura si siguen la mirada,
    * porque un animal mirando siempre al norte se ve raro.
    */
   private static void apply(ServerPlayer player, BlockPropMapper.Match match) {
      FantasticNetwork.applyProp(player, match.prop(), match.variant());

      if (!PropShapes.followsLook(match.prop())) {
         Services.PLATFORM.set(player, PaintAttachments.LOCK_YAW, 0.0F);
      }

      // Si ya estaba fijado en el sitio, recolocamos en el momento para que no quede descentrado.
      if (Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.LOCKED))) {
         PropGridSnap.snapToCell(player, Services.PLATFORM.get(player, PaintAttachments.LOCK_YAW));
      }
   }

   /**
    * Comprobaciones de contexto: modo, rol y que no haya una seleccion de mundo en curso.
    *
    * <p>Dentro de una sala manda el modo de la sala, y los seekers no se disfrazan. Fuera de sala se
    * permite al staff, siguiendo la misma regla que ya usa el editor de props ({@code handleSetProp}
    * acepta {@code op || estaEnSala}), para poder probar la transformacion sin montar una partida.
    */
   public static boolean canTransform(ServerPlayer player) {
      if (WorldPick.isPending(player)) {
         return false;
      }

      Room room = Rooms.roomOf(player);
      if (room == null) {
         return Perms.isStaff(player);
      }

      return PropHunt.normalize(room.config().gameMode) == PropHunt.MODE_PROP_HUNT && !room.isSeeker(player.m_20148_());
   }

   /** Como {@link #canTransform} pero avisando al jugador de lo que le falta. */
   private static boolean ready(ServerPlayer player) {
      if (!canTransform(player)) {
         return false;
      }

      if (ChameleonArmor.coverageMask(player) != ChameleonArmor.ALL_MASK) {
         player.m_240418_(Component.m_237115_("fantastic.prop.need_full_set").m_130940_(ChatFormatting.RED), true);
         return false;
      }

      return true;
   }
}
