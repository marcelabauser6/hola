package com.fantasticchameleon.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Escritura directa del estado de marcha de un modelo de adorno.
 *
 * <p>La API pública no permite reproducir la animación de vanilla en un modelo que no se desplaza:
 * {@code update(target, blend)} avanza la fase <b>y</b> pisa la velocidad anterior en la misma llamada,
 * y {@code setSpeed} sólo escribe la velocidad actual. Con un solo camino público la velocidad anterior
 * quedaba clavada en cero, así que la amplitud que interpola el renderer saltaba de 0 al valor pleno en
 * cada tick: eso es lo que se veía como patas temblando a toda prisa aunque el ritmo fuese correcto.
 *
 * <p>Con acceso a los tres campos se fija exactamente el mismo estado que tendría la criatura real:
 * velocidad anterior, velocidad actual y fase acumulada.
 *
 * <p>Los nombres son SRG a propósito: esta clase no está en el refmap del JAR base, así que el nombre
 * se usa literal y en producción los campos se llaman así.
 */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationAccessor {
   /** Velocidad del tick anterior, extremo inicial de la interpolación de amplitud. */
   @Accessor("f_267406_")
   void fantastic$setSpeedOld(float value);

   /** Velocidad del tick actual. */
   @Accessor("f_267371_")
   void fantastic$setSpeed(float value);

   /** Fase acumulada de la marcha. */
   @Accessor("f_267358_")
   void fantastic$setPosition(float value);
}
