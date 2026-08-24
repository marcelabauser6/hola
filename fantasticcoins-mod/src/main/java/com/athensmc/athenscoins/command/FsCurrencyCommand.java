package com.athensmc.athenscoins.command;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2COpenWalletPacket;
import com.athensmc.athenscoins.transfer.PendingTransfer;
import com.athensmc.athenscoins.transfer.TransferManager;
import com.athensmc.athenscoins.transfer.TransferRequests;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletManager;
import com.athensmc.athenscoins.wallet.WalletSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /fscurrency} — the Fantastic Currency command. Command names stay in English on purpose;
 * everything the player reads is translated.
 *
 * <pre>
 * /fscurrency wallet  [player]              opens the wallet
 * /fscurrency balance [player]              coins and cash in chat
 * /fscurrency transfer &lt;amount&gt; &lt;player&gt;    asks a player to accept a payment
 * /fscurrency reload                        re-reads the config (permission 2)
 * </pre>
 *
 * <p>{@code transfer accept|deny <id>} also exists, but only while the player actually has a
 * request waiting: it backs the chat buttons and stays out of tab-completion otherwise.</p>
 */
public final class FsCurrencyCommand {

    private FsCurrencyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fscurrency")
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

                .then(Commands.literal("transfer")
                        // Hidden unless there is something to answer, so the amount argument is
                        // the only thing suggested during a normal transfer.
                        .then(Commands.literal("accept")
                                .requires(FsCurrencyCommand::hasPendingTransfer)
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> answer(context, true))))
                        .then(Commands.literal("deny")
                                .requires(FsCurrencyCommand::hasPendingTransfer)
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> answer(context, false))))
                        // No suggestions here: the amount is typed by hand.
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(FsCurrencyCommand::requestTransfer))))

                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(FsCurrencyCommand::reload)));
    }

    private static boolean hasPendingTransfer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                && TransferManager.hasPendingFor(player.getUUID());
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    // ------------------------------------------------------------------ wallet

    private static int openWallet(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer viewer = self(context);
        // Sent as a plain packet, not a container menu, so nothing touches the player's inventory.
        ModNetwork.toPlayer(viewer, new S2COpenWalletPacket(WalletSnapshot.of(target)));
        return 1;
    }

    // ------------------------------------------------------------------ balance

    private static int showBalance(CommandContext<CommandSourceStack> context, ServerPlayer target, boolean own) {
        CommandSourceStack source = context.getSource();
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        long cash = com.athensmc.athenscoins.api.FantasticCurrencyAPI.getBalance(
                target.server, target.getUUID());

        source.sendSuccess(() -> Component.translatable(
                        own ? "message.athens_coins.balance_header" : "message.athens_coins.balance_header_other",
                        target.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.literal(" ")
                .append(Component.literal(settings.currencyName)
                        .withStyle(style -> style.withColor(settings.cashColorRgb())))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(Money.format(cash, settings.currencySymbol))
                        .withStyle(ChatFormatting.WHITE)), false);

        source.sendSuccess(() -> Component.translatable("message.athens_coins.coins_header")
                .withStyle(ChatFormatting.GRAY), false);
        for (CoinType type : CoinType.ORDERED) {
            int count = WalletManager.countCoins(target, type);
            long worth = Money.multiply(type.cashValueCents(), count);
            MutableComponent line = Component.literal("  ")
                    .append(type.shortName().copy()
                            .withStyle(style -> style.withColor(settings.coinColor(type))))
                    .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  (" + Money.format(worth, settings.currencySymbol) + ")")
                            .withStyle(ChatFormatting.DARK_GRAY));
            source.sendSuccess(() -> line, false);
        }

        long inventoryValue = WalletManager.inventoryValueCents(target);
        long total = Money.canAdd(cash, inventoryValue) ? cash + inventoryValue : Money.MAX_CENTS;
        source.sendSuccess(() -> Component.translatable("message.athens_coins.balance_total",
                        Money.format(inventoryValue, settings.currencySymbol),
                        Money.format(total, settings.currencySymbol))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // ------------------------------------------------------------------ transfer

    private static int requestTransfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = self(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        long cents;
        try {
            cents = Money.parse(StringArgumentType.getString(context, "amount"));
        } catch (Money.InvalidAmountException exception) {
            context.getSource().sendFailure(Component.translatable(exception.reasonKey()));
            return 0;
        }

        // Policy, the request itself and the recipient's accept/deny card all live in
        // TransferRequests, shared with the ATM screen so both routes enforce the same rules.
        TransferRequests.Outcome outcome = TransferRequests.request(sender, target, cents);
        if (!outcome.ok()) {
            context.getSource().sendFailure(outcome.message());
            return 0;
        }
        context.getSource().sendSuccess(
                () -> outcome.message().copy().withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int answer(CommandContext<CommandSourceStack> context, boolean accepted)
            throws CommandSyntaxException {
        ServerPlayer responder = self(context);
        int id = IntegerArgumentType.getInteger(context, "id");
        CurrencyConfig.Settings settings = CurrencyConfig.get();

        PendingTransfer request = TransferManager.get(id);
        if (request == null) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfer_unknown"));
            return 0;
        }
        // Only the intended recipient may answer, whatever id they type.
        if (!request.target().equals(responder.getUUID())) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfer_not_yours"));
            return 0;
        }
        if (request.isExpired(System.currentTimeMillis())) {
            finish(id, responder);
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfer_unknown"));
            return 0;
        }

        ServerPlayer sender = responder.server.getPlayerList().getPlayer(request.sender());
        String amount = Money.format(request.cents(), settings.currencySymbol);

        if (!accepted) {
            finish(id, responder);
            responder.sendSystemMessage(Component.translatable("message.athens_coins.transfer_rejected_target",
                    amount, request.senderName()).withStyle(ChatFormatting.GRAY));
            if (sender != null) {
                sender.sendSystemMessage(Component.translatable("message.athens_coins.transfer_rejected_sender",
                        amount, request.targetName()).withStyle(ChatFormatting.RED));
            }
            return 1;
        }

        if (sender == null) {
            finish(id, responder);
            context.getSource().sendFailure(
                    Component.translatable("message.athens_coins.transfer_sender_offline", request.senderName()));
            return 0;
        }
        // Re-check funds: nothing was held in escrow while the request was pending.
        if (!WalletManager.transfer(sender, responder, request.cents())) {
            finish(id, responder);
            context.getSource().sendFailure(
                    Component.translatable("message.athens_coins.transfer_sender_broke", request.senderName()));
            sender.sendSystemMessage(Component.translatable("message.athens_coins.insufficient_funds", amount)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        finish(id, responder);
        responder.sendSystemMessage(Component.translatable("message.athens_coins.transfer_received",
                        amount, request.senderName())
                .withStyle(style -> style.withColor(settings.cashColorRgb())));
        sender.sendSystemMessage(Component.translatable("message.athens_coins.transfer_completed",
                        amount, request.targetName())
                .withStyle(style -> style.withColor(settings.cashColorRgb())));
        return 1;
    }

    /** Drops the request and hides accept/deny again if nothing else is pending. */
    private static void finish(int id, ServerPlayer responder) {
        TransferManager.remove(id);
        TransferManager.refreshCommands(responder.server, responder.getUUID());
    }

    // ------------------------------------------------------------------ reload

    private static int reload(CommandContext<CommandSourceStack> context) {
        CurrencyConfig.LoadResult result = CurrencyConfig.load();
        if (!result.ok()) {
            context.getSource().sendFailure(Component.translatable(
                    "message.athens_coins.reload_failed", result.detail()));
            return 0;
        }
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        context.getSource().sendSuccess(() -> Component.translatable("message.athens_coins.reload_ok",
                        CurrencyConfig.FILE_NAME)
                .withStyle(ChatFormatting.GREEN), true);
        context.getSource().sendSuccess(() -> Component.translatable("message.athens_coins.reload_rates",
                        Money.format(settings.coinValueCents(CoinType.BRONZE), settings.currencySymbol),
                        Money.format(settings.coinValueCents(CoinType.SILVER), settings.currencySymbol),
                        Money.format(settings.coinValueCents(CoinType.GOLD), settings.currencySymbol))
                .withStyle(ChatFormatting.GRAY), false);
        if (!result.detail().startsWith("loaded") && !result.detail().startsWith("created")) {
            context.getSource().sendSuccess(() -> Component.translatable(
                            "message.athens_coins.reload_adjusted", result.detail())
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        context.getSource().sendSuccess(() -> Component.translatable("message.athens_coins.reload_note")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }
}
