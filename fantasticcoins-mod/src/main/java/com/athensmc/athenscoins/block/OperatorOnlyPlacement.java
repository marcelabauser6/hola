package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.bank.BankAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stops non-operators placing the bank terminal.
 *
 * <p>The item has no recipe and only appears in the creative tab, but that alone would still let
 * anyone in creative mode found a bank, so placement is checked as well.</p>
 */
@Mod.EventBusSubscriber(modid = AthensCoinsMod.MOD_ID)
public final class OperatorOnlyPlacement {

    private OperatorOnlyPlacement() {
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        boolean bankTerminal = event.getPlacedBlock().is(ModBlocks.BANK_TERMINAL.get());
        boolean centralTerminal = event.getPlacedBlock().is(ModBlocks.CENTRAL_BANK_TERMINAL.get());
        // The stats projector is no longer here: it is issued branded by a bank, exactly like an ATM, so
        // holding one already means a bank handed it to you. Gating the placement as well would mean a
        // banker could be given a board for their own bank and then not be allowed to stand it up.
        if (!bankTerminal && !centralTerminal) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The central bank is the root of the licence chain - it is where founders are appointed - so it
        // stays operator-only. A founder may open it and set rates, but not plant a second one.
        if (centralTerminal ? BankAccess.isOperator(player) : BankAccess.canFoundBanks(player)) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.translatable(centralTerminal
                        ? "message.athens_coins.central_place_op_only"
                        : "message.athens_coins.terminal_op_only")
                .withStyle(ChatFormatting.RED));
    }
}
