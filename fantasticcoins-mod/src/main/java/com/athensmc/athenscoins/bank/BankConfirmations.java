package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * Makes the terminal's irreversible actions ask first.
 *
 * <p>Opening an account, handing somebody banker access and closing an account were all a single
 * click on a row, executed immediately, with nothing saying the row was even clickable. Those are not
 * actions a stray click should complete: banker access hands over the bank, and closing an account
 * liquidates somebody's money into a card.</p>
 *
 * <p>So the click now only <em>proposes</em>. The proposal goes to the actor's chat with the target
 * spelled out and two buttons, and nothing happens until one is pressed - the same shape as the
 * transfer accept/deny card, which players already know.</p>
 *
 * <p>In memory rather than persisted, like transfer requests: a proposal is a question asked of
 * somebody who is standing at the terminal right now, and one that outlived a restart would be a
 * click nobody remembers making.</p>
 */
public final class BankConfirmations {

    /** Long enough to read the line, short enough that a forgotten proposal cannot be pressed later. */
    private static final long TIMEOUT_MILLIS = 60_000L;

    public enum Kind {
        OPEN_ACCOUNT,
        GRANT_BANKER,
        REVOKE_BANKER,
        CLOSE_ACCOUNT,
        APPROVE_LOAN,
        REJECT_LOAN
    }

    /**
     * A proposal waiting on its actor.
     *
     * @param subject who or what the action is about, already resolved to a name for the message
     */
    public record Pending(int id, UUID actor, Kind kind, BlockPos pos, UUID target,
                          int account, long amount, String subject, long expiresAt) {

        public boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int nextId = 1;

    private BankConfirmations() {
    }

    public static synchronized void clear() {
        PENDING.clear();
        nextId = 1;
    }

    public static synchronized boolean hasPendingFor(UUID actor) {
        long now = System.currentTimeMillis();
        return PENDING.values().stream()
                .anyMatch(pending -> pending.actor().equals(actor) && !pending.isExpired(now));
    }

    @Nullable
    public static synchronized Pending get(int id) {
        return PENDING.get(id);
    }

    public static synchronized void remove(int id) {
        PENDING.remove(id);
    }

    /**
     * Registers a proposal and sends the actor the question, replacing any earlier proposal of the
     * same kind about the same subject so a double click does not queue two.
     */
    public static synchronized Pending propose(ServerPlayer actor, Kind kind, BlockPos pos,
                                               @Nullable UUID target, int account, long amount,
                                               String subject) {
        PENDING.values().removeIf(pending -> pending.actor().equals(actor.getUUID())
                && pending.kind() == kind && pending.account() == account
                && java.util.Objects.equals(pending.target(), target));

        Pending pending = new Pending(nextId++, actor.getUUID(), kind, pos, target, account, amount,
                subject == null ? "" : subject, System.currentTimeMillis() + TIMEOUT_MILLIS);
        PENDING.put(pending.id(), pending);
        ask(actor, pending);
        // accept/deny are hidden from tab-completion until there is something to answer.
        actor.server.getCommands().sendCommands(actor);
        return pending;
    }

    private static void ask(ServerPlayer actor, Pending pending) {
        String symbol = CurrencyConfig.get().currencySymbol;
        Component question = switch (pending.kind()) {
            case OPEN_ACCOUNT -> Component.translatable("message.athens_coins.ask_open_account",
                    pending.subject());
            case GRANT_BANKER -> Component.translatable("message.athens_coins.ask_grant_banker",
                    pending.subject());
            case REVOKE_BANKER -> Component.translatable("message.athens_coins.ask_revoke_banker",
                    pending.subject());
            case CLOSE_ACCOUNT -> Component.translatable("message.athens_coins.ask_close_account",
                    pending.subject());
            case APPROVE_LOAN -> Component.translatable("message.athens_coins.ask_approve_loan",
                    pending.subject(), Money.format(pending.amount(), symbol));
            case REJECT_LOAN -> Component.translatable("message.athens_coins.ask_reject_loan",
                    pending.subject());
        };
        actor.sendSystemMessage(question.copy().withStyle(ChatFormatting.YELLOW));
        actor.sendSystemMessage(Component.literal("  ")
                .append(button("message.athens_coins.button_confirm", ChatFormatting.GREEN,
                        "/fscurrency bank confirm " + pending.id()))
                .append(Component.literal("   "))
                .append(button("message.athens_coins.button_cancel", ChatFormatting.RED,
                        "/fscurrency bank cancel " + pending.id())));
    }

    private static MutableComponent button(String labelKey, ChatFormatting color, String command) {
        return Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(command))));
    }

    /** Drops proposals nobody answered, so a stale id cannot be pressed much later. */
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<Pending> expired = new ArrayList<>();
        synchronized (BankConfirmations.class) {
            PENDING.values().removeIf(pending -> {
                if (pending.isExpired(now)) {
                    expired.add(pending);
                    return true;
                }
                return false;
            });
        }
        for (Pending pending : expired) {
            ServerPlayer actor = server.getPlayerList().getPlayer(pending.actor());
            if (actor != null) {
                actor.sendSystemMessage(Component
                        .translatable("message.athens_coins.ask_expired")
                        .withStyle(ChatFormatting.GRAY));
                server.getCommands().sendCommands(actor);
            }
        }
    }
}
