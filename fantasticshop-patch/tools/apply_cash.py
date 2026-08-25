#!/usr/bin/env python3
"""
Adds Fantastic Cash as a fourth FShop currency.

Everything money-related in FShop already goes through CoinEconomy behind an int currency id, so
these edits are mostly about widening the "there are three currencies" assumptions to four and
teaching the GUIs to draw a cash cell that has no item icon.
"""
import pathlib
import sys

edits = 0


def patch(path, replacements, required=True):
    global edits
    file = pathlib.Path(path)
    text = file.read_text(encoding="utf-8")
    original = text
    for old, new in replacements:
        if old not in text:
            if required:
                print(f"  !! pattern not found in {path}:\n     {old[:110]}")
                sys.exit(1)
            continue
        text = text.replace(old, new, 1)
    if text != original:
        file.write_text(text, encoding="utf-8")
        edits += 1
        print(f"  patched {path}")


# ------------------------------------------------------------------ ShopOffer: allow id 3
patch("patchsrc/com/fshop/shop/ShopOffer.java", [
    ("this.coin = Math.max(0, Math.min(2, coin));",
     "this.coin = CoinEconomy.sanitize(coin);"),
    ("""    public void setCoin(int coin) {
        this.coin = Math.max(0, Math.min(2, coin));
    }""",
     """    public void setCoin(int coin) {
        this.coin = CoinEconomy.sanitize(coin);
    }"""),
    ("package com.fshop.shop;",
     "package com.fshop.shop;\n\nimport com.fshop.economy.CoinEconomy;"),
])

# ------------------------------------------------------------------ PlayerShop: 4 earnings slots
patch("patchsrc/com/fshop/shop/PlayerShop.java", [
    ("private final long[] pendingEarnings = new long[3];",
     "private final long[] pendingEarnings = new long[4];"),
    ("""    public long getPendingEarnings(int coin) {
        return this.pendingEarnings[Math.max(0, Math.min(2, coin))];
    }""",
     """    public long getPendingEarnings(int coin) {
        return this.pendingEarnings[Math.max(0, Math.min(3, coin))];
    }"""),
    ("return this.pendingEarnings[0] + this.pendingEarnings[1] + this.pendingEarnings[2];",
     "return this.pendingEarnings[0] + this.pendingEarnings[1] + this.pendingEarnings[2]\n"
     "                + this.pendingEarnings[3];"),
])

# clearEarnings, NBT and buffer all hardcode three slots
player_shop = pathlib.Path("patchsrc/com/fshop/shop/PlayerShop.java")
text = player_shop.read_text(encoding="utf-8")

text = text.replace("""        this.pendingEarnings[0] = 0L;
        this.pendingEarnings[1] = 0L;
        this.pendingEarnings[2] = 0L;""",
"""        this.pendingEarnings[0] = 0L;
        this.pendingEarnings[1] = 0L;
        this.pendingEarnings[2] = 0L;
        this.pendingEarnings[3] = 0L;""")

# The addEarnings index guard clamps to 2.
text = text.replace("int n = Math.max(0, Math.min(2, coin));",
                    "int n = Math.max(0, Math.min(3, coin));")

# NBT: keep writing the original three-long "earnings3" array so an older build still reads the
# coin earnings, and store cash separately under a new key.
text = text.replace('tag.m_128388_("earnings3", this.pendingEarnings);',
                    'tag.m_128388_("earnings3", new long[]{this.pendingEarnings[0], '
                    'this.pendingEarnings[1], this.pendingEarnings[2]});\n'
                    '        tag.m_128356_("earningsCash", this.pendingEarnings[3]);')
text = text.replace("""        long[] e = tag.m_128467_("earnings3");""",
                    """        shop.pendingEarnings[3] = tag.m_128454_("earningsCash");
        long[] e = tag.m_128467_("earnings3");""")

# Network buffer: append the cash slot after the existing three.
text = text.replace("""        buf.m_130103_(this.pendingEarnings[2]);""",
                    """        buf.m_130103_(this.pendingEarnings[2]);
        buf.m_130103_(this.pendingEarnings[3]);""")
text = text.replace("""        shop.pendingEarnings[2] = buf.m_130258_();""",
                    """        shop.pendingEarnings[2] = buf.m_130258_();
        shop.pendingEarnings[3] = buf.m_130258_();""")
player_shop.write_text(text, encoding="utf-8")
print("  patched patchsrc/com/fshop/shop/PlayerShop.java (earnings slots, nbt, buffer)")

# ------------------------------------------------------------------ ShopService: pay out cash too
patch("patchsrc/com/fshop/shop/ShopService.java", [
    ("for (int coin = 0; coin < 3; ++coin) {",
     "for (int coin = 0; coin < CoinEconomy.TYPES; ++coin) {"),
])

# ------------------------------------------------------------------ MainShopCreatorScreen
patch("patchsrc/com/fshop/client/screen/MainShopCreatorScreen.java", [
    ("o2.setCoin((o2.getCoin() + 1) % 3);",
     "o2.setCoin(CoinEconomy.sanitize((o2.getCoin() + 1) % CoinEconomy.TYPES));"),
    ("""        return switch (coin) {
            case 2 -> "Oro";
            case 1 -> "Plata";
            default -> "Bronce";
        };""",
     """        return switch (coin) {
            case 3 -> CoinEconomy.cashName();
            case 2 -> "Oro";
            case 1 -> "Plata";
            default -> "Bronce";
        };"""),
    ('"Moneda del precio: bronce (naranja), plata o oro. Clic para cambiar."',
     '"Moneda del precio: bronce, plata, oro o cash digital. Clic para cambiar."'),
])

# coinShort needs the cash case as well
creator = pathlib.Path("patchsrc/com/fshop/client/screen/MainShopCreatorScreen.java")
text = creator.read_text(encoding="utf-8")
start = text.index("private static String coinShort(int coin)")
end = text.index("}", text.index("};", start)) + 1
block = text[start:end]
if "case 3" not in block:
    text = text[:start] + block.replace("return switch (coin) {",
                                        "return switch (coin) {\n            case 3 -> "
                                        "CoinEconomy.cashSymbol();", 1) + text[end:]
    creator.write_text(text, encoding="utf-8")
    print("  patched coinShort() for cash")

print(f"\n{edits} files changed")
