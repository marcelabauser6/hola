package com.fantasticchameleon.client;

import com.fantasticchameleon.item.ArmorPaintHandler;
import com.fantasticchameleon.network.ClimbPayload;
import com.fantasticchameleon.network.CrawlPayload;
import com.fantasticchameleon.network.DetachPropPayload;
import com.fantasticchameleon.network.LockPayload;
import com.fantasticchameleon.network.PosePayload;
import com.fantasticchameleon.network.PropActPayload;
import com.fantasticchameleon.network.SetPropPayload;
import com.fantasticchameleon.network.ProvokePayload;
import com.fantasticchameleon.paint.FrozenFrame;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.ClientNet;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.prophunt.PropHuntClient;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class LockControls {
   public static KeyMapping poseWheelKey;
   /** Gesto de criatura en Prop Hunt: pastar, mugir, sisear. */
   public static KeyMapping actKey;
   public static KeyMapping paintKey;
   public static KeyMapping provokeKey;
   public static KeyMapping crawlKey;
   private static boolean climbJumpSent;
   private static boolean climbSneakSent;
   public static final String CATEGORY = "key.categories.fantastic_chameleon";
   private static boolean paintWasDown;
   private static boolean paintScreenWasOpen;
   private static boolean spaceWasDown;
   private static boolean sneakWasDown;
   private static boolean wasPosing;
   private static boolean wasLocked;
   private static float lastScale = 1.0F;

   private LockControls() {
   }

   private static boolean rawKeyDown(KeyMapping k) {
      Key key = ClientAccessors.boundKey(k);
      if (key.m_84868_() != Type.KEYSYM) {
         return k.m_90857_();
      } else {
         int code = key.m_84873_();
         return code >= 0 && InputConstants.m_84830_(Minecraft.m_91087_().m_91268_().m_85439_(), code);
      }
   }

   public static List<KeyMapping> registerKeyMappings() {
      poseWheelKey = new KeyMapping("key.fantastic_chameleon.pose", 82, "key.categories.fantastic_chameleon");
      actKey = new KeyMapping("key.fantastic_chameleon.act", 86, "key.categories.fantastic_chameleon");
      paintKey = new KeyMapping("key.fantastic_chameleon.paint", 70, "key.categories.fantastic_chameleon");
      provokeKey = new KeyMapping("key.fantastic_chameleon.provoke", 71, "key.categories.fantastic_chameleon");
      crawlKey = new KeyMapping("key.fantastic_chameleon.crawl", 67, "key.categories.fantastic_chameleon");
      return List.of(poseWheelKey, actKey, paintKey, provokeKey, crawlKey);
   }

   public static void forceExitFromServer() {
      Minecraft mc = Minecraft.m_91087_();
      if (ClientShims.screen(mc) instanceof PaintModeScreen
         || ClientShims.screen(mc) instanceof PoseWheelScreen
         || ClientShims.screen(mc) instanceof PropHuntLockedScreen) {
         mc.m_91152_(null);
      }

      exit(mc);
   }

   public static void lock(Minecraft mc) {
      if (mc.f_91074_ != null) {
         float bodyYaw = mc.f_91074_.f_20883_;
         boolean freeze = !Services.PLATFORM.get(mc.f_91074_, PaintAttachments.POSING);
         FrozenFrame frame = FrozenFrame.NONE;
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.LOCKED, true);
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSING, true);
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.LOCK_YAW, bodyYaw);
         RotateGizmo.rememberLockYaw(bodyYaw);
         if (freeze) {
            frame = new FrozenFrame(
               mc.f_91074_.m_6080_(),
               mc.f_91074_.m_146909_(),
               mc.f_91074_.f_267362_.m_267756_(),
               mc.f_91074_.f_267362_.m_267731_(),
               (float)mc.f_91074_.f_19797_
            );
            Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSE, -1);
            Services.PLATFORM.set(mc.f_91074_, PaintAttachments.FROZEN_FRAME, frame);
         }

         ClientNet.sendToServer(new LockPayload(true, bodyYaw, freeze, frame));
      }
   }

   /** Libera movimiento y ancla, pero conserva exactamente la transformación de Prop Hunt. */
   public static void detach(Minecraft mc) {
      if (mc.f_91074_ != null) {
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSING, false);
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.LOCKED, false);
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSE, 0);
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.FROZEN_FRAME, FrozenFrame.NONE);
         ClientNet.sendToServer(DetachPropPayload.INSTANCE);
         // Devolver el control aquí mismo: dejar noClip puesto hacía que el cuerpo se quedara clavado
         // hasta la siguiente corrección del servidor.
         mc.f_91074_.f_19794_ = false;
         mc.f_91074_.m_20256_(Vec3.f_82478_);
         mc.f_91074_.f_19789_ = 0.0F;
         mc.f_91074_.m_6210_();
         FreeCam.disable();
         WorldBrush.exitPaintMode();
         ClientPaintState.clearHidden();
         Gizmos.hide();
         if (ClientShims.screen(mc) instanceof PropHuntLockedScreen) {
            mc.m_91152_(null);
         }
      }
   }

   /** Revierte por completo el bloque o mob actual; es deliberadamente distinto de desacoplar. */
   public static void revertProp(Minecraft mc) {
      if (mc.f_91074_ != null) {
         ClientNet.sendToServer(new SetPropPayload(-1, 0));
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.LOCKED, false);
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSING, false);
         if (ClientShims.screen(mc) instanceof PropHuntLockedScreen) {
            mc.m_91152_(null);
         }
      }
   }

   public static void exit(Minecraft mc) {
      if (mc.f_91074_ != null) {
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSING, false);
         Services.PLATFORM.set(mc.f_91074_, PaintAttachments.LOCKED, false);
         ClientNet.sendToServer(new LockPayload(false, mc.f_91074_.f_20883_, false, FrozenFrame.NONE));
         ClientNet.sendToServer(new PosePayload(false, 0));
         FreeCam.disable();
         WorldBrush.exitPaintMode();
         ClientPaintState.clearHidden();
         ClientPaintState.setPaintGrid(0);
         Gizmos.hide();
      }
   }

   public static boolean onPauseScreenOpening(Screen newScreen) {
      Minecraft mc = Minecraft.m_91087_();
      if (!(newScreen instanceof PauseScreen) || ClientShims.screen(mc) != null) {
         return false;
      } else if (mc.f_91074_ == null) {
         return false;
      } else {
         boolean locked = Services.PLATFORM.get(mc.f_91074_, PaintAttachments.LOCKED);
         boolean posing = Services.PLATFORM.get(mc.f_91074_, PaintAttachments.POSING);
         boolean attached = Boolean.TRUE.equals(Services.PLATFORM.getOrNull(mc.f_91074_, PaintAttachments.ATTACHED));
         if (FreeCam.active() && attached) {
            // Adherido el cuerpo queda metido en el bloque, así que apagar aquí la cámara dejaba la
            // vista dentro de la geometría, a oscuras y sin salida. Esc abre el menú normal y la
            // cámara se conserva fuera; para soltarse están espacio, agacharse y el panel.
            return false;
         } else if (FreeCam.active()) {
            MecchaClientEvents.suppressFreeCam();
            FreeCam.disable();
            WorldBrush.exitPaintMode();
            return true;
         } else if (posing && !locked) {
            lock(mc);
            return true;
         } else {
            return false;
         }
      }
   }

   private static void syncShrink(Minecraft mc) {
      if (mc.f_91074_ != null) {
         float now = ArmorPaintHandler.scaleOf(mc.f_91074_);
         if ((double)Math.abs(now - lastScale) > 1.0E-4) {
            lastScale = now;
            mc.f_91074_.m_6210_();
         }
      }
   }

   public static void onClientTick(Minecraft mc) {
      syncShrink(mc);
      if (mc.f_91074_ != null
         && PropHuntClient.isPropHunt()
         && Services.PLATFORM.get(mc.f_91074_, PaintAttachments.LOCKED)
         && !PropHuntClient.canUseProp()) {
         forceExitFromServer();
      }

      if (paintKey != null) {
         while (poseWheelKey.m_90859_()) {
            if (mc.f_91074_ != null) {
               if (PropHuntClient.isPropHunt()) {
                  // R conserva el atajo historico de revertir; el panel al fijarse muestra ademas
                  // botones separados para que desacoplar nunca se confunda con quitar el disfraz.
                  revertProp(mc);
               } else {
                  mc.m_91152_(new PoseWheelScreen(ClientAccessors.boundKey(poseWheelKey).m_84873_()));
               }
            }
         }

         while (provokeKey.m_90859_()) {
            if (mc.f_91074_ != null) {
               ClientNet.sendToServer(ProvokePayload.INSTANCE);
               ClientShims.overlay(Component.m_237115_("fantastic.hud.whistle"), true);
            }
         }

         while (crawlKey.m_90859_()) {
            if (mc.f_91074_ != null) {
               ClientNet.sendToServer(CrawlPayload.INSTANCE);
            }
         }

         while (actKey.m_90859_()) {
            // Gesto de criatura: solo tiene sentido disfrazado en Prop Hunt.
            if (mc.f_91074_ != null && PropHuntClient.isPropHunt() && PropHuntClient.canUseProp()) {
               ClientNet.sendToServer(PropActPayload.INSTANCE);
            }
         }

         boolean paintRawDown = rawKeyDown(paintKey);
         if (paintRawDown && !paintWasDown && !paintScreenWasOpen && mc.f_91074_ != null && ClientShims.screen(mc) == null) {
            boolean propHunt = PropHuntClient.isPropHunt();
            if (!Services.PLATFORM.get(mc.f_91074_, PaintAttachments.LOCKED)
               && (!propHunt || PropHuntClient.canUseProp())) {
               if (!propHunt) {
                  // Meccha se fija de pie en la pose normal. El acople a una superficie se realiza
                  // aparte con clic derecho sobre la cara exacta del bloque.
                  Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSING, true);
                  Services.PLATFORM.set(mc.f_91074_, PaintAttachments.POSE, 0);
                  ClientNet.sendToServer(new PosePayload(true, 0));
               }

               // Prop Hunt ya no abre un panel al fijarse. Cualquier pantalla le quita el foco al juego,
               // y con el foco perdido el ratón deja de girar la vista: la cámara quedaba en un punto
               // fijo, sin la libertad que sí tiene Meccha. Las acciones van por tecla y se anuncian en
               // el HUD.
               lock(mc);
            } else if (propHunt && Services.PLATFORM.get(mc.f_91074_, PaintAttachments.LOCKED)) {
               // La misma tecla suelta: con la cámara libre, saltar y agacharse mueven la vista en
               // vertical, así que ya no pueden servir para desacoplarse.
               detach(mc);
            }

            // En Prop Hunt F solo fija durante COUNTDOWN/HIDING/SEEKING y siendo hider. Fuera de
            // esas fases no cambia estado local; el servidor aplica exactamente la misma regla.
            if (!propHunt) {
               if (!FreeCam.active()) {
                  FreeCam.enable();
               }

               WorldBrush.setPaintMode(true);
               mc.m_91152_(new PaintModeScreen());
            }
         }

         paintWasDown = paintRawDown;
         paintScreenWasOpen = ClientShims.screen(mc) != null;
         boolean space = mc.f_91074_ != null && InputConstants.m_84830_(mc.m_91268_().m_85439_(), 32);
         boolean sneak = mc.f_91066_ != null && mc.f_91066_.f_92090_.m_90857_();
         boolean releaseScreen = ClientShims.screen(mc) == null || ClientShims.screen(mc) instanceof PropHuntLockedScreen;
         // Con la cámara libre en Prop Hunt, saltar y agacharse la suben y la bajan: si además
         // soltaran el prop, bajar la cámara era imposible sin desacoplarse. Ahí se suelta con la misma
         // tecla con la que te colocaste.
         boolean freeCamVertical = PropHuntClient.isPropHunt() && FreeCam.active();
         boolean canRelease = releaseScreen
            && !freeCamVertical
            && mc.f_91074_ != null
            && Services.PLATFORM.get(mc.f_91074_, PaintAttachments.LOCKED);
         if (canRelease && (space && !spaceWasDown || sneak && !sneakWasDown)) {
            if (PropHuntClient.isPropHunt()) {
               detach(mc);
            } else {
               exit(mc);
            }
         }

         if (mc.f_91074_ != null && (space != climbJumpSent || sneak != climbSneakSent)) {
            climbJumpSent = space;
            climbSneakSent = sneak;
            ClientNet.sendToServer(new ClimbPayload(sneak, space));
         }

         spaceWasDown = space;
         sneakWasDown = sneak;
         FreeCam.tick();
         BodyPaint.tick();
         updatePoseState(mc);
      }
   }

   private static void updatePoseState(Minecraft mc) {
      if (mc.f_91074_ != null) {
         boolean posing = Services.PLATFORM.get(mc.f_91074_, PaintAttachments.POSING);
         if (posing != wasPosing) {
            mc.f_91074_.m_6210_();
            wasPosing = posing;
         }

         boolean locked = Services.PLATFORM.get(mc.f_91074_, PaintAttachments.LOCKED);
         if (locked) {
            mc.f_91074_.m_20256_(Vec3.f_82478_);
            mc.f_91074_.f_19789_ = 0.0F;
            mc.f_91074_.f_19794_ = true;
         } else if (wasLocked) {
            mc.f_91074_.f_19794_ = false;
         }

         wasLocked = locked;
      }
   }

   public static void freezeInputIfLocked(Input in) {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      if (p != null && Services.PLATFORM.get(p, PaintAttachments.LOCKED)) {
         in.f_108568_ = false;
         in.f_108569_ = false;
         in.f_108570_ = false;
         in.f_108571_ = false;
         in.f_108572_ = false;
         in.f_108573_ = false;
         in.f_108567_ = 0.0F;
         in.f_108566_ = 0.0F;
      }
   }

   public static boolean isLocalLocked() {
      LocalPlayer p = Minecraft.m_91087_().f_91074_;
      return p != null && Services.PLATFORM.get(p, PaintAttachments.LOCKED);
   }
}
