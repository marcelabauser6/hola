package com.athensmc.fsshopkeepers.shop;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Creating a shop, and every rule about whether one may be created.
 *
 * <p>Shared by the creation item and the commands, so the two cannot drift apart. The failure mode this avoids is a
 * limit that the item respects and the command does not, which is the sort of gap that only gets noticed once someone
 * has three hundred shops.</p>
 */
public final class ShopCreation {

    private ShopCreation() {
    }

    /** Either the new shop, or why it was refused. */
    public record Outcome(Shopkeeper shop, Component error) {

        public boolean ok() {
            return shop != null;
        }

        static Outcome no(String reason) {
            return new Outcome(null, Component.literal(reason).withStyle(ChatFormatting.RED));
        }

        static Outcome of(Shopkeeper shop) {
            return new Outcome(shop, null);
        }
    }

    /**
     * Creates a shop and registers it.
     *
     * <p>Order matters: everything is checked before the shop is put in the registry, so a refusal leaves no
     * half-created shop and no mob standing in the world with nothing behind it.</p>
     *
     * @param containerPos the chest backing the shop, or null for an admin shop.
     */
    public static Outcome create(ServerPlayer creator, ShopType type, ShopObjectKind kind, BlockPos where,
            BlockPos containerPos) {
        ShopConfig config = ShopConfig.get();
        ServerLevel level = creator.serverLevel();
        ShopRegistry registry = ShopRegistry.get(creator.server);

        boolean staff = FantasticShopkeepers.hasPermission(creator, FantasticShopkeepers.Perms.CREATE_ADMIN);
        if (!type.isPlayerShop() && !staff) {
            return Outcome.no("Solo el staff puede crear tiendas de administrador.");
        }
        if (type.isPlayerShop()
                && !FantasticShopkeepers.hasPermission(creator, FantasticShopkeepers.Perms.CREATE_PLAYER)) {
            return Outcome.no("No tienes permiso para crear tiendas.");
        }
        if (kind == ShopObjectKind.SIGN && !config.enableSignShops) {
            return Outcome.no("Las tiendas de cartel estan desactivadas.");
        }
        if (kind == ShopObjectKind.VIRTUAL && !config.enableVirtualShops) {
            return Outcome.no("Las tiendas virtuales estan desactivadas.");
        }

        if (type.isPlayerShop() && config.maxShopsPerPlayer >= 0
                && !FantasticShopkeepers.hasPermission(creator, FantasticShopkeepers.Perms.BYPASS_LIMIT)) {
            int owned = registry.countOf(creator.getUUID());
            if (owned >= config.maxShopsPerPlayer) {
                return Outcome.no("Ya tienes " + owned + " tiendas, el maximo es "
                        + config.maxShopsPerPlayer + ".");
            }
        }

        if (type.needsContainer()) {
            if (containerPos == null) {
                return Outcome.no("Primero haz clic derecho en un cofre con el objeto de creacion.");
            }
            if (!isContainer(level, containerPos)) {
                return Outcome.no("Ese bloque ya no es un contenedor valido.");
            }
            // Measured in the same world, because a chest at the same coordinates in the Nether is not nearby.
            double distance = Math.sqrt(containerPos.distSqr(where));
            if (distance > config.maxContainerDistance) {
                return Outcome.no("El cofre esta a " + (int) distance + " bloques y el maximo es "
                        + config.maxContainerDistance + ".");
            }
            Shopkeeper existing = registry.byContainer(level.dimension(), containerPos);
            if (existing != null) {
                return Outcome.no("Ese cofre ya pertenece a otra tienda.");
            }
        }

        if (kind.isPlaced() && registry.byPosition(level.dimension(), where) != null) {
            return Outcome.no("Ya hay una tienda en ese sitio.");
        }

        Shopkeeper shop = Shopkeeper.create(type, level.dimension(), where);
        if (type.isPlayerShop()) {
            shop.setOwner(creator.getUUID(), creator.getGameProfile().getName());
        }
        shop.setObjectKind(kind);
        if (type.needsContainer()) {
            shop.setContainerPos(containerPos);
        }
        registry.put(shop);

        if (kind.hasEntity()) {
            ShopSpawner.ensureSpawned(level, shop, registry);
        }
        FantasticShopkeepers.LOGGER.info("{} creo la tienda {} ({}) en {}",
                creator.getGameProfile().getName(), shop.id(), type.id(), where);
        return Outcome.of(shop);
    }

    /** True when the block at this position can hold items. */
    public static boolean isContainer(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container;
    }
}
