package com.fantasticicons.client;

import com.fantasticicons.FantasticIcons;
import com.fantasticicons.icon.NameDecorator;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Nombre flotante sobre la cabeza. Se recalcula cada frame, nunca queda obsoleto. */
@Mod.EventBusSubscriber(modid = FantasticIcons.MOD_ID, value = {Dist.CLIENT}, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientIconEvents {
   private ClientIconEvents() {
   }

   @SubscribeEvent
   public static void onRenderNameTag(RenderNameTagEvent event) {
      if (event.getEntity() instanceof Player player) {
         String icon = ClientIconStore.iconOf(player.getUUID());
         if (icon != null) {
            event.setContent(NameDecorator.decorate(event.getContent(), icon));
         }
      }
   }

   /**
    * Ultimo punto del pipeline de chat: EssentialsChat ya aplico aqui su
    * formato Bukkit, incluidos el prefijo y los colores de LuckPerms.
    */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onChatReceived(ClientChatReceivedEvent event) {
      event.setMessage(ClientChatDecorator.decorate(event.getMessage(), event.getSender(), event.isSystem()));
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      ClientIconStore.clear();
   }
}
