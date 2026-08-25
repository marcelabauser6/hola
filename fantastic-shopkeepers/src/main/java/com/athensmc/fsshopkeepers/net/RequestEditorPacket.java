package com.athensmc.fsshopkeepers.net;

import com.athensmc.fsshopkeepers.item.ModItems;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: I clicked this shopkeeper with the wand, open its editor.
 *
 * <p>Needed only because the client now swallows sneaking clicks on shopkeepers, to keep them away from mods that would
 * pocket the entity. Swallowing the click means the server never hears about it, so a sneaking wand click has to ask
 * directly.</p>
 *
 * <p>The request carries an entity uuid and nothing else. Everything that matters - that the entity is a shopkeeper, that
 * the player is close enough, that they are holding the wand and that they may edit this shop - is decided here, because a
 * client can claim to have clicked anything.</p>
 */
public record RequestEditorPacket(UUID entityId) {

    /** How far the player may be from the shopkeeper, matching normal interaction range. */
    private static final double REACH = 6.0D;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityId);
    }

    public static RequestEditorPacket decode(FriendlyByteBuf buf) {
        return new RequestEditorPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> open(ctx.getSender()));
        ctx.setPacketHandled(true);
    }

    private void open(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // Holding the wand is checked here as well as on the client: the client's copy of the inventory is not proof.
        if (!ModItems.isWand(player.getMainHandItem()) && !ModItems.isWand(player.getOffhandItem())) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = level.getEntity(entityId);
        if (target == null || target.isRemoved()) {
            return;
        }
        if (player.distanceToSqr(target) > REACH * REACH) {
            return;
        }
        UUID shopId = ShopSpawner.shopIdOf(target);
        if (shopId == null) {
            return;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        Shopkeeper shop = registry.byId(shopId);
        if (shop == null) {
            return;
        }
        if (!EditorAccess.mayEdit(player, shop)) {
            player.sendSystemMessage(Component.literal("Esa tienda no es tuya.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Net.openEditor(player, shop);
    }
}
