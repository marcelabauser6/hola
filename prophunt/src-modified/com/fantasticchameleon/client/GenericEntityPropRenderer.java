package com.fantasticchameleon.client;

import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sustituye visualmente al jugador por el renderer vanilla real de la criatura capturada. */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GenericEntityPropRenderer {
   private static final int CACHE_MAX = 64;
   private static final Map<UUID, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true);
   private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static ClientLevel cachedLevel;

   private GenericEntityPropRenderer() {
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
      if (Boolean.TRUE.equals(RENDERING.get())) {
         return;
      }

      Player player = event.getEntity();
      EntityPropSnapshot snapshot = AvatarState.entityProp(player);
      if (!snapshot.present()) {
         return;
      }

      ClientLevel level = Minecraft.m_91087_().f_91073_;
      if (level == null) {
         return;
      }

      if (cachedLevel != level) {
         CACHE.clear();
         cachedLevel = level;
      }

      Entry entry = CACHE.get(player.m_20148_());
      if (entry == null || !entry.snapshot.equals(snapshot)) {
         entry = create(level, player.m_20148_(), snapshot);
         if (entry == null) {
            // Fallo cerrado: nunca revelar el modelo humano mientras servidor e hitbox siguen siendo
            // una criatura. La siguiente sincronizacion/cambio de snapshot podra reconstruirlo.
            event.setCanceled(true);
            return;
         }
         CACHE.put(player.m_20148_(), entry);
         trim();
      }

      Entity driver = entry.entity;
      if (driver instanceof Player) {
         CACHE.remove(player.m_20148_());
         return;
      }

      float partial = event.getPartialTick();
      float yaw = AvatarState.locked(player)
         ? AvatarState.lockYaw(player)
         : Mth.m_14189_(partial, player.f_20884_, player.f_20883_);
      float headYaw = Mth.m_14189_(partial, player.f_20886_, player.f_20885_);
      float pitch = Mth.m_14179_(partial, player.f_19860_, player.m_146909_());
      driver.m_7678_(player.m_20185_(), player.m_20186_(), player.m_20189_(), yaw, pitch);
      driver.f_19797_ = player.f_19797_;

      if (driver instanceof LivingEntity living) {
         living.f_20884_ = yaw;
         living.f_20883_ = yaw;
         living.f_20886_ = headYaw;
         living.f_20885_ = headYaw;
         if (entry.lastTick != player.f_19797_) {
            living.f_267362_.m_267566_(player.f_267362_.m_267731_(), 1.0F);
            entry.lastTick = player.f_19797_;
         }
      }

      try {
         RENDERING.set(Boolean.TRUE);
         @SuppressWarnings("unchecked")
         EntityRenderer<Entity> renderer = (EntityRenderer<Entity>)Minecraft.m_91087_().m_91290_().m_114382_(driver);
         PoseStack pose = event.getPoseStack();
         renderer.m_7392_(driver, yaw, partial, pose, event.getMultiBufferSource(), event.getPackedLight());
         event.setCanceled(true);
      } catch (RuntimeException ex) {
         CACHE.remove(player.m_20148_());
         event.setCanceled(true);
      } finally {
         RENDERING.set(Boolean.FALSE);
      }
   }

   private static Entry create(ClientLevel level, UUID owner, EntityPropSnapshot snapshot) {
      try {
         EntityType<?> type = BuiltInRegistries.f_256780_.m_7745_(new ResourceLocation(snapshot.typeId()));
         if (type == null || type == EntityType.f_20532_) {
            return null;
         }

         Entity entity = type.m_20615_(level);
         if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            return null;
         }

         entity.m_20258_(snapshot.tagCopy());
         UUID renderId = UUID.nameUUIDFromBytes((owner + "|" + snapshot.typeId()).getBytes(StandardCharsets.UTF_8));
         entity.m_20084_(renderId);
         return new Entry(snapshot, entity);
      } catch (RuntimeException ex) {
         return null;
      }
   }

   private static void trim() {
      while (CACHE.size() > CACHE_MAX) {
         Iterator<UUID> it = CACHE.keySet().iterator();
         it.next();
         it.remove();
      }
   }

   private static final class Entry {
      final EntityPropSnapshot snapshot;
      final Entity entity;
      int lastTick = Integer.MIN_VALUE;

      Entry(EntityPropSnapshot snapshot, Entity entity) {
         this.snapshot = snapshot;
         this.entity = entity;
      }
   }
}
