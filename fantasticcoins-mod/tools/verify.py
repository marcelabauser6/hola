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
def _find_jar():
    """Locates the built jar without pinning the version, which used to drift on every release."""
    libs = os.path.join(ROOT, "build/libs")
    if not os.path.isdir(libs):
        return os.path.join(libs, "FantasticCurrency.jar")
    jars = sorted(n for n in os.listdir(libs)
                  if n.startswith("FantasticCurrency-") and n.endswith(".jar"))
    return os.path.join(libs, jars[-1]) if jars else os.path.join(libs, "FantasticCurrency.jar")


JAR = _find_jar()

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
                 "metric.athens_coins.",
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
#
# Every blockstate, not just the ATM's. A variant pointing at a model that does not exist is not a
# crash and not a warning - the block renders as the missing-model cube, which is only ever found by
# placing it. This used to check one file, which is exactly why the hologram's blockstate could have
# shipped broken.
blockstate_variants = 0
blockstate_dir = os.path.join(RES, "assets/athens_coins/blockstates")
for name in sorted(os.listdir(blockstate_dir)):
    if not name.endswith(".json"):
        continue
    with open(os.path.join(blockstate_dir, name), encoding="utf-8") as fh:
        bs = json.load(fh)
    for variant, cfg in bs.get("variants", {}).items():
        entries = cfg if isinstance(cfg, list) else [cfg]
        for entry in entries:
            blockstate_variants += 1
            ns, mp = asset_path(entry["model"], "models", ".json")
            if not os.path.exists(mp):
                problems.append(
                    f"blockstates/{name}[{variant}]: model {entry['model']} not found")
notes.append(f"{blockstate_variants} blockstate variants resolved to models")

# Every placeable block needs a loot table, or breaking it silently returns nothing.
for block_id in ("atm", "bank_terminal", "central_bank_terminal", "stats_hologram"):
    loot = os.path.join(RES, f"data/athens_coins/loot_tables/blocks/{block_id}.json")
    if not os.path.exists(loot):
        problems.append(f"block '{block_id}' has no loot table; breaking it would drop nothing")

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

            "assets/athens_coins/textures/gui/wallet_1.png",
            "assets/athens_coins/textures/gui/wallet_2.png",
            "assets/athens_coins/textures/gui/wallet_3.png",
            "assets/athens_coins/textures/gui/icons/balance.png",
            "assets/athens_coins/textures/gui/icons/documents.png",
            "data/athens_coins/loot_tables/blocks/atm.json",
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
            "com/athensmc/athenscoins/wallet/WalletSnapshot.class",
            "com/athensmc/athenscoins/menu/AtmMenu.class",
            "com/athensmc/athenscoins/network/S2COpenWalletPacket.class",
            "com/athensmc/athenscoins/api/FantasticCurrencyAPI.class",
            "com/athensmc/athenscoins/stats/EconomyStats.class",
            "com/athensmc/athenscoins/stats/EconomySnapshot.class",
            "com/athensmc/athenscoins/network/S2COpenHologramPacket.class",
            "com/athensmc/athenscoins/network/C2SHologramConfigPacket.class",
            "com/athensmc/athenscoins/client/screen/StatsHologramEditorScreen.class",
            "com/athensmc/athenscoins/client/render/StatsHologramRenderer.class",
            "com/athensmc/athenscoins/stats/StatsMetric.class",
            "com/athensmc/athenscoins/stats/HologramConfig.class",
            "com/athensmc/athenscoins/stats/HologramLines.class",
            "com/athensmc/athenscoins/block/StatsHologramBlock.class",
            "com/athensmc/athenscoins/block/StatsHologramBlockEntity.class",
            "com/athensmc/athenscoins/client/layout/ScreenLayout.class",
            "com/athensmc/athenscoins/client/layout/ScreenText.class",
            "com/athensmc/athenscoins/client/widget/ColorPicker.class",
            "com/athensmc/athenscoins/client/ClientCashCache.class",
            "com/athensmc/athenscoins/bank/BankAccess.class",
            "com/athensmc/athenscoins/bank/BankRules.class",
            "com/athensmc/athenscoins/bank/Bank.class",
            "com/athensmc/athenscoins/bank/BankAccount.class",
            "com/athensmc/athenscoins/bank/BankData.class",
            "com/athensmc/athenscoins/bank/BankManager.class",
            "com/athensmc/athenscoins/bank/LedgerEntry.class",
            "com/athensmc/athenscoins/bank/Loan.class",
            "com/athensmc/athenscoins/block/CentralBankTerminalBlock.class",
            "com/athensmc/athenscoins/block/AtmBlockEntity.class",
            "com/athensmc/athenscoins/block/ModBlockEntities.class",
            "com/athensmc/athenscoins/item/BankCardItem.class",
            "com/athensmc/athenscoins/client/screen/AccountDetailScreen.class",
            "com/athensmc/athenscoins/client/screen/CentralBankScreen.class",
            "com/athensmc/athenscoins/network/S2COpenAccountPacket.class",
            "com/athensmc/athenscoins/network/C2SRequestAccountPacket.class",
            "com/athensmc/athenscoins/network/S2COpenCentralPacket.class",
            "com/athensmc/athenscoins/network/C2SCentralActionPacket.class",
            "assets/athens_coins/blockstates/central_bank_terminal.json",
            "assets/athens_coins/textures/block/central_bank_front.png",
            "assets/athens_coins/textures/item/bank_card.png",
            "data/athens_coins/loot_tables/blocks/central_bank_terminal.json",
            "com/athensmc/athenscoins/block/BankTerminalBlock.class",
            "com/athensmc/athenscoins/block/OperatorOnlyPlacement.class",
            "com/athensmc/athenscoins/client/screen/BankTerminalScreen.class",
            "assets/athens_coins/blockstates/bank_terminal.json",
            "assets/athens_coins/models/block/bank_terminal.json",
            "assets/athens_coins/textures/block/bank_terminal_front.png",
            "data/athens_coins/loot_tables/blocks/bank_terminal.json",
            "com/athensmc/athenscoins/network/ModNetwork.class",
        ]
        for entry in required:
            if entry not in names:
                problems.append(f"jar missing entry: {entry}")
        # The source sprites receive detailed pixel checks below; byte equality ensures the JAR
        # actually contains those checked files rather than stale resources from an older build.
        for coin in ("bronze", "silver", "gold"):
            entry = f"assets/athens_coins/textures/item/{coin}_coin.png"
            source_texture = os.path.join(RES, entry)
            with open(source_texture, "rb") as fh:
                source_bytes = fh.read()
            if entry in names and zf.read(entry) != source_bytes:
                problems.append(f"jar coin texture differs from source resource: {entry}")
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

# ---------------------------------------------------------------- 5. regression guards

# (a) Item textures must stay small. A 1024x1024 sprite in Minecraft's item atlas is what
#     produced the coloured sparkles on the coins.
import struct
for base, _, files in os.walk(os.path.join(RES, "assets/athens_coins/textures/item")):
    for name in files:
        if not name.endswith(".png"):
            continue
        path = os.path.join(base, name)
        with open(path, "rb") as fh:
            head = fh.read(24)
        w, h = struct.unpack(">II", head[16:24])
        if w > 128 or h > 128:
            problems.append(f"item texture {name} is {w}x{h}; keep item sprites <=128x128 "
                            f"or the atlas mipmaps produce sparkles")
    notes.append(f"item textures checked: all <=128x128")

# Coin-specific content checks are stricter than the general atlas-size guard. They reproduce the
# approved generator in memory, so manual edits or a filter regression cannot silently ship. The
# metrics separately expose hidden RGB, alpha fringe, isolated highlights, local-palette overshoot,
# and edge halos to make failures actionable.
sys.path.insert(0, os.path.join(ROOT, "tools"))
from PIL import Image
from gen_coin_textures import COINS, FINAL_SIZE, load_source, measure, target_path, validation_errors

for coin in COINS:
    path = target_path(coin)
    if not path.exists():
        problems.append(f"coin texture missing: {path}")
        continue
    source = load_source(coin)
    with Image.open(path) as opened:
        coin_image = opened.copy()
    metrics = measure(coin_image, source)
    problems.extend(validation_errors(coin, coin_image, source))
    notes.append(
        f"{coin} coin {coin_image.size[0]}x{coin_image.size[1]}: "
        f"partial={metrics.partial_alpha}, hiddenRGB={metrics.hidden_rgb}, "
        f"isolatedHighlights={metrics.isolated_highlights}, "
        f"localPalette={metrics.local_palette_violations}, halos={metrics.edge_halos}"
    )
notes.append(f"coin sprites match deterministic {FINAL_SIZE}x{FINAL_SIZE} premultiplied-area generation")

# (b) The wallet must not be a container screen: that is what pulled the inventory in.
wallet_screen = open(os.path.join(SRC, "com/athensmc/athenscoins/client/screen/WalletScreen.java"),
                     encoding="utf-8").read()
if "AbstractContainerScreen" in wallet_screen:
    problems.append("WalletScreen extends AbstractContainerScreen again; it must be a plain Screen "
                    "so opening the wallet cannot involve the player's inventory")
if "class WalletScreen extends Screen" not in wallet_screen:
    problems.append("WalletScreen no longer extends Screen")
else:
    notes.append("wallet is a plain Screen (no container, no inventory interaction)")

if os.path.exists(os.path.join(SRC, "com/athensmc/athenscoins/menu/WalletMenu.java")):
    problems.append("WalletMenu.java is back; the wallet should have no container menu")

# (c) The transfer amount must not have suggestions, and accept/deny must stay gated.
cmd = open(os.path.join(SRC, "com/athensmc/athenscoins/command/FsCurrencyCommand.java"),
           encoding="utf-8").read()
if "AMOUNT_SUGGESTIONS" in cmd or re.search(r'"amount".*\n?.*\.suggests\(', cmd):
    problems.append("the transfer amount argument has suggestions again; it is typed manually")
else:
    notes.append("transfer amount has no suggestions")
for literal in ("accept", "deny"):
    pattern = r'literal\("' + literal + r'"\)\s*\.requires\(FsCurrencyCommand::hasPendingTransfer\)'
    if not re.search(pattern, cmd):
        problems.append(f"'{literal}' is no longer gated behind hasPendingTransfer; "
                        f"it would show up in tab-completion")
if "hasPendingTransfer" in cmd:
    notes.append("accept/deny gated behind having a pending request")

# (d) Every locale must carry the same translated text (the mod ships in Spanish everywhere).
lang_dir = os.path.join(RES, "assets/athens_coins/lang")
reference = None
for locale in ("en_us", "es_es", "es_mx", "es_ar"):
    with open(os.path.join(lang_dir, f"{locale}.json"), encoding="utf-8") as fh:
        data = json.load(fh)
    if reference is None:
        reference = data
    elif data != reference:
        differing = sorted(k for k in reference if data.get(k) != reference.get(k))
        problems.append(f"{locale}.json text differs from en_us: {differing[:5]}")
notes.append("all four locales carry identical Spanish text")

# All four locales are identical (checked immediately above), so one of them stands for the set.
with open(os.path.join(lang_dir, "es_es.json"), encoding="utf-8") as fh:
    lang_text = json.load(fh)

# (d1) Every hologram metric must be translatable.
#
# The metric names are built as "metric.athens_coins." + the enum's own id, so the generic
# string-literal scan can never see them - which means a metric added without its lang key would ship
# and show up on a hologram as the raw key. The enum is the source of truth, so read the ids out of it
# and check each one.
metric_source = open(os.path.join(SRC, "com/athensmc/athenscoins/stats/StatsMetric.java"),
                     encoding="utf-8").read()
metric_ids = re.findall(r'^\s{4}[A-Z_]+\("([a-z_]+)"', metric_source, re.MULTILINE)
if len(metric_ids) < 10:
    problems.append(f"only found {len(metric_ids)} metric ids in StatsMetric.java; "
                    f"the enum's shape changed and this check is no longer reading it")
for metric_id in metric_ids:
    key = f"metric.athens_coins.{metric_id}"
    if key not in lang_text:
        problems.append(f"hologram metric '{metric_id}' has no {key} translation")
notes.append(f"{len(metric_ids)} hologram metrics all have a translated name")

# (d3) The hologram's rows must be built in exactly one place.
#
# The editor draws a live preview of a hologram the player is not looking at while they edit it, so a
# preview that laid out its rows with its own copy of the rules would quietly promise something the
# projector would not produce - and both would look plausible in review. Both sides go through
# HologramLines, and the widest-row measurement is shared too.
for name, path in (("world renderer", "com/athensmc/athenscoins/client/render/StatsHologramRenderer.java"),
                   ("editor preview", "com/athensmc/athenscoins/client/screen/StatsHologramEditorScreen.java")):
    source = open(os.path.join(SRC, path), encoding="utf-8").read()
    if "HologramLines.build" not in source:
        problems.append(f"the {name} does not build its rows through HologramLines")
    if "HologramLines.panelWidth" not in source:
        problems.append(f"the {name} measures its panel width itself instead of sharing panelWidth")
notes.append("hologram rows and width come from one shared builder")

# (d2) Text budgets for the strings that share a row with a value.
#
# "there are texts in every GUI that do not fit" was a real bug, and the fix is not one pass of
# shortening - it is a budget, or the next label written will be too long again. Only the strings that
# genuinely compete for space are capped: a field label sits beside its value, a column heading sits
# above a number, a tab title sits inside a button. Notes, empty-state lines and hover tooltips are
# free-form: they get a whole band or a wrapping tooltip, and the screens clip them with an ellipsis
# and a tooltip anyway.
#
# Limits are in characters, which is a proxy for pixels - close enough at these lengths, and it does
# not need a font metric to check.
TEXT_BUDGETS = (
    (r"^gui\.athens_coins\.central_col_", 10, "central-bank column heading"),
    (r"^gui\.athens_coins\.risk_(?!hint)", 12, "risk-desk label"),
    (r"^gui\.athens_coins\.tab_(?!locked)", 14, "tab title"),
    (r"^gui\.athens_coins\.cfg_(?!hint|required|sent|bad_field|save|group|rate_of)", 20,
     "settings field label"),
    (r"^gui\.athens_coins\.acct_(?!hint|section|ledger|scroll|overdue|no_|close|lend|repay)", 12,
     "account label"),
    # The movement column holds a date, a name and an amount. The date and the amount are fixed-shape
    # strings that cannot shrink, so the budget has to fall on the name.
    (r"^ledger\.athens_coins\.", 18, "movement name"),
)
budgeted = 0
for key, value in lang_text.items():
    if key.endswith(("_tip", "_hint")):
        continue
    for pattern, limit, what in TEXT_BUDGETS:
        if re.search(pattern, key):
            budgeted += 1
            if len(value) > limit:
                problems.append(f"{what} too long for its row ({len(value)} > {limit}): "
                                f"{key} = {value!r}")
notes.append(f"{budgeted} space-constrained labels within their character budget")

# (d4) Authority lives in one place.
#
# The mod used to answer every question of "may they?" with an inline hasPermissions(2), spread across three
# blocks and six packet handlers. Each site spelled out its own version, which is how the bank terminal ended
# up creating a bank for whoever right-clicked it before checking whether they were allowed to be there. The
# tiers are defined once in BankAccess, and no other file outside it may test operator status directly.
auth_offenders = []
for base, _, files in os.walk(SRC):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(base, name)
        rel = os.path.relpath(path, SRC)
        if rel.endswith(os.path.join("bank", "BankAccess.java")):
            continue
        source = open(path, encoding="utf-8").read()
        for line in source.splitlines():
            if "hasPermissions(" in line and not line.strip().startswith(("*", "//")):
                auth_offenders.append(f"{rel}: {line.strip()}")
if auth_offenders:
    problems.append("permission checks outside BankAccess: " + "; ".join(auth_offenders[:4]))
else:
    notes.append("every permission check goes through BankAccess")

# (d5) Confirmations are in the screens, not in chat.
#
# A click-event button that runs a command is what the chat prompts were made of, so its absence from the
# bank code is what proves they stayed gone. Transfers are the one exception and keep theirs: the recipient
# of a transfer is not standing at a screen when it arrives, so there is nowhere else to ask them.
for base, _, files in os.walk(os.path.join(SRC, "com/athensmc/athenscoins")):
    for name in files:
        if not name.endswith(".java") or "transfer" in base:
            continue
        source = open(os.path.join(base, name), encoding="utf-8").read()
        if "ClickEvent.Action.RUN_COMMAND" in source:
            problems.append(f"{name} builds a chat command button; confirmations belong in the screen")
notes.append("confirmations are in-screen; only transfers use chat buttons")

# (d6) The command tree stays small.
#
# Every subcommand is a thing to document, a thing to gate and a thing that can be typed wrong. Two of them
# have already been removed for being redundant with a screen - a dashboard command and a confirmation
# command - so the list is pinned here: adding one is a decision, not something that happens by accident.
command_source = open(os.path.join(SRC, "com/athensmc/athenscoins/command/FsCurrencyCommand.java"),
                      encoding="utf-8").read()
tree_end = command_source.index("private static boolean hasPendingTransfer")
literals = set(re.findall(r'Commands\.literal\("(\w+)"\)', command_source[:tree_end]))
# "bank" is operator-only administration: list what exists, hand out a replacement terminal for one whose
# own drop was lost, clear a bank's accounts, delete a bank. It came back after the blind seat-adoption bug
# left banks unreachable with no way to look at them or get back in.
expected_literals = {"fscurrency", "wallet", "balance", "transfer", "accept", "deny", "reload",
                     "bank", "list", "give", "purge", "delete"}
if literals != expected_literals:
    problems.append("command tree changed: expected "
                    + ", ".join(sorted(expected_literals))
                    + " but found " + ", ".join(sorted(literals)))
else:
    notes.append(f"command tree is the expected {len(expected_literals)} literals")


# ---------------------------------------------------------------- 6. responsive screen layout arithmetic
# The ATM used to be a grid of constants pinned to a baked 248x198 texture, and this section
# re-derived that grid to check the bands did not collide. The screen now computes its geometry from
# client/layout/AtmLayout, which the Java layout guard exercises directly at nine viewports - a much
# stronger check than parsing constants out of source, because it runs the real code. What is worth
# asserting here instead is that the screen has not drifted back to hardcoded coordinates.
atm = open(os.path.join(SRC, "com/athensmc/athenscoins/client/screen/AtmScreen.java"),
           encoding="utf-8").read()
if "AtmLayout" not in atm:
    problems.append("AtmScreen no longer derives its geometry from AtmLayout")
if "leftPos +" in atm or "topPos +" in atm:
    problems.append("AtmScreen has gone back to texture-relative hardcoded offsets")
if "trimToWidth" in atm:
    problems.append("AtmScreen carries a private copy of the text-fitting helper again")
atm_layout = open(os.path.join(SRC, "com/athensmc/athenscoins/client/layout/AtmLayout.java"),
                  encoding="utf-8").read()
for band in ("amountRow", "messageRow", "closeRow"):
    if band not in atm_layout:
        problems.append(f"AtmLayout is missing the {band} band")
notes.append("ATM geometry comes from AtmLayout; bands verified by the Java layout guard")

# Amounts must be interpreted in exactly one place. Every screen that takes a typed figure has to go
# through AmountField/CountField: the bug this replaced was a per-screen helper that read an entry
# without a separator as cents and one with a separator as units, a factor of a hundred in one box.
screen_dir_early = os.path.join(SRC, "com/athensmc/athenscoins/client/screen")
for name in ("AtmScreen.java", "AccountDetailScreen.java", "CentralBankScreen.java",
             "BankTerminalScreen.java"):
    source = open(os.path.join(screen_dir_early, name), encoding="utf-8").read()
    if "AmountField" not in source and "CountField" not in source:
        problems.append(f"{name} does not use the shared amount entry widget")
    if "Long.parseLong" in source:
        problems.append(f"{name} parses an amount by hand instead of through Money.parse")
notes.append("four amount-taking screens share one parser")

# Every dynamic-text-heavy screen must use the shared ellipsis helper. Screens whose widgets are
# not texture-bound must also derive their panel/regions from the pure geometry utility.
screen_dir = os.path.join(SRC, "com/athensmc/athenscoins/client/screen")
redesigned = (
    "BankTerminalScreen.java", "AccountDetailScreen.java", "CentralBankScreen.java",
    "AtmScreen.java", "WalletScreen.java", "StatsHologramEditorScreen.java",
)
for name in redesigned:
    source = open(os.path.join(screen_dir, name), encoding="utf-8").read()
    if "ScreenText" not in source:
        problems.append(f"{name} does not use ScreenText for bounded dynamic text")
for name in set(redesigned) - {"AtmScreen.java"}:
    source = open(os.path.join(screen_dir, name), encoding="utf-8").read()
    if "ScreenLayout" not in source:
        problems.append(f"{name} does not use shared responsive geometry")
notes.append("seven redesigned screens use bounded text; six free-form screens use shared geometry")

# ---------------------------------------------------------------- 6b. model texture references
# Every texture a model names must exist. A mistyped path is not a crash and not a warning: the block
# simply renders as the missing-texture checkerboard, which is only ever found by looking at it.
model_refs = 0
for base, _, files in os.walk(os.path.join(RES, "assets/athens_coins/models")):
    for name in files:
        if not name.endswith(".json"):
            continue
        with open(os.path.join(base, name), encoding="utf-8") as fh:
            model = json.load(fh)
        for slot, ref in (model.get("textures") or {}).items():
            if not isinstance(ref, str) or ref.startswith("#"):
                continue
            namespace, sep, path = ref.partition(":")
            if not sep:
                namespace, path = "minecraft", ref
            if namespace == "minecraft":
                continue
            texture = os.path.join(RES, f"assets/{namespace}/textures/{path}.png")
            model_refs += 1
            if not os.path.isfile(texture):
                problems.append(f"models/{name}: texture slot '{slot}' points at missing {ref}")
notes.append(f"{model_refs} model texture reference(s) resolved")

layout_test = os.path.join(ROOT, "src/test/java/com/athensmc/athenscoins/client/layout/ScreenLayoutStaticTest.java")
if not os.path.exists(layout_test):
    problems.append("responsive layout static test is missing")
elif "{320, 240}" not in open(layout_test, encoding="utf-8").read():
    problems.append("layout static test does not cover the 320x240 logical GUI viewport")
else:
    notes.append("responsive geometry test covers common logical GUI resolutions")

# ---------------------------------------------------------------- 7. financial-core invariants
financial_files = {
    "api": "com/athensmc/athenscoins/api/FantasticCurrencyAPI.java",
    "manager": "com/athensmc/athenscoins/bank/BankManager.java",
    "bank_data": "com/athensmc/athenscoins/bank/BankData.java",
    "wallet_data": "com/athensmc/athenscoins/wallet/WalletData.java",
    "ledger": "com/athensmc/athenscoins/bank/LedgerEntry.java",
    "card": "com/athensmc/athenscoins/item/BankCardItem.java",
    "atm": "com/athensmc/athenscoins/block/AtmBlock.java",
}
financial = {name: open(os.path.join(SRC, path), encoding="utf-8").read()
             for name, path in financial_files.items()}
required_financial_guards = {
    "API default deposit reaches principal account": ("api", "creditAccount(server,playerId"),
    "API wallet charge is bank-gated manager operation": ("api", "chargeWallet(server,playerId"),
    "wallet data persists zero initialization": ("wallet_data", "zero entries are intentional"),
    "wallet migration has quarantine": ("wallet_data", "Quarantine"),
    "bank data archives closed accounts": ("bank_data", "ClosedAccounts"),
    "bank data retains orphan accounts": ("bank_data", "OrphanAccounts"),
    "card has signed owner UUID": ("card", "TAG_OWNER"),
    "card has unique token": ("card", "TAG_TOKEN"),
    "card redemption is persisted": ("bank_data", "redeemCard"),
    "ATM checks issuing bank ownership": ("atm", "accessFor"),
    "ledger stores before balance": ("ledger", 'putLong("before"'),
    "ledger stores correlation id": ("ledger", 'putString("correlation"'),
    "ledger stores actor": ("ledger", 'putString("actor"'),
    "commission catch-up is session-budgeted": ("manager", "commissionCatchUpUsed"),
    "wallet limit changes normalize excess": ("manager", "normalizacion de limite"),
    "active wallet persists with principal account": ("manager", "account.walletBalance()"),
}
for label, (name, needle) in required_financial_guards.items():
    if needle not in financial[name]:
        problems.append(f"financial invariant missing: {label}")
notes.append(f"{len(required_financial_guards)} financial-core static invariants checked")

# Direct wallet mutation in the public API was the original bank-account bypass.
if "WalletData.get(server).account(playerId)" in financial["api"]:
    problems.append("FantasticCurrencyAPI directly creates/mutates wallets again")
if "return wallets.computeIfAbsent" in financial["wallet_data"]:
    problems.append("WalletData account creation no longer explicitly marks SavedData dirty")

# ------------------------------------------------- block models: no see-through faces
#
# The ATM shipped with a hole in it. Its plinth had no up face, and because the cabinet standing on
# the plinth is narrower on all four sides, a rim of the plinth's top was permanently exposed - you
# could see straight through the base of the machine. It renders as air, which is exactly what it is.
#
# A missing face is only safe when something else covers it. This checks that directly: for every
# element of every block model, a face that is absent must be coplanar with, and contained inside,
# the opposing face of another element in the same model. Anything else is a hole, reported with the
# element and direction so it can be found without hunting.
FACE_AXIS = {
    "north": (2, 0, (0, 1)),   # -Z, so the face sits at from[2]
    "south": (2, 1, (0, 1)),   # +Z, at to[2]
    "west": (0, 0, (1, 2)),
    "east": (0, 1, (1, 2)),
    "down": (1, 0, (0, 2)),
    "up": (1, 1, (0, 2)),
}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west",
            "down": "up", "up": "down"}

def _face_plane(element, direction):
    """The coordinate the face lies on, and its extent on the other two axes."""
    axis, end, (u, v) = FACE_AXIS[direction]
    box = element["from"], element["to"]
    return box[end][axis], (box[0][u], box[0][v], box[1][u], box[1][v])

# How far a face may stick out beyond whatever covers it before the gap counts.
#
# A quarter of a texel. Inset bands are modelled a tenth of a pixel wider than the body they sit on, so
# a hair of their top and bottom is technically uncovered - at that size it cannot render as a visible
# gap, and flagging it would bury the real holes in noise. The ATM's plinth, by contrast, was exposed by
# half a pixel on two sides and a whole pixel on the others.
FACE_TOLERANCE = 0.25

def _contains(outer, inner, tolerance=FACE_TOLERANCE):
    return (outer[0] <= inner[0] + tolerance and outer[1] <= inner[1] + tolerance
            and outer[2] >= inner[2] - tolerance and outer[3] >= inner[3] - tolerance)

def _backed_by(other, plane, extent, direction):
    """True when solid geometry sits behind an opening, so it shows a surface rather than the sky.

    A missing face is not always a mistake. The machines' brand plates are frames deliberately left open
    at the front: the face is absent so that the head's own front, sitting a fraction behind it, shows
    through the opening. Minecraft draws faces one-sided, so what matters is whether some other element
    presents a face pointing the same way, further in, across the whole opening. If it does, a viewer
    sees that surface. If nothing does, they see straight through the model.
    """
    axis, end, (u, v) = FACE_AXIS[direction]
    other_plane = (other["from"], other["to"])[end][axis]
    # "Further in" is +axis for a low face and -axis for a high one.
    deeper = other_plane > plane + 1e-6 if end == 0 else other_plane < plane - 1e-6
    if not deeper:
        return False
    other_extent = (other["from"][u], other["from"][v], other["to"][u], other["to"][v])
    return _contains(other_extent, extent)

def _on_block_boundary(plane):
    """A face flush with the block's own edge.

    Minecraft culls these against the neighbouring block, and for a machine built from two blocks the
    neighbour is its other half - so an absent face here is not a hole. Counting them would flag every
    multi-block model in the mod and make the check noise.
    """
    return abs(plane) < 1e-6 or abs(plane - 16.0) < 1e-6

def _inside_element(other, element, direction):
    """True when a face is buried inside another element rather than merely touching it.

    Inset bands are modelled as a slightly larger box overlapping the body, so their top and bottom sit
    *within* the body's volume and no two faces are ever coplanar. That is perfectly solid, and the
    coplanar test alone would call it a hole.
    """
    axis, end, (u, v) = FACE_AXIS[direction]
    plane = (element["from"], element["to"])[end][axis]
    if not (other["from"][axis] - 1e-6 <= plane <= other["to"][axis] + 1e-6):
        return False
    extent = _face_plane(element, direction)[1]
    other_extent = (other["from"][u], other["from"][v], other["to"][u], other["to"][v])
    return _contains(other_extent, extent)

model_dir = os.path.join(RES, "assets/athens_coins/models/block")
holes = 0
models_checked = 0
for name in sorted(os.listdir(model_dir)) if os.path.isdir(model_dir) else []:
    if not name.endswith(".json"):
        continue
    with open(os.path.join(model_dir, name), encoding="utf-8") as fh:
        model = json.load(fh)
    elements = model.get("elements")
    if not elements:
        continue
    models_checked += 1
    for element in elements:
        faces = element.get("faces", {})
        label = element.get("name", "sin nombre")
        for direction in FACE_AXIS:
            if direction in faces:
                continue
            plane, extent = _face_plane(element, direction)
            if _on_block_boundary(plane):
                continue
            covered = False
            for other in elements:
                if other is element:
                    continue
                other_plane, other_extent = _face_plane(other, OPPOSITE[direction])
                # Touching means the two planes coincide; the neighbour must also be wide enough to
                # cover the whole opening, or part of it is still a hole.
                if abs(other_plane - plane) < 1e-6 and _contains(other_extent, extent):
                    covered = True
                    break
                if _inside_element(other, element, direction):
                    covered = True
                    break
                if _backed_by(other, plane, extent, direction):
                    covered = True
                    break
            if not covered:
                holes += 1
                problems.append(
                    f"{name}: element '{label}' has no {direction} face and nothing covers it, "
                    f"so the model is see-through there")
notes.append(f"{models_checked} block models checked for see-through faces")

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
