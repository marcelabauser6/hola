# Fantastic Chameleon 1.2.9 — Prop Hunt y Meccha corregidos

> **1.2.9 — interfaz, rendimiento, desacople y acople de verdad pegado.**
> - Los controles al fijarse son ahora dos filas diminutas de 76×12 pegadas al borde izquierdo, con el
>   mismo lenguaje visual que el resto del HUD. Fuera los botones vanilla enormes.
> - **Todos los textos en español, incluido `en_us`.** El `en_us` de este mod siempre fue el fallback en
>   español, así que traducirlo al inglés era lo que hacía salir *Detach* entre textos españoles. Además
>   se añadieron las claves que el HUD ya usaba y que faltaban en el JAR: por eso aparecía
>   `fantastic.prophunt.hint_clear` en crudo.
> - El indicador de escondido y sus teclas se dibujan **debajo** de la barra de ronda: se acabó el
>   solape con las cabezas y el reloj.
> - Sin carteles al transformarse: el disfraz ya se ve en el propio cuerpo.
> - **Desacoplar libera de verdad.** Compartía cupo de peticiones con fijarse, así que pulsar fijar y
>   soltar seguido descartaba el desacople: el cliente se creía libre y el servidor te devolvía a la
>   misma posición cada tick. Ahora tiene cupo propio, limpia ancla, inmovilización, colisión y
>   velocidad, y reafirma la posición para que el cliente no quede desincronizado.
> - **Lag al empezar la partida resuelto.** El guardia anti-clipping medía 75 puntos del cuerpo por
>   jugador y por tick, y hasta 5.625 al buscar hueco. Un disfraz roza su escondite a propósito, así que
>   ahora se mide con umbral laxo y una vez cada 10 ticks; a quien tapien de verdad se le sigue
>   rescatando, y la salida de emergencia bajo el agua se conserva.
> - **Los mobs capturados vuelven a sonar y a moverse.** La captura genérica deja `PROP` en -1, y los
>   sonidos exigían `PROP >= 0`: por eso enmudecieron. Ahora la voz se deriva del `EntityType` capturado,
>   así que suenan también los mobs de otros mods. El modelo se ticka una vez por tick desde el tick del
>   cliente, de modo que la gallina bate las alas y el lobo mueve la cola; va mudo y con caja vacía para
>   no delatar al jugador con voz doble ni empujones.
> - **El acople Meccha queda literalmente pegado.** Se medía con la hitbox (0,3 de medio ancho) cuando el
>   torso solo tiene 0,15 de fondo, así que quedaba un hueco visible; ahora se mide el cuerpo que se ve,
>   el giro lo decide la cara clicada, encima del bloque se centra en la celda y el roce intencionado
>   está permitido de forma explícita.
>
> **1.2.8 — todos los mobs, conexiones reales y acople preciso.**
> - Prop Hunt puede capturar cualquier `LivingEntity` no jugador mediante `EntityType` + NBT visual
>   saneado. El renderer vanilla conserva modelo, variante, armadura, objetos de mano, ballestas,
>   espadas, arcos, monturas y capas compatibles, también para mobs añadidos por otros mods.
> - Durante COUNTDOWN/HIDING/SEEKING, los mobs dentro de la arena delimitada ignoran a los hiders de
>   Prop Hunt. La protección no se aplica fuera de la arena, a espectadores ni fuera de una ronda.
> - La gallina se apoya con las patas sobre el suelo y no bate/gira las alas sin control. Vallas,
>   muros, paneles/barrotes y escaleras derivan sus conexiones vanilla del entorno 3×3×3 sin modificar
>   ningún bloque real del mundo.
> - **Desacoplar** conserva exactamente el bloque o mob capturado. Un panel lateral pequeño ofrece
>   **Desacoplar** y **Revertir transformación**; Esc solo cierra el panel y Space/agacharse libera el
>   ancla, incluso si agacharse está remapeado a un botón del ratón.
> - Meccha usa la pose normal de pie (pose 0) y se acopla con clic derecho únicamente a la cara exacta
>   señalada. El servidor repite el raycast y valida alcance, cara, punto y colisión antes de anclar.
> - La pipeta regular toma el RGBA final del framebuffer, incluida iluminación, AO, transparencias y
>   shaders/resource packs; los modos de mapa UV y TEXCROP mantienen sus rutas especializadas.
> - El protocolo de red ahora es estrictamente **2**: cliente y servidor deben ejecutar el mismo JAR
>   1.2.8. Las capturas se limitan a 64 KiB y a una cada 5 ticks.
>
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
2. Con el set completo de armadura camaleón puesto, **clic derecho** a cualquier bloque o mob vivo
   para convertirte en él. Los mobs conservan visualmente su equipo y variante.
3. Te podés mover normalmente estando disfrazado.
4. Al pulsar **F** te quedás clavado en el sitio y el prop **se centra solo** en la celda del bloque,
   alineado con los ejes del mundo.
5. Espacio o agacharse ejecutan **Desacoplar**: recuperás movilidad sin perder la transformación.
6. El panel lateral tiene botones separados para **Desacoplar** y **Revertir transformación**; Esc
   únicamente cierra ese panel. **R** también revierte por completo el disfraz.

### Teclas por modo

| Tecla | Meccha Chameleon | Prop Hunt |
|---|---|---|
| **Clic derecho** | Acoplarse a la cara exacta del bloque señalado | Convertirte en el bloque o mob vivo |
| **F** | Fijarse de pie + abrir el pintor | **Colocarte**: centrado, alineado y fijo |
| **Espacio / agacharse** | Soltarse | **Desacoplar** sin perder la transformación |
| **V** | — | **Gesto de criatura** (cuando existe) |
| **R** | Rueda de poses y formas | Revertir la transformación |
| **Esc** | Cerrar la pantalla actual | Cerrar el panel; no desacopla ni revierte |

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

**Criaturas**: cualquier `LivingEntity` excepto jugadores. Se sincronizan su tipo, dimensiones y NBT
visual saneado y se usa el renderer vanilla real, por lo que piglins, zombified piglins, esqueletos y
mobs modded conservan armadura, objetos de mano, capas y variantes compatibles. Los datos de
inventarios, ofertas, ownership, objetivos y capabilities se eliminan; el snapshot queda limitado a
64 KiB. Si el cliente no dispone del tipo/renderer de un mob modded, el render falla cerrado para no
revelar al jugador humano.

La apariencia de bloque también es la real: se renderiza su `BakedModel` y `BlockState` exactos con el
renderer vanilla y las conexiones se recalculan solo para la vista, sin mutar el mundo.

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
| `PropHuntCapture` | Captura genérica de bloques/mobs, saneado NBT, cooldown y validaciones |
| `PropHuntEvents` | Enganche server-side al clic derecho |
| `PropHuntClient` | Texturas/estados conectados y gates de fase cliente |
| `ArenaMobEvents` | Impide target/ataques de mobs contra hiders dentro de la arena |
| `EntityPropSnapshot` | Snapshot atómico de tipo, NBT visual y dimensiones del mob |
| `GenericEntityPropRenderer` | Renderer vanilla con equipo/capas y caché LRU |
| `MecchaClientEvents` / `MecchaAttachPayload` | Clic derecho y acople Meccha autoritativo |
| `DetachPropPayload` / `PropHuntLockedScreen` | Desacople sin reversión y panel lateral |
| `FramebufferColorSampler` | Pipeta del RGBA final, con buffer nativo reutilizable |
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
| `assets/.../lang/*.json` | Controles nuevos en los 8 idiomas |
| `META-INF/mods.toml` | Versión 1.2.8 y protocolo de red 2 en código |

No se tocó ningún mixin, access transformer, refmap ni entrypoint del mod. Los listeners nuevos se
registran con `@Mod.EventBusSubscriber`; la captura corre antes del bloqueo de interacciones existente
y el servidor sigue siendo autoritativo para cada acción.

## Verificación hecha

- **24.135 blockstates** (todos los del juego) pasados por el mapeador dentro del runtime real de
  Forge: 0 fallos, ningún prop ni variant fuera de rango, ninguna excepción.
- Compilación SRG Java 17 limpia de todas las clases modificadas; los 8 JSON de idioma son válidos,
  las clases insertadas coinciden con el build y el JAR no contiene entradas duplicadas ni self-test.
- Servidor Forge 1.20.1-47.4.0 arrancado y apagado correctamente con 1.2.8 y Jade 11.13.3; Jade
  descubrió y cargó `FantasticJadePlugin` sin errores. El self-test comprobó **24.135 blockstates** y
  terminó con `RESULT: ALL PASSED`.

Lo que **no** se pudo probar aquí: el render en pantalla y la sensación en partida (hace falta un
cliente gráfico), y Mohist en concreto (se probó en Forge puro). El código solo usa APIs estándar de
Forge y los mismos patrones que ya usaba el mod.

## Aviso de compatibilidad

El protocolo de red es estrictamente **2** y los payloads/attachments cambiaron. **Cliente y servidor
tienen que usar exactamente Fantastic Chameleon 1.2.8**; hay que actualizar el JAR en ambos lados al
mismo tiempo.

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
