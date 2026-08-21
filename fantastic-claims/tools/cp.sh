#!/bin/bash
# Classpath para compilar clases del mod contra Forge 1.20.1 con nombres SRG (m_xxxxx_),
# tal y como existen en produccion (Forge / Mohist).
#  - forge-*-server.jar y -universal.jar van PRIMERO: traen las clases de Minecraft
#    ya parcheadas por Forge (Entity con getPersistentData(), etc.).
#  - server-*-srg.jar completa el resto de Minecraft con nombres SRG.
#  - se excluyen los jars obfuscados (slim / unpacked / bundler).
FORGE=/projects/sandbox/build/forge
MCV=1.20.1-20230612.114412
FV=1.20.1-47.3.0
CP="$FORGE/libraries/net/minecraftforge/forge/$FV/forge-$FV-server.jar"
CP="$CP:$FORGE/libraries/net/minecraftforge/forge/$FV/forge-$FV-universal.jar"
CP="$CP:$FORGE/libraries/net/minecraft/server/$MCV/server-$MCV-srg.jar"
CP="$CP:$FORGE/libraries/net/minecraft/server/$MCV/server-$MCV-extra.jar"
for j in $(find $FORGE/libraries -name "*.jar" \
    | grep -v "net/minecraft/server/" \
    | grep -v "net/minecraftforge/forge/" \
    | grep -v installer); do
  CP="$CP:$j"
done
# el jar original aporta las clases del mod que NO recompilamos (incluidas las de cliente)
CP="$CP:/projects/sandbox/hola/Fantastic Blocks-7.7.0.jar"
echo "$CP"
