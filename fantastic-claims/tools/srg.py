#!/usr/bin/env python3
"""Resuelve nombres oficiales de Minecraft 1.20.1 a nombres SRG (m_xxxxx_ / f_xxxxx_).

Combina:
  - mappings.txt de Mojang            : oficial -> obf
  - mcp_config mappings-merged.txt    : obf     -> srg (con nombres de clase oficiales)

Uso: python3 srg.py <ClaseOficialConPuntos> <miembro> [<miembro> ...]
     python3 srg.py net.minecraft.server.MinecraftServer usesAuthentication
"""
import sys, re, io

LIB = "/projects/sandbox/build/forge/libraries"
MOJANG = LIB + "/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-mappings.txt"
MERGED = (LIB + "/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/"
                "mcp_config-1.20.1-20230612.114412-mappings-merged.txt")


def parse_mojang():
    """{claseOficial: {'obf': obfClase, 'members': [(oficial, obf, esMetodo, params)]}}"""
    out, cur = {}, None
    for line in io.open(MOJANG, encoding="utf-8"):
        if line.startswith("#"):
            continue
        if not line.startswith(" "):
            m = re.match(r"^(\S+) -> (\S+):", line)
            if m:
                cur = {"obf": m.group(1 + 1).replace(".", "/"), "members": []}
                out[m.group(1)] = cur
            continue
        if cur is None:
            continue
        body = line.strip()
        m = re.match(r"^(?:\d+:\d+:)?(\S+) (\w+)\((.*)\) -> (\S+)$", body)
        if m:
            cur["members"].append((m.group(2), m.group(4), True, m.group(3)))
            continue
        m = re.match(r"^(\S+) (\w+) -> (\S+)$", body)
        if m:
            cur["members"].append((m.group(2), m.group(3), False, None))
    return out


def parse_merged():
    """{claseOficialSlash: {'fields': {obf: srg}, 'methods': [(obf, desc, srg)]}}"""
    out, cur = {}, None
    for line in io.open(MERGED, encoding="utf-8"):
        if line.startswith("tsrg2"):
            continue
        if not line.startswith("\t"):
            parts = line.split()
            if len(parts) >= 2:
                cur = {"fields": {}, "methods": []}
                out[parts[1]] = cur
            continue
        if cur is None:
            continue
        depth = len(line) - len(line.lstrip("\t"))
        if depth != 1:
            continue
        parts = line.split()
        if len(parts) == 2:
            cur["fields"][parts[0]] = parts[1]
        elif len(parts) == 3:
            cur["methods"].append((parts[0], parts[1], parts[2]))
    return out


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cls, wanted = sys.argv[1], sys.argv[2:]
    mojang, merged = parse_mojang(), parse_merged()
    if cls not in mojang:
        print("[!] clase no encontrada en mappings de Mojang: " + cls)
        return 2
    info = mojang[cls]
    mm = merged.get(cls.replace(".", "/"), {"fields": {}, "methods": []})
    for name in wanted:
        hits = [m for m in info["members"] if m[0] == name]
        if not hits:
            print("%-28s [!] no existe en %s" % (name, cls))
            continue
        for official, obf, is_method, params in hits:
            if is_method:
                cands = [m for m in mm["methods"] if m[0] == obf]
                if not cands:
                    print("%-28s obf=%-6s -> (sin srg, nombre sin ofuscar)" % (
                        official + "(" + (params or "") + ")", obf))
                for obfn, desc, srg in cands:
                    print("%-28s obf=%-6s desc=%-52s SRG=%s" % (
                        official + "(" + (params or "") + ")", obfn, desc, srg))
            else:
                srg = mm["fields"].get(obf, "(sin srg)")
                print("%-28s obf=%-6s CAMPO SRG=%s" % (official, obf, srg))
    return 0


if __name__ == "__main__":
    sys.exit(main())
