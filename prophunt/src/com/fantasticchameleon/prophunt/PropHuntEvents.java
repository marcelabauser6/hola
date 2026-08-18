package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.item.ArenaWandItem;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.item.ShotgunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
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
 * <p><b>Prioridad HIGHEST a proposito.</b> El mod cancela el click derecho sobre bloques mientras hay
 * una ronda en marcha (solo deja puertas, trampillas, portones, botones y palancas), que es
 * justamente cuando se juega al Prop Hunt. Si escuchasemos despues, el evento ya vendria cancelado y
 * no habria forma de transformarse en plena partida. Por eso vamos primero y decidimos nosotros.
 *
 * <p>Para no pisar las herramientas que tambien usan el click derecho, se ignora el evento cuando el
 * jugador lleva la varita de arena o la escopeta, o cuando hay una seleccion de mundo pendiente
 * (esto ultimo lo comprueba {@link PropHuntCapture#canTransform}).
 */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PropHuntEvents {
   private static final int REFRESH_INTERVAL = 20;

   private static int tick;

   private PropHuntEvents() {
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (event.getHand() == InteractionHand.MAIN_HAND
         && event.getEntity() instanceof ServerPlayer player
         && !isToolInHand(event.getItemStack())
         && PropHuntCapture.canTransform(player)) {
         BlockState state = event.getLevel().m_8055_(event.getPos());
         if (!state.m_60795_() && PropHuntCapture.captureBlock(player, state)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
      if (event.getHand() == InteractionHand.MAIN_HAND
         && event.getEntity() instanceof ServerPlayer player
         && !isToolInHand(event.getItemStack())
         && PropHuntCapture.canTransform(player)
         && PropHuntCapture.captureEntity(player, event.getTarget())) {
         event.setCanceled(true);
      }
   }

   /**
    * Mantiene el modo de juego sincronizado en cada jugador conectado.
    *
    * <p>Se hace por tick espaciado en vez de engancharse a las entradas y salidas de sala porque hay
    * muchas rutas por las que se cambia de sala (crear, entrar, salir, expulsar, banear, borrar la
    * sala) y perder una dejaria el cliente mostrando los menus del modo equivocado.
    */
   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
         if (++tick % REFRESH_INTERVAL == 0) {
            for (ServerPlayer player : event.getServer().m_6846_().m_11314_()) {
               PropHunt.refresh(player);
            }
         }
      }
   }

   /** Herramientas del mod que ya tienen su propio significado con click derecho. */
   private static boolean isToolInHand(ItemStack stack) {
      if (stack == null || stack.m_41619_()) {
         return false;
      }

      return ArenaWandItem.is(stack) || stack.m_41720_() instanceof ShotgunItem || stack.m_150930_(FantasticItems.PAINT_BRUSH.get());
   }
}
