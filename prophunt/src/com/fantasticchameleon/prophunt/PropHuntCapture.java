package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.game.WorldPick;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.LockTick;
import com.fantasticchameleon.pose.PropShapes;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Captura de props en el modo Prop Hunt: convertirse en el bloque o la entidad que tocas.
 *
 * <p>Es la pieza que faltaba en el mod. Ya existia todo el motor de disfraces (formas, hitbox, render,
 * sincronizacion), pero la unica forma de elegir un prop era abrir la rueda y escogerlo a mano. Aqui
 * el mundo es el catalogo: apuntas, click derecho y te convertis en eso.
 *
 * <p>Al capturar un bloque el jugador queda <b>colocado</b>: centrado en su celda, alineado con los
 * ejes y fijado en el sitio, igual que si acabaran de poner ese bloque ahi. Con espacio o agachandose
 * se suelta para volver a moverse (y para poder escalar, que estando fijado no se puede).
 */
public final class PropHuntCapture {
   private static final Map<UUID, Long> LAST_HINT = new ConcurrentHashMap<>();
   private static final long HINT_COOLDOWN = 80L;

   private PropHuntCapture() {
   }

   /** Convierte al jugador en el bloque indicado. Devuelve true si la transformacion se aplico. */
   public static boolean captureBlock(ServerPlayer player, BlockState state) {
      if (!ready(player)) {
         return false;
      }

      Block block = state.m_60734_();
      BlockPropMapper.Match match = BlockPropMapper.ofBlock(state);
      apply(player, match, String.valueOf(BuiltInRegistries.f_256975_.m_7981_(block)));
      player.m_240418_(Component.m_237110_("fantastic.prophunt.became", new Object[]{block.m_49954_()}).m_130940_(ChatFormatting.GREEN), true);
      return true;
   }

   /**
    * Convierte al jugador en la entidad indicada, si el mod tiene una forma para ella.
    *
    * <p>Para las criaturas no se guarda bloque de origen: su textura la sube el cliente, que es quien
    * puede leer el png del mob.
    */
   public static boolean captureEntity(ServerPlayer player, Entity target) {
      BlockPropMapper.Match match = BlockPropMapper.ofEntity(target);
      if (match == null) {
         return false;
      }

      if (!ready(player)) {
         return false;
      }

      apply(player, match, "");
      player.m_240418_(Component.m_237110_("fantastic.prophunt.became", new Object[]{target.m_7755_()}).m_130940_(ChatFormatting.GREEN), true);
      return true;
   }

   /**
    * Aplica forma, textura y colocacion.
    *
    * <p>Los props de bloque se alinean con los ejes del mundo: la orientacion real ya va dentro del
    * variant (el modelo se construye rotado), asi que el cuerpo del jugador tiene que quedar a yaw 0
    * para que las caras queden paralelas al grid. Los props de criatura si siguen la mirada, porque un
    * animal mirando siempre al norte se ve raro.
    *
    * <p>Se fija al jugador (LOCKED) porque el render solo respeta el yaw alineado cuando esta fijado;
    * sin eso el bloque giraria con el cuerpo y quedaria torcido.
    */
   private static void apply(ServerPlayer player, BlockPropMapper.Match match, String sourceBlockId) {
      FantasticNetwork.applyProp(player, match.prop(), match.variant());
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, sourceBlockId);

      boolean creature = PropShapes.followsLook(match.prop());
      float yaw = creature ? Services.PLATFORM.get(player, PaintAttachments.LOCK_YAW) : 0.0F;
      Services.PLATFORM.set(player, PaintAttachments.LOCK_YAW, PropGridSnap.snapYaw(yaw));
      Services.PLATFORM.set(player, PaintAttachments.POSING, true);
      Services.PLATFORM.set(player, PaintAttachments.LOCKED, true);

      Vec3 target = PropGridSnap.cellCenter(player);
      if (PropGridSnap.snapToCell(player, Services.PLATFORM.get(player, PaintAttachments.LOCK_YAW))) {
         LockTick.reanchor(player, target);
      } else {
         LockTick.reanchor(player, player.m_20182_());
      }

      player.m_6210_();
   }

   /**
    * Comprobaciones de contexto: modo, rol y que no haya una seleccion de mundo en curso.
    *
    * <p>Se exige estar en una sala en modo Prop Hunt. Antes se permitia al staff transformarse fuera
    * de una sala, pero eso creaba dos verdades distintas sobre "estoy en Prop Hunt": la captura decia
    * si y el centrado y el gateo de las GUIs decian no, con lo que el disfraz salia descolocado y con
    * los menus del modo clasico encima.
    */
   public static boolean canTransform(ServerPlayer player) {
      if (WorldPick.isPending(player)) {
         return false;
      }

      Room room = Rooms.roomOf(player);
      return room != null
         && PropHunt.normalize(room.config().gameMode) == PropHunt.MODE_PROP_HUNT
         && !room.isSeeker(player.m_20148_());
   }

   /** Como {@link #canTransform} pero avisando al jugador de lo que le falta. */
   private static boolean ready(ServerPlayer player) {
      if (!canTransform(player)) {
         hint(player, "fantastic.prophunt.needs_room");
         return false;
      }

      if (ChameleonArmor.coverageMask(player) != ChameleonArmor.ALL_MASK) {
         hint(player, "fantastic.prop.need_full_set");
         return false;
      }

      return true;
   }

   /**
    * Aviso espaciado. El click derecho se repite mucho, asi que sin freno esto llenaria la pantalla,
    * pero fallar en silencio es peor: era imposible saber por que no pasaba nada.
    */
   private static void hint(ServerPlayer player, String key) {
      if (ChameleonArmor.coverageMask(player) == 0) {
         return;
      }

      long now = player.m_9236_().m_46467_();
      Long last = LAST_HINT.get(player.m_20148_());
      if (last == null || now - last > HINT_COOLDOWN) {
         LAST_HINT.put(player.m_20148_(), now);
         player.m_240418_(Component.m_237115_(key).m_130940_(ChatFormatting.RED), true);
      }
   }
}
