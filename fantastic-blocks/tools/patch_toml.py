import io, sys

path = sys.argv[1]
src = io.open(path, encoding="utf-8").read()

src = src.replace('version="7.7.0"', 'version="7.7.2"')

old_desc_start = src.index("description='''")
old_desc_end = src.index("'''", old_desc_start + len("description='''")) + 3
new_desc = """description='''
Mod de proteccion de zonas: 10 tiers usando concretos vanilla, flags por proteccion, panel admin y persistencia JSON. Forge 1.20.1.
Incluye visualizacion del area con particulas (server-side) y mensajes de bienvenida y de salida configurables.
Pensado para servidores hibridos (Mohist / Arclight / Magma): la entrada de texto de los menus se captura a nivel de paquete, por lo que
funciona aunque los plugins de chat consuman el mensaje; en servidores sin autenticacion (offline-mode) los miembros y baneos se resuelven
con el UUID offline correcto; y el canal de red es opcional, asi que no bloquea la conexion de clientes que no tengan el mod.
Nota: el mod registra items propios, por lo que el cliente necesita tener el mod instalado para ver y colocar las piedras de proteccion.
'''"""
src = src[:old_desc_start] + new_desc + src[old_desc_end:]

io.open(path, "w", encoding="utf-8").write(src)
print("mods.toml -> 7.7.2")
