#!/usr/bin/env bash
# Rebuilds FantasticShop from the patched sources in src/ and swaps the recompiled classes back
# into the original jar.
#
# FantasticShop ships SRG-obfuscated, so there is no buildable project: patches are individual
# classes recompiled against the SRG-named Minecraft jar and dropped over the originals. Public
# signatures stay identical, which is what keeps the 60-odd untouched classes linking.
#
# The classpath is assembled from the ForgeGradle caches the currency mod's build already populated,
# so build the currency mod first.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
cd "$ROOT"

VERSION="${1:-1.4.0-bank}"
BASE_JAR="${2:-../FantasticShop-1.1.jar}"
OUT_JAR="FantasticShop-${VERSION}.jar"

# Pin Java 17 rather than trusting an inherited JAVA_HOME: Forge 1.20.1 targets 17, and the sandbox
# defaults to a much newer JDK, which ForgeGradle's toolchain resolution then refuses.
if [ -z "${SHOP_JDK17:-}" ]; then
    SHOP_JDK17="$(ls -d "$HOME"/.local/share/mise/installs/java/17* \
        /opt/toolchains/.local/share/mise/installs/java/17.0.2 2>/dev/null | head -1)"
fi
if [ -z "$SHOP_JDK17" ] || [ ! -x "$SHOP_JDK17/bin/javac" ]; then
    echo "no JDK 17 found; set SHOP_JDK17 to one" >&2
    exit 1
fi
export JAVA_HOME="$SHOP_JDK17"
export PATH="$JAVA_HOME/bin:$PATH"
echo "using JDK: $(java -version 2>&1 | head -1)"

CURRENCY_PROJECT="$ROOT/../fantasticcoins-mod"
GRADLE_CACHE="$HOME/.gradle/caches"
SRG="$(find "$GRADLE_CACHE/forge_gradle/mcp_repo" -name 'joined-*-srg.jar' | head -1)"
CURRENCY="$(find "$CURRENCY_PROJECT/build/libs" -name 'FantasticCurrency-*.jar' 2>/dev/null | head -1)"

if [ -z "$SRG" ] || [ ! -f "$SRG" ]; then
    echo "SRG Minecraft jar not found; build the currency mod first to populate the cache" >&2
    exit 1
fi
if [ -z "$CURRENCY" ] || [ ! -f "$CURRENCY" ]; then
    echo "currency mod jar not found; run its gradle build first" >&2
    exit 1
fi

# Ask Gradle what it resolved rather than guessing at jar paths: fmlcore, netty, authlib, brigadier
# and DataFixerUpper are all compile-time requirements of these sources and live in different caches.
echo "resolving the compile classpath from the currency mod..."
LIBS="$(cd "$CURRENCY_PROJECT" && gradle -q --no-daemon --console=plain \
    -Porg.gradle.java.installations.paths="$JAVA_HOME" printCompileClasspath | tail -1)"
if [ -z "$LIBS" ]; then
    echo "could not resolve the compile classpath" >&2
    exit 1
fi

rm -rf original out
mkdir -p original out
unzip -oq "$BASE_JAR" -d original

# SRG first: these sources use SRG member names (m_130077_ and friends), so net.minecraft.* has to
# resolve there even though the library list also carries an officially-named Minecraft jar.
mapfile -t SOURCES < <(find src -name '*.java')
javac -nowarn -encoding UTF-8 \
    -cp "$SRG:$LIBS:$CURRENCY:original" \
    -d out "${SOURCES[@]}"

PATCHED="$(find out -name '*.class' | wc -l)"
cp -r out/. original/

# The patched classes reference translation keys the base jar never had; without these a player sees
# the raw identifier instead of a sentence.
python3 "$HERE/merge_lang.py"
BEFORE="$(unzip -l "$BASE_JAR" | tail -1 | awk '{print $2}')"
(cd original && jar --create --file "../$OUT_JAR" --manifest META-INF/MANIFEST.MF .)
AFTER="$(unzip -l "$OUT_JAR" | tail -1 | awk '{print $2}')"

echo "recompiled $PATCHED classes from ${#SOURCES[@]} sources"
echo "$OUT_JAR: $AFTER entries (base jar had $BEFORE)"
