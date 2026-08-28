/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.DispenserBlock
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.claimblocks.mixin;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={DispenserBlock.class})
public abstract class DispenserBlockMixin {
    // require = 0: igual que en PressurePlateMixin, CraftBukkit tambien parchea dispenseFrom.
    @Inject(method={"dispenseFrom"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$blockCrossClaimDispense(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        Direction facing;
        BlockState state = level.m_8055_(pos);
        try {
            facing = (Direction)state.m_61143_((Property)DispenserBlock.f_52659_);
        }
        catch (Exception var10) {
            return;
        }
        BlockPos target = pos.m_121945_(facing);
        ClaimManager mgr = ClaimManager.getInstance();
        Claim self = mgr.getClaimAt((Level)level, pos);
        Claim tgt = mgr.getClaimAt((Level)level, target);
        if (!DispenserBlockMixin.sameClaim(self, tgt) && (DispenserBlockMixin.protectsBuilding(tgt) || DispenserBlockMixin.protectsBuilding(self))) {
            ci.cancel();
        }
    }

    private static boolean sameClaim(Claim a, Claim b) {
        if (a == null && b == null) {
            return true;
        }
        return a != null && b != null ? a.getClaimId().equals(b.getClaimId()) : false;
    }

    private static boolean protectsBuilding(Claim c) {
        return c == null ? false : c.getFlags().publicMode || c.getFlags().blockBuilding;
    }
}

