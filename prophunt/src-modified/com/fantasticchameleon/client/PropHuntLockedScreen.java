package com.fantasticchameleon.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Panel lateral transparente y compacto mostrado al fijarse en Prop Hunt. */
public final class PropHuntLockedScreen extends Screen {
   private static final int PANEL_W = 150;
   private static final int PANEL_H = 54;
   private int left;
   private int top;

   public PropHuntLockedScreen() {
      super(Component.m_237115_("fantastic.prophunt.controls"));
   }

   @Override
   protected void m_7856_() {
      this.left = 8;
      this.top = Math.max(24, this.f_96544_ / 2 - PANEL_H / 2);
      this.m_142416_(
         Button.m_253074_(Component.m_237115_("fantastic.prophunt.detach"), b -> LockControls.detach(this.f_96541_))
            .m_252987_(this.left + 8, this.top + 8, PANEL_W - 16, 16)
            .m_253136_()
      );
      this.m_142416_(
         Button.m_253074_(Component.m_237115_("fantastic.prophunt.revert"), b -> LockControls.revertProp(this.f_96541_))
            .m_252987_(this.left + 8, this.top + 30, PANEL_W - 16, 16)
            .m_253136_()
      );
   }

   @Override
   public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      HudPanel.panel(graphics, this.left, this.top, PANEL_W, PANEL_H);
      super.m_88315_(graphics, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
      int sneakCode = ClientAccessors.boundKey(this.f_96541_.f_91066_.f_92090_).m_84873_();
      if (keyCode == 32 || keyCode == sneakCode) {
         LockControls.detach(this.f_96541_);
         return true;
      }
      return super.m_7933_(keyCode, scanCode, modifiers);
   }

   /** El mundo y la ronda siguen corriendo mientras se muestran los dos controles. */
   @Override
   public boolean m_6913_() {
      return false;
   }
}
