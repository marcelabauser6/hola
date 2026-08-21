package com.fantasticchameleon.mixin.client;

import com.fantasticchameleon.client.FreeCam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aplica la posición libre y la recorta contra colisión real en cada frame. */
@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   @Nullable
   private Entity f_90551_;

   @Shadow
   protected abstract void m_90584_(double x, double y, double z);

   @Inject(method = "setup", at = @At("TAIL"))
   private void fantastic$freeCamPosition(
      BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci
   ) {
      if (!FreeCam.active() || this.f_90551_ != Minecraft.m_91087_().f_91074_) {
         return;
      }

      Vec3 anchor = FreeCam.cameraAnchorPosition(this.f_90551_, partialTicks);
      Vec3 desired = FreeCam.cameraPosition(this.f_90551_, partialTicks);
      Vec3 ray = desired.m_82546_(anchor);
      double length = ray.m_82553_();
      if (length > 1.0E-5) {
         double clear = length;
         // Igual que la tercera persona vanilla, se prueban las ocho esquinas de un cubo pequeño;
         // un único rayo central dejaba atravesar esquinas y paneles finos.
         for (int ix = 0; ix < 2; ix++) {
            for (int iy = 0; iy < 2; iy++) {
               for (int iz = 0; iz < 2; iz++) {
                  Vec3 offset = new Vec3((ix * 2 - 1) * 0.1, (iy * 2 - 1) * 0.1, (iz * 2 - 1) * 0.1);
                  Vec3 start = anchor;
                  Vec3 end = desired.m_82549_(offset);
                  HitResult hit = this.f_90551_.m_9236_().m_45547_(
                     new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, this.f_90551_)
                  );
                  if (hit.m_6662_() == HitResult.Type.BLOCK) {
                     clear = Math.min(clear, Math.max(0.0, start.m_82554_(hit.m_82450_()) - 0.2));
                  }
               }
            }
         }
         desired = anchor.m_82549_(ray.m_82490_(clear / length));
      }

      this.m_90584_(desired.f_82479_, desired.f_82480_, desired.f_82481_);
   }
}
