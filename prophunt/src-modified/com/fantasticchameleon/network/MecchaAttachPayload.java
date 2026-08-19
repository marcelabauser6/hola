package com.fantasticchameleon.network;

import com.fantasticchameleon.compat.CustomPacketPayload;
import com.fantasticchameleon.compat.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Cara y punto exactos del bloque seleccionado para el acople de Meccha. */
public record MecchaAttachPayload(BlockPos pos, Direction face, float hitX, float hitY, float hitZ, float yaw) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<MecchaAttachPayload> TYPE = new CustomPacketPayload.Type<>(
      new ResourceLocation("fantastic_chameleon", "meccha_attach")
   );
   public static final StreamCodec<ByteBuf, MecchaAttachPayload> STREAM_CODEC = StreamCodec.of(
      (buf, value) -> {
         buf.writeLong(value.pos.m_121878_());
         buf.writeByte(value.face.m_122411_());
         buf.writeFloat(value.hitX);
         buf.writeFloat(value.hitY);
         buf.writeFloat(value.hitZ);
         buf.writeFloat(value.yaw);
      },
      buf -> new MecchaAttachPayload(
         BlockPos.m_122022_(buf.readLong()),
         Direction.m_122376_(buf.readUnsignedByte()),
         buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()
      )
   );

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
