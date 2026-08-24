package de.z0rdak.yawp.wand;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The wand's game hooks: marking a corner, and making sure the rod does nothing else.
 *
 * <p>Registered by hand from {@link WandHook} rather than by an annotation. There is no {@code @Mod} class
 * for Forge to scan - the wand is part of YAWP rather than a mod beside it - so there is nothing for
 * {@code @Mod.EventBusSubscriber} to attach to.</p>
 *
 * <p><strong>Every listener here is at {@link EventPriority#HIGHEST} and cancels.</strong> That is the
 * point, not a detail. The wand is a plain blaze rod, and on a modded server a blaze rod is not an inert
 * item: packs make it placeable, turn it into a tool, feed it to machines or bind their own right-click
 * behaviour to it. Any of those firing on the same click would either eat the wand or do something to the
 * world instead of marking a corner. Running before every other listener and cancelling means the click
 * belongs to the wand and to nothing else.</p>
 */
public final class WandEvents {

    private WandEvents() {
    }

    /**
     * Marks a corner when a block is right-clicked with the wand.
     *
     * <p>This mod does its own marking instead of leaving it to YAWP's interaction mixin. The mixin does
     * the job when it applies, but a tool that only works if someone else's mixin applied is a tool that
     * stops working for reasons the person holding it cannot see.</p>
     *
     * <p>Being handled in both places would double every corner, so {@link MarkerData#write} rewrites the
     * whole corner list from this mod's own view of it rather than appending, and a repeated click on the
     * same block is ignored. Whoever else writes to the tag, the next click puts it right.</p>
     *
     * <p>Cancelled with a SUCCESS result so no block is placed, no container opens, no other mod gets the
     * click - and the arm still swings, which on a vanilla client is the only sign the click landed.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack wand = event.getItemStack();
        if (!RegionWand.isWand(wand)) {
            return;
        }
        // Cancelled on both sides. The client half stops the placement prediction, which would otherwise
        // flash a ghost block where the corner was marked.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);

        if (event.getEntity() instanceof ServerPlayer player) {
            WandMarking.onRightClick(player, wand, event.getPos(), player.isShiftKeyDown());
        }
    }

    /**
     * Stops the wand being used to break things.
     *
     * <p>A blaze rod cannot mine, but in creative a left click breaks the block outright - which would have
     * the wand destroying the very corner it was aimed at.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (RegionWand.isWand(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /**
     * Swallows a click on empty air.
     *
     * <p>Nothing for the wand to do, but plenty for a mod that has given blaze rods a use to do. Cancelling
     * keeps the rod inert everywhere except on a block.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (RegionWand.isWand(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Keeps the wand from interacting with entities.
     *
     * <p>Otherwise a click aimed at a corner with a cow in the way saddles the cow, and on a server with
     * entity-heavy mods the rod becomes a way to trigger whatever they bound to a right click.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (RegionWand.isWand(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** The same, for the variant fired when the exact point on the entity matters. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (RegionWand.isWand(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Redraws the outline for whoever is holding a wand.
     *
     * <p>On the player tick, so a server with no wand out does no work beyond the phase check. The counter
     * is per player because a global one would have two administrators sharing a refresh slot and each
     * seeing half the frames.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % WandOutline.REFRESH_TICKS == 0) {
            WandOutline.tick(player);
        }
        // Offset from the outline's refresh so the two are never computed on the same tick, which keeps the
        // per-tick cost even rather than spiking. Both only do work with the rod in hand.
        if ((player.tickCount + 2) % BoundaryWarning.REFRESH_TICKS == 0) {
            BoundaryWarning.tick(player);
        }
    }
}
