package com.fantasticchameleon.mixin.client;

import com.fantasticchameleon.client.AvatarState;
import com.fantasticchameleon.client.BodyCanvasData;
import com.fantasticchameleon.client.PoseRenderData;
import com.fantasticchameleon.paint.BodyPart;
import com.fantasticchameleon.paint.FrozenFrame;
import com.fantasticchameleon.pose.BlockShapes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({HumanoidModel.class})
public abstract class HumanoidModelMixin {
   @Shadow
   public ModelPart f_102808_;
   @Shadow
   public ModelPart f_102810_;
   @Shadow
   public ModelPart f_102814_;
   @Shadow
   public ModelPart f_102813_;
   @Shadow
   public ModelPart f_102812_;
   @Shadow
   public ModelPart f_102811_;
   @Shadow
   public ModelPart f_102809_;
   @Shadow
   public boolean f_102817_;
   @Unique
   private float[] fantastic$baseOffsets;

   public HumanoidModelMixin() {
   }

   @Unique
   private static Player fantastic$posedPlayer(Object entity) {
      if (!(entity instanceof Player player)) {
         return null;
      } else {
         PoseRenderData pose = AvatarState.pose(player);
         return pose != null && pose.posed() ? player : null;
      }
   }

   @ModifyVariable(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private float fantastic$limbSwing(
      float value, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      Player player = fantastic$posedPlayer(entity);
      if (player == null) {
         return value;
      } else {
         PoseRenderData pose = AvatarState.pose(player);
         return pose.pose() == -1 ? AvatarState.frozenFrame(player).walkPos() : 0.0F;
      }
   }

   @ModifyVariable(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = @At("HEAD"),
      ordinal = 1,
      argsOnly = true
   )
   private float fantastic$limbSwingAmount(
      float value, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      Player player = fantastic$posedPlayer(entity);
      if (player == null) {
         return value;
      } else {
         PoseRenderData pose = AvatarState.pose(player);
         return pose.pose() == -1 ? AvatarState.frozenFrame(player).walkSpeed() : 0.0F;
      }
   }

   @ModifyVariable(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = @At("HEAD"),
      ordinal = 2,
      argsOnly = true
   )
   private float fantastic$ageInTicks(
      float value, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      Player player = fantastic$posedPlayer(entity);
      if (player == null) {
         return value;
      } else {
         PoseRenderData pose = AvatarState.pose(player);
         return pose.pose() == -1 ? AvatarState.frozenFrame(player).age() : 0.0F;
      }
   }

   @ModifyVariable(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = @At("HEAD"),
      ordinal = 3,
      argsOnly = true
   )
   private float fantastic$netHeadYaw(
      float value, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      Player player = fantastic$posedPlayer(entity);
      if (player != null && AvatarState.locked(player)) {
         PoseRenderData pose = AvatarState.pose(player);
         if (pose.pose() == -1) {
            FrozenFrame frame = AvatarState.frozenFrame(player);
            return Mth.m_14177_(frame.headYaw() - AvatarState.lockYaw(player));
         } else {
            return 0.0F;
         }
      } else {
         return value;
      }
   }

   @ModifyVariable(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = @At("HEAD"),
      ordinal = 4,
      argsOnly = true
   )
   private float fantastic$headPitch(
      float value, LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      Player player = fantastic$posedPlayer(entity);
      if (player != null && AvatarState.locked(player)) {
         PoseRenderData pose = AvatarState.pose(player);
         return pose.pose() == -1 ? AvatarState.frozenFrame(player).pitch() : 0.0F;
      } else {
         return value;
      }
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = {@At("HEAD")}
   )
   private void fantastic$resetBlock(
      LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
   ) {
      if (entity instanceof Player player) {
         this.f_102808_.f_233553_ = 1.0F;
         this.f_102808_.f_233554_ = 1.0F;
         this.f_102808_.f_233555_ = 1.0F;
         this.f_102808_.f_104200_ = 0.0F;
         this.f_102808_.f_104201_ = 0.0F;
         this.f_102808_.f_104202_ = 0.0F;
         this.f_102808_.f_104207_ = true;
         this.fantastic$restoreOffsets();
         this.f_102810_.f_104207_ = true;
         this.f_102812_.f_104207_ = true;
         this.f_102811_.f_104207_ = true;
         this.f_102814_.f_104207_ = true;
         this.f_102813_.f_104207_ = true;
         this.f_102809_.f_104207_ = true;
      }
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = {@At("TAIL")}
   )
   private void fantastic$applyPose(
      LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
   ) {
      if (entity instanceof Player player) {
         int headBit = 1 << BodyPart.HEAD.ordinal();
         BodyCanvasData headData = AvatarState.canvas(player);
         boolean headCanvas = headData != null && (headData.enabledMask() & headBit) != 0;
         boolean blockDisguise = AvatarState.blockForm(player);
         int propIdx = AvatarState.prop(player);
         if (propIdx >= 0) {
            this.f_102809_.f_104207_ = false;
            this.f_102808_.f_104207_ = false;
            this.f_102810_.f_104207_ = false;
            this.f_102812_.f_104207_ = false;
            this.f_102811_.f_104207_ = false;
            this.f_102814_.f_104207_ = false;
            this.f_102813_.f_104207_ = false;
         } else {
            this.f_102809_.f_104207_ = !headCanvas && !blockDisguise;
            if (blockDisguise) {
               this.f_102810_.f_104207_ = false;
               this.f_102812_.f_104207_ = false;
               this.f_102811_.f_104207_ = false;
               this.f_102814_.f_104207_ = false;
               this.f_102813_.f_104207_ = false;
               this.f_102808_.f_104207_ = true;
               straighten(this.f_102808_);
               this.f_102808_.f_104200_ = 0.0F;
               this.f_102808_.f_104202_ = 0.0F;
               this.f_102808_.f_104201_ = 24.0F;
               BlockShapes.Shape shape = BlockShapes.of(AvatarState.blockShape(player));
               this.f_102808_.f_233553_ = shape.scaleXZ();
               this.f_102808_.f_233554_ = shape.scaleY();
               this.f_102808_.f_233555_ = shape.scaleXZ();
            } else {
               int hidden = AvatarState.hiddenParts(player);
               if (hidden != 0) {
                  this.fantastic$applyHidden(hidden);
               }

               PoseRenderData pose = AvatarState.pose(player);
               if (pose != null && pose.posed() && pose.pose() != -1) {
                  straighten(this.f_102808_);
                  boolean sneak = pose.pose() == 3;
                  if (!sneak) {
                     straighten(this.f_102812_);
                     straighten(this.f_102811_);
                     straighten(this.f_102814_);
                     straighten(this.f_102813_);
                  }

                  switch (pose.pose()) {
                     case 1:
                        this.f_102814_.f_104203_ = -1.5708F;
                        this.f_102813_.f_104203_ = -1.5708F;
                        break;
                     case 2:
                        this.f_102812_.f_104205_ = -1.2F;
                        this.f_102811_.f_104205_ = 1.2F;
                        this.f_102814_.f_104205_ = -0.25F;
                        this.f_102813_.f_104205_ = 0.25F;
                        break;
                     case 3:
                        this.f_102812_.f_104203_ = -1.4F;
                        this.f_102811_.f_104203_ = -1.4F;
                        break;
                     case 4:
                        this.f_102812_.f_104205_ = -1.5708F;
                        this.f_102811_.f_104205_ = 1.5708F;
                        break;
                     case 5:
                        this.f_102812_.f_104203_ = -3.0F;
                        this.f_102812_.f_104205_ = -0.25F;
                        this.f_102811_.f_104203_ = -3.0F;
                        this.f_102811_.f_104205_ = 0.25F;
                        break;
                     case 6:
                        this.f_102812_.f_104203_ = -2.9F;
                        this.f_102814_.f_104203_ = 0.3F;
                        this.f_102813_.f_104203_ = 0.3F;
                        break;
                     case 7:
                        this.f_102812_.f_104203_ = -1.4F;
                        this.f_102811_.f_104203_ = -1.4F;
                        this.f_102814_.f_104203_ = -1.9F;
                        this.f_102813_.f_104203_ = -1.9F;
                     case 8:
                     default:
                        break;
                     case 9:
                        this.f_102814_.f_104203_ = 1.5708F;
                        this.f_102813_.f_104203_ = 1.5708F;
                        this.f_102812_.f_104203_ = -0.15F;
                        this.f_102811_.f_104203_ = -0.15F;
                        break;
                     case 10:
                        this.f_102811_.f_104203_ = -2.6F;
                        this.f_102811_.f_104205_ = -0.45F;
                        this.f_102812_.f_104203_ = 0.35F;
                        break;
                     case 11:
                        this.f_102811_.f_104203_ = -1.5708F;
                        this.f_102812_.f_104203_ = 0.35F;
                        break;
                     case 12:
                        this.f_102814_.f_104203_ = -1.5708F;
                        this.f_102813_.f_104203_ = -1.5708F;
                        this.f_102812_.f_104203_ = 0.5F;
                        this.f_102811_.f_104203_ = 0.5F;
                        break;
                     case 13:
                        this.f_102812_.f_104203_ = -1.5708F;
                        this.f_102811_.f_104203_ = -1.5708F;
                        this.f_102814_.f_104203_ = -1.5708F;
                        this.f_102813_.f_104203_ = -1.5708F;
                        this.f_102814_.f_104205_ = -0.22F;
                        this.f_102813_.f_104205_ = 0.22F;
                        break;
                     case 14:
                        this.f_102814_.f_104203_ = -1.9F;
                        this.f_102813_.f_104203_ = -1.9F;
                        this.f_102812_.f_104203_ = -1.6F;
                        this.f_102812_.f_104205_ = 0.35F;
                        this.f_102811_.f_104203_ = -1.6F;
                        this.f_102811_.f_104205_ = -0.35F;
                        break;
                     case 15:
                        this.f_102812_.f_104205_ = -1.2F;
                        this.f_102811_.f_104205_ = 1.2F;
                        this.f_102814_.f_104205_ = -0.25F;
                        this.f_102813_.f_104205_ = 0.25F;
                        break;
                     case 16:
                        this.f_102812_.f_104203_ = 3.1416F;
                        this.f_102811_.f_104203_ = 3.1416F;
                        this.f_102814_.f_104205_ = -0.15F;
                        this.f_102813_.f_104205_ = 0.15F;
                        break;
                     case 17:
                        this.f_102811_.f_104203_ = -2.2F;
                        this.f_102811_.f_104205_ = -0.9F;
                        this.f_102812_.f_104203_ = -2.5F;
                        this.f_102812_.f_104205_ = -1.0F;
                        this.f_102808_.f_104203_ = 0.5F;
                        this.f_102808_.f_104205_ = -0.25F;
                        break;
                     case 18:
                        this.f_102812_.f_104203_ = 1.85F;
                        this.f_102812_.f_104205_ = -0.18F;
                        this.f_102811_.f_104203_ = 1.85F;
                        this.f_102811_.f_104205_ = 0.18F;
                        this.f_102814_.f_104203_ = 0.75F;
                        this.f_102813_.f_104203_ = -0.95F;
                        this.f_102808_.f_104203_ = 0.45F;
                        break;
                     case 19:
                        this.f_102810_.f_104207_ = false;
                        this.f_102808_.f_104201_ = 24.0F;
                        this.f_102808_.f_104202_ = -6.0F;
                        this.f_102811_.f_104200_ = 0.0F;
                        this.f_102811_.f_104201_ = 22.0F;
                        this.f_102811_.f_104202_ = 0.0F;
                        this.f_102811_.f_104203_ = 1.5708F;
                        this.f_102811_.f_104204_ = -0.12F;
                        this.f_102812_.f_104200_ = 0.0F;
                        this.f_102812_.f_104201_ = 22.0F;
                        this.f_102812_.f_104202_ = 12.0F;
                        this.f_102812_.f_104203_ = 1.5708F;
                        this.f_102812_.f_104204_ = 0.12F;
                        this.f_102813_.f_104200_ = 0.0F;
                        this.f_102813_.f_104201_ = 22.0F;
                        this.f_102813_.f_104202_ = 22.0F;
                        this.f_102813_.f_104203_ = 1.5708F;
                        this.f_102813_.f_104204_ = -0.12F;
                        this.f_102814_.f_104200_ = 0.0F;
                        this.f_102814_.f_104201_ = 22.0F;
                        this.f_102814_.f_104202_ = 34.0F;
                        this.f_102814_.f_104203_ = 1.5708F;
                        this.f_102814_.f_104204_ = 0.12F;
                        break;
                     case 20:
                        this.f_102812_.f_104200_ = 1.0F;
                        this.f_102811_.f_104200_ = -1.0F;
                        this.f_102810_.f_104201_ = -12.0F;
                        this.f_102808_.f_104201_ = -12.0F;
                        break;
                     case 21:
                        this.f_102808_.f_104200_ = 8.0F;
                        this.f_102808_.f_104201_ = 24.0F;
                        this.f_102808_.f_104202_ = -7.0F;
                        this.f_102808_.f_104204_ = 0.9F;
                        this.f_102810_.f_104200_ = -2.0F;
                        this.f_102810_.f_104201_ = 22.0F;
                        this.f_102810_.f_104202_ = 3.0F;
                        this.f_102810_.f_104203_ = 1.5708F;
                        this.f_102810_.f_104204_ = 0.35F;
                        this.f_102812_.f_104200_ = 15.0F;
                        this.f_102812_.f_104201_ = 22.0F;
                        this.f_102812_.f_104202_ = 5.0F;
                        this.f_102812_.f_104203_ = 1.5708F;
                        this.f_102812_.f_104204_ = 1.15F;
                        this.f_102811_.f_104200_ = -13.0F;
                        this.f_102811_.f_104201_ = 22.0F;
                        this.f_102811_.f_104202_ = -9.0F;
                        this.f_102811_.f_104203_ = 1.5708F;
                        this.f_102811_.f_104204_ = -0.55F;
                        this.f_102814_.f_104200_ = 3.0F;
                        this.f_102814_.f_104201_ = 22.0F;
                        this.f_102814_.f_104202_ = 14.0F;
                        this.f_102814_.f_104203_ = 1.5708F;
                        this.f_102814_.f_104204_ = 2.35F;
                        this.f_102813_.f_104200_ = -8.0F;
                        this.f_102813_.f_104201_ = 22.0F;
                        this.f_102813_.f_104202_ = -14.0F;
                        this.f_102813_.f_104203_ = 1.5708F;
                        this.f_102813_.f_104204_ = -2.1F;
                        break;
                     case 22:
                        this.f_102812_.f_104207_ = false;
                        this.f_102811_.f_104207_ = false;
                        this.f_102814_.f_104207_ = false;
                        this.f_102813_.f_104207_ = false;
                        break;
                     case 23:
                        this.f_102810_.f_104207_ = false;
                        this.f_102812_.f_104207_ = false;
                        this.f_102811_.f_104207_ = false;
                        this.f_102814_.f_104207_ = false;
                        this.f_102813_.f_104207_ = false;
                        this.f_102808_.f_104201_ = 24.0F;
                        break;
                     case 24:
                        this.f_102812_.f_104203_ = -1.5708F;
                        this.f_102812_.f_104205_ = 1.35F;
                        this.f_102812_.f_104202_ = -1.5F;
                        this.f_102811_.f_104203_ = -1.5708F;
                        this.f_102811_.f_104205_ = -1.35F;
                        break;
                     case 25:
                        this.f_102811_.f_104203_ = -1.9F;
                        this.f_102811_.f_104205_ = -0.95F;
                        this.f_102812_.f_104205_ = 0.05F;
                        break;
                     case 26:
                        this.f_102811_.f_104203_ = -2.35F;
                        this.f_102811_.f_104205_ = -0.3F;
                        this.f_102808_.f_104203_ = 0.45F;
                        this.f_102812_.f_104205_ = 0.05F;
                        break;
                     case 27:
                        this.f_102814_.f_104203_ = -1.5708F;
                        this.f_102813_.f_104203_ = -1.5708F;
                        this.f_102811_.f_104203_ = -2.05F;
                        this.f_102811_.f_104205_ = -0.25F;
                        this.f_102812_.f_104203_ = -1.3F;
                        this.f_102812_.f_104205_ = 0.2F;
                        this.f_102808_.f_104203_ = 0.3F;
                        break;
                     case 28:
                        this.f_102811_.f_104205_ = -1.15F;
                        this.f_102811_.f_104203_ = 0.35F;
                        this.f_102812_.f_104205_ = 1.15F;
                        this.f_102812_.f_104203_ = 0.35F;
                        break;
                     case 29:
                        this.f_102810_.f_104203_ = 1.05F;
                        this.f_102808_.f_104203_ = 0.35F;
                        this.f_102812_.f_104203_ = 0.85F;
                        this.f_102811_.f_104203_ = 0.85F;
                        this.f_102812_.f_104201_ = 4.0F;
                        this.f_102811_.f_104201_ = 4.0F;
                        break;
                     case 30:
                        // Meccha: extremidades recogidas y casi solapadas con el torso para que la
                        // silueta acostada no revele inmediatamente la geometria de un jugador.
                        this.f_102812_.f_104200_ = -2.5F;
                        this.f_102811_.f_104200_ = 2.5F;
                        this.f_102812_.f_104201_ = 2.0F;
                        this.f_102811_.f_104201_ = 2.0F;
                        this.f_102814_.f_104200_ = -1.0F;
                        this.f_102813_.f_104200_ = 1.0F;
                        this.f_102814_.f_104203_ = 0.0F;
                        this.f_102813_.f_104203_ = 0.0F;
                  }
               }
            }
         }
      }
   }

   @Unique
   private void fantastic$restoreOffsets() {
      if (this.fantastic$baseOffsets == null) {
         this.fantastic$baseOffsets = new float[]{
            this.f_102810_.f_104200_,
            this.f_102810_.f_104201_,
            this.f_102810_.f_104202_,
            this.f_102812_.f_104200_,
            this.f_102812_.f_104201_,
            this.f_102812_.f_104202_,
            this.f_102811_.f_104200_,
            this.f_102811_.f_104201_,
            this.f_102811_.f_104202_,
            this.f_102814_.f_104200_,
            this.f_102814_.f_104201_,
            this.f_102814_.f_104202_,
            this.f_102813_.f_104200_,
            this.f_102813_.f_104201_,
            this.f_102813_.f_104202_
         };
      } else {
         float[] b = this.fantastic$baseOffsets;
         this.f_102810_.f_104200_ = b[0];
         this.f_102810_.f_104201_ = b[1];
         this.f_102810_.f_104202_ = b[2];
         this.f_102812_.f_104200_ = b[3];
         this.f_102812_.f_104201_ = b[4];
         this.f_102812_.f_104202_ = b[5];
         this.f_102811_.f_104200_ = b[6];
         this.f_102811_.f_104201_ = b[7];
         this.f_102811_.f_104202_ = b[8];
         this.f_102814_.f_104200_ = b[9];
         this.f_102814_.f_104201_ = b[10];
         this.f_102814_.f_104202_ = b[11];
         this.f_102813_.f_104200_ = b[12];
         this.f_102813_.f_104201_ = b[13];
         this.f_102813_.f_104202_ = b[14];
      }
   }

   private static void straighten(ModelPart part) {
      part.f_104203_ = 0.0F;
      part.f_104204_ = 0.0F;
      part.f_104205_ = 0.0F;
   }

   private void fantastic$applyHidden(int mask) {
      if ((mask & 1) != 0) {
         this.f_102808_.f_104207_ = false;
         this.f_102809_.f_104207_ = false;
      }

      if ((mask & 2) != 0) {
         this.f_102810_.f_104207_ = false;
      }

      if ((mask & 4) != 0) {
         this.f_102812_.f_104207_ = false;
      }

      if ((mask & 8) != 0) {
         this.f_102811_.f_104207_ = false;
      }

      if ((mask & 16) != 0) {
         this.f_102814_.f_104207_ = false;
      }

      if ((mask & 32) != 0) {
         this.f_102813_.f_104207_ = false;
      }
   }
}
