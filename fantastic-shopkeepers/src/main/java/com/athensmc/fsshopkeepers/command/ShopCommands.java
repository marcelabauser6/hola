package com.athensmc.fsshopkeepers.command;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.menu.ShopMenus;
import com.athensmc.fsshopkeepers.money.Cash;
import com.athensmc.fsshopkeepers.net.EditorAccess;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.shop.ShopCreation;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.ShopType;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The {@code /tienda} command and its subcommands.
 *
 * <p>Registered under {@code /tienda} with {@code /shopkeeper} as an alias, so a server migrating from the plugin keeps
 * the command its staff already type. Brigadier's own permission hook is not used for the staff checks: those go
 * through {@link FantasticShopkeepers#hasPermission}, which can answer from a permissions plugin, whereas Brigadier
 * only knows operator levels.</p>
 *
 * <p>Every subcommand that acts on "the shop in front of you" resolves it the same way, through
 * {@link #nearestShop}, so there is one definition of which shop is meant.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID)
public final class ShopCommands {

    /** How far away a shop may be to count as the one the player means. */
    private static final double PICK_RANGE = 8.0D;

    private ShopCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build("tienda"));
        event.getDispatcher().register(build("shopkeeper"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .executes(ShopCommands::help)
                .then(Commands.literal("ayuda").executes(ShopCommands::help))
                .then(Commands.literal("editar").executes(ShopCommands::edit))
                .then(Commands.literal("info").executes(ShopCommands::info))
                .then(Commands.literal("borrar").executes(ShopCommands::remove))
                .then(Commands.literal("lista")
                        .executes(context -> list(context, null))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(context -> list(context,
                                        EntityArgument.getPlayer(context, "jugador")))))
                .then(Commands.literal("dar")
                        .executes(context -> give(context, null))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(context -> give(context,
                                        EntityArgument.getPlayer(context, "jugador")))))
                .then(Commands.literal("traspasar")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(ShopCommands::transfer)))
                .then(Commands.literal("recargar").executes(ShopCommands::reload))
                .then(Commands.literal("arreglar").executes(ShopCommands::respawnAll))
                .then(Commands.literal("crear")
                        .then(Commands.argument("tipo", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (ShopType type : ShopType.values()) {
                                        builder.suggest(type.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> create(context, "mob"))
                                .then(Commands.argument("cuerpo", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (ShopObjectKind kind : ShopObjectKind.values()) {
                                                builder.suggest(kind.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> create(context,
                                                StringArgumentType.getString(context, "cuerpo"))))));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Fantastic Shopkeepers")
                .withStyle(ChatFormatting.GOLD), false);
        line(source, "/tienda crear <admin|venta|compra|trueque|libros> [mob|cartel|virtual]",
                "Crea una tienda donde estas.");
        line(source, "/tienda editar", "Abre el editor de la tienda que tengas delante.");
        line(source, "/tienda info", "Muestra los datos de la tienda que tengas delante.");
        line(source, "/tienda borrar", "Borra la tienda que tengas delante.");
        line(source, "/tienda lista [jugador]", "Lista tus tiendas o las de otro jugador.");
        line(source, "/tienda dar [jugador]", "Da el objeto para crear tiendas.");
        line(source, "/tienda traspasar <jugador>", "Cambia el dueño de la tienda que tengas delante.");
        line(source, "/tienda arreglar", "Vuelve a poner los tenderos que falten.");
        line(source, "/tienda recargar", "Recarga la configuracion.");
        source.sendSuccess(() -> Component.literal(
                "Truco: agachate y haz clic derecho en un tendero para abrir su editor.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String command, String description) {
        source.sendSuccess(() -> Component.literal(command).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  " + description).withStyle(ChatFormatting.GRAY)), false);
    }

    /**
     * The registered shop nearest the player, within {@link #PICK_RANGE}.
     *
     * <p>Chosen by distance to the shop's recorded position rather than by ray-tracing what the player is looking at.
     * A shop's mob can be standing behind a block or inside a wall, and an admin trying to fix that shop still needs
     * to be able to select it.</p>
     */
    private static Shopkeeper nearestShop(ServerPlayer player) {
        ShopRegistry registry = ShopRegistry.get(player.server);
        Shopkeeper best = null;
        double bestDistance = PICK_RANGE * PICK_RANGE;
        for (Shopkeeper shop : registry.all()) {
            if (!shop.level().equals(player.level().dimension()) || !shop.objectKind().isPlaced()) {
                continue;
            }
            double distance = player.distanceToSqr(shop.pos().getX() + 0.5D, shop.pos().getY() + 0.5D,
                    shop.pos().getZ() + 0.5D);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = shop;
            }
        }
        return best;
    }

    private static int edit(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        Shopkeeper shop = nearestShop(player);
        if (shop == null) {
            fail(context, "No hay ninguna tienda cerca.");
            return 0;
        }
        if (!EditorAccess.mayEdit(player, shop)) {
            fail(context, "No tienes permiso para editar esa tienda.");
            return 0;
        }
        Net.openEditor(player, shop);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        Shopkeeper shop = nearestShop(player);
        if (shop == null) {
            fail(context, "No hay ninguna tienda cerca.");
            return 0;
        }
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(shop.displayName()).withStyle(ChatFormatting.GOLD), false);
        detail(source, "Tipo", shop.type().title());
        detail(source, "Cuerpo", shop.objectKind().title()
                + (shop.objectKind().hasEntity() ? " (" + shop.entityType() + ")" : ""));
        detail(source, "Dueño", shop.ownerName().isBlank() ? "administrador" : shop.ownerName());
        detail(source, "Posicion", shop.pos().toShortString());
        detail(source, "Cofre", shop.containerPos() == null ? "ninguno"
                : shop.containerPos().toShortString());
        detail(source, "Tratos", shop.tradableOffers().size() + " activos de " + shop.offers().size());
        detail(source, "Cuenta", shop.linkedAccount() > 0 ? String.valueOf(shop.linkedAccount())
                : "por defecto");
        if (shop.forHire()) {
            detail(source, "Traspaso", Cash.format(shop.hireCost()));
        }
        if (!shop.tradePermission().isBlank()) {
            detail(source, "Permiso", shop.tradePermission());
        }
        detail(source, "Id", shop.id().toString());
        return 1;
    }

    private static void detail(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        Shopkeeper shop = nearestShop(player);
        if (shop == null) {
            fail(context, "No hay ninguna tienda cerca.");
            return 0;
        }
        if (!EditorAccess.mayEdit(player, shop)) {
            fail(context, "No tienes permiso para borrar esa tienda.");
            return 0;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        ServerLevel level = player.server.getLevel(shop.level());
        if (level != null) {
            ShopSpawner.despawn(level, shop, registry);
        }
        registry.remove(shop.id());
        context.getSource().sendSuccess(() -> Component.literal("Tienda borrada.")
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ServerPlayer subject = target == null ? player : target;
        boolean others = !subject.getUUID().equals(player.getUUID());
        String node = others ? FantasticShopkeepers.Perms.LIST_OTHERS : FantasticShopkeepers.Perms.LIST_OWN;
        if (others && !FantasticShopkeepers.hasPermission(player, node)) {
            fail(context, "No tienes permiso para ver las tiendas de otros.");
            return 0;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        List<Shopkeeper> shops = registry.shopsOf(subject.getUUID());
        CommandSourceStack source = context.getSource();
        if (shops.isEmpty()) {
            source.sendSuccess(() -> Component.literal(subject.getGameProfile().getName()
                    + " no tiene tiendas.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Tiendas de " + subject.getGameProfile().getName()
                + " (" + shops.size() + "):").withStyle(ChatFormatting.GOLD), false);
        for (Shopkeeper shop : shops) {
            source.sendSuccess(() -> Component.literal("· " + shop.displayName())
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("  " + shop.type().title() + " · "
                            + shop.pos().toShortString() + " · " + shop.tradableOffers().size() + " tratos")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        if (!FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.ADMIN)) {
            fail(context, "No tienes permiso para dar objetos de creacion.");
            return 0;
        }
        ServerPlayer receiver = target == null ? player : target;
        ResourceLocation itemId = ResourceLocation.tryParse(ShopConfig.get().shopCreationItem);
        Item item = itemId == null ? null : BuiltInRegistries.ITEM.get(itemId);
        if (item == null) {
            fail(context, "El objeto de creacion configurado (" + ShopConfig.get().shopCreationItem
                    + ") no existe.");
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        if (!receiver.getInventory().add(stack)) {
            receiver.drop(stack, false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Objeto de creacion entregado a "
                + receiver.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ServerPlayer newOwner;
        try {
            newOwner = EntityArgument.getPlayer(context, "jugador");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException notFound) {
            fail(context, "Ese jugador no esta conectado.");
            return 0;
        }
        Shopkeeper shop = nearestShop(player);
        if (shop == null) {
            fail(context, "No hay ninguna tienda cerca.");
            return 0;
        }
        if (!EditorAccess.mayEdit(player, shop)) {
            fail(context, "No tienes permiso para traspasar esa tienda.");
            return 0;
        }
        if (!shop.type().isPlayerShop()) {
            fail(context, "Una tienda de administrador no tiene dueño que traspasar.");
            return 0;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        shop.setOwner(newOwner.getUUID(), newOwner.getGameProfile().getName());
        // The linked account belonged to the previous owner, so it is cleared rather than left pointing at them.
        shop.setLinkedAccount(0);
        registry.refresh(shop);
        context.getSource().sendSuccess(() -> Component.literal("Tienda traspasada a "
                + newOwner.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN), true);
        newOwner.sendSystemMessage(Component.literal("Ahora eres el dueño de la tienda "
                + shop.displayName() + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null && !FantasticShopkeepers.hasPermission(player,
                FantasticShopkeepers.Perms.RELOAD)) {
            fail(context, "No tienes permiso para recargar la configuracion.");
            return 0;
        }
        ShopConfig.load();
        context.getSource().sendSuccess(() -> Component.literal("Configuracion recargada.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * Re-spawns every shop mob that is missing.
     *
     * <p>The repair command. A shop whose mob was removed by something outside this mod becomes unclickable, and while
     * chunk loading fixes that eventually, an admin standing in front of the problem should not have to unload the
     * chunk to solve it.</p>
     */
    private static int respawnAll(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null && !FantasticShopkeepers.hasPermission(player,
                FantasticShopkeepers.Perms.ADMIN)) {
            fail(context, "No tienes permiso para eso.");
            return 0;
        }
        ShopRegistry registry = ShopRegistry.get(context.getSource().getServer());
        int fixed = 0;
        for (Shopkeeper shop : registry.all()) {
            if (!shop.objectKind().hasEntity()) {
                continue;
            }
            ServerLevel level = context.getSource().getServer().getLevel(shop.level());
            if (level == null) {
                continue;
            }
            if (ShopSpawner.ensureSpawned(level, shop, registry)) {
                fixed++;
            }
        }
        int total = fixed;
        context.getSource().sendSuccess(() -> Component.literal("Tenderos restaurados: " + total + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context, String bodyId) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ShopType type = ShopType.byId(StringArgumentType.getString(context, "tipo"));
        ShopObjectKind kind = ShopObjectKind.byId(bodyId);
        BlockPos where = player.blockPosition();

        BlockPos container = null;
        if (type.needsContainer()) {
            container = findContainerNear(player);
            if (container == null) {
                fail(context, "No hay ningun cofre libre a menos de "
                        + ShopConfig.get().maxContainerDistance + " bloques.");
                return 0;
            }
        }
        ShopCreation.Outcome outcome = ShopCreation.create(player, type, kind, where, container);
        if (!outcome.ok()) {
            context.getSource().sendFailure(outcome.error());
            return 0;
        }
        Shopkeeper shop = outcome.shop();
        context.getSource().sendSuccess(() -> Component.literal("Tienda creada: " + shop.type().title()
                + ". Abriendo el editor...").withStyle(ChatFormatting.GREEN), false);
        Net.openEditor(player, shop);
        // A virtual shop has no body to click, so its window is opened once here to prove it works.
        if (kind == ShopObjectKind.VIRTUAL) {
            ShopMenus.openTrade(player, shop);
        }
        return 1;
    }

    /**
     * The nearest unclaimed container, for creating a shop by command.
     *
     * <p>Searched in a cube around the player rather than asked for as an argument, because the alternative is typing
     * three coordinates for a chest you are standing next to.</p>
     */
    private static BlockPos findContainerNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ShopRegistry registry = ShopRegistry.get(player.server);
        int range = Math.max(1, ShopConfig.get().maxContainerDistance);
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-range, -range, -range),
                origin.offset(range, range, range))) {
            if (!ShopCreation.isContainer(level, pos)) {
                continue;
            }
            if (registry.byContainer(level.dimension(), pos) != null) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal(
                    "Este comando lo tiene que usar un jugador dentro del mundo."));
        }
        return player;
    }

    private static void fail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
    }
}
