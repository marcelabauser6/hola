package com.fantasticchameleon.client;

import com.fantasticchameleon.FantasticChameleon;
import com.fantasticchameleon.pose.PropShapes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class PropModels {
   public static final int TEX = 64;
   public static final float FEET_Y = 24.0F;
   private static final float PLAYER_SHRINK = 0.9375F;
   private static final float SIZE_FIX = 1.0666667F;
   private static final Map<Integer, ModelPart> CACHE = new HashMap<>();
   private static final Map<Integer, ModelPart> ALPHA_CACHE = new HashMap<>();
   private static final Set<Integer> NO_ALPHA = new HashSet<>();
   public static final int WOOL_V = 32;

   private PropModels() {
   }

   public static ModelPart part(int index, int variant) {
      int prop = Math.floorMod(index, PropShapes.PROPS.length);
      int var = Math.floorMod(variant, PropShapes.variantCount(prop));
      int key = prop * 64 + var;
      ModelPart hit = CACHE.get(key);
      if (hit != null) {
         return hit;
      } else {
         ModelPart built = PropShapes.followsLook(prop)
            ? buildMob(PropShapes.of(prop).key())
            : build(PropShapes.boxesOf(prop, var), PropShapes.yawOf(prop, var), false);
         CACHE.put(key, built);
         return built;
      }
   }

   public static ModelPart alphaPart(int index, int variant) {
      int prop = Math.floorMod(index, PropShapes.PROPS.length);
      int var = Math.floorMod(variant, PropShapes.variantCount(prop));
      int key = prop * 64 + var;
      if (NO_ALPHA.contains(key)) {
         return null;
      } else {
         ModelPart hit = ALPHA_CACHE.get(key);
         if (hit != null) {
            return hit;
         } else if (PropShapes.followsLook(prop)) {
            NO_ALPHA.add(key);
            return null;
         } else {
            boolean any = false;

            for (PropShapes.Box b : PropShapes.boxesOf(prop, var)) {
               any |= b.alpha();
            }

            if (!any) {
               NO_ALPHA.add(key);
               return null;
            } else {
               ModelPart built = build(PropShapes.boxesOf(prop, var), PropShapes.yawOf(prop, var), true);
               ALPHA_CACHE.put(key, built);
               return built;
            }
         }
      }
   }

   public static RenderType renderType(ResourceLocation canvasTexture) {
      return RenderType.m_110452_(canvasTexture);
   }

   /**
    * Anima el modelo de criatura con el mismo patrón de marcha alternada de los mobs vanilla. Los
    * ModelPart están cacheados y compartidos, por eso cada campo modificado se restablece en cada frame.
    */
   public static void animateMob(
      ModelPart root,
      String key,
      float limbSwing,
      float limbSwingAmount,
      float ageInTicks,
      float netHeadYaw,
      float headPitch,
      float actAge
   ) {
      float yaw = netHeadYaw * ((float)Math.PI / 180.0F);
      float pitch = headPitch * ((float)Math.PI / 180.0F);
      float amount = Math.min(1.0F, Math.max(0.0F, limbSwingAmount));
      float walkA = Mth.m_14031_(limbSwing * 0.6662F) * 1.4F * amount;
      float walkB = Mth.m_14031_(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * amount;
      boolean acting = actAge >= 0.0F && actAge < 30.0F;
      float act = acting ? Mth.m_14031_((actAge / 30.0F) * (float)Math.PI) : 0.0F;

      root.f_233553_ = SIZE_FIX;
      root.f_233554_ = SIZE_FIX;
      root.f_233555_ = SIZE_FIX;
      ModelPart head = child(root, "head");
      if (head != null) {
         head.f_104204_ = yaw;
         head.f_104203_ = pitch;
         head.f_104205_ = 0.0F;
      }

      switch (key) {
         case "cow", "pig", "sheep":
            quadrupedWalk(root, walkA, walkB);
            if (head != null) {
               // V simula pastar: bajar y cabecear sobre el suelo, no solo emitir partículas.
               head.f_104203_ = pitch + act * (1.15F + 0.08F * Mth.m_14031_(actAge * 1.7F));
            }
            if ("sheep".equals(key)) {
               copyRotation(root, "head", "wool_head");
               copyRotation(root, "right_hind_leg", "wool_right_hind_leg");
               copyRotation(root, "left_hind_leg", "wool_left_hind_leg");
               copyRotation(root, "right_front_leg", "wool_right_front_leg");
               copyRotation(root, "left_front_leg", "wool_left_front_leg");
            }
            break;
         case "chicken":
            setX(root, "right_leg", walkA);
            setX(root, "left_leg", walkB);
            float flap = acting ? act * (0.8F + 0.35F * Mth.m_14031_(actAge * 2.2F)) : amount * 0.18F * Mth.m_14031_(ageInTicks * 1.7F);
            setZ(root, "right_wing", flap);
            setZ(root, "left_wing", -flap);
            break;
         case "wolf":
            quadrupedWalk(root, walkA, walkB);
            ModelPart tail = child(root, "tail");
            if (tail != null) {
               tail.f_104203_ = (float)Math.PI / 5.0F;
               tail.f_104204_ = (0.25F + act * 0.6F) * Mth.m_14031_(ageInTicks * (acting ? 1.8F : 0.35F));
               tail.f_104205_ = 0.0F;
            }
            if (head != null) {
               head.f_104205_ = acting ? act * 0.35F : 0.0F;
            }
            break;
         case "creeper":
            quadrupedWalk(root, walkA, walkB);
            if (acting) {
               float swell = 1.0F + act * 0.08F;
               root.f_233553_ = SIZE_FIX * swell;
               root.f_233554_ = SIZE_FIX * (1.0F + act * 0.03F);
               root.f_233555_ = SIZE_FIX * swell;
            }
            break;
         case "enderman":
            float longWalkA = Math.max(-0.4F, Math.min(0.4F, walkA * 0.5F));
            float longWalkB = Math.max(-0.4F, Math.min(0.4F, walkB * 0.5F));
            copyRotation(root, "head", "hat");
            setX(root, "right_leg", longWalkA);
            setX(root, "left_leg", longWalkB);
            setX(root, "right_arm", acting ? -1.35F * act : longWalkB);
            setX(root, "left_arm", acting ? -1.35F * act : longWalkA);
            break;
      }
   }

   private static void quadrupedWalk(ModelPart root, float walkA, float walkB) {
      setX(root, "right_hind_leg", walkA);
      setX(root, "left_hind_leg", walkB);
      setX(root, "right_front_leg", walkB);
      setX(root, "left_front_leg", walkA);
   }

   private static ModelPart child(ModelPart root, String name) {
      return root.m_233562_(name) ? root.m_171324_(name) : null;
   }

   private static void setX(ModelPart root, String name, float value) {
      ModelPart part = child(root, name);
      if (part != null) {
         part.f_104203_ = value;
      }
   }

   private static void setZ(ModelPart root, String name, float value) {
      ModelPart part = child(root, name);
      if (part != null) {
         part.f_104205_ = value;
      }
   }

   private static void copyRotation(ModelPart root, String fromName, String toName) {
      ModelPart from = child(root, fromName);
      ModelPart to = child(root, toName);
      if (from != null && to != null) {
         to.f_104203_ = from.f_104203_;
         to.f_104204_ = from.f_104204_;
         to.f_104205_ = from.f_104205_;
      }
   }

   public static List<int[]> faceRects(int index, int variant) {
      int prop = Math.floorMod(index, PropShapes.PROPS.length);
      int var = Math.floorMod(variant, PropShapes.variantCount(prop));
      List<int[]> out = new ArrayList<>();
      int shelfX = 0;
      int shelfY = 0;
      int shelfHeight = 0;

      for (PropShapes.Box b : PropShapes.boxesOf(prop, var)) {
         int netW = (int)Math.ceil((double)(2.0F * (b.d() + b.w())));
         int netH = (int)Math.ceil((double)(b.d() + b.h()));
         if (shelfX + netW > 64) {
            shelfX = 0;
            shelfY += shelfHeight;
            shelfHeight = 0;
         }

         if (shelfY + netH > 64) {
            break;
         }

         int w = (int)Math.ceil((double)b.w());
         int h = (int)Math.ceil((double)b.h());
         int d = (int)Math.ceil((double)b.d());
         out.add(new int[]{shelfX + d, shelfY, w, d});
         out.add(new int[]{shelfX + d + w, shelfY, w, d});
         out.add(new int[]{shelfX, shelfY + d, d, h});
         out.add(new int[]{shelfX + d, shelfY + d, w, h});
         out.add(new int[]{shelfX + d + w, shelfY + d, d, h});
         out.add(new int[]{shelfX + d + w + d, shelfY + d, w, h});
         shelfX += netW;
         shelfHeight = Math.max(shelfHeight, netH);
      }

      return out;
   }

   public static List<int[][]> cutoutFacePairs(int index, int variant) {
      int prop = Math.floorMod(index, PropShapes.PROPS.length);
      int var = Math.floorMod(variant, PropShapes.variantCount(prop));
      List<int[][]> out = new ArrayList<>();
      if (PropShapes.followsLook(prop)) {
         return out;
      } else {
         int shelfX = 0;
         int shelfY = 0;
         int shelfHeight = 0;

         for (PropShapes.Box b : PropShapes.boxesOf(prop, var)) {
            int netW = (int)Math.ceil((double)(2.0F * (b.d() + b.w())));
            int netH = (int)Math.ceil((double)(b.d() + b.h()));
            if (shelfX + netW > 64) {
               shelfX = 0;
               shelfY += shelfHeight;
               shelfHeight = 0;
            }

            if (shelfY + netH > 64) {
               break;
            }

            if (b.alpha()) {
               int w = (int)Math.ceil((double)b.w());
               int h = (int)Math.ceil((double)b.h());
               int d = (int)Math.ceil((double)b.d());
               out.add(new int[][]{{shelfX + d, shelfY + d, w, h}, {shelfX + d + w + d, shelfY + d, w, h}});
            }

            shelfX += netW;
            shelfHeight = Math.max(shelfHeight, netH);
         }

         return out;
      }
   }

   private static ModelPart buildMob(String key) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.m_171576_();
      float yFix = "enderman".equals(key) ? -1.0F : 0.0F;
      PartDefinition prop = root.m_171599_("prop", CubeListBuilder.m_171558_(), PartPose.m_171419_(0.0F, -1.6000013F + yFix, 0.0F));
      switch (key) {
         case "cow":
            prop.m_171599_(
               "head",
               CubeListBuilder.m_171558_()
                  .m_171514_(0, 0)
                  .m_171481_(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F)
                  .m_171514_(22, 0)
                  .m_171517_("right_horn", -5.0F, -5.0F, -4.0F, 1.0F, 3.0F, 1.0F)
                  .m_171514_(22, 0)
                  .m_171517_("left_horn", 4.0F, -5.0F, -4.0F, 1.0F, 3.0F, 1.0F),
               PartPose.m_171419_(0.0F, 4.0F, -8.0F)
            );
            prop.m_171599_(
               "body",
               CubeListBuilder.m_171558_()
                  .m_171514_(18, 4)
                  .m_171481_(-6.0F, -10.0F, -7.0F, 12.0F, 18.0F, 10.0F)
                  .m_171514_(52, 0)
                  .m_171481_(-2.0F, 2.0F, -8.0F, 4.0F, 6.0F, 1.0F),
               PartPose.m_171423_(0.0F, 5.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
            );
            legs(
               prop,
               12,
               CubeListBuilder.m_171558_().m_171514_(0, 16).m_171481_(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
               CubeListBuilder.m_171558_().m_171514_(0, 16).m_171481_(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
               4.0F,
               7.0F,
               -5.0F
            );
            break;
         case "pig":
            prop.m_171599_(
               "head",
               CubeListBuilder.m_171558_()
                  .m_171514_(0, 0)
                  .m_171481_(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F)
                  .m_171514_(16, 16)
                  .m_171481_(-2.0F, 0.0F, -9.0F, 4.0F, 3.0F, 1.0F),
               PartPose.m_171419_(0.0F, 12.0F, -6.0F)
            );
            quadrupedBody(prop, 6, false, false);
            break;
         case "sheep":
            prop.m_171599_(
               "head", CubeListBuilder.m_171558_().m_171514_(0, 0).m_171481_(-3.0F, -4.0F, -6.0F, 6.0F, 6.0F, 8.0F), PartPose.m_171419_(0.0F, 6.0F, -8.0F)
            );
            prop.m_171599_(
               "body",
               CubeListBuilder.m_171558_().m_171514_(28, 8).m_171481_(-4.0F, -10.0F, -7.0F, 8.0F, 16.0F, 6.0F),
               PartPose.m_171423_(0.0F, 5.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
            );
            quadrupedLegs(prop, 12, false, false);
            sheepFur(prop);
            break;
         case "chicken": {
            PartDefinition head = prop.m_171599_(
               "head", CubeListBuilder.m_171558_().m_171514_(0, 0).m_171481_(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 3.0F), PartPose.m_171419_(0.0F, 15.0F, -4.0F)
            );
            head.m_171599_("beak", CubeListBuilder.m_171558_().m_171514_(14, 0).m_171481_(-2.0F, -4.0F, -4.0F, 4.0F, 2.0F, 2.0F), PartPose.f_171404_);
            head.m_171599_("wattle", CubeListBuilder.m_171558_().m_171514_(14, 4).m_171481_(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 2.0F), PartPose.f_171404_);
            prop.m_171599_(
               "body",
               CubeListBuilder.m_171558_().m_171514_(0, 9).m_171481_(-3.0F, -4.0F, -3.0F, 6.0F, 8.0F, 6.0F),
               PartPose.m_171423_(0.0F, 16.0F, 0.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
            );
            CubeListBuilder leg = CubeListBuilder.m_171558_().m_171514_(26, 0).m_171481_(-1.0F, 0.0F, -3.0F, 3.0F, 5.0F, 3.0F);
            prop.m_171599_("right_leg", leg, PartPose.m_171419_(-2.0F, 19.0F, 1.0F));
            prop.m_171599_("left_leg", leg, PartPose.m_171419_(1.0F, 19.0F, 1.0F));
            prop.m_171599_(
               "right_wing",
               CubeListBuilder.m_171558_().m_171514_(24, 13).m_171481_(0.0F, 0.0F, -3.0F, 1.0F, 4.0F, 6.0F),
               PartPose.m_171419_(-4.0F, 13.0F, 0.0F)
            );
            prop.m_171599_(
               "left_wing",
               CubeListBuilder.m_171558_().m_171514_(24, 13).m_171481_(-1.0F, 0.0F, -3.0F, 1.0F, 4.0F, 6.0F),
               PartPose.m_171419_(4.0F, 13.0F, 0.0F)
            );
            break;
         }
         case "wolf":
            prop.m_171599_(
               "head",
               CubeListBuilder.m_171558_()
                  .m_171514_(0, 0)
                  .m_171481_(-2.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F)
                  .m_171514_(16, 14)
                  .m_171481_(-2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F)
                  .m_171514_(16, 14)
                  .m_171481_(2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F)
                  .m_171514_(0, 10)
                  .m_171481_(-0.5F, -0.001F, -5.0F, 3.0F, 3.0F, 4.0F),
               PartPose.m_171419_(-1.0F, 13.5F, -7.0F)
            );
            prop.m_171599_(
               "body",
               CubeListBuilder.m_171558_().m_171514_(18, 14).m_171481_(-3.0F, -2.0F, -3.0F, 6.0F, 9.0F, 6.0F),
               PartPose.m_171423_(0.0F, 14.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
            );
            prop.m_171599_(
               "upper_body",
               CubeListBuilder.m_171558_().m_171514_(21, 0).m_171481_(-3.0F, -3.0F, -3.0F, 8.0F, 6.0F, 7.0F),
               PartPose.m_171423_(-1.0F, 14.0F, -3.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
            );
            CubeListBuilder left = CubeListBuilder.m_171558_().m_171514_(0, 18).m_171481_(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F);
            CubeListBuilder right = CubeListBuilder.m_171558_().m_171514_(0, 18).m_171481_(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F);
            prop.m_171599_("right_hind_leg", right, PartPose.m_171419_(-2.5F, 16.0F, 7.0F));
            prop.m_171599_("left_hind_leg", left, PartPose.m_171419_(0.5F, 16.0F, 7.0F));
            prop.m_171599_("right_front_leg", right, PartPose.m_171419_(-2.5F, 16.0F, -4.0F));
            prop.m_171599_("left_front_leg", left, PartPose.m_171419_(0.5F, 16.0F, -4.0F));
            prop.m_171599_(
               "tail",
               CubeListBuilder.m_171558_().m_171514_(9, 18).m_171481_(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
               PartPose.m_171423_(-1.0F, 12.0F, 8.0F, (float) (Math.PI / 5), 0.0F, 0.0F)
            );
            break;
         case "creeper": {
            prop.m_171599_(
               "head", CubeListBuilder.m_171558_().m_171514_(0, 0).m_171481_(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.m_171419_(0.0F, 6.0F, 0.0F)
            );
            prop.m_171599_(
               "body", CubeListBuilder.m_171558_().m_171514_(16, 16).m_171481_(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F), PartPose.m_171419_(0.0F, 6.0F, 0.0F)
            );
            CubeListBuilder leg = CubeListBuilder.m_171558_().m_171514_(0, 16).m_171481_(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F);
            prop.m_171599_("right_hind_leg", leg, PartPose.m_171419_(-2.0F, 18.0F, 4.0F));
            prop.m_171599_("left_hind_leg", leg, PartPose.m_171419_(2.0F, 18.0F, 4.0F));
            prop.m_171599_("right_front_leg", leg, PartPose.m_171419_(-2.0F, 18.0F, -4.0F));
            prop.m_171599_("left_front_leg", leg, PartPose.m_171419_(2.0F, 18.0F, -4.0F));
            break;
         }
         case "enderman":
            prop.m_171599_(
               "head", CubeListBuilder.m_171558_().m_171514_(0, 0).m_171481_(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.m_171419_(0.0F, -13.0F, 0.0F)
            );
            prop.m_171599_(
               "hat",
               CubeListBuilder.m_171558_().m_171514_(0, 16).m_171488_(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(-0.5F)),
               PartPose.m_171419_(0.0F, -13.0F, 0.0F)
            );
            prop.m_171599_(
               "body", CubeListBuilder.m_171558_().m_171514_(32, 16).m_171481_(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F), PartPose.m_171419_(0.0F, -14.0F, 0.0F)
            );
            CubeListBuilder limb = CubeListBuilder.m_171558_().m_171514_(56, 0).m_171481_(-1.0F, -2.0F, -1.0F, 2.0F, 30.0F, 2.0F);
            CubeListBuilder limbMirror = CubeListBuilder.m_171558_().m_171480_().m_171514_(56, 0).m_171481_(-1.0F, -2.0F, -1.0F, 2.0F, 30.0F, 2.0F);
            prop.m_171599_("right_arm", limb, PartPose.m_171419_(-5.0F, -12.0F, 0.0F));
            prop.m_171599_("left_arm", limbMirror, PartPose.m_171419_(5.0F, -12.0F, 0.0F));
            prop.m_171599_(
               "right_leg",
               CubeListBuilder.m_171558_().m_171514_(56, 0).m_171481_(-1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F),
               PartPose.m_171419_(-2.0F, -5.0F, 0.0F)
            );
            prop.m_171599_(
               "left_leg",
               CubeListBuilder.m_171558_().m_171480_().m_171514_(56, 0).m_171481_(-1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F),
               PartPose.m_171419_(2.0F, -5.0F, 0.0F)
            );
      }

      ModelPart part = LayerDefinition.m_171565_(mesh, 64, 64).m_171564_().m_171324_("prop");
      part.f_233553_ = 1.0666667F;
      part.f_233554_ = 1.0666667F;
      part.f_233555_ = 1.0666667F;
      return part;
   }

   private static void quadrupedBody(PartDefinition prop, int legSize, boolean mirrorLeft, boolean mirrorRight) {
      prop.m_171599_(
         "body",
         CubeListBuilder.m_171558_().m_171514_(28, 8).m_171481_(-5.0F, -10.0F, -7.0F, 10.0F, 16.0F, 8.0F),
         PartPose.m_171423_(0.0F, (float)(17 - legSize), 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
      );
      quadrupedLegs(prop, legSize, mirrorLeft, mirrorRight);
   }

   private static void quadrupedLegs(PartDefinition prop, int legSize, boolean mirrorLeft, boolean mirrorRight) {
      CubeListBuilder right = CubeListBuilder.m_171558_().m_171555_(mirrorRight).m_171514_(0, 16).m_171481_(-2.0F, 0.0F, -2.0F, 4.0F, (float)legSize, 4.0F);
      CubeListBuilder left = CubeListBuilder.m_171558_().m_171555_(mirrorLeft).m_171514_(0, 16).m_171481_(-2.0F, 0.0F, -2.0F, 4.0F, (float)legSize, 4.0F);
      legs(prop, legSize, right, left, 3.0F, 7.0F, -5.0F);
   }

   private static void legs(PartDefinition prop, int legSize, CubeListBuilder right, CubeListBuilder left, float x, float hindZ, float frontZ) {
      float y = (float)(24 - legSize);
      prop.m_171599_("right_hind_leg", right, PartPose.m_171419_(-x, y, hindZ));
      prop.m_171599_("left_hind_leg", left, PartPose.m_171419_(x, y, hindZ));
      prop.m_171599_("right_front_leg", right, PartPose.m_171419_(-x, y, frontZ));
      prop.m_171599_("left_front_leg", left, PartPose.m_171419_(x, y, frontZ));
   }

   private static void sheepFur(PartDefinition prop) {
      prop.m_171599_(
         "wool_head",
         CubeListBuilder.m_171558_().m_171514_(0, 32).m_171488_(-3.0F, -4.0F, -4.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.6F)),
         PartPose.m_171419_(0.0F, 6.0F, -8.0F)
      );
      prop.m_171599_(
         "wool_body",
         CubeListBuilder.m_171558_().m_171514_(28, 40).m_171488_(-4.0F, -10.0F, -7.0F, 8.0F, 16.0F, 6.0F, new CubeDeformation(1.75F)),
         PartPose.m_171423_(0.0F, 5.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
      );
      CubeListBuilder leg = CubeListBuilder.m_171558_().m_171514_(0, 48).m_171488_(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.5F));
      prop.m_171599_("wool_right_hind_leg", leg, PartPose.m_171419_(-3.0F, 12.0F, 7.0F));
      prop.m_171599_("wool_left_hind_leg", leg, PartPose.m_171419_(3.0F, 12.0F, 7.0F));
      prop.m_171599_("wool_right_front_leg", leg, PartPose.m_171419_(-3.0F, 12.0F, -5.0F));
      prop.m_171599_("wool_left_front_leg", leg, PartPose.m_171419_(3.0F, 12.0F, -5.0F));
   }

   private static ModelPart build(PropShapes.Box[] boxes, float yawDegrees, boolean wantAlpha) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.m_171576_();
      CubeListBuilder cubes = CubeListBuilder.m_171558_();
      Map<Float, CubeListBuilder> turned = new LinkedHashMap<>();
      int shelfX = 0;
      int shelfY = 0;
      int shelfHeight = 0;

      for (PropShapes.Box b : boxes) {
         int netW = (int)Math.ceil((double)(2.0F * (b.d() + b.w())));
         int netH = (int)Math.ceil((double)(b.d() + b.h()));
         if (shelfX + netW > 64) {
            shelfX = 0;
            shelfY += shelfHeight;
            shelfHeight = 0;
         }

         if (shelfY + netH > 64) {
            FantasticChameleon.LOGGER.error("[Fantastic] Prop UV overflow: a form needs more than {}px of canvas height.", 64);
            break;
         }

         if (b.alpha() != wantAlpha) {
            shelfX += netW;
            shelfHeight = Math.max(shelfHeight, netH);
         } else {
            CubeListBuilder into = b.yaw() == 0.0F ? cubes : turned.computeIfAbsent(b.yaw(), y -> CubeListBuilder.m_171558_());
            into.m_171514_(shelfX, shelfY);
            into.m_171481_(b.x1() - 8.0F, 24.0F - b.y2(), b.z1() - 8.0F, b.w(), b.h(), b.d());
            shelfX += netW;
            shelfHeight = Math.max(shelfHeight, netH);
         }
      }

      PartDefinition prop = root.m_171599_("prop", cubes, PartPose.m_171423_(0.0F, -1.6000013F, 0.0F, 0.0F, yawDegrees * (float) (Math.PI / 180.0), 0.0F));
      int i = 0;

      for (Entry<Float, CubeListBuilder> e : turned.entrySet()) {
         prop.m_171599_("turn" + i++, e.getValue(), PartPose.m_171430_(0.0F, e.getKey() * (float) (Math.PI / 180.0), 0.0F));
      }

      ModelPart part = LayerDefinition.m_171565_(mesh, 64, 64).m_171564_().m_171324_("prop");
      part.f_233553_ = 1.0666667F;
      part.f_233554_ = 1.0666667F;
      part.f_233555_ = 1.0666667F;
      return part;
   }
}
