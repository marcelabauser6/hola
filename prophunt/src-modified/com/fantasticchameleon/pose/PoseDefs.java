package com.fantasticchameleon.pose;

import java.util.Arrays;

/** Catálogo canónico: cada pose humana seleccionable aparece exactamente en una página. */
public final class PoseDefs {
   public static final int TILT_NONE = 0;
   public static final int TILT_BACK = 1;
   public static final int TILT_SIDE = 2;
   public static final int TILT_PRONE = 3;
   public static final int TILT_HANDSTAND = 4;
   public static final int TILT_LEAN = 5;
   public static final double[] CLIP_STAND = {-0.15, 0.15, -0.28, 0.28, 0.1, 1.75};
   public static final double[] CLIP_SIT = {-0.15, 0.65, -0.2, 0.2, 0.05, 0.95};
   public static final double[] CLIP_LIE = {0.0, 1.7, -0.2, 0.2, 0.0, 0.5};
   public static final double[] CLIP_WIDE = {-0.15, 0.15, -0.75, 0.75, 0.1, 1.75};
   public static final double[] CLIP_WALL = {-0.15, 0.15, -0.2, 0.2, 0.1, 1.9};
   public static final double[] CLIP_SNAKE = {-2.9, 0.45, -0.22, 0.22, 0.0, 0.3};
   public static final double[] CLIP_SCATTER = {-1.1, 1.1, -1.1, 1.1, 0.0, 0.3};
   public static final double[] CLIP_TOTEM = {-0.2, 0.2, -0.3, 0.3, 0.0, 2.8};
   public static final double[] CLIP_FOURS = {-0.9, 0.9, -0.3, 0.3, 0.0, 0.9};
   public static final double[] CLIP_LEAN = {-0.55, 0.85, -0.35, 0.35, 0.1, 1.55};
   public static final double[] CLIP_SMALL = {-0.25, 0.25, -0.25, 0.25, 0.0, 0.55};
   public static final double[] CLIP_BUST = {-0.2, 0.2, -0.28, 0.28, 0.0, 1.1};
   public static final double[] CLIP_CROUCH = {-0.15, 0.15, -0.28, 0.28, 0.1, 1.3};

   private static final Def[] DEFS = {
      new Def(0, "stand", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(1, "sit", -0.7F, 0, 0.0F, CLIP_SIT),
      new Def(2, "lie_back", 0.1F, 1, -0.9F, CLIP_LIE),
      new Def(3, "sneak", 0.0F, 0, 0.0F, CLIP_CROUCH),
      new Def(4, "tpose", 0.0F, 0, 0.0F, CLIP_WIDE),
      new Def(5, "arms_up", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(6, "lie_side", 0.35F, 2, -0.9F, CLIP_LIE),
      new Def(7, "fetal", 0.6F, 3, -0.9F, CLIP_LIE),
      new Def(8, "block", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(9, "kneel", -0.72F, 0, 0.0F, CLIP_SIT),
      new Def(10, "wave", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(11, "point", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(12, "sit_block", -0.35F, 0, 0.0F, CLIP_SIT),
      new Def(13, "all_fours", 0.75F, 3, -0.5F, CLIP_FOURS),
      new Def(14, "ball", -0.72F, 0, 0.0F, CLIP_SIT),
      new Def(15, "wall", 0.0F, 0, 0.0F, CLIP_WALL),
      new Def(16, "handstand", 0.0F, 4, 0.0F, CLIP_STAND),
      new Def(17, "dab", 0.0F, 0, 0.0F, CLIP_WIDE),
      new Def(18, "naruto_run", 0.0F, 5, 0.0F, CLIP_LEAN),
      new Def(19, "snake", 0.0F, 0, 0.0F, CLIP_SNAKE),
      new Def(20, "totem", 0.0F, 0, 0.0F, CLIP_TOTEM),
      new Def(21, "scatter", 0.0F, 0, 0.0F, CLIP_SCATTER),
      new Def(22, "bust", -0.75F, 0, 0.0F, CLIP_BUST),
      new Def(23, "head_only", 0.0F, 0, 0.0F, CLIP_SMALL),
      new Def(24, "arms_crossed", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(25, "salute", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(26, "facepalm", 0.0F, 0, 0.0F, CLIP_STAND),
      new Def(27, "thinker", -0.7F, 0, 0.0F, CLIP_SIT),
      new Def(28, "hero", 0.0F, 0, 0.0F, CLIP_WIDE),
      new Def(29, "bow", 0.0F, 0, 0.0F, CLIP_LEAN),
      new Def(30, "lie_flat", 0.1F, 1, -0.9F, CLIP_LIE)
   };

   // 8 es una tumba de protocolo de la antigua forma-bloque, no una pose humana. Las otras 30
   // aparecen una sola vez y por tanto pueden seleccionarse y auditarse desde la rueda.
   private static final Page[] PAGES = {
      new Page("basics", new int[]{0, 3, 1, 5, 10, 30}),
      new Page("seated", new int[]{9, 12, 14, 27}),
      new Page("statues", new int[]{2, 6, 7, 13, 15, 4}),
      new Page("gestures", new int[]{24, 25, 26, 28, 29}),
      new Page("fun", new int[]{17, 18, 19, 20, 21, 22}),
      new Page("supporter", new int[]{16, 23, 11}, true)
   };

   private PoseDefs() {}

   public static boolean isDonorPose(int pose) {
      for (Page page : PAGES) {
         if (page.donor()) {
            for (int id : page.poses()) {
               if (id == pose) return true;
            }
         }
      }
      return false;
   }

   public static boolean isValid(int pose) {
      return pose >= 0 && pose < DEFS.length;
   }

   public static boolean selectable(int pose) {
      return isValid(pose) && pose != 8;
   }

   public static int count() { return DEFS.length; }
   public static Def def(int pose) { return isValid(pose) ? DEFS[pose] : DEFS[0]; }

   public static double[] footprint(double[] clip, float yaw, double scale) {
      int quarter = Math.floorMod(Math.round(yaw / 90.0F), 4);
      double fx, fz, rx, rz;
      switch (quarter) {
         case 1 -> { fx = -1.0; fz = 0.0; rx = 0.0; rz = -1.0; }
         case 2 -> { fx = 0.0; fz = -1.0; rx = 1.0; rz = 0.0; }
         case 3 -> { fx = 1.0; fz = 0.0; rx = 0.0; rz = 1.0; }
         default -> { fx = 0.0; fz = 1.0; rx = -1.0; rz = 0.0; }
      }
      double f0 = clip[0] * scale, f1 = clip[1] * scale;
      double r0 = clip[2] * scale, r1 = clip[3] * scale;
      double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
      double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
      for (int i = 0; i < 4; i++) {
         double f = (i & 1) == 0 ? f0 : f1;
         double r = (i & 2) == 0 ? r0 : r1;
         double x = fx * f + rx * r;
         double z = fz * f + rz * r;
         minX = Math.min(minX, x); maxX = Math.max(maxX, x);
         minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
      }
      return new double[]{minX, maxX, minZ, maxZ};
   }

   public static double[] hugPosition(double[] foot, int blockX, int blockZ, boolean[] solid) {
      double x = solid[0] && !solid[1] ? blockX - foot[0]
         : solid[1] && !solid[0] ? blockX + 1.0 - foot[1] : blockX + 0.5;
      double z = solid[2] && !solid[3] ? blockZ - foot[2]
         : solid[3] && !solid[2] ? blockZ + 1.0 - foot[3] : blockZ + 0.5;
      return new double[]{x, z};
   }

   public static Page[] pages() { return PAGES; }

   public static int[] wheelPoses() {
      int count = 0;
      for (Page p : PAGES) if (!p.donor()) count += p.poses().length;
      int[] out = new int[count];
      int i = 0;
      for (Page p : PAGES) if (!p.donor()) for (int pose : p.poses()) out[i++] = pose;
      return Arrays.copyOf(out, i);
   }

   public static String label(int pose) { return "fantastic.pose." + def(pose).key(); }
   public static boolean tilted(int pose) { return def(pose).tilt() != 0; }

   public record Def(int id, String key, float floorY, int tilt, float lieShift, double[] clip) {
      public float height() { return (float)this.clip[5]; }
      public float halfSide() {
         double lateral = Math.max(Math.abs(this.clip[2]), Math.abs(this.clip[3]));
         return (float)Math.max(0.125, Math.min(0.6, lateral));
      }
      public double[] clipBox() { return this.clip; }
   }

   public record Page(String key, int[] poses, boolean donor) {
      public Page(String key, int[] poses) { this(key, poses, false); }
   }
}
