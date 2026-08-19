package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.network.PropActPayload;
import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Gestos de criatura para el modo Prop Hunt.
 *
 * <p>Un prop quieto se delata: los animales de verdad balan, pastan y se mueven. Esto le da al jugador
 * disfrazado de criatura los gestos de su bicho para poder imitar su comportamiento a voluntad, en vez
 * de depender de una IA automatica que le quitaria el control.
 *
 * <p>Se hace con sonido y particulas en lugar de animacion de modelo porque el prop se dibuja como un
 * conjunto de cajas estaticas: cambiar eso obligaria a tocar el renderer del mod, que es justo lo que
 * no queremos romper. El resultado que percibe el que te busca es el mismo: oye la oveja y ve la hierba
 * saltar donde pastaste.
 */
public final class PropHuntActs {
   private static final Map<UUID, Long> LAST_ACT = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> NEXT_AMBIENT = new ConcurrentHashMap<>();
   private static final Map<String, Optional<SoundEvent>> CAPTURED_VOICE = new ConcurrentHashMap<>();
   private static final long COOLDOWN = 12L;

   private PropHuntActs() {
   }

   public static void handle(PropActPayload payload, ServerPlayer player) {
      act(player);
   }

   /**
    * Sonido ambiental automatico de las criaturas.
    *
    * <p>Un animal que nunca suena se delata igual que uno que no se mueve: las criaturas de verdad
    * balan y gruñen cada tanto por su cuenta. Esto lo hace por el jugador, sin quitarle el control, y a
    * intervalos irregulares para que no parezca un metronomo.
    */
   public static void ambientTick(ServerPlayer player) {
      Room room = Rooms.roomOf(player);
      if (room == null || !room.canUseProp(player.m_20148_())) {
         NEXT_AMBIENT.remove(player.m_20148_());
         return;
      }

      SoundEvent sound = voiceOf(player);
      if (sound == null) {
         return;
      }

      ServerLevel level = player.m_284548_();
      Long next = NEXT_AMBIENT.get(player.m_20148_());
      long now = level.m_46467_();
      if (next == null) {
         NEXT_AMBIENT.put(player.m_20148_(), now + schedule(level));
         return;
      }

      if (now >= next) {
         NEXT_AMBIENT.put(player.m_20148_(), now + schedule(level));
         play(level, player, sound, 0.9F, 0.95F + level.m_213780_().m_188501_() * 0.1F);
      }
   }

   /** Proximo sonido entre 6 y 18 segundos, como el ritmo ambiental de los mobs vanilla. */
   private static long schedule(ServerLevel level) {
      return 120L + (long)level.m_213780_().m_188503_(240);
   }

   /**
    * Voz del disfraz actual.
    *
    * <p>La captura genérica de criaturas guarda el bicho en {@code ENTITY_PROP} y deja {@code PROP} en
    * -1, así que exigir {@code PROP >= 0} dejaba mudo a todo mob capturado. Aquí el sonido se deriva del
    * propio {@code EntityType}, de modo que también suenan los mobs que no están en el catálogo, incluidos
    * los de otros mods.
    */
   private static SoundEvent voiceOf(ServerPlayer player) {
      Integer prop = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
      if (prop != null && prop >= 0) {
         return PropShapes.followsLook(prop) ? ambientSound(PropShapes.of(prop).key()) : null;
      }

      EntityPropSnapshot snapshot = Services.PLATFORM.getOrNull(player, PaintAttachments.ENTITY_PROP);
      return snapshot != null && snapshot.present() ? capturedSound(snapshot.typeId()) : null;
   }

   /** Resuelve y memoriza el sonido ambiental del tipo capturado por convención de registro. */
   private static SoundEvent capturedSound(String typeId) {
      return CAPTURED_VOICE.computeIfAbsent(typeId, PropHuntActs::resolveVoice).orElse(null);
   }

   private static Optional<SoundEvent> resolveVoice(String typeId) {
      try {
         ResourceLocation type = new ResourceLocation(typeId);
         // Sin ".flop": ese es el sonido de un pez fuera del agua, no su ruido normal.
         for (String suffix : new String[]{".ambient", ".idle", ".say", ".ambient.land", ".growl", ".breathe"}) {
            SoundEvent found = BuiltInRegistries.f_256894_.m_7745_(new ResourceLocation(type.m_135827_(), "entity." + type.m_135815_() + suffix));
            if (found != null) {
               return Optional.of(found);
            }
         }
      } catch (RuntimeException ignored) {
         // Un id inválido simplemente no tiene voz; no debe romper el tick del jugador.
      }

      return Optional.empty();
   }

   private static SoundEvent ambientSound(String key) {
      return switch (key) {
         case "sheep" -> SoundEvents.f_12341_;
         case "cow" -> SoundEvents.f_11830_;
         case "pig" -> SoundEvents.f_12233_;
         case "chicken" -> SoundEvents.f_11750_;
         case "wolf" -> SoundEvents.f_12617_;
         case "panda" -> SoundEvents.f_12179_;
         case "enderman" -> SoundEvents.f_11899_;
         default -> null;
      };
   }

   /** Ejecuta el gesto del prop que lleva puesto. */
   public static void act(ServerPlayer player) {
      Room room = Rooms.roomOf(player);
      if (room == null || !room.canUseProp(player.m_20148_())) {
         return;
      }

      Integer prop = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
      if (prop == null || prop < 0) {
         actCaptured(player);
         return;
      }

      if (throttled(player)) {
         return;
      }

      String key = PropShapes.of(prop).key();
      ServerLevel level = player.m_284548_();

      switch (key) {
         case "sheep":
            // Pastar: el sonido de la oveja y la hierba saltando a sus pies.
            play(level, player, SoundEvents.f_12341_, 1.0F, 1.0F);
            graze(level, player);
            break;
         case "cow":
            play(level, player, SoundEvents.f_11830_, 1.0F, 1.0F);
            graze(level, player);
            break;
         case "pig":
            play(level, player, SoundEvents.f_12233_, 1.0F, 1.0F);
            graze(level, player);
            break;
         case "chicken":
            play(level, player, SoundEvents.f_11750_, 1.0F, 1.0F);
            puff(level, player, ParticleTypes.f_123796_, 6);
            break;
         case "wolf":
            play(level, player, SoundEvents.f_12617_, 1.0F, 1.0F);
            puff(level, player, ParticleTypes.f_123748_, 4);
            break;
         case "panda":
            play(level, player, SoundEvents.f_12179_, 1.0F, 1.0F);
            graze(level, player);
            break;
         case "creeper":
            // Sisear: el aviso clasico, sin explotar nada.
            play(level, player, SoundEvents.f_11837_, 0.8F, 1.0F);
            puff(level, player, ParticleTypes.f_123762_, 8);
            break;
         case "enderman":
            play(level, player, SoundEvents.f_11899_, 1.0F, 1.0F);
            puff(level, player, ParticleTypes.f_123760_, 16);
            break;
         default:
            // Los props de bloque no tienen gesto: un bloque que hace ruido te delata.
            player.m_240418_(Component.m_237115_("fantastic.prophunt.no_act").m_130940_(ChatFormatting.GRAY), true);
            return;
      }

      Services.PLATFORM.set(player, PaintAttachments.PROP_ACT_TICK, level.m_46467_());
      LAST_ACT.put(player.m_20148_(), level.m_46467_());
   }

   /** Gesto de una criatura capturada con el sistema genérico, que no tiene clave de catálogo. */
   private static void actCaptured(ServerPlayer player) {
      EntityPropSnapshot snapshot = Services.PLATFORM.getOrNull(player, PaintAttachments.ENTITY_PROP);
      if (snapshot == null || !snapshot.present() || throttled(player)) {
         return;
      }

      ServerLevel level = player.m_284548_();
      SoundEvent sound = capturedSound(snapshot.typeId());
      if (sound != null) {
         play(level, player, sound, 1.0F, 1.0F);
      }

      // Partículas neutras: pastar solo tiene sentido en un animal de pasto, y esta ruta cubre
      // cualquier criatura, incluidos murciélagos, ghasts o mobs de otros mods.
      puff(level, player, ParticleTypes.f_123796_, 4);
      Services.PLATFORM.set(player, PaintAttachments.PROP_ACT_TICK, level.m_46467_());
      LAST_ACT.put(player.m_20148_(), level.m_46467_());
   }

   /** Comer del suelo: particulas de hierba justo bajo el prop. */
   private static void graze(ServerLevel level, ServerPlayer player) {
      level.m_8767_(ParticleTypes.f_123748_, player.m_20185_(), player.m_20186_() + 0.1, player.m_20189_(), 8, 0.25, 0.05, 0.25, 0.0);
      level.m_8767_(ParticleTypes.f_123749_, player.m_20185_(), player.m_20186_() + 0.1, player.m_20189_(), 6, 0.3, 0.05, 0.3, 0.0);
   }

   private static void puff(ServerLevel level, ServerPlayer player, ParticleOptions particle, int count) {
      level.m_8767_(particle, player.m_20185_(), player.m_20186_() + 0.6, player.m_20189_(), count, 0.25, 0.25, 0.25, 0.02);
   }

   private static void play(ServerLevel level, ServerPlayer player, SoundEvent sound, float volume, float pitch) {
      level.m_6263_(null, player.m_20185_(), player.m_20186_(), player.m_20189_(), sound, SoundSource.NEUTRAL, volume, pitch);
   }

   /** Freno para que no se pueda spamear el sonido y quede ridiculo. */
   private static boolean throttled(ServerPlayer player) {
      Long last = LAST_ACT.get(player.m_20148_());
      return last != null && player.m_9236_().m_46467_() - last < COOLDOWN;
   }
}
