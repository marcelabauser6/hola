package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.pose.PropShapes;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Traduce un bloque o una entidad del mundo a la forma de prop equivalente del mod.
 *
 * <p>El mod ya tiene el catalogo de formas en {@link PropShapes} (bloque, slab, escalera, valla,
 * muro, panel, trampilla, alfombra, maceta, pastel, farol, yunque y varios mobs). Lo que faltaba era
 * poder mirar un bloque cualquiera del mundo y decidir en que forma y orientacion convertirse.
 *
 * <p>Las propiedades del blockstate se leen <b>por nombre</b> ("facing", "half", "type", "shape",
 * "north"...) y sus valores como texto, en vez de usar las constantes de {@code BlockStateProperties}.
 * Los enums de Minecraft serializan a minusculas ({@code Direction.NORTH.toString() == "north"}), asi
 * que este mapeo no depende de mappings y sobrevive a cambios de nombres.
 *
 * <p>El variant se empaqueta con {@link PropShapes#pack(int, int)} como {@code form * 4 + facing},
 * donde facing es 0=norte, 1=este, 2=sur, 3=oeste.
 */
public final class BlockPropMapper {

   /** Resultado del mapeo: indice de prop y variant ya empaquetado. */
   public static record Match(int prop, int variant) {
   }

   private BlockPropMapper() {
   }

   // ------------------------------------------------------------------ bloques

   /** Mapea un blockstate a la forma de prop mas parecida. Nunca devuelve null: cae a "block". */
   public static Match ofBlock(BlockState state) {
      Block block = state.m_60734_();
      Map<String, String> props = propertiesOf(state);
      int facing = facingIndex(props.get("facing"));

      if (block instanceof SlabBlock) {
         String type = props.getOrDefault("type", "bottom");
         if ("double".equals(type)) {
            return simple("block");
         }

         return new Match(prop("slab"), PropShapes.pack("top".equals(type) ? 1 : 0, 0));
      }

      if (block instanceof StairBlock) {
         return stairs(props, facing);
      }

      if (block instanceof FenceBlock) {
         return connected("fence_post", props, false);
      }

      if (block instanceof WallBlock) {
         return connected("wall", props, true);
      }

      if (block instanceof IronBarsBlock) {
         return connected("pane", props, false);
      }

      if (block instanceof TrapDoorBlock) {
         return trapdoor(props, facing);
      }

      if (block instanceof CarpetBlock) {
         return simple("carpet");
      }

      if (block instanceof CakeBlock) {
         return simple("cake");
      }

      if (block instanceof FlowerPotBlock) {
         return flowerPot(block);
      }

      if (block instanceof LanternBlock) {
         boolean hanging = "true".equals(props.get("hanging"));
         return new Match(prop("lantern"), PropShapes.pack(hanging ? 1 : 0, 0));
      }

      if (block instanceof AnvilBlock) {
         return new Match(prop("anvil"), PropShapes.pack(0, facing));
      }

      // Cualquier otra cosa se representa como bloque completo, que es la forma mas neutra.
      return simple("block");
   }

   /** Escaleras: la forma sale de half + shape, y la orientacion de facing. */
   private static Match stairs(Map<String, String> props, int facing) {
      boolean top = "top".equals(props.get("half"));
      String shape = props.getOrDefault("shape", "straight");
      int form;
      if (shape.startsWith("inner")) {
         form = top ? 4 : 1;
      } else if (shape.startsWith("outer")) {
         form = top ? 5 : 2;
      } else {
         form = top ? 3 : 0;
      }

      // El catalogo solo tiene una esquina interior y una exterior, no espejadas. Las variantes
      // "_right" son la version reflejada, que equivale a girar un cuarto de vuelta.
      int turn = shape.endsWith("_right") ? 1 : 0;
      return new Match(prop("stairs"), PropShapes.pack(form, facing + turn));
   }

   /** Trampillas: suelo/techo segun half, y pared cuando estan abiertas. */
   private static Match trapdoor(Map<String, String> props, int facing) {
      boolean open = "true".equals(props.get("open"));
      boolean top = "top".equals(props.get("half"));
      int form = open ? 2 : (top ? 1 : 0);
      return new Match(prop("trapdoor"), PropShapes.pack(form, facing));
   }

   /** Macetas: los bloques "potted_*" son bloques propios, asi que el contenido sale del id. */
   private static Match flowerPot(Block block) {
      String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.f_256975_.m_7981_(block));
      int form = 0;
      if (id.contains("potted_cactus")) {
         form = 2;
      } else if (id.contains("potted_")) {
         form = 1;
      }

      return new Match(prop("flower_pot"), PropShapes.pack(form, 0));
   }

   /**
    * Vallas, muros y paneles comparten catalogo de formas: post, end, straight, corner, tee, cross.
    * La forma sale de que lados estan conectados y el facing de hacia donde apuntan esos brazos.
    *
    * @param tri true para muros, cuyos lados valen none/low/tall en vez de true/false
    */
   private static Match connected(String key, Map<String, String> props, boolean tri) {
      boolean n = side(props.get("north"), tri);
      boolean e = side(props.get("east"), tri);
      boolean s = side(props.get("south"), tri);
      boolean w = side(props.get("west"), tri);
      int count = (n ? 1 : 0) + (e ? 1 : 0) + (s ? 1 : 0) + (w ? 1 : 0);
      int propIdx = prop(key);

      switch (count) {
         case 0:
            return new Match(propIdx, PropShapes.pack(0, 0));
         case 1: {
            // "end": el brazo unico define la orientacion.
            int facing = n ? 0 : e ? 1 : s ? 2 : 3;
            return new Match(propIdx, PropShapes.pack(1, facing));
         }

         case 2:
            if (n && s) {
               return new Match(propIdx, PropShapes.pack(2, 0));
            }

            if (e && w) {
               return new Match(propIdx, PropShapes.pack(2, 1));
            }

            // Esquina: la forma base une norte y este, el resto son giros de esa.
            if (n && e) {
               return new Match(propIdx, PropShapes.pack(3, 0));
            }

            if (e && s) {
               return new Match(propIdx, PropShapes.pack(3, 1));
            }

            if (s && w) {
               return new Match(propIdx, PropShapes.pack(3, 2));
            }

            return new Match(propIdx, PropShapes.pack(3, 3));
         case 3: {
            // "tee": la forma base tiene brazos norte, sur y este, o sea le falta el oeste.
            int facing = !w ? 0 : !n ? 1 : !e ? 2 : 3;
            return new Match(propIdx, PropShapes.pack(4, facing));
         }

         default:
            return new Match(propIdx, PropShapes.pack(5, 0));
      }
   }

   private static boolean side(String value, boolean tri) {
      if (value == null) {
         return false;
      }

      return tri ? !"none".equals(value) : "true".equals(value);
   }

   // ----------------------------------------------------------------- entidades

   /**
    * Mapea una entidad a su prop de mob. Devuelve null si el mod no tiene forma para ella, para poder
    * avisar al jugador en vez de convertirlo en algo que no se parece.
    */
   public static Match ofEntity(Entity entity) {
      String key = null;
      if (entity instanceof Cow) {
         key = "cow";
      } else if (entity instanceof Pig) {
         key = "pig";
      } else if (entity instanceof Sheep) {
         key = "sheep";
      } else if (entity instanceof Chicken) {
         key = "chicken";
      } else if (entity instanceof Wolf) {
         key = "wolf";
      } else if (entity instanceof Panda) {
         key = "panda";
      } else if (entity instanceof Creeper) {
         key = "creeper";
      } else if (entity instanceof EnderMan) {
         key = "enderman";
      }

      if (key == null) {
         return null;
      }

      int idx = PropShapes.indexOf(key);
      return idx < 0 ? null : new Match(idx, 0);
   }

   // ------------------------------------------------------------------- helpers

   private static Match simple(String key) {
      return new Match(prop(key), 0);
   }

   private static int prop(String key) {
      int idx = PropShapes.indexOf(key);
      return idx < 0 ? Math.max(0, PropShapes.indexOf("block")) : idx;
   }

   /** Direccion horizontal -> indice de facing del catalogo (0=norte, 1=este, 2=sur, 3=oeste). */
   private static int facingIndex(String facing) {
      if (facing == null) {
         return 0;
      }

      return switch (facing) {
         case "east" -> 1;
         case "south" -> 2;
         case "west" -> 3;
         default -> 0;
      };
   }

   /** Propiedades del blockstate como pares nombre/valor en texto. */
   private static Map<String, String> propertiesOf(BlockState state) {
      Map<String, String> out = new HashMap<>();
      ImmutableMap<Property<?>, Comparable<?>> values = state.m_61148_();

      for (Map.Entry<Property<?>, Comparable<?>> entry : values.entrySet()) {
         out.put(entry.getKey().m_61708_(), String.valueOf(entry.getValue()));
      }

      return out;
   }
}
