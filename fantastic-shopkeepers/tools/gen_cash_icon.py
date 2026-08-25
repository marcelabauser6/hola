#!/usr/bin/env python3
"""
Draws the Fantastic Cash note icon.

A 16x16 RGBA PNG written by hand, because the sandbox has no image library and because pixel art this small is
better specified as a character map than painted. Each row below is exactly 16 characters, so the shape is visible
in the source and a mistake in it is visible too.

The shape is a banknote rather than another coin: Fantastic Currency already has three round coins, and a fourth
circle at 16 pixels would be indistinguishable from them in a trade slot. A wide note with a gold medallion reads
as "money, but not a coin" at a glance, which is exactly what digital cash is.

The bottom right is left as plain border because that is where Minecraft draws an item's stack count.
"""

import struct
import zlib
import pathlib
import sys

# '.' transparent, 'd' dark edge, 'h' top highlight, 'b' body, 'l' light body,
# 'D' bottom shade, 'g' gold medallion, 's' dark engraving.
ART = [
    "oooooooooooooooo",
    "oooooooooooooooo",
    "oddddddddddddddo",
    "odhhhhhhhhhhhhdo",
    "odlllllllllllldo",
    "odlgggllsssslldo",
    "odlgggllsssslldo",
    "odlggglllllllldo",
    "odbbbbbbssssbbdo",
    "odbbbbbbbbbbbbdo",
    "odDDDDDDDDDDDDdo",
    "oddddddddddddddo",
    "oooooooooooooooo",
    "oooooooooooooooo",
    "oooooooooooooooo",
    "oooooooooooooooo",
]

# A wider, taller note than the first attempt, which read as a small green smudge. The bill now fills 14x10 of the
# 16 pixels with a bright top edge, a shaded bottom edge, a three-pixel gold seal on the left and dark engraving
# bars on the right, which is what makes it legible as money at this size. Rows 12 down are left clear so the
# stack count Minecraft draws in the corner does not sit on top of the artwork.
PALETTE = {
    "o": (0, 0, 0, 0),
    "d": (0x14, 0x40, 0x1F, 0xFF),
    "h": (0x8F, 0xE8, 0xA8, 0xFF),
    "l": (0x4F, 0xB8, 0x6E, 0xFF),
    "b": (0x3F, 0xA4, 0x5E, 0xFF),
    "D": (0x24, 0x69, 0x3A, 0xFF),
    "g": (0xF0, 0xB8, 0x4A, 0xFF),
    "s": (0x10, 0x36, 0x1D, 0xFF),
}


def chunk(tag: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def main() -> int:
    for index, row in enumerate(ART):
        if len(row) != 16:
            print(f"la fila {index} mide {len(row)} y tiene que medir 16", file=sys.stderr)
            return 1
        for char in row:
            if char not in PALETTE:
                print(f"la fila {index} usa el caracter desconocido {char!r}", file=sys.stderr)
                return 1

    raw = bytearray()
    for row in ART:
        raw.append(0)  # no per-scanline filter
        for char in row:
            raw.extend(PALETTE[char])

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))

    out = pathlib.Path(sys.argv[1])
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(png)
    print(f"escrito {out} ({len(png)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
