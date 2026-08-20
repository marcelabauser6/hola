#!/usr/bin/env python3
"""Static checks on the built FantasticCoins mod: JSON validity, lang coverage, asset references."""
import json
import os
import re
import sys
import zipfile

ROOT = "/projects/sandbox/hola/fantasticcoins-mod"
RES = os.path.join(ROOT, "src/main/resources")
SRC = os.path.join(ROOT, "src/main/java")
JAR = os.path.join(ROOT, "build/libs/FantasticCurrency-2.0.0-1.20.1.jar")

problems = []
notes = []

# ---------------------------------------------------------------- 1. JSON validity
json_files = []
for base, _, files in os.walk(RES):
    for name in files:
        if name.endswith(".json") or name.endswith(".mcmeta"):
            json_files.append(os.path.join(base, name))
for path in json_files:
    try:
        with open(path, encoding="utf-8") as fh:
            json.load(fh)
    except Exception as exc:
        problems.append(f"INVALID JSON {os.path.relpath(path, RES)}: {exc}")
notes.append(f"{len(json_files)} JSON/mcmeta files parsed")

# ---------------------------------------------------------------- 2. lang coverage
lang_path = os.path.join(RES, "assets/athens_coins/lang/en_us.json")
with open(lang_path, encoding="utf-8") as fh:
    en = json.load(fh)

# Proper Java string-literal matcher. A naive '"([^"\\]+)"' desynchronises on the first
# literal containing a backslash (a regex, for instance): the closing quote of that literal
# then pairs with the opening quote of the next one and every literal after it is missed.
JAVA_STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')

LANG_PREFIXES = ("gui.athens_coins.", "tooltip.athens_coins.", "message.athens_coins.",
                 "coin.athens_coins.", "item.athens_coins.", "block.athens_coins.",
                 "creative_tab.athens_coins")

used = set()
dynamic = set()
for base, _, files in os.walk(SRC):
    for name in files:
        if not name.endswith(".java"):
            continue
        text = open(os.path.join(base, name), encoding="utf-8").read()
        # Any string literal that looks like one of our lang keys, however it reaches
        # Component.translatable (direct call, ternary, variable, ...).
        for literal in JAVA_STRING.findall(text):
            if not literal.startswith(LANG_PREFIXES):
                continue
            if literal.endswith("."):
                dynamic.add(literal)      # built by concatenation
            else:
                used.add(literal)
for prefix in sorted(dynamic):
    notes.append(f"dynamic lang key prefix (checked per denomination): {prefix}*")

# registry-derived keys that Minecraft generates itself
auto = {"block.athens_coins.atm", "item.athens_coins.atm"}
for coin in ("bronze", "silver", "gold"):
    auto.add(f"item.athens_coins.{coin}_coin")
    auto.add(f"coin.athens_coins.{coin}")

missing = sorted(k for k in used if k not in en)
if missing:
    problems.append(f"lang keys used in code but missing from en_us.json: {missing}")

# every dynamic family must be present
for coin in ("bronze", "silver", "gold"):
    for key in (f"item.athens_coins.{coin}_coin", f"coin.athens_coins.{coin}"):
        if key not in en:
            problems.append(f"missing dynamic lang key {key}")

unused = sorted(k for k in en if k not in used and k not in auto)
if unused:
    notes.append(f"lang keys present but not referenced in code (fine if used by MC): {unused}")

# all locales share the same key set
for locale in ("es_es", "es_mx", "es_ar"):
    p = os.path.join(RES, f"assets/athens_coins/lang/{locale}.json")
    if not os.path.exists(p):
        problems.append(f"missing locale {locale}")
        continue
    with open(p, encoding="utf-8") as fh:
        other = json.load(fh)
    diff = set(en) ^ set(other)
    if diff:
        problems.append(f"{locale} key mismatch vs en_us: {sorted(diff)}")
notes.append(f"en_us.json has {len(en)} keys; {len(used)} distinct keys referenced from Java")

# ---------------------------------------------------------------- 3. asset references
def asset_path(ref, kind, ext):
    if ":" in ref:
        namespace, rest = ref.split(":", 1)
    else:
        namespace, rest = "minecraft", ref
    return namespace, os.path.join(RES, "assets", namespace, kind, rest + ext)

for base, _, files in os.walk(os.path.join(RES, "assets/athens_coins/models")):
    for name in files:
        path = os.path.join(base, name)
        model = json.load(open(path, encoding="utf-8"))
        rel = os.path.relpath(path, RES)
        for key, ref in (model.get("textures") or {}).items():
            if ref.startswith("#"):
                continue
            ns, tex = asset_path(ref, "textures", ".png")
            if ns == "athens_coins" and not os.path.exists(tex):
                problems.append(f"{rel}: texture '{ref}' not found ({tex})")
        parent = model.get("parent")
        if parent and parent.startswith("athens_coins:"):
            ns, pp = asset_path(parent, "models", ".json")
            if not os.path.exists(pp):
                problems.append(f"{rel}: parent model '{parent}' not found")

# blockstate models
bs = json.load(open(os.path.join(RES, "assets/athens_coins/blockstates/atm.json"), encoding="utf-8"))
for variant, cfg in bs["variants"].items():
    ns, mp = asset_path(cfg["model"], "models", ".json")
    if not os.path.exists(mp):
        problems.append(f"blockstates/atm.json[{variant}]: model {cfg['model']} not found")

# GUI textures referenced from Java
for base, _, files in os.walk(SRC):
    for name in files:
        if not name.endswith(".java"):
            continue
        text = open(os.path.join(base, name), encoding="utf-8").read()
        checked = 0
        for ref in re.findall(r'ResourceLocation\(\s*AthensCoinsMod\.MOD_ID\s*,\s*"([^"\n]+)"', text):
            if not ref.startswith("textures/"):
                continue          # e.g. the network channel id, not an asset
            if ref.endswith(".png"):
                candidates = [ref]
            else:
                # prefix built by concatenation, e.g. "textures/gui/wallet_" + theme + ".png"
                candidates = [f"{ref}{i}.png" for i in (1, 2, 3)]
            for candidate in candidates:
                p = os.path.join(RES, "assets/athens_coins", candidate)
                checked += 1
                if not os.path.exists(p):
                    problems.append(f"{name}: referenced texture missing: assets/athens_coins/{candidate}")
        if checked:
            notes.append(f"{name}: {checked} texture reference(s) resolved")

# ---------------------------------------------------------------- 3b. example config drift
# The shipped example config must list exactly the fields CurrencyConfig.Settings serialises,
# otherwise admins copy a file with keys the mod silently ignores.
settings_src = open(os.path.join(SRC, "com/athensmc/athenscoins/config/CurrencyConfig.java"),
                    encoding="utf-8").read()
body_start = settings_src.index("public static class Settings")
body = settings_src[body_start:]
java_fields = set()
for match in re.finditer(
        r'^\s*public\s+(?!static)(?:boolean|int|long|double|String)\s+(\w+)\s*=', body, re.M):
    java_fields.add(match.group(1))

example_path = os.path.join(ROOT, "fantasticcurrency.example.json")
if not os.path.exists(example_path):
    problems.append("fantasticcurrency.example.json is missing")
else:
    with open(example_path, encoding="utf-8") as fh:
        example = json.load(fh)
    only_java = sorted(java_fields - set(example))
    only_json = sorted(set(example) - java_fields)
    if only_java:
        problems.append(f"example config missing keys present in Settings: {only_java}")
    if only_json:
        problems.append(f"example config has keys Settings does not define: {only_json}")
    notes.append(f"example config matches all {len(java_fields)} configurable fields")

# ---------------------------------------------------------------- 4. jar contents
if not os.path.exists(JAR):
    problems.append(f"jar not built: {JAR}")
else:
    with zipfile.ZipFile(JAR) as zf:
        names = set(zf.namelist())
        required = [
            "META-INF/mods.toml",
            "pack.mcmeta",
            "fantasticcoins_logo.png",
            "assets/athens_coins/lang/en_us.json",
            "assets/athens_coins/lang/es_es.json",
            "assets/athens_coins/blockstates/atm.json",
            "assets/athens_coins/models/block/atm.json",
            "assets/athens_coins/models/item/atm.json",
            "assets/athens_coins/models/item/bronze_coin.json",
            "assets/athens_coins/textures/item/bronze_coin.png",
            "assets/athens_coins/textures/item/silver_coin.png",
            "assets/athens_coins/textures/item/gold_coin.png",
            "assets/athens_coins/textures/block/atm_front.png",
            "assets/athens_coins/textures/gui/atm.png",
            "assets/athens_coins/textures/gui/wallet_1.png",
            "assets/athens_coins/textures/gui/wallet_2.png",
            "assets/athens_coins/textures/gui/wallet_3.png",
            "assets/athens_coins/textures/gui/icons/balance.png",
            "assets/athens_coins/textures/gui/icons/documents.png",
            "data/athens_coins/loot_tables/blocks/atm.json",
            "data/athens_coins/recipes/atm.json",
            "data/minecraft/tags/blocks/mineable/pickaxe.json",
            "com/athensmc/athenscoins/AthensCoinsMod.class",
            "com/athensmc/athenscoins/command/FsCurrencyCommand.class",
            "com/athensmc/athenscoins/config/CurrencyConfig.class",
            "com/athensmc/athenscoins/config/DisplaySettings.class",
            "com/athensmc/athenscoins/transfer/TransferManager.class",
            "com/athensmc/athenscoins/transfer/PendingTransfer.class",
            "com/athensmc/athenscoins/wallet/Money.class",
            "com/athensmc/athenscoins/client/screen/WalletScreen.class",
            "com/athensmc/athenscoins/client/screen/AtmScreen.class",
            "com/athensmc/athenscoins/block/AtmBlock.class",
            "com/athensmc/athenscoins/wallet/WalletData.class",
            "com/athensmc/athenscoins/menu/AtmMenu.class",
            "com/athensmc/athenscoins/network/ModNetwork.class",
        ]
        for entry in required:
            if entry not in names:
                problems.append(f"jar missing entry: {entry}")
        # mods.toml must have been expanded (no leftover ${...})
        toml = zf.read("META-INF/mods.toml").decode("utf-8")
        if "${" in toml:
            problems.append("mods.toml still contains unexpanded ${...} placeholders")
        for field in ('modId="athens_coins"', 'logoFile="fantasticcoins_logo.png"',
                      'displayName="Fantastic Currency"'):
            if field not in toml:
                problems.append(f"mods.toml missing {field}")
        notes.append(f"jar contains {len(names)} entries")
        # reobfuscation sanity: SRG names must be present in compiled classes
        blob = zf.read("com/athensmc/athenscoins/block/AtmBlock.class")
        if b"m_" not in blob:
            problems.append("AtmBlock.class does not look reobfuscated to SRG names")

# ---------------------------------------------------------------- report
print("=" * 70)
for note in notes:
    print("note:", note)
print("=" * 70)
if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for p in problems:
        print("  -", p)
    sys.exit(1)
print("ALL CHECKS PASSED")
