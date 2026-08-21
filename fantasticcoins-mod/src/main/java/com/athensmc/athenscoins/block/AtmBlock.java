package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.menu.AtmMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * A bank's cash machine. Right-click to swap coins for cash at that bank's rates.
 *
 * <p>Machines are issued by a bank terminal and carry their issuer's identity, so an unbranded one
 * cannot be used: there is no bank behind it to hold the money.</p>
 */
public class AtmBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 2.0D, 15.0D, 16.0D, 14.0D);
    private static final VoxelShape SHAPE_EW = Block.box(2.0D, 0.0D, 1.0D, 14.0D, 16.0D, 15.0D);

    public AtmBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AtmBlockEntity(pos, state);
    }

    /** Carries the branding from the item onto the placed machine. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                           ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof AtmBlockEntity atm) {
            atm.applyFrom(stack);
        }
    }

    /** Keeps the branding when the machine is broken and picked back up. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof AtmBlockEntity atm && !atm.unbranded()) {
            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
            tag.putUUID(AtmBlockEntity.TAG_BANK, atm.bankId());
            tag.putString(AtmBlockEntity.TAG_BANK_NAME, atm.bankName());
            tag.putInt(AtmBlockEntity.TAG_BANK_COLOR, atm.themeColor());
        }
        return stack;
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
        if (!(level.getBlockEntity(pos) instanceof AtmBlockEntity atm) || atm.unbranded()) {
            serverPlayer.sendSystemMessage(Component
                    .translatable("message.athens_coins.atm_unbranded")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        Bank bank = atm.bank(serverPlayer.server);
        if (bank == null) {
            serverPlayer.sendSystemMessage(Component
                    .translatable("message.athens_coins.atm_bank_gone")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        if (!BankManager.hasAccount(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component
                    .translatable("message.athens_coins.atm_needs_account")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        open(serverPlayer, level, pos, bank);
        return InteractionResult.CONSUME;
    }

    public static void open(ServerPlayer player, Level level, BlockPos pos, Bank bank) {
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, owner) -> createMenu(containerId, inventory, access, bank),
                Component.literal(bank.name()));
        NetworkHooks.openScreen(player, provider, buffer -> AtmMenu.writeState(buffer, player, bank));
    }

    private static AbstractContainerMenu createMenu(int containerId, Inventory inventory,
                                                    ContainerLevelAccess access, Bank bank) {
        return new AtmMenu(containerId, inventory, access, bank);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
