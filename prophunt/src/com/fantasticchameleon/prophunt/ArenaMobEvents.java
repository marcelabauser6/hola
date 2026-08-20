package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.FantasticChameleon;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Protección estrictamente limitada a participantes y mobs dentro de una arena Prop Hunt activa. */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaMobEvents {
   private static boolean barrierFailureLogged;

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
      if (!(event.getEntity() instanceof Mob mob) || mob.m_9236_().f_46443_) {
         return;
      }

      // La barrera corre dentro del tick de entidades: un fallo aquí reventaría el tick del mundo
      // entero, así que se aísla y como mucho deja de expulsar a ese mob.
      try {
         Room barrier = Rooms.blockingArena(mob);
         if (barrier != null) {
            eject(mob, barrier.arenaBounds());
            return;
         }
      } catch (RuntimeException | LinkageError ex) {
         if (!barrierFailureLogged) {
            barrierFailureLogged = true;
            FantasticChameleon.LOGGER.error("[Prop Hunt] fallo en la barrera de arena", ex);
         }
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

   @SubscribeEvent
   public static void onDamage(LivingDamageEvent event) {
      if (blocked(event.getEntity(), event.getSource())) {
         event.setCanceled(true);
      }
   }

   private static boolean blocked(LivingEntity victim, DamageSource source) {
      if (!(victim instanceof ServerPlayer player) || source == null) {
         return false;
      }

      Mob causing = mobOwner(source.m_7639_());
      Mob direct = mobOwner(source.m_7640_());
      return causing != null && protectedTarget(causing, player)
         || direct != null && protectedTarget(direct, player);
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

   private static Mob mobOwner(Entity entity) {
      if (entity instanceof Mob mob) {
         return mob;
      }
      if (entity instanceof Projectile projectile && projectile.m_19749_() instanceof Mob owner) {
         return owner;
      }
      return null;
   }

   /**
    * Devuelve inmediatamente el mob al exterior por la cara horizontal más cercana. Su caja completa
    * queda fuera, se cancela la ruta y se limpia el objetivo; no puede seguir empujando contra el borde
    * ni completar un ataque iniciado dentro.
    */
   private static void eject(Mob mob, AABB arena) {
      AABB box = mob.m_20191_();
      double halfX = Math.max(0.01, (box.f_82291_ - box.f_82288_) * 0.5);
      double halfZ = Math.max(0.01, (box.f_82293_ - box.f_82290_) * 0.5);
      double x = mob.m_20185_();
      double z = mob.m_20189_();
      double left = arena.f_82288_ - halfX - 0.05;
      double right = arena.f_82291_ + halfX + 0.05;
      double north = arena.f_82290_ - halfZ - 0.05;
      double south = arena.f_82293_ + halfZ + 0.05;
      double dl = Math.abs(x - left);
      double dr = Math.abs(right - x);
      double dn = Math.abs(z - north);
      double ds = Math.abs(south - z);
      double best = Math.min(Math.min(dl, dr), Math.min(dn, ds));
      if (best == dl) x = left;
      else if (best == dr) x = right;
      else if (best == dn) z = north;
      else z = south;
      mob.m_6021_(x, mob.m_20186_(), z);
      mob.m_20256_(Vec3.f_82478_);
      mob.m_21573_().m_26573_();
      clearTarget(mob);
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
