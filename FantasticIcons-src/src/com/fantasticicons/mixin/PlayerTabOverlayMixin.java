package com.fantasticicons.mixin;

import com.fantasticicons.client.ClientTabDecorator;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compatibilidad final con el plugin TAB (NEZNAMY) en Mohist.
 *
 * Se inyecta en RETURN para recibir exactamente el componente que Minecraft va
 * a medir y renderizar. El nombre SRG se usa directamente porque el jar de
 * produccion de Minecraft 1.20.1 expone m_94549_.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
   @Inject(
      method = "m_94549_(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
      at = @At("RETURN"),
      cancellable = true,
      require = 1,
      remap = false
   )
   private void fantasticicons$decorateTabName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> callback) {
      callback.setReturnValue(ClientTabDecorator.decorate(playerInfo, callback.getReturnValue()));
   }
}
