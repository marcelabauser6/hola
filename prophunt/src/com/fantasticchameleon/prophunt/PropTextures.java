package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.client.ClientAccessors;
import com.fantasticchameleon.client.Pixels;
import com.fantasticchameleon.client.PropModels;
import com.fantasticchameleon.paint.BodyCanvas;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Genera la textura de un prop para que se vea igual que el original.
 *
 * <p>El lienzo de un prop es un atlas: {@link PropModels#faceRects} da, por cada caja del modelo, los
 * seis rectangulos donde van sus caras (arriba, abajo y los cuatro lados), con el mismo reparto que usa
 * Minecraft para desplegar un cubo. Aprovechando eso se puede pintar <b>cara por cara</b> en vez de
 * repetir una sola imagen en todas, que es lo que hacia que un tronco descortezado saliera con la veta
 * lateral tambien arriba.
 *
 * <p>Ademas se hornea el sombreado direccional de Minecraft en la textura. Los bloques del mundo se
 * dibujan con las caras superiores mas claras y las laterales mas oscuras; un prop es una entidad y no
 * recibe ese sombreado, asi que sin esto el disfraz se veia mas claro que el bloque de al lado y
 * cantaba a distancia.
 */
public final class PropTextures {
   private static final int CANVAS = 64;

   /** Multiplicadores de luz por cara que aplica Minecraft al dibujar bloques. */
   private static final float SHADE_UP = 1.0F;
   private static final float SHADE_DOWN = 0.5F;
   private static final float SHADE_NORTH_SOUTH = 0.8F;
   private static final float SHADE_EAST_WEST = 0.6F;

   private static final RandomSource RANDOM = RandomSource.m_216335_(42L);
   private static final Map<String, BodyCanvas> MOB_CACHE = new HashMap<>();

   /** Texturas vanilla de las criaturas que el mod puede imitar. */
   private static final Map<String, String> MOB_TEXTURES = Map.of(
      "cow", "textures/entity/cow/cow.png",
      "pig", "textures/entity/pig/pig.png",
      "sheep", "textures/entity/sheep/sheep.png",
      "chicken", "textures/entity/chicken.png",
      "wolf", "textures/entity/wolf/wolf.png",
      "creeper", "textures/entity/creeper/creeper.png",
      "enderman", "textures/entity/enderman/enderman.png"
   );

   private PropTextures() {
   }

   // -------------------------------------------------------------------- bloques

   /**
    * Lienzo de un prop de bloque, con la textura propia de cada cara y el sombreado horneado.
    *
    * @param state bloque a imitar
    * @param prop indice de forma del prop
    * @param variant variante de la forma (define el reparto de rectangulos)
    */
   public static BodyCanvas forBlock(BlockState state, int prop, int variant) {
      try {
         BakedModel model = Minecraft.m_91087_().m_91289_().m_110910_(state);
         if (model == null) {
            return null;
         }

         NativeImage up = faceImage(model, state, Direction.UP);
         NativeImage down = faceImage(model, state, Direction.DOWN);
         NativeImage north = faceImage(model, state, Direction.NORTH);
         NativeImage south = faceImage(model, state, Direction.SOUTH);
         NativeImage east = faceImage(model, state, Direction.EAST);
         NativeImage west = faceImage(model, state, Direction.WEST);
         if (up == null && north == null) {
            return null;
         }

         int[] px = new int[CANVAS * CANVAS];
         List<int[]> rects = PropModels.faceRects(prop, variant);

         // faceRects entrega los rectangulos en el orden del despliegue de un cubo:
         // arriba, abajo, y luego los cuatro lados.
         for (int i = 0; i + 5 < rects.size(); i += 6) {
            paint(px, rects.get(i), up, SHADE_UP);
            paint(px, rects.get(i + 1), down, SHADE_DOWN);
            paint(px, rects.get(i + 2), east, SHADE_EAST_WEST);
            paint(px, rects.get(i + 3), north, SHADE_NORTH_SOUTH);
            paint(px, rects.get(i + 4), west, SHADE_EAST_WEST);
            paint(px, rects.get(i + 5), south, SHADE_NORTH_SOUTH);
         }

         return new BodyCanvas(px);
      } catch (Exception var13) {
         return null;
      }
   }

   /** Imagen de la cara pedida del modelo; si esa cara no tiene quads propios, se usa lo que haya. */
   private static NativeImage faceImage(BakedModel model, BlockState state, Direction direction) {
      TextureAtlasSprite sprite = spriteFor(model, state, direction);
      if (sprite == null) {
         return null;
      }

      try {
         return ClientAccessors.originalImage(sprite.m_245424_());
      } catch (Exception var5) {
         return null;
      }
   }

   private static TextureAtlasSprite spriteFor(BakedModel model, BlockState state, Direction direction) {
      try {
         List<BakedQuad> quads = model.m_213637_(state, direction, RANDOM);
         if (!quads.isEmpty()) {
            return quads.get(0).m_173410_();
         }

         // Modelos que no son cubos (vallas, macetas) declaran sus quads sin direccion.
         quads = model.m_213637_(state, null, RANDOM);
         if (!quads.isEmpty()) {
            return quads.get(0).m_173410_();
         }

         return model.m_6160_();
      } catch (Exception var5) {
         try {
            return model.m_6160_();
         } catch (Exception var4) {
            return null;
         }
      }
   }

   /**
    * Copia una textura dentro de un rectangulo del lienzo, escalandola y aplicando el sombreado.
    *
    * <p>Las texturas animadas guardan los fotogramas apilados en la misma imagen, asi que se recorta al
    * tamano de un fotograma para no mezclarlos.
    */
   private static void paint(int[] px, int[] rect, NativeImage image, float shade) {
      if (image == null || rect == null || rect.length < 4) {
         return;
      }

      int rx = rect[0];
      int ry = rect[1];
      int rw = rect[2];
      int rh = rect[3];
      if (rw <= 0 || rh <= 0) {
         return;
      }

      int tw = image.m_84982_();
      int th = image.m_85084_();
      if (tw <= 0 || th <= 0) {
         return;
      }

      int frame = Math.min(tw, th);

      for (int y = 0; y < rh; y++) {
         int cy = ry + y;
         if (cy < 0 || cy >= CANVAS) {
            continue;
         }

         for (int x = 0; x < rw; x++) {
            int cx = rx + x;
            if (cx < 0 || cx >= CANVAS) {
               continue;
            }

            int sx = rw == 1 ? 0 : x * frame / rw;
            int sy = rh == 1 ? 0 : y * frame / rh;
            int argb = Pixels.get(image, Math.min(sx, tw - 1), Math.min(sy, th - 1));
            px[cy * CANVAS + cx] = shade(argb, shade);
         }
      }
   }

   /** Multiplica el color por el factor de luz de la cara, dejando el alfa intacto. */
   private static int shade(int argb, float factor) {
      int a = argb >>> 24 & 0xFF;
      if (a < 128) {
         // Los huecos transparentes se rellenan con el color sombreado para que no salgan blancos.
         a = 255;
      }

      int r = (int)((float)(argb >> 16 & 0xFF) * factor);
      int g = (int)((float)(argb >> 8 & 0xFF) * factor);
      int b = (int)((float)(argb & 0xFF) * factor);
      return a << 24 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
   }

   // ------------------------------------------------------------------ criaturas

   /**
    * Lienzo de un prop de criatura.
    *
    * <p>Los modelos de criatura del mod se construyen con los mismos desplazamientos de textura que los
    * modelos de Minecraft, asi que aqui no hay que recomponer nada: basta copiar el png del mob tal cual
    * en el lienzo y las UV caen donde deben. La oveja lleva ademas su lana en la mitad inferior, que es
    * donde el modelo la busca ({@link PropModels#WOOL_V}).
    */
   public static BodyCanvas forMob(String key) {
      BodyCanvas cached = MOB_CACHE.get(key);
      if (cached != null) {
         return cached;
      }

      String path = MOB_TEXTURES.get(key);
      if (path == null) {
         return null;
      }

      int[] px = new int[CANVAS * CANVAS];
      if (!copyTexture(px, path, 0)) {
         return null;
      }

      if ("sheep".equals(key)) {
         copyTexture(px, "textures/entity/sheep/sheep_fur.png", PropModels.WOOL_V);
      }

      BodyCanvas built = new BodyCanvas(px);
      MOB_CACHE.put(key, built);
      return built;
   }

   /** Copia un png de los resource packs al lienzo, empezando en la fila indicada. */
   private static boolean copyTexture(int[] px, String path, int rowOffset) {
      try {
         Optional<Resource> res = Minecraft.m_91087_().m_91098_().m_213713_(new ResourceLocation("minecraft", path));
         if (res.isEmpty()) {
            return false;
         }

         try (InputStream in = res.get().m_215507_()) {
            NativeImage image = NativeImage.m_85058_(in);

            try {
               int w = Math.min(CANVAS, image.m_84982_());
               int h = Math.min(CANVAS - rowOffset, image.m_85084_());

               for (int y = 0; y < h; y++) {
                  for (int x = 0; x < w; x++) {
                     px[(y + rowOffset) * CANVAS + x] = Pixels.get(image, x, y);
                  }
               }

               return true;
            } finally {
               image.close();
            }
         }
      } catch (Exception var12) {
         return false;
      }
   }

   /** Descarta los lienzos de criatura en cache, por si se recargan los resource packs. */
   public static void clearCache() {
      MOB_CACHE.clear();
   }
}
