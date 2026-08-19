package com.fantasticchameleon.pose;

public final class PropShapes {
   private static final PropShapes.Box FENCE_POST = new PropShapes.Box(6.0F, 0.0F, 6.0F, 10.0F, 16.0F, 10.0F);
   private static final PropShapes.Box FENCE_N1 = new PropShapes.Box(7.0F, 6.0F, 0.0F, 9.0F, 9.0F, 6.0F);
   private static final PropShapes.Box FENCE_N2 = new PropShapes.Box(7.0F, 12.0F, 0.0F, 9.0F, 15.0F, 6.0F);
   private static final PropShapes.Box FENCE_S1 = new PropShapes.Box(7.0F, 6.0F, 10.0F, 9.0F, 9.0F, 16.0F);
   private static final PropShapes.Box FENCE_S2 = new PropShapes.Box(7.0F, 12.0F, 10.0F, 9.0F, 15.0F, 16.0F);
   private static final PropShapes.Box FENCE_E1 = new PropShapes.Box(10.0F, 6.0F, 7.0F, 16.0F, 9.0F, 9.0F);
   private static final PropShapes.Box FENCE_E2 = new PropShapes.Box(10.0F, 12.0F, 7.0F, 16.0F, 15.0F, 9.0F);
   private static final PropShapes.Box FENCE_W1 = new PropShapes.Box(0.0F, 6.0F, 7.0F, 6.0F, 9.0F, 9.0F);
   private static final PropShapes.Box FENCE_W2 = new PropShapes.Box(0.0F, 12.0F, 7.0F, 6.0F, 15.0F, 9.0F);
   private static final PropShapes.Box WALL_POST = new PropShapes.Box(4.0F, 0.0F, 4.0F, 12.0F, 16.0F, 12.0F);
   private static final PropShapes.Box WALL_N = new PropShapes.Box(5.0F, 0.0F, 0.0F, 11.0F, 14.0F, 4.0F);
   private static final PropShapes.Box WALL_S = new PropShapes.Box(5.0F, 0.0F, 12.0F, 11.0F, 14.0F, 16.0F);
   private static final PropShapes.Box WALL_E = new PropShapes.Box(12.0F, 0.0F, 5.0F, 16.0F, 14.0F, 11.0F);
   private static final PropShapes.Box WALL_W = new PropShapes.Box(0.0F, 0.0F, 5.0F, 4.0F, 14.0F, 11.0F);
   private static final PropShapes.Box PANE_POST = new PropShapes.Box(7.0F, 0.0F, 7.0F, 9.0F, 16.0F, 9.0F);
   private static final PropShapes.Box PANE_N = new PropShapes.Box(7.0F, 0.0F, 0.0F, 9.0F, 16.0F, 7.0F);
   private static final PropShapes.Box PANE_S = new PropShapes.Box(7.0F, 0.0F, 9.0F, 9.0F, 16.0F, 16.0F);
   private static final PropShapes.Box PANE_E = new PropShapes.Box(9.0F, 0.0F, 7.0F, 16.0F, 16.0F, 9.0F);
   private static final PropShapes.Box PANE_W = new PropShapes.Box(0.0F, 0.0F, 7.0F, 7.0F, 16.0F, 9.0F);
   private static final PropShapes.Box[] POT = new PropShapes.Box[]{
      new PropShapes.Box(5.0F, 0.0F, 5.0F, 6.0F, 6.0F, 11.0F),
      new PropShapes.Box(10.0F, 0.0F, 5.0F, 11.0F, 6.0F, 11.0F),
      new PropShapes.Box(6.0F, 0.0F, 5.0F, 10.0F, 6.0F, 6.0F),
      new PropShapes.Box(6.0F, 0.0F, 10.0F, 10.0F, 6.0F, 11.0F),
      new PropShapes.Box(6.0F, 0.0F, 6.0F, 10.0F, 1.0F, 10.0F)
   };
   private static final PropShapes.Box STAIR_SLAB = new PropShapes.Box(0.0F, 0.0F, 0.0F, 16.0F, 8.0F, 16.0F);
   private static final PropShapes.Box STAIR_TOP_SLAB = new PropShapes.Box(0.0F, 8.0F, 0.0F, 16.0F, 16.0F, 16.0F);
   private static final PropShapes.Box STAIR_STEP = new PropShapes.Box(0.0F, 8.0F, 0.0F, 16.0F, 16.0F, 8.0F);
   private static final PropShapes.Box STAIR_STEP_LOW = new PropShapes.Box(0.0F, 0.0F, 0.0F, 16.0F, 8.0F, 8.0F);
   private static final PropShapes.Box STAIR_QUARTER = new PropShapes.Box(0.0F, 8.0F, 0.0F, 8.0F, 16.0F, 8.0F);
   private static final PropShapes.Box STAIR_QUARTER_LOW = new PropShapes.Box(0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 8.0F);
   private static final PropShapes.Box STAIR_QUARTER_B = new PropShapes.Box(8.0F, 8.0F, 0.0F, 16.0F, 16.0F, 8.0F);
   private static final PropShapes.Box STAIR_QUARTER_B_LOW = new PropShapes.Box(8.0F, 0.0F, 0.0F, 16.0F, 8.0F, 8.0F);
   private static final PropShapes.Box STAIR_SIDE = new PropShapes.Box(0.0F, 8.0F, 8.0F, 8.0F, 16.0F, 16.0F);
   private static final PropShapes.Box STAIR_SIDE_LOW = new PropShapes.Box(0.0F, 0.0F, 8.0F, 8.0F, 8.0F, 16.0F);
   public static final PropShapes.Prop[] PROPS = new PropShapes.Prop[]{
      new PropShapes.Prop(
         "slab",
         new PropShapes.Form[]{
            new PropShapes.Form("bottom", new PropShapes.Box(0.0F, 0.0F, 0.0F, 16.0F, 8.0F, 16.0F)),
            PropShapes.Form.sized("top", 1.0, 1.0, new PropShapes.Box(0.0F, 8.0F, 0.0F, 16.0F, 16.0F, 16.0F))
         },
         1,
         1.0,
         0.5
      ),
      new PropShapes.Prop(
         "stairs",
         new PropShapes.Form[]{
            new PropShapes.Form("straight", STAIR_SLAB, STAIR_STEP),
            new PropShapes.Form("inner", STAIR_SLAB, STAIR_STEP, STAIR_SIDE),
            new PropShapes.Form("outer", STAIR_SLAB, STAIR_QUARTER),
            new PropShapes.Form("straight_top", STAIR_TOP_SLAB, STAIR_STEP_LOW),
            new PropShapes.Form("inner_top", STAIR_TOP_SLAB, STAIR_STEP_LOW, STAIR_SIDE_LOW),
            new PropShapes.Form("outer_top", STAIR_TOP_SLAB, STAIR_QUARTER_LOW)
         },
         4,
         1.0,
         1.0
      ),
      simple("cake", 0.9, 0.5, new PropShapes.Box(1.0F, 0.0F, 1.0F, 15.0F, 8.0F, 15.0F)),
      new PropShapes.Prop(
         "flower_pot",
         new PropShapes.Form[]{
            new PropShapes.Form("empty", POT),
            PropShapes.Form.sized(
               "plant",
               0.45,
               1.0,
               POT[0],
               POT[1],
               POT[2],
               POT[3],
               POT[4],
               PropShapes.Box.alpha(3.0F, 2.0F, 8.0F, 13.0F, 16.0F, 8.0F, 45.0F),
               PropShapes.Box.alpha(3.0F, 2.0F, 8.0F, 13.0F, 16.0F, 8.0F, -45.0F)
            ),
            PropShapes.Form.sized("cactus", 0.45, 1.0, POT[0], POT[1], POT[2], POT[3], POT[4], new PropShapes.Box(6.0F, 6.0F, 6.0F, 10.0F, 16.0F, 10.0F))
         },
         1,
         0.45,
         0.4
      ),
      new PropShapes.Prop(
         "fence_post",
         new PropShapes.Form[]{
            new PropShapes.Form("post", FENCE_POST),
            new PropShapes.Form("end", FENCE_POST, FENCE_N1, FENCE_N2),
            new PropShapes.Form("straight", FENCE_POST, FENCE_N1, FENCE_N2, FENCE_S1, FENCE_S2),
            new PropShapes.Form("corner", FENCE_POST, FENCE_N1, FENCE_N2, FENCE_E1, FENCE_E2),
            new PropShapes.Form("tee", FENCE_POST, FENCE_N1, FENCE_N2, FENCE_S1, FENCE_S2, FENCE_E1, FENCE_E2),
            new PropShapes.Form("cross", FENCE_POST, FENCE_N1, FENCE_N2, FENCE_S1, FENCE_S2, FENCE_W1, FENCE_W2, FENCE_E1, FENCE_E2)
         },
         4,
         0.3,
         1.0
      ),
      new PropShapes.Prop(
         "lantern",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "standing",
               new PropShapes.Box(5.0F, 0.0F, 5.0F, 11.0F, 7.0F, 11.0F),
               new PropShapes.Box(6.0F, 7.0F, 6.0F, 10.0F, 9.0F, 10.0F),
               new PropShapes.Box(7.0F, 9.0F, 7.0F, 9.0F, 12.0F, 9.0F)
            ),
            PropShapes.Form.sized(
               "hanging",
               0.4,
               1.0,
               new PropShapes.Box(5.0F, 4.0F, 5.0F, 11.0F, 11.0F, 11.0F),
               new PropShapes.Box(6.0F, 11.0F, 6.0F, 10.0F, 13.0F, 10.0F),
               new PropShapes.Box(7.0F, 13.0F, 7.0F, 9.0F, 16.0F, 9.0F)
            )
         },
         1,
         0.4,
         0.75
      ),
      new PropShapes.Prop(
         "anvil",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(2.0F, 0.0F, 2.0F, 14.0F, 4.0F, 14.0F),
               new PropShapes.Box(4.0F, 4.0F, 3.0F, 12.0F, 5.0F, 13.0F),
               new PropShapes.Box(6.0F, 5.0F, 4.0F, 10.0F, 10.0F, 12.0F),
               new PropShapes.Box(3.0F, 10.0F, 0.0F, 13.0F, 16.0F, 16.0F)
            )
         },
         4,
         0.9,
         1.0
      ),
      simple("block", 0.95, 1.0, new PropShapes.Box(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F)),
      simple("carpet", 1.0, 0.1, new PropShapes.Box(0.0F, 0.0F, 0.0F, 16.0F, 1.0F, 16.0F)),
      new PropShapes.Prop(
         "trapdoor",
         new PropShapes.Form[]{
            new PropShapes.Form("floor", new PropShapes.Box(0.0F, 0.0F, 0.0F, 16.0F, 3.0F, 16.0F)),
            PropShapes.Form.sized("ceiling", 1.0, 1.0, new PropShapes.Box(0.0F, 13.0F, 0.0F, 16.0F, 16.0F, 16.0F)),
            PropShapes.Form.sized("wall", 1.0, 1.0, new PropShapes.Box(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 3.0F))
         },
         4,
         1.0,
         0.2
      ),
      new PropShapes.Prop(
         "wall",
         new PropShapes.Form[]{
            new PropShapes.Form("post", WALL_POST),
            PropShapes.Form.sized("end", 1.0, 1.0, WALL_POST, WALL_N),
            PropShapes.Form.sized("straight", 1.0, 1.0, WALL_POST, WALL_N, WALL_S),
            PropShapes.Form.sized("corner", 1.0, 1.0, WALL_POST, WALL_N, WALL_E),
            PropShapes.Form.sized("tee", 1.0, 1.0, WALL_POST, WALL_N, WALL_S, WALL_E),
            PropShapes.Form.sized("cross", 1.0, 1.0, WALL_POST, WALL_N, WALL_S, WALL_W, WALL_E)
         },
         4,
         0.5,
         1.0
      ),
      new PropShapes.Prop(
         "pane",
         new PropShapes.Form[]{
            new PropShapes.Form("post", PANE_POST),
            new PropShapes.Form("end", PANE_POST, PANE_N),
            new PropShapes.Form("straight", PANE_POST, PANE_N, PANE_S),
            new PropShapes.Form("corner", PANE_POST, PANE_N, PANE_E),
            new PropShapes.Form("tee", PANE_POST, PANE_N, PANE_S, PANE_E),
            new PropShapes.Form("cross", PANE_POST, PANE_N, PANE_S, PANE_W, PANE_E)
         },
         4,
         0.3,
         1.0
      ),
      new PropShapes.Prop(
         "cow",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(2.0F, 12.0F, -1.0F, 14.0F, 22.0F, 17.0F),
               new PropShapes.Box(4.0F, 13.0F, -7.0F, 12.0F, 21.0F, -1.0F),
               new PropShapes.Box(5.0F, 13.0F, -8.0F, 11.0F, 16.0F, -7.0F),
               new PropShapes.Box(3.0F, 20.0F, -6.0F, 4.0F, 23.0F, -5.0F),
               new PropShapes.Box(12.0F, 20.0F, -6.0F, 13.0F, 23.0F, -5.0F),
               new PropShapes.Box(3.0F, 0.0F, 1.0F, 7.0F, 12.0F, 5.0F),
               new PropShapes.Box(9.0F, 0.0F, 1.0F, 13.0F, 12.0F, 5.0F),
               new PropShapes.Box(3.0F, 0.0F, 11.0F, 7.0F, 12.0F, 15.0F),
               new PropShapes.Box(9.0F, 0.0F, 11.0F, 13.0F, 12.0F, 15.0F)
            )
         },
         1,
         0.9,
         1.4
      ),
      new PropShapes.Prop(
         "pig",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(3.0F, 6.0F, 0.0F, 13.0F, 14.0F, 16.0F),
               new PropShapes.Box(4.0F, 6.0F, -8.0F, 12.0F, 14.0F, 0.0F),
               new PropShapes.Box(6.0F, 7.0F, -9.0F, 10.0F, 10.0F, -8.0F),
               new PropShapes.Box(3.0F, 0.0F, 1.0F, 7.0F, 6.0F, 5.0F),
               new PropShapes.Box(9.0F, 0.0F, 1.0F, 13.0F, 6.0F, 5.0F),
               new PropShapes.Box(3.0F, 0.0F, 11.0F, 7.0F, 6.0F, 15.0F),
               new PropShapes.Box(9.0F, 0.0F, 11.0F, 13.0F, 6.0F, 15.0F)
            )
         },
         1,
         0.9,
         0.9
      ),
      new PropShapes.Prop(
         "sheep",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(2.0F, 10.0F, 0.0F, 14.0F, 20.0F, 18.0F),
               new PropShapes.Box(5.0F, 12.0F, -8.0F, 11.0F, 18.0F, 0.0F),
               new PropShapes.Box(3.0F, 0.0F, 2.0F, 7.0F, 10.0F, 6.0F),
               new PropShapes.Box(9.0F, 0.0F, 2.0F, 13.0F, 10.0F, 6.0F),
               new PropShapes.Box(3.0F, 0.0F, 12.0F, 7.0F, 10.0F, 16.0F),
               new PropShapes.Box(9.0F, 0.0F, 12.0F, 13.0F, 10.0F, 16.0F)
            )
         },
         1,
         0.9,
         1.3
      ),
      new PropShapes.Prop(
         "chicken",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(5.0F, 5.0F, 4.0F, 11.0F, 11.0F, 12.0F),
               new PropShapes.Box(6.0F, 9.0F, 2.0F, 10.0F, 15.0F, 5.0F),
               new PropShapes.Box(6.0F, 10.0F, 0.0F, 10.0F, 12.0F, 2.0F),
               new PropShapes.Box(7.0F, 8.0F, 1.0F, 9.0F, 10.0F, 3.0F),
               new PropShapes.Box(4.0F, 6.0F, 5.0F, 5.0F, 11.0F, 11.0F),
               new PropShapes.Box(11.0F, 6.0F, 5.0F, 12.0F, 11.0F, 11.0F),
               new PropShapes.Box(5.0F, 0.0F, 6.0F, 8.0F, 5.0F, 9.0F),
               new PropShapes.Box(8.0F, 0.0F, 6.0F, 11.0F, 5.0F, 9.0F)
            )
         },
         1,
         0.4,
         0.7
      ),
      new PropShapes.Prop(
         "wolf",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(5.0F, 7.0F, 8.0F, 11.0F, 13.0F, 17.0F),
               new PropShapes.Box(4.0F, 7.0F, 2.0F, 12.0F, 14.0F, 8.0F),
               new PropShapes.Box(5.0F, 7.0F, -1.0F, 11.0F, 14.0F, 3.0F),
               new PropShapes.Box(6.0F, 8.0F, -4.0F, 10.0F, 11.0F, 0.0F),
               new PropShapes.Box(7.0F, 8.0F, 16.0F, 9.0F, 16.0F, 19.0F),
               new PropShapes.Box(5.0F, 0.0F, 14.0F, 7.0F, 8.0F, 16.0F),
               new PropShapes.Box(9.0F, 0.0F, 14.0F, 11.0F, 8.0F, 16.0F),
               new PropShapes.Box(5.0F, 0.0F, 3.0F, 7.0F, 8.0F, 5.0F),
               new PropShapes.Box(9.0F, 0.0F, 3.0F, 11.0F, 8.0F, 5.0F)
            )
         },
         1,
         0.6,
         0.85
      ),
      new PropShapes.Prop(
         "creeper",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(4.0F, 18.0F, 4.0F, 12.0F, 26.0F, 12.0F),
               new PropShapes.Box(4.0F, 6.0F, 6.0F, 12.0F, 18.0F, 10.0F),
               new PropShapes.Box(4.0F, 0.0F, 10.0F, 8.0F, 6.0F, 14.0F),
               new PropShapes.Box(8.0F, 0.0F, 10.0F, 12.0F, 6.0F, 14.0F),
               new PropShapes.Box(4.0F, 0.0F, 2.0F, 8.0F, 6.0F, 6.0F),
               new PropShapes.Box(8.0F, 0.0F, 2.0F, 12.0F, 6.0F, 6.0F)
            )
         },
         1,
         0.6,
         1.7
      ),
      new PropShapes.Prop(
         "enderman",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(4.0F, 38.0F, 4.0F, 12.0F, 46.0F, 12.0F),
               new PropShapes.Box(4.0F, 27.0F, 6.0F, 12.0F, 39.0F, 10.0F),
               new PropShapes.Box(2.0F, 9.0F, 7.0F, 4.0F, 39.0F, 9.0F),
               new PropShapes.Box(12.0F, 9.0F, 7.0F, 14.0F, 39.0F, 9.0F),
               new PropShapes.Box(5.0F, 0.0F, 7.0F, 7.0F, 30.0F, 9.0F),
               new PropShapes.Box(9.0F, 0.0F, 7.0F, 11.0F, 30.0F, 9.0F)
            )
         },
         1,
         0.6,
         2.9
      ),
      new PropShapes.Prop(
         "panda",
         new PropShapes.Form[]{
            new PropShapes.Form(
               "default",
               new PropShapes.Box(1.5F, 6.0F, 0.0F, 14.5F, 16.0F, 19.0F),
               new PropShapes.Box(1.5F, 8.0F, -9.0F, 14.5F, 18.0F, 0.0F),
               new PropShapes.Box(5.0F, 8.0F, -10.0F, 11.0F, 12.0F, -9.0F),
               new PropShapes.Box(2.0F, 0.0F, 1.0F, 6.0F, 6.0F, 6.0F),
               new PropShapes.Box(10.0F, 0.0F, 1.0F, 14.0F, 6.0F, 6.0F),
               new PropShapes.Box(2.0F, 0.0F, 13.0F, 6.0F, 6.0F, 18.0F),
               new PropShapes.Box(10.0F, 0.0F, 13.0F, 14.0F, 6.0F, 18.0F)
            )
         },
         1,
         1.3,
         1.25
      )
   };
   public static final PropShapes.ShapePage[] SHAPE_PAGES = new PropShapes.ShapePage[]{
      page("monsters", "creeper", "enderman"),
      page("mobs", "cow", "pig", "sheep", "chicken", "wolf", "panda"),
      page("decor", "cake", "flower_pot", "lantern", "anvil"),
      page("barriers", "fence_post", "wall", "pane"),
      page("blocks", "block", "slab", "stairs", "carpet", "trapdoor")
   };
   private static final int FACING_STRIDE = 4;

   private PropShapes() {
   }

   private static PropShapes.Prop simple(String key, double boxXZ, double boxY, PropShapes.Box... boxes) {
      return new PropShapes.Prop(key, new PropShapes.Form[]{new PropShapes.Form("default", boxes)}, 1, boxXZ, boxY);
   }

   private static PropShapes.ShapePage page(String key, String... keys) {
      int[] idx = new int[keys.length];

      for (int i = 0; i < keys.length; i++) {
         idx[i] = indexOf(keys[i]);
         if (idx[i] < 0) {
            throw new IllegalStateException("Unknown prop on wheel page " + key + ": " + keys[i]);
         }
      }

      return new PropShapes.ShapePage(key, idx);
   }

   public static PropShapes.Prop of(int index) {
      return PROPS[Math.floorMod(index, PROPS.length)];
   }

   public static int indexOf(String key) {
      for (int i = 0; i < PROPS.length; i++) {
         if (PROPS[i].key().equalsIgnoreCase(key)) {
            return i;
         }
      }

      return -1;
   }

   public static String nameKey(int index) {
      return "fantastic.prop." + of(index).key();
   }

   public static int pack(int form, int facing) {
      return form * 4 + Math.floorMod(facing, 4);
   }

   public static int formOf(int propIdx, int variant) {
      return Math.floorMod(Math.floorDiv(variant, 4), formCount(propIdx));
   }

   public static int facingOf(int propIdx, int variant) {
      return Math.floorMod(variant, 4) % Math.max(1, facingCount(propIdx));
   }

   public static int formCount(int propIdx) {
      return of(propIdx).forms().length;
   }

   public static int facingCount(int propIdx) {
      return Math.max(1, of(propIdx).facings());
   }

   private static boolean isCreaturePage(String key) {
      return "mobs".equals(key) || "monsters".equals(key);
   }

   public static boolean followsLook(int propIdx) {
      for (PropShapes.ShapePage page : SHAPE_PAGES) {
         if (isCreaturePage(page.key())) {
            for (int id : page.props()) {
               if (id == propIdx) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static int variantCount(int propIdx) {
      return formCount(propIdx) * 4;
   }

   public static PropShapes.Form formAt(int propIdx, int form) {
      PropShapes.Form[] fs = of(propIdx).forms();
      return fs[Math.floorMod(form, fs.length)];
   }

   public static PropShapes.Box[] boxesOf(int propIdx, int variant) {
      return formAt(propIdx, formOf(propIdx, variant)).boxes();
   }

   public static float yawOf(int propIdx, int variant) {
      return (float)facingOf(propIdx, variant) * 90.0F;
   }

   public static double boxXZOf(int propIdx, int variant) {
      PropShapes.Form f = formAt(propIdx, formOf(propIdx, variant));
      return f.boxXZ() > 0.0 ? f.boxXZ() : of(propIdx).boxXZ();
   }

   public static double boxYOf(int propIdx, int variant) {
      PropShapes.Form f = formAt(propIdx, formOf(propIdx, variant));
      return f.boxY() > 0.0 ? f.boxY() : of(propIdx).boxY();
   }

   public static boolean sameGeometry(int propIdx, int a, int b) {
      return formOf(propIdx, a) == formOf(propIdx, b);
   }

   public static String formKey(int propIdx, int form) {
      return "fantastic.propvar." + formAt(propIdx, form).key();
   }

   public static String facingKey(int facing) {
      return switch (Math.floorMod(facing, 4)) {
         case 1 -> "fantastic.propvar.east";
         case 2 -> "fantastic.propvar.south";
         case 3 -> "fantastic.propvar.west";
         default -> "fantastic.propvar.north";
      };
   }

   public static record Box(float x1, float y1, float z1, float x2, float y2, float z2, float yaw, boolean alpha) {
      public Box(float x1, float y1, float z1, float x2, float y2, float z2) {
         this(x1, y1, z1, x2, y2, z2, 0.0F, false);
      }

      public Box(float x1, float y1, float z1, float x2, float y2, float z2, float yaw) {
         this(x1, y1, z1, x2, y2, z2, yaw, false);
      }

      public static PropShapes.Box alpha(float x1, float y1, float z1, float x2, float y2, float z2, float yaw) {
         return new PropShapes.Box(x1, y1, z1, x2, y2, z2, yaw, true);
      }

      public float w() {
         return this.x2 - this.x1;
      }

      public float h() {
         return this.y2 - this.y1;
      }

      public float d() {
         return this.z2 - this.z1;
      }
   }

   public static record Form(String key, PropShapes.Box[] boxes, double boxXZ, double boxY) {
      public Form(String key, PropShapes.Box... boxes) {
         this(key, boxes, 0.0, 0.0);
      }

      public static PropShapes.Form sized(String key, double boxXZ, double boxY, PropShapes.Box... boxes) {
         return new PropShapes.Form(key, boxes, boxXZ, boxY);
      }
   }

   public static record Prop(String key, PropShapes.Form[] forms, int facings, double boxXZ, double boxY) {
      public PropShapes.Box[] boxes() {
         return this.forms[0].boxes();
      }
   }

   public static record ShapePage(String key, int[] props) {
   }
}
