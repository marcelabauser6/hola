# Fantastic Chameleon 1.2.7 — Modo Prop Hunt

> **1.2.7 — limpieza completa, modelos vanilla y Meccha adherido.**
> - Prop Hunt usa modelos vanilla reales para vaca, cerdo, oveja, gallina, lobo, creeper,
>   enderman y panda; las texturas salen del resource pack activo. Panda se añadió al final del
>   catálogo (índice 19), sin desplazar los IDs anteriores.
> - F solo funciona para hiders durante una ronda activa. Al terminar, salir, espectar o quitar el
>   disfraz se limpian prop, pose, ancla, freeze e inmovilización; el servidor rechaza bypasses de
>   `PosePayload` desde clientes modificados.
> - Los mensajes se separan por rol, los bloques fijados usan celda/culling discreto sin líneas
>   coplanares y los bloques móviles emiten todas sus caras.
> - Crear, entrar, salir, expulsar o borrar una sala actualiza inmediatamente el catálogo global; el
>   botón ahora dice **Guardar** y ejecuta la acción server-side correspondiente.
> - En Meccha, F adopta la pose plana 30, recoge brazos/piernas y se adhiere de forma segura al suelo
>   y a la pared horizontal más cercana. La pipeta con Space lee el texel exacto señalado.
>
> **1.2.6 — estado exacto, render vanilla, criaturas animadas y Jade indistinguible.**
> - El servidor sincroniza el **`BlockState` completo** (`PROP_STATE`), no solo el ID del bloque. Se
>   conservan eje, orientación, mitad, forma, conexiones, edad, encendido y demás propiedades.
> - Los props de bloque ya no reconstruyen un atlas aproximado: se dibujan con el mismo
>   **`BlockRenderDispatcher`** que usa el mundo. Esto conserva quads, modelos multipart, capas,
>   transparencia, tintes de bioma, iluminación ambiental y resource packs sin doble sombreado.
> - Las criaturas ahora tienen animación visible: patas al caminar, cabeza siguiendo la mirada,
>   alas de gallina, cola de lobo y gesto sincronizado con **V** (pastar, agitarse o sisear).
> - Jade reemplaza el accessor de jugador por un **`BlockAccessor` del estado imitado**. El bloque
>   falso muestra nombre, icono y mod como el verdadero; nunca muestra nombre ni corazones de jugador.
> - Las rutas clásicas de Meccha y sus previews mantienen su renderer anterior y limpian cualquier
>   estado capturado, para que los modos sigan separados y no reutilicen un disfraz obsoleto.
>
> **1.2.5 — fidelidad de textura y Jade ya no te delata.**
> - **Jade ya no muestra nada** al apuntar a un prop. Antes cantaba el nombre del jugador y sus
>   corazones, que arruinaba la partida entera. Se resuelve con un plugin propio que usa el callback de
>   raytrace de Jade para devolver nada sobre un jugador disfrazado.
> - **Textura cara por cara.** Antes se repetía una sola imagen en las seis caras, así que un tronco
>   descortezado salía con la veta lateral también arriba. Ahora cada cara lleva la suya, usando
>   `PropModels.faceRects`, que da el reparto exacto del atlas.
> - **Tono corregido.** Minecraft dibuja los bloques con las caras de arriba más claras y las laterales
>   más oscuras; un prop es una entidad y no recibe ese sombreado, por eso se veía más claro que el
>   bloque de al lado. Ahora ese sombreado se hornea en la textura (1.0 arriba, 0.8 y 0.6 los lados,
>   0.5 abajo, los mismos valores que usa el juego).
> - **Texturas de mob correctas.** Los modelos de criatura del mod usan los mismos desplazamientos de
>   textura que los de Minecraft, así que ahora se copia el png del mob tal cual (y la lana de la oveja
>   en su mitad inferior) en vez de estirar un recorte, que era lo que daba esa textura rara.
> - **Los mobs suenan solos** cada 6-18 segundos, como los de verdad, además del gesto manual con V.
>
> **1.2.4 — movilidad completa, botón de colocar y gestos de criatura.**
> - **Capturar ya no te fija.** Al tocar un bloque te transformás pero conservás movilidad completa:
>   caminar, saltar y **escalar** igual que en Meccha.
> - **Colocarse es un acto aparte**: con **F** quedás centrado en la celda, alineado y fijado. Espacio o
>   agacharse te suelta. Los props de bloque se colocan siempre a yaw 0, porque su orientación real ya
>   va en el `variant` y girar el cuerpo además los dejaría torcidos.
> - **Gestos de criatura** con **V**: la oveja pasta, la vaca muge, el creeper sisea. Cada mob tiene su
>   sonido real y sus partículas, para poder imitar su comportamiento a voluntad sin perder el control.
>
> **1.2.3 — texturas reales, colocación centrada y un modo, una verdad.**
> - Los props ahora llevan la **textura real del bloque**. El servidor guarda de qué bloque se trata
>   (`PROP_SOURCE`) y cada cliente genera la imagen, porque los píxeles solo existen en el cliente.
>   Adiós a las manchas de color.
> - Los **bots** eligen un bloque real de una lista de escondites creíbles y se convierten en él, así
>   que salen como bloques de verdad y no como vacas verdes.
> - **Se arregló la raíz de varios síntomas a la vez:** había dos verdades sobre "estoy en Prop Hunt".
>   La captura permitía al staff transformarse fuera de una sala, pero el centrado y el gateo de las
>   GUIs exigían sala en modo Prop Hunt. Fuera de sala te transformabas **sin centrar y con los menús
>   de Meccha encima**. Ahora todo se decide en un solo sitio y falla con aviso, no en silencio.
> - Al tocar un bloque quedás **colocado**: centrado en la celda, alineado y fijado, como un bloque
>   recién puesto.
> - El HUD de pistas de Meccha (`Space mover / F pintar / R pose / Esc menú`) ya no aparece en Prop
>   Hunt; ahora muestra las teclas que sí existen en este modo.
>
> **1.2.2 — se fueron los restos de Meccha en Prop Hunt.**
> - La rueda de poses (**R**) ya no se abre en Prop Hunt: ahí las poses no tienen sentido, así que la
>   tecla ahora **quita el disfraz**.
> - El pintor (**F**) ya no aparece. Antes el gateo leía el paquete de salas, que puede llegar tarde o
>   estar desactualizado; ahora el modo viaja **por jugador** en un atributo sincronizado
>   (`GAME_MODE`), así que el cliente siempre sabe en qué modo está.
> - Los **dummies** se disfrazan de prop al azar en vez de quedarse posando como muñecos.
>
> **1.2.1 arregló que la transformación no funcionaba.** El mod ya cancelaba el clic derecho sobre
> bloques mientras hay una ronda en marcha (solo dejaba puertas, trampillas, portones, botones y
> palancas), justo cuando se juega al Prop Hunt. El listener nuevo escuchaba *después* de esa
> cancelación y se salía sin hacer nada. Ahora corre a `EventPriority.HIGHEST`, antes de ese bloqueo.

Añade un segundo modo de juego al mod, separado del clásico Meccha Chameleon.

- **Meccha Chameleon** (modo original): te pintas el cuerpo para camuflarte. Sin transformación en bloques.
- **Prop Hunt** (nuevo): te convertís en el bloque o la criatura que toques con clic derecho. Sin GUI de pintura.

El modo se elige por sala en la pestaña **Reglas** del editor (`/fschameleon`), y solo se puede cambiar en el lobby.

## Cómo se juega el modo Prop Hunt

1. El líder de la sala pone el modo en `Prop Hunt` (pestaña Reglas, primer botón).
2. Con el set completo de armadura camaleón puesto, **clic derecho** a cualquier bloque o criatura para convertirte en eso.
3. Te podés mover normalmente estando disfrazado.
4. Al pulsar **F** te quedás clavado en el sitio y el prop **se centra solo** en la celda del bloque, alineado con los ejes del mundo.
5. Espacio o agacharse te libera para volver a moverte.
6. **R** quita el disfraz.

### Teclas por modo

| Tecla | Meccha Chameleon | Prop Hunt |
|---|---|---|
| **Clic derecho** | — | Convertirte en el bloque o criatura |
| **F** | Fijarse + abrir el pintor | **Colocarte**: centrado, alineado y fijo |
| **Espacio / agacharse** | Soltarse | Soltarse y recuperar movilidad |
| **V** | — | **Gesto de criatura** (pastar, mugir, sisear) |
| **R** | Rueda de poses y formas | Quitar el disfraz |

### Gestos por criatura (tecla V)

| Prop | Gesto |
|---|---|
| Oveja, vaca, cerdo | Pastar: sonido del animal + partículas de hierba a sus pies |
| Gallina | Cacareo + partículas |
| Lobo | Ladrido + partículas |
| Creeper | Siseo (sin explotar nada) |
| Enderman | Sonido y partículas de portal |

Los props de bloque no tienen gesto a propósito: un bloque que hace ruido te delata.

### Importante sobre la escalada

Escalar y estar colocado son incompatibles en el mod (`Climb.heldInPlace` lo impide, y funciona igual en
Meccha). El flujo es: te transformás → te movés y escalás con libertad → cuando encontrás el sitio,
**F** te coloca centrado.

Los bots (`dummies`) también respetan el modo: posan y se pintan en Meccha, y se disfrazan de props de
colores al azar en Prop Hunt.

Los seekers no pueden transformarse.

Las transformaciones solo se habilitan dentro de una sala activa en modo Prop Hunt; así el cliente,
el centrado y las reglas de la partida comparten una única fuente de verdad.

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

**Criaturas**: vaca, cerdo, oveja, gallina, lobo, panda, creeper y enderman. Otras entidades avisan que
todavía no tienen forma.

La apariencia del bloque es la real: se renderiza su `BakedModel` y `BlockState` exactos con el
renderer vanilla. Las criaturas usan el PNG del resource pack y su modelo animado.

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
| `META-INF/mods.toml` | Versión 1.2.7 y descripción |

No se tocó ningún mixin, ni el access transformer, ni el refmap, ni el entrypoint del mod. Los
listeners nuevos se registran con `@Mod.EventBusSubscriber` y corren a `EventPriority.LOW` para no
pisar las herramientas de staff que ya escuchaban el clic derecho.

## Verificación hecha

- **24.135 blockstates** (todos los del juego) pasados por el mapeador dentro del runtime real de
  Forge: 0 fallos, ningún prop ni variant fuera de rango, ninguna excepción.
- Compilación SRG Java 17 limpia de las clases modificadas; las clases insertadas coinciden por SHA-256
  con las compiladas y el JAR no contiene entradas duplicadas ni clases de self-test.
- Servidor Forge 1.20.1-47.4.0 arrancado y apagado correctamente con 1.2.7, tanto sin Jade como con
  Jade 11.13.3; Jade descubrió y cargó `FantasticJadePlugin` sin errores.

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
