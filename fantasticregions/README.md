# Fantastic Regions

Una vara de blaze para **preseleccionar** el área de una zona de Yet Another World Protector.

```
/yawp wand cuboide     Una caja recta, marcando dos esquinas opuestas
/yawp wand esfera      Una bola, marcando el centro y un bloque del borde
/yawp wand circulo     Un cilindro vertical, marcando el centro y el borde
/yawp wand poligono    Una planta de varios lados, marcando cada vértice
/yawp wand prisma      Un polígono con altura
```

El perímetro **se dibuja en el mundo mientras llevas la vara en la mano**, y sobre la barra rápida
aparece la medida (`24 x 18 x 24 bloques`, `radio 12 bloques`). Cuando falta un solo punto, el
contorno sigue al bloque que estás mirando en amarillo, así que ves la zona antes de confirmarla.

Después creas la zona con **el comando de crear de YAWP, sin cambios**: la preselección va en la vara
y su comando la recoge tal cual.

## A YAWP no se le toca nada

Ni un comando quitado, ni una clase parcheada, ni el jar reempaquetado. La vara lleva los datos de
marcado que YAWP ya define, escritos con su propio `StickUtil`, y de ahí salen dos cosas gratis:

- **Su comando de crear la acepta.** Pregunta `StickUtil.isMarker` y lee los bloques marcados de la
  etiqueta del item; nunca pregunta qué item los lleva.
- **Los clics ya funcionan.** Su mixin de interacción también mira solo `isMarker`, no si es un palo
  de vanilla, así que registra las esquinas él mismo. Este mod **no** intercepta los clics: hacerlo
  habría marcado cada esquina dos veces.

Las cinco formas son las cinco que YAWP tiene, y cuántos puntos pide cada una se lee de sus campos
`AreaType.neededBlocks`/`maxBlocks` en tiempo de ejecución, no de una copia aquí.

## Instalación: solo servidor

No hay item nuevo, ni paquete de red, ni código de renderizado. El contorno son partículas dirigidas
al jugador que lleva la vara, que cualquier cliente de vanilla dibuja. El mod va **solo en el
servidor**, junto a `yawp-1.20.1mohistfix.jar`, y `displayTest="IGNORE_ALL_VERSION"` deja que los
clientes sin él se conecten igual.

Las partículas se envían solo a quien lleva la vara: una preselección es andamio, no una parte del
mundo, y no tiene por qué llenarle la pantalla a quien pase al lado.

## Licencia: AGPL v3, y no es opcional

YAWP está publicado bajo **AGPL v3**. Este mod se compila contra su API, así que también es AGPL v3.
Eso trae tres obligaciones para quien lo distribuya o lo ponga en un servidor público:

1. **El derivado sigue siendo AGPL v3.** No se puede relicenciar ni cerrar.
2. **Hay que publicar el código.** Quien reciba el jar tiene derecho al fuente correspondiente.
3. **Sección 13, la que suele pillar por sorpresa:** si el mod corre en un servidor al que se conecta
   gente, esa gente puede pedir el código y hay que poder dárselo. No basta con tenerlo en local.

El jar de YAWP **no** se empaqueta dentro de este mod, y es a propósito: así sigue siendo el fichero
intacto que se descargó, y distribuirlo tal cual es redistribución verbatim, que la AGPL permite sin
más trámite.

El texto completo está en `LICENSE`.

## Compilar

`build.gradle` apunta al jar de YAWP que está en la raíz del repositorio
(`../yawp-1.20.1mohistfix.jar`). Al actualizar YAWP se cambia ese fichero y `yawp_jar` en
`gradle.properties`.

```sh
gradle build
```

`build` incluye una verificación que corre en una JVM normal, sin arrancar Minecraft:

| Tarea               | Qué comprueba                                                              |
|---------------------|----------------------------------------------------------------------------|
| `verifyWandShapes`  | Que las cinco formas cubran los cinco tipos de área de YAWP, que todo nombre que ofrece el autocompletado lo acepte Brigadier, y que se parseen con acento y sin él |

Ese verificador existe por un fallo concreto: Brigadier no acepta acentos en un argumento sin
comillas, así que un id como `círculo` se autocompletaría bien y luego fallaría al enviarlo. Los ids
son ASCII y los acentos viven solo en las etiquetas que se muestran.

## Créditos

Construido sobre [Yet Another World Protector](https://github.com/Z0rdak/Yet-Another-World-Protector)
de Z0rdak (AGPL v3). El marcado, la geometría de las áreas y la creación de zonas son suyos; este mod
solo entrega una vara mejor y dibuja lo que se va marcando.
