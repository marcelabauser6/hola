package com.fantasticchameleon.mixin.client;

import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Luz del cuerpo acoplado tomada del aire, no del bloque en el que está metido.
 *
 * <p>Al adherirse, el cuerpo queda hundido dentro del bloque a propósito. El juego mide la luz en el
 * punto donde está la entidad, así que ese punto cae dentro de piedra o madera y devuelve oscuridad
 * total: el muñeco se veía completamente negro y parecía haber perdido la pintura. Aquí la muestra se
 * desplaza a la celda de aire contigua a la cara de acople, que es la luz que le corresponde por estar
 * asomando a ese lado.
 *
 * <p>Sólo actúa sobre jugadores marcados como acoplados; cualquier otra entidad conserva la luz normal.
 * Los nombres son SRG porque esta clase no está en el refmap del JAR base.
 */
@Mixin(EntityRenderer.class)
public abstract class AttachedLightMixin {
   @Inject(method = "m_114508_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
   private void fantastic$attachedBlockLight(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
      // Arder sigue iluminando al máximo, igual que en vanilla.
      if (entity.m_6060_()) {
         return;
      }

      BlockPos probe = fantastic$airProbe(entity, pos);
      if (probe != null) {
         cir.setReturnValue(entity.m_9236_().m_45517_(LightLayer.BLOCK, probe));
      }
   }

   @Inject(method = "m_6086_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
   private void fantastic$attachedSkyLight(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
      BlockPos probe = fantastic$airProbe(entity, pos);
      if (probe != null) {
         cir.setReturnValue(entity.m_9236_().m_45517_(LightLayer.SKY, probe));
      }
   }

   /**
    * Celda de aire contigua a la cara de acople, o null si no hay que cambiar nada.
    *
    * <p>Se avanza como máximo dos celdas: una pared normal necesita una, y una pared doble dos. Si
    * incluso así sigue tapado, se deja la luz que calcula el juego.
    */
   @Unique
   private static BlockPos fantastic$airProbe(Entity entity, BlockPos given) {
      if (!(entity instanceof Player player)
         || !Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.ATTACHED))) {
         return null;
      }

      Integer faceId = Services.PLATFORM.getOrNull(player, PaintAttachments.ATTACH_FACE);
      if (faceId == null || faceId < 0 || faceId >= Direction.values().length) {
         return null;
      }

      Direction face = Direction.m_122376_(faceId);
      // Sólo las caras verticales de un bloque hunden el cuerpo. Un prop apoyado en el suelo declara
      // la cara superior y no necesita corrección: dejarlo fuera evita iluminarlo distinto del entorno,
      // que sería una pista para quien busca.
      if (face.m_122434_() == Direction.Axis.Y) {
         return null;
      }

      Level level = player.m_9236_();
      BlockPos probe = given;
      for (int step = 0; step < 2 && level.m_8055_(probe).m_60804_(level, probe); step++) {
         probe = probe.m_121945_(face);
      }

      return level.m_8055_(probe).m_60804_(level, probe) ? null : probe;
   }
}
