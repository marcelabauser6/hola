package com.fantasticchameleon.network;

import com.fantasticchameleon.compat.CustomPacketPayload;
import com.fantasticchameleon.compat.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * El jugador disfrazado hace el gesto propio de su prop: la oveja pastar, la vaca mugir, el creeper
 * sisear. No lleva datos porque el gesto se deduce del disfraz que ya tiene puesto en el servidor.
 */
public record PropActPayload() implements CustomPacketPayload {
   public static final PropActPayload INSTANCE = new PropActPayload();
   public static final CustomPacketPayload.Type<PropActPayload> TYPE = new CustomPacketPayload.Type<>(
      new ResourceLocation("fantastic_chameleon", "prop_act")
   );
   public static final StreamCodec<ByteBuf, PropActPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
