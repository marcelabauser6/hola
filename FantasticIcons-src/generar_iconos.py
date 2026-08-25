#!/usr/bin/env python3
"""Genera IconRegistry.java, la fuente bitmap y copia las 90 texturas."""
import json
import os

from PIL import Image

SRC = "/projects/sandbox/work/icons_zip/Nexo/pack/assets/minecraft/textures/verified_mod_icons"
ROOT = "/projects/sandbox/work/fsicons"
NS = "fantasticicons"

# color original -> (sufijo id, etiqueta)
COLORS = [
    ("blue",   "azul",     "Azul"),
    ("green",  "verde",    "Verde"),
    ("red",    "rojo",     "Rojo"),
    ("yellow", "dorado",   "Dorado"),
    ("silver", "plata",    "Plata"),
    ("rb",     "arcoiris", "Arcoiris"),
]

# forma original -> (prefijo id, etiqueta)
SHAPES = [
    ("mark",   "visto",                "Visto"),
    ("mark_1", "verificado_placa",     "Verificado Placa"),
    ("mark_2", "verificado_sello",     "Verificado Sello"),
    ("mark_3", "verificado_redondo",   "Verificado Redondo"),
    ("mark_4", "verificado_engranaje", "Verificado Engranaje"),
    ("mark_5", "verificado_estrella",  "Verificado Estrella"),
    ("mark_6", "verificado_escudo",    "Verificado Escudo"),
    ("mod_1",  "moderador_placa",      "Moderador Placa"),
    ("mod_2",  "moderador_sello",      "Moderador Sello"),
    ("mod_3",  "moderador_redondo",    "Moderador Redondo"),
    ("mod_4",  "moderador_engranaje",  "Moderador Engranaje"),
    ("mod_5",  "moderador_estrella",   "Moderador Estrella"),
    ("mod_6",  "moderador_escudo",     "Moderador Escudo"),
    ("shield", "escudo",               "Escudo"),
    ("star",   "estrella",             "Estrella"),
]

ACCENTS = {"Arcoiris": "Arcoíris"}

icons = []
code = 0xE100
for shape_file, shape_id, shape_label in SHAPES:
    for color_file, color_id, color_label in COLORS:
        icons.append({
            "id": f"{shape_id}_{color_id}",
            "name": f"{shape_label} {ACCENTS.get(color_label, color_label)}",
            "texture": f"{color_file}_{shape_file}",
            "glyph": code,
        })
        code += 1

assert len(icons) == 90, len(icons)
assert len({i["id"] for i in icons}) == 90
assert len({i["glyph"] for i in icons}) == 90

# --------------------------------------------------------------- texturas
#
# Se recorta SOLO en horizontal (el pack trae 1px transparente a los lados en 48
# de los 90 iconos, que en el juego se ve como un hueco entre el nombre y el
# icono). El alto se deja intacto: el contenido esta centrado en el lienzo de
# 16px, asi que con ascent 8 / height 9 el icono queda centrado con el texto.
#
# Geometria: el glifo se dibuja entre baseline-ascent y baseline-ascent+height,
# o sea -8..+1, cuyo centro es -3.5; el texto de Minecraft ocupa -7..0, centro
# -3.5 tambien. Desvio: 0 px exactos (comprobado en los 90).
ASCENT = 8
HEIGHT = 9

tex_dir = os.path.join(ROOT, "resources/assets", NS, "textures/font/iconos")
os.makedirs(tex_dir, exist_ok=True)
for icon in icons:
    image = Image.open(os.path.join(SRC, icon["texture"] + ".png")).convert("RGBA")
    box = image.getbbox()
    if box is not None:
        image = image.crop((box[0], 0, box[2], image.height))
    image.save(os.path.join(tex_dir, icon["texture"] + ".png"))

# ------------------------------------------------------------------ fuente
font = {"providers": [
    {
        "type": "bitmap",
        "file": f"{NS}:font/iconos/{icon['texture']}.png",
        "ascent": ASCENT,
        "height": HEIGHT,
        "chars": [chr(icon["glyph"])],
    }
    for icon in icons
]}
font_dir = os.path.join(ROOT, "resources/assets", NS, "font")
os.makedirs(font_dir, exist_ok=True)
with open(os.path.join(font_dir, "iconos.json"), "w", encoding="ascii") as fh:
    json.dump(font, fh, indent=2, ensure_ascii=True)

# ---------------------------------------------------------- IconRegistry
lines = []
lines.append("package com.fantasticicons.icon;")
lines.append("")
lines.append("import java.util.ArrayList;")
lines.append("import java.util.Collections;")
lines.append("import java.util.LinkedHashMap;")
lines.append("import java.util.List;")
lines.append("import java.util.Locale;")
lines.append("import java.util.Map;")
lines.append("import net.minecraft.ChatFormatting;")
lines.append("import net.minecraft.network.chat.Component;")
lines.append("import net.minecraft.network.chat.MutableComponent;")
lines.append("import net.minecraft.resources.ResourceLocation;")
lines.append("")
lines.append("/**")
lines.append(" * Catalogo de los 90 iconos verificados (Boxpix Studios). Cada icono es un glifo")
lines.append(" * de la fuente bitmap fantasticicons:iconos, por lo que se puede insertar en")
lines.append(" * cualquier Component (chat, nombre flotante, tab).")
lines.append(" *")
lines.append(" * NO EDITAR A MANO: generado a partir del pack de texturas.")
lines.append(" */")
lines.append("public final class IconRegistry {")
lines.append('   public static final ResourceLocation FONT = new ResourceLocation("fantasticicons", "iconos");')
lines.append("   private static final List<Icon> ICONS = new ArrayList<>();")
lines.append("   private static final Map<String, Icon> BY_ID = new LinkedHashMap<>();")
lines.append("")
lines.append("   private IconRegistry() {")
lines.append("   }")
lines.append("")
lines.append("   private static void add(String id, String name, char glyph) {")
lines.append("      Icon icon = new Icon(id, name, glyph);")
lines.append("      ICONS.add(icon);")
lines.append("      BY_ID.put(id, icon);")
lines.append("   }")
lines.append("")
lines.append("   static {")
for icon in icons:
    lines.append('      add("{id}", "{name}", \'\\u{glyph:04X}\');'.format(**icon))
lines.append("   }")
lines.append("")
lines.append("   public static List<Icon> all() {")
lines.append("      return Collections.unmodifiableList(ICONS);")
lines.append("   }")
lines.append("")
lines.append("   public static int count() {")
lines.append("      return ICONS.size();")
lines.append("   }")
lines.append("")
lines.append("   public static Icon get(String id) {")
lines.append("      return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT).trim());")
lines.append("   }")
lines.append("")
lines.append("   public static boolean exists(String id) {")
lines.append("      return get(id) != null;")
lines.append("   }")
lines.append("")
lines.append("   /** El glifo suelto, ya con la fuente aplicada. */")
lines.append("   public static MutableComponent glyph(String id) {")
lines.append("      Icon icon = get(id);")
lines.append("      return icon == null ? Component.empty() : icon.glyph();")
lines.append("   }")
lines.append("")
lines.append("   /** \"Nombre bonito\" + glifo, para mensajes de comandos. */")
lines.append("   public static MutableComponent label(String id) {")
lines.append("      Icon icon = get(id);")
lines.append("      if (icon == null) {")
lines.append('         return Component.literal("?");')
lines.append("      }")
lines.append("      return Component.literal(icon.name() + \" \").withStyle(ChatFormatting.WHITE).append(icon.glyph());")
lines.append("   }")
lines.append("")
lines.append("   public static record Icon(String id, String name, char character) {")
lines.append("      public MutableComponent glyph() {")
lines.append("         return Component.literal(String.valueOf(this.character)).withStyle(style -> style.withFont(FONT));")
lines.append("      }")
lines.append("   }")
lines.append("}")

src_dir = os.path.join(ROOT, "src/com/fantasticicons/icon")
os.makedirs(src_dir, exist_ok=True)
with open(os.path.join(src_dir, "IconRegistry.java"), "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines) + "\n")

print(f"iconos: {len(icons)}  glifos: U+{icons[0]['glyph']:04X}..U+{icons[-1]['glyph']:04X}")
for i in icons[:3] + icons[-2:]:
    print("  ", i["id"], "|", i["name"], "|", i["texture"], "|", hex(i["glyph"]))
