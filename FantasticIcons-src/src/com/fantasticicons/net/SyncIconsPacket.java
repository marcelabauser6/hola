package com.fantasticicons.net;

import com.fantasticicons.client.ClientIconStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Tabla completa jugador -> icono. Son unos pocos bytes por jugador. */
public class SyncIconsPacket {
   public final Map<UUID, String> icons;

   public SyncIconsPacket(Map<UUID, String> icons) {
      this.icons = icons;
   }

   public static void encode(SyncIconsPacket message, FriendlyByteBuf buffer) {
      buffer.writeVarInt(message.icons.size());

      for (Map.Entry<UUID, String> entry : message.icons.entrySet()) {
         buffer.writeUUID(entry.getKey());
         buffer.writeUtf(entry.getValue(), 64);
      }
   }

   public static SyncIconsPacket decode(FriendlyByteBuf buffer) {
      int size = buffer.readVarInt();
      Map<UUID, String> icons = new LinkedHashMap<>(Math.max(4, size));

      for (int i = 0; i < size; i++) {
         UUID id = buffer.readUUID();
         icons.put(id, buffer.readUtf(64));
      }

      return new SyncIconsPacket(icons);
   }

   public static void handle(SyncIconsPacket message, Supplier<NetworkEvent.Context> context) {
      NetworkEvent.Context ctx = context.get();
      ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientIconStore.receive(message.icons)));
      ctx.setPacketHandled(true);
   }
}
