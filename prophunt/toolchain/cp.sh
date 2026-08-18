#!/bin/bash
# Builds the Forge 1.20.1 PRODUCTION (SRG) compile classpath.
# Order matters: Forge-patched Minecraft classes must come before vanilla ones.
F=/projects/sandbox/work/forge
MODJAR="/projects/sandbox/hola/Fantastic Chameleon-1.20.1-1.1.0 (20).jar"

CP="$F/clientinst/libraries/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-client.jar"
CP="$CP:$F/libraries/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-server.jar"
CP="$CP:$F/libraries/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-universal.jar"
CP="$CP:$F/client-srg.jar"

# every remaining library from both installs (client install has the LWJGL/render stack)
for j in $(find $F/libraries $F/clientinst/libraries -name '*.jar' | sort -u); do
  case "$j" in
    *forge-1.20.1-47.4.0-client.jar|*forge-1.20.1-47.4.0-server.jar|*forge-1.20.1-47.4.0-universal.jar) continue ;;
  esac
  CP="$CP:$j"
done

# vanilla client libraries (LWJGL etc), JEI api, jetbrains annotations
for j in $(find $F/extralibs -name '*.jar' | grep -v natives | sort); do
  CP="$CP:$j"
done

# the original mod jar last: supplies every mod class we do NOT recompile
CP="$CP:$MODJAR"
export CP
