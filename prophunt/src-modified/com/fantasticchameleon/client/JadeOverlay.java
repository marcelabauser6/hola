package com.fantasticchameleon.client;

import java.lang.reflect.Method;

/**
 * Apaga la ficha de Jade mientras hay una ronda en marcha, y la devuelve al terminar.
 *
 * <p>La ficha de Jade se dibuja arriba en el centro, justo encima del marcador de la ronda, y lo tapaba.
 * Durante la partida no aporta nada y estorba, así que se apaga para todos los que jueguen.
 *
 * <p>Se hace por reflexión a propósito: Jade es opcional y no forma parte de las dependencias con las que
 * se compila este mod, de modo que no puede referenciarse su API directamente sin obligar a tenerlo
 * instalado. Si no está, o si cambia su API, esto no hace nada y el resto sigue funcionando igual.
 */
public final class JadeOverlay {
   private static Boolean available;
   private static Method setDisplayTooltip;
   private static Object general;
   private static boolean hidden;

   private JadeOverlay() {
   }

   public static void updateForRound() {
      boolean inRound = RoundBar.inRound();
      if (inRound == hidden) {
         return;
      }

      if (apply(!inRound)) {
         hidden = inRound;
      }
   }

   /** Devuelve la ficha al estado normal; se llama al salir de la partida o del mundo. */
   public static void restore() {
      if (hidden && apply(true)) {
         hidden = false;
      }
   }

   private static boolean apply(boolean show) {
      if (!resolve()) {
         return false;
      }

      try {
         setDisplayTooltip.invoke(general, show);
         return true;
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         available = Boolean.FALSE;
         return false;
      }
   }

   private static boolean resolve() {
      if (available != null) {
         return available;
      }

      available = Boolean.FALSE;
      try {
         Class<?> config = Class.forName("snownee.jade.api.config.IWailaConfig");
         Object instance = config.getMethod("get").invoke(null);
         Object generalConfig = null;
         for (String getter : new String[]{"getGeneral", "general"}) {
            try {
               generalConfig = config.getMethod(getter).invoke(instance);
               break;
            } catch (NoSuchMethodException ignored) {
               // Se prueba el siguiente nombre.
            }
         }

         if (generalConfig == null) {
            return false;
         }

         general = generalConfig;
         setDisplayTooltip = generalConfig.getClass().getMethod("setDisplayTooltip", boolean.class);
         available = Boolean.TRUE;
      } catch (ReflectiveOperationException | RuntimeException ignored) {
         // Jade no está instalado o su API cambió: no se toca nada.
      }

      return available;
   }
}
