# Sistema bancario — diseño confirmado

Estado: **motor de reglas implementado y probado** (`bank/BankRules.java`, 63 asserts en
`tools/BankRulesTest.java`). El resto está especificado abajo en orden de construcción.

## El cambio de fondo: dos saldos, no uno

Hasta ahora la wallet *era* la cuenta. Ya no:

```
Cuenta bancaria   →  todo tu dinero, sin techo, custodiado por el banco
Wallet (cash)     →  dinero electrónico que llevas encima, CON TECHO
```

La wallet es **una tarjeta con límite**. El banco fija ese techo en sus ajustes (p. ej. 25.000):
por mucho que tengas en la cuenta, en la wallet nunca llevas más de eso. Sacas del banco a la
wallet en el cajero, y solo hasta donde quepa.

**Sin cuenta bancaria no hay dinero electrónico.** Solo monedas físicas, y la wallet no te dice
cuánto valen en cash ni te muestra tasas: eso solo aparece si tienes cuenta, y son las tasas *de tu
banco*.

## Tasas: oficial con techo y suelo

La tasa **oficial** del servidor vive en el config del mod (y el banco central la puede cambiar en
partida). Cada banco fija las suyas, pero solo dentro de una banda alrededor de la oficial, para
forzarles a competir sin descontrolar la economía.

Con margen del 15% (configurable):

| Moneda | Oficial | Suelo | Techo |
|---|---|---|---|
| Bronce | 0.15 | 0.13 | **0.17** |
| Plata | 1.35 | 1.15 | 1.55 |
| Oro | 12.15 | 10.33 | 13.97 |

`BankRules.clampRate` fuerza cualquier valor dentro de la banda, así que un banquero no puede
guardar una tasa fuera de política ni por error ni a mala fe.

## Comisión: cantidad fija, periodo configurable

No es un porcentaje. Es **dinero** (céntimos o enteros) que el banco cobra cada N días reales:
diario, cada 3 días, semanal, lo que ponga el banquero. Es el ingreso del banco, y de ahí salen los
préstamos.

Se cuentan los periodos transcurridos, no se dispara un temporizador: si el servidor estuvo apagado
4 días, al arrancar cobra los 4 que faltaban. Hay un tope de recuperación (por defecto 7) para que
una caída larga no le presente a nadie una factura ruinosa. El reloj avanza exactamente lo cobrado,
así que el tiempo parcial no se pierde ni se cobra dos veces — está testeado.

## Préstamos

Salen de la **reserva del banco**: no puede prestar lo que no tiene. `BankRules.maxLoan` lo limita
por reserva, por política y por lo que el cliente ya deba.

Vencimiento a N **días hábiles** (se saltan sábado y domingo del reloj real). Pasada la fecha, el
interés se aplica una vez por día hábil vencido, en puntos base sobre lo pendiente (250 = 2,5%/día).
El fin de semana no genera interés.

## Reservas y banco central

- El banco ingresa por **comisiones**.
- Una **terminal de banco central** (bloque aparte, solo OP) permite: ver todos los bancos, inyectar
  cash en sus reservas, ver sus movimientos, y fijar las tasas oficiales en partida.

## Cajeros por banco

El cajero deja de ser genérico:

- La terminal del banco **genera cajeros a tu inventario**, los que quieras, con NBT que los liga a
  ese banco.
- El cajero **adopta la identidad del banco**: color de su GUI, nombre propio.
- Sus tasas son las del banco que lo emitió (dentro de la banda).
- Colocas cada uno donde quieras, en plazas distintas.

## La terminal del banco

Bloque `athens_coins:bank_terminal`. Sin receta, solo creativo, y colocarlo exige OP.

| Pestaña | Quién | Qué hace |
|---|---|---|
| **Usuarios** | solo OP | Jugadores conectados; añadir/quitar banqueros autorizados |
| **Apertura** | banquero | Abrir cuenta → nº único de 5 dígitos → entrega un `name_tag` con NBT (nº, titular, banco) |
| **Cuentas** | banquero | Todas las cuentas del banco; clic → detalle |
| **Cajeros** | banquero | Generar cajeros de este banco al inventario |
| **Configuración** | solo OP | Nombre, colores, techo de wallet, comisión (importe + periodo), tasas, préstamos |

**Detalle de cuenta:** titular, nº, banco, apertura, saldo en cuenta, saldo en wallet, comisión que
paga, préstamo vivo, y el ledger paginado (aperturas, depósitos y retiros de cajero, compras y
ventas del shop, transferencias, comisiones, préstamos, intereses). Solo cash.

## Cambiar de banco: la tarjeta

1. El jugador pide **retirar todo** al banquero.
2. Cuenta cerrada, y recibe `athens_coins:bank_card` con NBT: importe, nº anterior, banco de origen,
   titular, y un **HMAC** con la semilla del mundo.
3. Sus ofertas en Fantastic Shop quedan **congeladas**.
4. En otro banco, clic derecho en la terminal **con la tarjeta en mano**: cuenta nueva, importe
   intacto, tarjeta consumida, y el banquero recibe el `name_tag` con los datos nuevos.

Nota sobre "encriptado": lo correcto es **firmarlo**, no cifrarlo. Cifrado obliga a que la clave
esté en el servidor, así que no protege de nada; un HMAC sí impide fabricar una tarjeta editando el
NBT, que es el ataque real.

## Enganche con Fantastic Shop

`PlayerShop` gana `accountNumber`. Al crear tienda se pide; sin número válido la tienda queda en
**solo compra**: no publica y sus ofertas no se pueden comprar. Requiere otro parche al jar.

## Wallet

La celda de "Cuenta" pasa a **Mi banco**: nombre de la entidad, nº de cuenta, saldo en el banco,
techo de la wallet, comisión y periodo, y préstamo vivo con días restantes. Sin cuenta, explica cómo
conseguirla.

## Orden de construcción

1. ~~Motor de reglas + tests~~ **hecho**
2. Modelo de datos y persistencia (`Bank`, `BankAccount`, `LedgerEntry`, `Loan`, `BankData`) y el
   corte de la wallet en dos saldos
3. Terminal del banco + bloque + GUI de 5 pestañas + `name_tag` de cuenta
4. Cajeros por banco (NBT, identidad, tasas) — sustituye al cajero actual
5. Celda de banco en la wallet + bloqueo de cash sin cuenta
6. Comisiones y préstamos en marcha (ticker)
7. Terminal de banco central
8. Tarjeta de traspaso
9. Parche del shop para exigir número de cuenta

El paso 2 es el que rompe compatibilidad con los saldos actuales, así que llevará migración: el
saldo de wallet que ya exista pasa a la cuenta si el jugador tiene banco, y si no queda retenido
hasta que abra uno.
