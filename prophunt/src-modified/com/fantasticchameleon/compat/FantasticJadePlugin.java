package com.fantasticchameleon.compat;

import com.fantasticchameleon.client.AvatarState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Integracion con Jade para no delatar a los jugadores disfrazados.
 *
 * <p>Jade muestra un recuadro con el nombre y los corazones de lo que apuntas. Sobre un prop eso
 * cantaba "Dummy2" con vida de jugador, que arruina la partida entera: el buscador no necesita ni
 * mirar bien, le sale el cartel.
 *
 * <p>Se resuelve con el callback de raytrace de Jade: cuando lo que se apunta es un jugador
 * disfrazado, se devuelve null y Jade no dibuja nada, como si ahi no hubiera ninguna entidad.
 *
 * <p>Esta clase solo la carga Jade a traves de su propio escaneo de anotaciones, asi que si Jade no
 * esta instalado nunca se toca y no pasa nada.
 */
@WailaPlugin("fantastic_chameleon")
public class FantasticJadePlugin implements IWailaPlugin {

   public FantasticJadePlugin() {
   }

   @Override
   public void registerClient(IWailaClientRegistration registration) {
      registration.addRayTraceCallback((hitResult, accessor, originalAccessor) -> hide(accessor) ? null : accessor);
   }

   /** True si el objetivo es un jugador con disfraz puesto. */
   private static boolean hide(Accessor<?> accessor) {
      if (!(accessor instanceof EntityAccessor entityAccessor)) {
         return false;
      }

      Entity entity = entityAccessor.getEntity();
      return entity instanceof Player player && AvatarState.fullyDisguised(player);
   }
}
