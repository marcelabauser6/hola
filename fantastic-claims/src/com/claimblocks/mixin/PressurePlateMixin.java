/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.BasePressurePlateBlock
 *  net.minecraft.world.level.block.state.BlockState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.claimblocks.mixin;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BasePressurePlateBlock.class})
public abstract class PressurePlateMixin {
    // require = 0: CraftBukkit/Mohist parchean checkPressed para sus propios eventos. Si en
    // alguna build cambia la firma, el mixin se salta en vez de tirar el arranque del servidor.
    @Inject(method={"checkPressed"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$blockVisitorPlate(Entity entity, Level level, BlockPos pos, BlockState state, int currentSignal, CallbackInfo ci) {
        if (!level.f_46443_ && entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)entity;
            Claim claim = ClaimManager.getInstance().getClaimAt(level, pos);
            if (claim != null && !claim.getFlags().publicMode) {
                boolean bypass;
                boolean bl = bypass = player.m_20310_(2) && ClaimManager.getInstance().isBypassing(player.m_20148_());
                if (!claim.canModify((Player)player) && !bypass) {
                    ci.cancel();
                }
            }
        }
    }
}

