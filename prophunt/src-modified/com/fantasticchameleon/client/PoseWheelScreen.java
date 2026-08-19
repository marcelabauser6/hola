package com.fantasticchameleon.client;

import com.fantasticchameleon.donor.DonorFeatures;
import com.fantasticchameleon.network.PosePayload;
import com.fantasticchameleon.network.SetPropPayload;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.ClientNet;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PoseDefs;
import com.fantasticchameleon.pose.PropShapes;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;

public final class PoseWheelScreen extends Screen {
   /** La rueda de poses tampoco pausa la partida: en una ronda, pausar congela a todo el mundo. */
   @Override
   public boolean m_6913_() {
      return false;
   }

   private static final int AVATAR_HALF_W = 18;
   private static final int AVATAR_TOP = 32;
   private static final int AVATAR_BOTTOM = 16;
   private static final int AVATAR_SIZE = 24;
   private static final float VIEW_YAW = 30.0F;
   private static final float VIEW_PITCH = 30.0F;
   private static final float LIE_SIDE_EXTRA_YAW = 90.0F;
   private static final float AVATAR_Y_OFFSET = 0.1F;
   private static final int KIND_POSE = 0;
   private static final int KIND_SHAPES = 1;
   private static final int KIND_FORM = 2;
   private static final int KIND_FACING = 3;
   private final int holdKey;
   private final boolean returnToPaint;
   private int selected = -1;
   private int selectedKind = -1;
   private static int page;
   private static boolean lastWasShapes;
   private static int lastPosePage;
   private static int lastShapePage;
   private static int slideDir;
   private static long slideStart;
   private static final long SLIDE_MS = 160L;
   private static final int CARD_W = 54;
   private static final int CARD_H = 76;
   private static final int CARD_MARGIN = 34;
   private final int[] cardX = new int[2];
   private final int[] cardY = new int[2];
   private final int[] cardPage = new int[]{-1, -1};
   private int hoveredCard = -1;

   private static void rememberPage() {
      int idx = pageIndex();
      switch (kindOf(idx)) {
         case 0:
            lastWasShapes = false;
            lastPosePage = idx;
            break;
         case 1:
            lastWasShapes = true;
            lastShapePage = shapePageOf(idx);
      }
   }

   private static int restoredPage() {
      if (propOnly()) {
         return 0;
      } else {
         int lead = leadCount();
         if (lastWasShapes) {
            return lead + Math.floorMod(lastShapePage, PropShapes.SHAPE_PAGES.length);
         } else {
            return lead == 0 ? 0 : Math.floorMod(lastPosePage, lead);
         }
      }
   }

   private static int wornProp() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return p == null ? -1 : Services.PLATFORM.get(p, PaintAttachments.PROP);
   }

   private static int wornVariant() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return p == null ? 0 : Services.PLATFORM.get(p, PaintAttachments.PROP_VARIANT);
   }

   private static boolean inProp() {
      return wornProp() >= 0;
   }

   private static int[] propLead() {
      int prop = wornProp();
      boolean forms = PropShapes.formCount(prop) > 1;
      boolean facings = PropShapes.facingCount(prop) > 1;
      if (forms && facings) {
         return new int[]{2, 3};
      } else if (forms) {
         return new int[]{2};
      } else {
         return facings ? new int[]{3} : new int[0];
      }
   }

   private static int leadCount() {
      return inProp() ? propLead().length : PoseDefs.pages().length;
   }

   private static boolean propOnly() {
      return inProp() && propLead().length > 0;
   }

   private static int pageCount() {
      int lead = leadCount();
      return propOnly() ? lead : lead + PropShapes.SHAPE_PAGES.length;
   }

   private static int pageIndex() {
      return Math.floorMod(page, pageCount());
   }

   private static int kind() {
      return kindOf(pageIndex());
   }

   private static int kindOf(int p) {
      int lead = leadCount();
      if (p >= lead) {
         return 1;
      } else {
         return inProp() ? propLead()[p] : 0;
      }
   }

   private static int shapePage() {
      return shapePageOf(pageIndex());
   }

   private static int shapePageOf(int page) {
      int p = page - leadCount();
      return p < 0 ? -1 : Math.floorMod(p, PropShapes.SHAPE_PAGES.length);
   }

   private int[] slices() {
      if (kind() == 1) {
         return PropShapes.SHAPE_PAGES[shapePage()].props();
      } else {
         int n = switch (kind()) {
            case 2 -> PropShapes.formCount(wornProp());
            case 3 -> PropShapes.facingCount(wornProp());
            default -> -1;
         };
         if (n < 0) {
            return PoseDefs.pages()[pageIndex()].poses();
         } else {
            int[] out = new int[n];
            int i = 0;

            while (i < n) {
               out[i] = i++;
            }

            return out;
         }
      }
   }

   private Component label(int value) {
      return switch (kind()) {
         case 1 -> Component.m_237115_(PropShapes.nameKey(value));
         case 2 -> Component.m_237115_(PropShapes.formKey(wornProp(), value));
         case 3 -> Component.m_237115_(PropShapes.facingKey(value));
         default -> Component.m_237115_(PoseDefs.label(value));
      };
   }

   private int placementFor(int kind, int value) {
      int prop = wornProp();
      int worn = wornVariant();
      int form = kind == 2 ? value : PropShapes.formOf(prop, worn);
      int facing = kind == 3 ? value : PropShapes.facingOf(prop, worn);
      return PropShapes.pack(form, facing);
   }

   private Component pageTitle() {
      return this.pageTitle(pageIndex());
   }

   private Component pageTitle(int idx) {
      return (Component)(switch (kindOf(idx)) {
         case 1 -> Component.m_237110_(
         "fantastic.pose.page.props", new Object[]{Component.m_237115_("fantastic.pose.page.props." + PropShapes.SHAPE_PAGES[shapePageOf(idx)].key())}
      );
         case 2 -> Component.m_237110_("fantastic.pose.page.form", new Object[]{Component.m_237115_(PropShapes.nameKey(wornProp()))});
         case 3 -> Component.m_237110_("fantastic.pose.page.facing", new Object[]{Component.m_237115_(PropShapes.nameKey(wornProp()))});
         default -> {
            PoseDefs.Page p = PoseDefs.pages()[idx];
            Component name = Component.m_237115_("fantastic.pose.page." + p.key());
            yield p.donor() && !DonorFeatures.unlocked(Minecraft.m_91087_().f_91074_ == null ? null : Minecraft.m_91087_().f_91074_.m_20148_())
               ? name.m_6881_().m_130940_(ChatFormatting.GOLD)
               : name;
         }
      });
   }

   public PoseWheelScreen(int holdKey) {
      this(holdKey, false);
   }

   public PoseWheelScreen(int holdKey, boolean returnToPaint) {
      super(Component.m_237115_("fantastic.ui.pose_screen"));
      this.holdKey = holdKey;
      this.returnToPaint = returnToPaint;
      page = restoredPage();
   }

   public boolean m_7043_() {
      return false;
   }

   public void m_86600_() {
      if (!InputConstants.m_84830_(Minecraft.m_91087_().m_91268_().m_85439_(), this.holdKey)) {
         this.apply();
      }
   }

   public void m_280273_(GuiGraphics g) {
      g.m_280509_(0, 0, this.f_96543_, this.f_96544_, 1711276032);
   }

   public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
      super.m_88315_(g, mouseX, mouseY, partial);
      int cx = this.f_96543_ / 2;
      int cy = this.f_96544_ / 2;
      int radius = Math.min(this.f_96543_, this.f_96544_) / 4;
      this.layoutCards(cx, cy, radius, mouseX, mouseY);
      this.updateSelection(mouseX, mouseY, cx, cy, radius);
      LocalPlayer player = Minecraft.m_91087_().f_91074_;
      int[] slices = this.slices();
      int n = slices.length;
      if (n == 0) {
         Gfx.centeredText(g, this.f_96547_, Component.m_237115_("fantastic.pose.page.empty"), cx, cy - 12, -14020);
         Gfx.centeredText(g, this.f_96547_, Component.m_237115_("fantastic.pose.page.empty_sub"), cx, cy + 2, -4604988);
         this.renderPageStrip(g, cx, cy - radius - 32 - 14);
         this.renderNeighbourCards(g, player);
      } else {
         for (int i = 0; i < n; i++) {
            int pose = slices[i];
            double ang = Math.toRadians(-90.0 + (double)i * (360.0 / (double)n));
            int ox = cx + (int)Math.round((double)radius * Math.cos(ang));
            int oy = cy + (int)Math.round((double)radius * Math.sin(ang));
            boolean sel = i == this.selected;
            int x0 = ox - 18;
            int y0 = oy - 32;
            int x1 = ox + 18;
            int y1 = oy + 16;
            g.m_280509_(x0 - 2, y0 - 2, x1 + 2, y1 + 12, sel ? -870147585 : 1713381408);
            this.drawPreview(g, player, kind(), pose, x0, y0, x1, y1);
            Gfx.centeredText(g, this.f_96547_, this.label(pose), ox, y1 + 1, sel ? -1 : -2236963);
         }

         if (this.selected >= 0) {
            Gfx.centeredText(g, this.f_96547_, this.label(slices[this.selected]), cx, cy - 4, -1);
         } else {
            Gfx.centeredText(g, this.f_96547_, Component.m_237115_(propOnly() ? "fantastic.pose.leave_prop" : "fantastic.pose.cancel"), cx, cy - 4, -40864);
         }

         this.renderPageStrip(g, cx, cy - radius - 32 - 14);
         this.renderNeighbourCards(g, player);
      }
   }

   private static void beginSlide(int dir) {
      slideDir = dir;
      slideStart = System.currentTimeMillis();
   }

   private static float slideProgress() {
      if (slideDir == 0) {
         return 1.0F;
      } else {
         long dt = System.currentTimeMillis() - slideStart;
         if (dt >= 160L) {
            slideDir = 0;
            return 1.0F;
         } else {
            float t = (float)dt / 160.0F;
            return 1.0F - (1.0F - t) * (1.0F - t);
         }
      }
   }

   private void renderPageStrip(GuiGraphics g, int cx, int y) {
      int n = pageCount();
      int cur = pageIndex();
      Component curName = this.pageTitle(cur);
      int curW = this.f_96547_.m_92852_(curName);
      float k = slideProgress();
      int shift = Math.round((float)slideDir * (1.0F - k) * ((float)curW / 2.0F + 40.0F));
      Gfx.centeredText(g, this.f_96547_, curName, cx + shift, y, -1);
      if (n > 1) {
         int gap = curW / 2 + 12;
         Gfx.text(g, this.f_96547_, "<", cx + shift - gap - 4, y, -4604988, false);
         Gfx.text(g, this.f_96547_, ">", cx + shift + gap, y, -4604988, false);
      }
   }

   private void drawPreview(GuiGraphics g, LocalPlayer player, int k, int value, int x0, int y0, int x1, int y1) {
      if (player != null) {
         if (k == 1) {
            PropAvatarPreview.set(value, 0);
         } else if (k != 2 && k != 3) {
            PoseAvatarPreview.set(value);
         } else {
            PropAvatarPreview.set(wornProp(), this.placementFor(k, value));
         }

         try {
            this.renderPosedAvatar(g, player, k == 0 ? value : 0, x0, y0, x1, y1);
         } finally {
            PoseAvatarPreview.clear();
            PropAvatarPreview.clear();
         }
      }
   }

   private static int heroOf(int pageIdx) {
      int k = kindOf(pageIdx);
      if (k == 1) {
         PropShapes.ShapePage page = PropShapes.SHAPE_PAGES[shapePageOf(pageIdx)];
         String pageKey = page.key();

         int pick = switch (pageKey) {
            case "blocks" -> 2;
            case "barriers" -> 0;
            case "decor" -> 1;
            case "monsters" -> 0;
            default -> 0;
         };
         int[] props = page.props();
         return props[Math.min(pick, props.length - 1)];
      } else if (k == 0) {
         int[] poses = PoseDefs.pages()[pageIdx].poses();
         return poses.length == 0 ? 0 : poses[Math.min(1, poses.length - 1)];
      } else {
         return 0;
      }
   }

   private void layoutCards(int cx, int cy, int radius, int mouseX, int mouseY) {
      int n = pageCount();
      this.cardPage[0] = -1;
      this.cardPage[1] = -1;
      this.hoveredCard = -1;
      if (n > 1) {
         int cur = pageIndex();
         int y = cy - 38;
         this.cardX[0] = cx - radius - 34 - 54;
         this.cardX[1] = cx + radius + 34;
         this.cardY[0] = y;
         this.cardY[1] = y;
         this.cardPage[0] = Math.floorMod(cur - 1, n);
         this.cardPage[1] = Math.floorMod(cur + 1, n);
         this.hoveredCard = this.cardAt((double)mouseX, (double)mouseY);
      }
   }

   private int cardAt(double mx, double my) {
      for (int i = 0; i < 2; i++) {
         if (this.cardPage[i] >= 0
            && mx >= (double)this.cardX[i]
            && mx < (double)(this.cardX[i] + 54)
            && my >= (double)this.cardY[i]
            && my < (double)(this.cardY[i] + 76)) {
            return i;
         }
      }

      return -1;
   }

   private void renderNeighbourCards(GuiGraphics g, LocalPlayer player) {
      for (int i = 0; i < 2; i++) {
         if (this.cardPage[i] >= 0) {
            this.drawCard(g, player, i);
         }
      }
   }

   private void drawCard(GuiGraphics g, LocalPlayer player, int slot) {
      int x = this.cardX[slot];
      int y = this.cardY[slot];
      int pageIdx = this.cardPage[slot];
      boolean hot = this.hoveredCard == slot;
      g.m_280509_(x, y, x + 54, y + 76, hot ? -870147585 : 1713381408);
      HudPanel.frame(g, x, y, 54, 76, hot ? -1 : 1090519039);
      this.drawPreview(g, player, kindOf(pageIdx), heroOf(pageIdx), x + 6, y + 6, x + 54 - 6, y + 76 - 22);
      Gfx.centeredText(g, this.f_96547_, this.pageTitle(pageIdx), x + 27, y + 76 - 12, hot ? -1 : -4208683);
      Gfx.centeredText(g, this.f_96547_, slot == 0 ? "<" : ">", x + 27, y + 2, hot ? -1 : -4604988);
   }

   private void turnPage(int dir) {
      page = Math.floorMod(pageIndex() + dir, pageCount());
      rememberPage();
      beginSlide(dir);
      this.selected = -1;
      FantasticSound.tap();
   }

   public boolean m_6375_(double mouseX, double mouseY, int button) {
      MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);
      boolean doubleClick = false;
      if (event.button() == 0) {
         int card = this.cardAt(event.x(), event.y());
         if (card >= 0) {
            this.turnPage(card == 0 ? -1 : 1);
            return true;
         }
      }

      return super.m_6375_(event.x(), event.y(), event.button());
   }

   private void renderPosedAvatar(GuiGraphics g, LocalPlayer player, int poseIndex, int x0, int y0, int x1, int y1) {
      float yaw = 30.0F + (poseIndex == 6 ? 90.0F : 0.0F);
      int cx = (x0 + x1) / 2;
      int cy = (y0 + y1) / 2;
      int size = Math.max(1, Math.min(x1 - x0, y1 - y0));
      float pitchRad = (float) (Math.PI / 6);
      Quaternionf cameraOverride = new Quaternionf().rotateX(pitchRad);
      Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(cameraOverride);
      InventoryScreen.m_280432_(g, cx, cy + size / 2, (int)((float)size * 0.42F), rotation, cameraOverride, player);
   }

   public boolean m_6050_(double mx, double my, double scrollY) {
      double scrollX = 0.0;
      if (scrollY != 0.0) {
         this.turnPage(scrollY > 0.0 ? -1 : 1);
         return true;
      } else {
         return super.m_6050_(mx, my, scrollY);
      }
   }

   private void updateSelection(int mx, int my, int cx, int cy, int radius) {
      if (this.hoveredCard >= 0) {
         this.selected = -1;
      } else {
         double dx = (double)(mx - cx);
         double dy = (double)(my - cy);
         double dist = Math.sqrt(dx * dx + dy * dy);
         int n = this.slices().length;
         if (n == 0) {
            this.selected = -1;
         } else if (dist < (double)radius * 0.45) {
            this.selected = -1;
         } else {
            double a = (Math.toDegrees(Math.atan2(dy, dx)) + 90.0 + 360.0) % 360.0;
            this.selected = (int)Math.round(a / (360.0 / (double)n)) % n;
            this.selectedKind = kind();
         }
      }
   }

   private void apply() {
      Minecraft mc = Minecraft.m_91087_();
      if (mc.f_91074_ == null) {
         this.m_7379_();
      } else if (this.hoveredCard >= 0) {
         if (this.returnToPaint) {
            this.backToPaint();
         } else {
            this.m_7379_();
         }
      } else if (this.selected < 0) {
         if (propOnly() && this.returnToPaint) {
            ClientNet.sendToServer(new SetPropPayload(-1, 0));
            page = 0;
            this.backToPaint();
         } else if (this.returnToPaint) {
            this.backToPaint();
         } else {
            LockControls.exit(mc);
            this.m_7379_();
         }
      } else if (this.selectedKind >= 0 && this.selectedKind != kind()) {
         this.m_7379_();
      } else {
         int value = this.slices()[Math.min(this.selected, this.slices().length - 1)];
         int k = kind();
         if (k == 1 || k == 2 || k == 3) {
            int prop = k == 1 ? value : wornProp();
            int variant = k == 1 ? 0 : this.placementFor(k, value);
            ClientNet.sendToServer(new SetPropPayload(prop, variant));
            if (k == 1) {
               rememberPage();
               page = 0;
            }

            if (this.returnToPaint) {
               this.backToPaint();
            } else {
               this.m_7379_();
            }
         } else if (PoseDefs.isDonorPose(value) && !DonorFeatures.unlocked(mc.f_91074_.m_20148_())) {
            ClientShims.overlay(Component.m_237115_("fantastic.pose.locked").m_130940_(ChatFormatting.GOLD), true);
            this.m_7379_();
         } else {
            Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSING, true);
            Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSE, value);
            ClientNet.sendToServer(new PosePayload(true, value));
            if (this.returnToPaint) {
               this.backToPaint();
            } else {
               this.m_7379_();
            }
         }
      }
   }

   private void backToPaint() {
      WorldBrush.setPaintMode(true);
      Minecraft.m_91087_().m_91152_(new PaintModeScreen());
   }

   public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
      KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
      if (event.key() == 256) {
         if (this.returnToPaint) {
            this.backToPaint();
         } else {
            LockControls.exit(Minecraft.m_91087_());
            this.m_7379_();
         }

         return true;
      } else {
         return super.m_7933_(event.key(), event.scancode(), event.modifiers());
      }
   }
}
