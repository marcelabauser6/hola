package de.z0rdak.yawp.wand.mixin;

import de.z0rdak.yawp.handler.MarkerStickHandler;
import de.z0rdak.yawp.wand.RegionWand;
import de.z0rdak.yawp.wand.WandMarking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes over the marking for our own rod, from inside YAWP's marking handler.
 *
 * <p><strong>This is the hook that makes the wand work at all, and the reason is worth writing down.</strong>
 * The obvious place to catch the click is Forge's {@code PlayerInteractEvent.RightClickBlock}, and that is
 * where this started. It never fired. YAWP's own {@code ServerPlayerInteractionManagerMixin} injects at
 * {@code HEAD} of {@code ServerPlayerGameMode.useItemOn}, and for any item carrying marker data it calls
 * {@link MarkerStickHandler#onMarkBlock} and then sets the return value to SUCCESS. Forge fires
 * {@code RightClickBlock} further down that same method - so YAWP returns first and the event never
 * happens. Every corner clicked went to YAWP silently, and from the outside the rod did nothing.</p>
 *
 * <p>So the click is taken here instead, at the one place it is guaranteed to arrive. Injected at
 * {@code HEAD} and cancelling, so our marking replaces YAWP's rather than running alongside it - which
 * also removes any chance of a corner being recorded twice.</p>
 *
 * <p><strong>Only our rod.</strong> When the item is not the blaze rod wand - YAWP's own vanilla stick, for
 * instance - this returns without cancelling and YAWP's marking proceeds exactly as before. Nothing about
 * the existing marker stick changes.</p>
 */
@Mixin(value = MarkerStickHandler.class, remap = false)
public abstract class MarkerStickHandlerMixin {

    @Inject(method = "onMarkBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void yawpwand$markWithWand(Player player, ItemStack stack, BlockPos clicked,
                                              CallbackInfo callback) {
        if (!(player instanceof ServerPlayer serverPlayer) || !RegionWand.isWand(stack)) {
            return;
        }
        // Crouching undoes instead of marking, so the tool carries its own undo.
        WandMarking.onRightClick(serverPlayer, stack, clicked, serverPlayer.isShiftKeyDown());
        callback.cancel();
    }
}
