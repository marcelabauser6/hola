# FantasticCurrency Bridge

Conecta **FantasticCurrency** con **Shopkeepers** y con cualquier plugin que hable **Vault**.

## Lo que hay que saber primero

**Shopkeepers no tiene economía virtual.** Comercia con *items* de moneda (`currency-item: EMERALD` en su
config). La única mención a Vault en su jar es una gráfica de estadísticas — no mueve dinero por ahí.

Así que la forma de que comercie con el dinero de este servidor no es interceptar sus ventas, es **que su
moneda sea el dinero**: las Fantastic Coins que el mod ya acuña, que los jugadores ya llevan y ya cambian por
saldo en el ATM. Con la moneda puesta, todo Shopkeepers funciona intacto — tiendas de jugador, de admin,
contratación, registro de ventas — porque no sabe que pasa nada raro.

## Las dos mitades

| Mitad | Qué hace | Necesita |
|---|---|---|
| **Moneda de Shopkeepers** | Sustituye su moneda por las Fantastic Coins | Shopkeepers |
| **Economía en Vault** | Publica el saldo como economía de Vault | Vault |

Ninguna necesita a la otra. Cada una informa de su estado y un fallo en una deja la otra funcionando.

## Por qué no se configura en el `config.yml`

Porque no se puede. El config nombra un `Material` de Bukkit, y `athens_coins:gold_coin` no es uno. Pero
Shopkeepers empareja la moneda en runtime con `ItemData`, que envuelve un `ItemStack` completo, y un
`ItemStack` **sí** puede contener un item modded en un servidor híbrido. El puente coge la moneda del
registro del juego y la mete en el registro de monedas de Shopkeepers, rodeando el config en vez de pasar por
él.

Hay que rehacerlo tras cada `/shopkeeper reload`, porque eso reconstruye la lista desde el config y volvería a
poner esmeraldas. El puente lo hace solo.

## La trampa de las denominaciones

FantasticCurrency valora cada moneda en **céntimos**, cualquier cantidad. Shopkeepers precia todo en **unidades
enteras** de una moneda base, y admite exactamente dos: la base (vale 1) y una alta que vale un múltiplo
entero.

Así que la base es la moneda más barata y la alta tiene que ser múltiplo exacto. Si el oro vale 500 céntimos y
el bronce 3, **no hay entero** que exprese el oro en bronces: el puente lo rechaza y lo dice, en vez de dejar
que Shopkeepers redondee y pierda céntimos en cada venta.

## Comprobar qué se conectó

```
/fcbridge
```

Dice si FantasticCurrency respondió, si puede construir items del juego, si publicó la economía en Vault y qué
moneda quedó en Shopkeepers, con el valor de cada denominación.

## Instalación

`plugins/`, junto a Vault y Shopkeepers. Necesita un servidor híbrido (Mohist) donde el mod y los plugins
convivan.

## Compilar

```sh
gradle build
```

Las pruebas corren en una JVM normal y cubren las dos aritméticas que podrían perder dinero en silencio: el
reparto de denominaciones y la conversión céntimos↔`double` de Vault.
