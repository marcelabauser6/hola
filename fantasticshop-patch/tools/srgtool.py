#!/usr/bin/env python3
"""
Look up SRG names from official Minecraft names for 1.20.1.

FantasticShop ships obfuscated, so patched sources must be written in SRG names. This reads
ForgeGradle's srg_to_official mapping and answers questions like "what is SRG for
ResourceKey.create" so patches can be written without guessing.

Usage:
    python3 srgtool.py class  net/minecraft/resources/ResourceKey
    python3 srgtool.py field  net/minecraft/core/registries/Registries CREATIVE_MODE_TAB
    python3 srgtool.py method net/minecraft/resources/ResourceKey create
"""
import sys
import glob

TSRG = glob.glob("/root/.gradle/caches/forge_gradle/minecraft_user_repo/de/oceanlabs/mcp/"
                 "mcp_config/*/srg_to_official_1.20.1.tsrg")[0]


def load():
    """Returns {class: (official_class, {srg_field: off_field}, {(srg_method, desc): off_method})}"""
    classes = {}
    current = None
    for raw in open(TSRG):
        if raw.startswith("tsrg2") or not raw.strip():
            continue
        stripped = raw.rstrip("\n")
        indent = len(stripped) - len(stripped.lstrip("\t"))
        parts = stripped.strip().split()
        if indent == 0:
            if len(parts) >= 2:
                current = parts[0]
                classes[current] = [parts[1], {}, {}]
        elif indent == 1 and current:
            if len(parts) == 2:
                classes[current][1][parts[0]] = parts[1]
            elif len(parts) == 3:
                classes[current][2][(parts[0], parts[1])] = parts[2]
    return classes


def main():
    data = load()
    if len(sys.argv) < 2:
        print(__doc__)
        return
    mode = sys.argv[1]

    if mode == "class":
        target = sys.argv[2].replace(".", "/")
        for srg_class, (off, _, _) in data.items():
            if off == target or srg_class == target:
                print(f"srg={srg_class}  official={off}")
        return

    target = sys.argv[2].replace(".", "/")
    wanted = sys.argv[3]
    for srg_class, (off, fields, methods) in data.items():
        if off != target and srg_class != target:
            continue
        print(f"class srg={srg_class} official={off}")
        if mode == "field":
            for srg_name, off_name in fields.items():
                if off_name == wanted:
                    print(f"  field {off_name} -> SRG {srg_name}")
        elif mode == "method":
            for (srg_name, desc), off_name in methods.items():
                if off_name == wanted:
                    print(f"  method {off_name}{desc} -> SRG {srg_name}")
        return
    print(f"class {target} not found")


if __name__ == "__main__":
    main()
