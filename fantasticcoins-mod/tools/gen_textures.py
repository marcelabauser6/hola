#!/usr/bin/env python3
"""Generates the ATM block textures, the ATM GUI panel and the mod logo for FantasticCoins."""
from PIL import Image, ImageDraw
import os

RES = "/projects/sandbox/hola/fantasticcoins-mod/src/main/resources/assets/athens_coins"
BLOCK = os.path.join(RES, "textures/block")
GUI = os.path.join(RES, "textures/gui")
ICONS = os.path.join(GUI, "icons")
for d in (BLOCK, GUI, ICONS):
    os.makedirs(d, exist_ok=True)

# ---------------------------------------------------------------- palette
CASE_DARK = (38, 40, 46, 255)
CASE_MID = (58, 62, 70, 255)
CASE_LIGHT = (80, 86, 96, 255)
CASE_HI = (104, 112, 124, 255)
SLOT = (16, 16, 18, 255)
SCREEN_BG = (10, 26, 24, 255)
SCREEN_DIM = (24, 62, 52, 255)
SCREEN_GLOW = (86, 214, 150, 255)
KEY = (150, 156, 166, 255)
GOLD = (255, 201, 60, 255)
RED_LED = (226, 74, 74, 255)


def new(fill=CASE_MID):
    img = Image.new("RGBA", (16, 16), fill)
    return img, ImageDraw.Draw(img)


def bevel(d):
    """1px lighter top/left edge, darker bottom/right - reads as a metal casing."""
    d.line([(0, 0), (15, 0)], fill=CASE_HI)
    d.line([(0, 0), (0, 15)], fill=CASE_LIGHT)
    d.line([(0, 15), (15, 15)], fill=CASE_DARK)
    d.line([(15, 0), (15, 15)], fill=CASE_DARK)


# ---------------------------------------------------------------- front
img, d = new()
bevel(d)
# screen recess + screen
d.rectangle([2, 2, 13, 8], fill=CASE_DARK)
d.rectangle([3, 3, 12, 7], fill=SCREEN_BG)
# glowing text lines on the screen
d.line([(4, 4), (9, 4)], fill=SCREEN_GLOW)
d.line([(4, 5), (7, 5)], fill=SCREEN_DIM)
d.line([(4, 6), (10, 6)], fill=SCREEN_DIM)
d.point((11, 4), fill=GOLD)          # little coin cursor
d.point((11, 6), fill=SCREEN_GLOW)
# keypad
d.rectangle([3, 10, 8, 13], fill=CASE_DARK)
for ky in (10, 12):
    for kx in (4, 6, 8):
        d.point((kx, ky), fill=KEY)
# card slot + LED
d.rectangle([10, 10, 13, 10], fill=SLOT)
d.point((13, 12), fill=SCREEN_GLOW)
d.point((11, 12), fill=RED_LED)
# cash dispenser
d.rectangle([2, 14, 13, 14], fill=GOLD)
d.rectangle([2, 15, 13, 15], fill=SLOT)
img.save(os.path.join(BLOCK, "atm_front.png"))

# ---------------------------------------------------------------- side
img, d = new()
bevel(d)
d.line([(4, 1), (4, 14)], fill=CASE_LIGHT)
d.line([(11, 1), (11, 14)], fill=CASE_DARK)
for vy in range(4, 12, 2):
    d.line([(6, vy), (9, vy)], fill=CASE_DARK)
d.point((13, 2), fill=CASE_HI)
img.save(os.path.join(BLOCK, "atm_side.png"))

# ---------------------------------------------------------------- back
img, d = new()
bevel(d)
d.rectangle([3, 3, 12, 12], fill=CASE_LIGHT)
d.rectangle([4, 4, 11, 11], fill=CASE_MID)
for bx, by in ((3, 3), (12, 3), (3, 12), (12, 12)):
    d.point((bx, by), fill=CASE_DARK)
d.line([(7, 13), (7, 15)], fill=SLOT)
d.line([(8, 13), (8, 15)], fill=SLOT)
img.save(os.path.join(BLOCK, "atm_back.png"))

# ---------------------------------------------------------------- top
img, d = new()
bevel(d)
d.rectangle([2, 2, 13, 13], fill=CASE_LIGHT)
d.rectangle([3, 3, 12, 12], fill=CASE_MID)
d.rectangle([5, 4, 10, 5], fill=GOLD)
d.point((12, 12), fill=SCREEN_GLOW)
img.save(os.path.join(BLOCK, "atm_top.png"))

# ---------------------------------------------------------------- bottom
img, d = new(CASE_DARK)
d.rectangle([1, 1, 14, 14], fill=CASE_MID)
for bx in (2, 13):
    for by in (2, 13):
        d.point((bx, by), fill=CASE_DARK)
img.save(os.path.join(BLOCK, "atm_bottom.png"))

# ---------------------------------------------------------------- ATM GUI panel
# Wallet palette so the ATM screen matches the wallet artwork.
P_DARK = (60, 29, 35, 255)
P_MID = (99, 58, 58, 255)
P_SHADE = (81, 44, 47, 255)
P_LIGHT = (131, 86, 86, 255)

PW, PH = 236, 150
panel = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
pd = ImageDraw.Draw(panel)
# outer frame
pd.rectangle([0, 0, PW - 1, PH - 1], fill=P_MID, outline=P_DARK)
pd.rectangle([1, 1, PW - 2, PH - 2], outline=P_LIGHT)
pd.rectangle([2, 2, PW - 3, PH - 3], outline=P_MID)
# title bar
pd.rectangle([4, 4, PW - 5, 17], fill=P_SHADE, outline=P_DARK)
# balance table area
pd.rectangle([4, 40, PW - 5, 114], fill=P_SHADE, outline=P_DARK)
# alternating row bands (rows start at y=54, pitch 20)
for i in range(3):
    y = 52 + i * 20
    if i % 2 == 0:
        pd.rectangle([6, y, PW - 7, y + 18], fill=(88, 50, 52, 255))
# separator above the hints
pd.line([(6, 120), (PW - 7, 120)], fill=P_DARK)
pd.line([(6, 121), (PW - 7, 121)], fill=P_LIGHT)
panel.save(os.path.join(GUI, "atm.png"))

print("ATM textures + GUI panel written")

# ---------------------------------------------------------------- copy wallet art + icons
SRC_RP = "/projects/sandbox/work/wallet_rp/assets"
for i in (1, 2, 3):
    Image.open(f"{SRC_RP}/along/textures/font/wallet_{i}.png").convert("RGBA").save(
        os.path.join(GUI, f"wallet_{i}.png"))
for name in ("balance", "documents"):
    Image.open(f"{SRC_RP}/minecraft/textures/custom/gui/icons/{name}.png").convert("RGBA").save(
        os.path.join(ICONS, f"{name}.png"))
print("wallet art + icons copied")

# ---------------------------------------------------------------- mod logo
gold = Image.open(os.path.join(RES, "textures/item/gold_coin.png")).convert("RGBA")
logo = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
logo.paste(gold.resize((116, 116), Image.LANCZOS), (6, 6))
logo.save("/projects/sandbox/hola/fantasticcoins-mod/src/main/resources/fantasticcoins_logo.png")
print("logo written")
