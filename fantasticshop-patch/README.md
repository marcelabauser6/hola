# FantasticShop patches

FantasticShop ships as a production (SRG-obfuscated) jar, so patches are applied by recompiling
individual classes against the SRG-named Minecraft jar and swapping them back into the jar. Public
method signatures are kept identical so every other class still links.

Output: `FantasticShop-1.2.0-cash.jar` (107 entries, same as the original).

## 1. Fantastic Cash as a fourth currency

All shop money already funnelled through `com.fshop.economy.CoinEconomy` behind an `int` currency
id (0 bronze, 1 silver, 2 gold), and `ShopService` only ever calls `balance` / `withdraw` /
`deposit`. Cash slots in as id **3**, so purchases, payouts and stock handling work through the
paths that were already there.

| Class | Change |
|---|---|
| `economy/CoinEconomy` | `CASH = 3`, `TYPES = 4`, routing to `FantasticCurrencyAPI`, symbol/name/colour for cash, `sanitize()` |
| `shop/ShopOffer` | currency id clamps through `CoinEconomy.sanitize` instead of `min(2, …)` |
| `shop/PlayerShop` | fourth pending-earnings slot, plus NBT and packet fields |
| `shop/ShopService` | earnings payout loops over `CoinEconomy.TYPES` |
| `client/ShopWidgets` | fourth wallet cell showing the account balance |
| `client/screen/MainShopCreatorScreen` | currency button cycles all four; cash name and symbol |
| `client/screen/PriceInputScreen` | fourth selector cell, symbol on the price readout |

### Cash is counted in whole units

Accounts keep two decimals, but FShop prices are plain `long`s. Inside the shop cash is therefore
measured in whole units: a price of `10` means `10.00`, and a player holding `9.99` reads as `9`
and cannot afford it, with their 99 cents untouched. This keeps every existing price field, packet
and comparison working unchanged. The currency mod exposes
`FantasticCurrencyAPI.chargeUnits` / `depositUnits` / `getDisplayBalanceUnits` for exactly this.

### Client-side balance

Coin balances are counted from the inventory, so they work on both sides. Cash lives only on the
server, so the currency mod pushes the balance to the client on join and after every change, and
`FantasticCurrencyAPI.getDisplayBalance` dispatches by side. That is what lets the wallet strip
show a cash figure while rendering.

### Save compatibility

`PlayerShop` keeps writing the original three-long `earnings3` array and stores cash separately
under `earningsCash`, so an older build still reads the coin earnings from a world touched by this
one. The shop's network packet gained a fourth long, so client and server must both run this jar -
they always do, it is one file.

### Without the currency mod

Every cash path is behind `ModList.get().isLoaded("athens_coins")`. If the currency mod is absent
the fourth cell disappears, the currency button cycles three options, and any offer already priced
in cash falls back to gold. The dependency in `mods.toml` stays optional.

## 2. Softer interface sounds

Every one of FShop's six interface sounds funnelled into a single call:

```java
SimpleSoundInstance.forUI(SoundEvents.f_144243_, pitch, volume)
```

`f_144243_` was confirmed to be `AMETHYST_BLOCK_CHIME` by pairing the 1468 `SoundEvents` fields of
the SRG jar against the official-named jar position by position. That is the bright glassy ping.

Replaced with a softer palette, one sound per action instead of six sharing one. Only plain
`SoundEvent` fields were used, so no signatures changed (`UI_BUTTON_CLICK` and `NOTE_BLOCK_HARP`
are `Holder.Reference` in 1.20.1 and would have needed `.value()`):

| Action | Sound | SRG | Pitch / Volume |
|---|---|---|---|
| `click()` | `UI_LOOM_SELECT_PATTERN` | `f_12491_` | 1.00 / 0.30 |
| `select()` | `UI_LOOM_SELECT_PATTERN` | `f_12491_` | 1.10 / 0.26 |
| `success()` | `UI_LOOM_TAKE_RESULT` | `f_12492_` | 1.00 / 0.36 |
| `page()` | `BOOK_PAGE_TURN` | `f_11713_` | 0.95 / 0.30 |
| `step()` | `WOOL_HIT` | `f_12641_` | 1.15 / 0.22 |
| `spark()` | `BUNDLE_INSERT` | `f_184215_` | 1.00 / 0.28 |

Verified: zero references to `f_144243_` remain in any of the 66 classes.

## Repeatability

`tools/` carries everything needed to redo this against another FShop build:

- **`srgtool.py`** resolves SRG names from official ones using ForgeGradle's `srg_to_official`
  mapping, so patches can be written without guessing field and method names.
- **`fixmech.py`** repairs the decompiler's output. CFR produces 91 errors across 54 files, none of
  them behavioural: `(Object)` casts it inserts, `(GuiEventListener)` casts that defeat
  `addRenderableWidget`'s type inference, `ArrayList<MutableComponent>` locals passed where
  `List<Component>` is wanted, and `CreativeModeTabs` constants that Forge's access transformers
  make public at runtime but are private in the plain SRG jar - rebuilt as `ResourceKey.create`
  calls, which need no special access.
- **`apply_cash.py`** applies the currency changes and fails loudly if a pattern it expects is
  gone, so it cannot silently half-patch a future version.

### Rebuild

```sh
# 1. the SRG Minecraft jar comes from building the currency mod
cd ../fantasticcoins-mod
gradle build --no-daemon -Porg.gradle.java.installations.paths=<jdk17-path>

SRG=~/.gradle/caches/forge_gradle/mcp_repo/net/minecraft/joined/1.20.1-20230612.114412/joined-1.20.1-20230612.114412-srg.jar
FORGE=$(find ~/.gradle/caches/forge_gradle/minecraft_user_repo -name "*mapped_official*.jar" | head -1)
CURRENCY=../fantasticcoins-mod/build/libs/FantasticCurrency-3.0.0-1.20.1.jar

# 2. compile the patched classes and swap them in
cd ../fantasticshop-patch
unzip -oq FantasticShop-1.1.jar -d original
javac -nowarn -cp "$SRG:$FORGE:$CURRENCY:original" -d out $(find src -name "*.java")
cp -r out/. original/
cd original && jar --create --file ../FantasticShop-1.2.0-cash.jar --manifest META-INF/MANIFEST.MF .
```

`src/` holds the already-repaired and already-patched sources, so a straight rebuild needs neither
script. Run them only when starting again from a fresh decompile.
