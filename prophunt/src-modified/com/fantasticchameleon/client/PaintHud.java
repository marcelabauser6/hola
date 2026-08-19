package com.fantasticchameleon.client;

import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.prophunt.PropHuntClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PaintHud {
   private PaintHud() {
   }

   public static void renderHud(GuiGraphics graphics) {
      Minecraft mc = Minecraft.m_91087_();
      if (!HudPanel.ownScreen(mc)) {
         if (ClientPick.active() && !ClientShims.hideGui(mc)) {
            drawPickBanner(graphics, mc);
         }

         if (ArenaShot.active() && !ClientShims.hideGui(mc)) {
            String hint = ArenaShot.hint().getString();
            Gfx.text(graphics, mc.f_91062_, hint, mc.m_91268_().m_85445_() / 2 - mc.f_91062_.m_92895_(hint) / 2, mc.m_91268_().m_85446_() - 46, -1);
         }

         if (mc.f_91074_ != null && !ClientShims.hideGui(mc)) {
            boolean locked = Services.PLATFORM.get(mc.f_91074_, PaintAttachments.LOCKED);
            int cx = mc.m_91268_().m_85445_() / 2;
            if (locked && FreeCam.active() && !WorldBrush.paintMode()) {
               drawViewfinderCorners(graphics, mc);
               List<String[]> fcHints = new ArrayList<>();
               fcHints.add(new String[]{"F", Component.m_237115_("fantastic.hint.paint").getString()});
               fcHints.add(new String[]{"Esc", Component.m_237115_("fantastic.hint.leave").getString()});
               if (FreeCam.canCycle()) {
                  fcHints.add(new String[]{"L/R click", Component.m_237115_("fantastic.hint.switch").getString()});
               }

               int fcY = RoundBar.bottom() + 4;
               ControlHints.rowCentered(graphics, mc.f_91062_, cx, fcY, fcHints);
               String fcWatched = FreeCam.watchedName();
               if (fcWatched != null) {
                  String fcLabel = Component.m_237110_("fantastic.freecam.spectating", new Object[]{fcWatched}).getString();
                  Gfx.text(graphics, mc.f_91062_, fcLabel, cx - mc.f_91062_.m_92895_(fcLabel) / 2, fcY + 12, -8875);
               }
            } else if (locked && !FreeCam.active()) {
               // Debajo de la barra de ronda: arriba fijo se solapaba con las cabezas y el reloj. El
               // suelo de 46 cubre el primer frame, cuando la barra todavia no ha publicado su alto.
               int topY = RoundBar.inRound() ? Math.max(RoundBar.bottom(), 42) + 4 : 4;
               String mode = "● " + Component.m_237115_("fantastic.hud.hiding").getString();
               Gfx.text(graphics, mc.f_91062_, mode, cx - mc.f_91062_.m_92895_(mode) / 2, topY, -11141291);
               // En Prop Hunt no hay pintura ni poses, asi que se muestran solo las teclas que existen.
               ControlHints.rowCentered(
                  graphics,
                  mc.f_91062_,
                  cx,
                  topY + 10,
                  PropHuntClient.isPropHunt()
                     ? List.of(
                        new String[]{"Space", Component.m_237115_("fantastic.hint.move").getString()},
                        new String[]{"R", Component.m_237115_("fantastic.prophunt.hint_clear").getString()}
                     )
                     : List.of(
                        new String[]{"Space", Component.m_237115_("fantastic.hint.move").getString()},
                        new String[]{"F", Component.m_237115_("fantastic.hint.paint").getString()},
                        new String[]{"R", Component.m_237115_("fantastic.hint.pose").getString()},
                        new String[]{"Esc", Component.m_237115_("fantastic.hint.menu").getString()}
                     )
               );
            }

            if (!locked && !FreeCam.active() && WindowLayouts.flag("HotkeyCard", true) && ChameleonArmor.fullCoverage(mc.f_91074_)) {
               boolean propHunt = PropHuntClient.isPropHunt();
               List<String[]> hints = new ArrayList<>(4);
               if (propHunt) {
                  hints.add(new String[]{"Right click", Component.m_237115_("fantastic.prophunt.hint_capture").getString()});
                  hints.add(new String[]{keyName(LockControls.paintKey, "F"), Component.m_237115_("fantastic.prophunt.hint_place").getString()});
                  hints.add(new String[]{keyName(LockControls.actKey, "V"), Component.m_237115_("fantastic.prophunt.hint_act").getString()});
                  hints.add(new String[]{keyName(LockControls.poseWheelKey, "R"), Component.m_237115_("fantastic.prophunt.hint_clear").getString()});
               } else {
                  hints.add(new String[]{keyName(LockControls.paintKey, "F"), Component.m_237115_("fantastic.hint.hide_paint").getString()});
                  hints.add(new String[]{keyName(LockControls.poseWheelKey, "R"), Component.m_237115_("fantastic.hint.pose").getString()});
               }

               hints.add(new String[]{keyName(LockControls.provokeKey, "G"), Component.m_237115_("fantastic.hint.whistle").getString()});
               hints.add(new String[]{keyName(LockControls.crawlKey, "C"), Component.m_237115_("fantastic.hint.crawl").getString()});
               hints.add(new String[]{"Sneak+Space", Component.m_237115_("fantastic.hint.climb").getString()});
               ControlHints.legendCardBottomRight(graphics, mc.f_91062_, mc.m_91268_().m_85445_() - 4, mc.m_91268_().m_85446_() - 4, hints, 0.72F);
            }
         }
      }
   }

   private static void drawPickBanner(GuiGraphics g, Minecraft mc) {
      int cx = mc.m_91268_().m_85445_() / 2;
      int y = mc.m_91268_().m_85446_() / 2 + 24;
      String what = Component.m_237115_(ClientPick.labelKey()).getString();
      String how = Component.m_237115_("fantastic.pick.how").getString();
      int w = Math.max(mc.f_91062_.m_92895_(what), mc.f_91062_.m_92895_(how)) + 16;
      HudPanel.panel(g, cx - w / 2, y - 6, w, 30);
      Gfx.centeredText(g, mc.f_91062_, what, cx, y, -8396640);
      Gfx.centeredText(g, mc.f_91062_, how, cx, y + 12, -4604988);
   }

   private static String keyName(KeyMapping key, String fallback) {
      return key == null ? fallback : key.m_90863_().getString();
   }

   private static void drawViewfinderCorners(GuiGraphics g, Minecraft mc) {
      int w = mc.m_91268_().m_85445_();
      int h = mc.m_91268_().m_85446_();
      int inset = 18;
      int len = 22;
      int t = 2;
      int shadow = 1879048192;
      int col = -1;

      for (int pass = 0; pass < 2; pass++) {
         int o = pass == 0 ? 1 : 0;
         int c = pass == 0 ? shadow : col;
         g.m_280509_(inset + o, inset + o, inset + len + o, inset + t + o, c);
         g.m_280509_(inset + o, inset + o, inset + t + o, inset + len + o, c);
         g.m_280509_(w - inset - len + o, inset + o, w - inset + o, inset + t + o, c);
         g.m_280509_(w - inset - t + o, inset + o, w - inset + o, inset + len + o, c);
         g.m_280509_(inset + o, h - inset - t + o, inset + len + o, h - inset + o, c);
         g.m_280509_(inset + o, h - inset - len + o, inset + t + o, h - inset + o, c);
         g.m_280509_(w - inset - len + o, h - inset - t + o, w - inset + o, h - inset + o, c);
         g.m_280509_(w - inset - t + o, h - inset - len + o, w - inset + o, h - inset + o, c);
      }
   }
}
