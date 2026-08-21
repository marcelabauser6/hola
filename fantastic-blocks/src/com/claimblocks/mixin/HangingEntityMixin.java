package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.util.DecorationProtection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blindaje de cuadros y marcos dentro de una proteccion.
 *
 * Se engancha a HangingEntity.hurt, que es el unico punto por el que pasan TODAS las formas de
 * romper un cuadro: golpe de jugador, flecha, bola de nieve, huevo, tridente, explosion, mob...
 * Los eventos de Forge solo cubrian el golpe directo de un jugador, asi que todo lo demas se
 * saltaba la proteccion y el cuadro caia al suelo como item.
 *
 * Al inyectar sobre HangingEntity quedan cubiertos de golpe los cuadros y marcos de vanilla y los
 * de los mods que heredan de esta clase, como el EntityCanvas de Joy of Painting.
 *
 * El nombre del metodo se indica en SRG con descriptor completo, que es como se llama en
 * produccion (Forge / Mohist), de modo que no depende del refmap.
 */
@Mixin(value={HangingEntity.class})
public abstract class HangingEntityMixin {
    @Inject(method={"m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$protectDecoration(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        try {
            Entity self = (Entity)(Object)this;
            if (DecorationProtection.blocksDamage(self, source)) {
                cir.setReturnValue(false);
            }
        }
        catch (Throwable t) {
            ClaimBlocksMod.LOGGER.error("[ClaimBlocks] Fallo protegiendo una decoracion", t);
        }
    }
}
