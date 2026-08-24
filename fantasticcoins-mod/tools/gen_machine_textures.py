#!/usr/bin/env python3
"""
Draws every block texture for the three machines: the ATM, the bank terminal and the central bank.

Drawn here rather than downloaded. A texture found online arrives with unknown licensing and it is
the mod's author who would be redistributing it inside their own jar, so the whole set is generated:
the palette, the bands and every pixel are defined below and reproducible. Run it and the textures in
the resource folder are exactly what this file says they are.

Four decisions worth stating.

*32x32 everywhere.* The terminals were still 16x16 while the ATM had already moved to 32. Sixteen
pixels could not hold a panelled counter, a monitor and a nameplate, so the terminals read as plain
brown cubes - which is the "the central bank and terminal textures look simple" complaint. 32 is a
multiple of 16, which is all mipmapping asks for.

*Two halves, two texture sets.* Each machine is two blocks tall now, so each has a body set and a
head set. The head carries the screen and the trim; the body carries the controls and the panelling.
Nothing is drawn twice, so nothing can disagree with itself.

*One tinted surface per machine, and only on the ATM.* `atm_accent` is drawn in greys and multiplied
by the issuing bank's colour at render time. It is a band rather than the whole cabinet because a
tinted cabinet stops being brushed steel and becomes a block of flat colour, and because a 1px outline
of the same colour has to stay readable in the GUI as well.

*Three different materials, on purpose.* Steel for the cash machine, wood and brass for the teller
counter, marble and gold for the central bank. They are three different kinds of thing and should not
share a palette; the previous set was three variations on brown-grey.

Reference for what these objects actually have - ATM parts (screen, keypad, card reader, cash
dispenser, receipt slot), and the pilaster/pediment vocabulary the central bank borrows:
- https://atmtrader.com/blogs/news/understanding-atm-parts
- https://money.howstuffworks.com/personal-finance/banking/atm3.htm
- https://en.wikipedia.org/wiki/Pilaster
Minecraft style and mipmap constraints:
- https://www.blockbench.net/wiki/guides/minecraft-style-guide
- https://gist.github.com/HalbFettKaese/c193caeccc94b793b29981aa38170ea6
"""
import os
import sys

from PIL import Image, ImageDraw

S = 32  # a multiple of 16, so mipmapping stays well-behaved
LAST = S - 1

BLOCK = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "../src/main/resources/assets/athens_coins/textures/block")
os.makedirs(BLOCK, exist_ok=True)

written = []


def new(fill):
    img = Image.new("RGBA", (S, S), fill)
    return img, ImageDraw.Draw(img)


def save(img, name):
    img.save(os.path.join(BLOCK, name + ".png"))
    written.append(name)


def bevel(d, light, dark):
    """One lit edge top-left, one shaded edge bottom-right: the usual cue that a face is solid."""
    d.line([(0, 0), (LAST - 1, 0)], fill=light)
    d.line([(0, 0), (0, LAST - 1)], fill=light)
    d.line([(0, LAST), (LAST, LAST)], fill=dark)
    d.line([(LAST, 0), (LAST, LAST)], fill=dark)


def inset(d, x0, y0, x1, y1, dark, light, fill=None):
    """A recessed panel: shadow on the top-left, highlight on the bottom-right."""
    if fill is not None:
        d.rectangle([x0, y0, x1, y1], fill=fill)
    d.line([(x0, y0), (x1, y0)], fill=dark)
    d.line([(x0, y0), (x0, y1)], fill=dark)
    d.line([(x0, y1), (x1, y1)], fill=light)
    d.line([(x1, y0), (x1, y1)], fill=light)


# ====================================================================== ATM
# Brushed steel, brass trim, a green display. The old set was four mid-greys within 66 levels of each
# other, which is why nothing on the front stood out; the body tones are spread and the screen and the
# brass carry the contrast.
A_DARK = (24, 26, 32, 255)
A_BODY = (46, 50, 59, 255)
A_LIGHT = (70, 76, 88, 255)
A_HI = (102, 110, 124, 255)
A_HOOD = (33, 36, 43, 255)

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


def atm_brass(d, y0, y1, highlight=None):
    d.rectangle([2, y0, S - 3, y1], fill=GOLD_DIM)
    d.line([(2, y0), (S - 3, y0)], fill=GOLD)
    if highlight:
        d.rectangle([highlight[0], y0, highlight[1], y1], fill=GOLD)
    d.line([(2, y1), (S - 3, y1)], fill=GOLD_DEEP)


def atm_body():
    """
    The lower front: the controls, and nothing else.

    The screen moved to the head, which freed the whole face for the things a hand touches. The keypad
    gets a real 3x4 grid instead of six suggestive dots, and the card reader and the receipt slot stop
    competing with a display for the same fourteen pixels.

    Rows 21-27, columns 6-26 sit behind the protruding cash tray element, so the dispenser recess is
    drawn there and nothing else is.
    """
    img, d = new(A_BODY)
    bevel(d, A_HI, A_DARK)
    d.rectangle([1, 1, S - 2, 2], fill=A_DARK)          # shadow cast by the head above
    atm_brass(d, 3, 4)

    # --- keypad, left: three columns, four rows, on a raised plate
    inset(d, 2, 7, 15, 19, A_DARK, A_HI, fill=A_LIGHT)
    for row in range(4):
        for col in range(3):
            kx = 4 + col * 4
            ky = 9 + row * 3
            d.rectangle([kx, ky, kx + 2, ky + 1], fill=KEY)
            d.line([(kx, ky + 1), (kx + 2, ky + 1)], fill=KEY_DARK)

    # --- card reader, right: a lit throat, which is the one part a user has to find first
    inset(d, 17, 7, 29, 12, A_DARK, A_HI, fill=A_LIGHT)
    d.rectangle([18, 9, 28, 10], fill=SLOT)
    d.line([(19, 9), (27, 9)], fill=GOLD_DIM)
    d.point((28, 8), fill=LED_RED)

    # --- receipt slot, right, below the reader
    inset(d, 17, 14, 29, 19, A_DARK, A_HI, fill=A_LIGHT)
    d.rectangle([18, 16, 26, 17], fill=SLOT)
    d.line([(18, 15), (26, 15)], fill=SLOT_EDGE)

    # --- dispenser recess, behind the tray element
    atm_brass(d, 20, 20)
    d.rectangle([6, 21, 26, 27], fill=A_DARK)
    d.rectangle([7, 22, 25, 26], fill=SLOT)
    # --- plinth shadow
    d.rectangle([1, 29, S - 2, S - 2], fill=A_DARK)
    save(img, "atm_front")


def atm_head():
    """
    The upper front: one large display, and the brand band above it.

    Rows 1-7 are covered by the tinted band element, so the band's own texture is what shows there and
    this face only needs to not fight it.
    """
    img, d = new(A_BODY)
    bevel(d, A_HI, A_DARK)
    d.rectangle([1, 1, S - 2, 7], fill=A_LIGHT)         # behind the band, if ever seen
    atm_brass(d, 8, 9)

    # --- the display: bezel, glass, and a few lines of text
    inset(d, 2, 11, 29, 29, A_DARK, A_HI, fill=SLOT_EDGE)
    d.rectangle([4, 13, 27, 27], fill=SCREEN_BG)
    d.line([(4, 13), (27, 13)], fill=(30, 78, 66, 255))  # glass sheen
    d.line([(4, 13), (4, 27)], fill=(21, 58, 50, 255))
    d.line([(6, 15), (21, 15)], fill=SCREEN_GLOW)
    d.line([(6, 18), (18, 18)], fill=SCREEN_LINE)
    d.line([(6, 20), (15, 20)], fill=SCREEN_LINE)
    d.line([(6, 22), (19, 22)], fill=SCREEN_LINE)
    d.line([(6, 25), (12, 25)], fill=SCREEN_GLOW)
    # a currency glyph, so the display is unmistakably a money screen
    d.rectangle([22, 17, 25, 21], fill=SCREEN_HI)
    d.rectangle([23, 18, 24, 20], fill=SCREEN_BG)
    d.line([(23, 16), (23, 22)], fill=SCREEN_HI)
    d.rectangle([1, 30, S - 2, S - 2], fill=A_DARK)     # seam with the body below
    save(img, "atm_head_front")


def atm_accent():
    """
    The band the bank's colour lands on.

    Drawn near-white with soft shading because the render multiplies it: a mid-grey band would come out
    as a muddy version of whatever colour was chosen, and a pure flat white would come out as a flat
    colour with no shape at all. The brushed streaks survive the multiply and keep the band reading as
    painted metal.
    """
    img, d = new((236, 238, 242, 255))
    d.line([(0, 0), (LAST, 0)], fill=(255, 255, 255, 255))
    for y in range(1, S):
        shade = 236 - int(y * 74 / S)
        d.line([(0, y), (LAST, y)], fill=(shade, shade + 2, shade + 6, 255))
    for x in range(0, S, 3):                            # brushed streaks
        d.line([(x, 2), (x, S - 4)], fill=(214, 217, 222, 255))
    d.line([(0, S - 3), (LAST, S - 3)], fill=(128, 130, 134, 255))
    d.rectangle([0, S - 2, LAST, LAST], fill=(88, 90, 94, 255))
    save(img, "atm_accent")


def atm_sides():
    for name, head in (("atm_side", False), ("atm_head_side", True)):
        img, d = new(A_BODY)
        bevel(d, A_HI, A_DARK)
        if head:
            atm_brass(d, 2, 3, highlight=(12, 20))
            inset(d, 4, 6, 27, 28, A_DARK, A_HI, fill=A_BODY)
            for vy in range(9, 26, 3):                  # louvres: heat has to leave somewhere
                d.line([(8, vy), (23, vy)], fill=A_DARK)
                d.line([(8, vy + 1), (23, vy + 1)], fill=A_LIGHT)
        else:
            inset(d, 3, 3, 28, 28, A_DARK, A_HI, fill=A_BODY)
            for vy in range(7, 26, 4):
                d.line([(7, vy), (24, vy)], fill=A_DARK)
                d.line([(7, vy + 1), (24, vy + 1)], fill=A_LIGHT)
        save(img, name)

    img, d = new(A_BODY)
    bevel(d, A_HI, A_DARK)
    inset(d, 4, 5, 27, 27, A_DARK, A_HI, fill=A_BODY)
    for bx in (6, 25):
        for by in (7, 25):
            d.point((bx, by), fill=A_DARK)
    d.rectangle([13, 27, 18, S - 1], fill=SLOT)          # conduit leaving the floor
    save(img, "atm_back")

    img, d = new(A_BODY)
    bevel(d, A_HI, A_DARK)
    inset(d, 5, 6, 26, 26, A_DARK, A_HI, fill=A_LIGHT)
    d.rectangle([11, 12, 20, 20], fill=A_DARK)           # service lock plate
    d.rectangle([12, 13, 19, 19], fill=A_BODY)
    d.point((15, 16), fill=GOLD_DIM)
    save(img, "atm_head_back")


def atm_top_hood():
    # No mid-face brass stripe: it landed across the middle of the canopy in the inventory view and
    # read as a stray gold bar rather than trim. The brass sits on the front lip only.
    img, d = new(A_HOOD)
    bevel(d, A_HI, A_DARK)
    inset(d, 3, 3, 28, 28, A_LIGHT, A_DARK, fill=A_HOOD)
    d.rectangle([4, 4, 27, 5], fill=GOLD_DIM)            # front lip
    d.line([(4, 4), (27, 4)], fill=GOLD)
    inset(d, 10, 12, 21, 20, A_DARK, A_LIGHT, fill=A_HOOD)
    d.point((26, 7), fill=SCREEN_GLOW)                   # status light, near the front lip
    save(img, "atm_top")

    img, d = new(A_HOOD)
    d.line([(0, 0), (LAST, 0)], fill=A_HI)
    d.line([(0, LAST), (LAST, LAST)], fill=A_DARK)
    d.rectangle([0, 8, LAST, 16], fill=GOLD_DIM)
    d.line([(0, 8), (LAST, 8)], fill=GOLD)
    d.line([(0, 16), (LAST, 16)], fill=GOLD_DEEP)
    save(img, "atm_hood")


def atm_bottom_tray():
    img, d = new(A_DARK)
    d.rectangle([1, 1, S - 2, S - 2], fill=A_BODY)
    inset(d, 4, 4, S - 5, S - 5, A_DARK, A_LIGHT, fill=A_BODY)
    for fx in (5, 24):
        for fy in (5, 24):
            d.rectangle([fx, fy, fx + 2, fy + 2], fill=A_DARK)
    save(img, "atm_bottom")

    # The dispenser. Its own texture because it is a separate element in front of the body: reusing the
    # body texture here would have printed a slice of keypad across the tray.
    img, d = new(A_LIGHT)
    d.rectangle([0, 0, LAST, 3], fill=GOLD_DIM)
    d.line([(0, 0), (LAST, 0)], fill=GOLD)
    d.line([(0, 3), (LAST, 3)], fill=GOLD_DEEP)
    d.rectangle([1, 5, 30, 26], fill=A_DARK)
    d.rectangle([2, 7, 29, 24], fill=SLOT)               # the mouth itself
    d.line([(2, 6), (29, 6)], fill=SLOT_EDGE)
    d.rectangle([0, 28, LAST, LAST], fill=A_DARK)
    d.line([(0, 27), (LAST, 27)], fill=A_HI)
    save(img, "atm_tray")


# ============================================================ BANK TERMINAL
# A teller counter: dark wood, brass trim, an amber screen. Warmer and more official than the cash
# machine, because it is staffed.
W_DARK = (38, 26, 20, 255)
W_BODY = (72, 50, 38, 255)
W_LIGHT = (104, 74, 55, 255)
W_HI = (134, 99, 74, 255)
BRASS = (206, 164, 72, 255)
BRASS_DIM = (150, 116, 48, 255)
BRASS_DEEP = (96, 74, 30, 255)
AMBER_BG = (26, 20, 12, 255)
AMBER = (244, 198, 96, 255)
AMBER_DIM = (150, 116, 52, 255)
FELT = (34, 66, 52, 255)
FELT_LIGHT = (48, 88, 68, 255)


def wood_grain(d, x0, y0, x1, y1, tone):
    """Grain as broken horizontal lines: continuous ones read as stripes, not timber."""
    for y in range(y0, y1 + 1, 3):
        d.line([(x0 + (y % 3), y), (x1 - ((y + 1) % 4), y)], fill=tone)


def brass_band(d, y0, y1):
    d.rectangle([0, y0, LAST, y1], fill=BRASS_DIM)
    d.line([(0, y0), (LAST, y0)], fill=BRASS)
    d.line([(0, y1), (LAST, y1)], fill=BRASS_DEEP)


def terminal_body():
    """The counter front: three panels, a brass top rail and a kick rail, as a real counter has."""
    img, d = new(W_BODY)
    bevel(d, W_HI, W_DARK)
    wood_grain(d, 1, 1, S - 2, S - 2, W_LIGHT)
    brass_band(d, 2, 4)
    for x0 in (2, 12, 22):
        inset(d, x0, 8, x0 + 8, 25, W_DARK, W_HI, fill=W_LIGHT)
        wood_grain(d, x0 + 1, 9, x0 + 7, 24, W_BODY)
    brass_band(d, 28, 29)
    d.rectangle([0, 30, LAST, LAST], fill=W_DARK)        # kick shadow at the floor
    save(img, "bank_terminal_front")

    img, d = new(W_BODY)
    bevel(d, W_HI, W_DARK)
    wood_grain(d, 1, 1, S - 2, S - 2, W_LIGHT)
    inset(d, 4, 5, 27, 27, W_DARK, W_HI, fill=W_LIGHT)
    wood_grain(d, 5, 6, 26, 26, W_BODY)
    save(img, "bank_terminal_side")

    # The teller's side: two drawers with brass pulls. This is the face staff stand at, and drawers are
    # what tells you which side that is.
    img, d = new(W_BODY)
    bevel(d, W_HI, W_DARK)
    wood_grain(d, 1, 1, S - 2, S - 2, W_LIGHT)
    for y0 in (4, 18):
        inset(d, 3, y0, 28, y0 + 10, W_DARK, W_HI, fill=W_LIGHT)
        d.rectangle([12, y0 + 4, 19, y0 + 5], fill=BRASS_DIM)
        d.line([(12, y0 + 4), (19, y0 + 4)], fill=BRASS)
    save(img, "bank_terminal_back")

    # The worktop: green desk felt inside a brass edge. Wood here would have made the whole block one
    # tone and lost the counter's top edge entirely.
    img, d = new(BRASS_DIM)
    d.rectangle([0, 0, LAST, 1], fill=BRASS)
    d.rectangle([0, LAST - 1, LAST, LAST], fill=BRASS_DEEP)
    d.rectangle([3, 3, 28, 28], fill=FELT)
    inset(d, 3, 3, 28, 28, BRASS_DEEP, BRASS, fill=None)
    for y in range(5, 27, 2):
        d.line([(5, y), (26, y)], fill=FELT_LIGHT)
    save(img, "bank_terminal_top")

    img, d = new(W_DARK)
    d.rectangle([2, 2, S - 3, S - 3], fill=W_BODY)
    wood_grain(d, 3, 3, S - 4, S - 4, W_DARK)
    save(img, "bank_terminal_bottom")


def terminal_head():
    """The back panel a customer sees: a brass nameplate and a barred teller window."""
    img, d = new(W_BODY)
    bevel(d, W_HI, W_DARK)
    wood_grain(d, 1, 1, S - 2, S - 2, W_LIGHT)
    brass_band(d, 3, 6)
    d.rectangle([6, 4, 25, 5], fill=BRASS)               # the engraved line of a nameplate
    inset(d, 4, 10, 27, 27, W_DARK, W_HI, fill=AMBER_BG)
    for bx in range(7, 26, 4):                           # the grille
        d.line([(bx, 11), (bx, 26)], fill=BRASS_DIM)
        d.line([(bx + 1, 11), (bx + 1, 26)], fill=BRASS_DEEP)
    d.line([(5, 24), (26, 24)], fill=BRASS_DIM)          # the pass-through shelf
    save(img, "bank_terminal_head_front")

    img, d = new(W_BODY)
    bevel(d, W_HI, W_DARK)
    wood_grain(d, 1, 1, S - 2, S - 2, W_LIGHT)
    brass_band(d, 3, 4)
    inset(d, 5, 8, 26, 27, W_DARK, W_HI, fill=W_LIGHT)
    save(img, "bank_terminal_head_side")

    # A brass crown for the head. It needs its own texture rather than borrowing the worktop's: the
    # worktop is green desk felt, and a felt cap on top of the panel read as a second counter surface
    # floating above the first.
    img, d = new(BRASS_DIM)
    d.rectangle([0, 0, LAST, 1], fill=BRASS)
    d.rectangle([0, LAST - 1, LAST, LAST], fill=BRASS_DEEP)
    for x in range(2, S, 4):
        d.line([(x, 3), (x, S - 4)], fill=BRASS)
    inset(d, 4, 6, 27, 25, BRASS_DEEP, BRASS, fill=None)
    save(img, "bank_terminal_cornice")

    # Pigeonholes: paperwork has to live somewhere, and it marks the staff side of the head too.
    img, d = new(W_BODY)
    bevel(d, W_HI, W_DARK)
    for row in range(3):
        for col in range(4):
            x0 = 2 + col * 7
            y0 = 4 + row * 9
            inset(d, x0, y0, x0 + 6, y0 + 7, W_DARK, W_HI, fill=W_DARK)
            d.rectangle([x0 + 1, y0 + 1, x0 + 5, y0 + 2], fill=(196, 186, 168, 255))
    save(img, "bank_terminal_head_back")

    # The monitor: an account list, which is exactly what this screen shows in game.
    img, d = new(W_DARK)
    bevel(d, W_HI, (20, 14, 10, 255))
    d.rectangle([2, 2, 29, 29], fill=AMBER_BG)
    d.line([(3, 4), (18, 4)], fill=AMBER)                # title
    d.line([(3, 6), (28, 6)], fill=AMBER_DIM)            # rule
    for i, width in enumerate((22, 18, 24, 15, 20, 12)):
        y = 9 + i * 3
        d.line([(4, y), (4 + width, y)], fill=AMBER_DIM if i % 2 else AMBER)
        d.line([(26, y), (28, y)], fill=AMBER)           # the right-aligned amount column
    d.rectangle([3, 27, 12, 28], fill=AMBER_DIM)
    save(img, "bank_terminal_screen")


# ============================================================== CENTRAL BANK
# Pale marble, deep navy, gold. It is the one machine that should look like a building rather than a
# piece of equipment, so it borrows a facade's vocabulary: fluted pilasters, a plinth and a pediment.
M_SHADOW = (108, 106, 116, 255)
M_DARK = (146, 144, 154, 255)
M_BODY = (188, 187, 196, 255)
M_LIGHT = (216, 216, 224, 255)
M_HI = (240, 240, 246, 255)
NAVY = (22, 30, 52, 255)
NAVY_LIGHT = (38, 50, 82, 255)
G_GOLD = (240, 200, 96, 255)
G_DIM = (176, 142, 62, 255)
G_DEEP = (112, 90, 38, 255)
TICKER = (126, 226, 168, 255)


# ========================================================== STATS PROJECTOR
# Dark metal and cyan light. Deliberately not sharing a palette with any of the three banks: this is a
# display, not a counter, and it should not read as a fourth kind of bank furniture.
H_DARK = (16, 22, 28, 255)
H_BODY = (34, 44, 54, 255)
H_HI = (74, 92, 108, 255)
H_GLOW = (126, 226, 244, 255)
H_GLOW_DIM = (46, 122, 142, 255)


def marble(d, x0, y0, x1, y1):
    """Veining: a few diagonal strokes. Marble with no veins is just grey."""
    for i, (vx, vy) in enumerate(((3, 6), (14, 2), (22, 11), (8, 20), (25, 22))):
        tone = M_DARK if i % 2 else M_LIGHT
        for step in range(5):
            px = vx + step
            py = vy + (step // 2)
            if x0 <= px <= x1 and y0 <= py <= y1:
                d.point((px, py), fill=tone)


def pilasters(d, y0, y1, columns=(3, 11, 19, 27)):
    """Fluted half-columns. The single cue that turns a grey slab into a bank facade."""
    for cx in columns:
        d.line([(cx, y0), (cx, y1)], fill=M_HI)
        d.line([(cx + 1, y0), (cx + 1, y1)], fill=M_SHADOW)
        d.line([(cx + 2, y0), (cx + 2, y1)], fill=M_LIGHT)


def gold_band(d, y0, y1):
    d.rectangle([0, y0, LAST, y1], fill=G_DIM)
    d.line([(0, y0), (LAST, y0)], fill=G_GOLD)
    d.line([(0, y1), (LAST, y1)], fill=G_DEEP)


def central_body():
    img, d = new(M_BODY)
    bevel(d, M_HI, M_SHADOW)
    marble(d, 1, 1, S - 2, S - 2)
    gold_band(d, 2, 3)
    pilasters(d, 6, 26)
    gold_band(d, 28, 29)
    d.rectangle([0, 30, LAST, LAST], fill=M_SHADOW)
    save(img, "central_bank_front")

    img, d = new(M_BODY)
    bevel(d, M_HI, M_SHADOW)
    marble(d, 1, 1, S - 2, S - 2)
    gold_band(d, 2, 3)
    pilasters(d, 6, 27, columns=(5, 14, 23))
    save(img, "central_bank_side")

    img, d = new(M_BODY)
    bevel(d, M_HI, M_SHADOW)
    marble(d, 1, 1, S - 2, S - 2)
    inset(d, 6, 8, 25, 24, M_SHADOW, M_HI, fill=M_DARK)
    for vy in range(10, 24, 3):                          # a service grille
        d.line([(8, vy), (23, vy)], fill=NAVY)
        d.line([(8, vy + 1), (23, vy + 1)], fill=M_LIGHT)
    save(img, "central_bank_back")

    img, d = new(M_DARK)
    d.rectangle([2, 2, S - 3, S - 3], fill=M_BODY)
    marble(d, 3, 3, S - 4, S - 4)
    save(img, "central_bank_bottom")

    # The pediment's top face, seen from above: gold inlay round a medallion.
    img, d = new(M_LIGHT)
    bevel(d, M_HI, M_SHADOW)
    marble(d, 2, 2, S - 3, S - 3)
    inset(d, 3, 3, 28, 28, G_DIM, G_GOLD, fill=None)
    inset(d, 6, 6, 25, 25, M_SHADOW, M_HI, fill=M_BODY)
    d.ellipse([12, 12, 19, 19], fill=G_DIM, outline=G_GOLD)
    d.rectangle([15, 13, 16, 18], fill=G_GOLD)           # a currency glyph on the medallion
    d.line([(13, 14), (18, 14)], fill=G_DEEP)
    d.line([(13, 17), (18, 17)], fill=G_DEEP)
    save(img, "central_bank_top")


def hologram_projector():
    """
    The stats projector: a dark ring of emitters on a metal plinth.

    Cyan rather than the ATM's green or the central bank's gold, because it is the one block here that
    is not a bank - it is a display, and it should read as one from across a square. The lens ring is
    what says "this thing projects": a plain plate with a light on it would look like a pressure plate.
    """
    img, d = new(H_BODY)
    bevel(d, H_HI, H_DARK)
    d.rectangle([3, 3, 28, 28], fill=H_DARK)
    # The emitter ring, with the lens itself brightest at the centre.
    d.ellipse([5, 5, 26, 26], fill=H_BODY, outline=H_GLOW_DIM)
    d.ellipse([8, 8, 23, 23], fill=(10, 22, 30, 255), outline=H_GLOW)
    d.ellipse([12, 12, 19, 19], fill=H_GLOW_DIM, outline=H_GLOW)
    d.ellipse([14, 14, 17, 17], fill=(226, 253, 255, 255))
    # Four mounting bolts, so the top does not read as a hole in the floor.
    for bx, by in ((4, 4), (27, 4), (4, 27), (27, 27)):
        d.rectangle([bx - 1, by - 1, bx + 1, by + 1], fill=H_HI)
    save(img, "stats_hologram_top")

    img, d = new(H_BODY)
    bevel(d, H_HI, H_DARK)
    # Vents low down and a lit strip along the top, which is the face a player sees at eye level.
    d.rectangle([0, 1, LAST, 4], fill=H_GLOW_DIM)
    d.line([(0, 1), (LAST, 1)], fill=H_GLOW)
    d.line([(0, 4), (LAST, 4)], fill=(12, 40, 52, 255))
    inset(d, 3, 8, 28, 27, H_DARK, H_HI, fill=H_BODY)
    for vy in range(11, 26, 3):
        d.line([(6, vy), (25, vy)], fill=H_DARK)
        d.line([(6, vy + 1), (25, vy + 1)], fill=H_HI)
    save(img, "stats_hologram_side")

    img, d = new(H_DARK)
    d.rectangle([2, 2, S - 3, S - 3], fill=H_BODY)
    inset(d, 6, 6, 25, 25, H_DARK, H_HI, fill=H_DARK)
    for fx in (7, 24):
        for fy in (7, 24):
            d.rectangle([fx - 1, fy - 1, fx + 1, fy + 1], fill=H_HI)
    save(img, "stats_hologram_bottom")


def central_head():
    img, d = new(M_BODY)
    bevel(d, M_HI, M_SHADOW)
    marble(d, 1, 1, S - 2, S - 2)
    gold_band(d, 1, 2)
    # Pilasters flanking the recess the rate board sits in, which is why they stop where they do.
    pilasters(d, 5, 27, columns=(2, 27))
    inset(d, 6, 5, 25, 26, M_SHADOW, M_HI, fill=M_DARK)
    save(img, "central_bank_head_front")

    img, d = new(M_BODY)
    bevel(d, M_HI, M_SHADOW)
    marble(d, 1, 1, S - 2, S - 2)
    gold_band(d, 1, 2)
    pilasters(d, 5, 28, columns=(6, 15, 24))
    save(img, "central_bank_head_side")

    img, d = new(M_BODY)
    bevel(d, M_HI, M_SHADOW)
    marble(d, 1, 1, S - 2, S - 2)
    gold_band(d, 1, 2)
    inset(d, 5, 6, 26, 26, M_SHADOW, M_HI, fill=M_LIGHT)
    save(img, "central_bank_head_back")

    # The rate board: three rows, one per coin, each a glyph and a value. The screen this machine
    # actually shows is a rate table, and the block should say so from across the square.
    img, d = new(NAVY)
    bevel(d, NAVY_LIGHT, (12, 16, 28, 255))
    d.rectangle([2, 2, 29, 5], fill=NAVY_LIGHT)
    d.line([(4, 3), (18, 3)], fill=G_GOLD)               # the board's title
    for i, (colour, width) in enumerate(((G_DEEP, 14), (M_DARK, 18), (G_GOLD, 22))):
        y = 9 + i * 7
        d.ellipse([3, y, 8, y + 5], fill=colour, outline=M_HI)
        d.line([(11, y + 1), (11 + width, y + 1)], fill=TICKER)
        d.line([(11, y + 3), (11 + width - 6, y + 3)], fill=M_DARK)
    save(img, "central_bank_screen")


# ==================================================================== run
atm_body()
atm_head()
atm_accent()
atm_sides()
atm_top_hood()
atm_bottom_tray()
terminal_body()
terminal_head()
central_body()
central_head()
hologram_projector()

print(f"{len(written)} machine textures written at {S}x{S} to {os.path.relpath(BLOCK)}")
for name in written:
    print("  ", name + ".png")

# ---------------------------------------------------------------- preview
if "--preview" in sys.argv:
    wanted = [a for a in sys.argv[1:] if not a.startswith("-")] or written
    for name in wanted:
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
