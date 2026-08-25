package com.athensmc.fsshopkeepers.event;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.menu.ShopMenus;
import com.athensmc.fsshopkeepers.net.EditorAccess;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Everything that happens because a player did something in the world.
 *
 * <p>Clicking a shopkeeper, opening a sign shop, protecting a shop's chest, and bringing a shop's mob back when its
 * chunk loads. All of it is server side: the handlers return early on the client, because acting on both sides would
 * open the trading window twice.</p>
 *
 * <p>There is deliberately no shop-creation item. Handing a player a spawn egg that silently means something else, and
 * then asking them to right-click a chest and then the ground in the right order, is a flow nobody can guess without
 * being told. Making a shop is {@code /fskeepers crear} and nothing else.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID)
public final class ShopEvents {

    private ShopEvents() {
    }

    /**
     * Clicking a shopkeeper.
     *
     * <p>A plain click trades, a sneaking click by someone allowed to edit opens the editor. Sneak-to-edit rather than a
     * command, because the shop you want to edit is the one you are standing in front of.</p>
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        UUID shopId = ShopSpawner.shopIdOf(target);
        if (shopId == null) {
            return;
        }
        // Consumed either way: a shopkeeper must never respond as the mob it happens to be made of.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        ShopRegistry registry = ShopRegistry.get(player.server);
        Shopkeeper shop = registry.byId(shopId);
        if (shop == null) {
            // The shop is gone but its body was left behind, so the body goes too.
            target.discard();
            return;
        }
        openFor(player, shop);
    }

    /** Opens the editor for someone who may edit, otherwise the trading window. */
    private static void openFor(ServerPlayer player, Shopkeeper shop) {
        if (player.isShiftKeyDown() && EditorAccess.mayEdit(player, shop)) {
            Net.openEditor(player, shop);
            return;
        }
        ShopMenus.openTrade(player, shop);
    }

    /**
     * Right-clicking a block: opening a sign shop, or being refused someone else's shop chest.
     *
     * <p>At {@link EventPriority#HIGH} so a shop's chest is protected before another mod's handler opens it. Protection
     * that runs after the container has been opened is not protection.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        ShopRegistry registry = ShopRegistry.get(player.server);

        Shopkeeper atBlock = registry.byPosition(level.dimension(), pos);
        if (atBlock != null && atBlock.objectKind() == ShopObjectKind.SIGN) {
            event.setCanceled(true);
            openFor(player, atBlock);
            return;
        }

        Shopkeeper owningShop = registry.byContainer(level.dimension(), pos);
        if (owningShop != null && ShopConfig.get().protectContainers
                && !owningShop.isOwner(player.getUUID())
                && !FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.ADMIN)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Ese cofre abastece la tienda de "
                    + owningShop.ownerName() + ".").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Breaking a block a shop depends on.
     *
     * <p>A sign shop's sign takes the shop with it, since the shop has no other body. A shop's chest is refused unless
     * the config says breaking it should delete the shop, which is the choice between a shop that cannot be un-stocked
     * by accident and a chest that cannot be held hostage by a shop.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        BlockPos pos = event.getPos();

        Shopkeeper signShop = registry.byPosition(level.dimension(), pos);
        if (signShop != null && signShop.objectKind() == ShopObjectKind.SIGN) {
            if (!EditorAccess.mayEdit(player, signShop)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("Ese cartel es la tienda de "
                        + signShop.ownerName() + ".").withStyle(ChatFormatting.RED));
                return;
            }
            registry.remove(signShop.id());
            player.sendSystemMessage(Component.literal("Tienda de cartel borrada.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        Shopkeeper owningShop = registry.byContainer(level.dimension(), pos);
        if (owningShop == null) {
            return;
        }
        boolean allowed = owningShop.isOwner(player.getUUID())
                || FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.ADMIN);
        if (!allowed) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Ese cofre abastece la tienda de "
                    + owningShop.ownerName() + ".").withStyle(ChatFormatting.RED));
            return;
        }
        if (ShopConfig.get().deleteShopOnContainerBreak) {
            ShopSpawner.despawn(level, owningShop, registry);
            registry.remove(owningShop.id());
            player.sendSystemMessage(Component.literal("Has roto el cofre, la tienda se ha borrado.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal(
                "Este cofre abastece una tienda. Borrala con /fskeepers borrar antes de romperlo.")
                .withStyle(ChatFormatting.RED));
    }

    /**
     * A shopkeeper cannot be hurt.
     *
     * <p>The mob is already flagged invulnerable; this is the belt to that braces. Invulnerability is a field another mod
     * may reset, whereas an attack refused here is refused whatever the field says.</p>
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (ShopSpawner.isShopEntity(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Bringing a chunk's shops back when it loads.
     *
     * <p>A shop's mob is an ordinary entity, so anything that removes entities can remove it: a chunk purge, a crash,
     * another mod's cleanup. Re-spawning on chunk load means the shop reappears instead of becoming a row in the registry
     * with nothing to click.</p>
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        ShopRegistry registry = ShopRegistry.get(level.getServer());
        for (Shopkeeper shop : registry.all()) {
            if (!shop.objectKind().hasEntity() || !shop.level().equals(level.dimension())) {
                continue;
            }
            if (new ChunkPos(shop.pos()).equals(chunkPos)) {
                ShopSpawner.ensureSpawned(level, shop, registry);
            }
        }
    }
}
