package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.network.PropActPayload;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
   private static final long COOLDOWN = 12L;

   private PropHuntActs() {
   }

   public static void handle(PropActPayload payload, ServerPlayer player) {
      act(player);
   }

   /** Ejecuta el gesto del prop que lleva puesto. */
   public static void act(ServerPlayer player) {
      Integer prop = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
      if (prop == null || prop < 0) {
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
