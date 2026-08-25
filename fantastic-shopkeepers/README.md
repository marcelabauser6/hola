# Fantastic Shopkeepers

Tiendas atendidas por NPC para Minecraft 1.20.1, mod de Forge para **cliente y servidor**, pensado para Mohist.
Precios en **Fantastic Cash** cuando Fantastic Currency está instalado.

El servidor manda: guarda las tiendas con el mundo, cobra el dinero y decide si un trato puede ocurrir. El cliente
aporta el editor a pantalla completa con el que un administrador monta la tienda.

## Qué es esto exactamente

**No es Shopkeepers 2.17.2 portado clase por clase.** El plugin original son 1116 clases compiladas contra la API de
Bukkit, sin fuente disponible en el jar. Esto es una reimplementación desde cero en Forge que cubre el núcleo
funcional, más el editor rediseñado y el cobro en Fantastic Cash que se pidieron.

### Implementado

| Área | Detalle |
|---|---|
| Tipos de tienda | Administrador (existencias infinitas), venta, compra, trueque, libros |
| Cuerpos | Cualquier mob que el servidor conozca (incluidos los de otros mods), cartel, virtual (solo por comando) |
| Variantes de mob | Bebé, color de lana, collar, profesión y bioma de aldeano, conejo, panda, zorro, loro, llama, caballo, ajolote, rana, champiñaca, sentado |
| Editor | Pantalla completa, 3 pestañas, selector de artículos con búsqueda, precios escritos como números, borrado con confirmación |
| Comercio | Cobro en Fantastic Cash, pago con artículos, o ambos a la vez. Existencias leídas del cofre en vivo |
| Economía | Impuesto configurable, abono a la cuenta bancaria enlazada, avisos al dueño |
| Protección | Cofres de tienda cerrados a terceros, NPCs invulnerables, carteles no rompibles por otros |
| Comandos | `/Fskeepers` (y `/fskeepers`): crear, editar, info, borrar, lista, dar, traspasar, arreglar, recargar |
| Permisos | Nodos `shopkeeper.*`, consultados a un plugin de permisos vía Bukkit en Mohist, con reserva a nivel de operador |
| Persistencia | `SavedData` del mundo, así que las tiendas entran en las copias de seguridad y los rollbacks |

### No implementado, y por qué

- **Citizens, WorldGuard, Towny, Vault.** Son plugins de Bukkit; no existen como mods de Forge. Hacerles de puente
  desde un mod en Mohist sería posible por reflexión pero frágil, y una protección que falla en silencio es peor que
  no tenerla.
- **bStats.** Telemetría, no una función.
- **Importador de tiendas de Shopkeepers.** Las tiendas que ya existan en el plugin no se migran solas: viven en el
  formato propio del plugin. Si hace falta, es un trabajo aparte y hay que hacerlo contra un `save.yml` real.
- **Registro de tratos en CSV.** Hay registro al log del servidor (`logTrades`), no a un fichero aparte.

## Uso

1. Consigue el objeto de creación: `/Fskeepers dar` (por defecto un huevo de aldeano).
2. Clic derecho en un cofre → queda elegido como almacén.
3. Clic derecho en el suelo → aparece el tendero.
4. **Agáchate y haz clic derecho en el tendero** → se abre el editor.
5. En *Tratos*: clic en el hueco del artículo para elegirlo, escribe el precio, ajusta la cantidad. Guarda.

Los clientes normales hacen clic sin agacharse y ven la ventana de compra.

## Configuración

`config/fantasticshopkeepers.json`, creado al primer arranque. Lo relevante: `shopCreationItem`,
`maxShopsPerPlayer`, `maxContainerDistance`, `protectContainers`, `taxPercent`, `allowedShopEntities`
(lista vacía = todos los mobs permitidos).

Recarga en caliente con `/Fskeepers recargar`.

## Compilar

```bash
./gradlew build          # jar en build/libs/
./gradlew check          # las dos verificaciones de abajo
```

Dos comprobaciones corren en una JVM normal como parte de `check`:

- **`verifyEditorLayout`** — recorre el editor a 14 tamaños de ventana, desde 320x240 hasta 3840x2160, y falla la
  compilación si dos controles comparten un solo pixel, si una fila se sale de su lista, o si el campo de precio o el
  botón de guardar desaparecen al estrechar. El requisito de "sin solapamientos de letras, pestañas e iconos" es una
  aserción, no una impresión visual.
- **`verifyMoney`** — parseo, formato y aritmética de precios. Rechaza tres decimales en vez de redondearlos, acepta
  la coma del teclado español, y comprueba que multiplicar un precio grande satura en vez de desbordar a negativo.

## Verificación de arranque

Levantado en un servidor dedicado de Forge 1.20.1 real:

```
Config creada en run/config/fantasticshopkeepers.json
Fantastic Currency no esta instalado: los precios en Fantastic Cash quedan desactivados
  y las tiendas usaran items como moneda.
Fantastic Shopkeepers listo. Abre el editor con /Fskeepers editar.
Done (5.752s)! For help, type "help"
```

Sin Fantastic Currency el mod carga igual y degrada a pagos con artículos, en vez de fallar al arrancar. Eso es lo
que da el puente por reflexión de `money/Cash.java`.

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
- **La geometría está separada del dibujado.** `client/layout/` es aritmética pura sin Minecraft, que es lo que
  permite que un solapamiento sea una aserción fallida y no una captura de pantalla.
