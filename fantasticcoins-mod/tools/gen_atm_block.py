#!/usr/bin/env python3
"""
Draws the ATM block textures.

Drawn here rather than downloaded. A texture found online arrives with unknown licensing and it is
the mod's author who would be redistributing it inside their own jar, so the whole set is generated:
the palette, the bands and every pixel are defined below and reproducible.

Three decisions worth stating.

*32x32, not 16x16.* The old front had to fit a screen, a keypad, a card slot and a cash mouth into
sixteen pixels and none of them were legible - at play distance the block read as a plain grey cube.
Doubling the grid stays a multiple of 16, which is what mipmapping cares about.

*Shape in the model, detail in the texture.* Minecraft's style guide puts the silhouette in the model
and the detail in the texture, with as few elements as possible. The model contributes a plinth, a
hood and a protruding cash tray; everything else is here.

*The cash mouth belongs to the tray, not to the front.* Drawing it on the body texture as well left
the lower half of the front fighting for space between a keypad, two slots and a mouth - and the
protruding tray then covered whatever was behind it. Each part is drawn once, on the surface it
physically is, which is also what decides the vertical order below: hood, screen, card and receipt,
keypad, and the tray last, at the very bottom where the model puts it.

Reference for what an ATM actually has (screen, keypad, card reader, cash dispenser, receipt slot):
- https://atmtrader.com/blogs/news/understanding-atm-parts
- https://money.howstuffworks.com/personal-finance/banking/atm3.htm
Minecraft style and mipmap constraints:
- https://www.blockbench.net/wiki/guides/minecraft-style-guide
- https://gist.github.com/HalbFettKaese/c193caeccc94b793b29981aa38170ea6
"""
import os
import sys

from PIL import Image, ImageDraw

S = 32  # a multiple of 16, so mipmapping stays well-behaved

BLOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "../src/main/resources/assets/athens_coins/textures/block")

# ---------------------------------------------------------------- palette
# The old set was four mid-greys within 66 levels of each other, which is why nothing on the front
# stood out. This spreads the body tones and lets the screen and the brass carry the contrast.
BODY_DARK = (24, 26, 32, 255)
BODY = (46, 50, 59, 255)
BODY_LIGHT = (70, 76, 88, 255)
BODY_HI = (102, 110, 124, 255)
HOOD = (33, 36, 43, 255)

SLOT = (10, 11, 13, 255)
SLOT_EDGE = (17, 19, 23, 255)

SCREEN_BG = (7, 20, 19, 255)
SCREEN_LINE = (38, 112, 90, 255)
SCREEN_GLOW = (110, 232, 168, 255)
SCREEN_HI = (204, 255, 228, 255)

GOLD = (255, 201, 60, 255)
GOLD_DIM = (176, 136, 42, 255)
GOLD_DEEP = (116, 86, 26, 255)

KEY = (162, 168, 178, 255)
KEY_DARK = (92, 98, 108, 255)
LED_RED = (230, 78, 78, 255)


def new(fill=BODY):
    img = Image.new("RGBA", (S, S), fill)
    return img, ImageDraw.Draw(img)


def bevel(d, light=BODY_HI, dark=BODY_DARK):
    """One lit pixel top-left, one shaded bottom-right: the usual cue that a face is solid."""
    d.line([(0, 0), (S - 2, 0)], fill=light)
    d.line([(0, 0), (0, S - 2)], fill=light)
    d.line([(0, S - 1), (S - 1, S - 1)], fill=dark)
    d.line([(S - 1, 0), (S - 1, S - 1)], fill=dark)


def brass_band(d, y0, y1, highlight=(11, 20)):
    """A brass band; the strongest 'bank machine' cue the block has."""
    d.rectangle([2, y0, S - 3, y1], fill=GOLD_DIM)
    d.line([(2, y0), (S - 3, y0)], fill=GOLD)
    if highlight:
        d.rectangle([highlight[0], y0, highlight[1], y1], fill=GOLD)
    d.line([(2, y1), (S - 3, y1)], fill=GOLD_DEEP)


def save(img, name):
    img.save(os.path.join(BLOCK, name))


# ================================================================ front
# The tray covers block rows y=2..4, which on a face spanning y=2..14 is the bottom sixth of the
# texture: rows 27 and below. Everything is laid out inside rows 0..26.
#
# Two things were fixed here after looking at a render of the finished block. The screen was spread
# across a third of the height and 75% of the width, so it came out as a letterbox strip rather than a
# display; it now takes half the visible height. And there was a brass band along the top edge, which
# sat directly under the hood's own brass and read as a doubled stripe - the accents are the hood and
# the tray lip, with one thin divider here.
FRONT_HIDDEN_FROM = 27

img, d = new()
bevel(d)
# Shadow under the overhanging hood.
d.rectangle([1, 1, S - 2, 1], fill=BODY_DARK)

# --- screen: half the visible height, and narrower than before so it reads as a display
d.rectangle([3, 3, 28, 16], fill=BODY_DARK)
d.rectangle([4, 4, 27, 15], fill=SLOT_EDGE)
d.rectangle([5, 5, 26, 14], fill=SCREEN_BG)
d.line([(7, 7), (20, 7)], fill=SCREEN_GLOW)
d.line([(7, 9), (17, 9)], fill=SCREEN_LINE)
d.line([(7, 11), (14, 11)], fill=SCREEN_LINE)
d.line([(7, 12), (11, 12)], fill=SCREEN_LINE)
# a currency glyph, so the display is unmistakably a money screen
d.rectangle([21, 8, 23, 11], fill=SCREEN_HI)
d.point((22, 9), fill=SCREEN_BG)
d.line([(22, 7), (22, 12)], fill=SCREEN_HI)
# glass sheen along the top and left of the display
d.line([(5, 5), (26, 5)], fill=(30, 78, 66, 255))
d.line([(5, 5), (5, 14)], fill=(21, 58, 50, 255))

# --- a single brass divider between the display and the controls
d.line([(3, 18), (28, 18)], fill=GOLD_DIM)
d.line([(3, 19), (28, 19)], fill=GOLD_DEEP)

# --- keypad, left, three rows of three keys
d.rectangle([2, 20, 15, 26], fill=BODY_DARK)
d.rectangle([3, 21, 14, 25], fill=BODY_LIGHT)
for row in range(2):
    for col in range(3):
        kx = 4 + col * 4
        ky = 22 + row * 2
        d.rectangle([kx, ky, kx + 1, ky], fill=KEY)
        d.point((kx + 1, ky + 1), fill=KEY_DARK)

# --- card reader and receipt slot, right
d.rectangle([17, 20, 29, 26], fill=BODY_DARK)
d.rectangle([18, 21, 28, 22], fill=SLOT)          # card reader throat
d.line([(19, 21), (27, 21)], fill=GOLD_DIM)
d.point((28, 21), fill=LED_RED)
d.rectangle([18, 24, 26, 25], fill=SLOT)          # receipt
# Below: plain body, hidden behind the tray in game.
d.rectangle([1, FRONT_HIDDEN_FROM, S - 2, S - 2], fill=BODY)
save(img, "atm_front.png")

# ================================================================ side
img, d = new()
bevel(d)
brass_band(d, 1, 3, highlight=None)
d.rectangle([4, 6, 27, 28], fill=BODY_LIGHT)
d.rectangle([5, 7, 26, 27], fill=BODY)
for vy in (10, 13, 16, 19, 22):
    d.line([(9, vy), (22, vy)], fill=BODY_DARK)
    d.line([(9, vy + 1), (22, vy + 1)], fill=BODY_LIGHT)
save(img, "atm_side.png")

# ================================================================ back
img, d = new()
bevel(d)
brass_band(d, 1, 3, highlight=None)
d.rectangle([5, 7, 26, 26], fill=BODY_LIGHT)
d.rectangle([6, 8, 25, 25], fill=BODY)
for bx in (7, 24):
    for by in (9, 24):
        d.point((bx, by), fill=BODY_DARK)
d.rectangle([13, 14, 18, 19], fill=BODY_DARK)      # lock plate
d.rectangle([14, 15, 17, 18], fill=BODY_LIGHT)
d.rectangle([14, 27, 17, 31], fill=SLOT)           # conduit leaving the floor
save(img, "atm_back.png")

# ================================================================ top (hood upper face)
# No mid-face brass stripe here: it landed across the middle of the hood in the inventory view and
# read as a stray gold bar rather than trim. The brass sits on the front lip only.
img, d = new(HOOD)
bevel(d)
d.rectangle([3, 3, 28, 28], fill=BODY_LIGHT)
d.rectangle([4, 4, 27, 27], fill=HOOD)
d.rectangle([4, 4, 27, 5], fill=GOLD_DIM)          # front lip
d.line([(4, 4), (27, 4)], fill=GOLD)
# A single shallow inset, centred. The earlier version had a panel with vent lines across it, which
# at the inventory angle read as a stray rectangle rather than as part of the machine.
d.rectangle([10, 12, 21, 20], fill=BODY_DARK)
d.rectangle([11, 13, 20, 19], fill=HOOD)
d.point((26, 7), fill=SCREEN_GLOW)                 # status light, near the front lip
save(img, "atm_top.png")

# ================================================================ hood sides
img, d = new(HOOD)
d.line([(0, 0), (S - 1, 0)], fill=BODY_HI)
d.line([(0, S - 1), (S - 1, S - 1)], fill=BODY_DARK)
d.rectangle([0, 6, S - 1, 12], fill=GOLD_DIM)
d.line([(0, 6), (S - 1, 6)], fill=GOLD)
d.line([(0, 12), (S - 1, 12)], fill=GOLD_DEEP)
save(img, "atm_hood.png")

# ================================================================ bottom
img, d = new(BODY_DARK)
d.rectangle([1, 1, S - 2, S - 2], fill=BODY)
d.rectangle([4, 4, S - 5, S - 5], fill=BODY_DARK)
d.rectangle([5, 5, S - 6, S - 6], fill=BODY)
for fx in (5, 24):
    for fy in (5, 24):
        d.rectangle([fx, fy, fx + 2, fy + 2], fill=BODY_DARK)
save(img, "atm_bottom.png")

# ================================================================ cash tray
# The dispenser. Its own texture because it is a separate element in front of the body: reusing the
# body texture here would have printed a slice of keypad across the tray.
img, d = new(BODY_LIGHT)
d.rectangle([0, 0, S - 1, 3], fill=GOLD_DIM)
d.line([(0, 0), (S - 1, 0)], fill=GOLD)
d.line([(0, 3), (S - 1, 3)], fill=GOLD_DEEP)
d.rectangle([1, 5, 30, 26], fill=BODY_DARK)
d.rectangle([2, 7, 29, 24], fill=SLOT)             # the mouth itself
d.line([(2, 6), (29, 6)], fill=SLOT_EDGE)
d.rectangle([0, 28, S - 1, S - 1], fill=BODY_DARK)
d.line([(0, 27), (S - 1, 27)], fill=BODY_HI)
save(img, "atm_tray.png")

print(f"ATM block textures written at {S}x{S} to {os.path.relpath(BLOCK)}")

# ---------------------------------------------------------------- preview
if "--preview" in sys.argv:
    for name in ("atm_front", "atm_tray", "atm_top", "atm_hood"):
        im = Image.open(os.path.join(BLOCK, name + ".png")).convert("RGBA")
        seen = {}
        print("=" * (S * 2 + 4))
        print(name)
        for y in range(S):
            row = ""
            for x in range(S):
                p = im.getpixel((x, y))
                if p[3] < 32:
                    row += ".."
                    continue
                if p not in seen:
                    seen[p] = chr(ord("A") + len(seen))
                row += seen[p] * 2
            print(" ", row)
        print("  colours:", len(seen))
