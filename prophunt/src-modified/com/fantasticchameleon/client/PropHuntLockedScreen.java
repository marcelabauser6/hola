package com.fantasticchameleon.client;

import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Controles de Prop Hunt al quedar fijado.
 *
 * <p>Dos filas diminutas pegadas al borde izquierdo, con el mismo lenguaje visual que el resto del HUD
 * del mod. No usa botones vanilla a propósito: eran enormes, opacos y desentonaban con todo lo demás,
 * y encima tapaban media pantalla justo cuando hay que estar mirando el mundo.
 */
public final class PropHuntLockedScreen extends Screen {
   private static final int W = 76;
   private static final int H = 12;
   private static final int GAP = 2;
   private static final int MARGIN = 4;
   private int left;
   private int top;

   public PropHuntLockedScreen() {
      super(Component.m_237115_("fantastic.prophunt.controls"));
   }

   @Override
   protected void m_7856_() {
      this.left = MARGIN;
      this.top = this.f_96544_ / 2 - (H * 2 + GAP) / 2;
   }

   @Override
   public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      row(graphics, this.top, "fantastic.prophunt.detach", mouseX, mouseY);
      row(graphics, this.top + H + GAP, "fantastic.prophunt.revert", mouseX, mouseY);
   }

   private void row(GuiGraphics graphics, int y, String key, int mouseX, int mouseY) {
      boolean hover = hovering(mouseX, mouseY, y);
      HudPanel.panel(graphics, this.left, y, W, H, hover ? HudPanel.ACCENT : HudPanel.SEP);
      Gfx.text(
         graphics,
         this.f_96547_,
         Component.m_237115_(key),
         this.left + 6,
         y + 2,
         hover ? HudPanel.TEXT : HudPanel.TEXT_DIM
      );
   }

   private boolean hovering(int mouseX, int mouseY, int y) {
      return mouseX >= this.left && mouseX <= this.left + W && mouseY >= y && mouseY <= y + H;
   }

   @Override
   public boolean m_6375_(double mouseX, double mouseY, int button) {
      if (button == 0) {
         if (hovering((int)mouseX, (int)mouseY, this.top)) {
            LockControls.detach(this.f_96541_);
            return true;
         }

         if (hovering((int)mouseX, (int)mouseY, this.top + H + GAP)) {
            LockControls.revertProp(this.f_96541_);
            return true;
         }
      }

      return super.m_6375_(mouseX, mouseY, button);
   }

   @Override
   public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 32 || keyCode == sneakKeyCode()) {
         LockControls.detach(this.f_96541_);
         return true;
      }

      // Revertir también con teclado: con el panel abierto las pulsaciones van a la pantalla, así que
      // sin esto la reversión solo era alcanzable con el ratón.
      if (keyCode == revertKeyCode()) {
         LockControls.revertProp(this.f_96541_);
         return true;
      }

      return super.m_7933_(keyCode, scanCode, modifiers);
   }

   private int revertKeyCode() {
      Key bound = LockControls.poseWheelKey == null ? null : ClientAccessors.boundKey(LockControls.poseWheelKey);
      return bound != null && bound.m_84868_() == Type.KEYSYM ? bound.m_84873_() : Integer.MIN_VALUE;
   }

   /** Agacharse remapeado a un botón del ratón también tiene que soltar, no solo las teclas. */
   private int sneakKeyCode() {
      Key bound = ClientAccessors.boundKey(this.f_96541_.f_91066_.f_92090_);
      return bound.m_84868_() == Type.KEYSYM ? bound.m_84873_() : Integer.MIN_VALUE;
   }

   @Override
   public boolean m_5534_(char codePoint, int modifiers) {
      return false;
   }

   /** El mundo y la ronda siguen corriendo mientras se muestran los dos controles. */
   @Override
   public boolean m_6913_() {
      return false;
   }
}
