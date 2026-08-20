package com.athensmc.athenscoins.command;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.menu.WalletMenu;
import com.athensmc.athenscoins.transfer.PendingTransfer;
import com.athensmc.athenscoins.transfer.TransferManager;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.Wallet;
import com.athensmc.athenscoins.wallet.WalletManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /fscurrency} — the Fantastic Currency command.
 *
 * <pre>
 * /fscurrency wallet  [player]        opens the wallet GUI
 * /fscurrency balance [player]        prints coins and cash to chat
 * /fscurrency transfer &lt;amount&gt; &lt;player&gt;   asks a player to accept a payment
 * /fscurrency transfer accept|deny &lt;id&gt;    answer a request (used by the chat buttons)
 * /fscurrency reload                  re-reads the config from disk (permission 2)
 * </pre>
 */
public final class FsCurrencyCommand {

    /** Handy amounts, plus whatever the player can actually afford. */
    private static final SuggestionProvider<CommandSourceStack> AMOUNT_SUGGESTIONS =
            (context, builder) -> {
                List<String> options = new ArrayList<>(List.of("1", "5", "10", "50", "100"));
                if (context.getSource().getEntity() instanceof ServerPlayer player) {
                    long balance = WalletManager.accountOf(player).balance();
                    if (balance > 0L) {
                        options.add(0, Money.plain(balance));
                    }
                }
                // Goes through SharedSuggestionProvider so entries are filtered by what the
                // player has typed so far.
                return SharedSuggestionProvider.suggest(options, builder);
            };

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
                        // Literals are matched before arguments, so "accept"/"deny" never
                        // collide with an amount (an amount has to parse as a number).
                        .then(Commands.literal("accept")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> answer(context, true))))
                        .then(Commands.literal("deny")
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(context -> answer(context, false))))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .suggests(AMOUNT_SUGGESTIONS)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(FsCurrencyCommand::requestTransfer))))

                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(FsCurrencyCommand::reload)));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static String symbol() {
        return CurrencyConfig.get().currencySymbol;
    }

    // ------------------------------------------------------------------ wallet

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

    // ------------------------------------------------------------------ balance

    private static int showBalance(CommandContext<CommandSourceStack> context, ServerPlayer target, boolean own) {
        CommandSourceStack source = context.getSource();
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        Wallet account = WalletManager.accountOf(target);

        source.sendSuccess(() -> Component.translatable(
                        own ? "message.athens_coins.balance_header" : "message.athens_coins.balance_header_other",
                        target.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);

        // Fantastic Cash
        source.sendSuccess(() -> Component.literal(" ")
                .append(Component.literal(settings.currencyName)
                        .withStyle(style -> style.withColor(settings.cashColorRgb())))
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(Money.format(account.balance(), settings.currencySymbol))
                        .withStyle(ChatFormatting.WHITE)), false);

        // Physical coins
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
        long total = Money.clampBalance(account.balance() + inventoryValue);
        source.sendSuccess(() -> Component.translatable("message.athens_coins.balance_total",
                        Money.format(inventoryValue, settings.currencySymbol),
                        Money.format(total, settings.currencySymbol))
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    // ------------------------------------------------------------------ transfer

    private static int requestTransfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        if (!settings.transfersEnabled) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfers_disabled"));
            return 0;
        }

        ServerPlayer sender = self(context);
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        long cents;
        try {
            cents = Money.parse(StringArgumentType.getString(context, "amount"));
        } catch (Money.InvalidAmountException exception) {
            context.getSource().sendFailure(Component.translatable(exception.reasonKey()));
            return 0;
        }

        if (target.getUUID().equals(sender.getUUID())) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfer_self"));
            return 0;
        }
        if (cents < settings.minTransferCents()) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfer_too_small",
                    Money.format(settings.minTransferCents(), settings.currencySymbol)));
            return 0;
        }
        if (cents > settings.maxTransferCents()) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfer_too_big",
                    Money.format(settings.maxTransferCents(), settings.currencySymbol)));
            return 0;
        }
        if (!WalletManager.accountOf(sender).canAfford(cents)) {
            context.getSource().sendFailure(Component.translatable("message.athens_coins.insufficient_funds",
                    Money.format(cents, settings.currencySymbol)));
            return 0;
        }

        PendingTransfer request = TransferManager.create(sender, target, cents);
        String amount = Money.format(cents, settings.currencySymbol);

        // The recipient decides.
        target.sendSystemMessage(Component.translatable("message.athens_coins.transfer_incoming",
                        Component.literal(sender.getGameProfile().getName())
                                .withStyle(ChatFormatting.WHITE),
                        Component.literal(amount)
                                .withStyle(style -> style.withColor(settings.cashColorRgb()).withBold(true)))
                .withStyle(ChatFormatting.GRAY));
        target.sendSystemMessage(Component.literal("  ")
                .append(button("message.athens_coins.button_accept", ChatFormatting.GREEN,
                        "/fscurrency transfer accept " + request.id(),
                        "message.athens_coins.button_accept_hover"))
                .append(Component.literal("   "))
                .append(button("message.athens_coins.button_deny", ChatFormatting.RED,
                        "/fscurrency transfer deny " + request.id(),
                        "message.athens_coins.button_deny_hover")));

        context.getSource().sendSuccess(() -> Component.translatable("message.athens_coins.transfer_sent",
                        amount, target.getGameProfile().getName(),
                        settings.transferRequestTimeoutSeconds)
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static MutableComponent button(String labelKey, ChatFormatting color,
                                           String command, String hoverKey) {
        return Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(hoverKey))));
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
            TransferManager.remove(id);
            context.getSource().sendFailure(Component.translatable("message.athens_coins.transfer_unknown"));
            return 0;
        }

        ServerPlayer sender = responder.server.getPlayerList().getPlayer(request.sender());
        String amount = Money.format(request.cents(), settings.currencySymbol);

        if (!accepted) {
            TransferManager.remove(id);
            responder.sendSystemMessage(Component.translatable("message.athens_coins.transfer_rejected_target",
                    amount, request.senderName()).withStyle(ChatFormatting.GRAY));
            if (sender != null) {
                sender.sendSystemMessage(Component.translatable("message.athens_coins.transfer_rejected_sender",
                        amount, request.targetName()).withStyle(ChatFormatting.RED));
            }
            return 1;
        }

        if (sender == null) {
            TransferManager.remove(id);
            context.getSource().sendFailure(
                    Component.translatable("message.athens_coins.transfer_sender_offline", request.senderName()));
            return 0;
        }
        // Re-check funds: nothing was held in escrow while the request was pending.
        if (!WalletManager.transfer(sender, responder, request.cents())) {
            TransferManager.remove(id);
            context.getSource().sendFailure(
                    Component.translatable("message.athens_coins.transfer_sender_broke", request.senderName()));
            sender.sendSystemMessage(Component.translatable("message.athens_coins.insufficient_funds", amount)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        TransferManager.remove(id);
        responder.sendSystemMessage(Component.translatable("message.athens_coins.transfer_received",
                        amount, request.senderName())
                .withStyle(style -> style.withColor(settings.cashColorRgb())));
        sender.sendSystemMessage(Component.translatable("message.athens_coins.transfer_completed",
                        amount, request.targetName())
                .withStyle(style -> style.withColor(settings.cashColorRgb())));
        return 1;
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
