package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.util.BorderGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Impide que una tolva saque items de los contenedores de una zona protegida.
 *
 * Se cubren los dos sentidos del movimiento de una tolva:
 *   - m_155552_ (suckInItems): la tolva ABSORBE del contenedor que tiene encima. Es el robo
 *     clasico: tolva justo debajo del limite inferior de la zona, bajo un cofre. Tambien lo usan
 *     las vagonetas-tolva, que implementan la misma interfaz Hopper.
 *   - m_155578_ (tryMoveItems): la tolva EMPUJA hacia el contenedor al que apunta.
 *
 * Nombres SRG con descriptor completo: es como se llaman en produccion (Forge / Mohist).
 */
@Mixin(value={HopperBlockEntity.class})
public abstract class HopperGuardMixin {

    @Inject(method={"m_155552_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private static void claimblocks$blockHopperSuck(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (level == null || hopper == null) {
                return;
            }
            BlockPos hopperPos = BlockPos.m_274561_(hopper.m_6343_(), hopper.m_6358_(), hopper.m_6446_());
            BlockPos sourcePos = hopperPos.m_121945_(Direction.UP);
            if (BorderGuard.blocksItemExtraction(level, sourcePos, hopperPos)) {
                cir.setReturnValue(false);
            }
        }
        catch (Throwable t) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo controlando una tolva (absorcion)", t);
        }
    }

    @Inject(method={"m_155578_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;Ljava/util/function/BooleanSupplier;)Z"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private static void claimblocks$blockHopperPush(Level level, BlockPos pos, BlockState state, HopperBlockEntity hopper, java.util.function.BooleanSupplier supplier, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (level == null || pos == null || state == null) {
                return;
            }
            Direction facing = (Direction)state.m_61143_((Property)HopperBlock.f_54021_);
            if (facing == null) {
                return;
            }
            if (BorderGuard.blocksItemExtraction(level, pos, pos.m_121945_(facing))) {
                cir.setReturnValue(false);
            }
        }
        catch (Throwable t) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] Fallo controlando una tolva (empuje)", t);
        }
    }
}
