/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 */
package com.claimblocks.event;

import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.event.PassiveEffectsManager;
import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class PlayerTracker {
    private static final Map<UUID, UUID> lastClaim = new ConcurrentHashMap<UUID, UUID>();
    private static final Map<UUID, Long> lastAlert = new ConcurrentHashMap<UUID, Long>();
    private static final long ALERT_COOLDOWN_TICKS = 600L;
    private static int bypassReminderCounter = 0;
    private static final Map<UUID, Long> lastBanHit = new ConcurrentHashMap<UUID, Long>();

    public static void onDisconnect(UUID id) {
        lastClaim.remove(id);
        lastAlert.remove(id);
        ClaimManager.getInstance().onPlayerDisconnect(id);
        PassiveEffectsManager.onPlayerDisconnect(id);
    }

    public static void tick(MinecraftServer server) {
        boolean showBypassReminder = ++bypassReminderCounter % 60 == 0;
        for (ServerLevel world : server.m_129785_()) {
            for (ServerPlayer player : world.m_6907_()) {
                PlayerTracker.handle(world, player);
                if (!showBypassReminder || !player.m_20310_(2) || !ClaimManager.getInstance().isBypassing(player.m_20148_())) continue;
                player.m_5661_((Component)Component.m_237113_((String)"[!] BYPASS ACTIVO").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}), true);
            }
        }
    }

    private static void handle(ServerLevel world, ServerPlayer player) {
        UUID nowId;
        Claim now = ClaimManager.getInstance().getClaimAt((Level)world, player.m_20183_());
        UUID prev = lastClaim.get(player.m_20148_());
        UUID uUID = nowId = now == null ? null : PlayerTracker.zoneId(now);
        if (Objects.equals(prev, nowId)) {
            if (now != null && now.isBanned(player.m_20148_()) && !player.m_20310_(2)) {
                PlayerTracker.repelBanned(world, player, now);
            }
        } else {
            Claim left = PlayerTracker.resolveZone(prev);
            if (left != null) {
                MutableComponent msg = left.getFlags().showLeave && left.getFlags().leaveMessage != null && !left.getFlags().leaveMessage.isBlank() ? Component.m_237113_((String)"[Protecci\u00f3n] ").m_130940_(ChatFormatting.GRAY).m_7220_((Component)Component.m_237113_((String)PlayerTracker.truncate(left.getFlags().leaveMessage, 50)).m_130940_(ChatFormatting.GOLD)) : Component.m_237113_((String)"[Protecci\u00f3n] ").m_130940_(ChatFormatting.GRAY).m_7220_((Component)Component.m_237113_((String)"Saliendo de la zona ").m_130940_(ChatFormatting.RED)).m_7220_((Component)Component.m_237113_((String)PlayerTracker.truncate(PlayerTracker.zoneLabel(left), 24)).m_130944_(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
                player.m_5661_((Component)msg, true);
                player.m_6330_(SoundEvents.f_144243_, SoundSource.PLAYERS, 2.0f, 0.9f);
            }
            if (now != null) {
                if (now.isBanned(player.m_20148_()) && !player.m_20310_(2)) {
                    PlayerTracker.repelBanned(world, player, now);
                    lastClaim.remove(player.m_20148_());
                    return;
                }
                MutableComponent entryMsg = now.getFlags().showWelcome && now.getFlags().welcomeMessage != null && !now.getFlags().welcomeMessage.isBlank() ? Component.m_237113_((String)"[Protecci\u00f3n] ").m_130940_(ChatFormatting.GRAY).m_7220_((Component)Component.m_237113_((String)PlayerTracker.truncate(now.getFlags().welcomeMessage, 50)).m_130940_(ChatFormatting.GREEN)) : Component.m_237113_((String)"[Protecci\u00f3n] ").m_130940_(ChatFormatting.GRAY).m_7220_((Component)Component.m_237113_((String)"Entrando a la zona ").m_130940_(ChatFormatting.GREEN)).m_7220_((Component)Component.m_237113_((String)PlayerTracker.truncate(PlayerTracker.zoneLabel(now), 24)).m_130944_(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
                player.m_5661_((Component)entryMsg, true);
                player.m_6330_(SoundEvents.f_144243_, SoundSource.PLAYERS, 2.0f, 1.4f);
                if (now.getFlags().trespasserAlerts && !now.canModify((Player)player)) {
                    long t = world.m_46467_();
                    Long last = lastAlert.get(player.m_20148_());
                    if (last == null || t - last > (long)ClaimConfig.get().trespasserAlertTicks()) {
                        ServerPlayer owner;
                        lastAlert.put(player.m_20148_(), t);
                        UUID ownerId = PlayerTracker.zoneOwner(now);
                        ServerPlayer serverPlayer = owner = ownerId == null ? null : world.m_7654_().m_6846_().m_11259_(ownerId);
                        if (owner != null) {
                            MutableComponent alert = Component.m_237113_((String)"[!] ").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).m_7220_((Component)Component.m_237113_((String)PlayerTracker.truncate(player.m_7755_().getString(), 16)).m_130944_(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})).m_7220_((Component)Component.m_237113_((String)(" entr\u00f3 a tu zona en X=" + now.getX() + " Z=" + now.getZ())).m_130940_(ChatFormatting.YELLOW));
                            owner.m_5661_((Component)alert, false);
                        }
                    }
                }
            }
            if (nowId == null) {
                lastClaim.remove(player.m_20148_());
            } else {
                lastClaim.put(player.m_20148_(), nowId);
            }
        }
    }

    private static UUID zoneId(Claim c) {
        return c.getGroupId() != null ? c.getGroupId() : c.getClaimId();
    }

    private static Claim resolveZone(UUID zoneId) {
        if (zoneId == null) {
            return null;
        }
        ClaimGroup g = ClaimManager.getInstance().getGroup(zoneId);
        return g != null ? ClaimManager.getInstance().getMotherClaim(zoneId) : PlayerTracker.findClaimById(zoneId);
    }

    private static String zoneLabel(Claim c) {
        ClaimGroup g;
        if (c == null) {
            return "";
        }
        if (c.getGroupId() != null && (g = ClaimManager.getInstance().getGroup(c.getGroupId())) != null) {
            return g.getName();
        }
        return c.getOwnerName() + " (" + c.sizeLabel() + ")";
    }

    private static UUID zoneOwner(Claim c) {
        ClaimGroup g;
        if (c.getGroupId() != null && (g = ClaimManager.getInstance().getGroup(c.getGroupId())) != null && g.getMotherOwnerId() != null) {
            return g.getMotherOwnerId();
        }
        return c.getOwnerUUID();
    }

    private static Claim findClaimById(UUID id) {
        for (Claim c : ClaimManager.getInstance().getAllClaims()) {
            if (!c.getClaimId().equals(id)) continue;
            return c;
        }
        return null;
    }

    /**
     * Saca a un jugador baneado de la zona.
     *
     * Antes se le aplicaba impulso mas 5 de dano cada 15 ticks: si se desconectaba dentro de la
     * zona y volvia a entrar ahi, aparecia y moria en bucle sin poder moverse. Ahora se le
     * teletransporta al borde mas cercano por fuera, sin dano.
     */
    private static void repelBanned(ServerLevel world, ServerPlayer player, Claim claim) {
        double cx = (double)claim.getX() + 0.5;
        double cz = (double)claim.getZ() + 0.5;
        double r = claim.getRadius() + 1.5;
        double px = player.m_20185_();
        double pz = player.m_20189_();
        // distancia a cada borde: se sale por el mas cercano
        double toWest = px - (cx - r);
        double toEast = cx + r - px;
        double toNorth = pz - (cz - r);
        double toSouth = cz + r - pz;
        double min = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
        double tx = px;
        double tz = pz;
        if (min == toWest) {
            tx = cx - r;
        } else if (min == toEast) {
            tx = cx + r;
        } else if (min == toNorth) {
            tz = cz - r;
        } else {
            tz = cz + r;
        }
        ClaimConfig cfg = ClaimConfig.get();
        if (cfg.banTeleportOut) {
            double ty = PlayerTracker.safeY(world, tx, player.m_20186_(), tz);
            player.m_8999_(world, tx, ty, tz, player.m_146908_(), player.m_146909_());
            player.m_20334_(0.0, 0.0, 0.0);
        } else {
            double px2 = player.m_20185_() - cx;
            double pz2 = player.m_20189_() - cz;
            double mag = Math.max(1.0E-4, Math.sqrt(px2 * px2 + pz2 * pz2));
            player.m_20334_(px2 / mag * 1.2, 0.42, pz2 / mag * 1.2);
        }
        player.f_19864_ = true;
        long now = world.m_46467_();
        Long last = lastBanHit.get(player.m_20148_());
        if (last == null || now - last >= ClaimConfig.get().banNoticeTicks()) {
            lastBanHit.put(player.m_20148_(), now);
            if (cfg.banDamage > 0.0f) {
                player.f_19802_ = 0;
                player.m_6469_(player.m_269291_().m_269425_(), cfg.banDamage);
            }
            player.m_5661_((Component)Component.m_237113_((String)"[!] Est\u00e1s baneado de esta zona.").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}), false);
            player.m_6330_(SoundEvents.f_144243_, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
    }

    /** Busca un hueco de 2 bloques de alto cerca de la Y indicada para no incrustar al jugador. */
    private static double safeY(ServerLevel world, double x, double y, double z) {
        int bx = (int)Math.floor(x);
        int bz = (int)Math.floor(z);
        int by = (int)Math.floor(y);
        for (int dy = 0; dy <= 6; ++dy) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int cy = by + dy * sign;
                if (cy < world.m_141937_() || cy > world.m_151558_() - 2) continue;
                if (!world.m_8055_(new BlockPos(bx, cy, bz)).m_60795_()) continue;
                if (!world.m_8055_(new BlockPos(bx, cy + 1, bz)).m_60795_()) continue;
                return cy;
            }
        }
        return y;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }
}

