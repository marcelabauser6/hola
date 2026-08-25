package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccess;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bank terminal. Placing one seats a bank; right-clicking opens its management screen.
 *
 * <p>Only operators may place it, and it has no recipe, so a bank cannot appear without staff
 * deciding it should.</p>
 */
public class BankTerminalBlock extends TallMachineBlock {

    /** The counter: a full-depth desk. */
    private static final VoxelShape DESK_NS = Block.box(0.0D, 0.0D, 2.0D, 16.0D, 16.0D, 14.0D);
    private static final VoxelShape DESK_EW = Block.box(2.0D, 0.0D, 0.0D, 14.0D, 16.0D, 16.0D);
    /** The head: a shallower back panel, so a teller can lean over the counter. */
    private static final VoxelShape HEAD_NS = Block.box(0.0D, 0.0D, 5.0D, 16.0D, 16.0D, 14.0D);
    private static final VoxelShape HEAD_EW = Block.box(2.0D, 0.0D, 0.0D, 11.0D, 16.0D, 16.0D);

    public BankTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape shapeFor(DoubleBlockHalf half, Direction facing) {
        boolean acrossX = facing.getAxis() == Direction.Axis.X;
        if (half == DoubleBlockHalf.UPPER) {
            return acrossX ? HEAD_EW : HEAD_NS;
        }
        return acrossX ? DESK_EW : DESK_NS;
    }

    /** NBT key binding a terminal item to the bank it came from. */
    public static final String TAG_BANK = "FcBank";
    public static final String TAG_BANK_NAME = "FcBankName";

    /**
     * Stamps a terminal item with a bank, so placing it puts that bank back.
     *
     * <p>This is what makes a broken terminal recoverable. Breaking one used to hand back a blank terminal
     * and leave the bank seatless, and placing a blank terminal then adopted an arbitrary seatless bank -
     * so an accident cost you the ability to reach your own bank at all.</p>
     */
    public static ItemStack bind(ItemStack stack, Bank bank) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_BANK, bank.id());
        tag.putString(TAG_BANK_NAME, bank.name());
        stack.setHoverName(Component.translatable("item.athens_coins.terminal_of", bank.name()));
        return stack;
    }

    /** Seats a bank on the terminal the moment it is placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_BANK)) {
            Bank bound = BankManager.seatBoundTerminal(player.server, pos, tag.getUUID(TAG_BANK));
            if (bound != null) {
                player.sendSystemMessage(Component.translatable(
                                "message.athens_coins.bank_reseated", bound.name())
                        .withStyle(ChatFormatting.GREEN));
                return;
            }
            // The binding did not resolve: the bank was deleted, or it already has a terminal standing
            // somewhere else. Say which, rather than silently founding a new bank under the old name.
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_bind_stale",
                    tag.getString(TAG_BANK_NAME)).withStyle(ChatFormatting.RED));
        }
        Bank bank = BankManager.seatTerminal(player.server, pos, "Banco");
        player.sendSystemMessage(Component.translatable("message.athens_coins.bank_seated",
                bank.name()).withStyle(ChatFormatting.GREEN));
    }

    /**
     * A broken terminal drops one bound to the bank that was standing there.
     *
     * <p>There is no block entity here to read, so the bank comes from the seat registry via the loot
     * context's origin - the same position {@code onRemove} is about to clear.</p>
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null || params.getLevel() == null || params.getLevel().getServer() == null) {
            return drops;
        }
        Bank bank = BankData.get(params.getLevel().getServer())
                .bankAt(mainPos(state, BlockPos.containing(origin)));
        if (bank == null) {
            return drops;
        }
        for (ItemStack stack : drops) {
            if (stack.is(com.athensmc.athenscoins.item.ModItems.BANK_TERMINAL_ITEM.get())) {
                bind(stack, bank);
            }
        }
        return drops;
    }

    /** Middle-click pick block keeps the binding. */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                       BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level instanceof Level real && real.getServer() != null) {
            Bank bank = BankData.get(real.getServer()).bankAt(mainPos(state, pos));
            if (bank != null) {
                bind(stack, bank);
            }
        }
        return stack;
    }

    /**
     * A broken terminal leaves the bank without a seat; the accounts survive.
     *
     * <p>Only the lower half is a seat. Clearing on the head too would unseat the bank when the head
     * is destroyed as part of the pair coming apart, and then seat it again on nothing.</p>
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && isMain(state) && !state.is(newState.getBlock())
                && level.getServer() != null) {
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
        // The seat is the lower half's position, whichever half the player reached for.
        BlockPos base = mainPos(state, pos);
        BankData data = BankData.get(serverPlayer.server);
        Bank bank = data.bankAt(base);
        if (bank == null) {
            // Seating creates a bank, so it needs the licence to create one. This used to run before any
            // check at all: right-clicking an unseated terminal founded a bank for whoever touched it
            // first, and only then asked whether they were allowed to be there.
            if (!BankAccess.canFoundBanks(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component
                        .translatable("message.athens_coins.bank_not_authorised")
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.CONSUME;
            }
            bank = BankManager.seatTerminal(serverPlayer.server, base, "Banco");
        }

        // A card in hand opens an account here and pours the money straight in. Deliberately before the
        // staff check: redeeming a card is a customer action, and the terminal is the counter.
        ItemStack held = serverPlayer.getItemInHand(hand);
        if (held.is(com.athensmc.athenscoins.item.ModItems.BANK_CARD.get())) {
            redeemCard(serverPlayer, bank, held);
            return InteractionResult.CONSUME;
        }

        if (!BankAccess.canOpenTerminal(serverPlayer, bank)) {
            serverPlayer.sendSystemMessage(Component
                    .translatable("message.athens_coins.bank_not_authorised")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        // "manager" rather than "operator": a founder licensed by the central bank runs their bank with
        // the same authority an operator would, which is the whole point of the licence.
        ModNetwork.toPlayer(serverPlayer,
                S2COpenTerminalPacket.of(serverPlayer, bank, base,
                        BankAccess.canConfigure(serverPlayer)));
        return InteractionResult.CONSUME;
    }

    /**
     * Turns a card into a fresh account at this bank.
     *
     * <p>The signature is checked first: a forged or hand-edited card is refused rather than
     * minting the money it claims.</p>
     */
    private static void redeemCard(ServerPlayer player, Bank bank, ItemStack card) {
        com.athensmc.athenscoins.item.BankCardItem.ValidatedCard validated =
                com.athensmc.athenscoins.item.BankCardItem.validateFor(player.server, player, card);
        if (validated == null) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.card_invalid")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        BankManager.OpenResult result = BankManager.redeemCardAccount(player.server, bank,
                player.getUUID(), player.getGameProfile().getName(),
                validated.token(), validated.amount());
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
}
