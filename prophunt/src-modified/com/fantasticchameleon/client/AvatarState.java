package com.fantasticchameleon.client;

import com.fantasticchameleon.donor.DonorFeatures;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.EntityPropSnapshot;
import com.fantasticchameleon.paint.FrozenFrame;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

public final class AvatarState {
   public static final int BLOCK_POSE = 8;
   public static final int FREEZE_POSE = -1;

   private AvatarState() {
   }

   private static boolean isLocal(Player player) {
      return player == Minecraft.m_91087_().f_91074_;
   }

   @Nullable
   public static BodyCanvasData canvas(Player player) {
      int prop = prop(player);
      if (prop >= 0) {
         BodyCanvas propCanvas = BodyPaint.localPropCanvas(player.m_20148_());
         if (propCanvas == null) {
            propCanvas = Services.PLATFORM.get(player, PaintAttachments.PROP_CANVAS);
         }

         return new BodyCanvasData(player.m_20148_(), propCanvas, -1);
      } else {
         int mask = ChameleonArmor.coverageMask(player);
         if (mask == 0) {
            return null;
         } else {
            BodyCanvas canvas = BodyPaint.localCanvas(player.m_20148_());
            if (canvas == null) {
               canvas = Services.PLATFORM.get(player, PaintAttachments.BODY_CANVAS);
            }

            return new BodyCanvasData(player.m_20148_(), canvas, mask);
         }
      }
   }

   public static int coverageMask(Player player) {
      return ChameleonArmor.coverageMask(player);
   }

   public static int hiddenParts(Player player) {
      return !isLocal(player) || ClientPaintState.hiddenParts() == 0 || !WorldBrush.paintMode() && !FreeCam.active() ? 0 : ClientPaintState.hiddenParts();
   }

   public static boolean blockForm(Player player) {
      if (isLocal(player)) {
         if (PropAvatarPreview.active()) {
            return false;
         }

         if (PoseAvatarPreview.active()) {
            return PoseAvatarPreview.pose() == 8;
         }
      }

      return Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.BLOCK_FORM));
   }

   public static int blockShape(Player player) {
      Integer shape = Services.PLATFORM.getOrNull(player, PaintAttachments.BLOCK_SHAPE);
      return shape == null ? 0 : shape;
   }

   public static int prop(Player player) {
      if (isLocal(player)) {
         if (PropAvatarPreview.active()) {
            return PropAvatarPreview.prop();
         }

         if (PoseAvatarPreview.active()) {
            return -1;
         }
      }

      Integer prop = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP);
      return prop == null ? -1 : prop;
   }

   public static int propVariant(Player player) {
      if (isLocal(player) && PropAvatarPreview.active()) {
         return PropAvatarPreview.variant();
      } else {
         Integer variant = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP_VARIANT);
         return variant == null ? 0 : variant;
      }
   }

   public static boolean propFollowsLook(Player player) {
      int prop = prop(player);
      return prop < 0 || PropShapes.followsLook(prop);
   }

   /** Tiempo transcurrido desde el gesto V del prop, en ticks; negativo si nunca se ejecutó. */
   public static float propActAge(Player player, float partialTick) {
      Long tick = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP_ACT_TICK);
      return tick == null || tick < 0L ? -1.0F : (float)(player.m_9236_().m_46467_() - tick) + partialTick;
   }

   @Nullable
   public static PoseRenderData pose(Player player) {
      boolean previewing = isLocal(player) && PoseAvatarPreview.active();
      if (previewing) {
         return new PoseRenderData(true, PoseAvatarPreview.pose());
      } else if (!Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.POSING))) {
         return null;
      } else {
         Integer raw = Services.PLATFORM.getOrNull(player, PaintAttachments.POSE);
         int pose = DonorFeatures.gatePose(player.m_20148_(), raw == null ? 0 : raw);
         return new PoseRenderData(true, pose);
      }
   }

   public static boolean locked(Player player) {
      return isLocal(player) && PoseAvatarPreview.active() ? false : Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.LOCKED));
   }

   public static float lockYaw(Player player) {
      Float yaw = Services.PLATFORM.getOrNull(player, PaintAttachments.LOCK_YAW);
      return RotateGizmo.renderYaw(player, yaw == null ? 0.0F : yaw);
   }

   public static FrozenFrame frozenFrame(Player player) {
      return Services.PLATFORM.get(player, PaintAttachments.FROZEN_FRAME);
   }

   @Nullable
   public static TiltRenderData tilt(Player player) {
      if (!locked(player)) {
         return null;
      } else {
         Float pitch = Services.PLATFORM.getOrNull(player, PaintAttachments.LOCK_PITCH);
         Float roll = Services.PLATFORM.getOrNull(player, PaintAttachments.LOCK_ROLL);
         float tiltPitch = RotateGizmo.renderPitch(player, pitch == null ? 0.0F : pitch);
         float tiltRoll = RotateGizmo.renderRoll(player, roll == null ? 0.0F : roll);
         return tiltPitch == 0.0F && tiltRoll == 0.0F ? null : new TiltRenderData(tiltPitch, tiltRoll);
      }
   }

   public static boolean hideShadow(Player player) {
      if (ClientGlobalSettings.shadowHiding && ChameleonArmor.coverageMask(player) == ChameleonArmor.ALL_MASK) {
         return true;
      } else {
         PoseRenderData pose = pose(player);
         return pose != null && locked(player);
      }
   }

   public static boolean hideNameTag(Player player) {
      if (ClientGlobalSettings.nametagHiding && !RoomMenu.localSeesAllNames()) {
         Team team = player.m_5647_();
         return team != null && "fantastic_np".equals(team.m_5758_()) && ChameleonArmor.coverageMask(player) == ChameleonArmor.ALL_MASK;
      } else {
         return false;
      }
   }

   public static boolean hasEntityProp(Player player) {
      EntityPropSnapshot snapshot = Services.PLATFORM.getOrNull(player, PaintAttachments.ENTITY_PROP);
      return snapshot != null && snapshot.present();
   }

   public static EntityPropSnapshot entityProp(Player player) {
      EntityPropSnapshot snapshot = Services.PLATFORM.getOrNull(player, PaintAttachments.ENTITY_PROP);
      return snapshot == null ? EntityPropSnapshot.NONE : snapshot;
   }

   public static boolean fullyDisguised(Player player) {
      return prop(player) >= 0 || blockForm(player) || hasEntityProp(player);
   }

   public static boolean hideArmorSlot(Player player, EquipmentSlot slot) {
      if (fullyDisguised(player)) {
         return true;
      } else {
         int hidden = hiddenParts(player);
         if (hidden == 0) {
            return false;
         } else {
            return switch (slot) {
               case HEAD -> (hidden & 1) != 0;
               case CHEST -> (hidden & 14) == 14;
               case LEGS, FEET -> (hidden & 48) == 48;
               default -> false;
            };
         }
      }
   }

   public static boolean hideHeldItems(Player player) {
      return blockForm(player) || prop(player) >= 0 || hasEntityProp(player);
   }
}
