package com.claimblocks.util;

import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Protecciones de borde: cosas que cruzan la frontera de una zona sin que nadie la pise.
 *
 * Eran dos agujeros abiertos:
 *
 *  - TOLVAS. El mod trataba la tolva como "contenedor que no puedes abrir", pero no controlaba la
 *    transferencia. Como la altura de una zona es +/-N desde la piedra, bastaba con poner una tolva
 *    justo debajo del limite inferior, bajo un cofre, para vaciar la base sin entrar. Lo mismo con
 *    una vagoneta-tolva pasando por debajo.
 *
 *  - FLUIDOS. Solo se controlaba el cubo (BucketItem), no el flujo. Se vaciaba lava pegada al borde
 *    por fuera y entraba sola.
 */
public final class BorderGuard {
    private BorderGuard() {
    }

    /** Dos zonas cuentan como la misma si son la misma piedra o pertenecen al mismo grupo. */
    private static boolean sameZone(Claim a, Claim b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getClaimId().equals(b.getClaimId())) {
            return true;
        }
        return a.getGroupId() != null && a.getGroupId().equals(b.getGroupId());
    }

    /**
     * true si hay que impedir que se saquen items de un contenedor situado en 'fromPos' hacia
     * 'toPos'. Solo se corta cuando el origen esta en una zona protegida y el destino NO es la
     * misma zona, asi que las tolvas internas del dueno siguen funcionando igual.
     */
    public static boolean blocksItemExtraction(Level level, BlockPos fromPos, BlockPos toPos) {
        if (level == null || level.m_5776_() || fromPos == null || toPos == null) {
            return false;
        }
        if (!ClaimConfig.get().protectHoppers) {
            return false;
        }
        ClaimManager mgr = ClaimManager.getInstance();
        Claim from = mgr.getClaimAt(level, fromPos);
        if (from == null) {
            return false;
        }
        Claim to = mgr.getClaimAt(level, toPos);
        if (BorderGuard.sameZone(from, to)) {
            return false;
        }
        ClaimFlags flags = from.getFlags();
        return flags.blockChestAccess || flags.publicMode;
    }

    /**
     * true si hay que impedir que un fluido entre en la zona de 'toPos' viniendo de 'fromPos'.
     * El flujo dentro de la propia zona no se toca.
     */
    public static boolean blocksFluidEntry(Level level, BlockPos fromPos, BlockPos toPos) {
        if (level == null || level.m_5776_() || fromPos == null || toPos == null) {
            return false;
        }
        if (!ClaimConfig.get().protectFluids) {
            return false;
        }
        ClaimManager mgr = ClaimManager.getInstance();
        Claim to = mgr.getClaimAt(level, toPos);
        if (to == null) {
            return false;
        }
        Claim from = mgr.getClaimAt(level, fromPos);
        if (BorderGuard.sameZone(to, from)) {
            return false;
        }
        ClaimFlags flags = to.getFlags();
        return flags.blockFluids || flags.publicMode;
    }
}
