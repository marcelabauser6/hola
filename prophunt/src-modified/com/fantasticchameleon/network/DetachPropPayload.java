package com.fantasticchameleon.network;

import com.fantasticchameleon.compat.CustomPacketPayload;
import com.fantasticchameleon.compat.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Solicita soltar el ancla conservando intacta la transformación actual. */
public record DetachPropPayload() implements CustomPacketPayload {
   public static final DetachPropPayload INSTANCE = new DetachPropPayload();
   public static final CustomPacketPayload.Type<DetachPropPayload> TYPE = new CustomPacketPayload.Type<>(
      new ResourceLocation("fantastic_chameleon", "detach_prop")
   );
   public static final StreamCodec<ByteBuf, DetachPropPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
