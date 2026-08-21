# Fantastic Claims 7.8.0

Jar: **`Fantastic Claims-7.8.0.jar`**. Incluye todo lo del 7.7.1 y 7.7.2. Se retiran los jars
anteriores para que no haya dudas de cuál instalar.

---

## Nuevo nombre y comandos

`displayName` pasa a **Fantastic Claims**, y los comandos llevan prefijo `fs`:

| Antes | Ahora |
|---|---|
| `/claim …` | **`/fsclaim …`** (mismos subcomandos) |
| `/claimadmin` | **`/fsclaimadmin`** |
| `/claimmerge accept\|reject\|leave` | **`/fsclaimmerge accept\|reject\|leave`** |

Los subcomandos de `/fsclaim` no cambian: `menu`, `info`, `list`, `remove`, `addmember`,
`delmember`, `members`, `ban`, `unban`, `transfer`, `removemember`, `give`, `clear`.

Se actualizaron también las referencias internas, que si no se rompían en silencio:

- el botón "Mis zonas" del menú ejecutaba `claim list` → ahora `fsclaim list`
- el panel de admin ejecutaba `claimadmin stats` → ahora `fsclaimadmin stats`
- las invitaciones de grupo llevan un botón clicable con `/claimmerge accept <código>` → ahora
  `/fsclaimmerge accept <código>`
- todos los textos de ayuda y la pista de "añadir miembro"

Comprobado en un servidor real, mandando los comandos por consola:

```
fsclaim        -> === Fantastic Claims ===  (+ la ayuda completa con /fsclaim ...)
fsclaimadmin   -> "A player is required to run this command here"  (existe; abre GUI)
claim          -> Unknown or incomplete command
claimadmin     -> Unknown or incomplete command
```

### El `modId` sigue siendo `claimblocks`, a propósito

No lo he cambiado y no conviene cambiarlo. El `modId` forma parte del nombre de registro de los
items (`claimblocks:proteccion_10x10`, etc.). Si se cambiara:

- **todas las piedras de protección que los jugadores tengan en el inventario o en cofres
  desaparecerían** al arrancar, porque su id ya no existiría;
- el `claimblocks_data.json` del mundo quedaría huérfano.

El nombre que ven los jugadores y el de los comandos ya es Fantastic Claims; el id interno es
invisible salvo que alguien mire con F3 o use `/give`.

---

## Hueco 1: tolvas sacando items de la zona

El mod trataba la tolva como "contenedor que no puedes abrir", pero **no controlaba la
transferencia**. Como la altura de una zona es ±N desde la piedra, bastaba con poner una tolva justo
por debajo del límite inferior, bajo un cofre, para vaciar la base sin entrar en la zona. Lo mismo
con una vagoneta-tolva pasando por debajo.

Arreglado con `HopperGuardMixin`, que cubre los dos sentidos del movimiento de una tolva:

- `suckInItems` — la tolva absorbe del contenedor que tiene encima. Es el vector clásico, y como
  las vagonetas-tolva implementan la misma interfaz `Hopper`, quedan cubiertas también.
- `tryMoveItems` — la tolva empuja hacia el contenedor al que apunta.

La regla (`BorderGuard.blocksItemExtraction`): se corta cuando el **origen** está en una zona con
`blockChestAccess` y el **destino no es la misma zona**. Dos piedras del mismo grupo cuentan como la
misma zona, así que las tolvas internas del dueño siguen funcionando igual que antes.

## Hueco 2: agua y lava entrando desde fuera

Solo se controlaba el **cubo** (`BucketItem`); el flujo no. Vaciabas lava pegada al borde por fuera
y entraba sola.

Arreglado con `FluidGuardMixin` sobre `FlowingFluid.canSpreadTo`, que es el punto donde el fluido
decide si puede extenderse a un bloque concreto y que recibe **la posición de origen y la de
destino**, así que se puede distinguir "entra desde fuera" de "corre por dentro". Se bloquea cuando
el destino está en una zona con `blockFluids` y el origen no es la misma zona. El flujo interno no
se toca.

---

## Verificaciones

| Qué | Resultado |
|---|---|
| Compilación (34 clases) | 0 errores |
| Arranque y apagado, con Joy of Painting instalado a la vez | limpio, 0 excepciones |
| Los **6** mixins se aplican (8 objetivos) | ver abajo |
| Las inyecciones entran de verdad en los métodos objetivo | ver abajo |
| Comandos nuevos responden y los viejos ya no existen | ver arriba |

```
Mixing DispenserBlockMixin    -> DispenserBlock
Mixing PressurePlateMixin     -> BasePressurePlateBlock
Mixing FluidGuardMixin        -> FlowingFluid
Mixing HopperGuardMixin       -> HopperBlockEntity
Mixing HangingEntityMixin     -> HangingEntity
Mixing HangingEntityMixin     -> ArmorStand
Mixing HangingEntityMixin     -> ItemFrame
Mixing ServerChatPromptMixin  -> ServerGamePacketListenerImpl
```

Y en el bytecode ya transformado, que es lo que de verdad prueba que la protección existe (con
`require = 0`, un mixin que no encuentra su método falla en silencio):

```
FlowingFluid.m_75977_  (canSpreadTo) : 1 llamada al guardián
HopperBlockEntity.m_155552_ (suckIn) : 1 llamada al guardián
HopperBlockEntity.m_155578_ (tryMove): 1 llamada al guardián
```

Los nombres SRG (`m_75977_`, `m_155552_`, `m_155578_`, `f_54021_`, `m_6343_/m_6358_/m_6446_`) se
resolvieron con `tools/srg.py` cruzando los mappings de Mojang con los de MCP y comparando
descriptores, no a ojo.

## Sin probar

No he podido probar con jugadores conectados. Lo que conviene verificar en el mundo de prueba:

1. Pon un cofre con cosas dentro de tu zona y una tolva justo debajo del límite, por fuera: no
   debería sacar nada.
2. Vacía un cubo de lava pegado al borde por fuera: no debería entrar.
3. Que tus propias tolvas y tu propio agua **dentro** de la zona sigan funcionando con normalidad.

El punto 3 es el que más me interesa que mires: la regla distingue "misma zona" de "otra zona", y si
algo estuviera mal ahí, se notaría en las granjas del dueño.

## Aviso

Si tienes bloques de comandos, plugins o macros que llamen a `/claim`, hay que actualizarlos a
`/fsclaim`. El comando viejo ya no existe.
