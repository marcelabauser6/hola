package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.client.AvatarState;
import com.fantasticchameleon.client.BlockTexturePresets;
import com.fantasticchameleon.client.ClientAccessors;
import com.fantasticchameleon.client.Pixels;
import com.fantasticchameleon.network.SetPropCanvasPayload;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.ClientNet;
import com.fantasticchameleon.platform.Services;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
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

   private static final Map<String, BodyCanvas> CANVAS_CACHE = new HashMap<>();
   private static final Map<UUID, String> APPLIED = new HashMap<>();

   private static BodyCanvas pending;
   private static int pendingTicks;

   private PropHuntClient() {
   }

   /**
    * True si el jugador local esta jugando en modo Prop Hunt.
    *
    * <p>Se lee del atributo sincronizado del propio jugador, no del paquete de salas: ese paquete
    * puede llegar tarde o estar desactualizado, y entonces se abrian los menus del modo equivocado.
    */
   public static boolean isPropHunt() {
      LocalPlayer player = Minecraft.m_91087_().f_91074_;
      if (player == null) {
         return false;
      }

      Integer mode = Services.PLATFORM.getOrNull(player, PaintAttachments.GAME_MODE);
      return mode != null && mode == PropHunt.MODE_PROP_HUNT;
   }

   /**
    * Guardamos la textura de lo que se toca sin comprobar el modo aqui: el servidor es quien decide si
    * hay transformacion, y el lienzo solo se envia si acabamos con un prop puesto. Asi tambien funciona
    * cuando el staff prueba fuera de una sala, y en Meccha nunca se envia porque ahi no hay props.
    */
   // Los bloques ya no necesitan subida: el servidor guarda de que bloque se trata y cada cliente
   // genera la textura por su cuenta. Solo las criaturas siguen subiendo su lienzo, porque su png se
   // lee del renderer de la entidad y eso solo se puede hacer aqui.

   @SubscribeEvent
   public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
      if (event.getLevel().m_5776_() && BlockPropMapper.ofEntity(event.getTarget()) != null) {
         queue(canvasOfEntity(event.getTarget()));
      }
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         if (pending != null) {
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

         paintPropsFromSource();
      }
   }

   /**
    * Da a cada prop la textura real del bloque que representa.
    *
    * <p>El servidor solo puede decir <i>que</i> bloque es (en {@code PROP_SOURCE}), porque los pixeles
    * de las texturas solo existen en el cliente. Asi que aqui, para cada jugador disfrazado, se genera
    * el lienzo del bloque y se escribe en su copia local del atributo. Escribir atributos en el cliente
    * no manda nada por red, es solo pintar lo que ya sabemos.
    *
    * <p>Esto vale igual para el jugador local, para los demas y para los bots, asi que todos los props
    * salen identicos al bloque de verdad en vez de manchas de color.
    */
   private static void paintPropsFromSource() {
      ClientLevel level = Minecraft.m_91087_().f_91073_;
      if (level == null) {
         return;
      }

      for (Player player : level.m_6907_()) {
         String source = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP_SOURCE);
         if (source == null || source.isEmpty() || AvatarState.prop(player) < 0) {
            continue;
         }

         if (source.equals(APPLIED.get(player.m_20148_()))) {
            continue;
         }

         BodyCanvas canvas = canvasOfBlockId(source);
         if (canvas != null) {
            Services.PLATFORM.set(player, PaintAttachments.PROP_CANVAS, canvas);
            APPLIED.put(player.m_20148_(), source);
         }
      }
   }

   /** Lienzo del bloque, con cache por id para no rehacer la imagen cada tick. */
   private static BodyCanvas canvasOfBlockId(String id) {
      BodyCanvas cached = CANVAS_CACHE.get(id);
      if (cached != null) {
         return cached;
      }

      try {
         Block block = BuiltInRegistries.f_256975_.m_7745_(new ResourceLocation(id));
         if (block == null) {
            return null;
         }

         BodyCanvas built = BlockTexturePresets.canvas(block);
         if (built != null) {
            CANVAS_CACHE.put(id, built);
         }

         return built;
      } catch (Exception var4) {
         return null;
      }
   }

   private static void queue(BodyCanvas canvas) {
      if (canvas != null) {
         pending = canvas;
         pendingTicks = PENDING_TIMEOUT;
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
