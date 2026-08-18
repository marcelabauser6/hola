package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.game.Room;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reglas de transicion entre modos de juego.
 *
 * <p>Cambiar de modo tiene que dejar la sala en un estado limpio: si una sala pasa a Meccha, nadie
 * puede quedarse disfrazado de bloque, y si pasa a Prop Hunt conviene empezar sin disfraz previo
 * para que la primera captura sea la que manda.
 */
public final class PropHuntRules {
   private static final Random RNG = new Random();

   private PropHuntRules() {
   }

   /**
    * Disfraza a un bot de prop al azar, para que en Prop Hunt no se queden como muñecos posando.
    *
    * <p>La textura se rellena de un color plano porque el servidor no tiene acceso a los pixeles de
    * los bloques (eso solo existe en el cliente). Da props de colores, que se leen claramente como
    * objetos y no como jugadores.
    */
   public static void dressAsProp(ServerPlayer dummy) {
      // Se elige un bloque real del mundo y de ahi sale la forma, en vez de una forma al azar con un
      // color inventado. Asi el bot parece un bloque de verdad y no una mancha de color.
      Block block = randomBlock(dummy);
      BlockPropMapper.Match match = BlockPropMapper.ofBlock(block.m_49966_());

      FantasticNetwork.applyProp(dummy, match.prop(), match.variant());
      Services.PLATFORM.set(dummy, PaintAttachments.PROP_SOURCE, String.valueOf(BuiltInRegistries.f_256975_.m_7981_(block)));

      // Los props de bloque se alinean con el mundo; los de criatura conservan su giro.
      if (!PropShapes.followsLook(match.prop())) {
         Services.PLATFORM.set(dummy, PaintAttachments.LOCK_YAW, 0.0F);
      }

      Services.PLATFORM.set(dummy, PaintAttachments.POSING, true);
      Services.PLATFORM.set(dummy, PaintAttachments.LOCKED, true);
      PropGridSnap.snapToCell(dummy, Services.PLATFORM.get(dummy, PaintAttachments.LOCK_YAW));
      dummy.m_6210_();
   }

   /** Bloque decorativo al azar entre los que se ven bien como prop. */
   private static Block randomBlock(ServerPlayer dummy) {
      Block[] pool = POOL;
      for (int attempt = 0; attempt < 8; attempt++) {
         Block candidate = pool[RNG.nextInt(pool.length)];
         if (candidate != null) {
            return candidate;
         }
      }

      return Blocks.f_50069_;
   }

   private static Block block(String id) {
      return BuiltInRegistries.f_256975_.m_7745_(new ResourceLocation(id));
   }

   /**
    * Bloques que se leen bien como escondite: cosas que uno espera encontrar tiradas por un escenario.
    * Se resuelven por id para no depender de constantes remapeadas.
    */
   private static final Block[] POOL = new Block[]{
      block("minecraft:oak_planks"),
      block("minecraft:stone"),
      block("minecraft:cobblestone"),
      block("minecraft:oak_log"),
      block("minecraft:hay_block"),
      block("minecraft:pumpkin"),
      block("minecraft:melon"),
      block("minecraft:bookshelf"),
      block("minecraft:crafting_table"),
      block("minecraft:barrel"),
      block("minecraft:cake"),
      block("minecraft:anvil"),
      block("minecraft:lantern"),
      block("minecraft:flower_pot"),
      block("minecraft:oak_stairs"),
      block("minecraft:stone_brick_stairs"),
      block("minecraft:smooth_stone_slab"),
      block("minecraft:oak_fence"),
      block("minecraft:cobblestone_wall"),
      block("minecraft:glass_pane"),
      block("minecraft:white_carpet"),
      block("minecraft:oak_trapdoor"),
      block("minecraft:bricks"),
      block("minecraft:sandstone"),
      block("minecraft:mossy_cobblestone")
   };

   /** Limpia el disfraz de prop de todos los miembros y les avisa del modo nuevo. */
   public static void onGameModeChanged(MinecraftServer server, Room room, int mode) {
      if (server == null || room == null) {
         return;
      }

      Component label = Component.m_237115_(PropHunt.nameKey(mode));

      for (UUID id : room.roster()) {
         ServerPlayer member = server.m_6846_().m_11259_(id);
         if (member != null) {
            FantasticNetwork.clearProp(member);
            member.m_240418_(Component.m_237110_("fantastic.gamemode.switched", new Object[]{label}).m_130940_(ChatFormatting.AQUA), true);
         }
      }
   }
}
