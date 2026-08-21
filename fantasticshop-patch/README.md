# FantasticShop patches

FantasticShop ships as a production (SRG-obfuscated) jar, so patches here are applied by
recompiling individual classes against the SRG-named Minecraft jar and swapping them back into
the jar. Public method signatures are kept identical so every other class still links.

## Applied

### Interface sounds (`com/fshop/client/Sfx`)

Every one of FShop's six interface sounds funnelled into a single call:

```java
SimpleSoundInstance.forUI(SoundEvents.f_144243_, pitch, volume)
```

`f_144243_` was confirmed to be `AMETHYST_BLOCK_CHIME` by pairing the 1468 `SoundEvents` fields
of the SRG jar against the official-named jar position by position. That is the bright glassy
ping.

Replaced with a softer palette, and each action now gets a sound that suits it instead of all
six sharing one. Only plain `SoundEvent` fields were used, so no signature changes were needed
(`UI_BUTTON_CLICK` and `NOTE_BLOCK_HARP` are `Holder.Reference` in 1.20.1 and would have
required `.value()`):

| Action      | Sound                              | SRG         | Pitch / Volume |
|-------------|------------------------------------|-------------|----------------|
| `click()`   | `UI_LOOM_SELECT_PATTERN`           | `f_12491_`  | 1.00 / 0.30    |
| `select()`  | `UI_LOOM_SELECT_PATTERN`           | `f_12491_`  | 1.10 / 0.26    |
| `success()` | `UI_LOOM_TAKE_RESULT`              | `f_12492_`  | 1.00 / 0.36    |
| `page()`    | `BOOK_PAGE_TURN`                   | `f_11713_`  | 0.95 / 0.30    |
| `step()`    | `WOOL_HIT`                         | `f_12641_`  | 1.15 / 0.22    |
| `spark()`   | `BUNDLE_INSERT`                    | `f_184215_` | 1.00 / 0.28    |

Verified: the rebuilt jar has the same 107 entries as the original, `Sfx` exposes the same
public methods, and scanning all 66 classes finds zero remaining references to `f_144243_`.

## How to rebuild

Needs the SRG Minecraft jar, which ForgeGradle produces while building the currency mod:

```sh
cd ../fantasticcoins-mod
gradle build --no-daemon -Porg.gradle.java.installations.paths=<jdk17-path>

SRG=~/.gradle/caches/forge_gradle/mcp_repo/net/minecraft/joined/1.20.1-20230612.114412/joined-1.20.1-20230612.114412-srg.jar
cd ../fantasticshop-patch
mkdir -p out && unzip -oq FantasticShop-1.1.jar -d original
javac -nowarn -cp "$SRG" -d out src/com/fshop/client/Sfx.java
cp out/com/fshop/client/Sfx.class original/com/fshop/client/
cd original && jar --create --file ../FantasticShop-1.1-sonido.jar --manifest META-INF/MANIFEST.MF .
```

## Not applied yet: Fantastic Cash as a shop currency

Investigated in detail. The good news is that all money movement funnels through
`com.fshop.economy.CoinEconomy`, which already abstracts currency behind an `int type`
(0 bronze, 1 silver, 2 gold). `ShopService` only ever calls
`CoinEconomy.balance/withdraw/deposit`, so adding type 3 for Fantastic Cash makes purchases and
payouts work without touching the purchase logic.

What makes it more than a small patch:

1. **`PlayerShop` stores pending earnings as `long[3]`**, serialised to the NBT key `earnings3`
   and written to the network buffer as three explicit longs. Supporting a fourth currency
   changes the save and packet format, so it needs a migration path that does not corrupt shops
   that already exist.
2. **`CoinEconomy.balance()` is called on the client** (`ShopWidgets:77`,
   `ShopBrowseScreen:114`) to show the player's wallet. Coin balances are counted from the
   inventory so they work client-side, but Fantastic Cash lives only on the server. This needs a
   client-side balance cache kept in sync by the currency mod.
3. **The currency picker is in the client screens** (`MainShopCreatorScreen:242`,
   `PriceInputScreen`), which cycle through exactly three options and need a fourth, plus an
   icon, colour and name for cash.
4. **Recompiling those screens** means fixing the decompiler's output first: 91 mechanical
   errors across 54 files (`(Object)` casts CFR inserts, `ArrayList<MutableComponent>` where
   `List<Component>` is wanted, generic inference on `addRenderableWidget`), and several
   `CreativeModeTabs` fields that the real build reaches through Forge's access transformers and
   that are private in the plain SRG jar.

None of that is blocked, but it is a proper piece of work touching the save format of live shop
data, which is why it is not bundled with a quick sound fix.
