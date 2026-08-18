package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.client.AvatarState;
import com.fantasticchameleon.client.BlockTexturePresets;
import com.fantasticchameleon.client.ClientAccessors;
import com.fantasticchameleon.client.Pixels;
import com.fantasticchameleon.client.RoomMenu;
import com.fantasticchameleon.network.RoomsPayload;
import com.fantasticchameleon.network.SetPropCanvasPayload;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.platform.ClientNet;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lado cliente del modo Prop Hunt: se encarga de la <b>apariencia</b> del disfraz.
 *
 * <p>La forma del prop la decide el servidor (es autoritativo), pero la textura solo existe en el
 * cliente, asi que aqui capturamos la imagen real de lo que el jugador toco y la subimos con el
 * paquete que ya usa el editor de props ({@link SetPropCanvasPayload}).
 *
 * <p>Como el servidor puede tardar unos ticks en confirmar la transformacion, y
 * {@code handleSetPropCanvas} descarta el lienzo si el jugador todavia no tiene prop, guardamos el
 * lienzo como pendiente y lo enviamos en cuanto la forma ya esta aplicada. Asi no hay carrera entre
 * los dos paquetes y no hace falta inventar red nueva.
 */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PropHuntClient {
   private static final int CANVAS_SIZE = 64;
   private static final int PENDING_TIMEOUT = 60;

   private static BodyCanvas pending;
   private static int pendingTicks;

   private PropHuntClient() {
   }

   /** True si la sala del jugador local esta en modo Prop Hunt. */
   public static boolean isPropHuntRoom() {
      RoomsPayload data = RoomMenu.current;
      if (data == null || data.yourRoom().isBlank()) {
         return false;
      }

      int[] cfg = data.config();
      return cfg != null && cfg.length > PropHunt.CFG_INDEX && cfg[PropHunt.CFG_INDEX] == PropHunt.MODE_PROP_HUNT;
   }

   @SubscribeEvent
   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (event.getLevel().m_5776_() && isPropHuntRoom()) {
         BlockState state = event.getLevel().m_8055_(event.getPos());
         queue(canvasOfBlock(state));
      }
   }

   @SubscribeEvent
   public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
      if (event.getLevel().m_5776_() && isPropHuntRoom() && BlockPropMapper.ofEntity(event.getTarget()) != null) {
         queue(canvasOfEntity(event.getTarget()));
      }
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase == TickEvent.Phase.END && pending != null) {
         LocalPlayer player = Minecraft.m_91087_().f_91074_;
         if (player == null) {
            pending = null;
         } else if (AvatarState.prop(player) >= 0) {
            ClientNet.sendToServer(new SetPropCanvasPayload(pending));
            pending = null;
         } else if (--pendingTicks <= 0) {
            pending = null;
         }
      }
   }

   private static void queue(BodyCanvas canvas) {
      if (canvas != null) {
         pending = canvas;
         pendingTicks = PENDING_TIMEOUT;
      }
   }

   /** Lienzo con la textura real del bloque, reutilizando el generador que ya trae el mod. */
   private static BodyCanvas canvasOfBlock(BlockState state) {
      try {
         Block block = state.m_60734_();
         return BlockTexturePresets.canvas(block);
      } catch (Exception var2) {
         return null;
      }
   }

   /**
    * Lienzo con la textura del mob. Se lee el png del renderer de la entidad directamente de los
    * resource packs, que es la unica via fiable de obtener los pixeles sin tocar la GPU.
    */
   private static BodyCanvas canvasOfEntity(Entity entity) {
      try {
         EntityRenderer<?> renderer = Minecraft.m_91087_().m_91290_().m_114382_(entity);
         if (renderer == null) {
            return null;
         }

         // getTextureLocation esta parametrizado por el tipo de entidad; el dispatcher ya nos devolvio
         // el renderer correcto para esta entidad, asi que el crudo es seguro aqui.
         @SuppressWarnings({"rawtypes", "unchecked"})
         ResourceLocation texture = ((EntityRenderer)renderer).m_5478_(entity);
         if (texture == null) {
            return null;
         }

         Optional<Resource> res = Minecraft.m_91087_().m_91098_().m_213713_(texture);
         if (res.isEmpty()) {
            return null;
         }

         try (InputStream in = res.get().m_215507_()) {
            NativeImage image = NativeImage.m_85058_(in);

            try {
               return sampleHead(image);
            } finally {
               image.close();
            }
         }
      } catch (Exception var9) {
         return null;
      }
   }

   /**
    * Recorta la zona mas representativa de la textura del mob y la estira al lienzo.
    *
    * <p>Las texturas de mob son atlas de partes del cuerpo; el cuadrante superior izquierdo suele ser
    * la cabeza, que es la parte con el color y el patron mas reconocibles del bicho.
    */
   private static BodyCanvas sampleHead(NativeImage image) {
      int w = image.m_84982_();
      int h = image.m_85084_();
      if (w <= 0 || h <= 0) {
         return null;
      }

      int cropW = Math.max(1, Math.min(w, w / 2));
      int cropH = Math.max(1, Math.min(h, h / 2));
      int[] px = new int[CANVAS_SIZE * CANVAS_SIZE];
      int fallback = 0xFF808080;

      for (int y = 0; y < CANVAS_SIZE; y++) {
         for (int x = 0; x < CANVAS_SIZE; x++) {
            int sx = Math.min(cropW - 1, x * cropW / CANVAS_SIZE);
            int sy = Math.min(cropH - 1, y * cropH / CANVAS_SIZE);
            int argb = Pixels.get(image, sx, sy);
            px[y * CANVAS_SIZE + x] = (argb >>> 24 & 0xFF) < 128 ? fallback : 0xFF000000 | argb & 16777215;
         }
      }

      return new BodyCanvas(px);
   }
}
