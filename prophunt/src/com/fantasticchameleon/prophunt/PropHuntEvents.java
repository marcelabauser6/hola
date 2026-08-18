package com.fantasticchameleon.prophunt;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enganche del modo Prop Hunt al click derecho.
 *
 * <p>Se registra por anotacion en lugar de tocar el entrypoint del mod, para no alterar el orden ni el
 * contenido de los listeners que ya existen.
 *
 * <p>Corre con prioridad baja a proposito: asi las herramientas de staff que ya escuchan este evento
 * (seleccion de esquinas de arena, varita, modo de picking) se resuelven antes y siguen funcionando
 * igual. Solo actuamos si nadie mas cancelo el evento.
 */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PropHuntEvents {

   private PropHuntEvents() {
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (!event.isCanceled() && event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer player) {
         if (PropHuntCapture.canTransform(player)) {
            BlockState state = event.getLevel().m_8055_(event.getPos());
            if (!state.m_60795_() && PropHuntCapture.captureBlock(player, state)) {
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
      if (!event.isCanceled() && event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer player) {
         if (PropHuntCapture.canTransform(player) && PropHuntCapture.captureEntity(player, event.getTarget())) {
            event.setCanceled(true);
         }
      }
   }
}
