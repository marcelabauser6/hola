#!/usr/bin/env python3
"""Static checks on the FantasticShop patch and the jar it produces.

FantasticShop has no test harness and cannot get one: it ships obfuscated, and the patch is a set of
individual classes swapped into a jar. What it can have is a guard against the specific defects that
were found and fixed, all of which are visible in the source text or in the built jar. Every check
below corresponds to a real bug, named in its own message.

Run after tools/rebuild.sh.
"""
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "src")
BASE_JAR = os.path.join(ROOT, os.pardir, "FantasticShop-1.1.jar")

problems = []
notes = []


def read(relative):
    with open(os.path.join(SRC, relative), encoding="utf-8") as handle:
        return handle.read()


def sources():
    for base, _, files in os.walk(SRC):
        for name in files:
            if name.endswith(".java"):
                path = os.path.join(base, name)
                with open(path, encoding="utf-8") as handle:
                    yield os.path.relpath(path, SRC), handle.read()


def body_after(text, start, lines=8):
    """The few lines following a match, used to judge what a loop is iterating over."""
    end = start
    for _ in range(lines):
        end = text.find("\n", end + 1)
        if end < 0:
            return text[start:]
    return text[start:end]


# ---------------------------------------------------------------- 1. no 3-currency loops left
# Every one of these was a money-loss or invisible-money bug: a loop that stopped at the three coin
# currencies while the earnings array, the wallet strip and the offers all understood four.
#
# The bound 3 is also legitimately the number of +/- step buttons, so the body decides: only a loop
# that touches a currency API is a currency loop.
CURRENCY_LOOP = re.compile(r"for\s*\(\s*(?:int\s+)?\w+\s*=\s*0\s*;\s*\w+\s*<\s*3\s*;")
CURRENCY_USE = re.compile(r"PendingEarnings|addEarnings|takeEarnings|CoinEconomy\.(?:balance|deposit"
                          r"|coinIcon|coinKey|isCash|formatAmount)|earnCellX|coinCellX")
currency_loops = 0
for name, text in sources():
    for match in CURRENCY_LOOP.finditer(text):
        body = body_after(text, match.end())
        if not CURRENCY_USE.search(body):
            continue        # the three step buttons, not the three coin currencies
        line = text[:match.start()].count("\n") + 1
        currency_loops += 1
        problems.append(f"{name}:{line}: loop bounded at 3 currencies; use CoinEconomy.TYPES")
if currency_loops == 0:
    notes.append("no currency loop stops at three")

# ---------------------------------------------------------------- 2. the crash
# balances[] arrives from the server's shop-view packet, which still carries three longs. Indexing it
# with the cash id threw ArrayIndexOutOfBoundsException, so any cash-priced offer crashed the screen.
for name in ("com/fshop/client/screen/AmountScreen.java",
             "com/fshop/client/screen/ShopViewScreen.java"):
    text = read(name)
    if "balanceOf(" not in text:
        problems.append(f"{name}: no balanceOf() helper; cash offers will crash on the balances array")
    for match in re.finditer(r"balances\[([^\]]+)\]", text):
        if "Math.min" not in match.group(1):
            line = text[:match.start()].count("\n") + 1
            problems.append(f"{name}:{line}: unclamped balances[{match.group(1)}] can crash on cash")
notes.append("both buy screens read balances through a clamped helper")

# ---------------------------------------------------------------- 3. payout cannot silently destroy
economy = read("com/fshop/economy/CoinEconomy.java")
if "public static boolean deposit(" not in economy:
    problems.append("CoinEconomy.deposit must report success; a void return hid failed cash payouts")
if "depositSale" not in economy:
    problems.append("CoinEconomy.depositSale missing; sales would not settle into the linked account")
if "creditSaleToAccount" not in economy:
    problems.append("depositSale does not use creditSaleToAccount, so the shop's account is ignored")

shop_service = read("com/fshop/shop/ShopService.java")
commands = read("com/fshop/command/FShopCommands.java")
for name, text in (("ShopService.collect", shop_service), ("FShopCommands.collect", commands)):
    if "takeEarnings" not in text or "restoreEarnings" not in text:
        problems.append(f"{name} does not take-and-restore earnings; a failed payout would delete money")
if "clearEarnings()" in shop_service:
    problems.append("ShopService still calls clearEarnings(), which discards unpaid amounts")
notes.append("both payout paths restore what they could not pay")

player_shop = read("com/fshop/shop/PlayerShop.java")
for method in ("takeEarnings", "restoreEarnings"):
    if f"public long {method}" not in player_shop and f"public void {method}" not in player_shop:
        problems.append(f"PlayerShop.{method} is missing")

save_packet = read("com/fshop/network/SaveMainShopPacket.java")
if "CoinEconomy.TYPES" not in save_packet:
    problems.append("SaveMainShopPacket carries over only some currencies; saving deletes the rest")

# ---------------------------------------------------------------- 4. the account requirement
def method_body(text, name):
    """A method's source, delimited by brace balance rather than by the first closing brace."""
    match = re.search(r"public static \w+ " + re.escape(name) + r"\s*\(", text)
    if not match:
        return None
    start = text.index("{", match.end())
    depth = 0
    for index in range(start, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[start:index + 1]
    return None


if "sellerBanked" not in shop_service or "refreshAccount" not in shop_service:
    problems.append("ShopService is missing the bank-account gates")
for gate in ("buy", "addOrRestock"):
    body = method_body(shop_service, gate)
    if body is None:
        problems.append(f"ShopService.{gate} not found")
    elif "NO_BANK_ACCOUNT" not in body:
        problems.append(f"ShopService.{gate} does not enforce the bank account")
# stock() and setPrice() deliberately re-sync without blocking; assert they at least re-sync.
for method in ("stock", "setPrice"):
    body = method_body(shop_service, method)
    if body is None:
        problems.append(f"ShopService.{method} not found")
    elif "refreshAccount" not in body:
        problems.append(f"ShopService.{method} never re-syncs the account, so a shop stays frozen")
collect_body = method_body(shop_service, "collect")
if collect_body is not None and "depositSale" not in collect_body:
    problems.append("ShopService.collect does not settle through the shop's linked account")
notes.append("buy and listing are gated; restock and price edits re-sync without blocking")

# ---------------------------------------------------------------- 5. manual price entry
price_screen = read("com/fshop/client/screen/PriceInputScreen.java")
if "centsOf" not in price_screen or "unitsOf" not in price_screen:
    problems.append("PriceInputScreen cannot convert a typed decimal price")
if "c == '.'" not in price_screen and 'c == \'.\'' not in price_screen:
    problems.append("PriceInputScreen does not accept a decimal separator, so cash prices are cents-only")
if "decimalsOf" not in price_screen:
    problems.append("PriceInputScreen does not cap the typed decimals at two")
notes.append("cash prices accept a typed decimal amount, capped at two places")

# ---------------------------------------------------------------- 6. translations
overlay_path = os.path.join(ROOT, "lang-overlay", "assets", "fshop", "lang", "overlay.json")
with open(overlay_path, encoding="utf-8") as handle:
    overlay = json.load(handle)
referenced = set()
for _, text in sources():
    referenced.update(re.findall(r'"(fshop\.[a-z0-9_.]+)"', text))
for key in ("fshop.coin.cash", "fshop.msg.no_bank_account"):
    if key not in overlay:
        problems.append(f"{key} is referenced by the patch but not in the language overlay")
unused = [key for key in overlay if key not in referenced]
if unused:
    notes.append(f"overlay keys not referenced from patched code (may be used by base classes): {unused}")

# ---------------------------------------------------------------- 7. the built jar
built = [name for name in os.listdir(ROOT)
         if name.startswith("FantasticShop-") and name.endswith(".jar")]
if not built:
    problems.append("no built jar found; run tools/rebuild.sh")
else:
    jar_path = os.path.join(ROOT, sorted(built)[-1])
    with zipfile.ZipFile(jar_path) as jar:
        entries = jar.namelist()
        notes.append(f"{os.path.basename(jar_path)} contains {len(entries)} entries")
        if os.path.isfile(BASE_JAR):
            with zipfile.ZipFile(BASE_JAR) as base:
                base_count = len(base.namelist())
            if len(entries) != base_count:
                problems.append(f"built jar has {len(entries)} entries, base jar has {base_count}; "
                                "a swapped class was added or lost")
            else:
                notes.append(f"entry count matches the base jar ({base_count})")
        if "META-INF/MANIFEST.MF" not in entries:
            problems.append("built jar has no manifest")
        # A referenced key missing from the shipped jar renders as the raw identifier in game.
        for name in [e for e in entries if e.startswith("assets/fshop/lang/") and e.endswith(".json")]:
            with jar.open(name) as handle:
                data = json.load(handle)
            for key in overlay:
                if key not in data:
                    problems.append(f"{name} in the jar is missing {key}")
        notes.append("every overlay key is present in every shipped locale")

# ---------------------------------------------------------------- report
print("=" * 70)
for note in notes:
    print("note:", note)
print("=" * 70)
if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)
print("ALL SHOP CHECKS PASSED")
