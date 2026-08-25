# Fantastic Icons 1.0.0 — Forge 1.20.1

90 iconos verificados **al final del nombre** del jugador: `Adim Pewez ✔`

Se ven en tres sitios:
- **Nombre flotante** sobre la cabeza
- **Chat** (`<Adim Pewez ✔> hola`)
- **Lista de tab**

## Comandos (solo OP, nivel 2)

| Comando | Qué hace |
|---|---|
| `/fsicons set <icono> <jugador>` | pone el icono (los dos argumentos se autocompletan con TAB) |
| `/fsicons change <jugador> <icono>` | cambia el icono |
| `/fsicons remove <jugador>` | quita el icono (alias: `/fsicons quitar`) |
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

## Instalación

`mods/` en el **servidor** y en el **cliente**. Los iconos son glifos de una fuente
bitmap propia (`fantasticicons:iconos`), así que el cliente necesita el mod para
verlos; un cliente sin el mod puede entrar igual, solo verá el nombre sin icono.

Los datos se guardan en `<mundo>/data/fantasticicons.json`.

## Compilar

Sin Gradle: se compila contra un Minecraft 1.20.1 con nombres Mojang y luego se
reobfusca a nombres SRG de producción (`build.sh` documenta los pasos exactos).

## Créditos

Iconos: **Verified Mod Icons — Boxpix Studios**.
