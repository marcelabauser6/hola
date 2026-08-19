package com.fantasticchameleon.client;

import java.lang.reflect.Method;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Apaga la ficha completa de Jade mientras hay una ronda en marcha y restaura exactamente la
 * preferencia que tenía el usuario al salir.
 *
 * <p>Jade es opcional, por eso la configuración se consulta por reflexión. A diferencia de la
 * implementación anterior, un intento temprano fallido no desactiva esta integración para siempre:
 * se limpia la caché y se vuelve a resolver cuando Jade ya terminó de inicializarse.
 */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JadeOverlay {
   private static Method setDisplayTooltip;
   private static Method shouldDisplayTooltip;
   private static Object general;
   private static Boolean previousDisplay;
   private static boolean hidden;

   private JadeOverlay() {
   }

   public static void updateForRound() {
      if (RoundBar.inRound()) {
         hide();
      } else {
         restore();
      }
   }

   private static void hide() {
      if (hidden || !resolve()) {
         return;
      }

      try {
         previousDisplay = (Boolean)shouldDisplayTooltip.invoke(general);
         setDisplayTooltip.invoke(general, false);
         hidden = true;
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         clearResolution();
      }
   }

   /** Devuelve la opción de Jade al valor anterior, también al desconectar en mitad de una ronda. */
   public static void restore() {
      if (!hidden) {
         return;
      }

      if (!resolve()) {
         // El plugin ya no puede dibujar tras abandonar el mundo; se conserva el intento para el
         // siguiente tick/login en vez de inventar un valor de configuración.
         return;
      }

      try {
         setDisplayTooltip.invoke(general, previousDisplay == null || previousDisplay);
         hidden = false;
         previousDisplay = null;
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         clearResolution();
      }
   }

   @SubscribeEvent
   public static void onLoggingOut(LoggingOut event) {
      restore();
   }

   private static boolean resolve() {
      if (general != null && setDisplayTooltip != null && shouldDisplayTooltip != null) {
         return true;
      }

      try {
         Class<?> config = Class.forName("snownee.jade.api.config.IWailaConfig");
         Object instance = config.getMethod("get").invoke(null);
         Object resolvedGeneral = null;
         for (String getter : new String[]{"getGeneral", "general"}) {
            try {
               resolvedGeneral = config.getMethod(getter).invoke(instance);
               break;
            } catch (NoSuchMethodException ignored) {
               // Compatibilidad entre revisiones de Jade 11.
            }
         }

         if (resolvedGeneral == null) {
            return false;
         }

         general = resolvedGeneral;
         setDisplayTooltip = resolvedGeneral.getClass().getMethod("setDisplayTooltip", boolean.class);
         shouldDisplayTooltip = resolvedGeneral.getClass().getMethod("shouldDisplayTooltip");
         return true;
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         clearResolution();
         return false;
      }
   }

   private static void clearResolution() {
      general = null;
      setDisplayTooltip = null;
      shouldDisplayTooltip = null;
   }
}
