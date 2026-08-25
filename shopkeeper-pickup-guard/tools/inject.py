#!/usr/bin/env python3
"""Puts the pickup guard inside Easy Villagers' own jar, and proves it changed nothing else.

Blocking the pickup needs code on the Forge side: it happens in Easy Villagers' packet handler, and a
Bukkit plugin cannot intercept that or apply a mixin, because Mohist applies mixins from mods and not
from plugins. So the guard either ships as a mod of its own or it goes inside the jar it guards. This is
the second option, for anyone who would rather not add another file to mods/.

Nothing of Easy Villagers is modified: its classes go in untouched, the guard's classes are added, and
one line is appended to the manifest's MixinConfigs list. The run verifies that rather than asserting it,
comparing every original entry by content and CRC, and fails if any of them moved.

Re-run it after updating Easy Villagers, since the update replaces the jar.

Usage:
    python3 tools/inject.py <easy-villagers.jar> <guard.jar> <output.jar>
"""

import shutil
import sys
import zipfile

# The only original entry this is allowed to modify.
MANIFEST = "META-INF/MANIFEST.MF"

# Added to the manifest's MixinConfigs list so Forge loads the guard's mixin.
GUARD_MIXIN_CONFIG = "skpickupguard.mixins.json"

# Entries from the guard's own build that must not be copied: its mod metadata would register a second
# mod, which is the thing this script exists to avoid.
SKIP_FROM_GUARD = {MANIFEST, "META-INF/mods.toml", "pack.mcmeta", "LICENSE"}


def read_entries(path):
    with zipfile.ZipFile(path) as archive:
        return {info.filename: (archive.read(info.filename), info.CRC)
                for info in archive.infolist() if not info.filename.endswith("/")}


def patch_manifest(raw):
    """Adds the guard's mixin config to the MixinConfigs line, leaving every other line alone."""
    text = raw.decode("utf-8")
    if GUARD_MIXIN_CONFIG in text:
        return raw, False

    lines = text.split("\r\n") if "\r\n" in text else text.split("\n")
    joiner = "\r\n" if "\r\n" in text else "\n"
    out = []
    patched = False
    for line in lines:
        if line.startswith("MixinConfigs:") and not patched:
            out.append(line.rstrip() + "," + GUARD_MIXIN_CONFIG)
            patched = True
        else:
            out.append(line)

    if not patched:
        # No MixinConfigs line at all. Add one rather than give up, but say so: it would mean this build
        # of Easy Villagers uses no mixins, which is worth noticing.
        print("  note: no MixinConfigs line found, adding one")
        insert = len(out) - 1 if out and out[-1] == "" else len(out)
        out.insert(insert, "MixinConfigs: " + GUARD_MIXIN_CONFIG)
        patched = True

    return joiner.join(out).encode("utf-8"), patched


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2

    host_jar, guard_jar, output = sys.argv[1], sys.argv[2], sys.argv[3]

    original = read_entries(host_jar)
    guard = read_entries(guard_jar)
    added = {name: data for name, (data, _) in guard.items() if name not in SKIP_FROM_GUARD}

    clashes = sorted(set(added) & set(original))
    if clashes:
        # Overwriting one of Easy Villagers' own files is exactly what this must never do.
        print("REFUSING: the guard would overwrite Easy Villagers' own entries:")
        for name in clashes:
            print("  " + name)
        return 1

    manifest_raw, patched = patch_manifest(original[MANIFEST][0])
    if not patched:
        print("REFUSING: could not add the mixin config to the manifest")
        return 1

    with zipfile.ZipFile(host_jar) as source, \
            zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
        # The manifest goes first, as a jar manifest should.
        target.writestr(MANIFEST, manifest_raw)
        for info in source.infolist():
            if info.filename.endswith("/") or info.filename == MANIFEST:
                continue
            target.writestr(info.filename, source.read(info.filename))
        for name in sorted(added):
            target.writestr(name, added[name])

    return verify(output, original, added, manifest_raw)


def verify(output, original, added, expected_manifest):
    """Every original entry byte for byte, every guard entry present, and nothing else."""
    result = read_entries(output)
    problems = []

    for name, (data, crc) in original.items():
        if name == MANIFEST:
            continue
        if name not in result:
            problems.append("lost: " + name)
        elif result[name][0] != data or result[name][1] != crc:
            problems.append("changed: " + name)

    for name, data in added.items():
        if name not in result:
            problems.append("missing guard entry: " + name)
        elif result[name][0] != data:
            problems.append("guard entry corrupted: " + name)

    if result[MANIFEST][0] != expected_manifest:
        problems.append("manifest is not what was written")

    unexpected = set(result) - set(original) - set(added)
    if unexpected:
        problems.extend("unexpected: " + name for name in sorted(unexpected))

    if problems:
        print("VERIFICATION FAILED:")
        for problem in problems:
            print("  " + problem)
        return 1

    host_classes = sum(1 for name in original if name.endswith(".class"))
    print("Injected into %s" % output)
    print("  %d entries from Easy Villagers, all byte-identical (%d of them classes)"
          % (len(original) - 1, host_classes))
    print("  %d guard entries added" % len(added))
    print("  1 line changed: MANIFEST.MF MixinConfigs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
