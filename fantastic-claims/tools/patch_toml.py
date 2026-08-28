import io, sys

path = sys.argv[1]
src = io.open(path, encoding="utf-8").read()

# OJO: el modId sigue siendo "claimblocks" a proposito. Cambiarlo renombraria los items
# (claimblocks:proteccion_*), con lo que las piedras que los jugadores ya tienen en el inventario
# desaparecerian, y ademas dejaria huerfano el claimblocks_data.json del mundo.
src = src.replace('version="7.7.0"', 'version="7.9.0"')
src = src.replace('displayName="Protecciones de Zonas"', 'displayName="Fantastic Claims"')

old_desc_start = src.index("description='''")
old_desc_end = src.index("'''", old_desc_start + len("description='''")) + 3
new_desc = """description='''
Fantastic Claims: proteccion de zonas con 10 tamanos usando concretos vanilla, flags por proteccion, panel admin y persistencia JSON. Forge 1.20.1.
Comandos: /fsclaim (menu, info, list, remove, addmember, delmember, members, ban, unban, transfer, give, clear), /fsclaimadmin y /fsclaimmerge.
Pensado para servidores hibridos (Mohist / Arclight / Magma): la entrada de texto de los menus se captura a nivel de paquete, por lo que
funciona aunque los plugins de chat consuman el mensaje; en servidores sin autenticacion (offline-mode) los miembros y baneos se resuelven
con el UUID offline correcto; y el canal de red es opcional, asi que no bloquea la conexion de clientes que no tengan el mod.
Protege tambien cuadros, marcos y soportes de armadura frente a flechas, explosiones y mobs, e impide que las tolvas saquen items de la zona
y que el agua o la lava entren desde fuera del borde.
Nota: el mod registra items propios, por lo que el cliente necesita tener el mod instalado para ver y colocar las piedras de proteccion.
'''"""
src = src[:old_desc_start] + new_desc + src[old_desc_end:]

io.open(path, "w", encoding="utf-8").write(src)
print("mods.toml -> Fantastic Claims 7.8.0")
