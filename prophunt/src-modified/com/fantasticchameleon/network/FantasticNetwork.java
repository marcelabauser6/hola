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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FantasticNetwork {
   private static final Map<UUID, Long> LAST_PROVOKE = new ConcurrentHashMap<>();
   private static final long PROVOKE_COOLDOWN = 30L;
   private static final int[] ACTION_COOLDOWN_TICKS = new int[]{20, 20, 5, 2, 4, 2, 10, 2, 1, 1, 2, 1};
   private static final int ACTION_BURST_FREE = 3;
   private static final int ACTION_RESET_TICKS = 100;
   private static final Map<UUID, long[]> ACTION_LAST = new ConcurrentHashMap<>();
   public static final ResourceLocation LOCK_MODIFIER_ID = new ResourceLocation("fantastic_chameleon", "pose_lock");
   public static final int POSE_COUNT = PoseDefs.count();
   public static final int BLOCK_POSE = 8;
   public static final int FREEZE_POSE = -1;
   private static final Map<UUID, Vec3> SAFE_POS = new ConcurrentHashMap<>();

   private FantasticNetwork() {
   }

   public static Optional<Component> versionMismatch(String serverVersion) {
      String mine = FantasticChameleon.VERSION;
      return serverVersion != null && !serverVersion.isEmpty() && !serverVersion.equals(mine)
         ? Optional.of(Component.m_237110_("fantastic.disconnect.version", new Object[]{serverVersion, mine}))
         : Optional.empty();
   }

   public static void handleArenaCorner(ArenaCornerPayload payload, ServerPlayer sp) {
      Rooms.setArenaCornerAt(sp, payload.which(), payload.pos());
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
      Rooms.setConfig(sp, payload.field(), payload.value(), true);
   }

   public static void handleEditorAction(EditorActionPayload payload, ServerPlayer sp) {
      if (!throttled(sp, 11)) {
         EditorActions.handle(sp, payload.action(), payload.a(), payload.b(), payload.value());
      }
   }

   public static void handleRoomAction(RoomActionPayload payload, ServerPlayer sp) {
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

   public static void handleLock(LockPayload payload, ServerPlayer player) {
      if (!throttled(player, 0)) {
         if (!payload.locked()) {
            if (Services.PLATFORM.get(player, PaintAttachments.POSE) == -1) {
               resetPose(player);
            } else {
               Services.PLATFORM.set(player, PaintAttachments.LOCKED, false);
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
                  // Un cliente antiguo puede seguir enviando freeze=true. En Meccha se fuerza la
                  // pose plana real para no conservar una FrozenFrame con silueta humana.
                  Services.PLATFORM.set(player, PaintAttachments.POSE, 30);
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
            if (propNow != null && propNow >= 0 && PropHunt.isPropHunt(player)) {
               // Un prop tiene que quedar centrado en su celda, no pegado a la pared como la
               // silueta pintada del modo clasico. Y los props de bloque se colocan siempre a yaw 0:
               // su orientacion real ya va dentro del variant, asi que girar el cuerpo ademas los
               // dejaria torcidos respecto al mundo. Las criaturas si conservan hacia donde miran.
               yaw = PropShapes.followsLook(propNow) ? yaw : 0.0F;
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
         // La pose plana ya descansa sobre el suelo; además se aproxima a la pared horizontal más
         // cercana sin atravesarla. reanchor hace que LockTick conserve exactamente esa adhesión.
         Vec3 target = BodyClip.snapToWall(player.m_9236_(), player.m_20182_(), yaw, Math.max(0, pose));
         Vec3 delta = target.m_82546_(player.m_20182_());
         if (player.m_9236_().m_45756_(player, player.m_20191_().m_82383_(delta))) {
            LockTick.reanchor(sp, target);
         } else {
            LockTick.reanchor(sp, player.m_20182_());
         }
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

   public static void clearProp(ServerPlayer player) {
      Services.PLATFORM.set(player, PaintAttachments.PROP, -1);
      Services.PLATFORM.set(player, PaintAttachments.PROP_VARIANT, 0);
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, "");
      Services.PLATFORM.set(player, PaintAttachments.PROP_STATE, -1);
      Services.PLATFORM.set(player, PaintAttachments.PROP_ACT_TICK, -1000L);
      Services.PLATFORM.set(player, PaintAttachments.PROP_CANVAS, BodyCanvas.EMPTY);
      player.m_6210_();
   }

   /**
    * Aplica un prop elegido por la UI clásica. No debe heredar el bloque capturado anteriormente.
    */
   public static void applyProp(ServerPlayer player, int propIdx, int variantIdx) {
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, "");
      Services.PLATFORM.set(player, PaintAttachments.PROP_STATE, -1);
      applyPropInternal(player, propIdx, variantIdx);
   }

   /**
    * Aplica un disfraz capturado publicando primero su estado exacto y PROP al final. Así los clientes
    * nunca observan una forma nueva con los metadatos del disfraz anterior.
    */
   public static void applyCapturedProp(ServerPlayer player, int propIdx, int variantIdx, String sourceBlockId, int stateId) {
      Services.PLATFORM.set(player, PaintAttachments.PROP_SOURCE, sourceBlockId == null ? "" : sourceBlockId);
      Services.PLATFORM.set(player, PaintAttachments.PROP_STATE, stateId);
      applyPropInternal(player, propIdx, variantIdx);
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
