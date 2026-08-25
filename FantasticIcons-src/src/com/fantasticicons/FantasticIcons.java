package com.fantasticicons;

import com.fantasticicons.icon.IconRegistry;
import com.fantasticicons.net.IconNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Fantastic Icons: iconos verificados al final del nombre del jugador
 * (nombre flotante, chat y lista de tab). Forge 1.20.1.
 *
 * Los iconos son glifos de una fuente bitmap propia, asi que el cliente
 * necesita tener el mod instalado para verlos.
 */
@Mod(FantasticIcons.MOD_ID)
public class FantasticIcons {
   public static final String MOD_ID = "fantasticicons";
   public static final Logger LOGGER = LogUtils.getLogger();

   public FantasticIcons() {
      IconNetwork.init();
      MinecraftForge.EVENT_BUS.register(new IconEvents());
      LOGGER.info("[FantasticIcons] v1.0.0 listo con " + IconRegistry.count() + " iconos. Comando: /fsicons");
   }
}
