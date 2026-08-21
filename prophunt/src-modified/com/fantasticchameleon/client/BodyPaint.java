package com.fantasticchameleon.client;

import com.fantasticchameleon.item.ArmorPresets;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.network.CanvasDeltaPayload;
import com.fantasticchameleon.network.SetCanvasPayload;
import com.fantasticchameleon.network.SetPropCanvasPayload;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.BodyPart;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.paint.SkinRegions;
import com.fantasticchameleon.platform.ClientNet;
import com.fantasticchameleon.platform.Services;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.client.model.geom.ModelPart.Vertex;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;

public final class BodyPaint {
   private static final int SYNC_PERIOD = 20;
   private static final List<BodyPaint.CapturedFace> poolA = new ArrayList<>();
   private static final List<BodyPaint.CapturedFace> poolB = new ArrayList<>();
   private static List<BodyPaint.CapturedFace> captured = poolA;
   private static int capturedCount;
   private static List<BodyPaint.CapturedFace> captureBuffer;
   private static int fillCount;
   private static boolean hasCursor;
   private static double[] cursorUv;
   private static double[] husFlat;
   private static int[] faceRect;
   private static boolean[] paintable;
   private static int paintableSize;
   private static int captureSize;
   private static final double[][] scrScreen = new double[4][2];
   private static final double[] scrHs = new double[9];
   private static final double[] scrHsInv = new double[9];
   private static final double[] scrSq = new double[2];
   private static final double[] scrHu = new double[9];
   private static final double[] scrHuInv = new double[9];
   private static final double[] scrRing = new double[2];
   private static boolean dirty;
   private static int syncTimer;
   private static int[] live;
   private static final Deque<int[]> undoStack = new ArrayDeque<>();
   private static final int UNDO_MAX = 24;
   private static final Random SPRAY_RNG = new Random();
   private static int liveVersion;
   private static Boolean liveProp;
   private static boolean captureLayer;
   private static String lastPropKey;
   private static String lastPropGeometry;
   private static int[] sentSnapshot;
   private static long lastSignedNag;

   private BodyPaint() {
   }

   public static int sessionSize() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return p != null ? ChameleonArmor.disguiseSize(p) : 64;
   }

   public static int paintCanvasSize() {
      return propMode() ? 64 : sessionSize();
   }

   private static double texelsPerBlock() {
      return (double)paintCanvasSize() / 4.0;
   }

   public static int gridStep() {
      int canvas = paintCanvasSize();
      int chosen = ClientPaintState.paintGrid();
      return chosen > 0 && chosen < canvas ? Math.max(1, canvas / chosen) : 1;
   }

   private static int cellOrigin(int t, int step) {
      return step == 1 ? t : Math.floorDiv(t, step) * step;
   }

   public static boolean hasCursor() {
      return hasCursor;
   }

   public static BodyPart cursorPart() {
      if (hasCursor && cursorUv != null) {
         int sz = sessionSize();
         BodyPart part = SkinRegions.partAtScaled((int)Math.floor(cursorUv[0]), (int)Math.floor(cursorUv[1]), sz);
         if (part == BodyPart.LEFT_LEG_LOWER) {
            return BodyPart.LEFT_LEG;
         } else {
            return part == BodyPart.RIGHT_LEG_LOWER ? BodyPart.RIGHT_LEG : part;
         }
      } else {
         return null;
      }
   }

   public static void beginStroke() {
      ensureLive();
      if (live != null) {
         undoStack.push((int[])live.clone());

         while (undoStack.size() > 24) {
            undoStack.removeLast();
         }
      }
   }

   public static boolean isPainting() {
      return live != null;
   }

   public static void replaceLive(int[] px) {
      if (live != null) {
         live = (int[])px.clone();
         liveVersion++;
         dirty = true;
      }
   }

   public static void undo() {
      if (!undoStack.isEmpty()) {
         live = undoStack.pop();
         liveVersion++;
         dirty = true;
      }
   }

   private static void ensureLive() {
      ensureLive(Minecraft.m_91087_().f_91074_);
   }

   public static boolean propMode() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return p != null && Services.PLATFORM.get(p, PaintAttachments.PROP) >= 0;
   }

   private static void ensureLive(LocalPlayer p) {
      if (p != null) {
         boolean prop = propMode();
         if (live != null && liveProp != null && liveProp != prop) {
            live = null;
         }

         if (live == null) {
            live = (int[])Services.PLATFORM.get(p, prop ? PaintAttachments.PROP_CANVAS : PaintAttachments.BODY_CANVAS).pixels().clone();
            liveProp = prop;
            liveVersion++;
            sentSnapshot = null;
         }
      }
   }

   public static BodyCanvas localCanvas(UUID uuid) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return live != null && !Boolean.TRUE.equals(liveProp) && p != null && p.m_20148_().equals(uuid) ? new BodyCanvas(live) : null;
   }

   public static BodyCanvas localPropCanvas(UUID uuid) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return live != null && Boolean.TRUE.equals(liveProp) && p != null && p.m_20148_().equals(uuid) ? new BodyCanvas(live) : null;
   }

   public static int paintVersion(UUID uuid) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return live != null && p != null && p.m_20148_().equals(uuid) ? liveVersion : 0;
   }

   public static int propPaintVersion(UUID uuid) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return live != null && Boolean.TRUE.equals(liveProp) && p != null && p.m_20148_().equals(uuid) ? liveVersion : 0;
   }

   public static boolean shouldCapture(UUID uuid) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return WorldBrush.paintMode() && p != null && p.m_20148_().equals(uuid);
   }

   public static boolean captureLayer() {
      return captureLayer;
   }

   public static void setCaptureLayer(boolean v) {
      captureLayer = v;
   }

   public static void beginCapture() {
      captureBuffer = captured == poolA ? poolB : poolA;
      fillCount = 0;
   }

   public static void captureProp(ModelPart part, PoseStack pose, int canvasSize) {
      capturePart(part, pose, canvasSize);
   }

   public static void capturePart(ModelPart part, PoseStack pose, int canvasSize) {
      if (captureBuffer != null) {
         captureSize = canvasSize;
         part.m_171309_(pose, (cubePose, path, cubeIndex, cube) -> {
            for (Polygon polygon : cube.f_104341_) {
               Vertex[] vs = polygon.f_104359_;
               if (vs.length == 4) {
                  BodyPaint.CapturedFace face;
                  if (fillCount < captureBuffer.size()) {
                     face = captureBuffer.get(fillCount);
                  } else {
                     face = new BodyPaint.CapturedFace();
                     captureBuffer.add(face);
                  }

                  fillCount++;

                  for (int i = 0; i < 4; i++) {
                     Vector3f local = vs[i].f_104371_;
                     cubePose.m_252922_().transformPosition(local.x() / 16.0F, local.y() / 16.0F, local.z() / 16.0F, face.corners[i]);
                     face.uv[i][0] = (double)(vs[i].f_104372_ * (float)canvasSize);
                     face.uv[i][1] = (double)(vs[i].f_104373_ * (float)canvasSize);
                  }
               }
            }
         });
      }
   }

   public static void endCapture() {
      if (captureBuffer != null) {
         captured = captureBuffer;
         capturedCount = fillCount;
         captureBuffer = null;
         buildPaintable();
      }
   }

   private static void buildPaintable() {
      int sz = captureSize;
      if (sz <= 0) {
         paintable = null;
         paintableSize = 0;
      } else {
         if (paintable != null && paintableSize == sz) {
            Arrays.fill(paintable, false);
         } else {
            paintable = new boolean[sz * sz];
            paintableSize = sz;
         }

         for (int f = 0; f < capturedCount; f++) {
            int[] rect = uvRect(captured.get(f).uv);
            int x0 = Math.max(0, rect[0]);
            int y0 = Math.max(0, rect[1]);
            int x1 = Math.min(sz, rect[0] + rect[2]);
            int y1 = Math.min(sz, rect[1] + rect[3]);

            for (int y = y0; y < y1; y++) {
               int row = y * sz;

               for (int x = x0; x < x1; x++) {
                  paintable[row + x] = true;
               }
            }
         }
      }
   }

   private static boolean paintableAt(int u, int v, int sz, boolean prop) {
      if (u >= 0 && v >= 0 && u < sz && v < sz) {
         boolean inMask = false;
         if (paintable != null && paintableSize > 0) {
            int mu = paintableSize == sz ? u : (int)((long)u * (long)paintableSize / (long)sz);
            int mv = paintableSize == sz ? v : (int)((long)v * (long)paintableSize / (long)sz);
            inMask = mu >= 0 && mv >= 0 && mu < paintableSize && mv < paintableSize && paintable[mv * paintableSize + mu];
         }

         boolean inFace = faceRect != null && u >= faceRect[0] && u < faceRect[0] + faceRect[2] && v >= faceRect[1] && v < faceRect[1] + faceRect[3];
         if (!inMask && !inFace) {
            return false;
         } else if (!prop && cursorUv != null) {
            BodyPart under = SkinRegions.partAtScaled(u, v, sz);
            BodyPart at = SkinRegions.partAtScaled((int)Math.floor(cursorUv[0]), (int)Math.floor(cursorUv[1]), sz);
            return under == null || at == null || under == at;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static void rememberProp() {
      if (live != null && Boolean.TRUE.equals(liveProp) && PropPaintStore.slot() == 0) {
         PropPaintStore.put(PropPaintStore.wornKey(Minecraft.m_91087_().f_91074_), live);
      }
   }

   private static int[] blankProp() {
      int[] px = new int[4096];
      Arrays.fill(px, -1);
      return px;
   }

   private static int[] currentPropPixels(LocalPlayer p) {
      return live != null && Boolean.TRUE.equals(liveProp) ? live : Services.PLATFORM.get(p, PaintAttachments.PROP_CANVAS).pixels();
   }

   public static int activeSlot() {
      return propMode() ? PropPaintStore.slot() : BodyPaintStore.slot();
   }

   public static boolean slotFilled(LocalPlayer p, int slot) {
      return propMode() ? PropPaintStore.has(p, slot) : BodyPaintStore.has(slot);
   }

   private static void rememberBodySlot() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null && !propMode() && BodyPaintStore.slot() == 0 && live != null && !Boolean.TRUE.equals(liveProp)) {
         BodyPaintStore.put(0, live);
         BodyPaintStore.save();
      }
   }

   private static int[] currentBodyPixels(LocalPlayer p) {
      return live != null && !Boolean.TRUE.equals(liveProp) ? live : Services.PLATFORM.get(p, PaintAttachments.BODY_CANVAS).pixels();
   }

   public static void selectSlot(int slot) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null) {
         if (!propMode()) {
            selectBodySlot(p, slot);
         } else {
            PropPaintStore.setSlot(slot);
            int[] saved = PropPaintStore.get(PropPaintStore.wornKey(p));
            int[] px = saved != null ? (int[])saved.clone() : blankProp();
            live = px;
            liveProp = Boolean.TRUE;
            liveVersion++;
            sentSnapshot = null;
            dirty = false;
            undoStack.clear();
            lastPropKey = PropPaintStore.wornKey(p);
            ClientNet.sendToServer(new SetPropCanvasPayload(new BodyCanvas(px)));
         }
      }
   }

   private static void selectBodySlot(LocalPlayer p, int slot) {
      if (slot != BodyPaintStore.slot()) {
         rememberBodySlot();
      }

      BodyPaintStore.setSlot(slot);
      ensureLive(p);
      if (live != null) {
         int[] saved = BodyPaintStore.get(slot, BodyCanvas.sizeOf(live.length));
         if (saved != null) {
            live = saved;
            liveProp = Boolean.FALSE;
            liveVersion++;
            sentSnapshot = null;
            dirty = true;
            undoStack.clear();
         }
      }
   }

   public static void saveSlot(int slot) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null) {
         if (!propMode()) {
            BodyPaintStore.put(slot, currentBodyPixels(p));
            BodyPaintStore.save();
         } else {
            PropPaintStore.put(PropPaintStore.wornKey(p, slot), currentPropPixels(p));
            PropPaintStore.save();
         }
      }
   }

   public static boolean slotUnsaved() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p == null) {
         return false;
      } else if (propMode()) {
         int[] saved = PropPaintStore.get(PropPaintStore.wornKey(p));
         int[] now = currentPropPixels(p);
         return saved == null ? !isBlankProp(now) : !Arrays.equals(saved, now);
      } else {
         int[] now = currentBodyPixels(p);
         int[] saved = BodyPaintStore.get(BodyPaintStore.slot(), BodyCanvas.sizeOf(now.length));
         return saved == null || !Arrays.equals(saved, now);
      }
   }

   private static boolean isBlankProp(int[] px) {
      for (int c : px) {
         if (c != -1) {
            return false;
         }
      }

      return true;
   }

   private static void restoreStoredProp(LocalPlayer p) {
      String geom = PropPaintStore.wornGeometry(p);
      if (!Objects.equals(geom, lastPropGeometry)) {
         lastPropGeometry = geom;
         PropPaintStore.setSlot(0);
      }

      String key = PropPaintStore.wornKey(p);
      if (!Objects.equals(key, lastPropKey)) {
         if (lastPropKey != null && live != null && Boolean.TRUE.equals(liveProp)) {
            PropPaintStore.put(lastPropKey, live);
            PropPaintStore.save();
         }

         lastPropKey = key;
         if (key != null) {
            int[] saved = PropPaintStore.get(key);
            if (saved != null) {
               live = null;
               liveProp = null;
               sentSnapshot = null;
               ClientNet.sendToServer(new SetPropCanvasPayload(new BodyCanvas(saved)));
            }
         }
      }
   }

   public static void tick() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      restoreStoredProp(p);
      if (p != null && live != null) {
         boolean sessionAlive = Services.PLATFORM.get(p, PaintAttachments.LOCKED) || Boolean.TRUE.equals(liveProp) && propMode();
         if (!sessionAlive) {
            if (dirty) {
               sendCanvas();
            }

            rememberProp();
            PropPaintStore.save();
            rememberBodySlot();
            live = null;
            sentSnapshot = null;
            dirty = false;
            syncTimer = 0;
            captureLayer = false;
            undoStack.clear();
         } else if (dirty && ++syncTimer >= 20) {
            sendCanvas();
            dirty = false;
            syncTimer = 0;
         }
      }
   }

   private static void sendCanvas() {
      int[] now = (int[])live.clone();
      if (Boolean.TRUE.equals(liveProp)) {
         ClientNet.sendToServer(new SetPropCanvasPayload(new BodyCanvas(now)));
         rememberProp();
         sentSnapshot = now;
      } else if (sentSnapshot != null && sentSnapshot.length == now.length) {
         int[] runs = diffRuns(sentSnapshot, now);
         if (runs == null) {
            ClientNet.sendToServer(new SetCanvasPayload(new BodyCanvas(now)));
         } else if (runs.length > 0) {
            ClientNet.sendToServer(new CanvasDeltaPayload(BodyCanvas.sizeOf(now.length), runs));
         }

         sentSnapshot = now;
      } else {
         ClientNet.sendToServer(new SetCanvasPayload(new BodyCanvas(now)));
         sentSnapshot = now;
      }
   }

   private static int[] diffRuns(int[] before, int[] after) {
      List<int[]> runs = new ArrayList<>();
      int changed = 0;
      int i = 0;

      while (i < after.length) {
         if (after[i] != before[i]) {
            int start = i;
            int color = after[i];

            while (i < after.length && after[i] != before[i] && after[i] == color) {
               i++;
            }

            runs.add(new int[]{start, i - start, color});
            changed += i - start;
            if (runs.size() > 8192 || changed * 3 > after.length) {
               return null;
            }
         } else {
            i++;
         }
      }

      int[] out = new int[runs.size() * 3];

      for (int k = 0; k < runs.size(); k++) {
         int[] r = runs.get(k);
         out[k * 3] = r[0];
         out[k * 3 + 1] = r[1];
         out[k * 3 + 2] = r[2];
      }

      return out;
   }

   public static boolean compute(double mx, double my, float partialTick) {
      hasCursor = false;
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer p = mc.f_91074_;
      if (p != null && capturedCount != 0) {
         CameraProjection cam = CameraProjection.of(partialTick);
         if (cam == null) {
            return false;
         } else {
            double bestDepth = Double.MAX_VALUE;
            double[] bestHus = null;
            double[] bestUv = null;
            int[] bestRect = null;

            for (int f = 0; f < capturedCount; f++) {
               BodyPaint.CapturedFace face = captured.get(f);
               double depthSum = 0.0;
               boolean ok = true;

               for (int i = 0; i < 4; i++) {
                  Vector3f c = face.corners[i];
                  if (!cam.project((double)c.x(), (double)c.y(), (double)c.z(), scrScreen[i])) {
                     ok = false;
                     break;
                  }

                  depthSum += cam.depth((double)c.x(), (double)c.y(), (double)c.z());
               }

               if (ok) {
                  squareToQuad(scrScreen, scrHs);
                  if (invert(scrHs, scrHsInv)) {
                     apply(scrHsInv, mx, my, scrSq);
                     if (!(scrSq[0] < 0.0) && !(scrSq[0] > 1.0) && !(scrSq[1] < 0.0) && !(scrSq[1] > 1.0)) {
                        double depth = depthSum / 4.0;
                        if (!(depth >= bestDepth)) {
                           squareToQuad(face.uv, scrHu);
                           if (invert(scrHu, scrHuInv)) {
                              double[] uv = new double[2];
                              apply(scrHu, scrSq[0], scrSq[1], uv);
                              double[] hus = new double[9];
                              mul(scrHs, scrHuInv, hus);
                              bestUv = uv;
                              bestHus = hus;
                              bestRect = uvRect(face.uv);
                              bestDepth = depth;
                           }
                        }
                     }
                  }
               }
            }

            if (bestHus == null) {
               return false;
            } else {
               hasCursor = true;
               cursorUv = bestUv;
               husFlat = bestHus;
               faceRect = bestRect;
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static void renderRing(GuiGraphics g) {
      if (hasCursor) {
         int col = PaintModeScreen.contrastFor(sampleAtCursor());
         double rt = brushTexels();
         int segs = 48;

         for (int i = 0; i < segs; i++) {
            double a = (double)i / (double)segs * Math.PI * 2.0;
            apply(husFlat, cursorUv[0] + Math.cos(a) * rt, cursorUv[1] + Math.sin(a) * rt, scrRing);
            int sx = (int)Math.round(scrRing[0]);
            int sy = (int)Math.round(scrRing[1]);
            g.m_280509_(sx - 1, sy - 1, sx + 1, sy + 1, col);
         }
      }
   }

   public static void paint() {
      if (hasCursor) {
         LocalPlayer p = Minecraft.m_91087_().f_91074_;
         if (p != null) {
            ClientPaintState.Tool tool = ClientPaintState.tool();
            boolean spray = tool == ClientPaintState.Tool.SPRAY;
            boolean texture = ClientPaintState.isTextureTool(tool) && ClientPaintState.hasTexturePattern();
            int solid = tool == ClientPaintState.Tool.ERASER ? 0 : ClientPaintState.currentColor();
            double rt = brushTexels();
            ensureLive(p);
            boolean prop = Boolean.TRUE.equals(liveProp);
            int[] px = live;
            int sz = BodyCanvas.sizeOf(px.length);
            int cu = (int)Math.floor(cursorUv[0]);
            int cv = (int)Math.floor(cursorUv[1]);
            int r = (int)Math.ceil(rt);
            boolean eraser = tool == ClientPaintState.Tool.ERASER;
            double opacity = ClientPaintState.opacity();
            double smooth = ClientPaintState.smooth();
            double sparkleProb = sparkleProb(rt);
            int lockedMask = ChameleonArmor.lockedMask(p);
            boolean any = false;
            int step = gridStep();
            int cuCell = cellOrigin(cu, step);
            int cvCell = cellOrigin(cv, step);

            for (int cy = cellOrigin(cv - r, step); cy <= cv + r; cy += step) {
               for (int cx = cellOrigin(cu - r, step); cx <= cu + r; cx += step) {
                  double du = (double)cx + (double)step * 0.5 - cursorUv[0];
                  double dv = (double)cy + (double)step * 0.5 - cursorUv[1];
                  double d2 = du * du + dv * dv;
                  boolean center = cx == cuCell && cy == cvCell;
                  if ((center || d2 <= rt * rt) && (!(sparkleProb > 0.0) || !(SPRAY_RNG.nextDouble() > sparkleProb))) {
                     double op = opacity;
                     if (smooth > 0.0 && rt > 0.0 && !center) {
                        op = opacity * (1.0 - smooth * Math.min(1.0, Math.sqrt(d2) / rt));
                     }

                     int col = texture ? ClientPaintState.textureAt(cx, cy) : (spray ? ClientPaintState.paintColor(SPRAY_RNG) : solid);

                     for (int v = cy; v < cy + step; v++) {
                        for (int u = cx; u < cx + step; u++) {
                           if (paintableAt(u, v, sz, prop)) {
                              if (!prop) {
                                 BodyPart lbp = SkinRegions.partAtScaled(u, v, sz);
                                 if (lbp != null && (lockedMask & 1 << lbp.ordinal()) != 0) {
                                    continue;
                                 }
                              }

                              int idx = SkinRegions.indexIn(u, v, sz);
                              px[idx] = eraser ? 0 : blend(px[idx], col, op);
                              any = true;
                           }
                        }
                     }
                  }
               }
            }

            if (any) {
               mirrorCutoutFaces(cu, cv);
               dirty = true;
               liveVersion++;
            } else if (!prop) {
               signedNag(p, sz, lockedMask);
            }
         }
      }
   }

   public static void applyPreset(String name) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null) {
         BodyCanvas preset = ArmorPresets.canvas(name);
         if (preset != null) {
            ensureLive(p);
            if (live != null && !Boolean.TRUE.equals(liveProp)) {
               int[] src = preset.pixels();
               int sz = BodyCanvas.sizeOf(live.length);
               int ssz = BodyCanvas.sizeOf(src.length);
               int mask = ChameleonArmor.coverageMask(p);
               int lockedMask = ChameleonArmor.lockedMask(p);
               beginStroke();

               for (int v = 0; v < sz; v++) {
                  for (int u = 0; u < sz; u++) {
                     BodyPart part = SkinRegions.partAtScaled(u, v, sz);
                     if (part != null && (mask & 1 << part.ordinal()) != 0 && (lockedMask & 1 << part.ordinal()) == 0) {
                        int su = ssz == sz ? u : u * ssz / sz;
                        int sv = ssz == sz ? v : v * ssz / sz;
                        live[SkinRegions.indexIn(u, v, sz)] = src[SkinRegions.indexIn(su, sv, ssz)];
                     }
                  }
               }

               dirty = true;
               liveVersion++;
            }
         }
      }
   }

   public static int presetSwatch(String name) {
      BodyCanvas preset = ArmorPresets.canvas(name);
      if (preset == null) {
         return -14013910;
      } else {
         long r = 0L;
         long g = 0L;
         long b = 0L;
         int n = 0;

         for (int c : preset.pixels()) {
            if ((c & 0xFF000000) != 0) {
               r += (long)(c >> 16 & 0xFF);
               g += (long)(c >> 8 & 0xFF);
               b += (long)(c & 0xFF);
               n++;
            }
         }

         return n == 0 ? -14013910 : 0xFF000000 | (int)(r / (long)n) << 16 | (int)(g / (long)n) << 8 | (int)(b / (long)n);
      }
   }

   public static boolean applyBlockTexture(Block block) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p == null) {
         return false;
      } else {
         BodyCanvas generated = BlockTexturePresets.canvas(block);
         if (generated == null) {
            return false;
         } else {
            ensureLive(p);
            if (live != null && !Boolean.TRUE.equals(liveProp)) {
               int[] src = generated.pixels();
               int sz = BodyCanvas.sizeOf(live.length);
               int ssz = BodyCanvas.sizeOf(src.length);
               int mask = ChameleonArmor.coverageMask(p);
               int lockedMask = ChameleonArmor.lockedMask(p);
               beginStroke();

               for (int v = 0; v < sz; v++) {
                  for (int u = 0; u < sz; u++) {
                     BodyPart part = SkinRegions.partAtScaled(u, v, sz);
                     if (part != null && (mask & 1 << part.ordinal()) != 0 && (lockedMask & 1 << part.ordinal()) == 0) {
                        int su = ssz == sz ? u : u * ssz / sz;
                        int sv = ssz == sz ? v : v * ssz / sz;
                        live[SkinRegions.indexIn(u, v, sz)] = src[SkinRegions.indexIn(su, sv, ssz)];
                     }
                  }
               }

               dirty = true;
               liveVersion++;
               return true;
            } else {
               return false;
            }
         }
      }
   }

   public static int[] presetQuad(String name) {
      int fallback = presetSwatch(name);
      int[] out = new int[]{fallback, fallback, fallback, fallback};
      BodyCanvas preset = ArmorPresets.canvas(name);
      if (preset == null) {
         return out;
      } else {
         int[] px = preset.pixels();
         int sz = BodyCanvas.sizeOf(px.length);
         int[] uv = new int[]{21, 22, 25, 22, 21, 28, 25, 28};

         for (int i = 0; i < 4; i++) {
            int u = uv[i * 2] * sz / 64;
            int v = uv[i * 2 + 1] * sz / 64;
            if (u >= 0 && v >= 0 && u < sz && v < sz) {
               int c = px[SkinRegions.indexIn(u, v, sz)];
               if ((c & 0xFF000000) != 0) {
                  out[i] = c;
               }
            }
         }

         return out;
      }
   }

   public static TextureAtlasSprite presetSprite(String name) {
      for (Item item : ArmorPresets.presetBlocks()) {
         if (name.equals(ArmorPresets.presetFor(item)) && item instanceof BlockItem bi) {
            return BlockTexturePresets.sprite(bi.m_40614_());
         }
      }

      return null;
   }

   public static List<String> blockPresets() {
      List<String> out = new ArrayList<>();

      for (String n : ArmorPresets.STARTER_PRESETS) {
         if (ArmorPresets.canvas(n) != null) {
            out.add(n);
         }
      }

      return out;
   }

   public static void paintTexel(double cu, double cv) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null) {
         boolean prop = propMode();
         int mask = ChameleonArmor.coverageMask(p);
         if (mask != 0 || prop) {
            int lockedMask = ChameleonArmor.lockedMask(p);
            ensureLive(p);
            int sz = BodyCanvas.sizeOf(live.length);
            ClientPaintState.Tool tool = ClientPaintState.tool();
            boolean spray = tool == ClientPaintState.Tool.SPRAY;
            boolean texture = ClientPaintState.isTextureTool(tool) && ClientPaintState.hasTexturePattern();
            int solid = tool == ClientPaintState.Tool.ERASER ? 0 : ClientPaintState.currentColor();
            double rt = brushTexels();
            int r = (int)Math.ceil(rt);
            int cu0 = (int)Math.floor(cu);
            int cv0 = (int)Math.floor(cv);
            boolean eraser = tool == ClientPaintState.Tool.ERASER;
            double opacity = ClientPaintState.opacity();
            double smooth = ClientPaintState.smooth();
            double sparkleProb = sparkleProb(rt);
            boolean any = false;
            int step = gridStep();
            int cuCell = cellOrigin(cu0, step);
            int cvCell = cellOrigin(cv0, step);

            for (int cy = cellOrigin(cv0 - r, step); cy <= cv0 + r; cy += step) {
               for (int cx = cellOrigin(cu0 - r, step); cx <= cu0 + r; cx += step) {
                  double du = (double)cx + (double)step * 0.5 - cu;
                  double dv = (double)cy + (double)step * 0.5 - cv;
                  double d2 = du * du + dv * dv;
                  boolean center = cx == cuCell && cy == cvCell;
                  if ((center || d2 <= rt * rt) && (!(sparkleProb > 0.0) || !(SPRAY_RNG.nextDouble() > sparkleProb))) {
                     double op = opacity;
                     if (smooth > 0.0 && rt > 0.0 && !center) {
                        op = opacity * (1.0 - smooth * Math.min(1.0, Math.sqrt(d2) / rt));
                     }

                     int col = texture ? ClientPaintState.textureAt(cx, cy) : (spray ? ClientPaintState.paintColor(SPRAY_RNG) : solid);

                     for (int v = cy; v < cy + step; v++) {
                        for (int u = cx; u < cx + step; u++) {
                           if (u >= 0 && u < sz && v >= 0 && v < sz) {
                              if (!prop) {
                                 BodyPart part = SkinRegions.partAtScaled(u, v, sz);
                                 if (part == null || (mask & 1 << part.ordinal()) == 0 || (lockedMask & 1 << part.ordinal()) != 0) {
                                    continue;
                                 }
                              }

                              int idx = SkinRegions.indexIn(u, v, sz);
                              live[idx] = eraser ? 0 : blend(live[idx], col, op);
                              any = true;
                           }
                        }
                     }
                  }
               }
            }

            if (any) {
               mirrorCutoutFaces(cu0, cv0);
               dirty = true;
               liveVersion++;
            }
         }
      }
   }

   private static void mirrorCutoutFaces(int u, int v) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (live != null && Boolean.TRUE.equals(liveProp) && p != null) {
         int prop = Services.PLATFORM.get(p, PaintAttachments.PROP);
         if (prop >= 0) {
            int variant = Services.PLATFORM.get(p, PaintAttachments.PROP_VARIANT);
            List<int[][]> pairs = PropModels.cutoutFacePairs(prop, variant);
            if (!pairs.isEmpty()) {
               int sz = 64;
               int[] src = null;
               boolean srcBack = false;

               for (int[][] pair : pairs) {
                  for (int f = 0; f < 2; f++) {
                     int[] r = pair[f];
                     if (u >= r[0] && u < r[0] + r[2] && v >= r[1] && v < r[1] + r[3]) {
                        src = r;
                        srcBack = f == 1;
                     }
                  }
               }

               if (src != null) {
                  for (int[][] pair : pairs) {
                     for (int fx = 0; fx < 2; fx++) {
                        int[] dst = pair[fx];
                        if (dst != src && dst[2] == src[2] && dst[3] == src[3]) {
                           copyFace(src, dst, fx == 1 != srcBack, sz);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void copyFace(int[] src, int[] dst, boolean mirror, int sz) {
      for (int dy = 0; dy < src[3]; dy++) {
         for (int dx = 0; dx < src[2]; dx++) {
            int sx = src[0] + dx;
            int sy = src[1] + dy;
            int tx = dst[0] + (mirror ? dst[2] - 1 - dx : dx);
            int ty = dst[1] + dy;
            if (sx >= 0 && sy >= 0 && tx >= 0 && ty >= 0 && sx < sz && sy < sz && tx < sz && ty < sz) {
               live[ty * sz + tx] = live[sy * sz + sx];
            }
         }
      }
   }

   public static void clearCursor() {
      hasCursor = false;
   }

   private static void signedNag(LocalPlayer p, int sz, int lockedMask) {
      BodyPart part = SkinRegions.partAtScaled((int)Math.floor(cursorUv[0]), (int)Math.floor(cursorUv[1]), sz);
      if (part != null && (lockedMask & 1 << part.ordinal()) != 0) {
         long now = p.m_9236_().m_46467_();
         if (now - lastSignedNag >= 60L) {
            lastSignedNag = now;
            ClientShims.overlay(Component.m_237115_("fantastic.paint.signed_locked"), false);
         }
      }
   }

   private static int blend(int existing, int col, double op) {
      if (op >= 0.999) {
         return col;
      } else {
         int e = (existing & 0xFF000000) == 0 ? -1 : existing;
         int er = e >> 16 & 0xFF;
         int eg = e >> 8 & 0xFF;
         int eb = e & 0xFF;
         int cr = col >> 16 & 0xFF;
         int cg = col >> 8 & 0xFF;
         int cb = col & 0xFF;
         int rr = (int)Math.round((double)er + (double)(cr - er) * op);
         int gg = (int)Math.round((double)eg + (double)(cg - eg) * op);
         int bb = (int)Math.round((double)eb + (double)(cb - eb) * op);
         return 0xFF000000 | rr << 16 | gg << 8 | bb;
      }
   }

   private static int[] currentPixels() {
      if (live != null) {
         return live;
      } else {
         LocalPlayer p = Minecraft.m_91087_().f_91074_;
         return p == null ? null : Services.PLATFORM.get(p, propMode() ? PaintAttachments.PROP_CANVAS : PaintAttachments.BODY_CANVAS).pixels();
      }
   }

   public static int sampleAtCursor() {
      return hasCursor ? sampleTexel(cursorUv[0], cursorUv[1]) : 0;
   }

   /** Devuelve exactamente el texel editable bajo el cursor, sin promediar el radio del pincel. */
   public static int sampleTexel(double cu, double cv) {
      int[] px = currentPixels();
      if (px == null) {
         return 0;
      }

      int sz = BodyCanvas.sizeOf(px.length);
      int u = (int)Math.floor(cu);
      int v = (int)Math.floor(cv);
      if (u < 0 || v < 0 || u >= sz || v >= sz) {
         return 0;
      }

      int color = px[SkinRegions.indexIn(u, v, sz)];
      return (color & 0xFF000000) == 0 ? 0 : color;
   }

   public static int sampleArea(double cu, double cv) {
      int[] px = currentPixels();
      if (px == null) {
         return 0;
      } else {
         int sz = BodyCanvas.sizeOf(px.length);
         double rt = brushTexels();
         int r = (int)Math.ceil(rt);
         int cu0 = (int)Math.floor(cu);
         int cv0 = (int)Math.floor(cv);
         long rs = 0L;
         long gs = 0L;
         long bs = 0L;
         int n = 0;

         for (int v = cv0 - r; v <= cv0 + r; v++) {
            for (int u = cu0 - r; u <= cu0 + r; u++) {
               if (u >= 0 && u < sz && v >= 0 && v < sz) {
                  double du = (double)u + 0.5 - cu;
                  double dv = (double)v + 0.5 - cv;
                  if (u == cu0 && v == cv0 || !(du * du + dv * dv > rt * rt)) {
                     int c = px[SkinRegions.indexIn(u, v, sz)];
                     if ((c & 0xFF000000) != 0) {
                        rs += (long)(c >> 16 & 0xFF);
                        gs += (long)(c >> 8 & 0xFF);
                        bs += (long)(c & 0xFF);
                        n++;
                     }
                  }
               }
            }
         }

         return n == 0 ? -1 : 0xFF000000 | (int)(rs / (long)n) << 16 | (int)(gs / (long)n) << 8 | (int)(bs / (long)n);
      }
   }

   private static double sparkleProb(double rt) {
      int n = ClientPaintState.densityCount();
      if (n <= 0) {
         return 0.0;
      } else {
         double area = Math.max(1.0, Math.PI * rt * rt);
         return Math.min(1.0, (double)n / area);
      }
   }

   public static double brushTexels() {
      return Math.max(0.45, ClientPaintState.brushRadius() * texelsPerBlock());
   }

   private static int[] uvRect(double[][] uv) {
      double minU = Math.min(Math.min(uv[0][0], uv[1][0]), Math.min(uv[2][0], uv[3][0]));
      double maxU = Math.max(Math.max(uv[0][0], uv[1][0]), Math.max(uv[2][0], uv[3][0]));
      double minV = Math.min(Math.min(uv[0][1], uv[1][1]), Math.min(uv[2][1], uv[3][1]));
      double maxV = Math.max(Math.max(uv[0][1], uv[1][1]), Math.max(uv[2][1], uv[3][1]));
      int x = (int)Math.floor(minU);
      int y = (int)Math.floor(minV);
      return new int[]{x, y, Math.max(1, (int)Math.ceil(maxU) - x), Math.max(1, (int)Math.ceil(maxV) - y)};
   }

   private static void squareToQuad(double[][] q, double[] out) {
      double dx1 = q[1][0] - q[2][0];
      double dx2 = q[3][0] - q[2][0];
      double dx3 = q[0][0] - q[1][0] + q[2][0] - q[3][0];
      double dy1 = q[1][1] - q[2][1];
      double dy2 = q[3][1] - q[2][1];
      double dy3 = q[0][1] - q[1][1] + q[2][1] - q[3][1];
      double a;
      double b;
      double c;
      double d;
      double e;
      double f;
      double gg;
      double h;
      if (Math.abs(dx3) < 1.0E-9 && Math.abs(dy3) < 1.0E-9) {
         a = q[1][0] - q[0][0];
         b = q[2][0] - q[1][0];
         c = q[0][0];
         d = q[1][1] - q[0][1];
         e = q[2][1] - q[1][1];
         f = q[0][1];
         gg = 0.0;
         h = 0.0;
      } else {
         double den = dx1 * dy2 - dx2 * dy1;
         gg = (dx3 * dy2 - dx2 * dy3) / den;
         h = (dx1 * dy3 - dx3 * dy1) / den;
         a = q[1][0] - q[0][0] + gg * q[1][0];
         b = q[3][0] - q[0][0] + h * q[3][0];
         c = q[0][0];
         d = q[1][1] - q[0][1] + gg * q[1][1];
         e = q[3][1] - q[0][1] + h * q[3][1];
         f = q[0][1];
      }

      out[0] = a;
      out[1] = b;
      out[2] = c;
      out[3] = d;
      out[4] = e;
      out[5] = f;
      out[6] = gg;
      out[7] = h;
      out[8] = 1.0;
   }

   private static void mul(double[] a, double[] b, double[] out) {
      for (int i = 0; i < 3; i++) {
         for (int j = 0; j < 3; j++) {
            out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
         }
      }
   }

   private static boolean invert(double[] m, double[] out) {
      double a = m[0];
      double b = m[1];
      double c = m[2];
      double d = m[3];
      double e = m[4];
      double f = m[5];
      double g = m[6];
      double h = m[7];
      double i = m[8];
      double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
      if (Math.abs(det) < 1.0E-12) {
         return false;
      } else {
         double inv = 1.0 / det;
         out[0] = (e * i - f * h) * inv;
         out[1] = (c * h - b * i) * inv;
         out[2] = (b * f - c * e) * inv;
         out[3] = (f * g - d * i) * inv;
         out[4] = (a * i - c * g) * inv;
         out[5] = (c * d - a * f) * inv;
         out[6] = (d * h - e * g) * inv;
         out[7] = (b * g - a * h) * inv;
         out[8] = (a * e - b * d) * inv;
         return true;
      }
   }

   private static void apply(double[] m, double x, double y, double[] out) {
      double px = m[0] * x + m[1] * y + m[2];
      double py = m[3] * x + m[4] * y + m[5];
      double pw = m[6] * x + m[7] * y + m[8];
      out[0] = px / pw;
      out[1] = py / pw;
   }

   private static final class CapturedFace {
      final Vector3f[] corners = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
      final double[][] uv = new double[4][2];

      private CapturedFace() {
      }
   }
}
