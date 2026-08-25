package com.athensmc.fsshopkeepers.command;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.item.ModItems;
import com.athensmc.fsshopkeepers.money.Cash;
import com.athensmc.fsshopkeepers.net.EditorAccess;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.shop.ShopCreation;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.ShopType;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /fskeepers} command.
 *
 * <p>One command with eight subcommands, and that is the whole surface. Shopkeepers had twenty-five, most of which
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
                .then(Commands.literal("editor").executes(ShopCommands::giveWand))
                .then(Commands.literal("editar").executes(ShopCommands::edit))
                .then(Commands.literal("mover")
                        .executes(ShopCommands::moveList)
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1))
                                .executes(context -> move(context,
                                        IntegerArgumentType.getInteger(context, "numero")))))
                .then(Commands.literal("saldo").executes(ShopCommands::balance))
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
        line(source, LABEL + " editor", "Te da la varita del editor.");
        line(source, LABEL + " editar", "Abre el editor de la tienda que tengas delante.");
        line(source, LABEL + " mover", "Lista tus tiendas; con un numero, trae esa aqui.");
        line(source, LABEL + " saldo", "Muestra el dinero que la tienda ve en tu cuenta.");
        line(source, LABEL + " borrar", "Borra la tienda que tengas delante.");
        line(source, LABEL + " lista", "Lista tus tiendas y donde estan.");
        line(source, LABEL + " recargar", "Vuelve a leer la configuracion.");
        source.sendSuccess(() -> Component.literal(
                "Con la varita en la mano, clic derecho en un tendero abre su editor.")
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
    /**
     * Creates a shop where the player stands and opens its editor.
     *
     * <p>No chest, and none looked for. An earlier version hunted for the nearest unclaimed container and linked it
     * silently, which produced shops backed by a chest their owner had never seen and could not find - so every trade
     * showed as out of stock forever. A shop with no chest has unlimited stock, which is what a shop selling for money
     * wants anyway.</p>
     */
    private static int create(CommandContext<CommandSourceStack> context, ShopType type) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ShopCreation.Outcome outcome = ShopCreation.create(player, type, ShopObjectKind.LIVING,
                player.blockPosition(), null);
        if (!outcome.ok()) {
            context.getSource().sendFailure(outcome.error());
            return 0;
        }
        Shopkeeper shop = outcome.shop();
        context.getSource().sendSuccess(() -> Component.literal("Tienda creada con existencias infinitas. "
                + "Abriendo el editor...").withStyle(ChatFormatting.GREEN), false);
        Net.openEditor(player, shop);
        return 1;
    }

    /**
     * Hands over the editor wand.
     *
     * <p>The wand exists because the editor used to open on a sneaking right-click, and that is the same gesture Easy
     * Villagers uses to pick a villager up as an item. That mod acts on the client and sends its own packet, so it took the
     * shopkeeper away before the server heard about the click. An item in the hand collides with nothing.</p>
     */
    private static int giveWand(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        if (!FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.EDIT_OWN)
                && !FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.EDIT_OTHERS)) {
            context.getSource().sendFailure(Component.literal("No tienes permiso para editar tiendas."));
            return 0;
        }
        ItemStack wand = ModItems.wand();
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Toma la varita del editor. Con ella en la mano, clic derecho en un tendero o en un cartel "
                        + "para abrir su editor.").withStyle(ChatFormatting.GREEN), false);
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

    /** Every shop this player is allowed to move, in a stable order. */
    private static List<Shopkeeper> movableShops(ServerPlayer player) {
        ShopRegistry registry = ShopRegistry.get(player.server);
        List<Shopkeeper> shops = new ArrayList<>();
        for (Shopkeeper shop : registry.all()) {
            if (shop.objectKind().isPlaced() && EditorAccess.mayEdit(player, shop)) {
                shops.add(shop);
            }
        }
        return shops;
    }

    /** A shop's label in the move list: its name, or its type when it has none, plus where it is. */
    private static String moveLabel(Shopkeeper shop) {
        return shop.displayName() + " @ " + shop.pos().toShortString();
    }

    /**
     * Lists the shops that can be moved.
     *
     * <p>Printed with a number rather than asked for by name, because shop names repeat, may be blank, and may contain
     * spaces. A number is unambiguous and short to type.</p>
     */
    private static int moveList(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        List<Shopkeeper> shops = movableShops(player);
        CommandSourceStack source = context.getSource();
        if (shops.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No tienes ninguna tienda que mover.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Tiendas que puedes mover:")
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < shops.size(); i++) {
            Shopkeeper shop = shops.get(i);
            int number = i + 1;
            String dimension = shop.level().location().getPath();
            source.sendSuccess(() -> Component.literal(number + ". ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(shop.displayName()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + shop.pos().toShortString() + " en " + dimension)
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        source.sendSuccess(() -> Component.literal("Usa " + LABEL + " mover <numero> para traerla donde estas.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Moves a chosen shop to where the player is standing, from wherever it was.
     *
     * <p>Across dimensions too: the body is removed from the level it was in and re-created in the one the player is
     * standing in. Re-created rather than teleported, so the mob arrives with its variant and its flags applied the way a
     * fresh one would, and the registry is re-indexed so the shop stops being protected at its old position.</p>
     */
    private static int move(CommandContext<CommandSourceStack> context, int number) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        List<Shopkeeper> shops = movableShops(player);
        if (shops.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No tienes ninguna tienda que mover."));
            return 0;
        }
        if (number < 1 || number > shops.size()) {
            context.getSource().sendFailure(Component.literal("No hay una tienda con el numero " + number
                    + ". Usa " + LABEL + " mover para ver la lista."));
            return 0;
        }
        Shopkeeper shop = shops.get(number - 1);

        ServerLevel destination = player.serverLevel();
        BlockPos target = player.blockPosition();
        ShopRegistry registry = ShopRegistry.get(player.server);

        Shopkeeper alreadyThere = registry.byPosition(destination.dimension(), target);
        if (alreadyThere != null && !alreadyThere.id().equals(shop.id())) {
            context.getSource().sendFailure(Component.literal("Ya hay otra tienda justo ahi. Muevete un poco."));
            return 0;
        }

        // Despawned in the level it is currently in, which may not be the one the player is standing in.
        ServerLevel origin = player.server.getLevel(shop.level());
        if (origin != null) {
            ShopSpawner.despawn(origin, shop, registry);
        }
        // A chest cannot follow the shop, so a shop that had one keeps selling only if it is still in range.
        if (shop.usesContainer()) {
            boolean sameWorld = shop.level().equals(destination.dimension());
            double distance = sameWorld ? Math.sqrt(shop.containerPos().distSqr(target)) : Double.MAX_VALUE;
            if (distance > ShopConfig.get().maxContainerDistance) {
                shop.setContainerPos(null);
                context.getSource().sendSuccess(() -> Component.literal(
                        "El cofre quedaba demasiado lejos, asi que la tienda pasa a tener existencias infinitas.")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }
        shop.setLevel(destination.dimension());
        shop.setPos(target);
        registry.refresh(shop);
        ShopSpawner.ensureSpawned(destination, shop, registry);

        context.getSource().sendSuccess(() -> Component.literal("\"" + shop.displayName()
                + "\" movida aqui.").withStyle(ChatFormatting.GREEN), false);
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

    /**
     * Prints exactly what the shops see of a player's money.
     *
     * <p>Here because "it does not recognise my Cash" is impossible to diagnose from the outside. Fantastic Currency keeps
     * two pots - a bank principal and a small spendable wallet float drawn from it - and a shop can be looking at one while
     * the player is looking at the other. This prints both, the total, and whether the bridge to the bank is even
     * connected, so the answer stops being a guess.</p>
     */
    private static int balance(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        if (!Cash.available()) {
            source.sendFailure(Component.literal(
                    "Fantastic Currency no esta conectado, asi que las tiendas no pueden cobrar dinero."));
            return 0;
        }
        long wallet = Cash.balance(player.server, player.getUUID());
        long account = Cash.accountBalance(player.server, player.getUUID());
        long total = Cash.spendable(player.server, player.getUUID());
        int number = Cash.accountNumber(player.server, player.getUUID());

        source.sendSuccess(() -> Component.literal("Lo que las tiendas ven de tu dinero")
                .withStyle(ChatFormatting.GOLD), false);
        detail(source, "Cartera", Cash.format(wallet));
        detail(source, "Cuenta del banco", Cash.format(account));
        detail(source, "Total que puedes gastar", Cash.format(total));
        detail(source, "Numero de cuenta", number > 0 ? String.valueOf(number) : "sin cuenta");
        detail(source, "Puede cobrar de la cuenta", Cash.canChargeAccount() ? "si" : "no");
        if (number <= 0) {
            source.sendSuccess(() -> Component.literal(
                    "No tienes cuenta en ningun banco, y sin cuenta Fantastic Currency deja el saldo en cero. "
                            + "Abre una en un cajero.").withStyle(ChatFormatting.RED), false);
        } else if (!Cash.canChargeAccount() && account > 0L) {
            source.sendSuccess(() -> Component.literal(
                    "Tienes dinero en la cuenta pero este mod no puede tocarla en esta version de Fantastic "
                            + "Currency, asi que solo podras gastar la cartera.")
                    .withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    private static void detail(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
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

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal(
                    "Este comando lo tiene que usar un jugador dentro del mundo."));
        }
        return player;
    }
}
