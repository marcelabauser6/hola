package com.fantasticchameleon.client;

import com.fantasticchameleon.network.RoundStatePayload;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Cámara desacoplada alrededor del cuerpo fijado, con ancla compartida por render y colisión. */
public final class FreeCam {
   private static final double RADIUS = 6.0;
   private static final double SPEED = 0.2425;
   private static final double SPRINT_MULT = 2.2;
   private static final double ORBIT_SENS = 0.01;
   private static final double MIN_ORBIT = 0.8;
   private static final double ZOOM_STEP = 0.85;
   private static UUID target;
   private static boolean prevWasDown;
   private static boolean nextWasDown;
   private static boolean active;
   private static double offX;
   private static double offY;
   private static double offZ;
   private static double offXo;
   private static double offYo;
   private static double offZo;
   private static CameraType previousType;

   private FreeCam() {}

   public static boolean active() { return active; }

   public static void toggle() {
      if (active) disable(); else enable();
   }

   public static void enable() {
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer player = mc.f_91074_;
      if (active || player == null || !Services.PLATFORM.get(player, PaintAttachments.LOCKED)) {
         return;
      }

      active = true;
      Vec3 eye = player.m_20299_(1.0F);
      boolean attached = Boolean.TRUE.equals(Services.PLATFORM.getOrNull(player, PaintAttachments.ATTACHED));
      // Pegado a una pared, "detrás" mira justo hacia el sólido. La cámara debe empezar en el lado
      // exterior, delante del cuerpo, y mirar de vuelta al ancla.
      Integer faceId = Services.PLATFORM.getOrNull(player, PaintAttachments.ATTACH_FACE);
      Direction face = faceId != null && faceId >= 0 && faceId < Direction.values().length
         ? Direction.m_122376_(faceId)
         : null;
      Vec3 direction;
      if (attached && face != null && face != Direction.UP && face != Direction.DOWN) {
         // Sólo una normal horizontal define un lado útil de cámara. Prop Hunt usa UP para indicar
         // apoyo en el suelo; tratarla como offset mandaba la cámara cuatro bloques hacia arriba y
         // la dejaba mirando verticalmente hacia abajo.
         direction = new Vec3((double)face.m_122429_(), 0.0, (double)face.m_122431_());
      } else {
         direction = horizontalBehind(player);
      }
      double distance = 4.0;
      HitResult hit = player.m_9236_().m_45547_(
         new ClipContext(eye, eye.m_82549_(direction.m_82490_(distance)), Block.COLLIDER, Fluid.NONE, player)
      );
      if (hit.m_6662_() == HitResult.Type.BLOCK) {
         distance = Math.max(0.3, eye.m_82554_(hit.m_82450_()) - 0.3);
      }

      offX = direction.f_82479_ * distance;
      offY = direction.f_82480_ * distance;
      offZ = direction.f_82481_ * distance;
      offXo = offX;
      offYo = offY;
      offZo = offZ;
      previousType = mc.f_91066_.m_92176_();
      mc.f_91066_.m_92157_(CameraType.THIRD_PERSON_BACK);
      if (attached) {
         aimAtAnchor(player);
      }
   }

   private static Vec3 horizontalBehind(LocalPlayer player) {
      Vec3 look = player.m_20252_(1.0F);
      Vec3 flat = new Vec3(look.f_82479_, 0.0, look.f_82481_);
      if (flat.m_82556_() < 1.0E-6) {
         double yaw = Math.toRadians((double)player.m_146908_());
         flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
      }
      return flat.m_82541_().m_82490_(-1.0);
   }

   public static void disable() {
      if (!active) return;
      active = false;
      target = null;
      Minecraft mc = Minecraft.m_91087_();
      if (previousType != null && mc.f_91066_ != null) {
         mc.f_91066_.m_92157_(previousType);
      }
      previousType = null;
   }

   public static String watchedName() {
      Minecraft mc = Minecraft.m_91087_();
      if (target == null || mc.f_91073_ == null) return null;
      Player player = mc.f_91073_.m_46003_(target);
      return player == null ? null : player.m_7755_().getString();
   }

   public static boolean canCycle() { return canWatch() && !watchable().isEmpty(); }

   private static Entity anchor() {
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer self = mc.f_91074_;
      if (target != null && mc.f_91073_ != null && self != null) {
         Player watched = mc.f_91073_.m_46003_(target);
         if (watched != null) return watched;
         target = null;
      }
      return self;
   }

   /** Posición interpolada de la misma entidad alrededor de la que se calcula cameraPosition. */
   public static Vec3 cameraAnchorPosition(Entity fallback, float partialTick) {
      Entity entity = anchor();
      if (entity == null) entity = fallback;
      double x = Mth.m_14139_((double)partialTick, entity.f_19854_, entity.m_20185_());
      double y = Mth.m_14139_((double)partialTick, entity.f_19855_, entity.m_20186_()) + (double)entity.m_20192_();
      double z = Mth.m_14139_((double)partialTick, entity.f_19856_, entity.m_20189_());
      return new Vec3(x, y, z);
   }

   private static boolean canWatch() {
      LocalPlayer self = Minecraft.m_91087_().f_91074_;
      if (self == null) return false;
      for (RoundStatePayload.Entry entry : RoundBar.entries()) {
         if (entry.id().equals(self.m_20148_())) return !entry.found();
      }
      return false;
   }

   private static List<UUID> watchable() {
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer self = mc.f_91074_;
      List<UUID> out = new ArrayList<>();
      if (mc.f_91073_ == null || self == null) return out;
      for (RoundStatePayload.Entry entry : RoundBar.entries()) {
         if (!entry.found() && !entry.id().equals(self.m_20148_()) && mc.f_91073_.m_46003_(entry.id()) != null) {
            out.add(entry.id());
         }
      }
      return out;
   }

   private static void cycle(int direction) {
      List<UUID> list = watchable();
      if (list.isEmpty()) {
         target = null;
      } else {
         int current = target == null ? -1 : list.indexOf(target);
         int count = list.size() + 1;
         int next = Math.floorMod(current + 1 + direction, count) - 1;
         target = next < 0 ? null : list.get(next);
      }
      snapBehindAnchor();
   }

   private static void snapBehindAnchor() {
      Entity entity = anchor();
      if (entity == null) return;
      Vec3 back = entity.m_20252_(1.0F).m_82490_(-4.0);
      offX = back.f_82479_;
      offY = back.f_82480_;
      offZ = back.f_82481_;
      offXo = offX;
      offYo = offY;
      offZo = offZ;
   }

   /** Estado de una tecla; en crudo cuando una pantalla del mod tiene el foco. */
   private static boolean pressed(KeyMapping mapping, boolean raw) {
      if (!raw) {
         return mapping.m_90857_();
      }

      Key key = ClientAccessors.boundKey(mapping);
      int code = key.m_84873_();
      long window = Minecraft.m_91087_().m_91268_().m_85439_();
      if (key.m_84868_() == Type.KEYSYM) {
         return code >= 0 && InputConstants.m_84830_(window, code);
      }

      // Quien tenga el movimiento en un botón del ratón también debe poder mover la cámara.
      if (key.m_84868_() == Type.MOUSE) {
         return code >= 0 && GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
      }

      return mapping.m_90857_();
   }

   private static void tickWatchInput(Minecraft mc) {
      long window = mc.m_91268_().m_85439_();
      boolean previous = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_BRACKET) == GLFW.GLFW_PRESS;
      boolean next = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_BRACKET) == GLFW.GLFW_PRESS;
      Screen screen = ClientShims.screen(mc);
      if ((screen == null || screen instanceof PropHuntLockedScreen) && canWatch()) {
         if (previous && !prevWasDown) cycle(-1);
         if (next && !nextWasDown) cycle(1);
      }
      prevWasDown = previous;
      nextWasDown = next;
   }

   public static void orbit(double dx, double dy) {
      if (!active) return;
      LocalPlayer player = Minecraft.m_91087_().f_91074_;
      if (player == null) return;
      double radius = Math.max(0.5, Math.sqrt(offX * offX + offY * offY + offZ * offZ));
      double azimuth = Math.atan2(offZ, offX) + dx * ORBIT_SENS;
      double elevation = Math.asin(Mth.m_14008_(offY / radius, -1.0, 1.0));
      elevation = Mth.m_14008_(elevation + dy * ORBIT_SENS, -1.35, 1.35);
      double horizontal = Math.cos(elevation);
      offX = radius * horizontal * Math.cos(azimuth);
      offY = radius * Math.sin(elevation);
      offZ = radius * horizontal * Math.sin(azimuth);
      offXo = offX;
      offYo = offY;
      offZo = offZ;
      aimAtAnchor(player);
   }

   public static void zoom(int notches) {
      if (!active || notches == 0) return;
      LocalPlayer player = Minecraft.m_91087_().f_91074_;
      if (player == null) return;
      double radius = Math.sqrt(offX * offX + offY * offY + offZ * offZ);
      if (radius < 1.0E-4) return;
      double wanted = Mth.m_14008_(radius * Math.pow(ZOOM_STEP, (double)notches), MIN_ORBIT, RADIUS);
      double factor = wanted / radius;
      offX *= factor;
      offY *= factor;
      offZ *= factor;
      offXo = offX;
      offYo = offY;
      offZo = offZ;
      aimAtAnchor(player);
   }

   private static void aimAtAnchor(LocalPlayer player) {
      Vec3 look = new Vec3(-offX, -offY, -offZ).m_82541_();
      float pitch = (float)(-Math.toDegrees(Math.asin(Mth.m_14008_(look.f_82480_, -1.0, 1.0))));
      float yaw = (float)Math.toDegrees(Math.atan2(-look.f_82479_, look.f_82481_));
      player.m_146922_(yaw);
      player.f_19859_ = yaw;
      player.m_146926_(pitch);
      player.f_19860_ = pitch;
   }

   public static void tick() {
      if (!active) return;
      Minecraft mc = Minecraft.m_91087_();
      LocalPlayer player = mc.f_91074_;
      if (player == null || !Services.PLATFORM.get(player, PaintAttachments.LOCKED)) {
         disable();
         return;
      }

      Vec3 look = new Vec3(-offX, -offY, -offZ);
      look = look.m_82556_() < 1.0E-6 ? player.m_20154_() : look.m_82541_();
      Vec3 flat = new Vec3(look.f_82479_, 0.0, look.f_82481_);
      Vec3 right = flat.m_82556_() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : flat.m_82541_().m_82537_(new Vec3(0.0, 1.0, 0.0));
      double dx = 0.0, dy = 0.0, dz = 0.0;
      Options options = mc.f_91066_;
      tickWatchInput(mc);
      if (WorldBrush.paintMode()) {
         offXo = offX;
         offYo = offY;
         offZo = offZ;
         return;
      }

      // Con el panel de controles abierto Minecraft suelta todas las teclas, así que los atajos
      // normales devuelven "no pulsada" y la cámara quedaba inmóvil. Ahí se consulta el teclado en
      // crudo, igual que hace el mod para detectar la tecla de fijarse.
      boolean panel = ClientShims.screen(mc) instanceof PropHuntLockedScreen;
      if (!panel && ClientShims.screen(mc) != null) {
         offXo = offX;
         offYo = offY;
         offZo = offZ;
         return;
      }

      if (pressed(options.f_92085_, panel)) { dx += look.f_82479_; dy += look.f_82480_; dz += look.f_82481_; }
      if (pressed(options.f_92087_, panel)) { dx -= look.f_82479_; dy -= look.f_82480_; dz -= look.f_82481_; }
      if (pressed(options.f_92086_, panel)) { dx -= right.f_82479_; dz -= right.f_82481_; }
      if (pressed(options.f_92088_, panel)) { dx += right.f_82479_; dz += right.f_82481_; }
      // Subir y bajar sólo sin el panel: con él abierto saltar y agacharse significan desacoplarse,
      // así que leerlos aquí soltaría el prop al primer intento de mover la cámara en vertical.
      if (!panel) {
         if (options.f_92089_.m_90857_()) dy++;
         if (options.f_92090_.m_90857_()) dy--;
      }
      double speed = pressed(options.f_92091_, panel) ? SPEED * SPRINT_MULT : SPEED;
      offXo = offX;
      offYo = offY;
      offZo = offZ;
      offX += dx * speed;
      offY += dy * speed;
      offZ += dz * speed;
      double length = Math.sqrt(offX * offX + offY * offY + offZ * offZ);
      if (length > RADIUS) {
         double factor = RADIUS / length;
         offX *= factor;
         offY *= factor;
         offZ *= factor;
      }
      if (dx != 0.0 || dy != 0.0 || dz != 0.0) aimAtAnchor(player);
   }

   public static Vec3 cameraPosition(Entity fallback, float partialTick) {
      Vec3 anchor = cameraAnchorPosition(fallback, partialTick);
      double x = Mth.m_14139_((double)partialTick, offXo, offX);
      double y = Mth.m_14139_((double)partialTick, offYo, offY);
      double z = Mth.m_14139_((double)partialTick, offZo, offZ);
      return anchor.m_82549_(new Vec3(x, y, z));
   }
}
