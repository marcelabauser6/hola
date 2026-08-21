#!/usr/bin/env python3
"""
Audits the two shipped jars against the full feature list.

Reads the jars themselves rather than the sources, so it reports what actually ships. Anything a
player could hit - a block with no model, an item with no name, a registered thing with no loot
table - counts as a failure.
"""
import json
import re
import subprocess
import sys
import zipfile
import os

REPO = "/projects/sandbox/hola"
CURRENCY = os.path.join(REPO, "FantasticCurrency-5.0.1.jar")
SHOP = os.path.join(REPO, "FantasticShop-1.3.0-bank.jar")
JAVAP = "/root/.local/share/mise/installs/java/17/bin/javap"

problems = []
notes = []


def fail(message):
    problems.append(message)


def bytecode(jar_dir, entry):
    """javap output for a class already extracted to disk."""
    path = os.path.join(jar_dir, entry)
    if not os.path.exists(path):
        return ""
    return subprocess.run([JAVAP, "-p", "-c", path], capture_output=True, text=True).stdout


# ==================================================================== currency mod
print("=" * 72)
print("FantasticCurrency-5.0.1.jar")
print("=" * 72)

if not os.path.exists(CURRENCY):
    fail("currency jar missing")
else:
    zf = zipfile.ZipFile(CURRENCY)
    names = set(zf.namelist())
    notes.append(f"currency jar: {len(names)} entries")

    # ---- registered content must be complete: model, blockstate, loot, tag, name
    blocks = ["atm", "bank_terminal", "central_bank_terminal"]
    for block in blocks:
        for entry, label in [
            (f"assets/athens_coins/blockstates/{block}.json", "blockstate"),
            (f"assets/athens_coins/models/block/{block}.json", "block model"),
            (f"assets/athens_coins/models/item/{block}.json", "item model"),
            (f"data/athens_coins/loot_tables/blocks/{block}.json", "loot table"),
        ]:
            if entry not in names:
                fail(f"block {block}: missing {label} ({entry})")

    items = ["bronze_coin", "silver_coin", "gold_coin", "bank_card"]
    for item in items:
        entry = f"assets/athens_coins/models/item/{item}.json"
        if entry not in names:
            fail(f"item {item}: missing model")

    # ---- every model's textures must exist
    for entry in [n for n in names if n.startswith("assets/athens_coins/models/")
                  and n.endswith(".json")]:
        model = json.loads(zf.read(entry))
        for ref in (model.get("textures") or {}).values():
            if not isinstance(ref, str) or ref.startswith("#"):
                continue
            namespace, _, path = ref.partition(":")
            if not path:
                namespace, path = "minecraft", namespace
            if namespace != "athens_coins":
                continue
            texture = f"assets/athens_coins/textures/{path}.png"
            if texture not in names:
                fail(f"{entry}: texture {ref} not in jar")

    # ---- mineable / tier tags
    for tag in ["mineable/pickaxe", "needs_stone_tool"]:
        entry = f"data/minecraft/tags/blocks/{tag}.json"
        if entry not in names:
            fail(f"missing tag {tag}")
            continue
        values = json.loads(zf.read(entry))["values"]
        for block in blocks:
            if f"athens_coins:{block}" not in values:
                fail(f"{block} not in tag {tag} (would never drop / mine instantly)")

    # ---- lang: a name for everything registered, and all locales in step
    lang = json.loads(zf.read("assets/athens_coins/lang/en_us.json"))
    required_names = {
        "block.athens_coins.atm",
        "block.athens_coins.bank_terminal",
        "block.athens_coins.central_bank_terminal",
        "item.athens_coins.bronze_coin",
        "item.athens_coins.silver_coin",
        "item.athens_coins.gold_coin",
        "item.athens_coins.bank_card",
        "item.athens_coins.account_tag",
        "item.athens_coins.atm_of",
        "creative_tab.athens_coins_tab",
    }
    for key in sorted(required_names - set(lang)):
        fail(f"lang: no name for {key}")
    for locale in ["es_es", "es_mx", "es_ar"]:
        other = json.loads(zf.read(f"assets/athens_coins/lang/{locale}.json"))
        if other != lang:
            fail(f"lang: {locale} differs from en_us")
    notes.append(f"lang: {len(lang)} keys, 4 locales identical")

    # ---- ledger kinds all have a label, or the register shows raw enum names
    kinds = ["opened", "coin_deposit", "coin_withdraw", "wallet_out", "wallet_in", "shop_sale",
             "shop_purchase", "transfer_sent", "transfer_received", "commission", "loan_granted",
             "loan_repaid", "loan_interest", "central_injection", "closed"]
    for kind in kinds:
        if f"ledger.athens_coins.{kind}" not in lang:
            fail(f"lang: ledger kind {kind} has no label")

    # ---- classes that must be there
    required_classes = [
        "bank/BankRules", "bank/Bank", "bank/BankAccount", "bank/BankData", "bank/BankManager",
        "bank/LedgerEntry", "bank/Loan",
        "block/AtmBlock", "block/AtmBlockEntity", "block/ModBlockEntities",
        "block/BankTerminalBlock", "block/CentralBankTerminalBlock", "block/OperatorOnlyPlacement",
        "item/BankCardItem",
        "client/screen/WalletScreen", "client/screen/AtmScreen", "client/screen/BankTerminalScreen",
        "client/screen/AccountDetailScreen", "client/screen/CentralBankScreen",
        "client/screen/StatsScreen", "client/screen/StatsThemeEditorScreen",
        "client/theme/StatsTheme", "client/widget/ColorPicker", "client/ClientCashCache",
        "stats/EconomyStats", "stats/EconomySnapshot",
        "network/ModNetwork", "network/S2COpenWalletPacket", "network/S2COpenStatsPacket",
        "network/S2COpenTerminalPacket", "network/C2STerminalActionPacket",
        "network/S2COpenAccountPacket", "network/C2SRequestAccountPacket",
        "network/S2COpenCentralPacket", "network/C2SCentralActionPacket",
        "network/S2CWalletSyncPacket",
        "api/FantasticCurrencyAPI", "command/FsCurrencyCommand", "config/CurrencyConfig",
        "wallet/Money", "wallet/Wallet", "wallet/WalletData", "wallet/WalletManager",
        "wallet/WalletSnapshot", "wallet/CoinType",
        "transfer/TransferManager", "transfer/PendingTransfer",
    ]
    for cls in required_classes:
        entry = f"com/athensmc/athenscoins/{cls}.class"
        if entry not in names:
            fail(f"missing class {cls}")

    # ---- WalletMenu must stay gone (the inventory clash)
    if "com/athensmc/athenscoins/menu/WalletMenu.class" in names:
        fail("WalletMenu is back; the wallet must not be a container")

    # ---- mods.toml
    toml = zf.read("META-INF/mods.toml").decode("utf-8")
    if "${" in toml:
        fail("mods.toml has unexpanded placeholders")
    for needle in ['modId="athens_coins"', 'displayName="Fantastic Currency"', 'version="5.0.1']:
        if needle not in toml:
            fail(f"mods.toml missing {needle}")

    # ---- reobfuscation: SRG names must be present
    tmp = "/tmp/audit_currency"
    subprocess.run(["rm", "-rf", tmp])
    zf.extractall(tmp)
    sample = bytecode(tmp, "com/athensmc/athenscoins/block/BankTerminalBlock.class")
    if "m_" not in sample:
        fail("currency classes do not look reobfuscated to SRG")

    # ---- commands actually registered
    cmd = bytecode(tmp, "com/athensmc/athenscoins/command/FsCurrencyCommand.class")
    for sub in ["wallet", "balance", "transfer", "stats", "reload", "accept", "deny"]:
        if f'String {sub}' not in cmd:
            fail(f"command literal '{sub}' not found in FsCurrencyCommand")

    # ---- all five network packets wired
    net = bytecode(tmp, "com/athensmc/athenscoins/network/ModNetwork.class")
    for packet in ["S2CWalletSyncPacket", "S2COpenWalletPacket", "S2COpenStatsPacket",
                   "S2COpenTerminalPacket", "C2STerminalActionPacket", "S2COpenAccountPacket",
                   "C2SRequestAccountPacket", "S2COpenCentralPacket", "C2SCentralActionPacket"]:
        if packet not in net:
            fail(f"packet {packet} not registered in ModNetwork")

    # ---- the ATM is issued by banks, so it must not be craftable or in the creative tab:
    #      a machine with no bank behind it is refused on use, and handing players one that
    #      cannot work looks like a bug.
    if "data/athens_coins/recipes/atm.json" in names:
        fail("the ATM has a crafting recipe again; a crafted one has no bank and is refused")
    tab = bytecode(tmp, "com/athensmc/athenscoins/item/ModCreativeModTabs.class")
    if "ATM_ITEM" in tab:
        fail("the ATM is back in the creative tab; it must come from a bank terminal")

    # ---- breaking a branded machine must return it branded, or relocating one bricks it.
    #      These are Minecraft overrides, so in the shipped jar they carry SRG names:
    #        m_49635_ = BlockBehaviour.getDrops(BlockState, LootParams.Builder)
    #        m_7397_  = Block.getCloneItemStack(BlockGetter, BlockPos, BlockState)
    atm_block = subprocess.run([JAVAP, "-p",
                                os.path.join(tmp, "com/athensmc/athenscoins/block/AtmBlock.class")],
                               capture_output=True, text=True).stdout
    if "m_49635_" not in atm_block:
        fail("AtmBlock does not override getDrops (m_49635_); breaking an ATM would erase its bank")
    if "m_7397_" not in atm_block:
        fail("AtmBlock does not override getCloneItemStack (m_7397_)")

    # ---- item textures stay small (the sparkle regression)
    import struct
    for entry in [n for n in names if n.startswith("assets/athens_coins/textures/item/")
                      and n.endswith(".png")]:
        head = zf.read(entry)[16:24]
        w, h = struct.unpack(">II", head)
        if w > 128 or h > 128:
            fail(f"{entry} is {w}x{h}; item sprites must stay <=128")

# ==================================================================== shop mod
print()
print("=" * 72)
print("FantasticShop-1.3.0-bank.jar")
print("=" * 72)

if not os.path.exists(SHOP):
    fail("shop jar missing")
else:
    zf = zipfile.ZipFile(SHOP)
    names = set(zf.namelist())
    notes.append(f"shop jar: {len(names)} entries")

    tmp = "/tmp/audit_shop"
    subprocess.run(["rm", "-rf", tmp])
    zf.extractall(tmp)

    # ---- cash currency present and working in cents
    coin = bytecode(tmp, "com/fshop/economy/CoinEconomy.class")
    for needle in ["CASH", "TYPES", "CASH_SCALE", "formatAmount", "cashAvailable", "sanitize"]:
        if needle not in coin:
            fail(f"CoinEconomy missing {needle}")
    if "FantasticCurrencyAPI" not in coin:
        fail("CoinEconomy does not call the currency API")

    # ---- shop tied to a bank account
    shop_service = bytecode(tmp, "com/fshop/shop/ShopService.class")
    for needle in ["refreshAccount", "sellerBanked", "FantasticCurrencyAPI"]:
        if needle not in shop_service:
            fail(f"ShopService missing {needle}")
    result = bytecode(tmp, "com/fshop/shop/ShopService$Result.class")
    if "NO_BANK_ACCOUNT" not in result:
        fail("ShopService.Result has no NO_BANK_ACCOUNT")

    player_shop = bytecode(tmp, "com/fshop/shop/PlayerShop.class")
    for needle in ["getAccountNumber", "setAccountNumber", "canSell", "earningsCash"]:
        if needle not in player_shop:
            fail(f"PlayerShop missing {needle}")

    # ---- the wallet strip kept all four of its methods
    widgets = subprocess.run([JAVAP, "-p",
                              os.path.join(tmp, "com/fshop/client/ShopWidgets.class")],
                             capture_output=True, text=True).stdout
    for method in ["renderInventory", "slotAt", "renderCoins", "coinAt"]:
        if method not in widgets:
            fail(f"ShopWidgets lost {method}")

    # ---- no amethyst anywhere
    amethyst = 0
    for root, _, files in os.walk(tmp):
        for name in files:
            if name.endswith(".class"):
                if "f_144243_" in bytecode(root, name):
                    amethyst += 1
    if amethyst:
        fail(f"{amethyst} class(es) still reference AMETHYST_BLOCK_CHIME")

    # ---- lang keys for the new states
    for locale in ["en_us", "es_es", "es_mx"]:
        entry = f"assets/fshop/lang/{locale}.json"
        if entry not in names:
            continue
        lang = json.loads(zf.read(entry))
        for key in ["fshop.coin.cash", "fshop.msg.no_bank_account"]:
            if key not in lang:
                fail(f"shop lang {locale}: missing {key}")

    # ---- metadata
    toml = zf.read("META-INF/mods.toml").decode("utf-8")
    if 'version="1.3.0"' not in toml:
        fail("shop mods.toml is not version 1.3.0")
    dependency = re.search(r'modId="athens_coins"\s*\n\s*mandatory=(\w+)', toml)
    if not dependency:
        fail("shop mods.toml has no athens_coins dependency")
    elif dependency.group(1) != "true":
        fail("shop depends on athens_coins optionally, but the bank check always runs")

    # ---- entry count must match the original, nothing added or lost
    original = os.path.join(REPO, "FantasticShop-1.1.jar")
    if os.path.exists(original):
        before = len(zipfile.ZipFile(original).namelist())
        if before != len(names):
            fail(f"shop jar has {len(names)} entries, original had {before}")
        else:
            notes.append(f"shop jar entry count matches the original ({before})")

# ==================================================================== report
print()
for note in notes:
    print("note:", note)
print("=" * 72)
if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)
print("BOTH JARS COMPLETE")
