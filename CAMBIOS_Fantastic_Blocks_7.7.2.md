# Fantastic Blocks 7.7.2 — robo de cuadros

Jar: **`Fantastic Blocks-7.7.2.jar`**. Incluye todo lo del 7.7.1 (ver
`CAMBIOS_Fantastic_Blocks_7.7.1.md`) más el arreglo del robo de cuadros.
El 7.7.1 se ha quitado del repo para que no haya dudas de cuál instalar.

---

## Qué pasaba

Las protecciones solo cubrían **el golpe directo de un jugador** contra una entidad
(`AttackEntityEvent`). Los cuadros, marcos de items y soportes de armadura son entidades, así que
todo lo demás se saltaba la protección por completo:

| Vector | Antes | Ahora |
|---|---|---|
| Golpe de jugador | bloqueado | bloqueado |
| **Flecha** (incluso disparada desde fuera de la zona) | rompía el cuadro | bloqueado |
| **Bola de nieve, huevo, tridente** | rompía el cuadro | bloqueado |
| **TNT / creeper** | rompía el cuadro | bloqueado con `blockExplosions` |
| **Flecha de esqueleto** u otro mob | rompía el cuadro | bloqueado |
| Clic derecho resuelto en `interactAt` | sin protección | bloqueado |

Cuando el cuadro se rompía, caía al suelo como item y cualquiera se lo llevaba.

## Sobre el mod Joy of Painting

Es el que buscaste: `xercapaint`, de xerca0, versión `xercapaint-1.20.1-1.0.1.jar` para Forge
1.20.1 ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/joy-of-painting); descripción
reformulada por licencia). Descargué el jar y comprobé su código:

```
xerca.xercapaint.common.entity.EntityCanvas extends net.minecraft.world.entity.decoration.HangingEntity
```

Sus lienzos **heredan de `HangingEntity`**, igual que los cuadros y marcos de vanilla, y solo
sobreescriben `interact` — no `hurt`. Por eso el arreglo se ha hecho sobre `HangingEntity`: cubre
de golpe los cuadros de vanilla, los marcos, los lienzos de Joy of Painting y los de cualquier
otro mod que cuelgue cuadros "como los de vanilla", sin depender de ese mod ni tenerlo como
dependencia.

## Cómo se ha arreglado

**1. Mixin en `HangingEntity.hurt`** (`HangingEntityMixin`). Es el único punto por el que pasan
*todas* las formas de romper un cuadro, venga el daño de un jugador, un proyectil, una explosión o
un mob. Antes de aplicar el daño se comprueba la zona y se anula si toca.

**2. Guardián central `DecorationProtection`** con la lógica compartida:

- Considera decoración cualquier `HangingEntity` (cuadros, marcos, lienzos de mods) y los
  `ArmorStand`.
- Identifica al **jugador responsable** aunque el daño llegue por un proyectil: para una flecha,
  `getEntity()` ya devuelve a quien disparó. Así se corta el robo a distancia.
- Si no hay jugador detrás: explosión → manda `blockExplosions`; mob, fuego o cualquier otra cosa
  → manda `blockBuilding`.
- Protege con `blockBuilding` **o** `blockEntityInteract`, porque quitar un cuadro es las dos
  cosas a la vez y así no queda expuesto por apagar solo uno de los dos botones.

**3. Cuadros en el borde de la zona.** Un cuadro colgado en una pared queda medio bloque por
delante de ella, así que si la pared es justo el borde de la protección, la posición del cuadro
caía fuera y no estaba protegido. Ahora se comprueba también el bloque de la pared donde está
clavado.

**4. `EntityInteractSpecific`.** Forge lanza dos eventos al hacer clic derecho en una entidad:
primero `EntityInteractSpecific` (clic en un punto concreto) y después `EntityInteract`. El mod
solo escuchaba el segundo, así que cualquier mod que resuelva la interacción en `interactAt` se
saltaba la protección. Ahora se escuchan los dos.

**5. `ProjectileImpactEvent`.** Segunda red para los proyectiles, que además cubre los
`ArmorStand` (que no son `HangingEntity`).

**6. Explosiones.** `ExplosionEvent.Detonate` solo filtraba los **bloques** afectados; ahora
también se saca la decoración de la lista de entidades afectadas.

## Verificaciones

| Qué | Resultado |
|---|---|
| Compilación (31 clases) | 0 errores |
| Arranque con el mod **y con Joy of Painting real** instalado | limpio, 0 excepciones |
| Los 4 mixins se aplican (`-Dmixin.debug.verbose`) | las 4 líneas `Mixing … into …` |
| **La inyección entra de verdad en `hurt`** (`-Dmixin.debug.export`) | ver abajo |

La última es la importante, porque con `require = 0` un mixin que no encuentra su método falla en
silencio. Exporté el bytecode ya transformado y comprobé que el callback existe y que **se invoca
desde dentro de `m_6469_` (`hurt`)**:

```
private void handler$zzd000$claimblocks$protectDecoration(DamageSource, float, CallbackInfoReturnable);

  // dentro de public boolean m_6469_(DamageSource, float):
  16: invokespecial handler$zzd000$claimblocks$protectDecoration:(...)V
  30: ireturn        <- salida temprana cuando se cancela
```

## Detalle a tener en cuenta

Con los flags por defecto (`blockBuilding` activo), un visitante **tampoco podrá hacer clic
derecho para ver** un cuadro ajeno, porque el clic derecho es la vía que algunos mods usan para
recogerlo y no hay forma de distinguir "mirar" de "coger" sin conocer cada mod. Prioricé que no te
roben. Si en tu servidor quieres que los visitantes puedan mirar los cuadros pero no cogerlos,
dímelo: se puede añadir un flag aparte para eso.

## Nota de compilación

El procesador de anotaciones de Mixin rechaza nombres SRG en `@Inject`, así que la compilación usa
`-proc:none`. El refmap del jar original se reutiliza, y el mixin nuevo referencia el método por
su nombre SRG con descriptor completo (`m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z`),
que es exactamente como se llama en producción. Verificado en el arranque real: se aplica.
Si algún día compilas en un entorno deobfuscado, ese mixin habría que pasarlo a nombres de origen.
