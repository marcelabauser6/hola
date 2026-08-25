package com.athensmc.fsshopkeepers.event;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.menu.ShopMenus;
import com.athensmc.fsshopkeepers.net.EditorAccess;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.shop.ShopCreation;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.ShopType;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything that happens because a player did something in the world.
 *
 * <p>Clicking a shopkeeper, making a shop, breaking a shop's chest and loading a chunk a shop stands in. All of it is
 * server side: the handlers return early on the client, because acting on both sides would open the trading window
 * twice and create the shop twice.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID)
public final class ShopEvents {

    /**
     * Which chest each player last clicked with the creation item.
     *
     * <p>Deliberately not saved. A half-finished shop creation is not worth surviving a restart, and a selection
     * pointing at a chest that was broken while the server was down would be worse than no selection.</p>
     */
    private static final Map<UUID, BlockPos> selectedContainer = new HashMap<>();

    private ShopEvents() {
    }

    /**
     * Clicking a shopkeeper.
     *
     * <p>A plain click trades, a sneaking click by someone allowed to edit opens the editor. Sneak-to-edit rather than
     * a separate command, because the shop you want to edit is the one you are standing in front of.</p>
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
        // Consumed either way: a shopkeeper must never respond as the mob it is made of.
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
     * Right-clicking a block: selecting a chest, placing a shop, or opening a sign shop.
     *
     * <p>Runs at {@link EventPriority#HIGH} so that a shop's chest is protected before another mod's handler opens it.
     * Protection that runs after the container has already been opened is not protection.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        ShopRegistry registry = ShopRegistry.get(player.server);

        // A sign shop is opened by clicking it.
        Shopkeeper atBlock = registry.byPosition(level.dimension(), pos);
        if (atBlock != null && atBlock.objectKind() == ShopObjectKind.SIGN) {
            event.setCanceled(true);
            openFor(player, atBlock);
            return;
        }

        // Someone else's shop chest stays shut.
        Shopkeeper owningShop = registry.byContainer(level.dimension(), pos);
        if (owningShop != null && ShopConfig.get().protectContainers
                && !owningShop.isOwner(player.getUUID())
                && !FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.ADMIN)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Ese cofre pertenece a la tienda de "
                    + owningShop.ownerName() + ".").withStyle(ChatFormatting.RED));
            return;
        }

        if (!isCreationItem(event.getItemStack())) {
            return;
        }

        event.setCanceled(true);
        if (ShopCreation.isContainer(level, pos)) {
            selectedContainer.put(player.getUUID(), pos.immutable());
            player.sendSystemMessage(Component.literal(
                    "Cofre seleccionado. Ahora haz clic derecho donde quieras al tendero.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        BlockPos where = pos.above();
        BlockPos container = selectedContainer.get(player.getUUID());
        if (container == null) {
            player.sendSystemMessage(Component.literal(
                    "Primero haz clic derecho en un cofre con este objeto para elegir el almacen.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ShopCreation.Outcome outcome = ShopCreation.create(player, ShopType.PLAYER_SELL,
                ShopObjectKind.LIVING, where, container);
        if (!outcome.ok()) {
            player.sendSystemMessage(outcome.error());
            return;
        }
        selectedContainer.remove(player.getUUID());
        if (!player.isCreative()) {
            event.getItemStack().shrink(1);
        }
        player.sendSystemMessage(Component.literal(
                "Tienda creada. Agachate y haz clic derecho en el tendero para configurarla.")
                .withStyle(ChatFormatting.GREEN));
    }

    private static boolean isCreationItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation configured = ResourceLocation.tryParse(ShopConfig.get().shopCreationItem);
        if (configured == null) {
            return false;
        }
        ResourceLocation held = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return configured.equals(held);
    }

    /**
     * Breaking a block that a shop depends on.
     *
     * <p>A sign shop's sign takes the shop with it, since the shop has no other body. A shop's chest is refused
     * outright unless the config says breaking it should delete the shop, which is a choice between a shop that
     * cannot be un-stocked by accident and a chest that cannot be locked away by a shop.</p>
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
            player.sendSystemMessage(Component.literal("Ese cofre pertenece a la tienda de "
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
                "Este cofre abastece una tienda. Borra la tienda antes de romperlo.")
                .withStyle(ChatFormatting.RED));
    }

    /**
     * A shopkeeper cannot be hurt.
     *
     * <p>The mob is already flagged invulnerable, and this is the belt to that braces. Invulnerability is a field on
     * the entity that another mod may reset, whereas an attack refused here is refused whatever the field says.</p>
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
     * <p>A shop's mob is a normal entity, so it can be removed by anything that removes entities: a chunk purge, a
     * crash, another mod's cleanup. Re-spawning on chunk load means the shop reappears rather than becoming a row in
     * the registry with nothing to click.</p>
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
