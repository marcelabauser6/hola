package com.claimblocks.util;

import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

/**
 * Proteccion de entidades de decoracion: cuadros, marcos de items y soportes de armadura.
 *
 * Estas entidades eran el gran agujero de las protecciones. Antes solo se cubria el golpe
 * directo de un jugador (AttackEntityEvent), asi que un cuadro se podia romper igualmente con
 * una flecha, una bola de nieve, un huevo, una explosion o incluso un esqueleto disparando, y el
 * item caia al suelo para que se lo llevara cualquiera.
 *
 * Cubre tambien los cuadros de otros mods: Joy of Painting (xercapaint) usa
 * xerca.xercapaint.common.entity.EntityCanvas, que hereda de HangingEntity, igual que los cuadros
 * y los marcos de vanilla. Cualquier mod que cuelgue cuadros "como los de vanilla" queda cubierto.
 */
public final class DecorationProtection {
    private DecorationProtection() {
    }

    /** Cuadros, marcos (y los cuadros de mods que heredan de HangingEntity) y soportes de armadura. */
    public static boolean isDecoration(Entity entity) {
        if (!ClaimConfig.get().protectDecoration) {
            return false;
        }
        return entity instanceof HangingEntity || entity instanceof ArmorStand;
    }

    /**
     * Zona que protege a esta decoracion.
     *
     * Un cuadro colgado en una pared queda medio bloque por delante de ella, asi que si la pared
     * es el borde justo de la proteccion, la posicion del cuadro puede caer fuera. Por eso se
     * mira tambien el bloque de la pared donde esta clavado.
     */
    public static Claim claimFor(Level level, Entity decoration) {
        if (level == null || decoration == null) {
            return null;
        }
        ClaimManager mgr = ClaimManager.getInstance();
        BlockPos at = decoration.m_20183_();
        Claim claim = mgr.getClaimAt(level, at);
        if (claim != null) {
            return claim;
        }
        Direction facing = decoration.m_6350_();
        if (facing != null) {
            return mgr.getClaimAt(level, at.m_121945_(facing.m_122424_()));
        }
        return null;
    }

    private static boolean isBypassing(Player player) {
        return player.m_20310_(2) && ClaimManager.getInstance().isBypassing(player.m_20148_());
    }

    /** true si este jugador no puede tocar las decoraciones de la zona. */
    public static boolean blocksPlayer(Claim claim, Player player) {
        if (claim == null || player == null) {
            return false;
        }
        if (claim.canModify(player) || DecorationProtection.isBypassing(player)) {
            return false;
        }
        ClaimFlags flags = claim.getFlags();
        // se protege con construir O interactuar: quitar un cuadro es las dos cosas, y asi no
        // queda expuesto por apagar solo uno de los dos botones
        return flags.blockBuilding || flags.blockEntityInteract || flags.publicMode;
    }

    /**
     * Decide si hay que anular un dano contra una decoracion, venga de donde venga.
     * Se usa desde el mixin de HangingEntity y desde los eventos.
     */
    public static boolean blocksDamage(Entity decoration, DamageSource source) {
        if (!DecorationProtection.isDecoration(decoration)) {
            return false;
        }
        Level level = decoration.m_9236_();
        if (level == null || level.m_5776_()) {
            return false;
        }
        Claim claim = DecorationProtection.claimFor(level, decoration);
        if (claim == null) {
            return false;
        }
        Player player = DecorationProtection.responsiblePlayer(source);
        if (player != null) {
            return DecorationProtection.blocksPlayer(claim, player);
        }
        ClaimFlags flags = claim.getFlags();
        if (source != null && source.m_269533_(DamageTypeTags.f_268415_)) {
            // TNT, creepers, cristales del end...
            return ClaimConfig.get().protectDecorationFromExplosions && (flags.blockExplosions || flags.publicMode);
        }
        // mobs, fuego, cactus, o cualquier otra cosa sin jugador detras
        return flags.blockBuilding || flags.publicMode;
    }

    /**
     * Jugador responsable del dano. Para una flecha, getEntity() ya devuelve al que disparo y
     * getDirectEntity() la flecha, asi que se cubre el robo a distancia.
     */
    public static Player responsiblePlayer(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity cause = source.m_7639_();
        if (cause instanceof Player) {
            return (Player)cause;
        }
        Entity direct = source.m_7640_();
        if (direct instanceof Player) {
            return (Player)direct;
        }
        return DecorationProtection.ownerOf(direct);
    }

    /** Jugador que lanzo un proyectil, si lo hay. */
    public static Player ownerOf(Entity projectile) {
        if (projectile instanceof Projectile) {
            Entity owner = ((Projectile)projectile).m_19749_();
            if (owner instanceof Player) {
                return (Player)owner;
            }
        }
        return null;
    }
}
