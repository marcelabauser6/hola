# Fantastic Chameleon 1.2.1 — Modo Prop Hunt

> **1.2.1 arregla que la transformación no funcionaba.** El mod ya cancelaba el clic derecho sobre
> bloques mientras hay una ronda en marcha (solo dejaba puertas, trampillas, portones, botones y
> palancas), justo cuando se juega al Prop Hunt. El listener nuevo escuchaba *después* de esa
> cancelación y se salía sin hacer nada. Ahora corre a `EventPriority.HIGHEST`, antes de ese bloqueo.
> Además el staff puede transformarse fuera de una sala para probar sin montar partida, y
> `/fschameleon` config muestra el modo en chat.

Añade un segundo modo de juego al mod, separado del clásico Meccha Chameleon.

- **Meccha Chameleon** (modo original): te pintas el cuerpo para camuflarte. Sin transformación en bloques.
- **Prop Hunt** (nuevo): te convertís en el bloque o la criatura que toques con clic derecho. Sin GUI de pintura.

El modo se elige por sala en la pestaña **Reglas** del editor (`/fschameleon`), y solo se puede cambiar en el lobby.

## Cómo se juega el modo Prop Hunt

1. El líder de la sala pone el modo en `Prop Hunt` (pestaña Reglas, primer botón).
2. Con el set completo de armadura camaleón puesto, **clic derecho** a cualquier bloque o criatura para convertirte en eso.
3. Te podés mover normalmente estando disfrazado.
4. Al pulsar la tecla de fijado te quedás clavado en el sitio y el prop **se centra solo** en la celda del bloque, alineado con los ejes del mundo.
5. Espacio o agacharse te libera para volver a moverte.

Los seekers no pueden transformarse.

Si sos staff (permiso nivel 2), podés transformarte **fuera de cualquier sala** para probar sin montar
una partida. Dentro de una sala manda el modo de la sala.

### Si no te transformás

- Te hace falta el **set completo de armadura camaleón**. Si no lo llevás, sale un aviso en rojo.
- La sala tiene que estar en modo **Prop Hunt**. Comprobalo con `/fschameleon` (la última línea de la
  config muestra el modo) o mirando el primer botón de la pestaña Reglas.
- Los **seekers** no se disfrazan.
- La **varita de arena**, la **escopeta** y el **pincel** conservan su función: si los llevás en la
  mano, el clic derecho no transforma.

## Qué se puede imitar

**Bloques** (la forma y la orientación salen del blockstate real): bloque completo, losa (arriba/abajo),
escaleras (recta/interior/exterior, normal e invertida, 4 orientaciones), valla, muro, panel/barrotes
(post/extremo/recta/esquina/te/cruz), trampilla (suelo/techo/pared), alfombra, pastel, maceta
(vacía/planta/cactus), farol (de pie/colgante) y yunque. Cualquier otro bloque cae a bloque completo.

**Criaturas**: vaca, cerdo, oveja, gallina, lobo, creeper y enderman. Otras entidades avisan que
todavía no tienen forma.

La textura es la real: se lee el sprite del bloque o el PNG del mob.

## Alineación al grid

Era el punto que faltaba. El modo clásico usa `placeAgainstCover`, que **pega** al jugador contra la
pared más cercana para que su silueta pintada encaje. Para un prop eso está mal: un bloque tiene que
quedar en el centro exacto de su celda.

`PropGridSnap` centra en `floor(x)+0.5` / `floor(z)+0.5`, apoya en `floor(y)` y deja el yaw en
múltiplos de 90°. Solo teleporta si el hueco destino está libre, así que nunca te mete dentro de un
bloque. Además, en los props de bloque el cuerpo se deja a yaw 0 porque la orientación ya va dentro
del `variant` (el modelo se construye rotado), y así las caras quedan paralelas al mundo.

## Archivos

### Nuevos — `com/fantasticchameleon/prophunt/`

| Clase | Qué hace |
|---|---|
| `PropHunt` | Constantes de modo y consulta del modo de la sala |
| `BlockPropMapper` | `BlockState`/`Entity` → forma + orientación de prop |
| `PropGridSnap` | Centrado y alineación al grid |
| `PropHuntCapture` | Lógica de captura y validaciones |
| `PropHuntEvents` | Enganche al clic derecho (servidor) |
| `PropHuntClient` | Captura la textura real y la sube |
| `PropHuntRules` | Limpieza de estado al cambiar de modo |

### Modificados

| Archivo | Cambio |
|---|---|
| `game/Room.java` | Campo `gameMode` en `Config` |
| `game/Rooms.java` | Config `gamemode` (solo en lobby) y envío al cliente |
| `network/RoomsPayload.java` | `CFG_LEN` 21 → 22 |
| `network/FantasticNetwork.java` | Snap al grid al fijarse; props bloqueados en Meccha |
| `client/FantasticEditorScreen.java` | Botón selector de modo |
| `client/LockControls.java` | En Prop Hunt la tecla solo fija, no abre el pintor |
| `assets/.../lang/*.json` | 10 claves nuevas en los 8 idiomas |
| `META-INF/mods.toml` | Versión 1.2.0 y descripción |

No se tocó ningún mixin, ni el access transformer, ni el refmap, ni el entrypoint del mod. Los
listeners nuevos se registran con `@Mod.EventBusSubscriber` y corren a `EventPriority.LOW` para no
pisar las herramientas de staff que ya escuchaban el clic derecho.

## Verificación hecha

- **24.135 blockstates** (todos los del juego) pasados por el mapeador dentro del runtime real de
  Forge: 0 fallos, ningún prop ni variant fuera de rango, ninguna excepción.
- 17 comprobaciones puntuales de mapeo correctas, incluida la orientación
  (`oak_stairs facing=east` → `facing=1`).
- Servidor Forge 1.20.1-47.4.0 arrancado con el mod: carga limpia, 0 errores, apagado correcto.
- El set de clases recompiladas coincide **exactamente** con el del JAR original: no se perdió ni se
  añadió ninguna clase por accidente.

Lo que **no** se pudo probar aquí: el render en pantalla y la sensación en partida (hace falta un
cliente gráfico), y Mohist en concreto (se probó en Forge puro). El código solo usa APIs estándar de
Forge y los mismos patrones que ya usaba el mod.

## Aviso de compatibilidad

El paquete de salas creció de 21 a 22 enteros de config, así que **cliente y servidor tienen que usar
la misma versión**. Hay que actualizar el JAR en los dos lados a la vez.

## Cómo recompilar

El repo no tiene el proyecto fuente, así que este trabajo se hizo sobre el JAR compilado. El JAR está
reobfuscado a SRG, lo que permite un truco: si compilás contra los JAR **de producción** de Forge
(que también son SRG), el código decompilado compila directo sin remapear nada.

```bash
# 1. Decompilar el jar
java -jar vineflower.jar --silent "Fantastic Chameleon-1.20.1-1.2.0.jar" decomp/

# 2. Instalar Forge 1.20.1 (server y client) para obtener los jars SRG de producción
java -jar forge-1.20.1-47.4.0-installer.jar --installServer
java -jar forge-1.20.1-47.4.0-installer.jar --installClient <dir>

# 3. Generar el Minecraft de cliente en namespace de producción
#    (clases con nombre oficial + miembros SRG) con compose_map.py + ForgeAutoRenamingTool

# 4. Compilar solo los archivos tocados, con el jar del mod en el classpath
#    para que aporte todas las clases que no se recompilan
javac -nowarn -proc:none -cp "$CP" -d build <archivos.java>

# 5. Inyectar las clases en una copia del jar
zip jar-nuevo.jar $(find build -name '*.class')
```

`toolchain/` trae los scripts usados: `cp.sh` (classpath), `compose_map.py` (mappings del cliente),
`srgname.py` (buscador de nombres SRG) y `add_lang.py` (claves de idioma).

Ojo con `srgname.py`: compara **descriptores completos**, no solo nombres. Los métodos sobrecargados
comparten nombre obfuscado y compararlos solo por nombre da resultados incorrectos.

Hay 15 archivos del árbol decompilado que no compilan y **no hay que tocar**: los 7 mixins (castean
`this` al target, solo válido con el AP de Mixin), `BodyPaint`/`BodyPaintLayer` (dependen del access
transformer), las 4 recetas y `FantasticChameleonForge` (el decompilador perdió los tipos genéricos de
unos 28 lambdas de eventos). Ninguno hace falta para esta función.
