package com.athensmc.fsshopkeepers.client;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.item.ModItems;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.net.RequestEditorPacket;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client's guard over shopkeepers.
 *
 * <p>Exists for one reason: other mods act on a click before the server ever hears about it. Easy Villagers turns a sneaking
 * right-click on a villager into "pocket this villager", entirely on the client, and sends its own packet to do it. A
 * server-side refusal arrives far too late - the shopkeeper is already an item in someone's hand and the shop is left
 * pointing at an entity that no longer exists.</p>
 *
 * <p>So sneaking clicks on a shopkeeper are stopped here, at {@link EventPriority#HIGHEST}, before any other mod's listener
 * runs. Forge does not deliver a cancelled event to listeners that did not ask for cancelled events, and no other mod does,
 * so cancelling first is enough.</p>
 *
 * <p>Only sneaking clicks are stopped. A plain click has to reach the server or nobody could shop, and sneaking means
 * nothing to this mod any more - the editor is opened by holding the wand. A sneaking click <em>with</em> the wand still
 * opens the editor, through a request of this mod's own, because the click it would have travelled on was just
 * swallowed.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID, value = Dist.CLIENT)
public final class ClientShopEvents {

    private ClientShopEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        guard(event, event.getEntity(), event.getTarget());
    }

    /**
     * The same, for clicks aimed at a precise point on an entity.
     *
     * <p>Forge fires this variant first, and a mod may act on either. Leaving one uncovered would leave the shopkeeper
     * takeable through whichever this mod did not watch.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        guard(event, event.getEntity(), event.getTarget());
    }

    private static void guard(PlayerInteractEvent event, Player player, Entity target) {
        if (player == null || target == null || !player.isShiftKeyDown()) {
            return;
        }
        if (!ClientShopBodies.isShopBody(target.getUUID())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        // The click has been swallowed, so a wand click has to ask the server directly. The server re-checks the wand,
        // the range and the permission; this is only a request.
        if (ModItems.isWand(player.getMainHandItem()) || ModItems.isWand(player.getOffhandItem())) {
            Net.requestEditor(new RequestEditorPacket(target.getUUID()));
        }
    }

    /** Forgets the shop list on disconnect, so it cannot leak into the next world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientShopBodies.clear();
    }
}
