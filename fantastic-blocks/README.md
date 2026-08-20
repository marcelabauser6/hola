# Fantastic Blocks (`claimblocks`) — código fuente reconstruido

Este directorio contiene el código fuente del mod, reconstruido a partir de
`Fantastic Blocks-7.7.0.jar` y con los arreglos de la versión **7.7.1** aplicados.
Los cambios están comentados en el propio código.

## Cómo está montado

El jar original está compilado con **nombres SRG** (`m_20148_`, `f_19864_`…), que es la forma
en la que Forge y Mohist ejecutan Minecraft en producción. Para no tener que reescribir 7.000
líneas a nombres oficiales, el proyecto se compila **directamente contra los jars SRG** que
genera el instalador de Forge. No hace falta Gradle ni ForgeGradle.

```
src/      código del mod (30 clases)
tools/    scripts de compilación y empaquetado
tests/    comprobaciones que se pueden ejecutar sin servidor
```

### Importante: las dos clases de cliente

`com/claimblocks/client/ClaimOutlineRenderer` y `ClientBorderStore` **no** están en `src/`:
usan clases de `net.minecraft.client`, que no existen en el jar de servidor. `package.sh` las
reutiliza tal cual del jar 7.7.0, que por eso hace falta para compilar. Si algún día hay que
tocarlas, habría que añadir al classpath un jar de cliente con nombres SRG.

## Preparar el entorno (una vez)

```bash
mkdir -p forge && cd forge
curl -O https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/forge-1.20.1-47.3.0-installer.jar
java -jar forge-1.20.1-47.3.0-installer.jar --installServer .
```

Eso descarga en `forge/libraries/` los jars que usa `tools/cp.sh`:

- `server-1.20.1-20230612.114412-srg.jar` → Minecraft con nombres SRG
- `forge-1.20.1-47.3.0-server.jar` y `-universal.jar` → clases de Forge y las clases de
  Minecraft ya parcheadas por Forge (van **primero** en el classpath, si no `getPersistentData()`
  y compañía no se resuelven)

Ajusta las rutas al principio de `tools/cp.sh` si no usas `/projects/sandbox/build`.

## Compilar y empaquetar

```bash
bash tools/package.sh        # deja el jar en dist/
```

Hace: compilar con Java 17 → descomprimir el jar 7.7.0 → sustituir las clases recompiladas
(conservando `client/`, `assets/`, `claimblocks.mixins.json` y `claimblocks.refmap.json`) →
actualizar `mods.toml` → generar el jar.

El **refmap se reutiliza sin regenerarlo**, lo cual es correcto porque los mixins siguen
apuntando a los mismos métodos (`checkPressed`, `dispenseFrom`, `handleChat`,
`broadcastChatMessage`). Si algún día cambias el objetivo de un mixin, hay que regenerar el
refmap con el annotation processor de Mixin, o el mixin no se aplicará en producción.

## Comprobaciones

```bash
CP="$(bash tools/cp.sh):out"
javac -cp "$CP" -d /tmp/t tests/UuidCheck.java tests/ClaimLogicCheck.java
java -cp "$CP:/tmp/t" UuidCheck          # UUID offline == el del servidor
java -cp "$CP:/tmp/t" ClaimLogicCheck    # miembros, baneos y persistencia JSON
```

Prueba de carga en un servidor real:

```bash
cp dist/Fantastic\ Blocks-7.7.1.jar forge/mods/
cd forge && echo "eula=true" > eula.txt
java -Xmx2G -Dmixin.debug.verbose=true @libraries/net/minecraftforge/forge/1.20.1-47.3.0/unix_args.txt nogui
```

En el log tienen que aparecer las tres líneas `Mixing … into net.minecraft…`. Si falta alguna,
el mixin no se aplicó.

## Utilidad para nombres SRG

`tools/srg.py` traduce nombres oficiales de Minecraft 1.20.1 a SRG cruzando los mappings de
Mojang con los de MCP. Sirve para no adivinar nunca un `m_xxxxx_`:

```bash
python3 tools/srg.py net.minecraft.server.MinecraftServer usesAuthentication
# usesAuthentication()  obf=U  desc=()Z  SRG=m_129797_
```

Cuando un método está sobrecargado salen varios candidatos: hay que elegir el que tenga el
descriptor correcto.
