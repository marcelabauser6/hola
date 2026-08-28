package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.util.BorderGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Impide que el agua o la lava entren en una zona protegida desde fuera.
 *
 * Antes solo se controlaba el cubo (BucketItem): vaciabas lava pegada al borde por fuera y el
 * flujo entraba solo. Se engancha a FlowingFluid.canSpreadTo (m_75977_), que es el punto donde el
 * fluido decide si puede extenderse a un bloque concreto y que recibe la posicion de origen Y la
 * de destino, asi que se puede distinguir "entra desde fuera" de "corre por dentro".
 *
 * El flujo interno de la propia zona no se toca.
 */
@Mixin(value={FlowingFluid.class})
public abstract class FluidGuardMixin {
    @Inject(method={"m_75977_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/material/Fluid;)Z"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$blockFluidEntering(BlockGetter getter, BlockPos fromPos, BlockState fromState, Direction direction, BlockPos toPos, BlockState toState, FluidState toFluid, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!(getter instanceof Level)) {
                return;
            }
            if (BorderGuard.blocksFluidEntry((Level)getter, fromPos, toPos)) {
                cir.setReturnValue(false);
            }
        }
        catch (Throwable t) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo controlando un fluido", t);
        }
    }
}
