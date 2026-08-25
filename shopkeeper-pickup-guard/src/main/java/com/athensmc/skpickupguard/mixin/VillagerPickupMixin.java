package com.athensmc.skpickupguard.mixin;

import com.athensmc.skpickupguard.ShopkeeperCheck;

import de.maxhenkel.easyvillagers.events.VillagerEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Easy Villagers picking up a Shopkeepers shop.
 *
 * <p>Two things made this necessary, both read out of Easy Villagers' own jar rather than assumed:</p>
 *
 * <ul>
 *   <li>Its {@code arePickupConditionsMet} checks that the villager is alive and not asleep. That is the whole
 *       check - no owner, no protection, no permission. Any villager anyone can reach can be taken.</li>
 *   <li>The pickup is asked for by the <em>client</em>, which sends a packet the server then obeys. So the
 *       server is in charge, but its copy of Easy Villagers was never told to check anything. Turning the
 *       feature off in one player's own config protects nothing from anyone else.</li>
 * </ul>
 *
 * <p>{@code pickUp} is the single place both routes converge - the right-click packet and the keybind packet
 * both end here - so one guard covers every way in.</p>
 *
 * <p>Everything else about Easy Villagers is left alone. Ordinary villagers are still picked up exactly as
 * before; only entities Shopkeepers owns are refused, and the player is told why rather than watching a click
 * do nothing.</p>
 */
@Mixin(value = VillagerEvents.class, remap = false)
public abstract class VillagerPickupMixin {

    @Inject(method = "pickUp", at = @At("HEAD"), cancellable = true, remap = false)
    private static void skpickupguard$protectShopkeepers(Villager villager, Player player,
                                                        CallbackInfo callback) {
        if (villager == null || !ShopkeeperCheck.isShopkeeper(villager)) {
            return;
        }
        callback.cancel();

        // Said out loud, because a cancelled pickup is otherwise indistinguishable from a missed click and the
        // player will simply try again, and again.
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component
                    .literal("Esta tienda no se puede recoger.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }
}
