package com.fantasticicons.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal opcional: los clientes sin el mod pueden conectarse igual
 * (acceptMissingOr), simplemente no veran los iconos.
 */
public final class IconNetwork {
   public static SimpleChannel CHANNEL;

   private IconNetwork() {
   }

   public static void init() {
      CHANNEL = NetworkRegistry.newSimpleChannel(
         new ResourceLocation("fantasticicons", "main"), () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1")
      );
      CHANNEL.registerMessage(0, SyncIconsPacket.class, SyncIconsPacket::encode, SyncIconsPacket::decode, SyncIconsPacket::handle);
   }

   public static void sendTo(ServerPlayer player, Object message) {
      if (CHANNEL != null && player != null) {
         CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
      }
   }

   public static void sendToAll(Object message) {
      if (CHANNEL != null) {
         CHANNEL.send(PacketDistributor.ALL.noArg(), message);
      }
   }
}
