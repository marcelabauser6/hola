package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An offer of a bank account, waiting on the person it is for.
 *
 * <p>This replaces the confirmation the <em>banker</em> used to get. Every other irreversible action is
 * now confirmed by a dialog inside the screen, where the click happened. Opening an account is the one
 * exception, and not for want of somewhere to put a dialog: an account is an agreement between a bank and
 * a customer, and the person who should be asked is the customer. A banker confirming their own decision
 * to sign somebody up confirms nothing.</p>
 *
 * <p>So the question goes to the prospective holder, in chat, with the terms in it - the fee, how often
 * it is charged, and the card ceiling - because those are what they are agreeing to and they will not see
 * the settings form. Chat is the right medium here for the same reason transfers use it: the recipient is
 * not standing at a screen when the offer arrives.</p>
 *
 * <p>Not persisted. An offer nobody answered before a restart is an offer the banker can make again, and
 * an account created from a proposal made in a previous session is worse than one that has to be
 * re-offered.</p>
 */
public final class AccountOffers {

    /** Long enough to read the terms; short enough that a forgotten offer cannot be accepted later. */
    private static final long TIMEOUT_MILLIS = 120_000L;

    /**
     * @param holder who is being offered the account, and the only player who may answer
     * @param bankId the offering bank, re-resolved on acceptance in case it has gone
     */
    public record Offer(int id, UUID holder, UUID bankId, java.util.UUID banker,
                        String bankName, long expiresAt) {

        public boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private static final Map<Integer, Offer> OFFERS = new HashMap<>();
    private static int nextId = 1;

    private AccountOffers() {
    }

    public static synchronized void clear() {
        OFFERS.clear();
        nextId = 1;
    }

    public static synchronized boolean hasPendingFor(UUID holder) {
        long now = System.currentTimeMillis();
        return OFFERS.values().stream()
                .anyMatch(offer -> offer.holder().equals(holder) && !offer.isExpired(now));
    }

    @Nullable
    public static synchronized Offer get(int id) {
        return OFFERS.get(id);
    }

    public static synchronized void remove(int id) {
        OFFERS.remove(id);
    }

    /**
     * Offers an account and tells the holder the terms.
     *
     * <p>An earlier offer from the same bank to the same person is replaced, so a banker clicking twice
     * does not leave the customer with two questions and a choice about which to answer.</p>
     */
    public static synchronized void offer(ServerPlayer banker, ServerPlayer holder, Bank bank) {
        OFFERS.values().removeIf(offer -> offer.holder().equals(holder.getUUID())
                && offer.bankId().equals(bank.id()));

        Offer offer = new Offer(nextId++, holder.getUUID(), bank.id(), banker.getUUID(),
                bank.name(), System.currentTimeMillis() + TIMEOUT_MILLIS);
        OFFERS.put(offer.id(), offer);
        ask(holder, bank, offer);
        // accept/decline stay out of tab-completion until there is something to answer.
        holder.server.getCommands().sendCommands(holder);
    }

    private static void ask(ServerPlayer holder, Bank bank, Offer offer) {
        String symbol = CurrencyConfig.get().currencySymbol;
        holder.sendSystemMessage(Component.translatable("message.athens_coins.offer_header",
                bank.name()).withStyle(ChatFormatting.GOLD));
        // The terms, because this is the only place the customer ever sees them.
        holder.sendSystemMessage(Component.translatable("message.athens_coins.offer_terms",
                        Money.format(bank.commissionFee(), symbol),
                        bank.commissionPeriodDays(),
                        Money.format(bank.walletLimit(), symbol))
                .withStyle(ChatFormatting.GRAY));
        holder.sendSystemMessage(Component.literal("  ")
                .append(button("message.athens_coins.offer_accept", ChatFormatting.GREEN,
                        "/fscurrency account accept " + offer.id()))
                .append(Component.literal("   "))
                .append(button("message.athens_coins.offer_decline", ChatFormatting.RED,
                        "/fscurrency account decline " + offer.id())));
    }

    private static MutableComponent button(String labelKey, ChatFormatting color, String command) {
        return Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(command))));
    }

    /** Drops offers nobody answered, so a stale id cannot be pressed much later. */
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<Offer> expired = new ArrayList<>();
        synchronized (AccountOffers.class) {
            OFFERS.values().removeIf(offer -> {
                if (offer.isExpired(now)) {
                    expired.add(offer);
                    return true;
                }
                return false;
            });
        }
        for (Offer offer : expired) {
            ServerPlayer holder = server.getPlayerList().getPlayer(offer.holder());
            if (holder != null) {
                holder.sendSystemMessage(Component
                        .translatable("message.athens_coins.offer_expired", offer.bankName())
                        .withStyle(ChatFormatting.GRAY));
                server.getCommands().sendCommands(holder);
            }
            // Tell the banker too: an offer that quietly lapsed looks like the button did nothing.
            ServerPlayer banker = server.getPlayerList().getPlayer(offer.banker());
            if (banker != null) {
                banker.sendSystemMessage(Component
                        .translatable("message.athens_coins.offer_lapsed")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
