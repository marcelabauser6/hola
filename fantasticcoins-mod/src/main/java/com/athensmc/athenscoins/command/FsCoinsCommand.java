package com.athensmc.athenscoins.command;

import com.athensmc.athenscoins.config.CoinsConfig;
import com.athensmc.athenscoins.menu.WalletMenu;
import com.athensmc.athenscoins.wallet.CoinFormat;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/**
 * {@code /fscoins} — everything the wallet system exposes to players and staff.
 */
public final class FsCoinsCommand {

    private static final SuggestionProvider<CommandSourceStack> COIN_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[] { "bronze", "silver", "gold" }, builder);

    private FsCoinsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fscoins")
                .then(Commands.literal("wallet")
                        .executes(context -> openWallet(context, self(context)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> openWallet(context,
                                        EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("balance")
                        .executes(context -> showBalance(context, self(context), true))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> showBalance(context,
                                        EntityArgument.getPlayer(context, "target"), false))))
                .then(Commands.literal("deposit")
                        .then(coinArgument()
                                .executes(context -> bank(context, true, Long.MAX_VALUE))
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                        .executes(context -> bank(context, true,
                                                LongArgumentType.getLong(context, "amount"))))))
                .then(Commands.literal("withdraw")
                        .then(coinArgument()
                                .executes(context -> bank(context, false, Long.MAX_VALUE))
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                        .executes(context -> bank(context, false,
                                                LongArgumentType.getLong(context, "amount"))))))
                .then(Commands.literal("pay")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(coinArgument()
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                                .executes(FsCoinsCommand::pay)))))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(coinArgument()
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                                .executes(context -> adminAdjust(context, AdminOp.GIVE))))))
                .then(Commands.literal("take")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(coinArgument()
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                                                .executes(context -> adminAdjust(context, AdminOp.TAKE))))))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(coinArgument()
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                                .executes(context -> adminAdjust(context, AdminOp.SET))))));

        dispatcher.register(root);

        // Short alias. Deliberately NOT plain "/wallet": on hybrid servers that name is often
        // already taken by an economy plugin, and overwriting it would break their command.
        dispatcher.register(Commands.literal("fswallet")
                .executes(context -> openWallet(context, self(context))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> coinArgument() {
        return Commands.argument("coin", StringArgumentType.word()).suggests(COIN_SUGGESTIONS);
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    // ------------------------------------------------------------------ wallet / balance

    private static int openWallet(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer viewer = self(context);
        NetworkHooks.openScreen(viewer,
                new SimpleMenuProvider(
                        (containerId, inventory, player) -> new WalletMenu(containerId, inventory, target),
                        Component.translatable("gui.athens_coins.wallet")),
                buffer -> WalletMenu.writeState(buffer, target));
        return 1;
    }

    private static int showBalance(CommandContext<CommandSourceStack> context, ServerPlayer target, boolean own) {
        CommandSourceStack source = context.getSource();
        Wallet wallet = WalletManager.walletOf(target);

        source.sendSuccess(() -> Component.translatable(
                        own ? "message.athens_coins.balance_header" : "message.athens_coins.balance_header_other",
                        target.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);

        for (CoinType type : CoinType.ORDERED) {
            long digital = wallet.get(type);
            int cash = WalletManager.countCash(target, type);
            MutableComponent line = Component.literal(" ")
                    .append(type.displayName().copy().withStyle(style -> style.withColor(type.color())))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("message.athens_coins.cash").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + CoinFormat.plain(cash)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("message.athens_coins.digital").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" " + CoinFormat.plain(digital)).withStyle(ChatFormatting.WHITE));
            source.sendSuccess(() -> line, false);
        }

        long digitalTotal = wallet.totalInBronze();
        long cashTotal = 0L;
        for (CoinType type : CoinType.ORDERED) {
            cashTotal += (long) WalletManager.countCash(target, type) * type.bronzeValue();
        }
        final long finalCashTotal = cashTotal;
        source.sendSuccess(() -> Component.translatable("message.athens_coins.balance_total",
                        CoinFormat.plain(finalCashTotal), CoinFormat.plain(digitalTotal))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // ------------------------------------------------------------------ banking

    private static int bank(CommandContext<CommandSourceStack> context, boolean depositing, long amount)
            throws CommandSyntaxException {
        ServerPlayer player = self(context);
        CoinType type = requireCoin(context);
        if (type == null) {
            return 0;
        }

        if (!CoinsConfig.BANKING_WITHOUT_ATM.get()) {
            BlockPos atm = WalletManager.findNearbyAtm(player);
            if (atm == null) {
                context.getSource().sendFailure(
                        Component.translatable("message.athens_coins.no_atm", CoinsConfig.ATM_RANGE.get()));
                return 0;
            }
        }

        WalletManager.Tx tx = depositing
                ? WalletManager.deposit(player, type, (int) Math.min(amount, Integer.MAX_VALUE))
                : WalletManager.withdraw(player, type, amount);

        if (tx.isEmpty()) {
            context.getSource().sendFailure(Component.translatable(
                    depositing ? "message.athens_coins.no_cash" : "message.athens_coins.no_funds",
                    type.displayName()));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(
                        depositing ? "message.athens_coins.deposited" : "message.athens_coins.withdrawn",
                        tx.cash(), type.displayName())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ------------------------------------------------------------------ transfers

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!CoinsConfig.ALLOW_PAY.get()) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.pay_disabled"));
            return 0;
        }
        ServerPlayer sender = self(context);
        ServerPlayer receiver = EntityArgument.getPlayer(context, "target");
        CoinType type = requireCoin(context);
        if (type == null) {
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");

        if (receiver.getUUID().equals(sender.getUUID())) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.pay_self"));
            return 0;
        }

        Wallet from = WalletManager.walletOf(sender);
        if (from.get(type) < amount) {
            context.getSource().sendFailure(
                    Component.translatable("message.athens_coins.no_funds", type.displayName()));
            return 0;
        }

        from.add(type, -amount);
        WalletManager.walletOf(receiver).add(type, amount);
        WalletManager.markDirty(sender);

        context.getSource().sendSuccess(() -> Component.translatable("message.athens_coins.pay_sent",
                        CoinFormat.plain(amount), type.displayName(), receiver.getGameProfile().getName())
                .withStyle(ChatFormatting.GREEN), false);
        receiver.sendSystemMessage(Component.translatable("message.athens_coins.pay_received",
                        CoinFormat.plain(amount), type.displayName(), sender.getGameProfile().getName())
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    // ------------------------------------------------------------------ admin

    private enum AdminOp { GIVE, TAKE, SET }

    private static int adminAdjust(CommandContext<CommandSourceStack> context, AdminOp op)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        CoinType type = requireCoin(context);
        if (type == null) {
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        Wallet wallet = WalletManager.walletOf(target);

        switch (op) {
            case GIVE -> wallet.add(type, amount);
            case TAKE -> wallet.add(type, -amount);
            case SET -> wallet.set(type, amount);
        }
        WalletManager.markDirty(target);

        long now = wallet.get(type);
        context.getSource().sendSuccess(() -> Component.translatable("message.athens_coins.admin_set",
                        target.getGameProfile().getName(), type.displayName(), CoinFormat.plain(now))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static CoinType requireCoin(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "coin");
        CoinType type = CoinType.byId(raw);
        if (type == null) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.unknown_coin", raw));
        }
        return type;
    }
}
