#!/usr/bin/env python3
"""
Draws the mod's item icons.

16x16 RGBA PNGs written by hand, because the sandbox has no image library and because pixel art this small is better
specified as a character map than painted: each row below is exactly sixteen characters, so the shape is visible in
the source and a mistake in it is visible too.

Run it as `python3 tools/gen_icons.py src/main/resources/assets/fsshopkeepers/textures/item`.
"""

import struct
import zlib
import pathlib
import sys

PALETTE = {
    ".": (0, 0, 0, 0),

    # Banknote: a dark outline, a bright top edge, a mid body, a shaded bottom edge and near-black engraving.
    "o": (0x12, 0x3A, 0x1C, 0xFF),
    "l": (0x8C, 0xEC, 0xAA, 0xFF),
    "m": (0x63, 0xD1, 0x84, 0xFF),
    "b": (0x4C, 0xB8, 0x6B, 0xFF),
    "D": (0x2E, 0x7C, 0x45, 0xFF),
    "s": (0x0B, 0x26, 0x14, 0xFF),

    # Wand: a brown handle and an amber tip with a white core.
    "H": (0x4A, 0x2E, 0x12, 0xFF),
    "h": (0x6E, 0x4A, 0x22, 0xFF),
    "a": (0xC9, 0x8A, 0x22, 0xFF),
    "A": (0xF0, 0xB8, 0x4A, 0xFF),
    "W": (0xFF, 0xF4, 0xD2, 0xFF),
}

# A banknote seen face on: outlined, lit along the top edge, shaded along the bottom, with a legible currency mark
# on the left and two engraving bars on the right. Rows 13 down are left clear so the stack count Minecraft draws
# in the corner never sits on the artwork.
CASH_NOTE = [
    "................",
    "................",
    "................",
    ".oooooooooooooo.",
    ".ollllllllllllo.",
    ".ombbsbbbbbbbmo.",
    ".ombsssbbssssmo.",
    ".ombssbbbbbbbmo.",
    ".ombbssbbbbbbmo.",
    ".ombsssbbssssmo.",
    ".ombbsbbbbbbbmo.",
    ".oDDDDDDDDDDDDo.",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
]

# A wand: a brown shaft running down to the left with an amber four-point tip. Diagonal because a vertical stick at
# this size is indistinguishable from any other stick in the game.
SHOP_WAND = [
    "...........A....",
    "..........AWA...",
    ".........AWWWA..",
    "..........AWA...",
    ".........AaA....",
    "........aA......",
    ".......hA.......",
    "......hH........",
    ".....hH.........",
    "....hH..........",
    "...hH...........",
    "..hH............",
    "..HH............",
    ".HH.............",
    ".H..............",
    "................",
]

ICONS = {
    "cash_note": CASH_NOTE,
    "shop_wand": SHOP_WAND,
}


def chunk(tag: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def encode(art: list[str]) -> bytes:
    raw = bytearray()
    for row in art:
        raw.append(0)  # no per-scanline filter
        for char in row:
            raw.extend(PALETTE[char])
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def validate(name: str, art: list[str]) -> bool:
    ok = True
    if len(art) != 16:
        print(f"{name}: tiene {len(art)} filas y deben ser 16", file=sys.stderr)
        ok = False
    for index, row in enumerate(art):
        if len(row) != 16:
            print(f"{name}: la fila {index} mide {len(row)} y debe medir 16", file=sys.stderr)
            ok = False
        for char in row:
            if char not in PALETTE:
                print(f"{name}: la fila {index} usa el caracter desconocido {char!r}", file=sys.stderr)
                ok = False
    return ok


def main() -> int:
    if len(sys.argv) != 2:
        print("uso: gen_icons.py <carpeta de texturas de item>", file=sys.stderr)
        return 2
    out_dir = pathlib.Path(sys.argv[1])
    out_dir.mkdir(parents=True, exist_ok=True)

    for name, art in ICONS.items():
        if not validate(name, art):
            return 1
        path = out_dir / f"{name}.png"
        path.write_bytes(encode(art))
        opaque = sum(1 for row in art for char in row if PALETTE[char][3] != 0)
        print(f"escrito {path} ({opaque} pixeles opacos)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
