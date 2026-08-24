#!/usr/bin/env python3
"""Puts the wand into YAWP's jar, and proves it changed nothing else.

The wand is part of YAWP rather than a mod beside it, so it ships inside YAWP's jar: its classes go in
alongside YAWP's, its mixin config is added to the manifest's MixinConfigs list, and nothing else is
touched. There is no second [[mods]] entry, so the server sees one mod with one more subcommand.

Why a script instead of a one-off edit: the jar is a community build carrying a Mohist fix, and the whole
point is that the fix is untouched. A script can be re-run after a YAWP update, and it verifies rather
than asserts - every original entry is compared by content and CRC, and the run fails if any of them moved.

Usage:
    python3 tools/inject.py <yawp.jar> <wand-classes.jar> <output.jar>
"""

import shutil
import sys
import zipfile

# The only original entry this is allowed to modify.
MANIFEST = "META-INF/MANIFEST.MF"

# Added to the manifest's MixinConfigs list so Forge loads the wand's mixin.
WAND_MIXIN_CONFIG = "yawpwand.mixins.json"

# Entries from the wand build that must not be copied. The metadata belongs to a standalone mod, which
# this is not; LICENSE is skipped because YAWP ships its own copy of the same AGPL text and overwriting
# it would be both pointless and the one thing this script refuses to do.
SKIP_FROM_WAND = {MANIFEST, "META-INF/mods.toml", "pack.mcmeta", "LICENSE"}


def read_entries(path):
    with zipfile.ZipFile(path) as archive:
        return {info.filename: (archive.read(info.filename), info.CRC)
                for info in archive.infolist() if not info.filename.endswith("/")}


def patch_manifest(raw):
    """Adds the wand's mixin config to the MixinConfigs line, leaving every other line alone."""
    text = raw.decode("utf-8")
    if WAND_MIXIN_CONFIG in text:
        return raw, False

    lines = text.split("\r\n") if "\r\n" in text else text.split("\n")
    joiner = "\r\n" if "\r\n" in text else "\n"
    out = []
    patched = False
    for line in lines:
        if line.startswith("MixinConfigs:") and not patched:
            out.append(line.rstrip() + "," + WAND_MIXIN_CONFIG)
            patched = True
        else:
            out.append(line)

    if not patched:
        # No MixinConfigs line at all. Add one rather than give up, but say so: it means this YAWP does
        # not use mixins, which would be a surprise worth noticing.
        print("  note: no MixinConfigs line found, adding one")
        insert = len(out) - 1 if out and out[-1] == "" else len(out)
        out.insert(insert, "MixinConfigs: " + WAND_MIXIN_CONFIG)
        patched = True

    return joiner.join(out).encode("utf-8"), patched


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2

    yawp_jar, wand_jar, output = sys.argv[1], sys.argv[2], sys.argv[3]

    original = read_entries(yawp_jar)
    wand = read_entries(wand_jar)
    added = {name: data for name, (data, _) in wand.items() if name not in SKIP_FROM_WAND}

    clashes = sorted(set(added) & set(original))
    if clashes:
        # Overwriting one of YAWP's own files is exactly what this must never do.
        print("REFUSING: the wand build would overwrite YAWP's own entries:")
        for name in clashes:
            print("  " + name)
        return 1

    manifest_raw, patched = patch_manifest(original[MANIFEST][0])
    if not patched:
        print("REFUSING: could not add the mixin config to the manifest")
        return 1

    with zipfile.ZipFile(yawp_jar) as source, \
            zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
        # The manifest goes first, as a jar manifest should.
        target.writestr(MANIFEST, manifest_raw)
        for info in source.infolist():
            if info.filename.endswith("/") or info.filename == MANIFEST:
                continue
            target.writestr(info.filename, source.read(info.filename))
        for name in sorted(added):
            target.writestr(name, added[name])

    return verify(yawp_jar, output, original, added, manifest_raw)


def verify(yawp_jar, output, original, added, expected_manifest):
    """Every original entry byte for byte, every wand entry present, and nothing else."""
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
            problems.append("missing wand entry: " + name)
        elif result[name][0] != data:
            problems.append("wand entry corrupted: " + name)

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

    yawp_classes = sum(1 for name in original if name.startswith("de/z0rdak/")
                       and "/wand/" not in name)
    print("Injected into %s" % output)
    print("  %d entries from YAWP, all byte-identical (%d of them its classes)"
          % (len(original) - 1, yawp_classes))
    print("  %d wand entries added" % len(added))
    print("  1 line changed: MANIFEST.MF MixinConfigs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
