package com.athensmc.athenscoins.command;

import com.athensmc.athenscoins.menu.WalletMenu;
import com.athensmc.athenscoins.wallet.CoinFormat;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

/**
 * {@code /fscoins} with two subcommands:
 *
 * <ul>
 *   <li>{@code wallet} — opens the wallet GUI</li>
 *   <li>{@code balance} — prints cash and digital balances to chat</li>
 * </ul>
 *
 * <p>Depositing and withdrawing is deliberately not available here: that is what the ATM block
 * is for.</p>
 */
public final class FsCoinsCommand {

    private FsCoinsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fscoins")
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
                                        EntityArgument.getPlayer(context, "target"), false)))));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    // ------------------------------------------------------------------ /fscoins wallet

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

    // ------------------------------------------------------------------ /fscoins balance

    private static int showBalance(CommandContext<CommandSourceStack> context, ServerPlayer target, boolean own) {
        CommandSourceStack source = context.getSource();
        Wallet wallet = WalletManager.walletOf(target);

        source.sendSuccess(() -> Component.translatable(
                        own ? "message.athens_coins.balance_header" : "message.athens_coins.balance_header_other",
                        target.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);

        long cashTotal = 0L;
        for (CoinType type : CoinType.ORDERED) {
            long digital = wallet.get(type);
            int cash = WalletManager.countCash(target, type);
            cashTotal += (long) cash * type.bronzeValue();

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

        final long finalCashTotal = cashTotal;
        final long digitalTotal = wallet.totalInBronze();
        source.sendSuccess(() -> Component.translatable("message.athens_coins.balance_total",
                        CoinFormat.plain(finalCashTotal), CoinFormat.plain(digitalTotal))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
