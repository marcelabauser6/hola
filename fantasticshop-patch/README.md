# FantasticShop patches

FantasticShop ships as a production (SRG-obfuscated) jar, so patches are applied by recompiling
individual classes against the SRG-named Minecraft jar and swapping them back into the jar. Public
method signatures are kept identical so every other class still links.

Output: `FantasticShop-1.4.0-bank.jar` (107 entries, same as the original).

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

### Cash is counted in cents

Accounts keep two decimals and FShop prices are plain `long`s, so a cash price is stored as a count
of cents: `150` means 1.50. Every existing price field, packet and comparison keeps working on
integers; only the display and the price entry know about the decimal point.

That has one consequence worth stating plainly, because it was a usability trap: a raw price of `10`
is ten *cents*, not ten units. `PriceInputScreen` therefore lets a cash price be **typed with a
decimal point** — `12.50` — and converts it, so nobody has to work out that twelve fifty is `1250`.
The `.` is accepted only for cash and only once, and a third decimal is untypable rather than
rejected afterwards. The +/- buttons and the `64` shortcut are rendered back into the same notation,
so typing and clicking always agree.

An earlier version of this document claimed whole units and named
`FantasticCurrencyAPI.chargeUnits` / `depositUnits` / `getDisplayBalanceUnits`. The code has always
used cents and the plain `charge` / `deposit` calls; the document was wrong, not the code.

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

## 3. Bank accounts, and the money bugs that came with them

A shop settles into a bank account: `PlayerShop` carries an `accountNumber`, `ShopService.buy`
re-checks on every purchase that the number still belongs to the owner, and listing re-syncs it. A
shop with no account is *frozen* — browsable, but nothing can be bought or listed.

Widening the currency count from three to four left a set of loops that still stopped at three, and
each one was a money bug rather than a cosmetic one:

| Where | What happened |
|---|---|
| `AmountScreen`, `ShopViewScreen` | The `balances` array from the shop-view packet is three longs. Indexing it with the cash id threw `ArrayIndexOutOfBoundsException`, so **any cash-priced offer crashed the screen**. Cash is now read client-side from the balance the currency mod pushes, which that packet never needed to carry. |
| `/fshop collect` | Summed three currencies but called `clearEarnings()`, which zeroes four. Pending cash was **deleted without being paid**. |
| `SaveMainShopPacket` | Carried over three of the four earnings slots, so saving the server shop **dropped its cash earnings**. |
| `ShopManageScreen` | Drew and hit-tested three earnings cells, so cash earnings were **invisible and un-collectable** from the GUI. |
| `CoinEconomy.deposit` | Returned `void` and discarded the currency mod's answer. A cash payout can genuinely fail — it needs a live account — and the caller cleared the balance either way. |

Payout now takes one currency at a time and puts back anything the deposit refused, so a failed
payout leaves the money pending instead of destroying it. Cash settles through
`FantasticCurrencyAPI.creditSaleToAccount` with the shop's stored number, which is what linking a
shop to an account was for; it was previously credited to whatever account the seller happened to
have at collect time.

`ShopService.stock()` and `setPrice()` re-sync the account but deliberately do **not** block: nothing
can be bought from a frozen shop anyway, and refusing to let an owner move stock or fix a price while
they arrange a bank account would strand their items for no gain.

## Rebuild

```sh
# The currency mod's build populates the ForgeGradle caches this needs, so do it first.
cd ../fantasticcoins-mod && gradle build --no-daemon \
    -Porg.gradle.java.installations.paths=<jdk17-path>

cd ../fantasticshop-patch
bash tools/rebuild.sh                 # -> FantasticShop-1.4.0-bank.jar
python3 tools/verify_shop.py          # static guard, see below
```

`tools/rebuild.sh` pins JDK 17 (Forge 1.20.1 targets it, and the sandbox defaults to a newer one),
asks Gradle for the resolved compile classpath rather than guessing at jar paths — fmlcore, netty,
authlib, brigadier and DataFixerUpper all live in different caches — and puts the SRG Minecraft jar
ahead of it so `net.minecraft.*` resolves to SRG names. It also merges `lang-overlay/` into the
extracted language files: the patched classes reference keys the base jar never had, and a missing
key renders as the raw identifier, so `fshop.msg.no_bank_account` used to appear on screen verbatim.

`src/` holds the already-repaired and already-patched sources, so a straight rebuild needs neither
`apply_*.py` script. Run those only when starting again from a fresh decompile of a future FShop
build; `apply_bank.py` anchors on what `apply_cash.py` produces, so it must run second.

## Verification

There is no test harness here and there cannot be one: the mod ships obfuscated and the patch is a
set of classes swapped into a jar. `tools/verify_shop.py` is the substitute — a static guard where
every check corresponds to one of the defects above and says so in its failure message. It checks
that no currency loop stops at three (distinguishing those from the three +/- step buttons, which
legitimately loop to 3), that both buy screens read balances through a clamped helper, that both
payout paths take-and-restore rather than clear, that the account gates are present, that cash prices
accept a typed decimal, and that every referenced translation key is in every shipped locale. It also
opens the built jar and asserts the entry count still matches the base jar, which catches a class
being added or lost by mistake.

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
- **`apply_bank.py`** adds the account field, the two `ShopService` gates, the `NO_BANK_ACCOUNT`
  result and the check in `createShop`. It anchors on what `apply_cash.py` produces, so it runs
  second.
- **`rebuild.sh`** and **`merge_lang.py`** build the jar and merge the added translation keys;
  **`verify_shop.py`** is the static guard over the result. These three are for every build, not just
  a re-patch.

Both `apply_*.py` scripts operate on a `patchsrc/` tree that is not kept in the repo — `src/` is
their applied output. If they are ever used again they should be extended with the fixes described
under "Bank accounts" above, which were made directly in `src/`.
