package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenTerminalPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bank terminal. Placing one seats a bank; right-clicking opens its management screen.
 *
 * <p>Only operators may place it, and it has no recipe, so a bank cannot appear without staff
 * deciding it should.</p>
 */
public class BankTerminalBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape SHAPE_NS = Block.box(1.0D, 0.0D, 3.0D, 15.0D, 16.0D, 13.0D);
    private static final VoxelShape SHAPE_EW = Block.box(3.0D, 0.0D, 1.0D, 13.0D, 16.0D, 15.0D);

    public BankTerminalBlock(Properties properties) {
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
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Seats a bank on the terminal the moment it is placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof ServerPlayer player)) {
            return;
        }
        Bank bank = BankManager.seatTerminal(player.server, pos, "Banco");
        player.sendSystemMessage(Component.translatable("message.athens_coins.bank_seated",
                bank.name()).withStyle(ChatFormatting.GREEN));
    }

    /** A broken terminal leaves the bank without a seat; the accounts survive. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getServer() != null) {
            BankData.get(level.getServer()).clearSeat(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
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
        BankData data = BankData.get(serverPlayer.server);
        Bank bank = data.bankAt(pos);
        if (bank == null) {
            bank = BankManager.seatTerminal(serverPlayer.server, pos, "Banco");
        }

        // A card in hand opens an account here and pours the money straight in.
        ItemStack held = serverPlayer.getItemInHand(hand);
        if (held.is(com.athensmc.athenscoins.item.ModItems.BANK_CARD.get())) {
            redeemCard(serverPlayer, bank, held);
            return InteractionResult.CONSUME;
        }

        boolean operator = serverPlayer.hasPermissions(2);
        if (!operator && !bank.isBanker(serverPlayer.getUUID())) {
            serverPlayer.sendSystemMessage(Component
                    .translatable("message.athens_coins.bank_not_authorised")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        ModNetwork.toPlayer(serverPlayer,
                S2COpenTerminalPacket.of(serverPlayer, bank, pos, operator));
        return InteractionResult.CONSUME;
    }

    /**
     * Turns a card into a fresh account at this bank.
     *
     * <p>The signature is checked first: a forged or hand-edited card is refused rather than
     * minting the money it claims.</p>
     */
    private static void redeemCard(ServerPlayer player, Bank bank, ItemStack card) {
        long amount = com.athensmc.athenscoins.item.BankCardItem.amountOf(player.server, card);
        if (amount < 0L) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.card_invalid")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankManager.OpenResult result = BankManager.openAccount(player.server, bank,
                player.getUUID(), player.getGameProfile().getName(), amount);
        if (!result.ok()) {
            player.sendSystemMessage(Component.translatable(result.messageKey())
                    .withStyle(ChatFormatting.RED));
            return;
        }
        card.shrink(1);
        com.athensmc.athenscoins.bank.BankAccount account =
                com.athensmc.athenscoins.bank.BankData.get(player.server).account(result.number());
        // The banker still gets the paperwork for the new number.
        ItemStack tag = BankManager.accountTag(account, bank);
        if (!player.getInventory().add(tag)) {
            player.drop(tag, false);
        }
        player.sendSystemMessage(Component.translatable("message.athens_coins.card_redeemed",
                        bank.name(), result.number())
                .withStyle(ChatFormatting.GREEN));
        com.athensmc.athenscoins.wallet.WalletManager.pushBalance(player);
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
