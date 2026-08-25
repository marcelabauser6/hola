package com.athensmc.fsshopkeepers.net;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: delete this shop.
 *
 * <p>Its own message rather than a flag on {@link SaveShopPacket}, because deleting is the one editor action that
 * cannot be undone and giving it a separate path makes the permission check for it impossible to skip by accident.</p>
 */
public record DeleteShopPacket(UUID shopId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(shopId);
    }

    public static DeleteShopPacket decode(FriendlyByteBuf buf) {
        return new DeleteShopPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            ShopRegistry registry = ShopRegistry.get(player.server);
            Shopkeeper shop = registry.byId(shopId);
            if (shop == null) {
                player.sendSystemMessage(Component.literal("Esa tienda ya no existe.")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            if (!EditorAccess.mayEdit(player, shop)) {
                player.sendSystemMessage(Component.literal("No tienes permiso para borrar esta tienda.")
                        .withStyle(ChatFormatting.RED));
                FantasticShopkeepers.LOGGER.warn("{} intento borrar la tienda {} sin permiso.",
                        player.getGameProfile().getName(), shopId);
                return;
            }
            ServerLevel level = player.server.getLevel(shop.level());
            if (level != null) {
                ShopSpawner.despawn(level, shop, registry);
            }
            registry.remove(shopId);
            player.sendSystemMessage(Component.literal("Tienda borrada.").withStyle(ChatFormatting.YELLOW));
        });
        ctx.setPacketHandled(true);
    }
}
