package com.fantasticicons.icon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Catalogo de los 90 iconos verificados (Boxpix Studios). Cada icono es un glifo
 * de la fuente bitmap fantasticicons:iconos, por lo que se puede insertar en
 * cualquier Component (chat, nombre flotante, tab).
 *
 * NO EDITAR A MANO: generado a partir del pack de texturas.
 */
public final class IconRegistry {
   public static final ResourceLocation FONT = new ResourceLocation("fantasticicons", "iconos");
   private static final List<Icon> ICONS = new ArrayList<>();
   private static final Map<String, Icon> BY_ID = new LinkedHashMap<>();

   private IconRegistry() {
   }

   private static void add(String id, String name, char glyph) {
      Icon icon = new Icon(id, name, glyph);
      ICONS.add(icon);
      BY_ID.put(id, icon);
   }

   static {
      add("visto_azul", "Visto Azul", '\uE100');
      add("visto_verde", "Visto Verde", '\uE101');
      add("visto_rojo", "Visto Rojo", '\uE102');
      add("visto_dorado", "Visto Dorado", '\uE103');
      add("visto_plata", "Visto Plata", '\uE104');
      add("visto_arcoiris", "Visto Arcoíris", '\uE105');
      add("verificado_placa_azul", "Verificado Placa Azul", '\uE106');
      add("verificado_placa_verde", "Verificado Placa Verde", '\uE107');
      add("verificado_placa_rojo", "Verificado Placa Rojo", '\uE108');
      add("verificado_placa_dorado", "Verificado Placa Dorado", '\uE109');
      add("verificado_placa_plata", "Verificado Placa Plata", '\uE10A');
      add("verificado_placa_arcoiris", "Verificado Placa Arcoíris", '\uE10B');
      add("verificado_sello_azul", "Verificado Sello Azul", '\uE10C');
      add("verificado_sello_verde", "Verificado Sello Verde", '\uE10D');
      add("verificado_sello_rojo", "Verificado Sello Rojo", '\uE10E');
      add("verificado_sello_dorado", "Verificado Sello Dorado", '\uE10F');
      add("verificado_sello_plata", "Verificado Sello Plata", '\uE110');
      add("verificado_sello_arcoiris", "Verificado Sello Arcoíris", '\uE111');
      add("verificado_redondo_azul", "Verificado Redondo Azul", '\uE112');
      add("verificado_redondo_verde", "Verificado Redondo Verde", '\uE113');
      add("verificado_redondo_rojo", "Verificado Redondo Rojo", '\uE114');
      add("verificado_redondo_dorado", "Verificado Redondo Dorado", '\uE115');
      add("verificado_redondo_plata", "Verificado Redondo Plata", '\uE116');
      add("verificado_redondo_arcoiris", "Verificado Redondo Arcoíris", '\uE117');
      add("verificado_engranaje_azul", "Verificado Engranaje Azul", '\uE118');
      add("verificado_engranaje_verde", "Verificado Engranaje Verde", '\uE119');
      add("verificado_engranaje_rojo", "Verificado Engranaje Rojo", '\uE11A');
      add("verificado_engranaje_dorado", "Verificado Engranaje Dorado", '\uE11B');
      add("verificado_engranaje_plata", "Verificado Engranaje Plata", '\uE11C');
      add("verificado_engranaje_arcoiris", "Verificado Engranaje Arcoíris", '\uE11D');
      add("verificado_estrella_azul", "Verificado Estrella Azul", '\uE11E');
      add("verificado_estrella_verde", "Verificado Estrella Verde", '\uE11F');
      add("verificado_estrella_rojo", "Verificado Estrella Rojo", '\uE120');
      add("verificado_estrella_dorado", "Verificado Estrella Dorado", '\uE121');
      add("verificado_estrella_plata", "Verificado Estrella Plata", '\uE122');
      add("verificado_estrella_arcoiris", "Verificado Estrella Arcoíris", '\uE123');
      add("verificado_escudo_azul", "Verificado Escudo Azul", '\uE124');
      add("verificado_escudo_verde", "Verificado Escudo Verde", '\uE125');
      add("verificado_escudo_rojo", "Verificado Escudo Rojo", '\uE126');
      add("verificado_escudo_dorado", "Verificado Escudo Dorado", '\uE127');
      add("verificado_escudo_plata", "Verificado Escudo Plata", '\uE128');
      add("verificado_escudo_arcoiris", "Verificado Escudo Arcoíris", '\uE129');
      add("moderador_placa_azul", "Moderador Placa Azul", '\uE12A');
      add("moderador_placa_verde", "Moderador Placa Verde", '\uE12B');
      add("moderador_placa_rojo", "Moderador Placa Rojo", '\uE12C');
      add("moderador_placa_dorado", "Moderador Placa Dorado", '\uE12D');
      add("moderador_placa_plata", "Moderador Placa Plata", '\uE12E');
      add("moderador_placa_arcoiris", "Moderador Placa Arcoíris", '\uE12F');
      add("moderador_sello_azul", "Moderador Sello Azul", '\uE130');
      add("moderador_sello_verde", "Moderador Sello Verde", '\uE131');
      add("moderador_sello_rojo", "Moderador Sello Rojo", '\uE132');
      add("moderador_sello_dorado", "Moderador Sello Dorado", '\uE133');
      add("moderador_sello_plata", "Moderador Sello Plata", '\uE134');
      add("moderador_sello_arcoiris", "Moderador Sello Arcoíris", '\uE135');
      add("moderador_redondo_azul", "Moderador Redondo Azul", '\uE136');
      add("moderador_redondo_verde", "Moderador Redondo Verde", '\uE137');
      add("moderador_redondo_rojo", "Moderador Redondo Rojo", '\uE138');
      add("moderador_redondo_dorado", "Moderador Redondo Dorado", '\uE139');
      add("moderador_redondo_plata", "Moderador Redondo Plata", '\uE13A');
      add("moderador_redondo_arcoiris", "Moderador Redondo Arcoíris", '\uE13B');
      add("moderador_engranaje_azul", "Moderador Engranaje Azul", '\uE13C');
      add("moderador_engranaje_verde", "Moderador Engranaje Verde", '\uE13D');
      add("moderador_engranaje_rojo", "Moderador Engranaje Rojo", '\uE13E');
      add("moderador_engranaje_dorado", "Moderador Engranaje Dorado", '\uE13F');
      add("moderador_engranaje_plata", "Moderador Engranaje Plata", '\uE140');
      add("moderador_engranaje_arcoiris", "Moderador Engranaje Arcoíris", '\uE141');
      add("moderador_estrella_azul", "Moderador Estrella Azul", '\uE142');
      add("moderador_estrella_verde", "Moderador Estrella Verde", '\uE143');
      add("moderador_estrella_rojo", "Moderador Estrella Rojo", '\uE144');
      add("moderador_estrella_dorado", "Moderador Estrella Dorado", '\uE145');
      add("moderador_estrella_plata", "Moderador Estrella Plata", '\uE146');
      add("moderador_estrella_arcoiris", "Moderador Estrella Arcoíris", '\uE147');
      add("moderador_escudo_azul", "Moderador Escudo Azul", '\uE148');
      add("moderador_escudo_verde", "Moderador Escudo Verde", '\uE149');
      add("moderador_escudo_rojo", "Moderador Escudo Rojo", '\uE14A');
      add("moderador_escudo_dorado", "Moderador Escudo Dorado", '\uE14B');
      add("moderador_escudo_plata", "Moderador Escudo Plata", '\uE14C');
      add("moderador_escudo_arcoiris", "Moderador Escudo Arcoíris", '\uE14D');
      add("escudo_azul", "Escudo Azul", '\uE14E');
      add("escudo_verde", "Escudo Verde", '\uE14F');
      add("escudo_rojo", "Escudo Rojo", '\uE150');
      add("escudo_dorado", "Escudo Dorado", '\uE151');
      add("escudo_plata", "Escudo Plata", '\uE152');
      add("escudo_arcoiris", "Escudo Arcoíris", '\uE153');
      add("estrella_azul", "Estrella Azul", '\uE154');
      add("estrella_verde", "Estrella Verde", '\uE155');
      add("estrella_rojo", "Estrella Rojo", '\uE156');
      add("estrella_dorado", "Estrella Dorado", '\uE157');
      add("estrella_plata", "Estrella Plata", '\uE158');
      add("estrella_arcoiris", "Estrella Arcoíris", '\uE159');
   }

   public static List<Icon> all() {
      return Collections.unmodifiableList(ICONS);
   }

   public static int count() {
      return ICONS.size();
   }

   public static Icon get(String id) {
      return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT).trim());
   }

   public static boolean exists(String id) {
      return get(id) != null;
   }

   /** El glifo suelto, ya con la fuente aplicada. */
   public static MutableComponent glyph(String id) {
      Icon icon = get(id);
      return icon == null ? Component.empty() : icon.glyph();
   }

   /** "Nombre bonito" + glifo, para mensajes de comandos. */
   public static MutableComponent label(String id) {
      Icon icon = get(id);
      if (icon == null) {
         return Component.literal("?");
      }
      return Component.literal(icon.name() + " ").withStyle(ChatFormatting.WHITE).append(icon.glyph());
   }

   public static record Icon(String id, String name, char character) {
      public MutableComponent glyph() {
         return Component.literal(String.valueOf(this.character)).withStyle(style -> style.withFont(FONT));
      }
   }
}
