/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffects
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.GameType
 *  net.minecraft.world.level.Level
 */
package com.claimblocks.event;

import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

public final class PassiveEffectsManager {
    private static int counter = 0;
    private static final Set<UUID> grantedFlight = ConcurrentHashMap.newKeySet();

    private PassiveEffectsManager() {
    }

    public static void tick(MinecraftServer server) {
        if (++counter % 20 == 0) {
            boolean runEffects = counter % Math.max(20, ClaimConfig.get().passiveEffectIntervalTicks) < 20;
            for (ServerLevel world : server.m_129785_()) {
                for (ServerPlayer player : world.m_6907_()) {
                    Claim claim = ClaimManager.getInstance().getClaimAt((Level)world, player.m_20183_());
                    PassiveEffectsManager.handleFlight(player, claim);
                    if (!runEffects) continue;
                    PassiveEffectsManager.applyEffects(player, claim);
                }
            }
        }
    }

    private static int paidLevel(ClaimTier t) {
        String var1;
        if (t == null) {
            return 0;
        }
        return switch (var1 = t.id) {
            case "claimstone_250x250" -> 1;
            case "claimstone_300x300" -> 2;
            case "claimstone_500x500" -> 3;
            default -> 0;
        };
    }

    /**
     * Vuelo de la zona.
     *
     * En un servidor hibrido es habitual que un plugin (Essentials /fly, rangos, etc.) ya haya
     * dado vuelo al jugador. Antes, al salir de la zona el mod ponia mayFly = false a cualquiera
     * que hubiese marcado, quitando el vuelo que habia concedido el plugin. Ahora solo se
     * devuelve el permiso al estado que tenia justo antes de entrar.
     */
    private static void handleFlight(ServerPlayer player, Claim claim) {
        UUID id = player.m_20148_();
        GameType mode = player.f_8941_.m_9290_();
        if (mode != GameType.CREATIVE && mode != GameType.SPECTATOR) {
            int level;
            boolean shouldHaveClaimFlight = false;
            if (claim != null && (level = PassiveEffectsManager.paidLevel(claim.getTier())) >= 3 && claim.isOwner((Player)player) && claim.getFlags().allowFlight) {
                shouldHaveClaimFlight = true;
            }
            boolean weGranted = grantedFlight.contains(id);
            boolean alreadyCanFly = player.m_150110_().f_35936_;
            if (shouldHaveClaimFlight) {
                if (!weGranted && !alreadyCanFly) {
                    player.m_150110_().f_35936_ = true;
                    player.m_6885_();
                    grantedFlight.add(id);
                    player.m_5661_((Component)Component.m_237113_((String)"\u2714 Vuelo activado (Owner 500x500).").m_130940_(ChatFormatting.GREEN), true);
                }
            } else if (weGranted) {
                grantedFlight.remove(id);
                if (PassiveEffectsManager.hasExternalFlight(player)) {
                    // otro mod/plugin le dio vuelo mientras estaba dentro: no se lo quitamos
                    player.m_5661_((Component)Component.m_237113_((String)"[i] Saliste de la zona de vuelo.").m_130940_(ChatFormatting.AQUA), true);
                } else {
                    player.m_150110_().f_35936_ = false;
                    player.m_150110_().f_35935_ = false;
                    player.m_6885_();
                    player.m_5661_((Component)Component.m_237113_((String)"[i] Saliste de la zona de vuelo.").m_130940_(ChatFormatting.AQUA), true);
                }
            }
        } else {
            grantedFlight.remove(id);
        }
    }

    /**
     * Detecta si el permiso de vuelo viene de fuera del mod. En Mohist los plugins de vuelo
     * suelen dejar al jugador volando (isFlying) o marcado como invulnerable/creativo por otra
     * via; en esos casos no hay que tocarle las abilities.
     */
    private static boolean hasExternalFlight(ServerPlayer player) {
        GameType mode = player.f_8941_.m_9290_();
        if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
            return true;
        }
        return player.m_150110_().f_35935_;
    }

    private static void applyEffects(ServerPlayer player, Claim claim) {
        int level;
        if (claim != null && (level = PassiveEffectsManager.paidLevel(claim.getTier())) != 0 && claim.canModify((Player)player)) {
            if (level >= 1 && claim.getFlags().effectRegeneration) {
                player.m_7292_(new MobEffectInstance(MobEffects.f_19605_, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }
            if (level >= 2 && claim.getFlags().effectResistance) {
                player.m_7292_(new MobEffectInstance(MobEffects.f_19606_, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }
            if (level >= 2 && claim.getFlags().effectSpeed) {
                player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }
        }
    }

    public static void onPlayerDisconnect(UUID id) {
        grantedFlight.remove(id);
    }
}

