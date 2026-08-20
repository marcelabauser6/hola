/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 */
package com.claimblocks.chat;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.gui.AdminClaimSubMenuHandler;
import com.claimblocks.gui.ClaimMenuHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChatPromptRouter {
    private static final long SUPPRESS_WINDOW_MS = 2000L;
    private static final Map<UUID, Suppression> suppressions = new ConcurrentHashMap<UUID, Suppression>();
    private static volatile boolean packetCaptureActive;

    private ChatPromptRouter() {
    }

    public static boolean isPacketCaptureActive() {
        return packetCaptureActive;
    }

    public static boolean hasPending(UUID playerId) {
        return playerId != null && (ClaimMenuHandler.hasPrompt(playerId) || AdminClaimSubMenuHandler.hasPendingTransfer(playerId));
    }

    public static boolean consume(ServerPlayer sender, String rawMessage) {
        if (sender == null || rawMessage == null) {
            return false;
        }
        MinecraftServer server = sender.m_20194_();
        if (server == null) {
            return false;
        }
        UUID id = sender.m_20148_();
        UUID transferClaimId = AdminClaimSubMenuHandler.popPendingTransfer(id);
        if (transferClaimId != null) {
            String text = ChatPromptRouter.stripControlChars(rawMessage);
            ChatPromptRouter.markSuppressed(id, rawMessage);
            server.execute(() -> ClaimMenuHandler.dispatchAdminTransfer(sender, transferClaimId, text));
            return true;
        }
        ClaimMenuHandler.PendingChat prompt = ClaimMenuHandler.popPrompt(id);
        if (prompt != null) {
            String text = ChatPromptRouter.stripControlChars(rawMessage);
            ChatPromptRouter.markSuppressed(id, rawMessage);
            server.execute(() -> ClaimMenuHandler.dispatchPrompt(sender, prompt, text));
            return true;
        }
        return false;
    }

    private static void markSuppressed(UUID playerId, String rawText) {
        suppressions.put(playerId, new Suppression(rawText, System.currentTimeMillis() + 2000L));
    }

    public static boolean shouldSuppress(UUID playerId, String rawText) {
        if (playerId == null || rawText == null) {
            return false;
        }
        Suppression s = suppressions.get(playerId);
        if (s == null) {
            return false;
        }
        if (System.currentTimeMillis() > s.expiresAt()) {
            suppressions.remove(playerId, s);
            return false;
        }
        if (!s.rawText().equals(rawText)) {
            return false;
        }
        suppressions.remove(playerId, s);
        return true;
    }

    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId != null) {
            suppressions.remove(playerId);
        }
    }

    public static void markPacketCaptureActive() {
        if (!packetCaptureActive) {
            packetCaptureActive = true;
            ClaimBlocksMod.LOGGER.info("[ClaimBlocks] Captura de respuestas a nivel de paquete ACTIVA (compatible con Mohist y plugins de chat).");
        }
    }

    public static String stripControlChars(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ++i) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
                continue;
            }
            if (c < ' ') continue;
            out.append(c);
        }
        return out.toString().trim();
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); ++i) {
            char c = raw.charAt(i);
            if ((c == '\u00a7' || c == '&') && i + 1 < raw.length() && ChatPromptRouter.isColorCode(raw.charAt(i + 1))) {
                ++i;
                continue;
            }
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
                continue;
            }
            if (c < ' ') continue;
            out.append(c);
        }
        return out.toString().trim().replaceAll("\\s{2,}", " ");
    }

    private static boolean isColorCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
    }

    public static boolean isCancel(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.trim();
        return t.equalsIgnoreCase("cancelar") || t.equalsIgnoreCase("cancel") || t.startsWith("/");
    }

    public static String extractPlayerName(String text) {
        String clean = ChatPromptRouter.sanitize(text);
        if (clean.isEmpty() || ChatPromptRouter.isValidName(clean)) {
            return clean;
        }
        String[] tokens = clean.split(" ");
        for (int i = tokens.length - 1; i >= 0; --i) {
            if (!ChatPromptRouter.isValidName(tokens[i])) continue;
            return tokens[i];
        }
        return clean;
    }

    private static boolean isValidName(String s) {
        if (s == null || s.length() < 3 || s.length() > 16) {
            return false;
        }
        for (int i = 0; i < s.length(); ++i) {
            boolean ok;
            char c = s.charAt(i);
            boolean bl = ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
            if (ok) continue;
            return false;
        }
        return true;
    }

    private record Suppression(String rawText, long expiresAt) {
    }
}

