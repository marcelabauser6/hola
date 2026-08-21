# Fantastic Blocks 7.7.1 — arreglos para servidores Mohist

Jar: **`Fantastic Blocks-7.7.2.jar` (el 7.7.1 fue reemplazado por el 7.7.2)** (raíz del repo). Código fuente en `fantastic-blocks/`.
Sustituye directamente al 7.7.0 en `mods/`: mismo `modId`, y el `claimblocks_data.json` que ya
tengas se carga sin migración ni pérdida.

---

## 1. Añadir miembros y baneos en servidores sin autenticación · **la causa real del fallo**

`PlayerLookup.resolve()` resolvía los nombres con `GameProfileCache.get(String)`. Ese método,
cuando el nombre no está en `usercache.json`, sale a la **API de Mojang**. En un servidor
**offline-mode** eso devuelve el UUID *premium*, que no es el UUID que realmente tiene el
jugador en tu servidor: el miembro quedaba guardado con un UUID que no coincidía con nadie y
"no le funcionaba". Y además la llamada es **bloqueante en el hilo principal**, así que
congelaba el servidor unos segundos en cada intento.

Ahora:

- Si el servidor no usa autenticación (`usesAuthentication() == false`), el UUID se calcula de
  forma determinista con `UUIDUtil.createOfflinePlayerUUID(nombre)` — exactamente el mismo que
  genera el servidor al entrar el jugador. Sin red y sin margen de error.
- Si el servidor sí es premium, la consulta se hace en un hilo aparte
  (`ClaimBlocks-ProfileLookup`) y la respuesta vuelve al hilo principal. El servidor ya no se
  congela.
- `resolve()` nunca sale a la red; para eso está el nuevo `resolveAsync()`, que es el que usan
  añadir miembro, banear, desbanear y la transferencia de admin.

**Verificado:** `tests/UuidCheck.java` comprueba que el UUID generado coincide con
`UUID.nameUUIDFromBytes("OfflinePlayer:" + nombre)` para varios nombres.

## 2. Banear desde una piedra de un grupo no hacía nada

`isBanned()` leía los baneos de la piedra madre del grupo, pero `banPlayer()` los escribía en la
piedra desde la que abrías el menú. Banear desde una piedra hija no tenía ningún efecto.

Ahora los baneos siempre se leen y se escriben en la piedra madre, banear expulsa al jugador de
la lista de miembros de todas las piedras del grupo, y `unbanPlayer()` limpia también los baneos
huérfanos que hubieran quedado escritos en piedras hijas por la versión anterior.

## 3. La mitad de los flags del menú no hacían nada

`denyForVisitor()` terminaba en `return publicMode ? true : true` — es decir, negaba cualquier
acción a cualquier visitante **ignorando el flag**. Y al final de `regularChecks()` había un
"catch-all" que negaba toda interacción. Encender o apagar estos botones daba el mismo
resultado:

`Romper`, `Construir`, `Cofres`, `Yunques`, `Letreros`, `Puertas`, `Fluidos`, `Usar items`,
`Interactuar con entidades`, `Talar`, `Cosechar`, `Modo público`.

Ahora cada comprobación consulta su propio flag y se ha eliminado el catch-all. Consecuencias
visibles:

- **Modo público** ya funciona: se entra y se interactúa, pero no se construye ni se rompe.
- Un visitante dentro de una zona ajena vuelve a poder **comer y usar items** (antes se
  bloqueaba sin mirar `blockItemUse`).
- Un visitante puede **defenderse de los mobs hostiles** dentro de una zona ajena. Antes
  `onAttackEntity` negaba dañar cualquier entidad si uno de cuatro flags estaba activo, y los
  cuatro vienen activos por defecto.
- `onEntityInteract` tenía la cadena de condiciones mal encadenada y acababa negando siempre;
  ahora distingue contenedor (`blockChestAccess`) de resto de entidades (`blockEntityInteract`).
- `isMatureCrop()` devolvía siempre `false`, así que la rama de "no cosechan cultivos" era código
  muerto. Ahora usa `CropBlock.isMaxAge()`.
- PVP: antes dos miembros de la misma zona podían pegarse aunque el PVP estuviese desactivado,
  porque la condición exigía que alguno **no** fuese miembro. Ahora manda el flag, y `pvpAll`
  ("todos se pueden atacar aquí") tiene prioridad para zonas de arena.

`publicMode` se mantiene como refuerzo de construir/romper, que es lo que dice su descripción
("todos entran pero no modifican").

## 4. Los baneados morían en bucle

`repelBanned()` aplicaba empuje **más 5 de daño cada 15 ticks**. Si un baneado se desconectaba
dentro de la zona y volvía a entrar ahí, aparecía y moría en bucle sin poder moverse.

Ahora se le teletransporta al borde más cercano por fuera, buscando un hueco de 2 bloques de alto
para no incrustarlo, sin daño, con aviso cada 2 segundos. El texto del botón del menú se ha
corregido para que diga lo que hace de verdad.

## 5. Pérdida de datos y tirones al guardar

`save()` se llamaba en **cada** cambio (cada clic de flag, cada miembro) y escribía el JSON
completo directamente sobre el fichero final, en el hilo principal. Un cierre a lo bruto a mitad
de escritura dejaba el fichero corrupto y perdías todas las zonas.

Ahora:

- La serialización sigue en el hilo del servidor (para tener una foto coherente), pero la
  escritura va a un hilo de disco (`ClaimBlocks-IO`).
- Se escribe a `.tmp` y se mueve de forma **atómica**, dejando una copia `.bak`.
- Varias llamadas seguidas a `save()` se agrupan en una sola escritura.
- Al arrancar, si `claimblocks_data.json` no se puede leer, se restaura automáticamente del
  `.bak`.
- Al apagar se usa `saveNow()` (sincrónico), para no dejar nada en el aire.

## 6. Compatibilidad Mohist

- **Mixins con `require = 0`** en `PressurePlateMixin` y `DispenserBlockMixin`. CraftBukkit
  parchea `checkPressed` y `dispenseFrom` para sus propios eventos; si en alguna build cambia la
  firma, antes el servidor **no arrancaba**. Ahora el mixin simplemente se salta.
- **Canal de red opcional.** Se pasaba `PROTOCOL::equals` como predicado, que devuelve `false`
  para los valores especiales `ABSENT` y `ACCEPTVANILLA` y convertía el canal en obligatorio,
  rechazando conexiones sin el mod o a través de proxy. Ahora usa
  `NetworkRegistry.acceptMissingOr(...)`: el borde por paquete es un extra, no un requisito.
- **Vuelo**: al salir de la zona el mod ponía `mayFly = false` a cualquiera que hubiese marcado,
  quitando el vuelo que hubiera dado un plugin (Essentials `/fly`, rangos…). Ahora se comprueba
  antes si el vuelo viene de fuera.
- Descripción del `mods.toml` corregida: decía "visible en clientes vanilla", pero el mod
  registra 10 items propios, así que el cliente necesita tenerlo instalado.

## 7. Rendimiento

- `regularChecks()` llamaba a `getClaimAt()` hasta **8 veces por clic derecho** (y `getClaimAt`
  recorre linealmente todas las zonas del mundo). Ahora resuelve la zona una sola vez.
- El paquete de bordes se enviaba a todos los jugadores cada segundo aunque la lista fuese
  idéntica o vacía. Ahora solo cuando cambia.
- `getInstance()` de `ClaimManager` es `synchronized` (tenía una carrera en el arranque).
- Los menús ya no editan una zona borrada mientras están abiertos.

---

## Verificaciones hechas

| Qué | Resultado |
|---|---|
| Compilación completa (30 clases, Java 17, nombres SRG) | 0 errores |
| Estructura del jar frente al 7.7.0 | 86/86 entradas, ninguna perdida |
| Arranque y apagado en un servidor Forge 1.20.1 real, `online-mode=false` | limpio, sin excepciones |
| Aplicación de los 3 mixins (`-Dmixin.debug.verbose=true`) | las 3 líneas `Mixing … into …` |
| `tests/UuidCheck` — UUID offline | coincide con el del servidor (4/4 nombres) |
| `tests/ClaimLogicCheck` — miembros, baneos, ida y vuelta a JSON | 19/19 |
| Escritura atómica: `claimblocks_data.json` + `.bak` releídos al reiniciar | correcto |

**Lo que no he podido probar aquí:** no tengo acceso a un servidor Mohist (su API de descargas no
responde desde este entorno), así que la prueba de carga es sobre Forge 1.20.1 puro, que es la
misma ruta de mixins y de eventos que usa Mohist, pero no ejercita el lado CraftBukkit. Tampoco
he podido probar con un cliente conectado, así que los flujos de menú (clic en cabezas, prompts
de chat) están revisados y compilados, pero no jugados. Recomiendo probar primero en un mundo de
prueba con dos cuentas antes de ponerlo en el servidor bueno.

## Lo que sigue pendiente (a propósito)

- **Índice espacial para `getClaimAt()`.** Sigue siendo un recorrido lineal por todas las zonas
  del mundo. Con 300-500 zonas y muchos jugadores se nota. La solución es un
  `Map<chunk, List<Claim>>`, pero hay que mantenerlo sincronizado en cinco sitios distintos y un
  índice desincronizado significa protecciones que dejan de aplicarse en silencio, que es peor
  que el lag. No me parecía sensato meterlo sin poder probarlo en un servidor con jugadores.
- **Puente Bukkit.** Las protecciones dependen de eventos de Forge, así que cualquier acción
  hecha desde un plugin (WorldEdit como plugin, `/setblock` de plugins, aperturas por API) se las
  salta. Cerrarlo requiere registrar un `Listener` de Bukkit por reflexión, y es una feature
  nueva, no un arreglo.
