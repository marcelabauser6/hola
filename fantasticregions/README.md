# Fantastic Regions

Reemplaza los comandos de **Yet Another World Protector** por una interfaz en español.

```
/yawp          Abre la lista de zonas
/yawp crear    Nombra la zona, elige la forma y márcala con la vara de blaze
/yawp borrar   Elimina una zona, con autocompletado de los nombres
```

Todo lo demás (flags, miembros, grupos, perímetro, prioridad, anclas) se edita en la interfaz.

## Instalación

Va en **cliente y servidor**, junto a `yawp-1.20.1mohistfix.jar`.

YAWP es un mod de servidor y nunca necesitó cliente. Una interfaz sí, así que este complemento se
instala en los dos lados y las dos mitades hablan por un canal propio. El servidor sigue siendo el
único que decide qué es una zona; el cliente ve una copia y pide cambios.

Quien vaya a administrar zonas necesita el mod en su cliente. Los jugadores que solo juegan no.

## Licencia: AGPL v3, y no es opcional

YAWP está publicado bajo **AGPL v3**. Este complemento se compila contra su API, así que también es
AGPL v3. Eso trae tres obligaciones para quien lo distribuya o lo ponga en un servidor público:

1. **El derivado sigue siendo AGPL v3.** No se puede relicenciar ni cerrar.
2. **Hay que publicar el código.** Quien reciba el jar tiene derecho al fuente correspondiente.
3. **Sección 13, la que suele pillar por sorpresa:** si el mod corre en un servidor al que se conecta
   gente, esa gente puede pedir el código y hay que poder dárselo. No basta con tenerlo en local.

El jar de YAWP **no** se empaqueta dentro de este mod, y esa decisión es a propósito: así el jar de
YAWP sigue siendo el fichero intacto que se descargó, y distribuirlo tal cual es redistribución
verbatim, que la AGPL permite sin más trámite. Se compila contra él (`compileOnly`) y se carga como
dependencia obligatoria en tiempo de ejecución.

El texto completo de la licencia está en `LICENSE`.

## Compilar

El `build.gradle` apunta al jar de YAWP que está en la raíz del repositorio
(`../yawp-1.20.1mohistfix.jar`). Al actualizar YAWP se cambia ese fichero y `yawp_jar` en
`gradle.properties`.

```sh
gradle build
```

`build` incluye tres verificaciones que corren en una JVM normal, sin arrancar Minecraft:

| Tarea                  | Qué comprueba                                                        |
|------------------------|----------------------------------------------------------------------|
| `verifyFlagCatalogue`  | Que estén las 93 flags de YAWP, con nombre y explicación que quepan   |
| `verifySelection`      | Las medidas del marcado: esquinas, radios, deshacer                   |
| `verifyScreenLayouts`  | Que ninguna pantalla se solape ni recorte texto                       |

## Créditos

Construido sobre [Yet Another World Protector](https://github.com/Z0rdak/Yet-Another-World-Protector)
de Z0rdak (AGPL v3). Las zonas, la evaluación de flags y el dibujado de contornos son suyos; este mod
los conduce desde una interfaz.
