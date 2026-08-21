package com.fantasticchameleon.paint;

import com.fantasticchameleon.compat.StreamCodec;
import io.netty.buffer.ByteBuf;

/**
 * Estado autoritativo de marcha de una criatura capturada.
 *
 * <p>El servidor calcula una sola vez por tick la distancia recorrida y publica su velocidad
 * normalizada. Esa misma medición gobierna el instante de los pasos, evitando que cada cliente
 * reconstruya una cadencia distinta a partir de posiciones interpoladas.
 */
public record PropMotionState(float speed) {
   public static final PropMotionState IDLE = new PropMotionState(0.0F);

   public static final StreamCodec<ByteBuf, PropMotionState> STREAM_CODEC = StreamCodec.of(
      (buf, value) -> buf.writeFloat(value.speed),
      buf -> new PropMotionState(buf.readFloat())
   );

   public PropMotionState {
      speed = Float.isFinite(speed) ? Math.max(0.0F, Math.min(1.0F, speed)) : 0.0F;
   }
}
