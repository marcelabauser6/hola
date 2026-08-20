package com.athensmc.athenscoins.transfer;

import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks outstanding transfer requests.
 *
 * <p>Deliberately in-memory only: a pending request is a short-lived conversation between two
 * online players, so there is nothing worth persisting across a restart.</p>
 */
public final class TransferManager {

    private static final Map<Integer, PendingTransfer> PENDING = new HashMap<>();
    private static int nextId = 1;

    private TransferManager() {
    }

    /**
     * Registers a new request, replacing any earlier one from the same sender to the same target
     * so a player cannot spam someone's chat with stacked requests.
     */
    public static synchronized PendingTransfer create(ServerPlayer sender, ServerPlayer target, long cents) {
        PENDING.values().removeIf(pending ->
                pending.sender().equals(sender.getUUID()) && pending.target().equals(target.getUUID()));

        long timeout = CurrencyConfig.get().transferRequestTimeoutSeconds * 1000L;
        PendingTransfer request = new PendingTransfer(
                nextId++,
                sender.getUUID(), sender.getGameProfile().getName(),
                target.getUUID(), target.getGameProfile().getName(),
                cents,
                System.currentTimeMillis() + timeout);
        PENDING.put(request.id(), request);
        return request;
    }

    @Nullable
    public static synchronized PendingTransfer get(int id) {
        return PENDING.get(id);
    }

    public static synchronized void remove(int id) {
        PENDING.remove(id);
    }

    /** Number of requests currently awaiting an answer. Used by the reload report. */
    public static synchronized int pendingCount() {
        return PENDING.size();
    }

    public static synchronized void clear() {
        PENDING.clear();
        nextId = 1;
    }

    /**
     * Drops requests that ran out of time, telling both sides. Called once a second from the
     * server tick.
     */
    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<PendingTransfer> expired = new ArrayList<>();
        synchronized (TransferManager.class) {
            PENDING.values().removeIf(pending -> {
                if (pending.isExpired(now)) {
                    expired.add(pending);
                    return true;
                }
                return false;
            });
        }
        if (expired.isEmpty()) {
            return;
        }
        String symbol = CurrencyConfig.get().currencySymbol;
        for (PendingTransfer pending : expired) {
            String amount = Money.format(pending.cents(), symbol);
            notify(server, pending.sender(), Component.translatable(
                            "message.athens_coins.transfer_expired_sender", amount, pending.targetName())
                    .withStyle(ChatFormatting.GRAY));
            notify(server, pending.target(), Component.translatable(
                            "message.athens_coins.transfer_expired_target", amount, pending.senderName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void notify(MinecraftServer server, UUID playerId, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }
}
