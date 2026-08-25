# Fantastic Shopkeepers

Tiendas atendidas por NPC para Minecraft 1.20.1, mod de Forge para **cliente y servidor**, pensado para Mohist.
Precios en **Fantastic Cash** cuando Fantastic Currency está instalado.

El servidor manda: guarda las tiendas con el mundo, cobra el dinero y decide si un trato puede ocurrir. El cliente
aporta el editor con el que un administrador monta la tienda.

## Qué es esto exactamente

**No es Shopkeepers 2.17.2 portado clase por clase.** El plugin original son 1116 clases compiladas contra la API de
Bukkit, sin fuente disponible en el jar. Esto es una reimplementación desde cero en Forge que cubre el núcleo
funcional, más el editor de la familia Fantastic y el cobro en Fantastic Cash.

## Las dos interfaces, y por qué son distintas

**La ventana de compra es la vanilla de comercio con aldeanos.** Se dibuja con la textura del propio juego
(`villager2.png`): el mismo marco, la misma lista de tratos a la izquierda, los mismos dos huecos de pago y el hueco
de resultado a la derecha, el mismo deslizador, y **al tamaño de vanilla**. Un cliente que ya ha comerciado con un
aldeano sabe usarla.

Lo único que cambia es lo que tiene que cambiar: un precio en Fantastic Cash es dinero y no un objeto, así que donde
vanilla pone esmeraldas aquí va el billete de Cash con el importe. Cuando un trato pide dinero *y* un artículo, el
Cash va al primer hueco, el artículo al segundo, y entre los dos se dibuja un **+** para que se lea como un precio
único y no como dos opciones.

No se amplía con ninguna escala propia. Se probó y quedaba mal: el inventario creativo y JEI se dibujan a la escala
de GUI del juego, así que una ventana escalada encima de ellos aparecía de otro tamaño que todo lo demás. Para
verlo todo más grande está la opción de escala de GUI de Minecraft, que agranda todas las ventanas a la vez.

**El editor es de la familia Fantastic.** Panel centrado de 540×320 como máximo, franja de título, pestañas, regla,
una línea de ayuda sobre lo que tengas bajo el cursor, y un pie de acciones. Los botones son los de la familia:
esquinas redondeadas, sombra, degradado teñido con el color de acento, brillo animado que sube desde la base con sus
veinte partículas, y sonido al pasar por encima. Las constantes de color y la tabla de redondeo de esquinas son
literalmente las mismas que usa Fantastic Crates, no una aproximación a ojo.

## Comandos

Uno solo, `/fskeepers`, con cinco subcomandos. El plugin original tenía veinticinco, y la mayoría existían para
suplir la falta de un editor: `setforhire`, `settradeperm`, `setcurrency` y compañía son campos de un formulario
aquí, y un comando que duplica un campo es un segundo sitio donde el mismo ajuste puede estar mal.

| Comando | Qué hace |
|---|---|
| `/fskeepers crear` | Crea la tienda junto al cofre libre más cercano y abre el editor |
| `/fskeepers crear admin` | Crea una tienda de staff con existencias infinitas |
| `/fskeepers editor` | Te da la varita del editor |
| `/fskeepers editar` | Abre el editor de la tienda que tengas delante |
| `/fskeepers borrar` | Borra la tienda que tengas delante |
| `/fskeepers lista` | Lista tus tiendas y dónde están |
| `/fskeepers recargar` | Vuelve a leer la configuración |

**No hay objeto de creación.** Dar un huevo de aldeano que en realidad significa otra cosa, y luego pedir que hagas
clic derecho en un cofre y después en el suelo en el orden correcto, es un flujo que nadie puede adivinar sin que se
lo cuenten. Crear una tienda es un comando y nada más.

### Uso

1. Pon un cofre donde quieras el almacén.
2. `/fskeepers crear` → aparece el tendero y se abre el editor.
3. En *Tratos*: elige una fila a la izquierda, y a la derecha ponle artículo y precio.
4. Guardar.

Los clientes hacen clic sin agacharse y ven la ventana de comercio de siempre.

## Qué está implementado

| Área | Detalle |
|---|---|
| Tipos de tienda | Administrador (existencias infinitas), venta, compra, trueque, libros |
| Cuerpos | Cualquier mob que el servidor conozca (incluidos los de otros mods), cartel, virtual |
| Variantes de mob | Bebé, color de lana, collar, profesión y bioma de aldeano, conejo, panda, zorro, loro, llama, caballo, ajolote, rana, champiñaca, sentado |
| Editor | Panel Fantastic, 3 pestañas, selector de artículos con búsqueda, precios escritos como números |
| Comercio | Cobro en Fantastic Cash, pago con artículos, o ambos. Existencias leídas del cofre en vivo |
| Economía | Impuesto configurable, abono a la cuenta bancaria enlazada, avisos al dueño |
| Protección | Cofres de tienda cerrados a terceros, NPCs invulnerables, carteles no rompibles por otros |
| Permisos | Nodos `shopkeeper.*`, consultados a un plugin de permisos vía Bukkit en Mohist, con reserva a nivel de operador |
| Persistencia | `SavedData` del mundo, así que las tiendas entran en las copias de seguridad y los rollbacks |

## No implementado, y por qué

- **Citizens, WorldGuard, Towny, Vault.** Son plugins de Bukkit; no existen como mods de Forge. Hacerles de puente
  desde un mod en Mohist sería posible por reflexión pero frágil, y una protección que falla en silencio es peor que
  no tenerla.
- **bStats.** Telemetría, no una función.
- **Importador de tiendas de Shopkeepers.** Las que ya existan en el plugin no se migran solas: viven en su formato
  propio. Es un trabajo aparte y hay que hacerlo contra un `save.yml` real.
- **Registro de tratos en CSV.** Hay registro al log del servidor (`logTrades`), no a un fichero aparte.

## Configuración

`config/fantasticshopkeepers.json`, creado al primer arranque. Lo relevante: `maxShopsPerPlayer`,
`maxContainerDistance`, `protectContainers`, `taxPercent`, `allowedShopEntities` (lista vacía = todos los mobs
permitidos). Recarga en caliente con `/fskeepers recargar`.

## Compilar

```bash
./gradlew build          # jar en build/libs/
./gradlew check          # las dos verificaciones de abajo
```

- **`verifyEditorGeometry`** — recorre la geometría del editor a 14 tamaños de ventana, de 320×240 a 3840×2160, y
  falla la compilación si dos controles comparten un pixel, si una columna se sale del panel, si el pie invade el
  cuerpo, o si el panel deja de estar centrado y limitado a 540px. Encontró un solapamiento real de los botones del
  pie en ventanas estrechas, que es exactamente para lo que está.
- **`verifyMoney`** — parseo, formato y aritmética de precios. Rechaza tres decimales en vez de redondearlos, acepta
  la coma del teclado español, y comprueba que multiplicar un precio grande sature en vez de desbordar a negativo.

La geometría vive en `client/layout/EditorGeometry.java`, aritmética pura sin Minecraft, y la pantalla la usa como
única fuente. Sin eso el test verificaría números distintos de los que se dibujan.

## Decisiones que conviene conocer

- **El dinero son céntimos (`long`), nunca `double`.** Un precio en coma flotante es cómo una tienda acaba cobrando
  24,999999 por algo que el administrador escribió como 25.
- **Se cobra antes de entregar, con reversión.** Cada camino de `TradeExecutor` comprueba todo antes de mover nada, y
  deshace lo hecho si un paso posterior falla. El fallo que importa es el que cobra y no entrega.
- **Las variantes de mob son un parche NBT, no una clase por variante.** El original necesitaba 83 clases para 30
  mobs porque asignaba campos tipados a entidades tipadas. El color de una oveja es `Color: 4` y se puede editar sin
  saber qué es una oveja, lo que además hace funcionar a los mobs de otros mods.
- **El cliente no se cree nada.** El editor manda una petición; el servidor revalida el permiso, el mob contra la
  lista blanca, el número de filas, cada precio, y descarta los campos que solo el staff puede tocar.
- **Los campos de texto son `EditBox` de verdad.** Un widget trae caret, selección, portapapeles y clic para colocar
  el cursor; un campo hecho a mano no tiene ninguna de esas cosas y nadie se da cuenta hasta que intenta corregir un
  precio por el medio.
