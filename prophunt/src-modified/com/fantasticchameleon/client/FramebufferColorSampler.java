package com.fantasticchameleon.client;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Lee un único píxel ya iluminado del framebuffer, antes de dibujar la GUI.
 *
 * <p>Es la ruta de reserva de la pipeta, para cuando lo señalado no es un bloque (una entidad, el
 * cielo, una partícula). Para bloques se usa el texel exacto, que no arrastra iluminación ni niebla.
 */
public final class FramebufferColorSampler {
   private static final ByteBuffer PIXEL = BufferUtils.createByteBuffer(4);

   private FramebufferColorSampler() {
   }

   public static int sample(Minecraft mc, double guiX, double guiY) {
      int fbW = Math.max(1, mc.m_91268_().m_85441_());
      int fbH = Math.max(1, mc.m_91268_().m_85442_());
      // Se usa la escala real de la GUI, no fbW/guiW: el ancho escalado se redondea hacia arriba, así
      // que ese cociente es algo menor que la escala y la muestra se desviaba más cuanto más lejos del
      // origen. Y se muestrea el CENTRO de la celda (+0,5), no su esquina superior izquierda, que
      // caía hasta 3 píxeles arriba y a la izquierda del punto señalado.
      double scale = mc.m_91268_().m_85449_();
      if (scale <= 0.0) {
         scale = 1.0;
      }

      int x = clamp((int)Math.floor((guiX + 0.5) * scale), fbW);
      int yTop = clamp((int)Math.floor((guiY + 0.5) * scale), fbH);
      int y = fbH - 1 - yTop;

      PIXEL.clear();
      GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXEL);
      int r = PIXEL.get(0) & 255;
      int g = PIXEL.get(1) & 255;
      int b = PIXEL.get(2) & 255;
      // El alfa del render target principal no es fiable: descartar la muestra cuando valía 0 hacía
      // que la pipeta pareciera no responder y conservara el color anterior.
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int clamp(int value, int size) {
      return Math.max(0, Math.min(size - 1, value));
   }
}
