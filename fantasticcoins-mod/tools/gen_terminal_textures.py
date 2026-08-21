#!/usr/bin/env python3
"""
Draws the bank terminal textures, in the same hand-made style as the ATM.

Not sourced from the internet on purpose: a texture found online comes with unknown licensing and
the mod's author would be the one redistributing it.
"""
from PIL import Image, ImageDraw
import os

BLOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "../src/main/resources/assets/athens_coins/textures/block")
os.makedirs(BLOCK, exist_ok=True)

# Warmer and more official than the ATM: dark wood cabinet, brass trim, amber screen.
WOOD_DARK = (46, 32, 26, 255)
WOOD = (72, 50, 38, 255)
WOOD_LIGHT = (98, 70, 52, 255)
BRASS = (188, 148, 62, 255)
BRASS_DIM = (132, 104, 44, 255)
SCREEN_BG = (26, 22, 14, 255)
SCREEN_GLOW = (240, 196, 92, 255)
SCREEN_DIM = (146, 112, 48, 255)
KEY = (160, 150, 136, 255)
SLOT = (18, 14, 12, 255)


def new(fill=WOOD):
    img = Image.new("RGBA", (16, 16), fill)
    return img, ImageDraw.Draw(img)


def bevel(d):
    d.line([(0, 0), (15, 0)], fill=WOOD_LIGHT)
    d.line([(0, 0), (0, 15)], fill=WOOD_LIGHT)
    d.line([(0, 15), (15, 15)], fill=WOOD_DARK)
    d.line([(15, 0), (15, 15)], fill=WOOD_DARK)


# ---------------------------------------------------------------- front
img, d = new()
bevel(d)
# brass header band with a small plaque
d.rectangle([1, 1, 14, 2], fill=BRASS_DIM)
d.rectangle([5, 1, 10, 2], fill=BRASS)
# screen with ledger-looking lines
d.rectangle([2, 4, 13, 9], fill=WOOD_DARK)
d.rectangle([3, 5, 12, 8], fill=SCREEN_BG)
d.line([(4, 6), (10, 6)], fill=SCREEN_GLOW)
d.line([(4, 7), (8, 7)], fill=SCREEN_DIM)
d.point((11, 7), fill=BRASS)
# keypad and a card slot
d.rectangle([3, 11, 8, 14], fill=WOOD_DARK)
for ky in (11, 13):
    for kx in (4, 6):
        d.point((kx, ky), fill=KEY)
d.rectangle([10, 11, 13, 11], fill=SLOT)
d.point((13, 13), fill=SCREEN_GLOW)
d.rectangle([10, 14, 13, 14], fill=BRASS_DIM)
img.save(os.path.join(BLOCK, "bank_terminal_front.png"))

# ---------------------------------------------------------------- side
img, d = new()
bevel(d)
d.line([(4, 1), (4, 14)], fill=WOOD_LIGHT)
d.line([(11, 1), (11, 14)], fill=WOOD_DARK)
for vy in range(5, 12, 2):
    d.line([(6, vy), (9, vy)], fill=WOOD_DARK)
d.rectangle([1, 1, 14, 2], fill=BRASS_DIM)
img.save(os.path.join(BLOCK, "bank_terminal_side.png"))

# ---------------------------------------------------------------- back
img, d = new()
bevel(d)
d.rectangle([1, 1, 14, 2], fill=BRASS_DIM)
d.rectangle([3, 4, 12, 12], fill=WOOD_LIGHT)
d.rectangle([4, 5, 11, 11], fill=WOOD)
for bx, by in ((3, 4), (12, 4), (3, 12), (12, 12)):
    d.point((bx, by), fill=WOOD_DARK)
d.line([(7, 13), (7, 15)], fill=SLOT)
d.line([(8, 13), (8, 15)], fill=SLOT)
img.save(os.path.join(BLOCK, "bank_terminal_back.png"))

# ---------------------------------------------------------------- top
img, d = new()
bevel(d)
d.rectangle([2, 2, 13, 13], fill=WOOD_LIGHT)
d.rectangle([3, 3, 12, 12], fill=WOOD)
d.rectangle([5, 5, 10, 7], fill=BRASS_DIM)
d.rectangle([6, 6, 9, 6], fill=BRASS)
d.point((12, 12), fill=SCREEN_GLOW)
img.save(os.path.join(BLOCK, "bank_terminal_top.png"))

# ---------------------------------------------------------------- bottom
img, d = new(WOOD_DARK)
d.rectangle([1, 1, 14, 14], fill=WOOD)
for bx in (2, 13):
    for by in (2, 13):
        d.point((bx, by), fill=WOOD_DARK)
img.save(os.path.join(BLOCK, "bank_terminal_bottom.png"))

print("bank terminal textures written to", os.path.relpath(BLOCK))
