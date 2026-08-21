#!/bin/bash
# Empaqueta el jar corregido: parte del jar original y sustituye las clases recompiladas.
# Las clases de com/claimblocks/client/** se conservan tal cual (no se recompilan porque
# necesitarian el jar de cliente de Minecraft).
set -e
export JAVA_HOME=/root/.local/share/mise/installs/java/17
BUILD=/projects/sandbox/build
ORIG="/projects/sandbox/hola/Fantastic Blocks-7.7.0.jar"
OUT="$BUILD/dist/Fantastic Claims-7.9.0.jar"

rm -rf "$BUILD/out" "$BUILD/dist" "$BUILD/stage"
mkdir -p "$BUILD/out" "$BUILD/dist" "$BUILD/stage"

echo ">> compilando (Java 17, nombres SRG)"
"$JAVA_HOME/bin/javac" -nowarn -proc:none -cp "$(bash $BUILD/cp.sh)" -d "$BUILD/out" $(find "$BUILD/src" -name "*.java")

echo ">> desempaquetando jar original"
cd "$BUILD/stage"
unzip -q -o "$ORIG"

echo ">> sustituyendo clases recompiladas"
# se borran las clases del mod que SI se recompilan (todo menos client/)
find com/claimblocks -name "*.class" -not -path "com/claimblocks/client/*" -delete
cp -r "$BUILD/out/com" .

echo ">> actualizando mods.toml"
python3 "$BUILD/patch_toml.py" "$BUILD/stage/META-INF/mods.toml"

echo ">> registrando mixins nuevos"
python3 "$BUILD/patch_mixins.py" "$BUILD/stage/claimblocks.mixins.json"

echo ">> generando jar"
"$JAVA_HOME/bin/jar" --create --file "$OUT" --manifest META-INF/MANIFEST.MF \
  $(ls -d * | grep -v '^META-INF$') META-INF/mods.toml
cd "$BUILD"
ls -la "$OUT"
