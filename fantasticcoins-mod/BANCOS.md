# Sistema bancario — diseño

Plan de implementación del banco sobre Fantastic Currency. El dinero ya existe y funciona; esto
añade **quién lo custodia**, con varias entidades compitiendo.

## Modelo de datos

Todo persistido en el `SavedData` del mundo, junto a las cuentas de cash.

```
Bank
  id            UUID interno
  name          "Banco del Pueblo"
  terminalPos   posición de la terminal (para saber a qué banco perteneces)
  bankers       Set<UUID>   autorizados a abrir la terminal
  commission    tasa diaria en puntos base (ej. 25 = 0.25% al día)
  walletLimit   saldo máximo que custodia (0 = sin límite)
  loanEnabled   bool
  loanMaxAmount céntimos
  loanInterest  puntos base por día de retraso
  loanDays      días hábiles para devolver

BankAccount
  number        5 dígitos, único en TODO el servidor
  owner         UUID del jugador
  bankId        a qué banco pertenece  (1 cuenta por jugador → índice owner→number)
  openedAt      epoch millis
  lastChargeAt  epoch millis del último cobro de comisión
  ledger        List<LedgerEntry>  (se recorta a los N últimos)

LedgerEntry
  at            epoch millis
  type          APERTURA | DEPOSITO_ATM | RETIRO_ATM | COMPRA_SHOP | VENTA_SHOP
                | TRANSFERENCIA_ENV | TRANSFERENCIA_REC | COMISION | PRESTAMO
                | PAGO_PRESTAMO | INTERES | CIERRE
  amount        céntimos (positivo o negativo)
  balanceAfter  céntimos
  note          texto corto (contraparte, nº de cuenta, etc.)

Loan
  accountNumber, principal, owed, takenAt, dueAt, lastInterestAt
```

**Índices necesarios:** `number → BankAccount`, `owner → number` (garantiza 1 cuenta por jugador),
`bankId → List<number>`.

## La terminal

Bloque `athens_coins:bank_terminal`. Sin receta, solo aparece en la pestaña de creativo y el evento
de colocación exige OP. Al romperla, el banco queda huérfano pero las cuentas sobreviven (se
reasignan cuando se coloca otra terminal y un OP la vincula).

**Sobre la textura:** la voy a dibujar yo en el mismo estilo que el cajero. Bajar una textura de
internet y meterla en tu mod es un problema de licencia — no sé bajo qué términos está publicada y
tú acabarías distribuyéndola. Si tienes una que ya sea tuya o de un pack con licencia clara,
pásamela y la uso.

### Pestañas de la GUI

| Pestaña | Quién | Qué hace |
|---|---|---|
| **Usuarios** | solo OP | Lista de jugadores conectados; añadir/quitar banqueros autorizados |
| **Apertura** | banquero | Lista de jugadores; abrir cuenta → nº de 5 dígitos único → entrega un `name_tag` con NBT (`AccountNumber`, `AccountOwner`, `BankName`) |
| **Cuentas** | banquero | Todas las cuentas del banco; clic → detalle |
| **Configuración** | solo OP | Nombre del banco, comisión diaria, límite de custodia, préstamos (activados, máximo, interés, días) |

**Detalle de cuenta:** titular, nº, banco, fecha de apertura, saldo actual, comisión que paga,
préstamo vivo si hay, y el historial del ledger paginado. Solo cash, sin monedas físicas.

## Comisión

Se cobra por **días reales de 24 h**, no por días de Minecraft. Cada cuenta guarda `lastChargeAt`;
un tick cada 30 s comprueba cuántos periodos de 24 h han pasado y cobra los que falten (así funciona
igual si el servidor estuvo apagado tres días). Si el saldo no cubre la comisión, se apunta como
deuda y se notifica.

## Préstamos

El banquero concede un préstamo desde el detalle de cuenta. Se acredita al instante y se fija
`dueAt` a N **días hábiles** (se saltan sábados y domingos del reloj real). Pasada la fecha, el
interés se aplica una vez al día sobre lo pendiente. Notificación al conectarse y al acercarse el
vencimiento.

## Cambiar de banco (la tarjeta)

1. El jugador pide al banquero **retirar todo**.
2. La cuenta se cierra: saldo a 0, entrada `CIERRE` en el ledger, y se le entrega un item
   `athens_coins:bank_card` con NBT: importe, nº de cuenta anterior, banco de origen, titular, y un
   **HMAC** con la semilla del mundo como clave.
3. Sus ofertas en Fantastic Shop quedan **congeladas** (no se pueden comprar) porque su tienda ya no
   tiene cuenta asociada.
4. Va a otro banco y hace clic derecho en la terminal **con la tarjeta en la mano**: se le abre
   cuenta nueva con nº nuevo, el importe entra intacto, la tarjeta se consume y el banquero recibe
   el `name_tag` con los datos nuevos.

Sobre el "dinero encriptado": lo que tiene sentido es **firmarlo**, no cifrarlo. Si va cifrado, el
servidor tiene que poder descifrarlo, así que la clave está en el servidor y no protege de nada
frente a un OP. Firmarlo con HMAC sí impide que alguien fabrique una tarjeta con NBT editado, que es
el ataque real. El importe se ve en el tooltip (es tu dinero) pero no se puede falsificar.

## Enganche con Fantastic Shop

- `PlayerShop` gana un campo `accountNumber`.
- Crear tienda pide el número; si no lo pone o no existe, la tienda queda en modo **solo compra**:
  sus ofertas no se pueden comprar y no puede publicar.
- Al cobrar, el dinero va a la wallet del titular de esa cuenta (ya funciona así).
- Requiere otro parche al jar del shop.

## Cambio en la wallet

La celda de "Cuenta" (la que muestra tu nombre) pasa a ser **Mi banco**: nombre de la entidad, nº de
cuenta, saldo custodiado, comisión diaria, y préstamo vivo con días restantes. Si no tienes cuenta,
dice cómo conseguirla.

## Decisiones que necesito confirmar

1. **¿La comisión se cobra sobre el saldo custodiado o es una cuota fija?** El texto dice "tasa
   fija", que puede ser un porcentaje fijo o una cantidad fija. Un porcentaje diario sobre el saldo
   castiga ahorrar; una cuota fija castiga a los pobres. Propongo **porcentaje diario con un mínimo
   y un máximo configurables**, así el banquero elige el modelo.

2. **¿Qué pasa si el banco se queda sin terminal?** (la rompen o la mueven). Propongo que el banco
   sobreviva y quede "sin sede": las cuentas siguen, pero no se puede operar hasta que un OP coloque
   otra terminal y la vincule.

3. **¿El cash sigue estando en la wallet del jugador, o pasa a estar "en el banco"?** Ahora mismo la
   wallet ES la cuenta. Si el banco custodia, hay dos saldos posibles: *en mano digital* y
   *en el banco*. Propongo lo simple: **el saldo del banco ES tu wallet**; el banco es quien te la
   administra y te cobra por ello. Si quieres dos bolsas separadas, dímelo, es bastante más trabajo
   y cambia el ATM y el shop.

4. **Sin cuenta bancaria, ¿puedes usar la wallet?** Propongo que sí, pero sin banco no ganas nada y
   no puedes vender en el shop. Si quieres que sin cuenta no tengas wallet en absoluto, cambia
   bastante el flujo de nuevos jugadores.

5. **¿Cuántas entradas de ledger guardo por cuenta?** Propongo 100, recortando las viejas, para que
   el fichero del mundo no crezca sin límite.
