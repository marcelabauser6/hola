package com.fantasticicons;

import com.fantasticicons.command.IconCommands;
import com.fantasticicons.data.IconStore;
import com.fantasticicons.icon.NameDecorator;
import com.fantasticicons.net.IconNetwork;
import com.fantasticicons.net.SyncIconsPacket;
import com.fantasticicons.server.ServerNames;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Eventos de servidor: comandos, persistencia y decoracion del nombre. */
public final class IconEvents {
   @SubscribeEvent
   public void onRegisterCommands(RegisterCommandsEvent event) {
      IconCommands.register(event.getDispatcher());
   }

   @SubscribeEvent
   public void onServerStarting(ServerStartingEvent event) {
      IconStore.get().load(event.getServer());
   }

   @SubscribeEvent
   public void onServerStopping(ServerStoppingEvent event) {
      IconStore.get().saveNow();
   }

   @SubscribeEvent
   public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         IconNetwork.sendTo(player, new SyncIconsPacket(IconStore.get().snapshot()));
         ServerNames.refresh(player);
      }
   }

   /**
    * Nombre usado por el chat vanilla ({@code <Nombre (icono)> mensaje}).
    * En el cliente no se toca: alli el nombre flotante se decora en
    * RenderNameTagEvent, que no se cachea y por tanto nunca queda obsoleto.
    */
   @SubscribeEvent
   public void onNameFormat(PlayerEvent.NameFormat event) {
      Player player = event.getEntity();
      if (player == null || player.level() == null || player.level().isClientSide()) {
         return;
      }

      String icon = IconStore.get().iconOf(player.getUUID());
      if (icon != null) {
         Component base = event.getDisplayname() == null ? event.getUsername() : event.getDisplayname();
         event.setDisplayname(NameDecorator.decorate(base, icon));
      }
   }
}
