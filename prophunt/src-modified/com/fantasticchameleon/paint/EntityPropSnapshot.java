package com.fantasticchameleon.paint;

import com.fantasticchameleon.compat.ByteBufCodecs;
import com.fantasticchameleon.compat.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Snapshot atomico de una criatura capturada por Prop Hunt.
 *
 * <p>El tipo y el NBT viajan juntos para que ningun cliente llegue a combinar una entidad nueva con
 * el equipo o variante de la captura anterior. Las dimensiones se calculan en el servidor y se usan
 * tambien para la hitbox del jugador disfrazado.
 */
public record EntityPropSnapshot(String typeId, CompoundTag tag, float width, float height, float eyeHeight, float movementSpeed) {
   public static final int MAX_NBT_BYTES = 65536;
   public static final EntityPropSnapshot NONE = new EntityPropSnapshot("", new CompoundTag(), 0.6F, 1.8F, 1.62F, 0.0F);

   public static final StreamCodec<ByteBuf, EntityPropSnapshot> STREAM_CODEC = StreamCodec.of(
      EntityPropSnapshot::encode,
      EntityPropSnapshot::decode
   );

   public EntityPropSnapshot {
      typeId = typeId == null ? "" : typeId;
      tag = tag == null ? new CompoundTag() : tag.m_6426_();
      width = validDimension(width, 0.6F);
      height = validDimension(height, 1.8F);
      eyeHeight = validEye(eyeHeight, height);
      movementSpeed = Float.isFinite(movementSpeed) && movementSpeed > 0.0F && movementSpeed <= 4.0F ? movementSpeed : 0.0F;
   }

   public boolean present() {
      return !this.typeId.isEmpty();
   }

   public CompoundTag tagCopy() {
      return this.tag.m_6426_();
   }

   private static void encode(ByteBuf raw, EntityPropSnapshot value) {
      FriendlyByteBuf buf = new FriendlyByteBuf(raw);
      buf.m_130072_(value.typeId, 128);
      buf.m_130079_(value.tag);
      raw.writeFloat(value.width);
      raw.writeFloat(value.height);
      raw.writeFloat(value.eyeHeight);
      raw.writeFloat(value.movementSpeed);
   }

   private static EntityPropSnapshot decode(ByteBuf raw) {
      FriendlyByteBuf buf = new FriendlyByteBuf(raw);
      String type = buf.m_130136_(128);
      CompoundTag tag = buf.m_130081_(new NbtAccounter(MAX_NBT_BYTES));
      float width = raw.readFloat();
      float height = raw.readFloat();
      float eye = raw.readFloat();
      float movementSpeed = raw.readFloat();
      return new EntityPropSnapshot(type, tag, width, height, eye, movementSpeed);
   }

   private static float validDimension(float value, float fallback) {
      return Float.isFinite(value) && value > 0.0F && value <= 32.0F ? value : fallback;
   }

   private static float validEye(float value, float height) {
      return Float.isFinite(value) && value >= 0.0F && value <= height ? value : height * 0.85F;
   }
}
