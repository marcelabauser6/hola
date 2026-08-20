package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.network.PropActPayload;
import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.paint.PropMotionState;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.SoundType;

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
   private static final Map<String, Optional<SoundEvent>> STEP_VOICE = new ConcurrentHashMap<>();
   private static final Map<UUID, Motion> MOTION = new ConcurrentHashMap<>();
   /**
    * Distancia entre pasos, la misma que usa el juego.
    *
    * <p>Vanilla no compara la distancia con 0,6: acumula {@code distancia × 0,6} y emite el paso al
    * llegar a 1, es decir un paso cada {@code 1 / 0,6} bloques. Tomar el 0,6 como umbral en bloques
    * disparaba los pasos casi tres veces más a menudo de lo normal, a cualquier velocidad.
    */
   private static final double STEP_DISTANCE = 1.0 / 0.6;
   private static final double TELEPORT_DISTANCE = 1.5;
   /** Correcciones de posición del servidor por debajo de esto no son caminar. */
   private static final double MOTION_EPSILON = 0.002;
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

   /** Cobertura de sonido de un tipo, para poder auditarla sin adivinar. */
   public static boolean hasVoice(String typeId) {
      return capturedSound(typeId) != null;
   }

   /** True si el tipo tiene sonido propio de movimiento; si no, se usa el del bloque pisado. */
   public static boolean hasStepSound(String typeId) {
      return stepSound(typeId) != null;
   }

   /** Resuelve y memoriza el sonido ambiental del tipo capturado por convención de registro. */
   private static SoundEvent capturedSound(String typeId) {
      return CAPTURED_VOICE.computeIfAbsent(typeId, PropHuntActs::resolveVoice).orElse(null);
   }

   /**
    * Voz preferida por convención de nombres, en orden de "qué suena cuando el bicho está tranquilo".
    *
    * <p>No todos los mobs tienen {@code .ambient}: un slime hace {@code .squish}, un ghast
    * {@code .moan}, un guardián {@code .attack}. Por eso la lista es larga y, si aun así no encaja
    * ninguna, se busca en el registro cualquier sonido del tipo que no sea de daño, muerte o pasos.
    */
   private static final String[] VOICE_SUFFIXES = {
      ".ambient", ".idle", ".say", ".ambient.land", ".growl", ".breathe", ".moan", ".squish", ".purr",
      ".chirp", ".hiss", ".roar", ".whine", ".pant", ".sniff", ".scream", ".celebrate", ".jump", ".flop"
   };

   /** Nunca se usan como voz de reposo: delatarían al jugador con un sonido que no toca. */
   private static final String[] VOICE_EXCLUDED = {
      ".hurt", ".death", ".step", ".eat", ".drink", ".explode", ".shoot", ".throw", ".splash", ".swim",
      ".land", ".break", ".place", ".shear", ".milk", ".plop", ".equip", ".unequip", ".saddle", ".armor",
      ".converted", ".cure", ".infect", ".teleport", ".shake", ".dash", ".spit", ".sneeze", ".fall",
      ".attack", ".charge", ".flap", ".retreat", ".warning", ".disappeared", ".reappeared", ".trade"
   };

   private static Optional<SoundEvent> resolveVoice(String typeId) {
      try {
         ResourceLocation type = new ResourceLocation(typeId);
         for (String path : candidates(type.m_135815_())) {
            String prefix = "entity." + path;
            for (String suffix : VOICE_SUFFIXES) {
               SoundEvent found = BuiltInRegistries.f_256894_.m_7745_(new ResourceLocation(type.m_135827_(), prefix + suffix));
               if (found != null) {
                  return Optional.of(found);
               }
            }

            // Cualquier sonido registrado para ese nombre que no esté vetado. Así no se queda mudo un
            // mob cuyo sonido no siga ninguna convención de nombres.
            ResourceLocation fallback = null;
            for (ResourceLocation id : BuiltInRegistries.f_256894_.m_6566_()) {
               if (id.m_135827_().equals(type.m_135827_()) && id.m_135815_().startsWith(prefix + ".") && !excluded(id.m_135815_())) {
                  if (fallback == null || id.m_135815_().length() < fallback.m_135815_().length()) {
                     fallback = id;
                  }
               }
            }

            if (fallback != null) {
               return Optional.ofNullable(BuiltInRegistries.f_256894_.m_7745_(fallback));
            }
         }
      } catch (RuntimeException ignored) {
         // Un id inválido simplemente no tiene voz; no debe romper el tick del jugador.
      }

      return Optional.empty();
   }

   /**
    * Nombres bajo los que buscar los sonidos de un tipo.
    *
    * <p>Varios mobs vanilla no tienen sonidos propios y reutilizan los de su pariente: la araña de cueva
    * usa los de la araña y la llama mercante los de la llama. Y algún id no coincide con el nombre de sus
    * sonidos: la entidad es {@code pufferfish} pero sus sonidos son {@code entity.puffer_fish.*}. Sin
    * esto, esos mobs se quedaban mudos aunque en el juego real sí suenan.
    */
   private static String[] candidates(String path) {
      String related = path.indexOf('_') > 0 ? path.substring(path.indexOf('_') + 1) : path;
      return switch (path) {
         case "pufferfish" -> new String[]{path, "puffer_fish"};
         case "giant" -> new String[]{path, "zombie"};
         case "snow_golem" -> new String[]{path, "snowman"};
         case "mooshroom" -> new String[]{path, "cow"};
         default -> path.equals(related) ? new String[]{path} : new String[]{path, related};
      };
   }

   private static boolean excluded(String path) {
      for (String bad : VOICE_EXCLUDED) {
         if (path.endsWith(bad)) {
            return true;
         }
      }

      return false;
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

   /**
    * Pasos de la criatura imitada.
    *
    * <p>El sonido de pasos de un mob lo emite el propio mob al desplazarse, y el disfraz es un modelo de
    * adorno que no se mueve por el mundo: por eso una araña o un zombi disfrazados andaban en silencio y
    * se notaba el engaño. Aquí lo emite el servidor, con la misma cadencia que vanilla y el sonido propio
    * del tipo capturado ({@code entity.<tipo>.step}); si ese tipo no tiene uno, se usa el del bloque que
    * se pisa, que es exactamente lo que hacen los mobs sin sonido propio.
    */
   /**
    * Actualiza una sola vez por tick la marcha autoritativa de la criatura imitada.
    *
    * <p>La distancia de aquí alimenta simultáneamente la velocidad que reciben los renderers y el
    * umbral de sonido. No existe ya un segundo acumulador cliente ni un muestreo de un segundo que
    * pueda dejar pasos en cola después de detenerse.
    */
   public static void motionTick(ServerPlayer player) {
      EntityPropSnapshot snapshot = Services.PLATFORM.getOrNull(player, PaintAttachments.ENTITY_PROP);
      Room room = Rooms.roomOf(player);
      // Un prop fijado no camina. Estando anclado el servidor lo devuelve a su ancla en cuanto se
      // desvía lo más mínimo, y ese reposicionamiento se medía como distancia recorrida: por eso las
      // patas andaban y sonaban pasos con el jugador completamente quieto.
      boolean anchored = Services.PLATFORM.get(player, PaintAttachments.LOCKED)
         || Services.PLATFORM.get(player, PaintAttachments.POSING);
      if (snapshot == null || !snapshot.present() || player.m_5833_() || anchored || room == null || !room.canUseProp(player.m_20148_())) {
         Motion removed = MOTION.remove(player.m_20148_());
         PropMotionState current = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP_MOTION);
         if (removed != null || current != null && current.speed() != 0.0F) {
            Services.PLATFORM.set(player, PaintAttachments.PROP_MOTION, PropMotionState.IDLE);
         }
         return;
      }

      Motion state = MOTION.get(player.m_20148_());
      int signature = snapshot.hashCode();
      if (state == null || state.snapshotSignature != signature) {
         state = new Motion(signature, player.m_20185_(), player.m_20189_());
         MOTION.put(player.m_20148_(), state);
         Services.PLATFORM.set(player, PaintAttachments.PROP_MOTION, PropMotionState.IDLE);
         return;
      }

      double dx = player.m_20185_() - state.x;
      double dz = player.m_20189_() - state.z;
      state.x = player.m_20185_();
      state.z = player.m_20189_();
      double distance = Math.sqrt(dx * dx + dz * dz);
      if (!Double.isFinite(distance) || distance > TELEPORT_DISTANCE || distance < MOTION_EPSILON) {
         if (!Double.isFinite(distance) || distance > TELEPORT_DISTANCE) {
            state.stepDistance = 0.0;
         }
         distance = 0.0;
      }

      // Exactamente la fórmula de vanilla sobre la distancia realmente recorrida: las patas van al
      // ritmo al que se mueve el jugador. Acotarlas al paso natural del animal las dejaba lentas y
      // deslizando, porque un disfraz se mueve mucho más rápido que la criatura suelta.
      float walk = (float)Math.min(1.0, distance * 4.0);
      boolean starting = walk > 0.0F && state.lastSpeed == 0.0F;
      boolean stopping = walk == 0.0F && state.lastSpeed > 0.0F;
      state.ticksSinceSync++;
      // Inicio y parada se publican en el mismo tick; durante marcha bastan 10 Hz porque el renderer
      // conserva la velocidad entre muestras. Así se evita una difusión cuadrática a 20 Hz.
      if (starting || stopping || walk > 0.0F && state.ticksSinceSync >= 2) {
         Services.PLATFORM.set(player, PaintAttachments.PROP_MOTION, new PropMotionState(walk));
         state.ticksSinceSync = 0;
      }
      state.lastSpeed = walk;

      if (!player.m_20096_() || distance <= 0.0) {
         return;
      }

      state.stepDistance += distance;
      int emitted = 0;
      while (state.stepDistance >= STEP_DISTANCE && emitted++ < 2) {
         state.stepDistance -= STEP_DISTANCE;
         playStep(player, snapshot);
      }
   }

   private static void playStep(ServerPlayer player, EntityPropSnapshot snapshot) {
      ServerLevel level = player.m_284548_();
      BlockPos below = player.m_20183_().m_7495_();
      SoundType ground = level.m_8055_(below).m_60827_();
      SoundEvent step = stepSound(snapshot.typeId());
      if (step != null) {
         play(level, player, step, 0.15F, 1.0F);
      } else if (!level.m_8055_(below).m_60795_()) {
         play(level, player, ground.m_56776_(), ground.m_56773_() * 0.15F, ground.m_56774_());
      }
   }

   /** Sonido de paso del tipo capturado, por convención de registro, memorizado por tipo. */
   private static SoundEvent stepSound(String typeId) {
      return STEP_VOICE.computeIfAbsent(typeId, id -> {
         try {
            ResourceLocation type = new ResourceLocation(id);
            // Un slime no tiene ".step" pero sí ".squish", y un mob que salta usa ".jump": son sus
            // sonidos de movimiento reales. Si no hay ninguno, el sonido lo pone el bloque pisado.
            for (String path : candidates(type.m_135815_())) {
               for (String suffix : new String[]{".step", ".squish", ".jump"}) {
                  SoundEvent found = BuiltInRegistries.f_256894_.m_7745_(new ResourceLocation(type.m_135827_(), "entity." + path + suffix));
                  if (found != null) {
                     return Optional.of(found);
                  }
               }
            }
         } catch (RuntimeException ignored) {
            // Sin sonido propio: se cae al del bloque.
         }

         return Optional.<SoundEvent>empty();
      }).orElse(null);
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

   /** Elimina todo estado por jugador que ya no está conectado; se ejecuta con el refresco de sala. */
   public static void sweep(Iterable<ServerPlayer> players) {
      Set<UUID> online = new HashSet<>();
      for (ServerPlayer player : players) {
         online.add(player.m_20148_());
      }
      MOTION.keySet().removeIf(id -> !online.contains(id));
      LAST_ACT.keySet().removeIf(id -> !online.contains(id));
      NEXT_AMBIENT.keySet().removeIf(id -> !online.contains(id));
   }

   private static final class Motion {
      final int snapshotSignature;
      double x;
      double z;
      double stepDistance;
      float lastSpeed;
      int ticksSinceSync;

      Motion(int snapshotSignature, double x, double z) {
         this.snapshotSignature = snapshotSignature;
         this.x = x;
         this.z = z;
      }
   }
}
