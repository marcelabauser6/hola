package com.fantasticchameleon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PandaModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;

/**
 * Modelos de criatura vanilla usados por Prop Hunt.
 *
 * <p>No se reconstruyen cubos ni UV: cada modelo se hornea desde su {@link ModelLayers} oficial y se
 * dibuja con la textura que suministra el resource pack activo. De ese modo patas, cuernos, alas,
 * jerarquia y coordenadas 64x32 coinciden exactamente con el renderer de Minecraft.
 */
public final class VanillaPropModels {
   private static final float PLAYER_SCALE_FIX = 1.0666667F;
   private static final int FULL_BRIGHT = 15728640;
   private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
   private static Models models;

   static {
      TEXTURES.put("cow", minecraft("textures/entity/cow/cow.png"));
      TEXTURES.put("pig", minecraft("textures/entity/pig/pig.png"));
      TEXTURES.put("sheep", minecraft("textures/entity/sheep/sheep.png"));
      TEXTURES.put("chicken", minecraft("textures/entity/chicken.png"));
      TEXTURES.put("wolf", minecraft("textures/entity/wolf/wolf.png"));
      TEXTURES.put("panda", minecraft("textures/entity/panda/panda.png"));
      TEXTURES.put("creeper", minecraft("textures/entity/creeper/creeper.png"));
      TEXTURES.put("enderman", minecraft("textures/entity/enderman/enderman.png"));
   }

   private VanillaPropModels() {
   }

   public static boolean render(
      String key,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      AbstractClientPlayer player,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch,
      float actAge
   ) {
      ResourceLocation texture = TEXTURES.get(key);
      if (texture == null) {
         return false;
      }

      Models cache = getModels(player.m_9236_());
      pose.m_85836_();
      // PlayerRenderer reduce el modelo a 0.9375; los EntityRenderers vanilla no. Se revierte aqui.
      pose.m_85841_(PLAYER_SCALE_FIX, PLAYER_SCALE_FIX, PLAYER_SCALE_FIX);

      switch (key) {
         case "cow" -> renderEntity(cache.cow, cache.cowRoot, player, texture, pose, buffers, light,
            limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
         case "pig" -> renderEntity(cache.pig, cache.pigRoot, player, texture, pose, buffers, light,
            limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
         case "sheep" -> {
            renderEntity(cache.sheep, cache.sheepRoot, cache.sheepDriver, texture, pose, buffers, light,
               limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
            renderEntity(cache.sheepFur, cache.sheepFurRoot, cache.sheepDriver,
               minecraft("textures/entity/sheep/sheep_fur.png"), pose, buffers, light,
               limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
         }
         case "chicken" -> renderEntity(cache.chicken, cache.chickenRoot, player, texture, pose, buffers, light,
            limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
         case "wolf" -> renderEntity(cache.wolf, cache.wolfRoot, cache.wolfDriver, texture, pose, buffers, light,
            limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
         case "panda" -> renderEntity(cache.panda, cache.pandaRoot, cache.pandaDriver, texture, pose, buffers, light,
            limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
         case "creeper" -> renderEntity(cache.creeper, cache.creeperRoot, player, texture, pose, buffers, light,
            limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
         case "enderman" -> {
            renderEntity(cache.enderman, cache.endermanRoot, player, texture, pose, buffers, light,
               limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch, actAge, key);
            VertexConsumer eyes = buffers.m_6299_(RenderType.m_110488_(minecraft("textures/entity/enderman/enderman_eyes.png")));
            cache.enderman.m_7695_(pose, eyes, FULL_BRIGHT, OverlayTexture.f_118083_, 1.0F, 1.0F, 1.0F, 1.0F);
         }
         default -> {
            pose.m_85849_();
            return false;
         }
      }

      pose.m_85849_();
      return true;
   }

   private static <T extends Entity> void renderEntity(
      EntityModel<T> model,
      ModelPart root,
      T entity,
      ResourceLocation texture,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch,
      float actAge,
      String key
   ) {
      resetScale(root);
      model.f_102608_ = 0.0F;
      model.f_102609_ = false;
      model.f_102610_ = false;
      model.m_6839_(entity, limbSwing, limbSwingAmount, partialTick);
      model.m_6973_(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
      applyAct(root, key, ageInTicks, actAge);
      VertexConsumer vertices = buffers.m_6299_(model.m_103119_(texture));
      model.m_7695_(pose, vertices, light, OverlayTexture.f_118083_, 1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void resetScale(ModelPart root) {
      root.f_233553_ = 1.0F;
      root.f_233554_ = 1.0F;
      root.f_233555_ = 1.0F;
   }

   /** Conserva los gestos de la tecla V sin sustituir la animacion de marcha vanilla. */
   private static void applyAct(ModelPart root, String key, float ageInTicks, float actAge) {
      if (actAge < 0.0F || actAge >= 30.0F) {
         return;
      }

      float act = Mth.m_14031_((actAge / 30.0F) * (float)Math.PI);
      ModelPart head = child(root, "head");
      switch (key) {
         case "cow", "pig", "sheep", "panda" -> {
            if (head != null) {
               head.f_104203_ += act * (1.15F + 0.08F * Mth.m_14031_(actAge * 1.7F));
            }
         }
         case "chicken" -> {
            ModelPart rightWing = child(root, "right_wing");
            ModelPart leftWing = child(root, "left_wing");
            float flap = act * (0.8F + 0.35F * Mth.m_14031_(actAge * 2.2F));
            if (rightWing != null) rightWing.f_104205_ = flap;
            if (leftWing != null) leftWing.f_104205_ = -flap;
         }
         case "wolf" -> {
            ModelPart tail = child(root, "tail");
            if (tail != null) tail.f_104204_ = 0.6F * act * Mth.m_14031_(ageInTicks * 1.8F);
            if (head != null) head.f_104205_ = act * 0.35F;
         }
         case "creeper" -> {
            root.f_233553_ = 1.0F + act * 0.08F;
            root.f_233554_ = 1.0F + act * 0.03F;
            root.f_233555_ = 1.0F + act * 0.08F;
         }
         case "enderman" -> {
            ModelPart rightArm = child(root, "right_arm");
            ModelPart leftArm = child(root, "left_arm");
            if (rightArm != null) rightArm.f_104203_ = -1.35F * act;
            if (leftArm != null) leftArm.f_104203_ = -1.35F * act;
         }
      }
   }

   private static ModelPart child(ModelPart root, String name) {
      if (root.m_233562_(name)) {
         return root.m_171324_(name);
      }

      return null;
   }

   private static Models getModels(Level level) {
      EntityModelSet set = Minecraft.m_91087_().m_167973_();
      if (models == null || models.modelSet != set || models.level != level) {
         models = new Models(set, level);
      }
      return models;
   }

   public static void clearCache() {
      models = null;
   }

   private static ResourceLocation minecraft(String path) {
      return new ResourceLocation("minecraft", path);
   }

   private static final class Models {
      final EntityModelSet modelSet;
      final Level level;
      final ModelPart cowRoot;
      final CowModel<Entity> cow;
      final ModelPart pigRoot;
      final PigModel<Entity> pig;
      final ModelPart sheepRoot;
      final SheepModel<Sheep> sheep;
      final ModelPart sheepFurRoot;
      final SheepFurModel<Sheep> sheepFur;
      final ModelPart chickenRoot;
      final ChickenModel<Entity> chicken;
      final ModelPart wolfRoot;
      final WolfModel<Wolf> wolf;
      final ModelPart pandaRoot;
      final PandaModel<Panda> panda;
      final ModelPart creeperRoot;
      final CreeperModel<Entity> creeper;
      final ModelPart endermanRoot;
      final EndermanModel<LivingEntity> enderman;
      final Sheep sheepDriver;
      final Wolf wolfDriver;
      final Panda pandaDriver;

      Models(EntityModelSet set, Level level) {
         this.modelSet = set;
         this.level = level;
         this.cowRoot = set.m_171103_(ModelLayers.f_171284_);
         this.cow = new CowModel<>(this.cowRoot);
         this.pigRoot = set.m_171103_(ModelLayers.f_171205_);
         this.pig = new PigModel<>(this.pigRoot);
         this.sheepRoot = set.m_171103_(ModelLayers.f_171177_);
         this.sheep = new SheepModel<>(this.sheepRoot);
         this.sheepFurRoot = set.m_171103_(ModelLayers.f_171178_);
         this.sheepFur = new SheepFurModel<>(this.sheepFurRoot);
         this.chickenRoot = set.m_171103_(ModelLayers.f_171277_);
         this.chicken = new ChickenModel<>(this.chickenRoot);
         this.wolfRoot = set.m_171103_(ModelLayers.f_171221_);
         this.wolf = new WolfModel<>(this.wolfRoot);
         this.pandaRoot = set.m_171103_(ModelLayers.f_171202_);
         this.panda = new PandaModel<>(this.pandaRoot);
         this.creeperRoot = set.m_171103_(ModelLayers.f_171285_);
         this.creeper = new CreeperModel<>(this.creeperRoot);
         this.endermanRoot = set.m_171103_(ModelLayers.f_171142_);
         this.enderman = new EndermanModel<>(this.endermanRoot);
         this.sheepDriver = EntityType.f_20520_.m_20615_(level);
         this.wolfDriver = EntityType.f_20499_.m_20615_(level);
         this.pandaDriver = EntityType.f_20507_.m_20615_(level);
      }
   }
}
