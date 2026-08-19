package com.fantasticchameleon.network;

import com.fantasticchameleon.FantasticChameleon;
import com.fantasticchameleon.compat.Attr;
import com.fantasticchameleon.game.Arenas;
import com.fantasticchameleon.game.EditorActions;
import com.fantasticchameleon.game.FantasticAdvancements;
import com.fantasticchameleon.game.GlobalSettings;
import com.fantasticchameleon.game.Perms;
import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.game.Stats;
import com.fantasticchameleon.game.WorldPick;
import com.fantasticchameleon.item.ArenaWandItem;
import com.fantasticchameleon.item.ArmorPaintHandler;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.movement.Climb;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.BodyPart;
import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.fantasticchameleon.paint.FrozenFrame;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.paint.SkinRegions;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.BodyClip;
import com.fantasticchameleon.prophunt.PropGridSnap;
import com.fantasticchameleon.prophunt.PropHunt;
import com.fantasticchameleon.pose.LockTick;
import com.fantasticchameleon.pose.PoseDefs;
import com.fantasticchameleon.pose.PropShapes;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class FantasticNetwork {
   private static final Map<UUID, Long> LAST_PROVOKE = new ConcurrentHashMap<>();
   private static final long PROVOKE_COOLDOWN = 30L;
   /** El último cupo es solo para desacoplar: compartirlo con fijarse descartaba el desacople. */
   private static final int[] ACTION_COOLDOWN_TICKS = new int[]{20, 20, 5, 2, 4, 2, 10, 2, 1, 1, 2, 1, 2};
   private static final int SLOT_DETACH = 12;
   private static final int ACTION_BURST_FREE = 3;
   private static final int ACTION_RESET_TICKS = 100;
   private static final Map<UUID, long[]> ACTION_LAST = new ConcurrentHashMap<>();
   public static final ResourceLocation LOCK_MODIFIER_ID = new ResourceLocation("fantastic_chameleon", "pose_lock");
   /** Modificador de velocidad que adopta el ritmo de la criatura imitada. */
   public static final ResourceLocation PROP_SPEED_ID = new ResourceLocation("fantastic_chameleon", "prop_speed");
   /**
    * Velocidad del zombi, que en el juego se mueve prácticamente al ritmo de un jugador andando. Sirve
    * de referencia para traducir la velocidad de cualquier mob a la escala del jugador.
    */
   private static final double REFERENCE_MOB_SPEED = 0.23;
   private static final double PLAYER_SPEED = 0.1;
   /**
    * Límites del ritmo adoptado.
    *
    * <p>Medido en el servidor, la velocidad base va de 0,09 (camello) a 1,2 (delfín), y los que saltan o
    * nadan la usan de otra forma: un slime marca 0,7 y un delfín 1,2, que traducidos a las piernas de un
    * jugador darían 3x y 5x. Se acota para que el disfraz cambie de ritmo de forma creíble sin convertir
    * a nadie en un cohete.
    */
   private static final double MIN_SPEED_RATIO = 0.6;
   private static final double MAX_SPEED_RATIO = 1.35;
   private static final Map<String, Double> MOB_SPEED = new ConcurrentHashMap<>();
   public static final int POSE_COUNT = PoseDefs.count();
   public static final int BLOCK_POSE = 8;
   public static final int FREEZE_POSE = -1;
   private static final Map<UUID, Vec3> SAFE_POS = new ConcurrentHashMap<>();
   /** Lo que el cuerpo se hunde en el bloque al acoplarse, para que no se vea una junta de aire. */
   private static final double ATTACH_SINK = 0.09;

   private FantasticNetwork() {
   }

   public static Optional<Component> versionMismatch(String serverVersion) {
      String mine = FantasticChameleon.VERSION;
      return serverVersion != null && !serverVersion.isEmpty() && !serverVersion.equals(mine)
         ? Optional.of(Component.m_237110_("fantastic.disconnect.version", new Object[]{serverVersion, mine}))
         : Optional.empty();
   }

   public static void handleArenaCorner(ArenaCornerPayload payload, ServerPlayer sp) {
      // Administrar arenas es cosa de operadores, también por red y no solo desde la interfaz.
      if (Perms.isStaff(sp)) {
         Rooms.setArenaCornerAt(sp, payload.which(), payload.pos());
      }
   }

   public static void handleArenaEdit(ArenaEditPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 10)) {
         boolean op = Perms.isStaff(sp);
         if (op) {
            Arenas.handleEdit(sp, payload.action(), payload.name(), payload.arg());
         }
      }
   }

   public static void handleArenaPreview(ArenaPreviewPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 10)) {
         boolean op = Perms.isStaff(sp);
         if (op && Arenas.storePreview(payload.arena(), payload.png())) {
            Arenas.sendList(sp);
            Services.PLATFORM.sendToClient(sp, new PreviewDataPayload(payload.arena(), payload.png()));
         }
      }
   }

   public static void handlePreviewRequest(PreviewRequestPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 6)) {
         Services.PLATFORM.sendToClient(sp, new PreviewDataPayload(payload.arena(), Arenas.readPreview(payload.arena())));
      }
   }

   public static void handleCrawl(CrawlPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 7)) {
         Rooms.toggleCrawl(sp);
      }
   }

   public static void handleClimb(ClimbPayload payload, ServerPlayer sp) {
      Climb.setInput(sp, payload.sneak(), payload.jump());
   }

   public static void handleRoomConfig(RoomConfigPayload payload, ServerPlayer sp) {
      if (Perms.isStaff(sp)) {
         Rooms.setConfig(sp, payload.field(), payload.value(), true);
      }
   }

   public static void handleEditorAction(EditorActionPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 11)) {
         EditorActions.handle(sp, payload.action(), payload.a(), payload.b(), payload.value());
      }
   }

   public static void handleRoomAction(RoomActionPayload payload, ServerPlayer sp) {
      // Crear, borrar, expulsar, banear y dar la varita son acciones administrativas: solo operadores.
      // La varita, además, es el único objeto que este canal podía entregar y lo hacía sin comprobar
      // nada, así que cualquier cliente podía pedirla.
      if (!Perms.isStaff(sp)) {
         sp.m_5661_(Component.m_237115_("fantastic.ui.staff_only").m_130940_(ChatFormatting.RED), true);
         return;
      }

      if ("wand".equals(payload.action())) {
         giveWand(sp);
      } else {
         Rooms.menuAction(sp, payload.action(), payload.a(), payload.b());
      }
   }

   private static void giveWand(ServerPlayer sp) {
      ItemStack wand = ArenaWandItem.create();
      if (!sp.m_150109_().m_36054_(wand)) {
         sp.m_36176_(wand, false);
      }

      sp.m_5661_(Component.m_237115_("fantastic.wand.given").m_130940_(ChatFormatting.GREEN), true);
   }

   public static void handleBrushPaint(BrushPaintPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 5) && (sp.m_21205_().m_150930_(FantasticItems.PAINT_BRUSH.get()) || sp.m_21206_().m_150930_(FantasticItems.PAINT_BRUSH.get()))) {
         ServerLevel level = sp.m_284548_();
         Vec3 pos = new Vec3((double)payload.x(), (double)payload.y(), (double)payload.z());
         if (!(sp.m_20238_(pos) > 64.0)) {
            PaintSplats.broadcast(level, pos, 600);
            FantasticAdvancements.award(sp, "splat");
         }
      }
   }

   public static void handleNudge(NudgePayload payload, ServerPlayer sp) {
      if (!throttled(sp, 2)) {
         LockTick.nudge(sp, payload.dir());
      }
   }

   public static void handleMove(MovePayload payload, ServerPlayer sp) {
      if (payload.end() || !throttled(sp, 8)) {
         LockTick.move(sp, (double)payload.dx(), (double)payload.dy(), (double)payload.dz(), payload.end());
      }
   }

   public static void handleSetOrient(SetOrientPayload payload, ServerPlayer sp) {
      if (payload.end() || !throttled(sp, 9)) {
         LockTick.setOrient(sp, payload.axis(), payload.value(), payload.end());
      }
   }

   public static void handleSetSize(SetSizePayload payload, ServerPlayer sp) {
      if (!throttled(sp, 7)) {
         Services.PLATFORM.set(sp, PaintAttachments.SIZE_MINI, payload.mini());
         ArmorPaintHandler.updateShrink(sp);
      }
   }

   public static void handleShaderState(ShaderStatePayload payload, ServerPlayer sp) {
      if (!throttled(sp, 7)) {
         Rooms.onShaderState(sp, payload.active());
      }
   }

   public static void handleRequestRooms(RequestRoomsPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 6)) {
         Services.PLATFORM.sendToClient(sp, Rooms.snapshot(sp));
      }
   }

   public static void handleProvoke(ProvokePayload payload, ServerPlayer sp) {
      long now = sp.m_9236_().m_46467_();
      Long last = LAST_PROVOKE.get(sp.m_20148_());
      if (last == null || now - last >= 30L) {
         LAST_PROVOKE.put(sp.m_20148_(), now);
         Room.WhistleResult scored = Rooms.whistle(sp);
         if (scored == Room.WhistleResult.PAID) {
            sp.m_240418_(Component.m_237110_("fantastic.whistle.scored", new Object[]{25}).m_130940_(ChatFormatting.GREEN), true);
         } else if (scored == Room.WhistleResult.TOO_FAR) {
            sp.m_240418_(Component.m_237115_("fantastic.whistle.too_far").m_130940_(ChatFormatting.YELLOW), true);
         }

         ServerLevel level = sp.m_284548_();
         level.m_6263_(null, sp.m_20185_(), sp.m_20186_(), sp.m_20189_(), (SoundEvent)SoundEvents.f_12212_.m_203334_(), SoundSource.PLAYERS, 1.2F, 1.6F);
         double py = sp.m_20186_() + (double)sp.m_20206_() + 0.2;
         level.m_8767_(ParticleTypes.f_123758_, sp.m_20185_(), py, sp.m_20189_(), 6, 0.25, 0.1, 0.25, 0.6);
      }
   }

   public static void sweepOfflinePlayers(MinecraftServer server) {
      LAST_PROVOKE.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
      ACTION_LAST.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
      WorldPick.sweep(server);
      Climb.sweep(server);
      // Anclas, gestos y esperas del guardia también se limpian: si no, cada desconexión con el
      // disfraz puesto dejaba una entrada viva para siempre.
      LockTick.sweep(server);
   }

   private static boolean throttled(Player player, int slot) {
      if (slot >= 0 && slot < ACTION_COOLDOWN_TICKS.length) {
         long now = player.m_9236_().m_46467_();
         long[] s = ACTION_LAST.computeIfAbsent(player.m_20148_(), k -> new long[ACTION_COOLDOWN_TICKS.length * 2]);
         long last = s[slot * 2];
         long streak = s[slot * 2 + 1];
         if (now - last > 100L) {
            streak = 0L;
         }

         if (streak >= 3L && now - last < (long)ACTION_COOLDOWN_TICKS[slot]) {
            return true;
         } else {
            s[slot * 2] = now;
            s[slot * 2 + 1] = streak + 1L;
            return false;
         }
      } else {
         return false;
      }
   }

   public static void handlePose(PosePayload payload, ServerPlayer player) {
      if (!throttled(player, 1)) {
         if (payload.posing() && PropHunt.isPropHunt(player)) {
            // Prop Hunt no usa poses humanas. Validarlo aquí impide que un cliente modificado
            // adopte una hitbox pequeña de Meccha como seeker, espectador o hider.
            resetPose(player);
            Services.PLATFORM.sendToClient(player, ForceExitPayload.INSTANCE);
            return;
         }

         if (!payload.posing()) {
            resetPose(player);
         } else {
            Integer wornProp = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
            if (wornProp == null || wornProp < 0) {
               int newPose = Math.floorMod(payload.pose(), POSE_COUNT);
               if (newPose != 8) {
                  if (Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.BLOCK_FORM))) {
                     Rooms.leaveBlockPose(player);
                  }

                  if (GlobalSettings.clipGuard() && Services.PLATFORM.get(player, PaintAttachments.LOCKED)) {
                     float yaw = Services.PLATFORM.get(player, PaintAttachments.LOCK_YAW);
                     Optional<Vec3> valid = BodyClip.findValidPosition(
                        player.m_9236_(), player.m_20182_(), yaw, LockTick.pitchOf(player), LockTick.rollOf(player), newPose, 1.0
                     );
                     if (valid.isPresent()) {
                        Vec3 v = valid.get();
                        player.m_6021_(v.f_82479_, v.f_82480_, v.f_82481_);
                     } else {
                        player.m_5661_(Component.m_237115_("fantastic.pose.tight").m_130940_(ChatFormatting.YELLOW), true);
                     }
                  }

                  if (!Services.PLATFORM.get(player, PaintAttachments.POSING) && !Services.PLATFORM.get(player, PaintAttachments.LOCKED)) {
                     SAFE_POS.put(player.m_20148_(), player.m_20182_());
                  }

                  Services.PLATFORM.set(player, PaintAttachments.POSING, true);
                  Services.PLATFORM.set(player, PaintAttachments.POSE, newPose);
                  player.m_6210_();
                  FantasticAdvancements.award(player, "pose");
               }
            }
         }
      }
   }

   public static void resetPose(Player player) {
      Services.PLATFORM.set(player, PaintAttachments.LOCKED, false);
      Services.PLATFORM.set(player, PaintAttachments.POSING, false);
      Services.PLATFORM.set(player, PaintAttachments.POSE, 0);
      Services.PLATFORM.set(player, PaintAttachments.ATTACHED, Boolean.FALSE);
      Services.PLATFORM.set(player, PaintAttachments.FROZEN_FRAME, FrozenFrame.NONE);
      LockTick.clearTilt(player);
      if (player instanceof ServerPlayer bp) {
         Rooms.leaveBlockPose(bp);
         clearProp(bp);
      } else {
         Services.PLATFORM.set(player, PaintAttachments.BLOCK_FORM, false);
      }

      LockTick.release(player);
      immobilize(player, false);
      player.m_20242_(false);
      player.m_6210_();
      Vec3 safe = SAFE_POS.remove(player.m_20148_());
      if (GlobalSettings.clipGuard() && safe != null && player instanceof ServerPlayer sp && !sp.m_9236_().m_45756_(sp, sp.m_20191_())) {
         sp.m_6021_(safe.f_82479_, safe.f_82480_, safe.f_82481_);
      }
   }

   public static void updateSafePosIfValid(ServerPlayer sp) {
      EntityDimensions dims = sp.m_6972_(Pose.STANDING);
      if (sp.m_9236_().m_45756_(sp, dims.m_20393_(sp.m_20182_()))) {
         SAFE_POS.put(sp.m_20148_(), sp.m_20182_());
      }
   }

   /** Acopla Meccha a la cara exacta clicada; nunca busca otra pared cercana. */
   public static void handleMecchaAttach(MecchaAttachPayload payload, ServerPlayer player) {
      if (throttled(player, 0) || !PropHunt.isMecchaRoom(player)) {
         return;
      }
      Room room = Rooms.roomOf(player);
      Room.Phase phase = room == null ? Room.Phase.LOBBY : room.phase();
      if (room == null || !room.isHider(player.m_20148_())
         || phase != Room.Phase.COUNTDOWN && phase != Room.Phase.HIDING && phase != Room.Phase.SEEKING
         || ChameleonArmor.coverageMask(player) != ChameleonArmor.ALL_MASK || payload.face() == Direction.DOWN) {
         return;
      }

      BlockPos pos = payload.pos();
      BlockState state = player.m_9236_().m_8055_(pos);
      if (state.m_60795_()) {
         return;
      }
      float hx = payload.hitX();
      float hy = payload.hitY();
      float hz = payload.hitZ();
      if (!Float.isFinite(hx) || !Float.isFinite(hy) || !Float.isFinite(hz)
         || hx < 0.0F || hx > 1.0F || hy < 0.0F || hy > 1.0F || hz < 0.0F || hz > 1.0F) {
         return;
      }

      Vec3 claimedHit = new Vec3((double)pos.m_123341_() + hx, (double)pos.m_123342_() + hy, (double)pos.m_123343_() + hz);
      HitResult authoritative = player.m_19907_(8.0, 1.0F, false);
      if (!(authoritative instanceof BlockHitResult ray)
         || authoritative.m_6662_() != HitResult.Type.BLOCK
         || !ray.m_82425_().equals(pos)
         || ray.m_82434_() != payload.face()
         || ray.m_82450_().m_82546_(claimedHit).m_82556_() > 0.04) {
         return;
      }
      Vec3 hit = ray.m_82450_();
      if (player.m_20238_(hit) > 64.0) {
         return;
      }

      // La espalda va contra la cara clicada, así que el giro lo decide la cara y no hacia dónde
      // mirabas: con el yaw del cliente la silueta podía quedar de perfil y dejar hueco.
      float yaw = switch (payload.face()) {
         case EAST -> -90.0F;
         case WEST -> 90.0F;
         case SOUTH -> 0.0F;
         case NORTH -> 180.0F;
         default -> Mth.m_14177_((float)Math.round(payload.yaw() / 90.0F) * 90.0F);
      };

      boolean wasLocked = Services.PLATFORM.get(player, PaintAttachments.LOCKED);
      boolean wasPosing = Services.PLATFORM.get(player, PaintAttachments.POSING);
      int wasPose = Services.PLATFORM.get(player, PaintAttachments.POSE);
      float wasYaw = Services.PLATFORM.get(player, PaintAttachments.LOCK_YAW);
      float wasPitch = LockTick.pitchOf(player);
      float wasRoll = LockTick.rollOf(player);
      FrozenFrame wasFrame = Services.PLATFORM.get(player, PaintAttachments.FROZEN_FRAME);
      if (!wasLocked && !wasPosing) {
         SAFE_POS.put(player.m_20148_(), player.m_20182_());
      }

      // El ancho de la caja depende de la pose fijada, así que primero se adopta la pose y solo
      // después se mide: al revés se separaba usando el ancho humano y quedaba flotando.
      Services.PLATFORM.set(player, PaintAttachments.POSE, 0);
      Services.PLATFORM.set(player, PaintAttachments.FROZEN_FRAME, FrozenFrame.NONE);
      Services.PLATFORM.set(player, PaintAttachments.LOCK_YAW, yaw);
      Services.PLATFORM.set(player, PaintAttachments.POSING, true);
      Services.PLATFORM.set(player, PaintAttachments.LOCKED, true);
      Services.PLATFORM.set(player, PaintAttachments.ATTACHED, true);
      LockTick.clearTilt(player);
      player.m_6210_();

      // Pegado de verdad: se mide el cuerpo que se ve, no la hitbox. El torso solo tiene 0,15 de
      // fondo mientras la caja mide 0,3 de medio ancho, así que separar por la caja dejaba un hueco
      // visible de 0,15 contra la pared. El solape de hitbox resultante es deliberado y está cubierto
      // por la excepción ATTACHED.
      double[] clip = PoseDefs.def(0).clipBox();
      double half = Math.max(0.02, Math.abs(clip[0]) * (double)ArmorPaintHandler.scaleOf(player) - ATTACH_SINK);
      // La superficie real del bloque, no el borde de su celda: una valla ocupa de 0,375 a 0,625 y un
      // panel de cristal es aún más fino, así que pegarse al borde de la celda dejaba al jugador
      // flotando en el aire y la comprobación de hueco no cuadraba. Se usa la caja del propio bloque.
      AABB shape = state.m_60812_(player.m_9236_(), pos).m_83281_()
         ? new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
         : state.m_60812_(player.m_9236_(), pos).m_83215_();
      double x = player.m_20185_();
      double y = player.m_20186_();
      double z = player.m_20189_();
      switch (payload.face()) {
         case EAST -> { x = (double)pos.m_123341_() + shape.f_82291_ + half; z = hit.f_82481_; }
         case WEST -> { x = (double)pos.m_123341_() + shape.f_82288_ - half; z = hit.f_82481_; }
         case SOUTH -> { z = (double)pos.m_123343_() + shape.f_82293_ + half; x = hit.f_82479_; }
         case NORTH -> { z = (double)pos.m_123343_() + shape.f_82290_ - half; x = hit.f_82479_; }
         // Encima del bloque se centra en la celda: con el punto exacto del rayo, clicar el borde
         // dejaba media huella en voladizo.
         case UP -> { x = (double)pos.m_123341_() + 0.5; y = (double)pos.m_123342_() + shape.f_82292_; z = (double)pos.m_123343_() + 0.5; }
         default -> { restoreLock(player, wasLocked, wasPosing, wasPose, wasYaw, wasPitch, wasRoll, wasFrame); return; }
      }

      Vec3 target = new Vec3(x, y, z);
      Vec3 delta = target.m_82546_(player.m_20182_());
      // Se valida con la caja encogida el ancho del solape intencionado: así se sigue rechazando
      // quedar metido dentro de geometría, pero no el roce a propósito contra la cara clicada.
      double tolerance = Math.min(0.24, Math.max(0.0, (double)player.m_20205_() * 0.5 - half) + 0.01);
      if (!player.m_9236_().m_45756_(player, player.m_20191_().m_82383_(delta).m_82406_(tolerance))) {
         restoreLock(player, wasLocked, wasPosing, wasPose, wasYaw, wasPitch, wasRoll, wasFrame);
         player.m_240418_(Component.m_237115_("fantastic.pose.tight").m_130940_(ChatFormatting.YELLOW), true);
         return;
      }

      LockTick.reanchor(player, target);
      // La cámara se gira hacia fuera de la pared. Al clicar un bloque estabas mirándolo, así que al
      // quedarte fijo te quedabas con la nariz metida en la textura y no se veía nada.
      if (payload.face() != Direction.UP) {
         player.m_19890_(target.f_82479_, target.f_82480_, target.f_82481_, yaw, 0.0F);
      }

      immobilize(player, true);
   }

   /** Deja el acople sin efecto si el hueco no da, en vez de quedarse a medias con pose cambiada. */
   private static void restoreLock(
      ServerPlayer player, boolean locked, boolean posing, int pose, float yaw, float pitch, float roll, FrozenFrame frame
   ) {
      Services.PLATFORM.set(player, PaintAttachments.LOCKED, locked);
      Services.PLATFORM.set(player, PaintAttachments.POSING, posing);
      Services.PLATFORM.set(player, PaintAttachments.POSE, pose);
      Services.PLATFORM.set(player, PaintAttachments.LOCK_YAW, yaw);
      Services.PLATFORM.set(player, PaintAttachments.LOCK_PITCH, pitch);
      Services.PLATFORM.set(player, PaintAttachments.LOCK_ROLL, roll);
      Services.PLATFORM.set(player, PaintAttachments.FROZEN_FRAME, frame);
      Services.PLATFORM.set(player, PaintAttachments.ATTACHED, Boolean.FALSE);
      player.m_6210_();
   }

   /**
    * Suelta el ancla sin borrar el bloque, mob, variante ni equipo capturados.
    *
    * <p>No lleva throttle y no exige modo: soltar siempre tiene que funcionar. Antes compartía cupo con
    * fijarse, así que pulsar fijar y soltar seguido descartaba el desacople: el cliente se creía libre
    * mientras el servidor seguía anclado y te devolvía a la misma posición cada tick, que es
    * exactamente el "me quedo pegado y bugueado".
    */
   public static void handleDetachProp(DetachPropPayload payload, ServerPlayer player) {
      // Solo suelta a quien está sujeto. Sin esta puerta, cualquiera podía spamear el paquete para
      // anular su caída y para multiplicar difusiones de atributos aunque no estuviera disfrazado.
      boolean anchored = Services.PLATFORM.get(player, PaintAttachments.LOCKED)
         || Services.PLATFORM.get(player, PaintAttachments.POSING)
         || Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.ATTACHED));
      if (!anchored || throttled(player, SLOT_DETACH)) {
         return;
      }

      if (Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.BLOCK_FORM))) {
         // Único camino que devuelve el casco guardado; ponerlo a false a mano lo dejaba perdido.
         Rooms.leaveBlockPose(player);
      }

      Services.PLATFORM.set(player, PaintAttachments.LOCKED, false);
      Services.PLATFORM.set(player, PaintAttachments.POSING, false);
      Services.PLATFORM.set(player, PaintAttachments.POSE, 0);
      Services.PLATFORM.set(player, PaintAttachments.FROZEN_FRAME, FrozenFrame.NONE);
      Services.PLATFORM.set(player, PaintAttachments.ATTACHED, Boolean.FALSE);
      LockTick.clearTilt(player);
      LockTick.release(player);
      immobilize(player, false);
      player.m_20242_(false);
      player.f_19794_ = false;
      player.m_20256_(Vec3.f_82478_);
      player.m_6210_();

      // Mismo rescate que al quitar la pose: soltar dentro de geometría dejaría al jugador libre
      // dentro de un bloque, así que se devuelve al último sitio donde cabía de pie.
      Vec3 safe = SAFE_POS.remove(player.m_20148_());
      if (GlobalSettings.clipGuard() && safe != null && !player.m_9236_().m_45756_(player, player.m_20191_())) {
         player.m_6021_(safe.f_82479_, safe.f_82480_, safe.f_82481_);
      } else {
         // Reafirma la posición con la caja nueva: sin esto el cliente conserva la predicción del
         // estado anclado y parece seguir clavado aunque el servidor ya lo haya soltado.
         player.m_6021_(player.m_20185_(), player.m_20186_(), player.m_20189_());
      }
   }

   public static void handleLock(LockPayload payload, ServerPlayer player) {
      if (!throttled(player, 0)) {
         if (!payload.locked()) {
            if (Services.PLATFORM.get(player, PaintAttachments.POSE) == -1) {
               resetPose(player);
            } else {
               Services.PLATFORM.set(player, PaintAttachments.LOCKED, false);
               // Dejar ATTACHED puesto mantenía la excepción de clipping con el jugador ya móvil.
               Services.PLATFORM.set(player, PaintAttachments.ATTACHED, Boolean.FALSE);
               LockTick.release(player);
               immobilize(player, false);
            }
         } else {
            if (PropHunt.isPropHunt(player)) {
               Room room = Rooms.roomOf(player);
               if (room == null || !room.canUseProp(player.m_20148_())) {
                  resetPose(player);
                  Services.PLATFORM.sendToClient(player, ForceExitPayload.INSTANCE);
                  return;
               }
            }

            boolean meccha = PropHunt.isMecchaRoom(player);
            float yaw = Mth.m_14177_(payload.yaw());
            LockTick.clearTilt(player);
            if (payload.freeze()) {
               if (meccha) {
                  // Un cliente antiguo puede seguir enviando freeze=true. Meccha canoniza siempre
                  // la pose normal erguida para evitar geometria humana solapada.
                  Services.PLATFORM.set(player, PaintAttachments.POSE, 0);
               } else {
                  Services.PLATFORM.set(player, PaintAttachments.POSE, -1);
                  Services.PLATFORM.set(player, PaintAttachments.FROZEN_FRAME, payload.frame());
               }
            }

            int pose = Services.PLATFORM.get(player, PaintAttachments.POSE);
            boolean blockForm = Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.BLOCK_FORM));
            if (!blockForm && GlobalSettings.clipGuard()) {
               int clipPose = BodyClip.measuredPose(player, pose);
               double sc = (double)ArmorPaintHandler.scaleOf(player);
               if (BodyClip.hiddenRatio(player.m_9236_(), player.m_20182_(), yaw, clipPose, sc) > 0.1) {
                  BodyClip.findValidPosition(player.m_9236_(), player.m_20182_(), yaw, clipPose, sc)
                     .ifPresent(v -> player.m_6021_(v.f_82479_, v.f_82480_, v.f_82481_));
               }
            }

            if (!Services.PLATFORM.get(player, PaintAttachments.POSING) && !Services.PLATFORM.get(player, PaintAttachments.LOCKED)) {
               SAFE_POS.put(player.m_20148_(), player.m_20182_());
            }

            yaw = (float)Math.round(yaw / 90.0F) * 90.0F;
            Integer propNow = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
            EntityPropSnapshot entityNow = Services.PLATFORM.getOrNull(player, PaintAttachments.ENTITY_PROP);
            boolean capturedEntity = entityNow != null && entityNow.present();
            if (PropHunt.isPropHunt(player) && (propNow != null && propNow >= 0 || capturedEntity)) {
               // Un prop tiene que quedar centrado en su celda, no pegado a la pared como la
               // silueta pintada del modo clasico. Los bloques mantienen la orientacion de su
               // BlockState; las criaturas (tambien las genericas) conservan hacia donde miran.
               yaw = capturedEntity || PropShapes.followsLook(propNow) ? yaw : 0.0F;
               if (!PropGridSnap.snapToCell(player, yaw)) {
                  resetPose(player);
                  Services.PLATFORM.sendToClient(player, ForceExitPayload.INSTANCE);
                  player.m_240418_(Component.m_237115_("fantastic.pose.tight").m_130940_(ChatFormatting.YELLOW), true);
                  return;
               }
            } else {
               placeAgainstCover(player, yaw);
            }
            Services.PLATFORM.set(player, PaintAttachments.LOCK_YAW, yaw);
            Services.PLATFORM.set(player, PaintAttachments.POSING, true);
            Services.PLATFORM.set(player, PaintAttachments.LOCKED, true);
            player.m_6210_();
            immobilize(player, true);
         }
      }
   }

   private static void placeAgainstCover(Player player, float yaw) {
      int pose = Services.PLATFORM.get(player, PaintAttachments.POSE);
      if (player instanceof ServerPlayer sp && PropHunt.isMecchaRoom(sp)) {
         // F puede fijar la pintura en la posicion actual, pero pegarse a una superficie solo ocurre
         // mediante MecchaAttachPayload y la cara exacta clicada; nunca se elige otra pared cercana.
         LockTick.reanchor(sp, player.m_20182_());
         return;
      }

      PoseDefs.Def def = PoseDefs.def(Math.max(0, pose));
      double[] foot = PoseDefs.footprint(def.clipBox(), yaw, (double)ArmorPaintHandler.scaleOf(player));
      int bx = Mth.m_14107_(player.m_20185_());
      int by = Mth.m_14107_(player.m_20186_());
      int bz = Mth.m_14107_(player.m_20189_());
      boolean[] solid = new boolean[]{
         isCover(player, bx - 1, by, bz), isCover(player, bx + 1, by, bz), isCover(player, bx, by, bz - 1), isCover(player, bx, by, bz + 1)
      };
      double[] at = PoseDefs.hugPosition(foot, bx, bz, solid);
      Vec3 target = new Vec3(at[0], player.m_20186_(), at[1]);
      if (player.m_9236_().m_45756_(player, player.m_20191_().m_82383_(target.m_82546_(player.m_20182_())))) {
         player.m_6021_(target.f_82479_, target.f_82480_, target.f_82481_);
      }
   }

   private static boolean isCover(Player player, int x, int y, int z) {
      BlockPos pos = new BlockPos(x, y, z);
      BlockState state = player.m_9236_().m_8055_(pos);
      return !state.m_60795_() && !state.m_60812_(player.m_9236_(), pos).m_83281_();
   }

   private static void immobilize(Player player, boolean on) {
      player.m_20242_(on);
      AttributeInstance speed = player.m_21051_(Attributes.f_22279_);
      if (speed != null) {
         Attr.remove(speed, LOCK_MODIFIER_ID);
         if (on) {
            speed.m_22118_(Attr.modifier(LOCK_MODIFIER_ID, -1.0, Operation.MULTIPLY_TOTAL));
         }
      }
   }

   public static void handleSetCanvas(SetCanvasPayload payload, ServerPlayer player) {
      if (!throttled(player, 4)) {
         BodyCanvas incoming = payload.canvas();
         if (incoming.size() == ChameleonArmor.disguiseSize(player)) {
            applyCanvasWrite(player, incoming.size(), (x, y) -> incoming.get(x, y), null);
         }
      }
   }

   public static void handleSetPropCanvas(SetPropCanvasPayload payload, ServerPlayer player) {
      Integer prop = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
      if (prop != null && prop >= 0 && payload.canvas().size() == 64) {
         Services.PLATFORM.set(player, PaintAttachments.PROP_CANVAS, payload.canvas());
      }
   }

   public static void handleSetProp(SetPropPayload payload, ServerPlayer player) {
      if (!throttled(player, 3)) {
         if (payload.prop() < 0) {
            // Quitar el disfraz también debe soltar ancla, pose e inmovilización si estaba colocado.
            resetPose(player);
         } else if (ChameleonArmor.coverageMask(player) != ChameleonArmor.ALL_MASK) {
            player.m_240418_(Component.m_237115_("fantastic.prop.need_full_set").m_130940_(ChatFormatting.RED), true);
         } else if (PropHunt.isPropHunt(player)
            && (Rooms.roomOf(player) == null || !Rooms.roomOf(player).canUseProp(player.m_20148_()))) {
            resetPose(player);
            Services.PLATFORM.sendToClient(player, ForceExitPayload.INSTANCE);
         } else if (PropHunt.isMecchaRoom(player)) {
            // En Meccha Chameleon el camuflaje es la pintura: convertirse en bloque es de Prop Hunt.
            player.m_240418_(Component.m_237115_("fantastic.prophunt.mode_only").m_130940_(ChatFormatting.RED), true);
         } else {
            boolean op = Perms.isStaff(player);
            if (op || Rooms.isInRoom(player)) {
               applyProp(player, payload.prop(), payload.variant());
            }
         }
      }
   }

   /**
    * El disfraz se mueve al ritmo del bicho: una vaca va más lenta que tú y una araña más rápida.
    *
    * <p>La velocidad base de los mobs no está en la misma escala que la del jugador, así que se traduce
    * tomando el zombi como referencia —se mueve casi igual que un jugador andando— y se acota, porque los
    * que saltan o nadan usan ese valor de otra manera.
    */
   private static void applyMobSpeed(ServerPlayer player, String typeId) {
      AttributeInstance speed = player.m_21051_(Attributes.f_22279_);
      if (speed == null) {
         return;
      }

      Attr.remove(speed, PROP_SPEED_ID);
      double base = mobSpeed(player, typeId);
      if (base <= 0.0) {
         return;
      }

      double target = base * PLAYER_SPEED / REFERENCE_MOB_SPEED;
      double ratio = Mth.m_14008_(target / PLAYER_SPEED, MIN_SPEED_RATIO, MAX_SPEED_RATIO);
      if (Math.abs(ratio - 1.0) > 0.01) {
         speed.m_22118_(Attr.modifier(PROP_SPEED_ID, ratio - 1.0, Operation.MULTIPLY_TOTAL));
      }
   }

   /** Velocidad base del tipo, leída una sola vez por tipo de una instancia desechable. */
   private static double mobSpeed(ServerPlayer player, String typeId) {
      return MOB_SPEED.computeIfAbsent(typeId, id -> {
         try {
            EntityType<?> type = BuiltInRegistries.f_256780_.m_7745_(new ResourceLocation(id));
            if (type == null) {
               return 0.0;
            }

            Entity probe = type.m_20615_(player.m_284548_());
            if (!(probe instanceof LivingEntity living)) {
               return 0.0;
            }

            AttributeInstance attr = living.m_21051_(Attributes.f_22279_);
            double value = attr == null ? 0.0 : attr.m_22115_();
            probe.m_146870_();
            return value;
         } catch (RuntimeException ignored) {
            return 0.0;
         }
      });
   }

   public static void clearProp(ServerPlayer player) {
      AttributeInstance speed = player.m_21051_(Attributes.f_22279_);
      if (speed != null) {
         // Volver a ser jugador es volver a tu propia velocidad.
         Attr.remove(speed, PROP_SPEED_ID);
      }

      Services.PLATFORM.set(player, PaintAttachments.PROP, -1);
      Services.PLATFORM.set(player, PaintAttachments.PROP_VARIANT, 0);
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, "");
      Services.PLATFORM.set(player, PaintAttachments.PROP_STATE, -1);
      Services.PLATFORM.set(player, PaintAttachments.ENTITY_PROP, EntityPropSnapshot.NONE);
      Services.PLATFORM.set(player, PaintAttachments.PROP_ACT_TICK, -1000L);
      Services.PLATFORM.set(player, PaintAttachments.PROP_CANVAS, BodyCanvas.EMPTY);
      player.m_6210_();
   }

   /**
    * Aplica un prop elegido por la UI clásica. No debe heredar el bloque capturado anteriormente.
    */
   public static void applyProp(ServerPlayer player, int propIdx, int variantIdx) {
      Services.PLATFORM.set(player, PaintAttachments.ENTITY_PROP, EntityPropSnapshot.NONE);
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, "");
      Services.PLATFORM.set(player, PaintAttachments.PROP_STATE, -1);
      applyPropInternal(player, propIdx, variantIdx);
   }

   /**
    * Aplica un disfraz capturado publicando primero su estado exacto y PROP al final. Así los clientes
    * nunca observan una forma nueva con los metadatos del disfraz anterior.
    */
   public static void applyCapturedProp(ServerPlayer player, int propIdx, int variantIdx, String sourceBlockId, int stateId) {
      Services.PLATFORM.set(player, PaintAttachments.ENTITY_PROP, EntityPropSnapshot.NONE);
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, sourceBlockId == null ? "" : sourceBlockId);
      Services.PLATFORM.set(player, PaintAttachments.PROP_STATE, stateId);
      applyPropInternal(player, propIdx, variantIdx);
   }

   /** Publica de forma atomica cualquier criatura capturada, incluido su equipo y variante NBT. */
   public static void applyCapturedEntity(ServerPlayer player, EntityPropSnapshot snapshot) {
      if (snapshot == null || !snapshot.present()) {
         clearProp(player);
         return;
      }

      Services.PLATFORM.set(player, PaintAttachments.PROP, -1);
      Services.PLATFORM.set(player, PaintAttachments.PROP_VARIANT, 0);
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, "");
      Services.PLATFORM.set(player, PaintAttachments.PROP_STATE, -1);
      Services.PLATFORM.set(player, PaintAttachments.PROP_CANVAS, BodyCanvas.EMPTY);
      Services.PLATFORM.set(player, PaintAttachments.PROP_ACT_TICK, -1000L);
      Services.PLATFORM.set(player, PaintAttachments.ENTITY_PROP, snapshot);
      Services.PLATFORM.set(player, PaintAttachments.SIZE_MINI, Boolean.FALSE);
      ArmorPaintHandler.updateShrink(player);
      applyMobSpeed(player, snapshot.typeId());
      player.m_6210_();
      FantasticAdvancements.award(player, "block");
   }

   private static void applyPropInternal(ServerPlayer player, int propIdx, int variantIdx) {
      int prop = Math.floorMod(propIdx, PropShapes.PROPS.length);
      int variant = Math.floorMod(variantIdx, PropShapes.variantCount(prop));
      Integer wasProp = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
      Integer wasVar = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP_VARIANT);
      boolean keepPaint = wasProp != null && wasProp == prop && PropShapes.sameGeometry(prop, wasVar == null ? 0 : wasVar, variant);
      if (!keepPaint) {
         int side = 64;
         int[] white = new int[side * side];
         Arrays.fill(white, -1);
         Services.PLATFORM.set(player, PaintAttachments.PROP_CANVAS, new BodyCanvas(white));
      }

      Services.PLATFORM.set(player, PaintAttachments.PROP_VARIANT, variant);
      Services.PLATFORM.set(player, PaintAttachments.PROP, prop);
      if (wasProp == null || wasProp != prop) {
         Services.PLATFORM.set(player, PaintAttachments.PROP_ACT_TICK, -1000L);
         Services.PLATFORM.set(player, PaintAttachments.SIZE_MINI, Boolean.FALSE);
         ArmorPaintHandler.updateShrink(player);
      }

      player.m_6210_();
      FantasticAdvancements.award(player, "block");
   }

   public static void handleCanvasDelta(CanvasDeltaPayload payload, ServerPlayer player) {
      if (SkinRegions.isResolution(payload.size())) {
         applyCanvasWrite(player, payload.size(), null, payload.runs());
      }
   }

   private static void applyCanvasWrite(ServerPlayer player, int size, FantasticNetwork.PixelSource full, int[] runs) {
      int mask = ChameleonArmor.coverageMask(player);
      if (mask != 0) {
         int lockedMask = ChameleonArmor.lockedMask(player);
         int[] existing = Services.PLATFORM.get(player, PaintAttachments.BODY_CANVAS).pixels();
         if (BodyCanvas.sizeOf(existing.length) != size) {
            existing = BodyCanvas.resample(existing, size);
         }

         int[] pixels = (int[])existing.clone();
         int changed = 0;
         if (full != null) {
            for (int y = 0; y < size; y++) {
               for (int x = 0; x < size; x++) {
                  changed += writePixel(pixels, existing, mask, lockedMask, size, x, y, full.get(x, y));
               }
            }
         } else {
            int count = size * size;

            for (int i = 0; i + 2 < runs.length; i += 3) {
               int start = runs[i];
               int len = runs[i + 1];
               int color = runs[i + 2];
               if (start >= 0 && len > 0 && start < count) {
                  int end = Math.min(count, start + len);

                  for (int idx = start; idx < end; idx++) {
                     changed += writePixel(pixels, existing, mask, lockedMask, size, idx % size, idx / size, color);
                  }
               }
            }
         }

         if (changed != 0) {
            Services.PLATFORM.set(player, PaintAttachments.BODY_CANVAS, new BodyCanvas(pixels));
            int scale = size / 64;
            Stats.addPixels(player, changed / (scale * scale));
            ArmorPaintHandler.storeFromBody(player);
            FantasticAdvancements.award(player, "paint");
         }
      }
   }

   private static int writePixel(int[] pixels, int[] existing, int mask, int lockedMask, int size, int x, int y, int color) {
      BodyPart part = SkinRegions.partAtScaled(x, y, size);
      if (part == null || (mask & 1 << part.ordinal()) == 0) {
         return 0;
      } else if ((lockedMask & 1 << part.ordinal()) != 0) {
         return 0;
      } else {
         int i = SkinRegions.indexIn(x, y, size);
         if (pixels[i] == color) {
            return 0;
         } else {
            pixels[i] = color;
            return existing[i] != color ? 1 : 0;
         }
      }
   }

   private interface PixelSource {
      int get(int var1, int var2);
   }
}
