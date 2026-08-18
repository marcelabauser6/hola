package com.fantasticchameleon.paint;

import com.fantasticchameleon.compat.ByteBufCodecs;
import com.fantasticchameleon.platform.FantasticAttachment;
import com.mojang.serialization.Codec;
import java.util.List;

public final class PaintAttachments {
   public static final FantasticAttachment<BodyCanvas> BODY_CANVAS = FantasticAttachment.syncedPersistent(
      "body_canvas", () -> BodyCanvas.EMPTY, BodyCanvas.MAP_CODEC.codec(), BodyCanvas.STREAM_CODEC, true
   );
   public static final FantasticAttachment<Boolean> POSING = FantasticAttachment.synced("posing", () -> Boolean.FALSE, ByteBufCodecs.BOOL);
   public static final FantasticAttachment<Boolean> LOCKED = FantasticAttachment.synced("locked", () -> Boolean.FALSE, ByteBufCodecs.BOOL);
   public static final FantasticAttachment<Float> LOCK_YAW = FantasticAttachment.synced("lock_yaw", () -> 0.0F, ByteBufCodecs.FLOAT);
   public static final FantasticAttachment<Float> LOCK_PITCH = FantasticAttachment.synced("lock_pitch", () -> 0.0F, ByteBufCodecs.FLOAT);
   public static final FantasticAttachment<Float> LOCK_ROLL = FantasticAttachment.synced("lock_roll", () -> 0.0F, ByteBufCodecs.FLOAT);
   public static final FantasticAttachment<Integer> POSE = FantasticAttachment.synced("pose", () -> 0, ByteBufCodecs.VAR_INT);
   public static final FantasticAttachment<Boolean> CRAWLING = FantasticAttachment.synced("crawling", () -> Boolean.FALSE, ByteBufCodecs.BOOL);
   public static final FantasticAttachment<Boolean> CLINGING = FantasticAttachment.synced("clinging", () -> Boolean.FALSE, ByteBufCodecs.BOOL);
   public static final FantasticAttachment<Boolean> CLIMB_UP = FantasticAttachment.synced("climb_up", () -> Boolean.FALSE, ByteBufCodecs.BOOL);
   public static final FantasticAttachment<Boolean> SHRUNK = FantasticAttachment.synced("shrunk", () -> Boolean.FALSE, ByteBufCodecs.BOOL);
   public static final FantasticAttachment<Boolean> BLOCK_FORM = FantasticAttachment.synced("block_form", () -> Boolean.FALSE, ByteBufCodecs.BOOL);
   public static final FantasticAttachment<Integer> BLOCK_SHAPE = FantasticAttachment.synced("block_shape", () -> 0, ByteBufCodecs.VAR_INT);
   /**
    * Modo de juego de la sala del jugador (0 = Meccha, 1 = Prop Hunt).
    *
    * <p>Va por jugador y sincronizado porque el cliente necesita saber el modo para decidir que GUI
    * tiene sentido. Deducirlo del paquete de salas era fragil: ese paquete puede estar desactualizado
    * o no haber llegado, y entonces salian menus del modo equivocado.
    */
   public static final FantasticAttachment<Integer> GAME_MODE = FantasticAttachment.synced("game_mode", () -> 0, ByteBufCodecs.VAR_INT);
   public static final FantasticAttachment<Integer> PROP = FantasticAttachment.synced("prop", () -> -1, ByteBufCodecs.VAR_INT);
   public static final FantasticAttachment<Integer> PROP_VARIANT = FantasticAttachment.synced("prop_variant", () -> 0, ByteBufCodecs.VAR_INT);
   public static final FantasticAttachment<BodyCanvas> PROP_CANVAS = FantasticAttachment.synced("prop_canvas", () -> BodyCanvas.EMPTY, BodyCanvas.STREAM_CODEC);
   /**
    * Id del bloque que representa el prop, por ejemplo {@code minecraft:oak_planks}.
    *
    * <p>El servidor no tiene los pixeles de las texturas, solo el cliente. Guardando aqui de que
    * bloque se trata, cada cliente puede generar la textura real por su cuenta: el disfraz sale
    * identico al bloque tocado y los bots dejan de ser manchas de color.
    */
   public static final FantasticAttachment<String> PROP_SOURCE = FantasticAttachment.synced("prop_source", () -> "", ByteBufCodecs.stringUtf8(64));
   public static final FantasticAttachment<Long> KIT_LAST = FantasticAttachment.persistent("kit_last", () -> 0L, Codec.LONG, true);
   public static final FantasticAttachment<Boolean> SIZE_MINI = FantasticAttachment.persistent("size_mini", () -> Boolean.FALSE, Codec.BOOL, true);
   public static final FantasticAttachment<Boolean> STARTER_BRUSH = FantasticAttachment.persistent("starter_brush", () -> Boolean.FALSE, Codec.BOOL, true);
   public static final FantasticAttachment<Boolean> HUB_SPAWNED = FantasticAttachment.persistent("hub_spawned", () -> Boolean.FALSE, Codec.BOOL, true);
   public static final FantasticAttachment<FrozenFrame> FROZEN_FRAME = FantasticAttachment.synced(
      "frozen_frame", () -> FrozenFrame.NONE, FrozenFrame.STREAM_CODEC
   );
   public static final List<FantasticAttachment<?>> ALL = List.of(
      BODY_CANVAS,
      POSING,
      LOCKED,
      LOCK_YAW,
      LOCK_PITCH,
      LOCK_ROLL,
      POSE,
      CRAWLING,
      CLINGING,
      CLIMB_UP,
      SHRUNK,
      BLOCK_FORM,
      BLOCK_SHAPE,
      GAME_MODE,
      PROP,
      PROP_VARIANT,
      PROP_CANVAS,
      PROP_SOURCE,
      KIT_LAST,
      SIZE_MINI,
      STARTER_BRUSH,
      HUB_SPAWNED,
      FROZEN_FRAME
   );

   private PaintAttachments() {
   }
}
