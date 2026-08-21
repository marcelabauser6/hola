package com.fantasticchameleon.pose;

import com.fantasticchameleon.FantasticConfig;
import com.fantasticchameleon.game.GlobalSettings;
import com.fantasticchameleon.item.ArmorPaintHandler;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.network.ForceExitPayload;
import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.fantasticchameleon.paint.FrozenFrame;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LockTick {
   private static final Map<UUID, Vec3> ANCHORS = new ConcurrentHashMap<>();
   private static final Map<UUID, LockTick.Gesture> GESTURES = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> CLIP_RETRY = new ConcurrentHashMap<>();
   private static final int CLIP_CHECK_TICKS = 10;
   private static final long CLIP_BACKOFF_TICKS = 60L;
   /**
    * Presupuesto global del guardia anti-clipping por tick de servidor.
    *
    * <p>El coste no puede depender de cuánta gente esté escondida: un cupo por jugador sigue sumando.
    * Por eso el tope es global y se reparte por orden de llegada; a quien no le toca se le atiende en el
    * tick siguiente, nunca se le descarta.
    *
    * <p>Medido dentro del runtime real de Forge, encerrado en piedra: una medición de oclusión cuesta
    * <b>5,8 µs</b> y una búsqueda de hueco fallida <b>398 µs</b>. Sin tope, 40 jugadores en ese peor caso
    * consumen el 32 % del tick y unos 125 lo agotan por completo: eso es tumbar el servidor. Con
    * 8 mediciones y 1 búsqueda por tick, el techo queda en ~0,9 % del tick, cueste lo que cueste.
    */
   private static final int CLIP_BUDGET_PER_TICK = 600;
   private static final int COST_RATIO = 75;
   private static final int MAX_SEARCHES_PER_TICK = 1;
   private static long budgetTick = Long.MIN_VALUE;
   private static int budgetLeft;
   private static int searchesThisTick;
   private static final int GESTURE_TIMEOUT = 40;
   private static final double MAX_GESTURE_DIST = 8.0;
   private static final double NUDGE_STEP = 0.0625;
   private static final double NUDGE_MAX_HIDDEN = 0.2;
   private static final float TURN_STEP = 5.0F;
   public static final float MAX_TILT = 90.0F;
   public static final int AXIS_YAW = 0;
   public static final int AXIS_PITCH = 1;
   public static final int AXIS_ROLL = 2;
   private static final double MAX_MOVE_STEP = 8.0;

   private LockTick() {
   }

   public static void reanchor(ServerPlayer sp, Vec3 pos) {
      ANCHORS.put(sp.m_20148_(), pos);
      sp.m_6021_(pos.f_82479_, pos.f_82480_, pos.f_82481_);
   }

   public static void release(Player player) {
      ANCHORS.remove(player.m_20148_());
      GESTURES.remove(player.m_20148_());
      CLIP_RETRY.remove(player.m_20148_());
   }

   private static LockTick.Gesture touchGesture(ServerPlayer sp) {
      long now = sp.m_9236_().m_46467_();
      LockTick.Gesture g = GESTURES.get(sp.m_20148_());
      LockTick.Gesture next = g == null
         ? new LockTick.Gesture(
            ANCHORS.getOrDefault(sp.m_20148_(), sp.m_20182_()), Services.PLATFORM.get(sp, PaintAttachments.LOCK_YAW), pitchOf(sp), rollOf(sp), now
         )
         : new LockTick.Gesture(g.pos(), g.yaw(), g.pitch(), g.roll(), now);
      GESTURES.put(sp.m_20148_(), next);
      return next;
   }

   public static boolean inGesture(Player player) {
      return GESTURES.containsKey(player.m_20148_());
   }

   public static void endGesture(ServerPlayer sp) {
      LockTick.Gesture g = GESTURES.remove(sp.m_20148_());
      if (g != null && Services.PLATFORM.get(sp, PaintAttachments.LOCKED)) {
         Vec3 pos = ANCHORS.getOrDefault(sp.m_20148_(), sp.m_20182_());
         int pose = BodyClip.measuredPose(sp, Services.PLATFORM.get(sp, PaintAttachments.POSE));
         float yaw = Services.PLATFORM.get(sp, PaintAttachments.LOCK_YAW);
         double hidden = BodyClip.hiddenRatio(sp.m_9236_(), pos, yaw, pitchOf(sp), rollOf(sp), pose, (double)ArmorPaintHandler.scaleOf(sp));
         if (hidden <= placeLimit()) {
            turnFrozenHead(sp, Mth.m_14177_(yaw - g.yaw()));
         } else {
            applyOrient(sp, 1, g.pitch());
            applyOrient(sp, 2, g.roll());
            applyOrient(sp, 0, g.yaw());
            ANCHORS.put(sp.m_20148_(), g.pos());
            sp.m_6021_(g.pos().f_82479_, g.pos().f_82480_, g.pos().f_82481_);
         }
      }
   }

   private static void expireGesture(ServerPlayer sp) {
      LockTick.Gesture g = GESTURES.get(sp.m_20148_());
      if (g != null && sp.m_9236_().m_46467_() - g.lastTick() > 40L) {
         endGesture(sp);
      }
   }

   private static double placeLimit() {
      return GlobalSettings.clipGuard() ? 0.2 : 0.45;
   }

   public static float pitchOf(Player p) {
      return Services.PLATFORM.get(p, PaintAttachments.LOCK_PITCH);
   }

   public static float rollOf(Player p) {
      return Services.PLATFORM.get(p, PaintAttachments.LOCK_ROLL);
   }

   public static void clearTilt(Player p) {
      Services.PLATFORM.set(p, PaintAttachments.LOCK_PITCH, 0.0F);
      Services.PLATFORM.set(p, PaintAttachments.LOCK_ROLL, 0.0F);
   }

   public static void nudge(ServerPlayer sp, int dir) {
      if (Services.PLATFORM.get(sp, PaintAttachments.LOCKED)) {
         float yaw = Services.PLATFORM.get(sp, PaintAttachments.LOCK_YAW);
         int poseNow = Services.PLATFORM.get(sp, PaintAttachments.POSE);
         if (dir != 6 && dir != 7) {
            double rad = Math.toRadians((double)yaw);
            Vec3 fwd = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
            Vec3 right = new Vec3(Math.cos(rad), 0.0, Math.sin(rad));

            Vec3 d = switch (dir) {
               case 0 -> fwd.m_82490_(0.0625);
               case 1 -> fwd.m_82490_(-0.0625);
               case 2 -> right.m_82490_(-0.0625);
               case 3 -> right.m_82490_(0.0625);
               case 4 -> new Vec3(0.0, 0.0625, 0.0);
               case 5 -> new Vec3(0.0, -0.0625, 0.0);
               default -> Vec3.f_82478_;
            };
            if (d.m_82556_() != 0.0) {
               Vec3 target = ANCHORS.getOrDefault(sp.m_20148_(), sp.m_20182_()).m_82549_(d);
               int pose = BodyClip.measuredPose(sp, Services.PLATFORM.get(sp, PaintAttachments.POSE));
               double scale = (double)ArmorPaintHandler.scaleOf(sp);
               double now = BodyClip.hiddenRatio(sp.m_9236_(), sp.m_20182_(), yaw, pitchOf(sp), rollOf(sp), pose, scale);
               double after = BodyClip.hiddenRatio(sp.m_9236_(), target, yaw, pitchOf(sp), rollOf(sp), pose, scale);
               if (!(after > placeLimit()) || !(after >= now)) {
                  ANCHORS.put(sp.m_20148_(), target);
                  sp.m_6021_(target.f_82479_, target.f_82480_, target.f_82481_);
               }
            }
         } else {
            float step = dir == 6 ? -5.0F : 5.0F;
            float newYaw = Mth.m_14177_(yaw + step);
            double sc = (double)ArmorPaintHandler.scaleOf(sp);
            float pi = pitchOf(sp);
            float ro = rollOf(sp);
            int clipPose = BodyClip.measuredPose(sp, poseNow);
            double nowH = BodyClip.hiddenRatio(sp.m_9236_(), sp.m_20182_(), yaw, pi, ro, clipPose, sc);
            double afterH = BodyClip.hiddenRatio(sp.m_9236_(), sp.m_20182_(), newYaw, pi, ro, clipPose, sc);
            if (!(afterH > placeLimit()) || !(afterH >= nowH)) {
               Services.PLATFORM.set(sp, PaintAttachments.LOCK_YAW, newYaw);
               if (poseNow == -1) {
                  FrozenFrame f = Services.PLATFORM.get(sp, PaintAttachments.FROZEN_FRAME);
                  Services.PLATFORM
                     .set(sp, PaintAttachments.FROZEN_FRAME, new FrozenFrame(Mth.m_14177_(f.headYaw() + step), f.pitch(), f.walkPos(), f.walkSpeed(), f.age()));
               }
            }
         }
      }
   }

   public static void move(ServerPlayer sp, double dx, double dy, double dz, boolean end) {
      if (Services.PLATFORM.get(sp, PaintAttachments.LOCKED)) {
         Vec3 d = new Vec3(Mth.m_14008_(dx, -8.0, 8.0), Mth.m_14008_(dy, -8.0, 8.0), Mth.m_14008_(dz, -8.0, 8.0));
         LockTick.Gesture g = touchGesture(sp);
         if (d.m_82556_() > 0.0 && spend(sp.m_9236_().m_46467_(), COST_RATIO)) {
            Vec3 target = ANCHORS.getOrDefault(sp.m_20148_(), sp.m_20182_()).m_82549_(d);
            // Se mide la oclusión del destino: antes el tick sacaba al jugador de la geometría, así
            // que mover el ancla dentro de una pared se corregía solo. Con la excepción de clipping
            // eso ya no pasa, y sin esta comprobación se podía acabar libre dentro de un bloque.
            int pose = BodyClip.measuredPose(sp, Services.PLATFORM.get(sp, PaintAttachments.POSE));
            double scale = (double)ArmorPaintHandler.scaleOf(sp);
            float yaw = Services.PLATFORM.get(sp, PaintAttachments.LOCK_YAW);
            boolean fits = BodyClip.hiddenRatio(sp.m_9236_(), target, yaw, pitchOf(sp), rollOf(sp), pose, scale) <= placeLimit();
            if (target.m_82557_(g.pos()) <= 64.0 && fits) {
               ANCHORS.put(sp.m_20148_(), target);
               sp.m_6021_(target.f_82479_, target.f_82480_, target.f_82481_);
            }
         }

         if (end) {
            endGesture(sp);
         }
      }
   }

   public static void setOrient(ServerPlayer sp, int axis, float value, boolean end) {
      if (Services.PLATFORM.get(sp, PaintAttachments.LOCKED)) {
         touchGesture(sp);
         applyOrient(sp, axis, value);
         if (end) {
            endGesture(sp);
         }
      }
   }

   private static void applyOrient(ServerPlayer sp, int axis, float value) {
      if (axis == 1) {
         Services.PLATFORM.set(sp, PaintAttachments.LOCK_PITCH, Mth.m_14036_(value, -90.0F, 90.0F));
      } else if (axis == 2) {
         Services.PLATFORM.set(sp, PaintAttachments.LOCK_ROLL, Mth.m_14036_(value, -90.0F, 90.0F));
      } else {
         Services.PLATFORM.set(sp, PaintAttachments.LOCK_YAW, Mth.m_14177_(value));
      }
   }

   private static void turnFrozenHead(ServerPlayer sp, float step) {
      if (step != 0.0F && Services.PLATFORM.get(sp, PaintAttachments.POSE) == -1) {
         FrozenFrame f = Services.PLATFORM.get(sp, PaintAttachments.FROZEN_FRAME);
         Services.PLATFORM
            .set(sp, PaintAttachments.FROZEN_FRAME, new FrozenFrame(Mth.m_14177_(f.headYaw() + step), f.pitch(), f.walkPos(), f.walkSpeed(), f.age()));
      }
   }

   private static boolean hasOneBlockClearance(ServerPlayer sp) {
      Vec3 p = sp.m_20182_();
      return sp.m_9236_().m_45756_(sp, new AABB(p.f_82479_ - 0.3, p.f_82480_ + 0.01, p.f_82481_ - 0.3, p.f_82479_ + 0.3, p.f_82480_ + 0.98, p.f_82481_ + 0.3));
   }

   /**
    * True si la adherencia del jugador a la geometría es deliberada.
    *
    * <p>Un prop fijado, una criatura capturada o un acople Meccha están pegados a propósito: parte del
    * cuerpo roza el bloque. El guardia anti-clipping mide exactamente eso, así que sin esta excepción
    * empujaba al jugador fuera cada tick (despegando el acople) y recalculaba 75 muestras de volumen
    * por jugador y por tick, que es lo que se notaba como lag al empezar la partida.
    */
   private static void rollBudget(long tick) {
      if (tick != budgetTick) {
         budgetTick = tick;
         budgetLeft = CLIP_BUDGET_PER_TICK;
         searchesThisTick = 0;
      }
   }

   /** Reserva cupo del tick actual. Devuelve false si ya se agotó y hay que esperar al siguiente. */
   private static boolean spend(long tick, int cost) {
      rollBudget(tick);
      if (budgetLeft < cost) {
         return false;
      }

      budgetLeft -= cost;
      return true;
   }

   /**
    * Cupo de búsqueda de hueco: como máximo una por tick en todo el servidor.
    *
    * <p>Se contabiliza aparte y no contra el presupuesto de mediciones a propósito. Si se descontara del
    * mismo saco, bastaría afinar mal una constante para que la búsqueda no cupiera nunca y el rescate
    * quedara desactivado en silencio: quien se quedara tapiado no saldría jamás.
    */
   private static boolean spendSearch(long tick) {
      rollBudget(tick);
      if (searchesThisTick >= MAX_SEARCHES_PER_TICK) {
         return false;
      }

      searchesThisTick++;
      return true;
   }

   /**
    * Turno de comprobación de un jugador.
    *
    * <p>El reparto se desfasa con su UUID para que no coincidan todos en el mismo tick: si todos los
    * escondidos se fijan a la vez, sus comprobaciones caerían juntas y producirían un pico periódico.
    */
   private static boolean due(ServerPlayer sp, long now) {
      int offset = Math.floorMod(sp.m_20148_().hashCode(), CLIP_CHECK_TICKS);
      return Math.floorMod(now, (long)CLIP_CHECK_TICKS) == (long)offset && now >= CLIP_RETRY.getOrDefault(sp.m_20148_(), 0L);
   }

   /** Limpia el estado de los jugadores que ya no están conectados. */
   public static void sweep(MinecraftServer server) {
      ANCHORS.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
      GESTURES.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
      CLIP_RETRY.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
   }

   private static boolean flushOnPurpose(ServerPlayer sp) {
      return Boolean.TRUE.equals(Services.PLATFORM.getOrNull(sp, PaintAttachments.BLOCK_FORM))
         || Boolean.TRUE.equals(Services.PLATFORM.getOrNull(sp, PaintAttachments.ATTACHED));
   }

   /** Lleva puesto un disfraz de Prop Hunt, sea del catálogo o una criatura capturada. */
   private static boolean wearsProp(ServerPlayer sp) {
      Integer prop = Services.PLATFORM.getOrNull(sp, PaintAttachments.PROP);
      if (prop != null && prop >= 0) {
         return true;
      }

      EntityPropSnapshot snapshot = Services.PLATFORM.getOrNull(sp, PaintAttachments.ENTITY_PROP);
      return snapshot != null && snapshot.present();
   }

   public static void onPlayerTick(ServerPlayer sp) {
      boolean locked = Services.PLATFORM.get(sp, PaintAttachments.LOCKED);
      boolean posing = Services.PLATFORM.get(sp, PaintAttachments.POSING);
      if (locked || posing) {
         boolean flush = flushOnPurpose(sp);
         boolean prop = wearsProp(sp);
         // La salida de emergencia por agua se mantiene para los disfraces: quedarse anclado bajo el
         // agua sin que el servidor te suelte era ahogarse sin remedio automático.
         if (flush || !GlobalSettings.clipGuard() || !sp.m_20069_() && (sp.m_20089_() != Pose.SWIMMING || hasOneBlockClearance(sp))) {
            if (sp.f_19797_ % 60 == 0) {
               FantasticNetwork.updateSafePosIfValid(sp);
            }

            if (locked) {
               sp.m_20256_(Vec3.f_82478_);
               sp.f_19789_ = 0.0F;
               if (inGesture(sp)) {
                  expireGesture(sp);
               } else {
                  float yaw = Services.PLATFORM.get(sp, PaintAttachments.LOCK_YAW);
                  int pose = BodyClip.measuredPose(sp, Services.PLATFORM.get(sp, PaintAttachments.POSE));
                  double sc = (double)ArmorPaintHandler.scaleOf(sp);
                  Vec3 anchor = ANCHORS.get(sp.m_20148_());
                  float pi = pitchOf(sp);
                  float ro = rollOf(sp);
                  // Medir la oclusión cuesta 75 muestras de volumen, y buscar hueco hasta 5.625. Al
                  // fijarse pegado a una pared el resultado siempre supera el límite, así que se
                  // repetía la búsqueda entera cada tick y en el mismo hilo del servidor: eso es lo
                  // que congelaba la partida para todo el mundo. Ahora se comprueba como máximo cada
                  // 10 ticks, con umbral laxo para los disfraces, y si no hay hueco se espera antes
                  // de volver a intentarlo en vez de insistir 20 veces por segundo.
                  long now = sp.m_9236_().m_46467_();
                  boolean due = due(sp, now);
                  double allowed = prop ? BodyClip.HARD_MAX_HIDDEN : BodyClip.limit();
                  if (!flush && due && spend(now, COST_RATIO)
                     && BodyClip.hiddenRatio(sp.m_9236_(), sp.m_20182_(), yaw, pi, ro, pose, sc) > allowed) {
                     // La búsqueda de hueco cuesta 398 µs en el peor caso, casi setenta veces más que
                     // medir. Solo se permite una por tick en todo el servidor: preferimos rescatar a
                     // alguien medio segundo más tarde que poner en riesgo el tick.
                     if (!spendSearch(now)) {
                        CLIP_RETRY.put(sp.m_20148_(), now + CLIP_CHECK_TICKS);
                        return;
                     }

                     Optional<Vec3> valid = BodyClip.findValidPosition(sp.m_9236_(), sp.m_20182_(), yaw, pi, ro, pose, sc);
                     if (valid.isPresent()) {
                        CLIP_RETRY.remove(sp.m_20148_());
                        anchor = valid.get();
                        ANCHORS.put(sp.m_20148_(), anchor);
                        sp.m_6021_(anchor.f_82479_, anchor.f_82480_, anchor.f_82481_);
                        return;
                     }

                     CLIP_RETRY.put(sp.m_20148_(), now + CLIP_BACKOFF_TICKS);
                     if ((pi != 0.0F || ro != 0.0F)
                        && spend(now, COST_RATIO)
                        && BodyClip.hiddenRatio(sp.m_9236_(), sp.m_20182_(), yaw, 0.0F, 0.0F, pose, sc) <= BodyClip.limit()) {
                        clearTilt(sp);
                        return;
                     }
                  }

                  if (anchor == null) {
                     ANCHORS.put(sp.m_20148_(), sp.m_20182_());
                  } else if (FantasticConfig.getOr(true, true) && sp.m_20182_().m_82557_(anchor) > 1.0E-6) {
                     sp.m_6021_(anchor.f_82479_, anchor.f_82480_, anchor.f_82481_);
                  }
               }
            }
         } else {
            FantasticNetwork.resetPose(sp);
            Services.PLATFORM.sendToClient(sp, ForceExitPayload.INSTANCE);
         }
      }
   }

   private static record Gesture(Vec3 pos, float yaw, float pitch, float roll, long lastTick) {
   }
}
