package com.athensmc.athenscoins.transfer;

import com.athensmc.athenscoins.api.FantasticCurrencyAPI;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The single validated path for starting a transfer.
 *
 * <p>The chat command owned all of this: the policy checks, the request, and the accept/deny card.
 * The ATM screen now starts transfers too, and a second copy of six checks is exactly how a rule
 * ends up enforced on one route and not the other. Both callers go through {@link #request} and get
 * back a message to show whoever asked.</p>
 */
public final class TransferRequests {

    private TransferRequests() {
    }

    /**
     * The sender-facing result. {@code ok} distinguishes a started request from a refusal, so
     * callers can style it as success or failure in their own idiom - chat feedback for the command,
     * an inline red line for the screen.
     */
    public record Outcome(boolean ok, Component message) {

        static Outcome fail(String key, Object... args) {
            return new Outcome(false, Component.translatable(key, args));
        }
    }

    /**
     * Validates and registers a transfer request, and sends the recipient their accept/deny card.
     *
     * <p>Nothing is held in escrow: the balance is checked here and checked again on accept, which
     * is why a request can still fail later. That is deliberate - freezing funds for a minute per
     * request would be worse than telling the sender their money moved elsewhere in the meantime.</p>
     */
    public static Outcome request(ServerPlayer sender, ServerPlayer target, long cents) {
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        if (!settings.transfersEnabled) {
            return Outcome.fail("message.athens_coins.transfers_disabled");
        }
        if (target == null) {
            return Outcome.fail("message.athens_coins.transfer_no_target");
        }
        if (target.getUUID().equals(sender.getUUID())) {
            return Outcome.fail("message.athens_coins.transfer_self");
        }
        if (cents <= 0L) {
            return Outcome.fail("message.athens_coins.amount_positive");
        }
        if (cents < settings.minTransferCents()) {
            return Outcome.fail("message.athens_coins.transfer_too_small",
                    Money.format(settings.minTransferCents(), settings.currencySymbol));
        }
        if (cents > settings.maxTransferCents()) {
            return Outcome.fail("message.athens_coins.transfer_too_big",
                    Money.format(settings.maxTransferCents(), settings.currencySymbol));
        }
        if (!BankManager.hasAccount(sender) || !BankManager.hasAccount(target)) {
            return Outcome.fail("message.athens_coins.atm_needs_account");
        }
        if (!FantasticCurrencyAPI.has(sender, cents)) {
            return Outcome.fail("message.athens_coins.insufficient_funds",
                    Money.format(cents, settings.currencySymbol));
        }

        PendingTransfer pending = TransferManager.create(sender, target, cents);
        String amount = Money.format(cents, settings.currencySymbol);

        target.sendSystemMessage(Component.translatable("message.athens_coins.transfer_incoming",
                        Component.literal(sender.getGameProfile().getName())
                                .withStyle(ChatFormatting.WHITE),
                        Component.literal(amount)
                                .withStyle(style -> style.withColor(settings.cashColorRgb()).withBold(true)))
                .withStyle(ChatFormatting.GRAY));
        target.sendSystemMessage(Component.literal("  ")
                .append(button("message.athens_coins.button_accept", ChatFormatting.GREEN,
                        "/fscurrency transfer accept " + pending.id(),
                        "message.athens_coins.button_accept_hover"))
                .append(Component.literal("   "))
                .append(button("message.athens_coins.button_deny", ChatFormatting.RED,
                        "/fscurrency transfer deny " + pending.id(),
                        "message.athens_coins.button_deny_hover")));
        // Let the recipient's client know accept/deny are available to them right now.
        TransferManager.refreshCommands(target.server, target.getUUID());

        return new Outcome(true, Component.translatable("message.athens_coins.transfer_sent",
                amount, target.getGameProfile().getName(), settings.transferRequestTimeoutSeconds));
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
}
