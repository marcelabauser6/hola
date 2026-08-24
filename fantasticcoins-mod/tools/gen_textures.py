#!/usr/bin/env python3
"""
Generates the mod logo.

This file used to generate three things. Two of them are gone:

- The ATM *block* textures moved to `gen_atm_block.py`, which redraws them at 32x32. They were four
  mid-greys inside a 66-level range, which is why the placed block read as a plain cube.
- The ATM *GUI panel* (`textures/gui/atm.png`) was deleted along with the texture itself. The ATM
  screen is now drawn in code in the issuing bank's colour, so nothing loaded that image any more.

It also copied wallet artwork out of a scratch directory that no longer exists; that step went with
it. What remains is the logo, which is derived from the gold coin sprite.
"""
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir)
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/athens_coins")

gold = Image.open(os.path.join(ASSETS, "textures/item/gold_coin.png")).convert("RGBA")
logo = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
logo.paste(gold.resize((116, 116), Image.LANCZOS), (6, 6))
logo.save(os.path.join(RES, "fantasticcoins_logo.png"))
print("logo written from the gold coin sprite")
