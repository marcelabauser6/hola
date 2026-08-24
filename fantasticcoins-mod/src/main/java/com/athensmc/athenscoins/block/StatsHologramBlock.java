package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccess;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.item.ModItems;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenHologramPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A projector that shows the server's economy as floating text above itself.
 *
 * <p>This exists because the statistics were only ever a private admin screen. An economy is something
 * a whole server participates in, and the numbers that describe it belong somewhere the players can
 * walk past and read - in the bank's lobby, in the market square - not behind a command only operators
 * can run. The screen is now the editor for this block rather than the destination.</p>
 *
 * <p>Kept low and flat on purpose: it is a plinth for the text above it, and anything taller would
 * compete with the hologram it is projecting. Operators only, like the terminals, because a projector
 * publishes the whole server's finances.</p>
 */
public class StatsHologramBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 5.0D, 14.0D);

    public StatsHologramBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The facing matters even though the block is symmetrical: a hologram set to stay flat instead
        // of turning to face the viewer is oriented by it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatsHologramBlockEntity(pos, state);
    }

    /**
     * The projector refreshes itself on a server ticker.
     *
     * <p>A ticker rather than a registry of positions swept from the mod's own tick handler: Minecraft
     * already knows which block entities are loaded and takes them off the list when their chunk goes
     * away. A hand-kept set of projector positions would be one more thing to get wrong on chunk
     * unload, and a leaked entry there is a hologram that keeps being updated after it stops
     * existing.</p>
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof StatsHologramBlockEntity projector) {
                projector.serverTick(tickLevel);
            }
        };
    }

    /**
     * Opens the editor.
     *
     * <p>The packet carries only the position. The configuration and the figures are already on the
     * client, in the projector's own block entity, because that is how a hologram gets drawn at all -
     * sending them again would be a second copy of the same state, and the two could disagree about
     * what the player is editing.</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // Who may edit a board follows who it belongs to. A bank's own staff may rearrange their bank's
        // board; a server-wide board is the central bank's, so it takes the licence that opens that.
        if (!canEdit(serverPlayer, level, pos)) {
            serverPlayer.sendSystemMessage(Component
                    .translatable("message.athens_coins.hologram_op_only")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        ModNetwork.toPlayer(serverPlayer, new S2COpenHologramPacket(pos));
        return InteractionResult.CONSUME;
    }

    /**
     * Whether this player may edit the board at this position.
     *
     * <p>Shared with the save packet, which has to reach the same verdict: a client that can be told "no"
     * by the block and "yes" by the packet is a client that can edit anything by sending the packet
     * directly.</p>
     */
    public static boolean canEdit(ServerPlayer player, BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StatsHologramBlockEntity board)) {
            return BankAccess.isOperator(player);
        }
        if (board.global()) {
            return BankAccess.canFoundBanks(player);
        }
        Bank bank = BankData.get(player.server).bank(board.bankId());
        return bank == null ? BankAccess.canFoundBanks(player)
                : BankAccess.canOpenTerminal(player, bank);
    }

    /** Carries the branding from the item onto the placed board. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof StatsHologramBlockEntity board) {
            board.applyFrom(stack);
        }
    }

    /** Middle-click pick block keeps the branding. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof StatsHologramBlockEntity board) {
            board.applyTo(stack);
        }
        return stack;
    }

    /**
     * Breaking a board returns it still branded.
     *
     * <p>Without this the loot table hands back a blank one, so moving a bank's board two blocks along
     * would quietly turn it into a server-wide board - the same figures, a different meaning, and nothing
     * on screen to say it had happened.</p>
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof StatsHologramBlockEntity board && !board.global()) {
            for (ItemStack stack : drops) {
                if (stack.is(ModItems.STATS_HOLOGRAM_ITEM.get())) {
                    board.applyTo(stack);
                }
            }
        }
        return drops;
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
