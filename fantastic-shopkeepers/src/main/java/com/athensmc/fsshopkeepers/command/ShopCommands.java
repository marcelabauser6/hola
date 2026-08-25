package com.athensmc.fsshopkeepers.command;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.net.EditorAccess;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.shop.ShopCreation;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.ShopType;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The {@code /fskeepers} command.
 *
 * <p>One command with five subcommands, and that is the whole surface. Shopkeepers had twenty-five, most of which
 * existed to work around not having an editor - {@code setforhire}, {@code settradeperm}, {@code setcurrency} and the
 * rest are all fields on a form here, and a command that duplicates a field is a second place for the same setting to be
 * wrong. What is left is the five things a command is genuinely better at than a screen: making a shop, opening one,
 * deleting one, listing them, and re-reading the config.</p>
 *
 * <p>Registered once, in lowercase. Brigadier matches literals case-sensitively, so registering both spellings would put
 * two entries in the completion popup for what players think of as one command.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID)
public final class ShopCommands {

    /** The command, as players type it and as help text writes it. */
    public static final String NAME = "fskeepers";
    public static final String LABEL = "/" + NAME;

    /** How far away a shop may be to count as the one the player means. */
    private static final double PICK_RANGE = 8.0D;

    private ShopCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(NAME)
                .executes(ShopCommands::help)
                .then(Commands.literal("crear")
                        .executes(context -> create(context, ShopType.PLAYER_SELL))
                        .then(Commands.literal("admin")
                                .executes(context -> create(context, ShopType.ADMIN))))
                .then(Commands.literal("editar").executes(ShopCommands::edit))
                .then(Commands.literal("borrar").executes(ShopCommands::remove))
                .then(Commands.literal("lista").executes(ShopCommands::list))
                .then(Commands.literal("recargar").executes(ShopCommands::reload));
    }

    /**
     * The help, which doubles as what a bare {@code /fskeepers} prints.
     *
     * <p>Each line says what the command does in terms of what the player will see happen, not in terms of what it sets.
     * "Crea una tienda y abre el editor" is checkable; "crea un shopkeeper de tipo venta" is not.</p>
     */
    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("\u2726 Fantastic Shopkeepers")
                .withStyle(ChatFormatting.GOLD), false);
        line(source, LABEL + " crear", "Crea una tienda junto al cofre mas cercano y abre el editor.");
        line(source, LABEL + " crear admin", "Crea una tienda de staff con existencias infinitas.");
        line(source, LABEL + " editar", "Abre el editor de la tienda que tengas delante.");
        line(source, LABEL + " borrar", "Borra la tienda que tengas delante.");
        line(source, LABEL + " lista", "Lista tus tiendas y donde estan.");
        line(source, LABEL + " recargar", "Vuelve a leer la configuracion.");
        source.sendSuccess(() -> Component.literal(
                "Tambien puedes agacharte y hacer clic derecho en un tendero para editarlo.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String command, String description) {
        source.sendSuccess(() -> Component.literal(command).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  " + description).withStyle(ChatFormatting.GRAY)), false);
    }

    /**
     * Creates a shop in one step and opens its editor.
     *
     * <p>A selling shop needs a chest, and rather than making the player select one first, the nearest unclaimed
     * container is found for them. That removes the two-step dance - and the creation item that went with it - which was
     * the part nobody could guess without being told.</p>
     */
    private static int create(CommandContext<CommandSourceStack> context, ShopType type) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        BlockPos where = player.blockPosition();
        BlockPos container = null;
        if (type.needsContainer()) {
            container = findContainerNear(player);
            if (container == null) {
                context.getSource().sendFailure(Component.literal(
                        "No hay ningun cofre libre a menos de " + ShopConfig.get().maxContainerDistance
                                + " bloques. Pon un cofre cerca y vuelve a intentarlo."));
                return 0;
            }
        }

        ShopCreation.Outcome outcome = ShopCreation.create(player, type, ShopObjectKind.LIVING, where, container);
        if (!outcome.ok()) {
            context.getSource().sendFailure(outcome.error());
            return 0;
        }
        Shopkeeper shop = outcome.shop();
        if (shop.containerPos() != null) {
            BlockPos chest = shop.containerPos();
            context.getSource().sendSuccess(() -> Component.literal("Tienda creada usando el cofre en "
                    + chest.toShortString() + ".").withStyle(ChatFormatting.GREEN), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Tienda de staff creada con existencias infinitas.").withStyle(ChatFormatting.GREEN), false);
        }
        Net.openEditor(player, shop);
        return 1;
    }

    private static int edit(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        Shopkeeper shop = nearestShop(player);
        if (shop == null) {
            context.getSource().sendFailure(Component.literal(
                    "No hay ninguna tienda cerca. Acercate al tendero y vuelve a intentarlo."));
            return 0;
        }
        if (!EditorAccess.mayEdit(player, shop)) {
            context.getSource().sendFailure(Component.literal("Esa tienda no es tuya."));
            return 0;
        }
        Net.openEditor(player, shop);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        Shopkeeper shop = nearestShop(player);
        if (shop == null) {
            context.getSource().sendFailure(Component.literal("No hay ninguna tienda cerca."));
            return 0;
        }
        if (!EditorAccess.mayEdit(player, shop)) {
            context.getSource().sendFailure(Component.literal("Esa tienda no es tuya."));
            return 0;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        ServerLevel level = player.server.getLevel(shop.level());
        if (level != null) {
            ShopSpawner.despawn(level, shop, registry);
        }
        registry.remove(shop.id());
        context.getSource().sendSuccess(() -> Component.literal(
                "Tienda borrada. El cofre y su contenido siguen ahi.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ShopRegistry registry = ShopRegistry.get(player.server);
        List<Shopkeeper> shops = registry.shopsOf(player.getUUID());
        CommandSourceStack source = context.getSource();
        if (shops.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No tienes tiendas. Crea una con " + LABEL + " crear.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Tus tiendas (" + shops.size() + "):")
                .withStyle(ChatFormatting.GOLD), false);
        for (Shopkeeper shop : shops) {
            source.sendSuccess(() -> Component.literal("\u00b7 " + shop.displayName())
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("  " + shop.pos().toShortString() + " \u00b7 "
                            + shop.tradableOffers().size() + " tratos activos")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null && !FantasticShopkeepers.hasPermission(player,
                FantasticShopkeepers.Perms.RELOAD)) {
            context.getSource().sendFailure(Component.literal("No tienes permiso para esto."));
            return 0;
        }
        ShopConfig.load();
        context.getSource().sendSuccess(() -> Component.literal("Configuracion recargada.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * The registered shop nearest the player.
     *
     * <p>By distance to the shop's recorded position rather than by ray-tracing what the player is looking at. A shop's
     * mob can be behind a block or stuck in a wall, and an admin trying to fix that shop still has to be able to select
     * it.</p>
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

    /** The nearest container that is not already another shop's, or null when there is none in range. */
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
}
