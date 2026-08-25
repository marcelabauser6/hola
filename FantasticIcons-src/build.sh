#!/usr/bin/env bash
# Compila Fantastic Icons sin ForgeGradle.
#
# El truco: el codigo se escribe con nombres legibles (mappings oficiales de
# Mojang) y despues se reobfusca a los nombres SRG que usa Forge en produccion
# (m_12345_ / f_12345_), que es exactamente lo que hace ForgeGradle al hacer
# "reobfJar".
#
#   1. mc_official.jar  = client.jar de Mojang remapeado con client.txt (proguard, invertido)
#   2. javac            contra mc_official.jar + forge universal + libs
#   3. official2srg.tsrg = composicion de client.txt (official->obf) con
#                          joined.tsrg de mcp_config (obf->srg)
#   4. ForgeAutoRenamingTool aplica ese mapping a las clases compiladas
#   5. zip con las clases reobfuscadas + assets + META-INF/mods.toml
#
# Requiere: java 17+, curl, python3, ForgeAutoRenamingTool (art.jar)
set -euo pipefail

WORK="${WORK:-$(cd "$(dirname "$0")/.." && pwd)}"
SRC="$(cd "$(dirname "$0")" && pwd)"
CP="$WORK/mc_official.jar:$WORK/forge-universal.jar:$(ls "$WORK"/libs/*.jar | tr '\n' ':')"

echo ">> compilando"
rm -rf "$SRC/classes" && mkdir -p "$SRC/classes"
javac --release 17 -encoding UTF-8 -nowarn -proc:none -cp "$CP" -d "$SRC/classes" \
   $(find "$SRC/src" -name '*.java')

echo ">> reobfuscando a SRG"
(cd "$SRC/classes" && jar --create --file "$SRC/classes_dev.jar" .)
java -jar "$WORK/art.jar" --input "$SRC/classes_dev.jar" --output "$SRC/classes_srg.jar" \
   --map "$WORK/official2srg.tsrg" \
   -e "$WORK/mc_official.jar" -e "$WORK/forge-universal.jar" \
   $(for j in "$WORK"/libs/*.jar; do printf -- "-e %s " "$j"; done)

echo ">> empaquetando"
rm -rf "$SRC/pack" && mkdir -p "$SRC/pack"
(cd "$SRC/pack" && unzip -oq "$SRC/classes_srg.jar" && rm -f META-INF/MANIFEST.MF)
cp -r "$SRC/resources/." "$SRC/pack/"
(cd "$SRC/pack" && jar --create --file "$WORK/Fantastic Icons-1.0.2.jar" --manifest "$SRC/MANIFEST.MF" .)

echo ">> listo: $WORK/Fantastic Icons-1.0.2.jar"
