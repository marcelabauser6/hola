package com.fantasticchameleon.client;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Lee un único píxel ya iluminado y postprocesado del framebuffer antes de dibujar la GUI. */
public final class FramebufferColorSampler {
   private static final ByteBuffer PIXEL = BufferUtils.createByteBuffer(4);

   private FramebufferColorSampler() {
   }

   public static int sample(Minecraft mc, double guiX, double guiY) {
      int guiW = Math.max(1, mc.m_91268_().m_85445_());
      int guiH = Math.max(1, mc.m_91268_().m_85446_());
      int fbW = Math.max(1, mc.m_91268_().m_85441_());
      int fbH = Math.max(1, mc.m_91268_().m_85442_());
      int x = Math.max(0, Math.min(fbW - 1, (int)Math.floor(guiX * (double)fbW / (double)guiW)));
      int yTop = Math.max(0, Math.min(fbH - 1, (int)Math.floor(guiY * (double)fbH / (double)guiH)));
      int y = fbH - 1 - yTop;

      PIXEL.clear();
      GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXEL);
      int r = PIXEL.get(0) & 255;
      int g = PIXEL.get(1) & 255;
      int b = PIXEL.get(2) & 255;
      int a = PIXEL.get(3) & 255;
      return a == 0 ? 0 : 0xFF000000 | r << 16 | g << 8 | b;
   }
}
