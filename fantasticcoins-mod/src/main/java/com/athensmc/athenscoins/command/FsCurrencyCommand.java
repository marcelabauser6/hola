package com.athensmc.athenscoins.command;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.block.BankTerminalBlock;
import com.athensmc.athenscoins.item.ModItems;
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
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

                // Bank administration. Operator only, and deliberately not available to founders: a
                // founder runs a bank, and deleting somebody else's is not part of running yours.
                .then(Commands.literal("bank")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(FsCurrencyCommand::listBanks))
                        .then(Commands.literal("give")
                                .then(Commands.argument("bank", StringArgumentType.greedyString())
                                        .suggests(FsCurrencyCommand::suggestBanks)
                                        .executes(FsCurrencyCommand::giveTerminal)))
                        .then(Commands.literal("purge")
                                .then(Commands.argument("bank", StringArgumentType.greedyString())
                                        .suggests(FsCurrencyCommand::suggestBanks)
                                        .executes(FsCurrencyCommand::purgeBank)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("bank", StringArgumentType.greedyString())
                                        .suggests(FsCurrencyCommand::suggestBanks)
                                        .executes(FsCurrencyCommand::deleteBank))))

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


    // ------------------------------------------------------------------ bank administration

    private static CompletableFuture<Suggestions> suggestBanks(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        MinecraftServer server = context.getSource().getServer();
        // Quoted, because bank names contain spaces and a bare suggestion would be unusable.
        for (Bank bank : BankData.get(server).banks()) {
            builder.suggest("\"" + bank.name() + "\"");
        }
        return builder.buildFuture();
    }

    /**
     * Resolves the {@code bank} argument by name.
     *
     * <p>By name rather than by a generated id because the name is what the operator can see - on the
     * terminal, in the central bank's list, in this command's own output. Surrounding quotes are stripped
     * so the value the suggestion offers can be used verbatim.</p>
     */
    @Nullable
    private static Bank resolveBank(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "bank").trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        Bank bank = BankData.get(context.getSource().getServer()).bankByName(raw);
        if (bank == null) {
            context.getSource().sendFailure(Component.translatable(
                    "message.athens_coins.admin_no_bank", raw));
        }
        return bank;
    }

    private static int listBanks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BankData data = BankData.get(source.getServer());
        List<Bank> banks = data.banks();
        if (banks.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.athens_coins.admin_no_banks")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        String symbol = CurrencyConfig.get().currencySymbol;
        source.sendSuccess(() -> Component.translatable("message.athens_coins.admin_list_header",
                banks.size()).withStyle(ChatFormatting.GOLD), false);
        for (Bank bank : banks) {
            int accounts = data.accountsOf(bank.id()).size();
            // Where it stands, or that it stands nowhere - which is the state that used to be invisible and
            // is exactly what an operator needs to see before handing out a replacement terminal.
            Component seat = bank.hasSeat()
                    ? Component.literal(bank.terminalPos().getX() + " " + bank.terminalPos().getY()
                            + " " + bank.terminalPos().getZ()).withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("message.athens_coins.admin_no_seat")
                            .withStyle(ChatFormatting.RED);
            source.sendSuccess(() -> Component.literal(" ")
                    .append(Component.literal(bank.name())
                            .withStyle(style -> style.withColor(bank.themeColor())))
                    .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("message.athens_coins.admin_list_line",
                                    accounts, Money.format(bank.reserve(), symbol))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  "))
                    .append(seat), false);
        }
        source.sendSuccess(() -> Component.translatable("message.athens_coins.admin_list_note")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return banks.size();
    }

    /**
     * Hands over a terminal bound to a chosen bank.
     *
     * <p>The recovery path for a terminal destroyed without its drop being picked up. Breaking one normally
     * returns a bound terminal already, so this is for accidents - lava, a creeper, a careless
     * {@code /setblock} - rather than the everyday case.</p>
     */
    private static int giveTerminal(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer admin = self(context);
        Bank bank = resolveBank(context);
        if (bank == null) {
            return 0;
        }
        ItemStack terminal = BankTerminalBlock.bind(
                new ItemStack(ModItems.BANK_TERMINAL_ITEM.get()), bank);
        if (!admin.getInventory().add(terminal)) {
            admin.drop(terminal, false);
        }
        if (bank.hasSeat()) {
            // Not an error: an operator may well want a spare. But placing it will be refused while the
            // original is standing, and finding that out at placement time would be worse.
            context.getSource().sendSuccess(() -> Component.translatable(
                            "message.athens_coins.admin_given_seated", bank.name())
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable(
                            "message.athens_coins.admin_given", bank.name())
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return 1;
    }

    private static int purgeBank(CommandContext<CommandSourceStack> context) {
        Bank bank = resolveBank(context);
        if (bank == null) {
            return 0;
        }
        int closed = BankData.get(context.getSource().getServer()).purgeAccountsOf(bank.id());
        context.getSource().sendSuccess(() -> Component.translatable(
                        "message.athens_coins.admin_purged", closed, bank.name())
                .withStyle(ChatFormatting.YELLOW), true);
        return closed;
    }

    private static int deleteBank(CommandContext<CommandSourceStack> context) {
        Bank bank = resolveBank(context);
        if (bank == null) {
            return 0;
        }
        BankData data = BankData.get(context.getSource().getServer());
        int accounts = data.accountsOf(bank.id()).size();
        String name = bank.name();
        if (!data.deleteBank(bank.id())) {
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                        "message.athens_coins.admin_deleted", name, accounts)
                .withStyle(ChatFormatting.RED), true);
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
