package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.item.ModItems;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A bank's cash machine. Right-click to swap coins for cash at that bank's rates.
 *
 * <p>Machines are issued by a bank terminal and carry their issuer's identity, so an unbranded one
 * cannot be used: there is no bank behind it to hold the money.</p>
 */
public class AtmBlock extends TallMachineBlock implements EntityBlock {

    /**
     * Collision reaches z=1, not z=2, because the canopy and the cash tray stick out that far.
     *
     * <p>Left at 2 the tray would have been visible but not clickable, and the player would have
     * walked through the part of the machine that overhangs them.</p>
     */
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 14.0D);
    private static final VoxelShape SHAPE_EW = Block.box(1.0D, 0.0D, 1.0D, 14.0D, 16.0D, 15.0D);

    public AtmBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape shapeFor(DoubleBlockHalf half, Direction facing) {
        // Both halves share a footprint: the machine is a column, and giving the head a narrower box
        // than the body would let a player stand inside its shoulders.
        return facing.getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE;
    }

    /**
     * Only the lower half carries the branding.
     *
     * <p>Returning {@code null} for the head is deliberate: two block entities would mean two answers
     * to "which bank issued this", and the head is the one nobody writes to.</p>
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMain(state) ? new AtmBlockEntity(pos, state) : null;
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

    /** Middle-click pick block keeps the branding, from either half. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(mainPos(state, pos)) instanceof AtmBlockEntity atm) {
            atm.applyTo(stack);
        }
        return stack;
    }

    /**
     * Breaking a machine returns it still branded.
     *
     * <p>Without this the loot table hands back a blank ATM, so relocating a bank's machine would
     * quietly turn it into a useless block that gets refused on use.</p>
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof AtmBlockEntity atm && !atm.unbranded()) {
            for (ItemStack stack : drops) {
                if (stack.is(ModItems.ATM_ITEM.get())) {
                    atm.applyTo(stack);
                }
            }
        }
        return drops;
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
        // Whichever half was clicked, the machine is the lower one.
        BlockPos base = mainPos(state, pos);
        if (!(level.getBlockEntity(base) instanceof AtmBlockEntity atm) || atm.unbranded()) {
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
        BankManager.Access access = BankManager.accessFor(serverPlayer, bank);
        if (access != BankManager.Access.OK) {
            // Names the bank when the problem is that the customer banks elsewhere, which the old
            // single "you need an account" message got wrong for anybody who already had one.
            BankManager.explainRefusal(serverPlayer, bank, access);
            return InteractionResult.CONSUME;
        }
        open(serverPlayer, level, base, bank);
        return InteractionResult.CONSUME;
    }

    public static void open(ServerPlayer player, Level level, BlockPos pos, Bank bank) {
        ContainerLevelAccess access = ContainerLevelAccess.create(level, pos);
        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, owner) -> createMenu(containerId, inventory, access, bank, pos),
                Component.literal(bank.name()));
        NetworkHooks.openScreen(player, provider,
                buffer -> AtmMenu.writeState(buffer, player, bank, pos));
    }

    private static AbstractContainerMenu createMenu(int containerId, Inventory inventory,
                                                    ContainerLevelAccess access, Bank bank,
                                                    BlockPos pos) {
        return new AtmMenu(containerId, inventory, access, bank, pos);
    }
}
