package com.claimblocks.util;

import com.claimblocks.ClaimBlocksMod;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

public final class PlayerLookup {
    /** Hilo propio para las consultas que pueden salir a la red (API de Mojang). */
    private static final ExecutorService LOOKUP = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ClaimBlocks-ProfileLookup");
        t.setDaemon(true);
        return t;
    });

    private PlayerLookup() {
    }

    /** true si el servidor no usa autenticacion de Mojang (offline-mode / "cracked"). */
    public static boolean isOfflineMode(MinecraftServer server) {
        return server != null && !server.m_129797_();
    }

    /**
     * Resolucion inmediata que NUNCA sale a la red ni bloquea el hilo del servidor.
     *
     * Devuelve null cuando haria falta consultar la API de Mojang; en ese caso hay que usar
     * {@link #resolveAsync}.
     */
    public static Resolved resolve(MinecraftServer server, String name) {
        if (server == null || name == null || name.isBlank()) {
            return null;
        }
        String query = name.trim();
        ServerPlayer online = server.m_6846_().m_11255_(query);
        if (online != null) {
            return new Resolved(online.m_20148_(), online.m_7755_().getString(), online);
        }
        if (PlayerLookup.isOfflineMode(server)) {
            // En un servidor sin autenticacion el UUID del jugador es deterministico
            // (MD5 de "OfflinePlayer:<nombre>"), exactamente el mismo que genera el propio
            // servidor al entrar. Preguntar a Mojang aqui devolveria el UUID premium y el
            // miembro quedaria guardado con un UUID que no coincide con nadie.
            UUID id = UUIDUtil.m_235879_(query);
            return new Resolved(id, query, server.m_6846_().m_11259_(id));
        }
        return null;
    }

    /**
     * Resolucion completa. En offline-mode o si el jugador esta conectado responde en el mismo
     * tick; si hace falta mirar la cache de perfiles / la API de Mojang, la consulta se hace en
     * un hilo aparte y el callback se ejecuta de vuelta en el hilo principal del servidor.
     *
     * Antes esto se hacia con GameProfileCache.get(String) directamente en el hilo principal, lo
     * que congelaba el servidor varios segundos con cualquier nombre no cacheado.
     */
    public static void resolveAsync(MinecraftServer server, String name, Consumer<Resolved> callback) {
        if (server == null || name == null || name.isBlank()) {
            callback.accept(null);
            return;
        }
        String query = name.trim();
        Resolved quick = PlayerLookup.resolve(server, query);
        if (quick != null) {
            callback.accept(quick);
            return;
        }
        GameProfileCache cache = server.m_129927_();
        if (cache == null) {
            callback.accept(null);
            return;
        }
        LOOKUP.execute(() -> {
            Resolved out = null;
            try {
                Optional<GameProfile> profile = cache.m_10996_(query);
                if (profile.isPresent() && profile.get().getId() != null) {
                    GameProfile p = profile.get();
                    out = new Resolved(p.getId(), p.getName() == null ? query : p.getName(), null);
                }
            }
            catch (Throwable t) {
                ClaimBlocksMod.LOGGER.warn("[ClaimBlocks] No se pudo resolver el perfil de '" + query + "'", t);
            }
            Resolved result = out;
            server.execute(() -> {
                // se re-resuelve el jugador conectado ya en el hilo principal
                Resolved finalResult = result;
                if (finalResult != null) {
                    finalResult = new Resolved(finalResult.id(), finalResult.name(),
                            server.m_6846_().m_11259_(finalResult.id()));
                }
                callback.accept(finalResult);
            });
        });
    }

    /** Nombre a partir del UUID. Solo mira jugadores conectados y la cache local: no sale a la red. */
    public static String nameOf(MinecraftServer server, UUID id) {
        if (id == null) {
            return "?";
        }
        if (server != null) {
            Optional<GameProfile> profile;
            ServerPlayer online = server.m_6846_().m_11259_(id);
            if (online != null) {
                return online.m_7755_().getString();
            }
            GameProfileCache cache = server.m_129927_();
            if (cache != null && (profile = cache.m_11002_(id)).isPresent() && profile.get().getName() != null) {
                return profile.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    public record Resolved(UUID id, String name, ServerPlayer online) {
        public boolean isOnline() {
            return this.online != null;
        }
    }
}
