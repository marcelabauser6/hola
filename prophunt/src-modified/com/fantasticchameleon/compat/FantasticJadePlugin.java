package com.fantasticchameleon.compat;

import com.fantasticchameleon.client.AvatarState;
import com.fantasticchameleon.client.RoundBar;
import com.fantasticchameleon.prophunt.PropHuntClient;
import com.fantasticchameleon.pose.PropShapes;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Integración Jade que presenta un prop de bloque como el BlockState exacto que está imitando.
 *
 * <p>Ocultar todo el overlay también delataba el prop: el bloque verdadero sí tenía ficha y el falso
 * no. El callback cambia el accessor de entidad/jugador por el accessor de bloque oficial de Jade, de
 * modo que Jade ejecuta sus proveedores normales de nombre, icono, mod y propiedades para ese bloque.
 */
@WailaPlugin("fantastic_chameleon")
public class FantasticJadePlugin implements IWailaPlugin {

   public FantasticJadePlugin() {
   }

   @Override
   public void registerClient(IWailaClientRegistration registration) {
      registration.addRayTraceCallback((hitResult, accessor, originalAccessor) -> disguise(registration, hitResult, accessor, originalAccessor));
   }

   private static Accessor<?> disguise(
      IWailaClientRegistration registration,
      net.minecraft.world.phys.HitResult hitResult,
      Accessor<?> accessor,
      Accessor<?> originalAccessor
   ) {
      // Durante la ronda no se entrega ningún accessor: no sólo se oculta el jugador disfrazado,
      // también desaparece la ficha de bloques que se dibujaba encima del marcador.
      if (RoundBar.inRound()) {
         return null;
      }

      // originalAccessor es inmutable a través de la cadena de callbacks de Jade; usarlo evita que el
      // resultado dependa del orden de otros addons.
      if (!(originalAccessor instanceof EntityAccessor entityAccessor)) {
         return accessor;
      }

      Entity entity = entityAccessor.getEntity();
      if (!(entity instanceof Player player) || !AvatarState.fullyDisguised(player)) {
         return accessor;
      }

      int prop = AvatarState.prop(player);
      if (prop < 0 || PropShapes.followsLook(prop)) {
         // No existe una entidad mob real detrás del jugador: no permitir que Jade revele nombre,
         // corazones ni tipo Player. Los props de bloque sí reciben ficha completa abajo.
         return null;
      }

      BlockState state = PropHuntClient.stateFor(player);
      if (state == null) {
         return null;
      }

      BlockHitResult fakeHit = new BlockHitResult(hitResult.m_82450_(), Direction.UP, player.m_20183_(), false);
      return registration.blockAccessor()
         .blockState(state)
         .hit(fakeHit)
         // Nunca pedir datos del bloque real que casualmente esté bajo la posición del jugador.
         .serverData(new CompoundTag())
         .serverConnected(false)
         .build();
   }
}
