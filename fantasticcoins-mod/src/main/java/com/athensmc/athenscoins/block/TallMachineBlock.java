package com.athensmc.athenscoins.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A machine two blocks tall, facing the player who placed it.
 *
 * <p>All three of this mod's machines are furniture a person stands at: a cash machine, a teller
 * counter and the central bank's console. Inside one block they were shorter than the player using
 * them, which is what "they are far too small, make the cash machine life-size" was about. A single
 * block cannot be taller than a block, so each machine occupies two: the lower half is the one that
 * exists as far as the rest of the mod is concerned - it holds the block entity, the bank seat and the
 * menu - and the upper half is scenery that follows it.</p>
 *
 * <p>The two halves are kept in step the way vanilla keeps a door in step. {@code updateShape}
 * destroys a half whose partner has gone, which is also what makes breaking the top give you the
 * item: {@code Block.updateOrDestroy} destroys the orphan <em>with</em> drops. So only the lower half
 * may ever drop anything, or a player would get two machines for one. In creative the lower half is
 * removed silently instead, since creative breaking is not supposed to yield an item at all.</p>
 *
 * <p>Subclasses see two hooks. {@link #shapeFor} supplies the collision box per half, and
 * {@link #mainPos} turns whichever half was clicked into the position everything else should use -
 * every {@code use} override starts by calling it, so clicking the screen at eye level does the same
 * thing as clicking the keypad.</p>
 */
public abstract class TallMachineBlock extends HorizontalDirectionalBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    protected TallMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** The collision box for one half, in block pixels, already oriented for the given facing. */
    protected abstract VoxelShape shapeFor(DoubleBlockHalf half, Direction facing);

    /**
     * The lower half's position, whichever half was clicked.
     *
     * <p>The seat, the block entity and the container access all live there. Without this, clicking
     * the top of a terminal would look up a bank at a position that has never had one.</p>
     */
    public static BlockPos mainPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** True for the half that owns the machine's state. */
    public static boolean isMain(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return shapeFor(state.getValue(HALF), state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Refuses the placement outright when there is no headroom.
     *
     * <p>Returning {@code null} makes the item do nothing and keeps itself, which is the honest
     * outcome. Placing only the lower half would leave a machine that looks broken and, for the
     * terminals, would have already seated a bank by the time anyone noticed.</p>
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        // The upper half first: subclasses do their own work in their override after calling super,
        // and a bank seated before the machine is whole would be seated on half a machine.
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return super.canSurvive(state, level, pos);
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Keeps the pair together, asymmetrically and on purpose.
     *
     * <p>A head with nothing under it is deleted: it is scenery, and scenery with no machine attached
     * is a floating box. A machine with no head is <em>repaired</em> instead, on the next tick.</p>
     *
     * <p>The asymmetry is what makes the change safe to ship. Every machine already standing in a world
     * was saved as a one-block block with no {@code half} in its state, so it loads back as a lower
     * half with nothing above it. Treating that as "broken, delete it" would quietly remove every ATM
     * and terminal the player had built the first time anything near them updated. Growing the missing
     * head instead upgrades them in place. The same rule then covers a head knocked off by anything
     * else - a piston, an explosion, a stray creative click: the machine is the lower half, and it puts
     * its own head back on.</p>
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean partnerIsUpper = half == DoubleBlockHalf.LOWER;
        boolean towardsPartner = direction == (partnerIsUpper ? Direction.UP : Direction.DOWN);
        if (towardsPartner && !isPartner(neighbor, half)) {
            if (partnerIsUpper) {
                level.scheduleTick(pos, this, 1);
            } else {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    private boolean isPartner(BlockState neighbor, DoubleBlockHalf half) {
        return neighbor.is(this) && neighbor.getValue(HALF) != half;
    }

    /** Also checked on placement, so a machine spawned by command or structure grows its head too. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (isMain(state) && !isPartner(level.getBlockState(pos.above()), DoubleBlockHalf.LOWER)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /**
     * Puts the head back, or gives up and drops the machine.
     *
     * <p>Giving up is the case where something solid has taken the space above. Destroying with drops
     * rather than leaving a headless machine keeps the rule "a machine is always two blocks tall" true,
     * which is what every other method here relies on.</p>
     */
    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                     net.minecraft.util.RandomSource random) {
        if (!isMain(state)) {
            return;
        }
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (isPartner(aboveState, DoubleBlockHalf.LOWER)) {
            return;
        }
        if (aboveState.canBeReplaced()) {
            level.setBlock(above, state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        } else {
            level.destroyBlock(pos, true);
        }
    }

    /**
     * Only the lower half drops anything.
     *
     * <p>Both halves are the same block, so both match the same loot table. Left alone, breaking the
     * head would hand over a whole machine and leave the real one standing.</p>
     */
    @Override
    public List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
                                                             LootParams.Builder params) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return List.of();
        }
        return super.getDrops(state, params);
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
