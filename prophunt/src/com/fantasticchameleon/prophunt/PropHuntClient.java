package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.client.AvatarState;
import com.fantasticchameleon.client.BlockTexturePresets;
import com.fantasticchameleon.client.PropAvatarPreview;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lado cliente del modo Prop Hunt: se encarga de la <b>apariencia</b> del disfraz.
 *
 * <p>La forma la decide el servidor, que es autoritativo, pero las texturas solo existen en el cliente.
 * El servidor dice de que bloque se trata en {@code PROP_SOURCE} (y para las criaturas basta el propio
 * indice de prop, que ya viaja sincronizado), y aqui se construye la imagen.
 *
 * <p>Se escribe en la copia local del atributo del lienzo, que en el cliente no manda nada por red: es
 * solo dibujar lo que ya sabemos. Vale igual para el jugador local, para los demas y para los bots, asi
 * que todos los props salen identicos al original sin depender de que nadie suba nada.
 */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PropHuntClient {
   private static final Map<String, BodyCanvas> BLOCK_CACHE = new HashMap<>();
   private static final Map<UUID, String> APPLIED = new HashMap<>();

   private PropHuntClient() {
   }

   /**
    * True si el jugador local esta jugando en modo Prop Hunt.
    *
    * <p>Se lee del atributo sincronizado del propio jugador, no del paquete de salas: ese paquete puede
    * llegar tarde o estar desactualizado, y entonces se abrian los menus del modo equivocado.
    */
   public static boolean isPropHunt() {
      LocalPlayer player = Minecraft.m_91087_().f_91074_;
      if (player == null) {
         return false;
      }

      Integer mode = Services.PLATFORM.getOrNull(player, PaintAttachments.GAME_MODE);
      return mode != null && mode == PropHunt.MODE_PROP_HUNT;
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase == TickEvent.Phase.END) {
         paintProps();
      }
   }

   /** Da a cada jugador disfrazado la textura que le corresponde. */
   private static void paintProps() {
      ClientLevel level = Minecraft.m_91087_().f_91073_;
      if (level == null) {
         return;
      }

      for (Player player : level.m_6907_()) {
         int prop = AvatarState.prop(player);
         if (prop < 0) {
            APPLIED.remove(player.m_20148_());
            continue;
         }

         boolean creature = PropShapes.followsLook(prop);
         BlockState state = creature ? null : stateFor(player);
         if (!creature && state == null) {
            continue;
         }

         // El id del estado incluye todas las propiedades visuales; evita reutilizar el atlas de otro
         // eje, orientación o conexión del mismo bloque.
         int variant = AvatarState.propVariant(player);
         int stateId = state == null ? -1 : Block.m_49956_(state);
         String key = prop + ":" + variant + ":" + stateId;
         if (key.equals(APPLIED.get(player.m_20148_()))) {
            continue;
         }

         BodyCanvas canvas = creature ? PropTextures.forMob(PropShapes.of(prop).key()) : blockCanvas(state, prop, variant);
         if (canvas != null) {
            Services.PLATFORM.set(player, PaintAttachments.PROP_CANVAS, canvas);
            APPLIED.put(player.m_20148_(), key);
         }
      }
   }

   /** Estado exacto sincronizado; PROP_SOURCE se conserva como fallback para mundos/paquetes antiguos. */
   public static BlockState stateFor(Player player) {
      if (player == Minecraft.m_91087_().f_91074_ && PropAvatarPreview.active()) {
         return null;
      }

      Integer stateId = Services.PLATFORM.getOrNull(player, PaintAttachments.PROP_STATE);
      if (stateId != null && stateId >= 0) {
         BlockState state = Block.m_49803_(stateId);
         if (state != null) {
            return state;
         }
      }

      String blockId = String.valueOf(Services.PLATFORM.getOrNull(player, PaintAttachments.PROP_SOURCE));
      if (blockId.isEmpty() || "null".equals(blockId)) {
         return null;
      }

      try {
         Block block = BuiltInRegistries.f_256975_.m_7745_(new ResourceLocation(blockId));
         return block == null ? null : block.m_49966_();
      } catch (Exception ignored) {
         return null;
      }
   }

   /** Lienzo del estado exacto con cache porque construirlo cuesta. */
   private static BodyCanvas blockCanvas(BlockState state, int prop, int variant) {
      int stateId = Block.m_49956_(state);
      String key = stateId + "|" + prop + "|" + variant;
      BodyCanvas cached = BLOCK_CACHE.get(key);
      if (cached != null) {
         return cached;
      }

      try {
         BodyCanvas built = PropTextures.forBlock(state, prop, variant);
         if (built == null) {
            // Ultimo recurso: la imagen plana que ya usaba el mod, mejor eso que un prop en blanco.
            built = BlockTexturePresets.canvas(state.m_60734_());
         }

         if (built != null) {
            BLOCK_CACHE.put(key, built);
         }

         return built;
      } catch (Exception ignored) {
         return null;
      }
   }

   /** Invalida lienzos cuando cambian resource packs o modelos. */
   public static void clearCaches() {
      BLOCK_CACHE.clear();
      APPLIED.clear();
      PropTextures.clearCache();
   }

   /** Los listeners de recursos se registran en el bus MOD, separado del tick del bus Forge. */
   @Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
   public static final class Reloads {
      private Reloads() {
      }

      @SubscribeEvent
      public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener((ResourceManagerReloadListener)manager -> clearCaches());
      }
   }
}
