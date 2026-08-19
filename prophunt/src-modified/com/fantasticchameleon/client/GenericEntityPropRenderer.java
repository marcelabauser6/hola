package com.fantasticchameleon.client;

import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sustituye visualmente al jugador por el renderer vanilla real de la criatura capturada. */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GenericEntityPropRenderer {
   private static final int CACHE_MAX = 64;
   private static final Map<UUID, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true);
   private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static final int MAX_CREATIONS_PER_TICK = 2;
   private static long creationTick = Long.MIN_VALUE;
   private static int creationsThisTick;
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
         // Al empezar la ronda todos los escondidos reciben su disfraz en el mismo tick, y construir
         // una entidad desde su NBT no es gratis. Se limitan a dos por tick: el resto entra en los
         // siguientes frames, que es imperceptible, en vez de provocar un tirón al arrancar la partida.
         if (!allowCreation()) {
            event.setCanceled(true);
            return;
         }

         entry = create(level, player.m_20148_(), snapshot);
         if (entry == null) {
            // Fallo cerrado: nunca revelar el modelo humano mientras servidor e hitbox siguen siendo
            // una criatura. La siguiente sincronizacion/cambio de snapshot podra reconstruirlo.
            event.setCanceled(true);
            return;
         }
         entry.prevX = player.m_20185_();
         entry.prevZ = player.m_20189_();
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
      // setPos y no moveTo: moveTo reajusta la posición anterior del modelo y con ello se perdía el
      // desplazamiento por tick del que sale la animación de las patas.
      driver.m_20343_(player.m_20185_(), player.m_20186_(), player.m_20189_());
      driver.m_146922_(yaw);
      driver.m_146926_(pitch);
      driver.f_19859_ = yaw;
      driver.f_19860_ = pitch;
      driver.f_19797_ = player.f_19797_;

      if (driver instanceof LivingEntity living) {
         living.f_20884_ = yaw;
         living.f_20883_ = yaw;
         living.f_20886_ = headYaw;
         living.f_20885_ = headYaw;
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

   /**
    * Ticka los modelos una vez por tick de juego, desde el tick del cliente y no desde el render.
    *
    * <p>Sin esto el modelo estaba congelado: la gallina no batía las alas ni el lobo movía la cola, y
    * se notaba a la legua que era un muñeco. Hacerlo en el render era peor de lo que parece: el evento
    * de render no se dispara en primera persona ni con el prop fuera de pantalla, así que justamente el
    * disfrazado nunca veía animarse su propio disfraz y la animación daba un salto al volver a verlo.
    */
   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END || CACHE.isEmpty()) {
         return;
      }

      ClientLevel level = Minecraft.m_91087_().f_91073_;
      if (level == null) {
         CACHE.clear();
         return;
      }

      for (Map.Entry<UUID, Entry> cached : new ArrayList<>(CACHE.entrySet())) {
         Entry entry = cached.getValue();
         Player owner = level.m_46003_(cached.getKey());
         // Si el jugador ya no lleva esa criatura, su modelo deja de existir aquí mismo. Sin esto, el
         // modelo viejo seguía ticando para siempre: al pasar de slime a otra cosa, el slime invisible
         // continuaba soltando sus partículas a los pies del jugador.
         if (owner == null || !AvatarState.entityProp(owner).equals(entry.snapshot)) {
            CACHE.remove(cached.getKey());
         } else if (entry.tickable && entry.entity instanceof LivingEntity living) {
            // Desplazamiento real del jugador en este tick, con la misma fórmula que usa el juego.
            double dx = owner.m_20185_() - entry.prevX;
            double dz = owner.m_20189_() - entry.prevZ;
            entry.prevX = owner.m_20185_();
            entry.prevZ = owner.m_20189_();
            float walk = (float)Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 4.0);

            living.f_19790_ = living.m_20185_();
            living.f_19791_ = living.m_20186_();
            living.f_19792_ = living.m_20189_();
            living.m_20343_(owner.m_20185_(), owner.m_20186_(), owner.m_20189_());
            living.f_19797_ = owner.f_19797_;
            // Suelo y caída se copian ANTES del tick porque son justo lo que leen las animaciones de
            // vuelo y aleteo: la gallina bate las alas al saltar o caer y las tiene quietas al andar.
            living.m_6853_(owner.m_20096_());
            living.f_19789_ = owner.f_19789_;
            animate(entry, living, owner);

            // Y aquí manda el jugador, no el muñeco. Medido en el servidor: dejando que el modelo
            // calculara su propia marcha, la velocidad subía a 1.0 y NO bajaba nunca, así que las patas
            // seguían andando con el jugador parado. Imponer la velocidad después del tick (sin avanzar
            // la fase, que ya la avanzó el tick) hace que las patas anden cuando andas y paren cuando
            // paras, sin depender de lo que el modelo crea que está haciendo.
            living.f_267362_.m_267771_(walk);
         }
      }
   }

   /**
    * Un tick de animación aislado del mundo.
    *
    * <p>Va mudo porque la voz la emite el servidor para todos por igual: si además sonara el modelo de
    * cada cliente, el disfrazado haría el doble de ruido que el mob de verdad y eso lo delata. Y va con
    * la caja de colisión vacía para que no empuje a quien pase al lado, que sería otra pista.
    */
   private static void animate(Entry entry, LivingEntity living, Player owner) {
      try {
         living.m_20225_(true);
         living.f_19794_ = true;
         living.m_20242_(true);
         // Se conserva la componente vertical del jugador: es la que distingue "en el aire" de "en el
         // suelo" para las animaciones de vuelo y aleteo.
         // Sin velocidad propia: el modelo no debe desplazarse por su cuenta, solo animarse. Lo que
         // distingue "en el aire" de "en el suelo" para el aleteo ya se le copia con el estado de suelo.
         living.m_20256_(Vec3.f_82478_);
         living.m_20011_(new AABB(living.m_20182_(), living.m_20182_()));
         living.m_8119_();
      } catch (RuntimeException | LinkageError ex) {
         // Un mob de otro mod puede dar por hecho que está registrado en el mundo. Se deja de tickear
         // ese modelo concreto (se queda estático) en vez de repetir el fallo 20 veces por segundo, y
         // nunca se cancela el disfraz ni se revela el cuerpo humano.
         entry.tickable = false;
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

   /** Como máximo dos modelos nuevos por tick de cliente, para no concentrar el coste al empezar. */
   private static boolean allowCreation() {
      long now = Minecraft.m_91087_().f_91074_ == null ? 0L : (long)Minecraft.m_91087_().f_91074_.f_19797_;
      if (now != creationTick) {
         creationTick = now;
         creationsThisTick = 0;
      }

      if (creationsThisTick >= MAX_CREATIONS_PER_TICK) {
         return false;
      }

      creationsThisTick++;
      return true;
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
      boolean tickable = true;
      double prevX;
      double prevZ;

      Entry(EntityPropSnapshot snapshot, Entity entity) {
         this.snapshot = snapshot;
         this.entity = entity;
      }
   }
}
