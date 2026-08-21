# Fantastic Claims 7.9.0 — configuración completa y `/fsclaimadmin reload`

Jar: **`Fantastic Claims-7.9.0.jar`**. Incluye todo lo anterior. Se retira el 7.8.0.

---

## La config

`<mundo>/data/claimblocks_config.json`. Se genera sola al arrancar y viene **documentada dentro**:
cada sección lleva un `_doc` explicando qué hace cada opción, porque JSON no admite comentarios.

**23 opciones + los 34 flags por defecto**, en siete secciones:

| Sección | Qué controlas |
|---|---|
| `limites` | `maxZonasPorJugador`, `maxMiembrosPorZona` (0 = sin límite) |
| `protecciones` | interruptores de las protecciones de tolvas, fluidos, decoración y decoración frente a explosiones |
| `baneados` | `expulsarPorTeletransporte`, `danoAlEntrar` (0 = sin daño), `segundosEntreAvisos` |
| `avisos` | antiespam del aviso de intruso, segundos para responder en el chat, longitud máxima de los mensajes |
| `rendimiento` | intervalos de partículas, bordes, barrido de fuego (y su radio), efectos pasivos, y distancia para ver partículas |
| `barreraDeHostiles` | segundos de fuego y daño del flag `BURN_HOSTILES` |
| `zonasNuevas` | partícula, densidad y **los 34 flags** con los que nace una zona nueva |

Lo de `zonasNuevas.flags` es lo más útil del lote: decides con qué protecciones nace cada zona sin
que el jugador tenga que tocar el menú. Los nombres son los mismos botones del menú (`BUILDING`,
`CHEST_ACCESS`, `DOORS_ACCESS`…) y se generan automáticamente del enum, así que si el mod añade un
flag en el futuro aparece solo en el fichero.

### Comportamiento

- Si **falta** una clave, se rellena con su valor por defecto y el fichero se reescribe completo.
  Así, al actualizar el mod, las opciones nuevas aparecen solas sin perder lo que ya tenías.
- Lo que **sí** está escrito se respeta: reescribir el fichero no pisa tus valores.
- La config antigua (`maxClaimsPerPlayer` en la raíz, en inglés) se sigue leyendo y se migra a
  `limites.maxZonasPorJugador`.
- Se escribe a `.tmp` y se mueve encima, para no corromperla si el servidor se cae a mitad.
- `zonasNuevas` no toca las zonas ya creadas, solo las que se creen después.

## El comando

```
/fsclaimadmin reload
```

Requiere OP (nivel 2), como el resto de `/fsclaimadmin`. Responde con un resumen del estado para
que veas de un golpe si se aplicó lo que esperabas:

```
✔ Configuracion de Fantastic Claims recargada.
  Zonas por jugador: 3
  Miembros por zona: 8
  Tolvas: OFF | Fluidos: protegido | Decoracion: protegido
```

Funciona en caliente porque **todo el código lee la config en el momento de usarla**, no copia los
valores al arrancar. Por eso el reload tiene efecto inmediato sin reiniciar.

También aparece en la ayuda de `/fsclaim` para operadores.

---

## Verificaciones

**1. Ninguna opción es decorativa.** `tools/audit_config.py` comprueba que cada campo de
`ClaimConfig` se lee en algún sitio fuera de la propia clase:

```
Claves JSON declaradas: 23  (+ los 34 flags de zonasNuevas, generados del enum)
Todas las opciones de la config tienen efecto real.
```

Esto lo hice a propósito: una config con opciones que no hacen nada es peor que no tener config.

**2. El reload funciona de verdad.** No me valía con que el comando respondiera, así que edité el
fichero con el servidor apagado, arranqué y ejecuté el reload:

| Prueba | Resultado |
|---|---|
| `maxZonasPorJugador: 0 → 3` | el comando reporta `3` |
| `maxMiembrosPorZona: 0 → 8` | reporta `8` |
| `tolvasNoSacanItemsDeLaZona: true → false` | reporta `Tolvas: OFF` |
| Borré `segundosParaResponderEnChat` a propósito | se rellenó solo con `90` |
| Valores que sí edité | intactos, no los pisó |
| `zonasNuevas.flags` | los 34 presentes |

**3. Arranque limpio**, 0 excepciones, los 6 mixins siguen aplicándose.

## Qué se dejó de hardcodear

Estos valores estaban fijos en el código y ahora salen de la config: intervalo de partículas (4
ticks), de bordes (20), distancia de render (24), barrido de fuego (40 ticks / radio 6), efectos
pasivos (40 ticks / 60 de duración), caducidad del prompt de chat (90 s), antiespam del aviso de
intruso (600 ticks), fuego y daño de la barrera de hostiles (3 s / 3.0), límite de los mensajes de
bienvenida (60 caracteres) y el comportamiento del baneo.

## Sin probar

El reload y los límites están probados por consola. Lo que necesita un jugador delante:

1. Pon `maxZonasPorJugador: 2`, recarga, e intenta colocar una tercera piedra → debería rechazarla.
2. Pon `maxMiembrosPorZona: 1`, recarga, e intenta añadir un segundo miembro.
3. Cambia algún flag en `zonasNuevas.flags`, recarga y **coloca una piedra nueva** → debería nacer
   con ese flag como lo dejaste. Las zonas viejas no cambian, eso es intencionado.
