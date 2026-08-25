# Fantastic Icons 1.0.4 — Forge 1.20.1

90 iconos verificados **al final del nombre** del jugador: `Adim Pewez ✔`

Se ven en tres sitios:
- **Nombre flotante** sobre la cabeza
- **Chat** (`<Adim Pewez ✔> hola`)
- **Lista de tab**

## Comandos (solo OP, nivel 2) — todo en español

| Comando | Qué hace |
|---|---|
| `/fsicons poner <icono> <jugador>` | pone el icono (los dos argumentos se autocompletan con TAB) |
| `/fsicons cambiar <jugador> <icono>` | cambia el icono |
| `/fsicons quitar <jugador>` | quita el icono |
| `/fsicons lista [pagina]` | catálogo de los 90 iconos; clic en una línea escribe el comando |
| `/fsicons ver <jugador>` | icono actual de un jugador |
| `/fsicons jugadores` | todos los iconos asignados |

Al escribir el icono, el autocompletado muestra el nombre en español como tooltip.
Funciona también con jugadores **desconectados** (cache de perfiles, y UUID offline
en servidores offline-mode).

## Los 90 iconos

`id = <forma>_<color>`

**Formas:** `visto`, `verificado_placa`, `verificado_sello`, `verificado_redondo`,
`verificado_engranaje`, `verificado_estrella`, `verificado_escudo`, `moderador_placa`,
`moderador_sello`, `moderador_redondo`, `moderador_engranaje`, `moderador_estrella`,
`moderador_escudo`, `escudo`, `estrella`

**Colores:** `azul`, `verde`, `rojo`, `dorado`, `plata`, `arcoiris`

Ejemplos: `visto_azul`, `verificado_placa_dorado`, `moderador_estrella_arcoiris`, `escudo_plata`

Catálogo visual: `FantasticIcons-catalogo.png`

## Alineación y tamaño del icono

`ascent 8 / height 8`, y hay motivo para cada número:

- El glifo se dibuja entre `baseline - ascent` y `baseline - ascent + height`, o sea
  filas `-8..-1`. Las mayúsculas de Minecraft ocupan `-7..-1`: el icono queda
  **rasante por abajo** con el texto y solo 1 px más alto por arriba.
- `height 8` es la única medida nítida: el arte es de 16 px y 16 → 8 es una reducción
  exacta 2:1. Con `height 9` o `7` la escala no es entera, el juego duplica o se come
  filas sueltas y el icono se ve grumoso y más grande de lo que toca.

Además las texturas se recortan en horizontal al generarlas: 54 de las 90 traían
1 px transparente a los lados, que en el juego se veía como un hueco entre el
nombre y el icono. El alto se deja intacto, que es lo que mantiene el centrado.

## Instalación

`mods/` en el **servidor** y en el **cliente**. Los iconos son glifos de una fuente
bitmap propia (`fantasticicons:iconos`), así que el cliente necesita el mod para
verlos; un cliente sin el mod puede entrar igual, solo verá el nombre sin icono.

Los datos se guardan en `<mundo>/data/fantasticicons.json`.

## Compilar

Sin Gradle: se compila contra un Minecraft 1.20.1 con nombres Mojang y luego se
reobfusca a nombres SRG de producción (`build.sh` documenta los pasos exactos).
`generar_iconos.py` regenera las texturas, la fuente y `IconRegistry.java`.

## Créditos

Iconos: **Verified Mod Icons — Boxpix Studios**.


## Compatibilidad con EssentialsChat en Mohist

EssentialsChat reconstruye el componente del chat mediante Bukkit. En Mohist,
`{DISPLAYNAME}` no incluye necesariamente los cambios que un mod Forge realiza en
`PlayerEvent.NameFormat`; por eso el icono podía verse en los mensajes de Fantastic
Icons, el tab y el nombre flotante, pero no en la línea final de EssentialsChat.

Desde la 1.0.3, el cliente procesa `ClientChatReceivedEvent` con prioridad `LOWEST`,
cuando Essentials ya aplicó su formato final. Usa el UUID del emisor para encontrar
su icono y lo inserta justo después del nombre. Si un build de Mohist pierde el UUID
o cambia entre chat normal y mensaje de sistema después de `/essentials reload`,
usa como respaldo los nombres de los jugadores online con icono, sin depender del
tipo de paquete.

La reescritura se hace sobre `Component.toFlatList()`: preserva el estilo efectivo
de cada fragmento, incluidos colores, fuente, hover y clic del prefijo de
LuckPerms/Essentials. También detecta el glifo ya presente para no duplicarlo.


## Compatibilidad con el plugin TAB (NEZNAMY)

Cuando `tablist-name-formatting.enabled` está activo, TAB reemplaza desde Bukkit
el `tabListDisplayName` que Forge había calculado. Fantastic Icons 1.0.4 se
inyecta al retorno de `PlayerTabOverlay.getNameForDisplay(PlayerInfo)`, el último
punto común antes de que Minecraft mida y dibuje cada entrada. En ese momento ya
están aplicados el prefijo, color y formato final de TAB; el mod agrega únicamente
el glifo del UUID a la derecha.

La decoración ocurre antes del cálculo de anchura de la columna, por lo que el
icono no se superpone al ping ni al objetivo. Se comprueba el carácter existente
para impedir iconos duplicados. No hace falta desactivar `tablist-name-formatting`
ni `scoreboard-teams`, ni modificar `groups.yml`.
