#!/usr/bin/env python3
"""
Rebuilds the shipped coin sprites from the untouched 1024x1024 originals.

Why downscale at all: a 1024x1024 sprite in Minecraft's item atlas makes mipmap generation
turn the fine detail into coloured speckles.

Why BOX and nothing else: BOX is a plain area average, the same operation a correct mipmap
performs, so no output pixel can fall outside the range of the source block it came from.
LANCZOS and BICUBIC have negative lobes and overshoot on high-contrast edges, which shows up
in game as bright pixels along the rim and the embossed lettering. Measured on gold_coin:

    LANCZOS   411/7603 pixels (5.41%) outside the source block range, max deviation 46
    BICUBIC   301/7608 (3.96%), max 40
    BILINEAR  322/7640 (4.21%), max 58
    BOX        11/7549 (0.15%), max 7      <- what we use

Do not change the filter without re-running that measurement.
"""
from PIL import Image
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ORIGINALS = os.path.join(HERE, "../originals/coin-textures-1024")
TARGET = os.path.join(HERE, "../src/main/resources/assets/athens_coins/textures/item")

SIZE = 128
FILTER = Image.BOX

for coin in ("bronze", "silver", "gold"):
    source = os.path.join(ORIGINALS, f"{coin}_coin.png")
    destination = os.path.join(TARGET, f"{coin}_coin.png")
    original = Image.open(source).convert("RGBA")
    original.resize((SIZE, SIZE), FILTER).save(destination, optimize=True)
    print(f"  {coin}_coin.png: {original.size[0]}x{original.size[1]} -> {SIZE}x{SIZE} "
          f"({os.path.getsize(destination):,} B) via BOX")
