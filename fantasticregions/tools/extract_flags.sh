#!/bin/sh
# Re-reads the flag identifiers out of YAWP's RegionFlag enum.
# Run after updating YAWP, and paste the result into FlagCatalogueStaticTest.
set -e
JAR="${1:-yawp-1.20.1mohistfix.jar}"
WORK=$(mktemp -d)
unzip -qo "$JAR" -d "$WORK"
cd "$WORK"
javap -c -classpath . de.z0rdak.yawp.core.flag.RegionFlag \
  | grep '// String' | sed 's/.*\/\/ String //' \
  | grep -E '^[a-z][a-z0-9-]*$' | sort -u
