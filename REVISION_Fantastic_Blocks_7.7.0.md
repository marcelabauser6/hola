# Revisión técnica — Fantastic Blocks 7.7.0 (`claimblocks`)

Revisión hecha sobre el `.jar` descompilado (32 clases, ~7.200 líneas). Objetivo: verificar que cada función
sirva en un servidor **Mohist 1.20.1** (Forge + plugins Bukkit).

Metadatos: `modLoader=javafml`, `loaderVersion=[47,)`, MC `[1.20.1,1.20.2)`, 3 mixins
(`PressurePlateMixin`, `DispenserBlockMixin`, `ServerChatPromptMixin`), refmap presente y correcto.

---

## 1. Lo de añadir / banear miembros: **sí, ya quedó bien**

El problema viejo era que el chat lo consumían los plugins antes de llegar al mod. Ahora está resuelto bien:

- `ServerChatPromptMixin` inyecta en `HEAD` de `ServerGamePacketListenerImpl.handleChat`
  (refmap → `m_7388_`), o sea **a nivel de paquete, antes de Bukkit**. Ningún plugin de chat puede robarlo.
- `ChatPromptRouter.consume()` corre en el hilo de red (en Mohist el chat es asíncrono) y hace
  `server.execute(...)` para volver al hilo principal. Correcto y thread-safe (`ConcurrentHashMap`).
- Doble red de seguridad: `ServerChatEvent` como fallback + supresión del mensaje por texto/2 s para que
  la respuesta no salga en el chat público.
- `require=0` en ambos injects → si Mohist cambia el método, no crashea, solo cae al fallback.
- Además hay salidas alternativas que no dependen del chat: `MemberSelectMenu` (clic en cabezas de
  jugadores conectados) y los comandos `/claim addmember`, `/claim delmember`, `/claim members`.

**Veredicto:** el flujo de prompts funciona en Mohist. Pero quedan 2 bugs reales en esa misma función:

### 1.1 (CRÍTICO) UUID equivocado en servidores offline-mode / cracked

`PlayerLookup.resolve()` hace: jugador conectado → si no, `GameProfileCache.get(nombre)`.
`GameProfileCache.get(String)` de vanilla, si el nombre **no está en `usercache.json`**, hace una
**consulta HTTP bloqueante a la API de Mojang en el hilo principal**. Dos consecuencias:

1. Congela el servidor unos segundos (o timeout) al añadir/banear a alguien que nunca entró.
2. En servidor **offline-mode** devuelve el UUID *premium*, que **no es** el UUID offline real del jugador
   → el miembro se guarda con un UUID que nunca coincide → *"lo agregué y no le funciona"*.
   Esto explica muy probablemente la falla original que reportaste.

Arreglo:

```java
// 1) nunca bloquear el main thread
cache.getAsync(name, opt -> server.execute(() -> callback.accept(opt)));

// 2) si el servidor no usa autenticación, generar el UUID offline
if (!server.usesAuthentication()) {                    // m_129799_()
    UUID id = UUIDUtil.createOfflinePlayerUUID(name);  // MD5 de "OfflinePlayer:"+name
    return new Resolved(id, name, server.getPlayerList().getPlayerByName(name));
}
```

### 1.2 El baneo es demasiado agresivo

`PlayerTracker.repelBanned()` aplica velocidad + **5 de daño cada 15 ticks**. Si un baneado se desconecta
dentro de la zona y vuelve a entrar ahí, aparece y muere en bucle sin poder moverse.
Mejor: teletransportar al punto más cercano fuera del borde (`connection.teleport(...)`) y no hacer daño,
o dejar el daño solo como opción configurable (flag).

Detalles menores del mismo flujo:
- `handleBanPlayer` llama `banPlayer()` y luego `removeMember()`, pero `banPlayer()` ya hace `removeMember()`.
- En zonas agrupadas `isBanned()` lee los baneos de la piedra madre, pero `banPlayer()` los escribe en la
  piedra donde abriste el menú → **banear desde una piedra hija no tiene efecto**. Hay que redirigir
  `banPlayer/unbanPlayer` a la madre (igual que `getFlags()`).

---

## 2. (CRÍTICO) La mitad de los flags del menú no hacen nada

Este es el hallazgo más grave y no tiene que ver con Mohist, es lógica del mod.

En `BlockProtectionEvents`:

```java
private static boolean denyForVisitor(Claim c, Player p) {
    if (c.canModify(p)) return false;
    if (isBypassing(p)) return false;
    return c.getFlags().publicMode ? true : true;   // <-- siempre true
}
```

Y al final de `regularChecks()` hay un catch-all:

```java
Claim c = getClaimAt(...);
if (c != null && !c.canModify(p)) { deny("No puedes interactuar en esta zona."); return FAIL; }
```

Resultado: para cualquier visitante se **niega todo**, sin mirar el flag. Los siguientes botones del GUI son
decorativos (encenderlos o apagarlos da exactamente el mismo comportamiento):

`BREAKING`, `BUILDING`, `CHEST_ACCESS`, `ANVIL_USE`, `SIGN_EDITING`, `DOORS_ACCESS`, `FLUIDS`,
`ITEM_USE`, `ENTITY_INTERACT`, `TREE_CHOPPING`, `CROP_HARVEST`, `PUBLIC_MODE`.

Consecuencias visibles en el server:
- "Modo público" no abre la zona a nadie.
- Un visitante dentro de una zona ajena **no puede comer, usar arco, ni usar nada** (`onRightClickItem`
  bloquea sin mirar `blockItemUse`).
- `onAttackEntity` niega dañar *cualquier* entidad a los visitantes → no pueden ni defenderse de un zombi
  dentro de una zona ajena.
- `isMatureCrop()` devuelve `false` siempre → la rama de `blockCropHarvest` es código muerto.

Arreglo: que cada chequeo consulte su flag, p. ej.
`if (!c.canModify(p) && c.getFlags().blockChestAccess) deny(...)`, y borrar el catch-all final.

---

## 3. Compatibilidad Mohist — punto por punto

| Función | Estado en Mohist | Nota |
|---|---|---|
| Prompts de chat (miembros, ban, bienvenida, merge) | ✅ | mixin a nivel de paquete + fallback |
| Menús GUI (`ChestMenu` 9x6) | ⚠️ | ver 3.1 |
| Items de protección (10 tiers, `DeferredRegister`) | ⚠️ | ver 3.2 |
| Comandos `/claim`, `/claimadmin`, `/claimmerge` (Brigadier) | ✅ | Mohist los expone también a Bukkit |
| Eventos de bloques/entidades (Forge) | ⚠️ | ver 3.3 |
| Mixins pressure plate / dispenser | ⚠️ | ver 3.4 |
| Partículas + borde por paquete | ⚠️ | ver 3.5 |
| Persistencia JSON en `world/data/` | ⚠️ | ver 4 |
| Vuelo / efectos por tier | ✅ | ojo: pelea con plugins de fly (ver 3.6) |

### 3.1 Menús
`NetworkHooks.openScreen` en Mohist dispara `InventoryOpenEvent` y cada clic dispara `InventoryClickEvent`.
Si un plugin (anti-dupe, GUI, anticheat) cancela esos eventos, **el menú no abre o los clics no responden** y
el mod no se enterará. El código en sí está bien blindado (`quickMoveStack` vacío, `clicked()` sin `super`,
slots ≥54 ignorados → no se pueden robar ítems). Recomendación: loguear en `debug` cuando `openScreen` no
produzca un `containerMenu` nuevo, para poder diagnosticar.

### 3.2 Items → el cliente **necesita** el mod
Se registran 10 items `claimblocks:proteccion_*`. Un cliente vanilla no puede sincronizar ese registro.
Además `ClaimNetwork.init()` usa:

```java
NetworkRegistry.newSimpleChannel(id, () -> "1", "1"::equals, "1"::equals);
```

Con predicados estrictos el canal queda **obligatorio**: los valores especiales `ABSENT` y `ACCEPTVANILLA`
devuelven `false`. La descripción del `mods.toml` dice "visible en clientes vanilla" — eso es engañoso.
Si quieres tolerar clientes sin el mod (o proxies tipo Velocity/Bungee), usa
`NetworkRegistry.acceptMissingOr("1")` o `s -> true` en ambos predicados. Y los ítems tampoco aparecen en
ninguna pestaña creativa: solo se consiguen con `/claim give`, asumo que a propósito (tienda).

### 3.3 Los plugins se saltan las protecciones
Todo se protege con eventos **de Forge**. Cualquier acción hecha desde el lado Bukkit no pasa por ahí:
WorldEdit/FAWE como plugin, VoxelSniper, `/setblock` de plugins, cofres abiertos por API, etc.
Es la limitación estructural de un mod puro en un híbrido. Si quieres cerrarlo, hay que añadir un
**puente Bukkit opcional por reflexión** (registrar un `Listener` de `BlockBreakEvent`,
`PlayerInteractEvent`, `InventoryOpenEvent` solo si la clase `org.bukkit.Bukkit` existe en el classpath).
Es la mejora de compatibilidad con más impacto real que le puedes hacer.

### 3.4 Mixins
`PressurePlateMixin` y `DispenserBlockMixin` no tienen `require = 0` y `claimblocks.mixins.json` declara
`"required": true` + `defaultRequire: 1`. Si una versión de Mohist/CraftBukkit toca esas firmas (CraftBukkit
ya parchea ambos métodos para sus eventos), el arranque **crashea** en vez de degradarse.
Pon `require = 0` y un `LOGGER.warn` de aviso, como ya hiciste con el mixin de chat.

### 3.5 Partículas y bordes
`sendBorderPackets` recorre **todos los mundos × todos los jugadores** cada 20 ticks y
`renderClaimParticles` cada 4 ticks, y por cada jugador itera `getClaimsOf(uuid)` (escaneo lineal, ver §4).
El paquete de bordes se envía siempre, incluso vacío, a todos los jugadores cada segundo. Envía solo cuando
la lista cambie respecto al último envío.

### 3.6 Vuelo
`PassiveEffectsManager.handleFlight` escribe `abilities.mayfly` directo. En Mohist eso choca con plugins tipo
Essentials `/fly`: el mod solo respeta `alreadyCanFly` en el momento de conceder, pero al salir de la zona
hace `mayfly = false` a cualquiera que él haya marcado → puede quitar el fly que dio el plugin.
Guarda el estado previo y restáuralo.

---

## 4. Rendimiento y datos (importa mucho con muchas zonas)

1. **`getClaimAt()` es un escaneo lineal** sobre todas las zonas del mundo, y se llama muchísimo:
   `PlayerTracker` cada tick por jugador, partículas cada 4 ticks, `PassiveEffects`, y hasta **8 veces**
   en un solo clic derecho dentro de `regularChecks()` (busca la misma zona una y otra vez).
   Con 300-500 zonas y 30 jugadores es lag garantizado.
   Arreglo: índice espacial `Map<Long /*chunkX,chunkZ*/, List<Claim>>` y en `regularChecks` resolver la zona
   **una sola vez** y reutilizar la variable.
2. **`save()` se llama en cada cambio individual** (cada clic de flag, cada miembro, cada zona creada) y
   serializa *todo* el JSON en el hilo principal. Usa un flag `dirty` + guardado cada 30 s / al apagar.
3. **Escritura no atómica**: `Files.writeString(file, ...)` directo sobre `claimblocks_data.json`. Si el
   server muere a mitad de escritura, se pierden todas las zonas. Escribe a `.tmp` y luego
   `Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)`, y guarda 1-2 backups rotativos.
4. `BlockProtectionEvents.extinguishAround` escanea 13×13×13 = **2.197 bloques** por jugador dentro de zona
   con `blockFire`, cada 40 ticks. Usa el `FireBlock`/`BlockEvent` en vez de barrer.
5. `PlayerTracker.findClaimById` y `getGroupClaims` hacen `getAllClaims()` (copia de todas las listas) —
   ya existe `claimIndex`, úsalo también ahí.

---

## 5. Cosas menores

- `ClaimManager.getInstance()` con lazy-init sin `synchronized` (posible carrera en arranque).
- Los menús guardan una referencia dura al `Claim`; si la zona se borra mientras el menú está abierto,
  `ClaimMenuHandler` sigue editando un objeto huérfano (`MemberSelectMenu` sí lo valida con
  `findClaimById`; hazlo igual en el resto).
- `ClaimMenuHandler` no revalida propiedad en cada clic, solo al abrir.
- `handleAdminTransfer` borra todos los miembros al transferir — verifica que sea intencional.
- `denyForVisitor(Claim, Player, boolean)` ignora su tercer parámetro (resto del refactor a medias).
- `flagLore` promete "Intrusos no pueden X" por flag; hoy la descripción no coincide con el comportamiento.

---

## 6. Prioridad sugerida

1. UUID offline + `getAsync` en `PlayerLookup` (§1.1) — es lo que rompe añadir miembros.
2. Respetar los flags en `BlockProtectionEvents` / `EntityProtectionEvents` (§2).
3. Banear en zonas agrupadas + repeler sin matar (§1.2).
4. `require = 0` en los dos mixins de bloques (§3.4).
5. Índice espacial + guardado diferido y atómico (§4).
6. Canal de red opcional (§3.2) si quieres clientes/proxies mixtos.

---

## 7. Qué le agregaría al mod

**Recomendación principal: roles por miembro en vez de "miembro = todo".**
Hoy `canModify()` es binario: o eres dueño/miembro con permiso total, o eres intruso sin nada. En un server
con plugins eso obliga a dar acceso total a un amigo para que solo pueda abrir una puerta. Propuesta:

| Rol | Permisos |
|---|---|
| `VISITANTE` | entrar, puertas y botones |
| `INQUILINO` | + cofres y cultivos |
| `CONSTRUCTOR` | + romper y colocar |
| `CO-DUEÑO` | + gestionar miembros y flags (no borrar la zona) |

Encaja casi sin fricción: `Claim.members` pasa de `List<UUID>` a `Map<UUID, Role>` (con migración desde el
JSON viejo asignando `CONSTRUCTOR`), `canModify()` pasa a `can(player, Action)`, y en el menú de miembros el
clic derecho sobre la cabeza rota el rol. Es la petición número uno en cualquier server de protecciones y ya
tienes el 90% de la infraestructura (GUI paginado, prompts, persistencia).

Otras dos que rinden mucho por lo que cuestan:

- **Historial de la zona** (`/claim log`): últimos 100 eventos — quién entró, quién rompió qué, quién fue
  añadido/baneado, con timestamp, en un `ArrayDeque` por zona persistido en el mismo JSON. Contesta la
  pregunta que más te van a hacer los jugadores: *"¿quién me robó?"*.
- **Expiración por inactividad**: si el dueño no entra en N días, la zona se marca y luego se libera
  devolviendo la piedra. Evita que el mapa se llene de zonas muertas; configurable en
  `claimblocks_config.json`, que ya existe.

Y si el server es híbrido de verdad, el **puente Bukkit opcional** de §3.3 vale más que cualquier feature
nueva, porque hoy cualquier plugin puede saltarse todas las protecciones.
