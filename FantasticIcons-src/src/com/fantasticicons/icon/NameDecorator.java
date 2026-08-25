package com.fantasticicons.icon;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Pega el glifo al final del nombre: "Adim Pewez (icono)". */
public final class NameDecorator {
   private NameDecorator() {
   }

   public static MutableComponent decorate(Component base, String iconId) {
      MutableComponent name = Component.empty();
      if (base != null) {
         name.append(base);
      }

      if (!IconRegistry.exists(iconId)) {
         return name;
      }

      return name.append(Component.literal(" ")).append(IconRegistry.glyph(iconId));
   }
}
