package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.AthensCoinsMod;
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
        if (!event.getPlacedBlock().is(ModBlocks.BANK_TERMINAL.get())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.hasPermissions(2)) {
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.translatable("message.athens_coins.terminal_op_only")
                .withStyle(ChatFormatting.RED));
    }
}
