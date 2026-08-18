package com.fantasticchameleon.client;

import com.fantasticchameleon.paint.BodyPart;
import com.fantasticchameleon.prophunt.PropHuntClient;
import com.fantasticchameleon.pose.PropShapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;

public class BodyPaintLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final int MODEL_CACHE_MAX = 192;
   private final boolean slim;
   private final Map<UUID, PlayerModel<AbstractClientPlayer>> models = new LinkedHashMap<>(16, 0.75F, true);

   public BodyPaintLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
      super(parent);
      this.slim = slim;
   }

   private PlayerModel<AbstractClientPlayer> modelFor(UUID id) {
      if (this.models.size() > 192) {
         Iterator<UUID> it = this.models.keySet().iterator();
         it.next();
         it.remove();
      }

      return this.models.computeIfAbsent(id, k -> {
         ModelPart root = Minecraft.m_91087_().m_167973_().m_171103_(this.slim ? ModelLayers.f_171166_ : ModelLayers.f_171162_);
         return new PlayerModel(root, this.slim);
      });
   }

   public void m_6494_(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      AbstractClientPlayer player,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      BodyCanvasData data = AvatarState.canvas(player);
      if (data != null && data.enabledMask() != 0 && !player.m_20145_()) {
         int propIdx = AvatarState.prop(player);
         if (propIdx >= 0) {
            this.renderProp(
               pose, buffers, light, data, player, propIdx, AvatarState.propVariant(player),
               limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch
            );
         } else {
            int canvasSize = data.canvas().size();
            ResourceLocation canvas = PlayerCanvasTextures.update(data.uuid(), data.canvas(), data.enabledMask());
            RenderType type = RenderType.m_110464_(canvas);
            VertexConsumer buffer = buffers.m_6299_(type);
            PlayerModel<AbstractClientPlayer> model = this.modelFor(data.uuid());
            ((PlayerModel)this.m_117386_()).m_102872_(model);
            model.m_6973_(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            model.f_102809_.f_104207_ = true;
            model.f_103378_.f_104207_ = true;
            model.f_103374_.f_104207_ = true;
            model.f_103375_.f_104207_ = true;
            model.f_103376_.f_104207_ = true;
            model.f_103377_.f_104207_ = true;
            boolean capture = BodyPaint.shouldCapture(data.uuid());
            boolean overlay = BodyPaint.captureLayer();
            if (capture) {
               BodyPaint.beginCapture();
            }

            boolean captureBase = capture && !overlay;
            renderPart(buffer, model.f_102808_, BodyPart.HEAD, data, pose, light, captureBase, canvasSize);
            renderPart(buffer, model.f_102810_, BodyPart.BODY, data, pose, light, captureBase, canvasSize);
            renderPart(buffer, model.f_102812_, BodyPart.LEFT_ARM, data, pose, light, captureBase, canvasSize);
            renderPart(buffer, model.f_102811_, BodyPart.RIGHT_ARM, data, pose, light, captureBase, canvasSize);
            renderLeg(buffer, model.f_102814_, BodyPart.LEFT_LEG, BodyPart.LEFT_LEG_LOWER, data, pose, light, captureBase, canvasSize);
            renderLeg(buffer, model.f_102813_, BodyPart.RIGHT_LEG, BodyPart.RIGHT_LEG_LOWER, data, pose, light, captureBase, canvasSize);
            boolean captureOverlay = capture && overlay;
            renderPart(buffer, model.f_102809_, BodyPart.HEAD, data, pose, light, captureOverlay, canvasSize);
            renderPart(buffer, model.f_103378_, BodyPart.BODY, data, pose, light, captureOverlay, canvasSize);
            renderPart(buffer, model.f_103374_, BodyPart.LEFT_ARM, data, pose, light, captureOverlay, canvasSize);
            renderPart(buffer, model.f_103375_, BodyPart.RIGHT_ARM, data, pose, light, captureOverlay, canvasSize);
            renderLeg(buffer, model.f_103376_, BodyPart.LEFT_LEG, BodyPart.LEFT_LEG_LOWER, data, pose, light, captureOverlay, canvasSize);
            renderLeg(buffer, model.f_103377_, BodyPart.RIGHT_LEG, BodyPart.RIGHT_LEG_LOWER, data, pose, light, captureOverlay, canvasSize);
            if (capture) {
               BodyPaint.endCapture();
            }
         }
      }
   }

   private void renderProp(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      BodyCanvasData data,
      AbstractClientPlayer player,
      int propIdx,
      int propVariant,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      Vec3 moveOff = MoveGizmo.renderOffset(data.uuid());
      boolean shifted = moveOff.m_82556_() > 0.0;
      if (shifted) {
         pose.m_85836_();
         pose.m_252880_((float)moveOff.f_82479_, (float)moveOff.f_82480_, (float)moveOff.f_82481_);
      }

      if (!PropShapes.followsLook(propIdx)) {
         BlockState state = PropHuntClient.stateFor(player);
         if (state != null) {
            renderExactBlock(pose, buffers, player, state, partialTick, light);
         } else {
            // Props elegidos desde la UI Meccha y previews no representan un bloque capturado. Se
            // conserva su renderer de cajas/lienzo para no volverlos invisibles ni reutilizar estado.
            renderCanvasBlock(pose, buffers, light, data, propIdx, propVariant);
         }
      } else {
         ResourceLocation propTex = PropCanvasTextures.update(data.uuid(), data.canvas());
         ModelPart propPart = PropModels.part(propIdx, propVariant);
         PropModels.animateMob(
            propPart, PropShapes.of(propIdx).key(), limbSwing, limbSwingAmount, ageInTicks,
            netHeadYaw, headPitch, AvatarState.propActAge(player, partialTick)
         );
         propPart.m_104301_(pose, buffers.m_6299_(RenderType.m_110464_(propTex)), light, OverlayTexture.f_118083_);

         if (BodyPaint.shouldCapture(data.uuid())) {
            BodyPaint.beginCapture();
            BodyPaint.captureProp(propPart, pose, 64);
            BodyPaint.endCapture();
         }
      }

      if (shifted) {
         pose.m_85849_();
      }
   }

   private static void renderCanvasBlock(
      PoseStack pose, MultiBufferSource buffers, int light, BodyCanvasData data, int propIdx, int propVariant
   ) {
      ResourceLocation propTex = PropCanvasTextures.update(data.uuid(), data.canvas());
      ModelPart propPart = PropModels.part(propIdx, propVariant);
      propPart.m_104301_(pose, buffers.m_6299_(RenderType.m_110464_(propTex)), light, OverlayTexture.f_118083_);
      ModelPart alphaPart = PropModels.alphaPart(propIdx, propVariant);
      if (alphaPart != null) {
         ResourceLocation alphaTex = PropCanvasTextures.updateAlpha(data.uuid(), data.canvas());
         alphaPart.m_104301_(pose, buffers.m_6299_(RenderType.m_110452_(alphaTex)), light, OverlayTexture.f_118083_);
      }

      if (BodyPaint.shouldCapture(data.uuid())) {
         BodyPaint.beginCapture();
         BodyPaint.captureProp(propPart, pose, 64);
         if (alphaPart != null) {
            BodyPaint.captureProp(alphaPart, pose, 64);
         }
         BodyPaint.endCapture();
      }
   }

   /**
    * Dibuja el BakedModel real del estado capturado, con sus quads, tintes de bioma, AO, capas y
    * resource pack. No recompone una textura aproximada: usa exactamente el mismo renderer del mundo.
    */
   private static void renderExactBlock(
      PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, BlockState state, float partialTick, int light
   ) {
      pose.m_85836_();

      // PlayerRenderer deja la matriz en espacio de modelo (escala 0.9375 e Y invertida). Esta
      // transformación la devuelve a unidades de mundo: una caja 0..1 ocupa exactamente una celda.
      pose.m_252880_(0.0F, 1.501F, 0.0F);
      pose.m_85841_(-1.0666667F, -1.0666667F, 1.0666667F);

      // setupRotations ya aplicó 180-bodyYaw. Se neutraliza para que facing/axis del BlockState siga
      // apuntando al mismo eje mundial que el bloque tocado, incluso mientras el jugador camina.
      float bodyYaw = AvatarState.locked(player)
         ? AvatarState.lockYaw(player)
         : Mth.m_14189_(partialTick, player.f_20884_, player.f_20883_);
      pose.m_252781_(Axis.f_252436_.m_252977_(bodyYaw - 180.0F));
      pose.m_252880_(-0.5F, 0.0F, -0.5F);

      if (state.m_60799_() == RenderShape.MODEL) {
         RenderType layer = ItemBlockRenderTypes.m_109282_(state);
         Minecraft.m_91087_().m_91289_().m_110918_(
            state, player.m_20183_(), player.m_9236_(), pose, buffers.m_6299_(layer)
         );
      } else {
         // Cofres/cabezas y otros ENTITYBLOCK_ANIMATED no tienen quads de mundo; se conserva el
         // renderer vanilla de ítem como fallback seguro en vez de mostrar un cubo blanco.
         Minecraft.m_91087_().m_91289_().m_110912_(state, pose, buffers, light, OverlayTexture.f_118083_);
      }

      pose.m_85849_();
   }

   private static void renderPart(
      VertexConsumer buffer, ModelPart part, BodyPart bodyPart, BodyCanvasData data, PoseStack pose, int light, boolean capture, int canvasSize
   ) {
      if (data.isPartEnabled(bodyPart.ordinal()) && part.f_104207_) {
         part.m_104301_(pose, buffer, light, OverlayTexture.f_118083_);
         if (capture) {
            BodyPaint.capturePart(part, pose, canvasSize);
         }
      }
   }

   private static void renderLeg(
      VertexConsumer buffer, ModelPart part, BodyPart upper, BodyPart lower, BodyCanvasData data, PoseStack pose, int light, boolean capture, int canvasSize
   ) {
      if ((data.isPartEnabled(upper.ordinal()) || data.isPartEnabled(lower.ordinal())) && part.f_104207_) {
         part.m_104301_(pose, buffer, light, OverlayTexture.f_118083_);
         if (capture) {
            BodyPaint.capturePart(part, pose, canvasSize);
         }
      }
   }
}
