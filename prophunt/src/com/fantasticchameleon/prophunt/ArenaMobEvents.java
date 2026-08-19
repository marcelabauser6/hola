package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Protección estrictamente limitada a participantes y mobs dentro de una arena Prop Hunt activa. */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaMobEvents {
   private ArenaMobEvents() {
   }

   @SubscribeEvent
   public static void onTargetChange(LivingChangeTargetEvent event) {
      if (event.getEntity() instanceof Mob mob
         && event.getNewTarget() instanceof ServerPlayer player
         && protectedTarget(mob, player)) {
         event.setNewTarget(null);
      }
   }

   @SubscribeEvent
   public static void onMobTick(LivingEvent.LivingTickEvent event) {
      if (!(event.getEntity() instanceof Mob mob)) {
         return;
      }

      if (mob.m_5448_() instanceof ServerPlayer player && protectedTarget(mob, player)) {
         clearTarget(mob);
         return;
      }

      Brain<?> brain = mob.m_6274_();
      if (brain.m_21876_(MemoryModuleType.f_26372_, MemoryStatus.REGISTERED)) {
         Object remembered = brain.m_21952_(MemoryModuleType.f_26372_).orElse(null);
         if (remembered instanceof ServerPlayer player && protectedTarget(mob, player)) {
            clearTarget(mob);
         }
      }
   }

   @SubscribeEvent
   public static void onAttack(LivingAttackEvent event) {
      if (blocked(event.getEntity(), event.getSource())) {
         event.setCanceled(true);
      }
   }

   /**
    * Segunda barrera por si algo llega al cálculo de daño sin pasar por el ataque.
    *
    * <p>Cancelar el ataque cubre el golpe directo y el proyectil con dueño, pero hay rutas que aplican
    * daño por otro camino. Con las dos puertas, ningún mob puede herir a un participante dentro de la zona.
    */
   @SubscribeEvent
   public static void onHurt(LivingHurtEvent event) {
      if (blocked(event.getEntity(), event.getSource())) {
         event.setCanceled(true);
      }
   }

   private static boolean blocked(LivingEntity victim, DamageSource source) {
      if (!(victim instanceof ServerPlayer player) || source == null) {
         return false;
      }

      // Se mira tanto el causante como el proyectil: una flecha tiene al esqueleto como dueño, pero
      // conviene comprobar los dos por si alguno llega nulo.
      return source.m_7639_() instanceof Mob shooter && protectedTarget(shooter, player)
         || source.m_7640_() instanceof Mob direct && protectedTarget(direct, player);
   }

   /** Aplica al final las dimensiones autoritativas del mob, despues de escalas y poses humanas. */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onSize(EntityEvent.Size event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }
      EntityPropSnapshot snapshot = Services.PLATFORM.getOrNull(player, PaintAttachments.ENTITY_PROP);
      if (snapshot != null && snapshot.present()) {
         event.setNewSize(EntityDimensions.m_20395_(snapshot.width(), snapshot.height()), true);
         event.setNewEyeHeight(snapshot.eyeHeight());
      }
   }

   private static boolean protectedTarget(Mob mob, ServerPlayer player) {
      Room room = Rooms.roomOf(player);
      if (room == null || !room.protectsFromWildMobs(player.m_20148_())) {
         return false;
      }

      // Basta con que el PROTEGIDO esté dentro de la zona. Antes se exigía que el mob también lo
      // estuviera, así que un esqueleto disparando desde fuera o un creeper al borde seguían haciendo
      // daño: justo lo que se pedía evitar. Si la sala no tiene arena delimitada, la ronda es la zona.
      return !room.hasArenaBounds() || room.containsArena(player);
   }

   private static void clearTarget(Mob mob) {
      mob.m_6710_(null);
      Brain<?> brain = mob.m_6274_();
      if (brain.m_21876_(MemoryModuleType.f_26372_, MemoryStatus.REGISTERED)) {
         brain.m_21936_(MemoryModuleType.f_26372_);
      }
      if (brain.m_21876_(MemoryModuleType.f_26370_, MemoryStatus.REGISTERED)) {
         brain.m_21936_(MemoryModuleType.f_26370_);
      }
   }
}
