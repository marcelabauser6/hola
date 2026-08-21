#!/usr/bin/env python3
"""
Draws the ATM screen background: assets/athens_coins/textures/gui/atm.png

Layout constants here must stay in step with AtmScreen.java.
"""
from PIL import Image, ImageDraw
import os

GUI = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "../src/main/resources/assets/athens_coins/textures/gui")
os.makedirs(GUI, exist_ok=True)

PANEL_W, PANEL_H = 248, 178

# Bank-terminal navy, deliberately distinct from the wallet's burgundy.
FRAME       = (18, 26, 32, 255)
PANEL_TOP   = (48, 72, 88, 255)
PANEL_BOT   = (27, 41, 52, 255)
HILITE      = (88, 122, 142, 255)
SHADOW      = (20, 30, 38, 255)
TITLE_BAR   = (38, 62, 78, 255)
GOLD_SOFT   = (150, 120, 50, 255)
CARD        = (17, 27, 35, 255)
TABLE_BG    = (23, 35, 44, 255)
HEAD_BAND   = (36, 56, 70, 255)
ROW_A       = (31, 47, 59, 255)
ROW_B       = (26, 40, 50, 255)

# Rows: 22px tall bands, matching AtmScreen.ROW_Y
ROW_Y = (74, 98, 122)
RULE_X = 154

img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def box(x0, y0, x1, y1, fill, outline=None, top_hilite=None):
    d.rectangle([x0, y0, x1, y1], fill=fill, outline=outline)
    if top_hilite:
        d.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=top_hilite)


# ---- panel body: vertical gradient so it does not read as a flat slab
for y in range(PANEL_H):
    t = y / (PANEL_H - 1)
    col = tuple(round(PANEL_TOP[i] + (PANEL_BOT[i] - PANEL_TOP[i]) * t) for i in range(3)) + (255,)
    d.line([(0, y), (PANEL_W - 1, y)], fill=col)

# ---- outer frame with a bevel, corners knocked out for a rounded feel
d.rectangle([0, 0, PANEL_W - 1, PANEL_H - 1], outline=FRAME)
d.rectangle([1, 1, PANEL_W - 2, PANEL_H - 2], outline=HILITE)
d.line([(2, PANEL_H - 3), (PANEL_W - 3, PANEL_H - 3)], fill=SHADOW)
d.line([(PANEL_W - 3, 2), (PANEL_W - 3, PANEL_H - 3)], fill=SHADOW)
for cx, cy in ((0, 0), (PANEL_W - 1, 0), (0, PANEL_H - 1), (PANEL_W - 1, PANEL_H - 1)):
    d.point((cx, cy), fill=(0, 0, 0, 0))

# ---- title bar, y 4..20
box(5, 4, PANEL_W - 6, 20, TITLE_BAR, FRAME, HILITE)
d.line([(6, 5), (PANEL_W - 7, 5)], fill=GOLD_SOFT)
d.line([(6, 19), (PANEL_W - 7, 19)], fill=GOLD_SOFT)

# ---- balance card, x 132..240, y 23..47
box(132, 23, 240, 47, CARD, GOLD_SOFT)
d.line([(133, 24), (239, 24)], fill=(30, 46, 58, 255))

# ---- table, y 53..148
box(5, 53, PANEL_W - 6, 148, TABLE_BG, FRAME)
d.rectangle([6, 54, PANEL_W - 7, 70], fill=HEAD_BAND)
d.line([(6, 71), (PANEL_W - 7, 71)], fill=GOLD_SOFT)
for index, y in enumerate(ROW_Y):
    d.rectangle([6, y - 1, PANEL_W - 7, y + 21], fill=ROW_A if index % 2 == 0 else ROW_B)
# rule separating the figures from the buttons
d.line([(RULE_X, 54), (RULE_X, 147)], fill=FRAME)
d.line([(RULE_X + 1, 72), (RULE_X + 1, 147)], fill=(38, 58, 72, 255))

# ---- footer band, y 152..172, roomy enough for two lines of text
box(5, 152, PANEL_W - 6, 172, CARD, FRAME)

img.save(os.path.join(GUI, "atm.png"))
print(f"atm.png written ({PANEL_W}x{PANEL_H}) - navy bank terminal")
