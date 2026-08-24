package com.athensmc.athenscoins.bank;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells borrowers about their own loans.
 *
 * <p>Nothing did. Opening an account messaged both the banker and the holder, but granting a loan
 * messaged only the banker, and there was no due-date reminder or overdue warning anywhere in the
 * mod. A player could carry a debt accruing penalty interest and the only place it appeared was a
 * tooltip inside the wallet screen - so the usual way to discover a loan was to notice the money
 * missing.</p>
 *
 * <p>Reminders are rate-limited per account. The banking ticker runs once a second, and an overdue
 * loan is overdue on every one of those ticks; without a cooldown the warning would be a scrolling
 * wall of text rather than a notice.</p>
 */
public final class LoanNotices {

    /** At most one reminder per account per real hour. */
    private static final long REMINDER_COOLDOWN_MILLIS = 60L * 60L * 1000L;

    private static final DateTimeFormatter DUE =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneOffset.UTC);

    private static final Map<UUID, Long> LAST_REMINDER = new ConcurrentHashMap<>();

    private LoanNotices() {
    }

    /** Clears the cooldowns; called on server start so a restart does not suppress notices. */
    public static void reset() {
        LAST_REMINDER.clear();
    }

    /**
     * Tells the bank's staff that an application is waiting.
     *
     * <p>A queue nobody knows about is a queue nobody serves. Every online banker and operator who
     * can act on it gets one line; the customer already got their own confirmation.</p>
     */
    public static void applicationFiled(MinecraftServer server, Bank bank, LoanRequest request) {
        String symbol = CurrencyConfig.get().currencySymbol;
        Component line = Component.translatable("message.athens_coins.loan_request_filed",
                        request.borrowerName(), Money.format(request.amount(), symbol), bank.name())
                .withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
            if (bank.isBanker(staff.getUUID()) || staff.hasPermissions(2)) {
                staff.sendSystemMessage(line);
            }
        }
    }

    /** Tells the applicant their request was refused. */
    public static void applicationRejected(MinecraftServer server, Bank bank, LoanRequest request) {
        ServerPlayer borrower = server.getPlayerList().getPlayer(request.borrower());
        if (borrower != null) {
            borrower.sendSystemMessage(Component.translatable(
                            "message.athens_coins.loan_request_rejected", bank.name())
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** Tells the holder they now owe money, how much, and when it is due. */
    public static void granted(MinecraftServer server, BankAccount account, long amount, long dueAt) {
        ServerPlayer holder = server.getPlayerList().getPlayer(account.owner());
        if (holder == null) {
            return;
        }
        String symbol = CurrencyConfig.get().currencySymbol;
        holder.sendSystemMessage(Component.translatable("message.athens_coins.loan_notice_holder",
                        Money.format(amount, symbol), DUE.format(Instant.ofEpochMilli(dueAt)))
                .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Reminds the holder of an overdue loan, at most once an hour.
     *
     * <p>Only fires for a loan that is actually overdue: a loan still inside its term is the
     * borrower's business, and nagging about it would train players to ignore the channel the real
     * warning arrives on.</p>
     */
    public static void remindIfOverdue(MinecraftServer server, BankAccount account, long now) {
        Loan loan = account.loan();
        if (loan == null || loan.settled() || !loan.overdue(now)) {
            LAST_REMINDER.remove(account.owner());
            return;
        }
        Long last = LAST_REMINDER.get(account.owner());
        if (last != null && now - last < REMINDER_COOLDOWN_MILLIS) {
            return;
        }
        ServerPlayer holder = server.getPlayerList().getPlayer(account.owner());
        if (holder == null) {
            return;
        }
        LAST_REMINDER.put(account.owner(), now);
        String symbol = CurrencyConfig.get().currencySymbol;
        holder.sendSystemMessage(Component.translatable("message.athens_coins.loan_notice_overdue",
                        Money.format(loan.owed(), symbol))
                .withStyle(ChatFormatting.RED));
    }
}
