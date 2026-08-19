package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * El mod no entrega ningún objeto al entrar al servidor.
 *
 * <p>El mod repartía un pincel la primera vez que alguien se conectaba, marcándolo con el atributo
 * {@code STARTER_BRUSH} para no repetirlo. Aquí se marca ese atributo como ya entregado con prioridad
 * máxima, es decir <b>antes</b> de que corra el reparto, de modo que no llega a ocurrir para nadie.
 *
 * <p>Se hace así, y no borrando el reparto, porque ese código vive en una clase del mod que el
 * decompilador no dejó compilable (perdió los genéricos de casi treinta lambdas de eventos): tocarla
 * obligaría a reescribirla entera y arriesgar el resto del mod. El efecto observable es el mismo.
 */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OpOnlyGuard {
   private OpOnlyGuard() {
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         Services.PLATFORM.set(sp, PaintAttachments.STARTER_BRUSH, true);
      }
   }
}
