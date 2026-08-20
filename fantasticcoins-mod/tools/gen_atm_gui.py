#!/usr/bin/env python3
"""Draws the ATM screen background: assets/athens_coins/textures/gui/atm.png"""
from PIL import Image, ImageDraw
import os

GUI = os.path.join(os.path.dirname(__file__),
                   "../src/main/resources/assets/athens_coins/textures/gui")
os.makedirs(GUI, exist_ok=True)

PANEL_W, PANEL_H = 248, 166

FRAME       = (26, 14, 17, 255)
PANEL_TOP   = (96, 54, 58, 255)
PANEL_BOT   = (56, 31, 35, 255)
HILITE      = (145, 98, 98, 255)
TITLE_BAR   = (124, 67, 72, 255)
GOLD        = (201, 162, 39, 255)
GOLD_SOFT   = (150, 116, 44, 255)
CARD        = (34, 17, 21, 255)
TABLE_BG    = (44, 23, 27, 255)
HEAD_BAND   = (70, 36, 41, 255)
ROW_A       = (58, 31, 35, 255)
ROW_B       = (50, 26, 30, 255)
SHADOW      = (34, 18, 21, 255)

img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def box(x0, y0, x1, y1, fill, outline=None, inner_hilite=None):
    d.rectangle([x0, y0, x1, y1], fill=fill, outline=outline)
    if inner_hilite:
        d.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=inner_hilite)


# ---- panel with a vertical gradient so it does not look flat
for y in range(PANEL_H):
    t = y / (PANEL_H - 1)
    col = tuple(round(PANEL_TOP[i] + (PANEL_BOT[i] - PANEL_TOP[i]) * t) for i in range(3)) + (255,)
    d.line([(0, y), (PANEL_W - 1, y)], fill=col)

# ---- outer frame + bevel, with clipped corners for a rounded feel
d.rectangle([0, 0, PANEL_W - 1, PANEL_H - 1], outline=FRAME)
d.rectangle([1, 1, PANEL_W - 2, PANEL_H - 2], outline=HILITE)
d.rectangle([2, 2, PANEL_W - 3, PANEL_H - 3], outline=None)
for cx, cy in ((0, 0), (PANEL_W - 1, 0), (0, PANEL_H - 1), (PANEL_W - 1, PANEL_H - 1)):
    d.point((cx, cy), fill=(0, 0, 0, 0))
# soften the inner bevel on the bottom/right
d.line([(2, PANEL_H - 3), (PANEL_W - 3, PANEL_H - 3)], fill=SHADOW)
d.line([(PANEL_W - 3, 2), (PANEL_W - 3, PANEL_H - 3)], fill=SHADOW)

# ---- title bar (y 4..20) with gold rules above and below
box(5, 4, PANEL_W - 6, 20, TITLE_BAR, FRAME, HILITE)
d.line([(6, 5), (PANEL_W - 7, 5)], fill=GOLD_SOFT)
d.line([(6, 19), (PANEL_W - 7, 19)], fill=GOLD_SOFT)

# ---- balance card (x 132..240, y 24..48)
box(132, 24, 240, 48, CARD, GOLD_SOFT)
d.line([(133, 25), (239, 25)], fill=(58, 31, 35, 255))

# ---- table (y 54..140)
box(5, 54, PANEL_W - 6, 140, TABLE_BG, FRAME)
# header band
d.rectangle([6, 55, PANEL_W - 7, 69], fill=HEAD_BAND)
d.line([(6, 70), (PANEL_W - 7, 70)], fill=GOLD_SOFT)
# alternating row bands: rows sit at y 72, 94, 116 with height 20
for index, y in enumerate((72, 94, 116)):
    d.rectangle([6, y - 1, PANEL_W - 7, y + 19], fill=ROW_A if index % 2 == 0 else ROW_B)
# vertical rule between the figures and the buttons
d.line([(152, 55), (152, 139)], fill=FRAME)
d.line([(153, 71), (153, 139)], fill=(64, 34, 38, 255))

# ---- footer band (y 142..160)
box(5, 142, PANEL_W - 6, 161, CARD, FRAME)

img.save(os.path.join(GUI, "atm.png"))
print(f"atm.png written ({PANEL_W}x{PANEL_H})")
