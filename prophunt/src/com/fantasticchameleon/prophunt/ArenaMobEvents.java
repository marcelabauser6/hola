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
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
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
      if (event.getEntity() instanceof ServerPlayer player
         && event.getSource().m_7639_() instanceof Mob mob
         && protectedTarget(mob, player)) {
         event.setCanceled(true);
      }
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

      // Si la sala tiene arena delimitada, se exige que ambos estén dentro. Si no la tiene, la ronda
      // en curso ES el perímetro: antes se exigía una arena configurada y, al no haberla, la
      // protección no se aplicaba nunca y los mobs seguían atacando.
      return !room.hasArenaBounds() || room.containsArena(player) && room.containsArena(mob);
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
