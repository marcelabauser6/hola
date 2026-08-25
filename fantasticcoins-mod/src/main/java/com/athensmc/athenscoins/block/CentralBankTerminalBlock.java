package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.bank.BankAccess;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenCentralPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The central bank. Sets the official rates every commercial bank must compete around, and issues
 * cash into their reserves.
 *
 * <p>Operators only, at every step: placing it, opening it and acting through it.</p>
 */
public class CentralBankTerminalBlock extends TallMachineBlock {

    private static final VoxelShape SHAPE_NS = Block.box(0.0D, 0.0D, 1.0D, 16.0D, 16.0D, 15.0D);
    private static final VoxelShape SHAPE_EW = Block.box(1.0D, 0.0D, 0.0D, 15.0D, 16.0D, 16.0D);

    public CentralBankTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape shapeFor(DoubleBlockHalf half, Direction facing) {
        // One box for both halves: the console is a slab of equipment from floor to top.
        return facing.getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!BankAccess.canFoundBanks(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component
                    .translatable("message.athens_coins.central_op_only")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        ModNetwork.toPlayer(serverPlayer, S2COpenCentralPacket.of(serverPlayer, mainPos(state, pos)));
        return InteractionResult.CONSUME;
    }
}
