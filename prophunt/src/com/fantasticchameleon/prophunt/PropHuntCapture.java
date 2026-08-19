package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.game.WorldPick;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.LockTick;
import com.fantasticchameleon.pose.PropShapes;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * <p>Capturar y colocarse son dos cosas distintas a proposito: al tocar el bloque te transformas pero
 * conservas movilidad completa (incluida la escalada), y solo al pulsar la tecla de colocar quedas
 * centrado en la celda, alineado y fijado, como un bloque recien puesto.
 */
public final class PropHuntCapture {
   private static final Map<UUID, Long> LAST_HINT = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> LAST_CAPTURE = new ConcurrentHashMap<>();
   private static final long HINT_COOLDOWN = 80L;
   private static final long CAPTURE_COOLDOWN = 5L;

   private PropHuntCapture() {
   }

   /** Convierte al jugador en el bloque indicado. Devuelve true si la transformacion se aplico. */
   public static boolean captureBlock(ServerPlayer player, BlockState state) {
      if (!ready(player) || !captureAllowed(player)) {
         return false;
      }

      Block block = state.m_60734_();
      BlockPropMapper.Match match = BlockPropMapper.ofBlock(state);
      apply(player, match, String.valueOf(BuiltInRegistries.f_256975_.m_7981_(block)), Block.m_49956_(state));
      player.m_240418_(Component.m_237110_("fantastic.prophunt.became", new Object[]{block.m_49954_()}).m_130940_(ChatFormatting.GREEN), true);
      return true;
   }

   /**
    * Convierte al jugador en cualquier criatura viva no-jugador. El snapshot conserva equipo,
    * edad, variante y datos visuales; el renderer cliente sera el renderer vanilla real del tipo.
    */
   public static boolean captureEntity(ServerPlayer player, Entity target) {
      if (!(target instanceof LivingEntity living) || target instanceof Player || !ready(player) || !captureAllowed(player)) {
         return false;
      }

      EntityPropSnapshot snapshot = snapshot(living);
      if (snapshot == null) {
         hint(player, "fantastic.prophunt.entity_too_large");
         return false;
      }

      FantasticNetwork.applyCapturedEntity(player, snapshot);
      player.m_240418_(Component.m_237110_("fantastic.prophunt.became", new Object[]{target.m_7755_()}).m_130940_(ChatFormatting.GREEN), true);
      return true;
   }

   private static EntityPropSnapshot snapshot(LivingEntity living) {
      String typeId = String.valueOf(BuiltInRegistries.f_256780_.m_7981_(living.m_6095_()));
      if (typeId.isEmpty() || "minecraft:player".equals(typeId)) {
         return null;
      }

      CompoundTag tag = living.m_20240_(new CompoundTag());
      for (String key : new String[]{
         "id", "UUID", "Pos", "Motion", "Rotation", "Passengers", "Leash", "Brain",
         "PortalCooldown", "Dimension", "CustomName", "CustomNameVisible", "HurtTime",
         "DeathTime", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable",
         // Datos privados o de gameplay que no influyen en el renderer. El equipo visible se
         // conserva expresamente en ArmorItems/HandItems.
         "Inventory", "Items", "Offers", "Gossips", "Recipes", "ForgeCaps", "ForgeData",
         "Owner", "OwnerUUID", "Trusted", "LoveCause", "AngryAt", "Target"
      }) {
         tag.m_128473_(key);
      }

      if (encodedSize(tag) > EntityPropSnapshot.MAX_NBT_BYTES) {
         return null;
      }

      return new EntityPropSnapshot(typeId, tag, living.m_20205_(), living.m_20206_(), living.m_20192_());
   }

   private static int encodedSize(CompoundTag tag) {
      try {
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.m_128941_(tag, out);
         }
         return bytes.size();
      } catch (IOException ex) {
         return Integer.MAX_VALUE;
      }
   }

   /**
    * Aplica forma y textura del prop.
    *
    * <p><b>Capturar no te fija.</b> Al tocar un bloque conservas movilidad completa: caminar, saltar y
    * escalar como en el modo clasico (fijarse impide escalar, ver {@code Climb.heldInPlace}). Colocarse
    * es un acto aparte y deliberado, con la tecla de colocar.
    */
   private static void apply(ServerPlayer player, BlockPropMapper.Match match, String sourceBlockId, int stateId) {
      FantasticNetwork.applyCapturedProp(player, match.prop(), match.variant(), sourceBlockId, stateId);
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
      return room != null && room.canUseProp(player.m_20148_());
   }

   private static boolean captureAllowed(ServerPlayer player) {
      long now = player.m_9236_().m_46467_();
      Long last = LAST_CAPTURE.get(player.m_20148_());
      if (last != null && now - last < CAPTURE_COOLDOWN) {
         return false;
      }
      LAST_CAPTURE.put(player.m_20148_(), now);
      return true;
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
