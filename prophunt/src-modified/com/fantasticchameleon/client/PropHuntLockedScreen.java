package com.fantasticchameleon.client;

import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Controles de Prop Hunt al quedar fijado.
 *
 * <p>Dos filas compactas pegadas al borde izquierdo, con el mismo lenguaje visual que el resto del HUD.
 * El ancho se calcula a partir del texto traducido más largo, porque con un ancho fijo la etiqueta de
 * revertir se salía de su recuadro en español. El texto va en blanco y con sombra: el gris tenue del HUD
 * era ilegible sobre hierba clara.
 */
public final class PropHuntLockedScreen extends Screen {
   private static final int H = 14;
   private static final int GAP = 3;
   private static final int PAD_X = 7;
   private static final int MARGIN = 6;
   private int left;
   private int top;
   private int width;
   private boolean orbiting;

   public PropHuntLockedScreen() {
      super(Component.m_237115_("fantastic.prophunt.controls"));
   }

   @Override
   protected void m_7856_() {
      int detach = this.f_96547_.m_92852_(Component.m_237115_("fantastic.prophunt.detach"));
      int revert = this.f_96547_.m_92852_(Component.m_237115_("fantastic.prophunt.revert"));
      this.width = Math.max(detach, revert) + PAD_X * 2;
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
      HudPanel.panel(graphics, this.left, y, this.width, H, hover ? HudPanel.ACCENT : HudPanel.SEP);
      Gfx.text(
         graphics,
         this.f_96547_,
         Component.m_237115_(key),
         this.left + PAD_X,
         y + (H - 8) / 2,
         hover ? HudPanel.ACCENT : HudPanel.TEXT,
         true
      );
   }

   private boolean hovering(int mouseX, int mouseY, int y) {
      return mouseX >= this.left && mouseX <= this.left + this.width && mouseY >= y && mouseY <= y + H;
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

      // Fuera de los dos botones, arrastrar con el botón izquierdo gira la cámara libre. Con el panel
      // abierto el cursor está libre, así que el ratón no llega al jugador y sin esto la vista no se
      // podía mover. Si la cámara no está activa no se consume el clic.
      if (button == 0 && FreeCam.active()) {
         this.orbiting = true;
         return true;
      }

      return super.m_6375_(mouseX, mouseY, button);
   }

   @Override
   public boolean m_7979_(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.orbiting) {
         FreeCam.orbit(dragX, dragY);
         return true;
      }

      return super.m_7979_(mouseX, mouseY, button, dragX, dragY);
   }

   @Override
   public boolean m_6348_(double mouseX, double mouseY, int button) {
      this.orbiting = false;
      return super.m_6348_(mouseX, mouseY, button);
   }

   /** La rueda acerca y aleja la cámara, como en el pintor. */
   @Override
   public boolean m_6050_(double mouseX, double mouseY, double scrollY) {
      if (scrollY != 0.0 && FreeCam.active()) {
         FreeCam.zoom(scrollY > 0.0 ? 1 : -1);
         return true;
      }

      return super.m_6050_(mouseX, mouseY, scrollY);
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

   /** Agacharse remapeado a un botón del ratón lo cubre el detector global de LockControls. */
   private int sneakKeyCode() {
      Key bound = ClientAccessors.boundKey(this.f_96541_.f_91066_.f_92090_);
      return bound.m_84868_() == Type.KEYSYM ? bound.m_84873_() : Integer.MIN_VALUE;
   }

   private int revertKeyCode() {
      Key bound = LockControls.poseWheelKey == null ? null : ClientAccessors.boundKey(LockControls.poseWheelKey);
      return bound != null && bound.m_84868_() == Type.KEYSYM ? bound.m_84873_() : Integer.MIN_VALUE;
   }

   @Override
   public boolean m_5534_(char codePoint, int modifiers) {
      return false;
   }

   /**
    * El mundo y la ronda siguen corriendo mientras se muestran los dos controles.
    *
    * <p>Este es {@code isPauseScreen}. Antes estaba sobrescrito {@code shouldCloseOnEsc} por error, con
    * dos consecuencias: el panel <b>pausaba el servidor integrado</b> —de ahí que al pulsar F se
    * congelara todo y volviera al desacoplarte, que es cuando se cierra— y Esc no cerraba el panel.
    */
   @Override
   public boolean m_7043_() {
      return false;
   }
}
