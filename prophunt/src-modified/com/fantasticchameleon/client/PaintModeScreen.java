package com.fantasticchameleon.client;

import com.fantasticchameleon.item.ArmorPaintHandler;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.network.SetSizePayload;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.BodyPart;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.ClientNet;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class PaintModeScreen extends Screen {
   private static final double REACH = 8.0;
   private static final int MAP_TEX = 64;
   private static final double MAP_SCALE = 1.6;
   private static final int MAP_SIZE = (int)Math.round(102.4);
   private static final int MIN_MAP_ROWS = 20;
   private final ColorPicker picker = new ColorPicker(8, 24);
   private boolean painting;
   private boolean pipetteHeld;
   private boolean mapPainting;
   private long lastSparkleMs;
   private static final int STRIP_H = 13;
   private static final int WBTN = 10;
   private static final float DEFAULT_PANEL_SCALE = 1.0F;
   private static final float PICKER_SCALE = 0.9F;
   private static final float LEGEND_SCALE = 0.85F;
   private final PaintModeScreen.Panel pickerPanel = new PaintModeScreen.Panel("PaintPicker", false, null, 0);
   private final PaintModeScreen.Panel mapPanel = new PaintModeScreen.Panel("PaintMap", true, "fantastic.ui.paint.tab_skin", 96);
   private final PaintModeScreen.Panel legendPanel = new PaintModeScreen.Panel("PaintLegend", true, "fantastic.ui.paint.tab_hotkeys", 136);
   private final PaintModeScreen.Panel hidePanel = new PaintModeScreen.Panel("PaintHide", true, "fantastic.ui.paint.tab_parts", 200);
   private static final int RESET_W = 84;
   private static final int RESET_H = 12;
   private static final int MAPX = 4;
   private static final int MAPY = 17;
   private static final int FIG_S = 2;
   private static final int FIG_W = 32;
   private static final int FIG_H = 64;
   private static final int[][] FIG = new int[][]{
      {0, 8, 8, 8, 8, 4, 0}, {3, 44, 20, 4, 12, 0, 8}, {1, 20, 20, 8, 12, 4, 8}, {2, 36, 52, 4, 12, 12, 8}, {5, 4, 20, 4, 12, 4, 20}, {4, 20, 52, 4, 12, 8, 20}
   };
   private static final int[][] FIG_BACK = new int[][]{
      {0, 24, 8, 8, 8, 4, 0},
      {2, 44, 52, 4, 12, 0, 8},
      {1, 32, 20, 8, 12, 4, 8},
      {3, 52, 20, 4, 12, 12, 8},
      {4, 28, 52, 4, 12, 4, 20},
      {5, 12, 20, 4, 12, 8, 20}
   };
   private static final int FIG_GAP = 6;
   private static final int TAB_W = 14;
   private static final int TOP_MARGIN = 12;
   private static final String[] WIN_TIP = new String[]{
      "fantastic.ui.win_smaller", "fantastic.ui.win_larger", "fantastic.ui.win_reset", "fantastic.ui.win_dock"
   };
   private static final boolean NUDGE_PAD = false;
   private static final int NUDGE_X = 110;
   private static final int NUDGE_Y = 38;
   private static final int NB = 18;
   private static final int NG = 2;
   private static final String[] NUDGE_LABEL = new String[]{"▲", "▼", "◀", "▶", "▲▲", "▼▼", "↶", "↷"};
   private static final int ROW_H = 12;
   private static final int ROW_GAP = 2;
   private static final int LABEL_H = 10;
   private static final int ROT_Y = 38;
   private static final int SIZE_Y = nextLabelled(38, 2);
   private static Boolean sizeMiniLocal;
   private static final int RES_Y = nextLabelled(SIZE_Y, 1);
   private static final int SIDE_W = 68;
   private static final int PANEL_MARGIN = 8;
   static final int PANEL_W = 182;
   private static final int[] RES_SIZES = new int[]{64, 128, 256};
   private static final String[] RES_LABEL = new String[]{"SD", "HD", "UHD"};
   private static final int HINTS_Y = RES_Y + 12 + 8;
   private static final int LAYER_Y = HINTS_Y + 12 + 8;
   private static int rowsProp = Integer.MIN_VALUE;
   private static int rowsVariant = Integer.MIN_VALUE;
   private static int rowsCache = 64;
   private static final int HIDE_PAD = 4;
   private static final int HIDE_FIG_Y = 19;
   private static final int HIDE_MIN_W = 48;
   private static final int HIDE_FIGS_W = 70;
   private static final int GUIDE_ALPHA = 1711276032;
   private static final int GRASS_TINT = 1719450987;
   private static final int SLOT_H = 11;
   private static final int SLOT_W = 13;
   private static final int SLOT_GAP = 1;
   private static final int SAVE_W = 30;
   private static final long CONFIRM_MS = 3000L;
   private int confirmSlot = -1;
   private long confirmUntil;
   private static final ClientPaintState.Tool[] TOOL_KEYS = new ClientPaintState.Tool[]{
      ClientPaintState.Tool.BRUSH, ClientPaintState.Tool.SPRAY, ClientPaintState.Tool.ERASER, ClientPaintState.Tool.TEXCROP
   };
   private boolean hudWasHidden;
   private boolean weHidTheHud;

   private static float ui() {
      int gs = (int)Minecraft.m_91087_().m_91268_().m_85449_();
      if (gs < 1) {
         gs = 1;
      }

      double m = Math.max(3.5, Math.min(4.8, 4.0 + 0.5 * (double)(gs - 2)));
      return (float)(m / (double)gs);
   }

   private PaintModeScreen.Panel[] panels() {
      return new PaintModeScreen.Panel[]{this.pickerPanel, this.mapPanel, this.legendPanel, this.hidePanel};
   }

   private int resetX() {
      return this.f_96543_ / 2 - 42;
   }

   private int resetY() {
      return this.f_96544_ - 38;
   }

   private void drawResetLayout(GuiGraphics g, int mouseX, int mouseY) {
      int x = this.resetX();
      int y = this.resetY();
      boolean hot = mouseX >= x && mouseX < x + 84 && mouseY >= y && mouseY < y + 12;
      g.m_280509_(x, y, x + 84, y + 12, hot ? -13355460 : -14803166);
      HudPanel.frame(g, x, y, 84, 12, hot ? -8396640 : 1174405119);
      Gfx.centeredText(g, this.f_96547_, Component.m_237115_("fantastic.ui.paint.reset_layout"), x + 42, y + 2, hot ? -1 : -4604988);
   }

   private void resetLayout() {
      WindowLayouts.resetAll();

      for (PaintModeScreen.Panel p : this.panels()) {
         p.min = false;
         p.placed = false;
         p.scale = this.defaultScale(p);
         this.placeDefault(p);
      }

      FantasticSound.tap();
   }

   private static String tabText(PaintModeScreen.Panel p) {
      return p.tabLabel == null ? "" : Component.m_237115_(p.tabLabel).getString();
   }

   private int tabH(PaintModeScreen.Panel p) {
      return this.f_96547_.m_92895_(tabText(p)) + 10;
   }

   public PaintModeScreen() {
      super(Component.m_237115_("fantastic.ui.paint_mode"));
   }

   protected void m_7856_() {
      super.m_7856_();
      if (!this.weHidTheHud) {
         Minecraft mc = Minecraft.m_91087_();
         this.hudWasHidden = ClientShims.hideGui(mc);
         this.weHidTheHud = !this.hudWasHidden && ClientShims.setHudHidden(mc, true);
      }
   }

   private void placeDefault(PaintModeScreen.Panel p) {
      int gw = Minecraft.m_91087_().m_91268_().m_85445_();
      int gh = Minecraft.m_91087_().m_91268_().m_85446_();
      p.scale = this.defaultScale(p);
      float e = p.eff();
      int rightX = gw - 4 - this.rightColumnWidth();
      if (p == this.mapPanel) {
         p.offX = rightX;
         p.offY = 12;
      } else if (p == this.hidePanel) {
         p.offX = rightX;
         p.offY = 12 + (int)((float)(mapBottom() + 6) * ui() * this.defaultScale(this.mapPanel));
      } else if (p == this.legendPanel) {
         int[] wh = ControlHints.measureLegend(this.f_96547_, null, this.legendRows());
         p.offX = rightX;
         p.offY = Math.max(12, gh - 30 - (int)((float)(17 + wh[1] + 4) * e));
      } else {
         p.offX = 4;
         p.offY = 12;
      }

      p.placed = true;
   }

   private int rightColumnWidth() {
      float mapE = ui() * this.defaultScale(this.mapPanel);
      float hideE = ui() * this.defaultScale(this.hidePanel);
      float legE = ui() * this.defaultScale(this.legendPanel);
      int[] wh = ControlHints.measureLegend(this.f_96547_, null, this.legendRows());
      return Math.max((int)((float)(MAP_SIZE + 12) * mapE), Math.max((int)((float)(this.hideW() + 4) * hideE), (int)((float)(wh[0] + 10) * legE)));
   }

   private float fitScale() {
      int gw = Minecraft.m_91087_().m_91268_().m_85445_();
      float u = ui();
      float needed = 190.0F * u * 0.9F + 8.0F + this.rightColumnAtFullScale(u);
      float available = (float)Math.max(80, gw - 8);
      return needed <= available ? 1.0F : Math.max(0.5F, available / needed);
   }

   private float rightColumnAtFullScale(float u) {
      int[] wh = ControlHints.measureLegend(this.f_96547_, null, this.legendRows());
      return Math.max((float)(MAP_SIZE + 12) * u, Math.max((float)(this.hideW() + 4) * u, (float)(wh[0] + 10) * u * 0.85F));
   }

   private float defaultScale(PaintModeScreen.Panel p) {
      float fit = this.fitScale();
      return p == this.pickerPanel ? 0.9F * fit : (p == this.legendPanel ? 0.85F : 1.0F) * fit;
   }

   private void ensurePlaced() {
      int gw = Minecraft.m_91087_().m_91268_().m_85445_();
      int gh = Minecraft.m_91087_().m_91268_().m_85446_();

      for (PaintModeScreen.Panel p : this.panels()) {
         if (!p.placed) {
            this.placeDefault(p);
         }

         p.offX = Math.max(0, Math.min(p.offX, gw - 24));
         p.offY = Math.max(0, Math.min(p.offY, gh - 24));
      }
   }

   private void resetPanel(PaintModeScreen.Panel p) {
      p.min = false;
      this.placeDefault(p);
      p.save();
   }

   private void drawFrame(GuiGraphics g, PaintModeScreen.Panel p, int x1, int y1, int x2, int y2, double rawMx, double rawMy, String title) {
      int line = p.dragging ? -7363640 : -11116946;
      p.pushChrome(g);
      int fx1 = Math.round(p.cx((double)x1));
      int fx2 = Math.round(p.cx((double)x2));
      int fy1 = Math.round(p.cyTop((double)y1));
      int fy2 = Math.round(p.cy((double)y2));
      double mx = p.toChromeX(rawMx);
      double my = p.toChromeY(rawMy);
      g.m_280509_(fx1, fy1 + 13, fx2, fy2, -938470376);
      g.m_280509_(fx1, fy1, fx2, fy1 + 13, -1340860386);
      g.m_280509_(fx1, fy1, fx2, fy1 + 1, line);
      g.m_280509_(fx1, fy2 - 1, fx2, fy2, line);
      g.m_280509_(fx1, fy1, fx1 + 1, fy2, line);
      g.m_280509_(fx2 - 1, fy1, fx2, fy2, line);
      int n = p.canMin ? 4 : 3;
      if (title != null) {
         int room = fx2 - n * 11 - 6 - (fx1 + 4);
         String shown = this.f_96547_.m_92895_(title) > room ? this.f_96547_.m_92834_(title, Math.max(0, room)) : title;
         if (room > 6) {
            Gfx.text(g, this.f_96547_, shown, fx1 + 4, fy1 + 3, -4604988, false);
         }
      }

      String[] glyphs = p.canMin ? new String[]{"-", "+", "↺", "»"} : new String[]{"-", "+", "↺"};

      for (int i = 0; i < n; i++) {
         int bx = fx2 - (n - i) * 11 - 2;
         int by = fy1 + 1;
         boolean hover = mx >= (double)bx && mx < (double)(bx + 10) && my >= (double)by && my < (double)(by + 10);
         g.m_280509_(bx, by, bx + 10, by + 10, hover ? -1069528480 : -1876940750);
         Gfx.centeredText(g, this.f_96547_, glyphs[i], bx + 5, by + 1, hover ? -1 : -4671304);
      }

      p.pop(g);
   }

   private int panelButtonAt(PaintModeScreen.Panel p, int x2, int y1, double rawMx, double rawMy) {
      int n = p.canMin ? 4 : 3;
      double mx = p.toChromeX(rawMx);
      double my = p.toChromeY(rawMy);
      int fx2 = Math.round(p.cx((double)x2));
      int fy1 = Math.round(p.cyTop((double)y1));
      if (!(my < (double)(fy1 + 1)) && !(my >= (double)(fy1 + 1 + 10))) {
         for (int i = 0; i < n; i++) {
            int bx = fx2 - (n - i) * 11 - 2;
            if (mx >= (double)bx && mx < (double)(bx + 10)) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private boolean onBorder(PaintModeScreen.Panel p, int x1, int y1, int x2, int y2, double rawMx, double rawMy) {
      double mx = p.toChromeX(rawMx);
      double my = p.toChromeY(rawMy);
      int fx1 = Math.round(p.cx((double)x1));
      int fx2 = Math.round(p.cx((double)x2));
      int fy1 = Math.round(p.cyTop((double)y1));
      int fy2 = Math.round(p.cy((double)y2));
      if (!(mx < (double)(fx1 - 1)) && !(mx > (double)(fx2 + 1)) && !(my < (double)(fy1 - 1)) && !(my > (double)(fy2 + 1))) {
         return my <= (double)(fy1 + 13) ? true : mx <= (double)(fx1 + 3) || mx >= (double)(fx2 - 3) || my >= (double)(fy2 - 3);
      } else {
         return false;
      }
   }

   private boolean clickFrame(PaintModeScreen.Panel p, int x1, int y1, int x2, int y2, double lx, double ly) {
      int btn = this.panelButtonAt(p, x2, y1, lx, ly);
      if (btn >= 0) {
         switch (btn) {
            case 0:
               p.scale = Math.max(0.5F, p.scale - 0.1F);
               p.save();
               break;
            case 1:
               p.scale = Math.min(2.0F, p.scale + 0.1F);
               p.save();
               break;
            case 2:
               this.resetPanel(p);
               break;
            default:
               p.min = true;
               p.save();
         }

         return true;
      } else if (this.onBorder(p, x1, y1, x2, y2, lx, ly)) {
         p.dragging = true;
         return true;
      } else {
         return false;
      }
   }

   private void drawTab(GuiGraphics g, PaintModeScreen.Panel p) {
      int gw = Minecraft.m_91087_().m_91268_().m_85445_();
      int x = gw - 14;
      int h = this.tabH(p);
      g.m_280509_(x, p.tabY, gw, p.tabY + h, -1071634382);
      g.m_280509_(x, p.tabY, x + 1, p.tabY + h, -11116946);
      g.m_280509_(x, p.tabY, gw, p.tabY + 1, -11116946);
      g.m_280509_(x, p.tabY + h - 1, gw, p.tabY + h, -11116946);
      Gfx.push(g);
      Gfx.translate(g, (float)(x + 3), (float)(p.tabY + h - 5));
      Gfx.rotate(g, (float) (-Math.PI / 2));
      Gfx.text(g, this.f_96547_, tabText(p), 0, 0, -4604988, false);
      Gfx.pop(g);
   }

   private boolean tabClick(double rawX, double rawY) {
      int gw = Minecraft.m_91087_().m_91268_().m_85445_();

      for (PaintModeScreen.Panel p : new PaintModeScreen.Panel[]{this.mapPanel, this.legendPanel, this.hidePanel}) {
         if (p.min && rawX >= (double)(gw - 14) && rawY >= (double)p.tabY && rawY < (double)(p.tabY + this.tabH(p))) {
            p.min = false;
            p.save();
            return true;
         }
      }

      return false;
   }

   static int contrastFor(int sampled) {
      int c = sampled == 0 ? -1 : sampled;
      double luma = 0.2126 * (double)(c >> 16 & 0xFF) + 0.7152 * (double)(c >> 8 & 0xFF) + 0.0722 * (double)(c & 0xFF);
      return luma > 170.0 ? -14671840 : -1;
   }

   public boolean m_7043_() {
      return false;
   }

   public boolean isInGameUi() {
      return true;
   }

   public void m_280273_(GuiGraphics graphics) {
   }

   public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer p = mc.f_91074_;
      if (p != null && Services.PLATFORM.get(p, PaintAttachments.LOCKED) && FreeCam.active()) {
         this.ensurePlaced();
         double p1x = this.pickerPanel.lx((double)mouseX);
         double p1y = this.pickerPanel.ly((double)mouseY);
         double p2x = this.mapPanel.lx((double)mouseX);
         double p2y = this.mapPanel.ly((double)mouseY);
         double p3x = this.legendPanel.lx((double)mouseX);
         double p3y = this.legendPanel.ly((double)mouseY);
         double p4x = this.hidePanel.lx((double)mouseX);
         double p4y = this.hidePanel.ly((double)mouseY);
         boolean mapVisible = ChameleonArmor.wearsAny(p) && !this.mapPanel.min;
         boolean hideVisible = ChameleonArmor.wearsAny(p) && !this.hidePanel.min;
         int pickerBottom = Math.max(this.picker.bottom(), LAYER_Y + 14) + 4;
         int[] legWh = ControlHints.measureLegend(this.f_96547_, null, this.legendRows());
         Gizmos.setStroke(this.painting);
         RotateGizmo.updateHover((double)mouseX, (double)mouseY);
         MoveGizmo.updateHover((double)mouseX, (double)mouseY);
         PlacePreview.update();
         boolean overRing = RotateGizmo.ringAt((double)mouseX, (double)mouseY) >= 0 || MoveGizmo.arrowAt((double)mouseX, (double)mouseY) >= 0;
         boolean overMap = mapVisible && this.mapContains(p2x, p2y);
         boolean overPicker = this.picker.contains(p1x, p1y);
         boolean overNudge = this.nudgeContains(p1x, p1y);
         boolean overPanels = overPicker
            || overNudge
            || this.onBorder(this.pickerPanel, 4, 6, 182, pickerBottom, (double)mouseX, (double)mouseY)
            || mapVisible && this.onBorder(this.mapPanel, 0, 2, MAP_SIZE + 8, mapBottom(), (double)mouseX, (double)mouseY)
            || !this.legendPanel.min && this.onBorder(this.legendPanel, 0, 2, legWh[0] + 8, 17 + legWh[1] + 2, (double)mouseX, (double)mouseY)
            || hideVisible && this.hideContains(p4x, p4y);
         if (overMap) {
            BodyPaint.clearCursor();
            this.pipetteMap(mc, p2x, p2y);
         } else if (overPanels) {
            BodyPaint.clearCursor();
         } else if (BodyPaint.compute((double)mouseX, (double)mouseY, partialTick)) {
            BodyPaint.renderRing(g);
            this.drawBrushHint(g, mouseX, mouseY);
            this.pipette(mc, (double)mouseX, (double)mouseY, partialTick);
         } else if (overRing) {
            BodyPaint.clearCursor();
         } else {
            this.renderBlockCursor(g, (double)mouseX, (double)mouseY, partialTick);
            this.pipette(mc, (double)mouseX, (double)mouseY, partialTick);
         }

         RotateGizmo.render(g, partialTick);
         MoveGizmo.render(g, partialTick);
         this.drawFrame(
            g, this.pickerPanel, 4, 6, 182, pickerBottom, (double)mouseX, (double)mouseY, Component.m_237115_("fantastic.ui.paint.palette").getString()
         );
         this.pickerPanel.push(g);
         this.picker.render(g);
         this.renderNudge(g);
         this.renderRotateRow(g);
         this.renderSizeRow(g);
         this.renderResRow(g);
         this.renderHintsRow(g);
         this.renderLayerRow(g);
         this.pickerPanel.pop(g);
         if (ChameleonArmor.wearsAny(p)) {
            if (this.mapPanel.min) {
               this.drawTab(g, this.mapPanel);
            } else {
               this.drawFrame(g, this.mapPanel, 0, 2, MAP_SIZE + 8, mapBottom(), (double)mouseX, (double)mouseY, null);
               this.mapPanel.push(g);
               this.renderSkinMap(g, mc, p);
               this.renderSlotTabs(g, p);
               if (overMap) {
                  this.drawMapBrushCursor(g, p2x, p2y);
               }

               this.mapPanel.pop(g);
            }
         }

         if (ChameleonArmor.wearsAny(p)) {
            if (this.hidePanel.min) {
               this.drawTab(g, this.hidePanel);
            } else {
               this.drawFrame(g, this.hidePanel, 0, 2, this.hideW(), this.hideBottom(), (double)mouseX, (double)mouseY, null);
               this.hidePanel.push(g);
               this.drawHideContent(g, p);
               this.hidePanel.pop(g);
            }
         }

         if (this.legendPanel.min) {
            this.drawTab(g, this.legendPanel);
         } else {
            this.drawFrame(
               g,
               this.legendPanel,
               0,
               2,
               legWh[0] + 8,
               17 + legWh[1] + 2,
               (double)mouseX,
               (double)mouseY,
               Component.m_237115_("fantastic.ui.paint_hotkeys").getString()
            );
            this.legendPanel.push(g);
            ControlHints.legendBottomRight(g, this.f_96547_, 4 + legWh[0], 17 + legWh[1], null, this.legendRows());
            this.legendPanel.pop(g);
         }

         Component swatchName = this.picker.swatchTooltip(p1x, p1y);
         if (swatchName != null) {
            this.drawWinTooltip(g, swatchName, mouseX, mouseY);
         }

         this.drawResetLayout(g, mouseX, mouseY);
         PlacePreview.render(g, this.f_96547_, this.f_96543_, this.f_96544_);
         if (!overMap && this.painting && ClientPaintState.density() > 0 && BodyPaint.hasCursor()) {
            long now = System.currentTimeMillis();
            if (now - this.lastSparkleMs >= 200L) {
               BodyPaint.paint();
               this.lastSparkleMs = now;
            }
         }

         int wb = -1;
         if (!this.legendPanel.min) {
            wb = this.panelButtonAt(this.legendPanel, legWh[0] + 8, 2, (double)mouseX, (double)mouseY);
         }

         if (wb < 0 && hideVisible) {
            wb = this.panelButtonAt(this.hidePanel, this.hideW(), 2, (double)mouseX, (double)mouseY);
         }

         if (wb < 0 && mapVisible) {
            wb = this.panelButtonAt(this.mapPanel, MAP_SIZE + 8, 2, (double)mouseX, (double)mouseY);
         }

         if (wb < 0) {
            wb = this.panelButtonAt(this.pickerPanel, 182, 6, (double)mouseX, (double)mouseY);
         }

         if (wb >= 0 && wb < WIN_TIP.length) {
            this.drawWinTooltip(g, Component.m_237115_(WIN_TIP[wb]), mouseX, mouseY);
         }
      } else {
         FreeCam.disable();
         this.m_7379_();
      }
   }

   private void drawWinTooltip(GuiGraphics g, Component text, int mx, int my) {
      int w = this.f_96547_.m_92852_(text);
      int bx = mx + 10;
      int by = my - 2;
      if (bx + w + 4 > this.f_96543_) {
         bx = mx - w - 12;
      }

      if (by < 2) {
         by = 2;
      }

      g.m_280509_(bx - 3, by - 3, bx + w + 3, by + 11, -267381736);
      HudPanel.frame(g, bx - 3, by - 3, w + 6, 14, -11116946);
      Gfx.text(g, this.f_96547_, text, bx, by, -1513240);
   }

   private int[] nudgeRect(int dir) {
      int s = 20;
      int half = 9;

      return switch (dir) {
         case 0 -> new int[]{110 + s, 38, 18, 18};
         case 1 -> new int[]{110 + s, 38 + 2 * s, 18, 18};
         case 2 -> new int[]{110, 38 + s, 18, 18};
         case 3 -> new int[]{110 + 2 * s, 38 + s, 18, 18};
         case 4 -> new int[]{110 + s, 38 + s, 18, half - 1};
         case 5 -> new int[]{110 + s, 38 + s + half, 18, 18 - half};
         case 6 -> new int[]{110, 38, 18, 18};
         case 7 -> new int[]{110 + 2 * s, 38, 18, 18};
         default -> new int[]{0, 0, 0, 0};
      };
   }

   private void renderNudge(GuiGraphics g) {
   }

   private void dimLabel(GuiGraphics g, Component text, int y) {
      Gfx.text(g, this.f_96547_, text.m_6881_().m_130940_(ChatFormatting.GRAY), 110, y, -1, false);
   }

   private void centreLabel(GuiGraphics g, Component text, int padW, int y, int colour) {
      String str = text.getString();
      if (this.f_96547_.m_92895_(str) > padW - 4) {
         str = this.f_96547_.m_92834_(str, padW - 4);
      }

      Gfx.text(g, this.f_96547_, str, 110 + (padW - this.f_96547_.m_92895_(str)) / 2, y, colour, false);
   }

   private boolean nudgeContains(double lx, double ly) {
      return lx >= 109.0 && lx <= 179.0 && ly >= 27.0 && ly <= (double)(LAYER_Y + 12);
   }

   private boolean clickNudge(double lx, double ly) {
      return false;
   }

   private static int nextLabelled(int y, int rows) {
      return y + rows * 12 + (rows - 1) * 2 + 2 + 10 + 2;
   }

   private void renderRotateRow(GuiGraphics g) {
      int padW = 68;
      this.dimLabel(g, Component.m_237115_("fantastic.ui.paint.rotation"), 28);
      boolean on = Gizmos.visible();
      g.m_280509_(110, 38, 110 + padW, 50, on ? -13730510 : -13421773);
      this.centreLabel(g, Component.m_237115_(on ? "fantastic.ui.paint.rings_on" : "fantastic.ui.paint.rings_off"), padW, 40, on ? -1 : -4473925);
      int resetY = 52;
      g.m_280509_(110, resetY, 110 + padW, resetY + 12, -13421773);
      this.centreLabel(g, Component.m_237115_("fantastic.ui.paint.reset_rot"), padW, resetY + 2, -4473925);
   }

   private boolean rotateRowClick(double lx, double ly) {
      int padW = 68;
      if (lx < 110.0 || lx > (double)(110 + padW)) {
         return false;
      } else if (ly >= 38.0 && ly <= 50.0) {
         Gizmos.toggle();
         return true;
      } else {
         int resetY = 52;
         if (ly >= (double)resetY && ly <= (double)(resetY + 12)) {
            RotateGizmo.reset();
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean sizeMini() {
      if (sizeMiniLocal != null) {
         return sizeMiniLocal;
      } else {
         LocalPlayer p = Minecraft.m_91087_().f_91074_;
         return p == null || ArmorPaintHandler.scaleOf(p) < 0.75F;
      }
   }

   private void renderSizeRow(GuiGraphics g) {
      int padW = 68;
      this.dimLabel(g, Component.m_237115_("fantastic.ui.paint.size"), SIZE_Y - 10);
      boolean mini = this.sizeMini();
      int bw = (padW - 2) / 2;
      this.drawSizeBtn(g, 110, bw, !mini, "fantastic.ui.size_normal");
      this.drawSizeBtn(g, 110 + bw + 2, bw, mini, "fantastic.ui.size_mini");
   }

   private void drawSizeBtn(GuiGraphics g, int x, int w, boolean sel, String key) {
      g.m_280509_(x, SIZE_Y, x + w, SIZE_Y + 12, sel ? -13730510 : -13421773);
      Component label = Component.m_237115_(key);
      Gfx.text(g, this.f_96547_, label, x + (w - this.f_96547_.m_92852_(label)) / 2, SIZE_Y + 2, sel ? -1 : -4473925, false);
   }

   private static int maxRes() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return p == null ? 64 : ChameleonArmor.disguiseSize(p);
   }

   private static int activeRes() {
      int chosen = ClientPaintState.paintGrid();
      int max = maxRes();
      return chosen > 0 && chosen <= max ? chosen : max;
   }

   private void renderResRow(GuiGraphics g) {
      this.dimLabel(g, Component.m_237115_("fantastic.ui.paint.resolution"), RES_Y - 10);
      int bw = 21;
      int active = activeRes();
      int max = maxRes();

      for (int i = 0; i < 3; i++) {
         int x = 110 + i * (bw + 2);
         boolean locked = RES_SIZES[i] > max;
         boolean sel = RES_SIZES[i] == active;
         g.m_280509_(x, RES_Y, x + bw, RES_Y + 12, sel ? -13730510 : (locked ? -14277082 : -13421773));
         int col = sel ? -1 : (locked ? -9803158 : -4473925);
         Gfx.text(g, this.f_96547_, RES_LABEL[i], x + (bw - this.f_96547_.m_92895_(RES_LABEL[i])) / 2, RES_Y + 2, col, false);
      }
   }

   private boolean resRowClick(double lx, double ly) {
      if (!(ly < (double)RES_Y) && !(ly > (double)(RES_Y + 12)) && !(lx < 110.0) && !(lx > 178.0)) {
         int bw = 21;
         int i = Math.min(2, Math.max(0, (int)((lx - 110.0) / (double)(bw + 2))));
         int max = maxRes();
         if (RES_SIZES[i] > max) {
            Item mat = i == 1 ? Items.f_42791_ : Items.f_42686_;
            ClientShims.overlay(
               Component.m_237110_("fantastic.ui.res_locked", new Object[]{RES_LABEL[i], i + 1, new ItemStack(mat).m_41786_()})
                  .m_130940_(ChatFormatting.YELLOW),
               true
            );
            return true;
         } else {
            ClientPaintState.setPaintGrid(RES_SIZES[i] == max ? 0 : RES_SIZES[i]);
            return true;
         }
      } else {
         return false;
      }
   }

   private void renderHintsRow(GuiGraphics g) {
      int padW = 68;
      boolean on = WindowLayouts.flag("HotkeyCard", true);
      g.m_280509_(110, HINTS_Y, 110 + padW, HINTS_Y + 12, on ? -13730510 : -13421773);
      this.centreLabel(g, Component.m_237115_(on ? "fantastic.ui.paint.hotkeys_on" : "fantastic.ui.paint.hotkeys_off"), padW, HINTS_Y + 2, on ? -1 : -4473925);
   }

   private boolean hintsRowClick(double lx, double ly) {
      int padW = 68;
      if (!(ly < (double)HINTS_Y) && !(ly > (double)(HINTS_Y + 12)) && !(lx < 110.0) && !(lx > (double)(110 + padW))) {
         WindowLayouts.setFlag("HotkeyCard", !WindowLayouts.flag("HotkeyCard", true));
         return true;
      } else {
         return false;
      }
   }

   private void renderLayerRow(GuiGraphics g) {
      int padW = 68;
      boolean overlay = BodyPaint.captureLayer();
      g.m_280509_(110, LAYER_Y, 110 + padW, LAYER_Y + 12, overlay ? -13730510 : -13421773);
      this.centreLabel(
         g, Component.m_237115_(overlay ? "fantastic.ui.paint.layer_2nd" : "fantastic.ui.paint.layer_base"), padW, LAYER_Y + 2, overlay ? -1 : -4473925
      );
   }

   private boolean layerRowClick(double lx, double ly) {
      int padW = 68;
      if (!(ly < (double)LAYER_Y) && !(ly > (double)(LAYER_Y + 12)) && !(lx < 110.0) && !(lx > (double)(110 + padW))) {
         BodyPaint.setCaptureLayer(!BodyPaint.captureLayer());
         return true;
      } else {
         return false;
      }
   }

   private boolean sizeRowClick(double lx, double ly) {
      int padW = 68;
      if (!(ly < (double)SIZE_Y) && !(ly > (double)(SIZE_Y + 12)) && !(lx < 110.0) && !(lx > (double)(110 + padW))) {
         boolean mini = lx >= 110.0 + (double)padW / 2.0;
         sizeMiniLocal = mini;
         ClientNet.sendToServer(new SetSizePayload(mini));
         return true;
      } else {
         return false;
      }
   }

   private static float selfLockYaw() {
      LocalPlayer pl = Minecraft.m_91087_().f_91074_;
      return pl == null ? 0.0F : Mth.m_14177_(Services.PLATFORM.get(pl, PaintAttachments.LOCK_YAW));
   }

   private static double mapPxPerTexel() {
      int texN = BodyPaint.paintCanvasSize();
      return texN > 0 ? (double)MAP_SIZE / (double)texN : 1.6;
   }

   private static int usedRows() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null && BodyPaint.propMode()) {
         int idx = Services.PLATFORM.get(p, PaintAttachments.PROP);
         int variant = Services.PLATFORM.get(p, PaintAttachments.PROP_VARIANT);
         if (idx == rowsProp && variant == rowsVariant) {
            return rowsCache;
         } else {
            int rows;
            if (PropShapes.followsLook(idx)) {
               rows = guideTexHeight();
               if (woolGuideTexture() != null) {
                  rows = Math.max(rows, 64);
               }
            } else {
               rows = 0;

               for (int[] r : PropModels.faceRects(idx, variant)) {
                  rows = Math.max(rows, r[1] + r[3]);
               }
            }

            rowsProp = idx;
            rowsVariant = variant;
            rowsCache = Math.max(20, Math.min(64, rows));
            return rowsCache;
         }
      } else {
         return 64;
      }
   }

   private static int mapH() {
      return Math.max(1, (int)Math.round((double)(MAP_SIZE * usedRows()) / 64.0));
   }

   private void drawMapBrushCursor(GuiGraphics g, double mouseX, double mouseY) {
      double ppt = mapPxPerTexel();
      double rt = Math.max(1.0, BodyPaint.brushTexels() * ppt);
      int segs = 28;
      int col = contrastFor(BodyPaint.sampleTexel((mouseX - 4.0) / ppt, (mouseY - 17.0) / ppt));

      for (int i = 0; i < segs; i++) {
         double a = (double)i / (double)segs * Math.PI * 2.0;
         int sx = (int)Math.round(mouseX + Math.cos(a) * rt);
         int sy = (int)Math.round(mouseY + Math.sin(a) * rt);
         if (sx >= 4 && sx < 4 + MAP_SIZE && sy >= 17 && sy < 17 + mapH()) {
            g.m_280509_(sx, sy, sx + 1, sy + 1, col);
         }
      }
   }

   private int hideW() {
      return Math.max(78, 48);
   }

   private int hideFigX() {
      return (this.hideW() - 70) / 2;
   }

   private int hideBackFigX() {
      return this.hideFigX() + 32 + 6;
   }

   private int hideShowAllY() {
      return 86;
   }

   private int hideBottom() {
      return this.hideShowAllY() + 11 + 3;
   }

   private void drawHideContent(GuiGraphics g, LocalPlayer p) {
      int mask = ChameleonArmor.coverageMask(p);
      if (mask != 0) {
         BodyCanvas canvas = BodyPaint.localCanvas(p.m_20148_());
         if (canvas == null) {
            canvas = Services.PLATFORM.get(p, PaintAttachments.BODY_CANVAS);
         }

         int texN = canvas.size();
         int s = texN / 64;
         ResourceLocation tex = PlayerCanvasTextures.update(p.m_20148_(), canvas, mask);
         this.drawHideFigure(g, tex, texN, s, FIG, this.hideFigX());
         this.drawHideFigure(g, tex, texN, s, FIG_BACK, this.hideBackFigX());
         int say = this.hideShowAllY();
         boolean anyHidden = ClientPaintState.hiddenParts() != 0;
         g.m_280509_(4, say, this.hideW() - 4, say + 11, anyHidden ? -1069528480 : -1876940750);
         Component lbl = Component.m_237115_("fantastic.ui.show_all");
         Gfx.centeredText(g, this.f_96547_, lbl, this.hideW() / 2, say + 2, anyHidden ? -1 : -4604988);
         String tally = ClientPaintState.hiddenPartCount() + "/2";
         Gfx.text(g, this.f_96547_, tally, 4, say - 10, ClientPaintState.hiddenPartCount() >= 2 ? -34181 : -5592406);
      }
   }

   private void drawHideFigure(GuiGraphics g, ResourceLocation tex, int texN, int s, int[][] table, int fx) {
      for (int[] f : table) {
         int bit = f[0];
         int u = f[1];
         int v = f[2];
         int w = f[3];
         int h = f[4];
         int dx = fx + f[5] * 2;
         int dy = 19 + f[6] * 2;
         int dw = w * 2;
         int dh = h * 2;
         boolean hidden = (ClientPaintState.hiddenParts() & 1 << bit) != 0;
         Gfx.blit(g, DefaultPlayerSkin.m_118626_(), dx, dy, (float)u, (float)v, dw, dh, w, h, 64, 64, hidden ? 587202559 : -1996488705);
         Gfx.blit(g, tex, dx, dy, (float)(u * s), (float)(v * s), dw, dh, w * s, h * s, texN, texN, hidden ? 1442840575 : -1);
         if (hidden) {
            g.m_280509_(dx, dy, dx + dw, dy + dh, 1711276032);
         }

         HudPanel.frame(g, dx, dy, dw, dh, hidden ? -34197 : 1174405119);
      }
   }

   private boolean hideContentClick(double lx, double ly) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null && ChameleonArmor.wearsAny(p)) {
         int say = this.hideShowAllY();
         if (lx >= 4.0 && lx < (double)(this.hideW() - 4) && ly >= (double)say && ly < (double)(say + 11)) {
            ClientPaintState.clearHidden();
            return true;
         } else {
            return this.hideFigureClick(lx, ly, FIG, this.hideFigX()) || this.hideFigureClick(lx, ly, FIG_BACK, this.hideBackFigX());
         }
      } else {
         return false;
      }
   }

   private static void hidePart(BodyPart part) {
      if (ClientPaintState.togglePartHidden(part)) {
         FantasticSound.tap();
      } else {
         ClientShims.overlay(Component.m_237110_("fantastic.paint.hide_limit", new Object[]{2}), true);
      }
   }

   private boolean hideFigureClick(double lx, double ly, int[][] table, int fx) {
      for (int[] f : table) {
         int dx = fx + f[5] * 2;
         int dy = 19 + f[6] * 2;
         int dw = f[3] * 2;
         int dh = f[4] * 2;
         if (lx >= (double)dx && lx < (double)(dx + dw) && ly >= (double)dy && ly < (double)(dy + dh)) {
            hidePart(BodyPart.VALUES[f[0]]);
            return true;
         }
      }

      return false;
   }

   private boolean hideContains(double lx, double ly) {
      return lx >= 0.0 && lx < (double)this.hideW() && ly >= 2.0 && ly < (double)this.hideBottom();
   }

   private void renderSkinMap(GuiGraphics g, Minecraft mc, LocalPlayer p) {
      int mask = ChameleonArmor.coverageMask(p);
      if (mask != 0) {
         UUID uuid = p.m_20148_();
         boolean prop = BodyPaint.propMode();
         BodyCanvas canvas = prop ? BodyPaint.localPropCanvas(uuid) : BodyPaint.localCanvas(uuid);
         if (canvas == null) {
            canvas = Services.PLATFORM.get(p, prop ? PaintAttachments.PROP_CANVAS : PaintAttachments.BODY_CANVAS);
         }

         int texN = canvas.size();
         ResourceLocation tex = PlayerCanvasTextures.updateForMap(uuid, canvas, prop ? -1 : mask);
         Gfx.text(
            g,
            this.f_96547_,
            Component.m_237115_(prop ? "fantastic.ui.paint.prop_map" : "fantastic.ui.paint.skin_map").m_130940_(ChatFormatting.GRAY),
            4,
            6,
            -1
         );
         g.m_280509_(4, 17, 4 + MAP_SIZE, 17 + mapH(), -1);
         ResourceLocation guide = prop ? propGuideTexture() : DefaultPlayerSkin.m_118626_();
         if (guide != null) {
            int gh = guideTexHeight();
            Gfx.blit(g, guide, 4, 17, 0.0F, 0.0F, MAP_SIZE, MAP_SIZE * gh / 64, 64, gh, 64, gh, 1728053247);
            ResourceLocation wool = woolGuideTexture();
            if (wool != null) {
               Gfx.blit(g, wool, 4, 17 + MAP_SIZE * 32 / 64, 0.0F, 0.0F, MAP_SIZE, MAP_SIZE * 32 / 64, 64, 32, 64, 32, 1728053247);
            }
         } else if (prop) {
            this.renderBlockPropGuide(g, p, texN);
         }

         int srcRows = Math.max(1, (int)Math.round((double)(usedRows() * texN) / 64.0));
         Gfx.blit(g, tex, 4, 17, 0.0F, 0.0F, MAP_SIZE, mapH(), texN, srcRows, texN, texN, -1);
      }
   }

   private void renderBlockPropGuide(GuiGraphics g, LocalPlayer p, int texN) {
      int idx = Services.PLATFORM.get(p, PaintAttachments.PROP);
      if (idx >= 0) {
         String key = PropShapes.of(idx).key();
         int variant = Services.PLATFORM.get(p, PaintAttachments.PROP_VARIANT);
         double px = (double)MAP_SIZE / (double)texN;
         List<int[]> rects = PropModels.faceRects(idx, variant);
         List<int[][]> cutouts = PropModels.cutoutFacePairs(idx, variant);
         PropShapes.Box[] boxes = PropShapes.followsLook(idx) ? null : PropShapes.boxesOf(idx, variant);
         Gfx.push(g);
         Gfx.translate(g, 4.0F, 17.0F);
         Gfx.scale(g, (float)px, (float)px);

         for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            PaintModeScreen.GuideTile tile = isCutout(cutouts, r) ? cutoutGuideTile(key) : null;
            if (tile == null) {
               tile = blockGuideTile(key, i % 6);
            }

            if (tile != null) {
               float[] src = boxes != null && i / 6 < boxes.length ? guideSource(boxes[i / 6], i % 6) : new float[]{0.0F, 0.0F, 16.0F, 16.0F};
               Gfx.blit(
                  g, tile.tex(), r[0], r[1], src[0], src[1], r[2], r[3], Math.max(1, Math.round(src[2])), Math.max(1, Math.round(src[3])), 16, 16, tile.tint()
               );
            }
         }

         Gfx.pop(g);

         for (int[] rx : rects) {
            int x = 4 + (int)Math.round((double)rx[0] * px);
            int y = 17 + (int)Math.round((double)rx[1] * px);
            int w = Math.max(1, 4 + (int)Math.round((double)(rx[0] + rx[2]) * px) - x);
            int h = Math.max(1, 17 + (int)Math.round((double)(rx[1] + rx[3]) * px) - y);
            HudPanel.frame(g, x, y, w, h, 587202559);
         }
      }
   }

   private static float[] guideSource(PropShapes.Box box, int face) {
      float x1 = Math.min(box.x1(), box.x2());
      float x2 = Math.max(box.x1(), box.x2());
      float y1 = Math.min(box.y1(), box.y2());
      float y2 = Math.max(box.y1(), box.y2());
      float z1 = Math.min(box.z1(), box.z2());
      float z2 = Math.max(box.z1(), box.z2());

      return switch (face) {
         case 0, 1 -> new float[]{x1, z1, x2 - x1, z2 - z1};
         case 2, 4 -> new float[]{z1, 16.0F - y2, z2 - z1, y2 - y1};
         default -> new float[]{x1, 16.0F - y2, x2 - x1, y2 - y1};
      };
   }

   private static boolean isCutout(List<int[][]> cutouts, int[] r) {
      for (int[][] pair : cutouts) {
         for (int[] face : pair) {
            if (face[0] == r[0] && face[1] == r[1] && face[2] == r[2] && face[3] == r[3]) {
               return true;
            }
         }
      }

      return false;
   }

   private static PaintModeScreen.GuideTile cutoutGuideTile(String key) {
      return "flower_pot".equals(key) ? new PaintModeScreen.GuideTile(blockTex("blue_orchid"), 1728053247) : null;
   }

   private static PaintModeScreen.GuideTile blockGuideTile(String key, int face) {
      boolean top = face == 0;
      boolean bottom = face == 1;
      if ("block".equals(key)) {
         return top
            ? new PaintModeScreen.GuideTile(blockTex("grass_block_top"), 1719450987)
            : new PaintModeScreen.GuideTile(blockTex(bottom ? "dirt" : "grass_block_side"), 1728053247);
      } else if ("cake".equals(key)) {
         return new PaintModeScreen.GuideTile(blockTex(top ? "cake_top" : (bottom ? "cake_bottom" : "cake_side")), 1728053247);
      } else {
         String path = switch (key) {
            case "slab", "stairs" -> "smooth_stone";
            case "carpet" -> "white_wool";
            case "trapdoor" -> "oak_trapdoor";
            case "fence_post" -> "oak_planks";
            case "wall" -> "cobblestone";
            case "pane" -> "glass";
            case "anvil" -> "anvil";
            case "lantern" -> "lantern";
            case "flower_pot" -> "flower_pot";
            default -> null;
         };
         return path == null ? null : new PaintModeScreen.GuideTile(blockTex(path), 1728053247);
      }
   }

   private static ResourceLocation blockTex(String path) {
      return new ResourceLocation("textures/block/" + path + ".png");
   }

   private static String mobPropKey() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p == null) {
         return null;
      } else {
         int idx = Services.PLATFORM.get(p, PaintAttachments.PROP);
         return idx >= 0 && PropShapes.followsLook(idx) ? PropShapes.of(idx).key() : null;
      }
   }

   private static ResourceLocation propGuideTexture() {
      String var1 = String.valueOf(mobPropKey());

      String path = switch (var1) {
         case "cow" -> "cow/cow_temperate";
         case "pig" -> "pig/pig_temperate";
         case "sheep" -> "sheep/sheep";
         case "chicken" -> "chicken/chicken_temperate";
         case "wolf" -> "wolf/wolf";
         case "panda" -> "panda/panda";
         case "creeper" -> "creeper/creeper";
         case "enderman" -> "enderman/enderman";
         default -> null;
      };
      return path == null ? null : new ResourceLocation("textures/entity/" + path + ".png");
   }

   private static int guideTexHeight() {
      String var0 = String.valueOf(mobPropKey());

      return switch (var0) {
         case "sheep", "chicken", "wolf", "creeper", "enderman" -> 32;
         default -> 64;
      };
   }

   private static ResourceLocation woolGuideTexture() {
      return "sheep".equals(mobPropKey()) ? new ResourceLocation("textures/entity/sheep/sheep_wool.png") : null;
   }

   private boolean mapContains(double mx, double my) {
      return mx >= 4.0 && mx < (double)(4 + MAP_SIZE) && my >= 17.0 && my < (double)(17 + mapH());
   }

   private static boolean slotTabsShown() {
      if (BodyPaint.propMode()) {
         return true;
      } else {
         LocalPlayer p = Minecraft.m_91087_().f_91074_;
         return p != null && ChameleonArmor.coverageMask(p) != 0;
      }
   }

   private static int tabsY() {
      return 17 + mapH() + 3;
   }

   private static int mapBottom() {
      return 17 + mapH() + 4 + (slotTabsShown() ? 15 : 0);
   }

   private boolean confirmLive(int slot) {
      return this.confirmSlot == slot && System.currentTimeMillis() < this.confirmUntil;
   }

   private void renderSlotTabs(GuiGraphics g, LocalPlayer p) {
      if (slotTabsShown()) {
         int y = tabsY();
         int active = BodyPaint.activeSlot();
         int allowed = PaintSlots.count();

         for (int i = 0; i < 5; i++) {
            int x = 4 + i * 14;
            boolean locked = i >= allowed;
            boolean filled = !locked && BodyPaint.slotFilled(p, i);
            int bg = locked ? 1075847200 : (i == active ? -870147585 : (filled ? -12959152 : -13948117));
            g.m_280509_(x, y, x + 13, y + 11, bg);
            HudPanel.frame(g, x, y, 13, 11, i == active ? -1 : 1090519039);
            String label = String.valueOf(i + 1);
            int tw = this.f_96547_.m_92895_(label);
            Gfx.text(g, this.f_96547_, label, x + (13 - tw) / 2, y + 2, locked ? -9803158 : (i == active ? -1 : -4473925), false);
         }

         int sx = 4 + MAP_SIZE - 30;
         boolean unsaved = BodyPaint.slotUnsaved();
         boolean asking = this.confirmLive(this.confirmSlot) && this.confirmSlot >= 0;
         g.m_280509_(sx, y, sx + 30, y + 11, asking ? -7718354 : (unsaved ? -13730510 : -13421773));
         HudPanel.frame(g, sx, y, 30, 11, 1090519039);
         Component label = Component.m_237115_(asking ? "fantastic.ui.paint.slot_sure" : "fantastic.ui.paint.slot_save");
         String str = label.getString();
         if (this.f_96547_.m_92895_(str) > 28) {
            str = this.f_96547_.m_92834_(str, 28);
         }

         Gfx.text(g, this.f_96547_, str, sx + (30 - this.f_96547_.m_92895_(str)) / 2, y + 2, !asking && !unsaved ? -4473925 : -1, false);
      }
   }

   private boolean slotTabsClick(double lx, double ly) {
      if (!slotTabsShown()) {
         return false;
      } else {
         int y = tabsY();
         if (!(ly < (double)y) && !(ly > (double)(y + 11)) && !(lx < 4.0) && !(lx >= (double)(4 + MAP_SIZE))) {
            LocalPlayer p = Minecraft.m_91087_().f_91074_;
            int sx = 4 + MAP_SIZE - 30;
            if (lx >= (double)sx && lx <= (double)(sx + 30)) {
               this.requestSave(p, BodyPaint.activeSlot());
               return true;
            } else {
               for (int i = 0; i < PaintSlots.count(); i++) {
                  int x = 4 + i * 14;
                  if (lx >= (double)x && lx <= (double)(x + 13)) {
                     if (shiftDown()) {
                        this.requestSave(p, i);
                     } else {
                        BodyPaint.selectSlot(i);
                        this.confirmSlot = -1;
                        FantasticSound.tap();
                     }

                     return true;
                  }
               }

               return true;
            }
         } else {
            return false;
         }
      }
   }

   private static boolean ctrlDown() {
      Window w = Minecraft.m_91087_().m_91268_();
      return InputConstants.m_84830_(w.m_85439_(), 341) || InputConstants.m_84830_(w.m_85439_(), 345);
   }

   private static boolean shiftDown() {
      Window w = Minecraft.m_91087_().m_91268_();
      return InputConstants.m_84830_(w.m_85439_(), 340) || InputConstants.m_84830_(w.m_85439_(), 344);
   }

   private void requestSave(LocalPlayer p, int slot) {
      boolean occupied = p != null && BodyPaint.slotFilled(p, slot) && (slot != 0 || BodyPaint.propMode());
      if (occupied && !this.confirmLive(slot)) {
         this.confirmSlot = slot;
         this.confirmUntil = System.currentTimeMillis() + 3000L;
         ClientShims.overlay(Component.m_237110_("fantastic.ui.paint.slot_overwrite", new Object[]{slot + 1}).m_130940_(ChatFormatting.GOLD), true);
      } else {
         this.confirmSlot = -1;
         BodyPaint.saveSlot(slot);
         FantasticSound.tap();
         ClientShims.overlay(Component.m_237110_("fantastic.ui.paint.slot_saved", new Object[]{slot + 1}), true);
      }
   }

   private void paintMap(double mx, double my) {
      double cx = Math.max(4.0, Math.min((double)(4 + MAP_SIZE - 1), mx));
      double cy = Math.max(17.0, Math.min((double)(17 + mapH() - 1), my));
      double mapScale = mapPxPerTexel();
      BodyPaint.paintTexel((cx - 4.0) / mapScale, (cy - 17.0) / mapScale);
   }

   private static void markColorUsed() {
      if (ClientPaintState.tool() == ClientPaintState.Tool.BRUSH) {
         ClientPaintState.addUsed(ClientPaintState.currentColor());
      }
   }

   private List<String[]> legendRows() {
      List<String[]> rows = new ArrayList<>();
      rows.add(new String[]{"LClick", Component.m_237115_("fantastic.hint.paint").getString()});
      rows.add(new String[]{"1-4", Component.m_237115_("fantastic.hint.tools").getString()});
      rows.add(new String[]{"H", Component.m_237115_("fantastic.hint.handles").getString()});
      rows.add(new String[]{"Scroll", Component.m_237115_("fantastic.hint.brush_size").getString()});
      rows.add(new String[]{"RClick", Component.m_237115_("fantastic.hint.hide_limb").getString()});
      rows.add(new String[]{"RClick", Component.m_237115_("fantastic.hint.pick_color").getString()});
      rows.add(new String[]{"Wheel drag", Component.m_237115_("fantastic.hint.rotate_cam").getString()});
      rows.add(new String[]{"Ctrl+Scroll", Component.m_237115_("fantastic.hint.zoom").getString()});
      rows.add(new String[]{"Space", Component.m_237115_("fantastic.hint.pipette").getString()});
      rows.add(new String[]{"Ctrl+Z", Component.m_237115_("fantastic.hint.undo").getString()});
      rows.add(new String[]{keyName(LockControls.poseWheelKey, "R"), Component.m_237115_("fantastic.hint.re_pose").getString()});
      rows.add(new String[]{keyName(LockControls.paintKey, "F"), Component.m_237115_("fantastic.hint.toggle_cam").getString()});
      rows.add(new String[]{"Esc", Component.m_237115_("fantastic.hint.leave").getString()});
      return rows;
   }

   private static String keyName(KeyMapping key, String fallback) {
      return key == null ? fallback : key.m_90863_().getString();
   }

   private PaintModeScreen.Basis basis(Minecraft mc, LocalPlayer p, float pt) {
      int gw = mc.m_91268_().m_85445_();
      int gh = mc.m_91268_().m_85446_();
      Vec3 cam = FreeCam.active() ? FreeCam.cameraPosition(p, pt) : p.m_20299_(pt);
      Vec3 fc = p.m_20252_(pt).m_82541_();
      Vec3 cross = fc.m_82537_(new Vec3(0.0, 1.0, 0.0));
      Vec3 rc;
      if (cross.m_82556_() < 1.0E-6) {
         double yaw = Math.toRadians((double)p.m_5675_(pt));
         rc = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
      } else {
         rc = cross.m_82541_();
      }

      Vec3 uc = rc.m_82537_(fc).m_82541_();
      double tanV = Math.tan(Math.toRadians((double)((Integer)mc.f_91066_.m_231837_().m_231551_()).intValue()) / 2.0);
      return new PaintModeScreen.Basis(cam, fc, rc, uc, tanV * (double)gw / (double)gh, tanV, gw, gh);
   }

   private int pipetteEntity(Minecraft mc, LocalPlayer p, float pt, double mx, double my, BlockHitResult blockHit) {
      PaintModeScreen.Basis b = this.basis(mc, p, pt);
      double ndcX = 2.0 * mx / (double)b.gw - 1.0;
      double ndcY = 1.0 - 2.0 * my / (double)b.gh;
      Vec3 dir = b.fc.m_82549_(b.rc.m_82490_(ndcX * b.tanH)).m_82549_(b.uc.m_82490_(ndcY * b.tanV)).m_82541_();
      double reach = blockHit.m_6662_() == Type.BLOCK ? blockHit.m_82450_().m_82554_(b.cam) : 8.0;
      Vec3 end = b.cam.m_82549_(dir.m_82490_(reach));
      AABB ray = new AABB(b.cam, end).m_82400_(0.5);
      double best = Double.MAX_VALUE;
      int color = 0;

      for (Entity e : mc.f_91073_.m_6249_(p, ray, EntityColorSampler::supported)) {
         Optional<Vec3> at = e.m_20191_().m_82400_(0.02).m_82371_(b.cam, end);
         if (!at.isEmpty()) {
            double d = at.get().m_82554_(b.cam);
            if (!(d >= best)) {
               int c = EntityColorSampler.sample(e, at.get());
               if (c != 0) {
                  best = d;
                  color = c;
               }
            }
         }
      }

      return color;
   }

   private BlockHitResult rayThroughMouse(Minecraft mc, LocalPlayer p, PaintModeScreen.Basis b, double mx, double my) {
      Vec3 origin = b.cam;
      Vec3 dir = null;
      CameraProjection proj = CameraProjection.of(1.0F);
      if (proj != null) {
         dir = proj.rayDirection(mx, my);
         if (dir != null) {
            origin = ClientShims.cameraPos();
         }
      }

      if (dir == null) {
         double ndcX = 2.0 * mx / (double)b.gw - 1.0;
         double ndcY = 1.0 - 2.0 * my / (double)b.gh;
         dir = b.fc.m_82549_(b.rc.m_82490_(ndcX * b.tanH)).m_82549_(b.uc.m_82490_(ndcY * b.tanV)).m_82541_();
      }

      Vec3 end = origin.m_82549_(dir.m_82490_(8.0));
      return mc.f_91073_.m_45547_(new ClipContext(origin, end, Block.OUTLINE, Fluid.NONE, p));
   }

   private void drawBrushHint(GuiGraphics g, int mouseX, int mouseY) {
      String left = Component.m_237115_("fantastic.ui.paint.hint_left").getString();
      String right = Component.m_237115_("fantastic.ui.paint.hint_right").getString();
      int w = Math.max(this.f_96547_.m_92895_(left), this.f_96547_.m_92895_(right)) + 8;
      int x = Math.min(mouseX + 12, this.f_96543_ - w - 2);
      int y = Math.min(mouseY + 10, this.f_96544_ - 26);
      g.m_280509_(x, y, x + w, y + 22, -1610612736);
      HudPanel.frame(g, x, y, w, 22, 1174405119);
      Gfx.text(g, this.f_96547_, left, x + 4, y + 3, -8396640, false);
      Gfx.text(g, this.f_96547_, right, x + 4, y + 12, -4604988, false);
   }

   private void renderBlockCursor(GuiGraphics g, double mx, double my, float pt) {
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer p = mc.f_91074_;
      if (p != null && mc.f_91073_ != null) {
         PaintModeScreen.Basis b = this.basis(mc, p, pt);
         BlockHitResult hit = this.rayThroughMouse(mc, p, b, mx, my);
         if (hit.m_6662_() == Type.BLOCK) {
            Direction face = hit.m_82434_();
            Vec3 n = new Vec3((double)face.m_122429_(), (double)face.m_122430_(), (double)face.m_122431_());
            Vec3 ref = Math.abs(n.f_82480_) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
            Vec3 t = n.m_82537_(ref).m_82541_();
            Vec3 bt = n.m_82537_(t).m_82541_();
            Vec3 baseP = hit.m_82450_().m_82549_(n.m_82490_(0.01));
            double r = ClientPaintState.brushRadius();
            int segs = 48;
            int col = contrastFor(BlockColorSampler.sampleAt(mc.f_91073_, hit.m_82425_(), mc.f_91073_.m_8055_(hit.m_82425_()), face, hit.m_82450_()));

            for (int i = 0; i < segs; i++) {
               double a = (double)i / (double)segs * Math.PI * 2.0;
               Vec3 wp = baseP.m_82549_(t.m_82490_(Math.cos(a) * r)).m_82549_(bt.m_82490_(Math.sin(a) * r));
               double[] s = this.project(wp, b);
               if (s != null) {
                  g.m_280509_((int)s[0] - 1, (int)s[1] - 1, (int)s[0] + 1, (int)s[1] + 1, col);
               }
            }
         }
      }
   }

   private double[] project(Vec3 world, PaintModeScreen.Basis b) {
      Vec3 rel = world.m_82546_(b.cam);
      double vz = rel.m_82526_(b.fc);
      if (vz <= 0.01) {
         return null;
      } else {
         double ndcX = rel.m_82526_(b.rc) / vz / b.tanH;
         double ndcY = rel.m_82526_(b.uc) / vz / b.tanV;
         return new double[]{(double)Math.round((0.5 + 0.5 * ndcX) * (double)b.gw), (double)Math.round((0.5 - 0.5 * ndcY) * (double)b.gh)};
      }
   }

   private void pipette(Minecraft mc, double mx, double my, float pt) {
      LocalPlayer p = mc.f_91074_;
      if (p != null && mc.f_91073_ != null) {
         boolean held = InputConstants.m_84830_(mc.m_91268_().m_85439_(), 32);
         ClientPaintState.Tool tool = ClientPaintState.tool();
         boolean textureTool = ClientPaintState.isTextureTool(tool);
         if (held && !textureTool && BodyPaint.hasCursor()) {
            int c = BodyPaint.sampleAtCursor();
            if (c != 0) {
               ClientPaintState.setCurrentColor(c);
            }
         } else if (held) {
            BlockHitResult hit = this.rayThroughMouse(mc, p, this.basis(mc, p, pt), mx, my);
            int entityColor = this.pipetteEntity(mc, p, pt, mx, my, hit);
            if (entityColor != 0) {
               ClientPaintState.setCurrentColor(entityColor);
            } else if (hit.m_6662_() == Type.BLOCK) {
               BlockPos bp = hit.m_82425_();
               BlockState st = mc.f_91073_.m_8055_(bp);
               if (tool == ClientPaintState.Tool.TEXCROP) {
                  ClientPaintState.setTexturePattern(BlockColorSampler.sample8x8Crop(mc.f_91073_, bp, st, hit.m_82434_(), hit.m_82450_()));
               } else {
                  ClientPaintState.setCurrentColor(BlockColorSampler.sampleAt(mc.f_91073_, bp, st, hit.m_82434_(), hit.m_82450_()));
               }
            }
         }

         if (this.pipetteHeld && !held && !textureTool) {
            ClientPaintState.addSampled(ClientPaintState.currentColor());
         }

         this.pipetteHeld = held;
      }
   }

   private boolean pickColorAt(double mx, double my) {
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer p = mc.f_91074_;
      if (p != null && mc.f_91073_ != null) {
         float pt = mc.m_91296_();
         BlockHitResult hit = this.rayThroughMouse(mc, p, this.basis(mc, p, pt), mx, my);
         int color = this.pipetteEntity(mc, p, pt, mx, my, hit);
         if (color == 0 && hit.m_6662_() == Type.BLOCK) {
            BlockPos bp = hit.m_82425_();
            color = BlockColorSampler.sampleAt(mc.f_91073_, bp, mc.f_91073_.m_8055_(bp), hit.m_82434_(), hit.m_82450_());
         }

         if (color == 0) {
            return false;
         } else {
            ClientPaintState.addSampled(color);
            ClientShims.overlay(Component.m_237115_("fantastic.paint.picked_color"), true);
            return true;
         }
      } else {
         return false;
      }
   }

   private void pipetteMap(Minecraft mc, double lmx, double lmy) {
      boolean held = InputConstants.m_84830_(mc.m_91268_().m_85439_(), 32);
      if (held && !ClientPaintState.isTextureTool(ClientPaintState.tool())) {
         double ppt = mapPxPerTexel();
         int c = BodyPaint.sampleTexel((lmx - 4.0) / ppt, (lmy - 17.0) / ppt);
         if (c != 0) {
            ClientPaintState.setCurrentColor(c);
         }
      }

      if (this.pipetteHeld && !held) {
         ClientPaintState.addSampled(ClientPaintState.currentColor());
      }

      this.pipetteHeld = held;
   }

   public boolean m_6375_(double mouseX, double mouseY, int button) {
      MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);
      boolean doubleClick = false;
      this.ensurePlaced();
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      boolean mapShown = p != null && ChameleonArmor.wearsAny(p) && !this.mapPanel.min;
      if (event.button() == 0 && this.tabClick(event.x(), event.y())) {
         return true;
      } else if (event.button() == 0
         && event.x() >= (double)this.resetX()
         && event.x() < (double)(this.resetX() + 84)
         && event.y() >= (double)this.resetY()
         && event.y() < (double)(this.resetY() + 12)) {
         this.resetLayout();
         return true;
      } else {
         double h4x = this.hidePanel.lx(event.x());
         double h4y = this.hidePanel.ly(event.y());
         boolean hideShown = p != null && ChameleonArmor.wearsAny(p) && !this.hidePanel.min;
         if (event.button() == 0 && hideShown && this.clickFrame(this.hidePanel, 0, 2, this.hideW(), this.hideBottom(), event.x(), event.y())) {
            return true;
         } else if (event.button() == 0 && hideShown && this.hideContentClick(h4x, h4y)) {
            return true;
         } else {
            if (event.button() == 1 && BodyPaint.hasCursor()) {
               BodyPart part = BodyPaint.cursorPart();
               if (part != null) {
                  hidePart(part);
                  return true;
               }
            }

            double p1x = this.pickerPanel.lx(event.x());
            double p1y = this.pickerPanel.ly(event.y());
            double p2x = this.mapPanel.lx(event.x());
            double p2y = this.mapPanel.ly(event.y());
            double p3x = this.legendPanel.lx(event.x());
            double p3y = this.legendPanel.ly(event.y());
            int pickerBottom = Math.max(this.picker.bottom(), LAYER_Y + 14) + 4;
            if (event.button() == 0) {
               if (!this.legendPanel.min) {
                  int[] wh = ControlHints.measureLegend(this.f_96547_, null, this.legendRows());
                  if (this.clickFrame(this.legendPanel, 0, 2, wh[0] + 8, 17 + wh[1] + 2, event.x(), event.y())) {
                     return true;
                  }
               }

               if (mapShown && this.clickFrame(this.mapPanel, 0, 2, MAP_SIZE + 8, mapBottom(), event.x(), event.y())) {
                  return true;
               }

               if (this.clickFrame(this.pickerPanel, 4, 6, 182, pickerBottom, event.x(), event.y())) {
                  return true;
               }
            }

            if (this.picker.hexEditing() && !this.picker.contains(p1x, p1y)) {
               this.picker.hexCommit();
            }

            if (this.picker.contains(p1x, p1y)) {
               this.picker.onPress(p1x, p1y);
               return true;
            } else if (event.button() == 0 && this.clickNudge(p1x, p1y)) {
               return true;
            } else if (event.button() == 0 && this.rotateRowClick(p1x, p1y)) {
               return true;
            } else if (event.button() == 0 && this.sizeRowClick(p1x, p1y)) {
               return true;
            } else if (event.button() == 0 && this.resRowClick(p1x, p1y)) {
               return true;
            } else if (event.button() == 0 && this.hintsRowClick(p1x, p1y)) {
               return true;
            } else if (event.button() == 0 && this.layerRowClick(p1x, p1y)) {
               return true;
            } else if (event.button() == 0 && mapShown && this.slotTabsClick(p2x, p2y)) {
               return true;
            } else if (event.button() == 0 && mapShown && this.mapContains(p2x, p2y)) {
               BodyPaint.beginStroke();
               markColorUsed();
               this.paintMap(p2x, p2y);
               this.mapPainting = true;
               return true;
            } else if (event.button() == 0 && !BodyPaint.hasCursor() && MoveGizmo.press(event.x(), event.y())) {
               return true;
            } else if (event.button() == 0 && !BodyPaint.hasCursor() && RotateGizmo.press(event.x(), event.y())) {
               return true;
            } else if (event.button() == 0 && BodyPaint.hasCursor()) {
               BodyPaint.beginStroke();
               markColorUsed();
               BodyPaint.paint();
               this.painting = true;
               return true;
            } else {
               return event.button() == 1 ? this.pickColorAt(event.x(), event.y()) : super.m_6375_(event.x(), event.y(), event.button());
            }
         }
      }
   }

   public boolean m_7979_(double mouseX, double mouseY, int button, double dragX, double dragY) {
      MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);

      for (PaintModeScreen.Panel p : this.panels()) {
         if (p.dragging) {
            p.offX = p.offX + (int)Math.round(dragX);
            p.offY = p.offY + (int)Math.round(dragY);
            return true;
         }
      }

      if (event.button() == 2) {
         FreeCam.orbit(dragX, dragY);
         return true;
      } else if (this.picker.isInteracting()) {
         this.picker.onDrag(this.pickerPanel.lx(event.x()), this.pickerPanel.ly(event.y()));
         return true;
      } else if (MoveGizmo.dragging() && event.button() == 0) {
         MoveGizmo.drag(event.x(), event.y());
         return true;
      } else if (RotateGizmo.dragging() && event.button() == 0) {
         RotateGizmo.drag(event.x(), event.y(), 0.0F);
         return true;
      } else if (this.mapPainting && event.button() == 0) {
         this.paintMap(this.mapPanel.lx(event.x()), this.mapPanel.ly(event.y()));
         return true;
      } else if (this.painting && event.button() == 0 && BodyPaint.hasCursor()) {
         BodyPaint.paint();
         return true;
      } else {
         return super.m_7979_(event.x(), event.y(), event.button(), dragX, dragY);
      }
   }

   public boolean m_6348_(double mouseX, double mouseY, int button) {
      MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);

      for (PaintModeScreen.Panel p : this.panels()) {
         if (p.dragging) {
            p.dragging = false;
            p.save();
            return true;
         }
      }

      this.picker.onRelease(this.pickerPanel.lx(event.x()), this.pickerPanel.ly(event.y()));
      this.painting = false;
      this.mapPainting = false;
      MoveGizmo.release();
      RotateGizmo.release();
      return super.m_6348_(event.x(), event.y(), event.button());
   }

   public boolean m_6050_(double mx, double my, double scrollY) {
      double scrollX = 0.0;
      if (scrollY == 0.0) {
         return true;
      } else if (this.picker.overVanilla(this.pickerPanel.lx(mx), this.pickerPanel.ly(my))) {
         this.picker.scrollVanilla(scrollY);
         return true;
      } else if (ctrlDown()) {
         FreeCam.zoom(scrollY > 0.0 ? 1 : -1);
         return true;
      } else {
         ClientPaintState.adjustBrush(scrollY > 0.0 ? 1.0 : -1.0);
         return true;
      }
   }

   public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
      KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
      int k = event.key();
      if (this.picker.hexEditing()) {
         if (k == 259) {
            this.picker.hexBackspace();
         } else if (k == 257 || k == 335) {
            this.picker.hexCommit();
         } else if (k == 256) {
            this.picker.hexCancel();
         } else if (k >= 48 && k <= 57 || k >= 65 && k <= 70) {
            this.picker.hexAppend((char)k);
         }

         return true;
      } else if ((k == 90 || k == 87) && event.hasControlDown()) {
         BodyPaint.undo();
         return true;
      } else if (k >= 49 && k <= 52) {
         ClientPaintState.setTool(TOOL_KEYS[k - 49]);
         FantasticSound.tap();
         return true;
      } else if (k == 72) {
         Gizmos.toggle();
         FantasticSound.tap();
         return true;
      } else if (LockControls.poseWheelKey != null && k == ClientAccessors.boundKey(LockControls.poseWheelKey).m_84873_()) {
         Minecraft.m_91087_().m_91152_(new PoseWheelScreen(k, true));
         return true;
      } else if (LockControls.paintKey != null && k == ClientAccessors.boundKey(LockControls.paintKey).m_84873_()) {
         this.m_7379_();
         return true;
      } else if (k == 256) {
         FreeCam.disable();
         this.m_7379_();
         return true;
      } else {
         return super.m_7933_(event.key(), event.scancode(), event.modifiers());
      }
   }

   public void m_7379_() {
      super.m_7379_();
   }

   public void m_7861_() {
      this.picker.close();
      WorldBrush.exitPaintMode();
      if (this.weHidTheHud) {
         ClientShims.setHudHidden(Minecraft.m_91087_(), this.hudWasHidden);
         this.weHidTheHud = false;
      }

      super.m_7861_();
   }

   private static record Basis(Vec3 cam, Vec3 fc, Vec3 rc, Vec3 uc, double tanH, double tanV, int gw, int gh) {
   }

   private static record GuideTile(ResourceLocation tex, int tint) {
   }

   private final class Panel {
      final String key;
      final boolean canMin;
      final String tabLabel;
      final int tabY;
      float scale = 1.0F;
      int offX;
      int offY;
      boolean min;
      boolean dragging;
      boolean placed;

      Panel(String key, boolean canMin, String tabLabel, int tabY) {
         this.key = key;
         this.canMin = canMin;
         this.tabLabel = tabLabel;
         this.tabY = tabY;
         if (WindowLayouts.has(key)) {
            WindowLayouts.Layout l = WindowLayouts.get(key);
            this.scale = Math.max(0.5F, Math.min(2.0F, l.scale));
            this.offX = l.offX;
            this.offY = l.offY;
            this.min = canMin && l.min;
            this.placed = true;
         }
      }

      float eff() {
         return PaintModeScreen.ui() * this.scale;
      }

      float chromeShift() {
         return 13.0F * (1.0F - this.scale) * PaintModeScreen.ui();
      }

      double lx(double raw) {
         return (raw - (double)this.offX) / (double)this.eff();
      }

      double ly(double raw) {
         return (raw - (double)this.offY - (double)this.chromeShift()) / (double)this.eff();
      }

      float cx(double local) {
         return (float)local * this.scale;
      }

      float cy(double local) {
         return (float)local * this.scale + 13.0F * (1.0F - this.scale);
      }

      float cyTop(double local) {
         return (float)local * this.scale;
      }

      double toChromeX(double raw) {
         return (raw - (double)this.offX) / (double)PaintModeScreen.ui();
      }

      double toChromeY(double raw) {
         return (raw - (double)this.offY) / (double)PaintModeScreen.ui();
      }

      void pushChrome(GuiGraphics g) {
         Gfx.push(g);
         Gfx.translate(g, (float)this.offX, (float)this.offY);
         Gfx.scale(g, PaintModeScreen.ui(), PaintModeScreen.ui());
      }

      void push(GuiGraphics g) {
         Gfx.push(g);
         Gfx.translate(g, (float)this.offX, (float)this.offY + this.chromeShift());
         Gfx.scale(g, this.eff(), this.eff());
      }

      void pop(GuiGraphics g) {
         Gfx.pop(g);
      }

      void save() {
         WindowLayouts.put(this.key, this.scale, this.offX, this.offY, this.min);
      }
   }
}
