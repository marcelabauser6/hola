#!/usr/bin/env python3
"""Merges the patch's added translation keys into the language files extracted from the base jar.

The patched classes reference keys the shipped jar has never had - `fshop.coin.cash` and
`fshop.msg.no_bank_account` among them - and a missing key does not fall back to anything readable:
Minecraft renders the identifier itself, so players saw literal `fshop.msg.no_bank_account` where a
sentence belonged.

This merges rather than replaces, and never overwrites an existing value, so every key the base jar
already ships survives untouched and a future FShop release that adds its own wording wins over ours.
`original/` is re-extracted on every rebuild, which is why the additions live in `lang-overlay/`
instead of being edited in place.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OVERLAY = os.path.join(ROOT, "lang-overlay", "assets", "fshop", "lang", "overlay.json")
LANG_DIR = os.path.join(ROOT, "original", "assets", "fshop", "lang")


def main():
    if not os.path.isfile(OVERLAY):
        print("no overlay file at %s" % OVERLAY, file=sys.stderr)
        return 1
    if not os.path.isdir(LANG_DIR):
        print("no extracted lang directory at %s" % LANG_DIR, file=sys.stderr)
        return 1

    with open(OVERLAY, encoding="utf-8") as handle:
        additions = json.load(handle)

    names = sorted(n for n in os.listdir(LANG_DIR) if n.endswith(".json"))
    if not names:
        print("no language files found to merge into", file=sys.stderr)
        return 1

    for name in names:
        path = os.path.join(LANG_DIR, name)
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
        added = [key for key in additions if key not in data]
        for key in added:
            data[key] = additions[key]
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2, sort_keys=True)
            handle.write("\n")
        print("%s: +%d keys, %d total" % (name, len(added), len(data)))

    # A key the code references but no locale defines would ship as a visible identifier.
    missing = [key for key in additions
               if key not in json.load(open(os.path.join(LANG_DIR, names[0]), encoding="utf-8"))]
    if missing:
        print("keys still missing after merge: %s" % missing, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
