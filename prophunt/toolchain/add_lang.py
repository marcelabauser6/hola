#!/usr/bin/env python3
"""Adds the Prop Hunt / game mode translation keys to every lang file.

The mod ships Spanish text in all locales (including en_us), so we follow that convention
and match the existing tone and quoting style.
"""
import json
import os
import collections

LANG_DIR = '/projects/sandbox/work/extract/assets/fantastic_chameleon/lang'

NEW = collections.OrderedDict([
    ("fantastic.ui.gamemode", "Modo de juego"),
    ("fantastic.ui.gamemode.tip",
     "Elige el modo antes de empezar la partida.\n"
     "Meccha Chameleon: te pintas el cuerpo para camuflarte con el escenario.\n"
     "Prop Hunt: te convertís en el bloque o la criatura que toques con clic derecho.\n"
     "Solo se puede cambiar en el lobby."),
    ("fantastic.gamemode.meccha", "Meccha Chameleon"),
    ("fantastic.gamemode.prophunt", "Prop Hunt"),
    ("fantastic.gamemode.switched", "Modo de juego: %s."),
    ("fantastic.cfg.gamemode_locked", "El modo de juego solo se puede cambiar en el lobby."),
    ("fantastic.prophunt.became", "Te convertiste en %s."),
    ("fantastic.prophunt.no_shape", "Todavía no hay una forma de disfraz para %s."),
    ("fantastic.prophunt.mode_only", "Convertirse en objeto es del modo Prop Hunt; esta sala es Meccha Chameleon."),
    ("fantastic.prophunt.hint", "Clic derecho a un bloque o criatura para convertirte en eso."),
    ("fantastic.prop.cow", "Vaca"),
    ("fantastic.prop.pig", "Cerdo"),
])

changed = 0
for name in sorted(os.listdir(LANG_DIR)):
    if not name.endswith('.json'):
        continue
    path = os.path.join(LANG_DIR, name)
    with open(path, encoding='utf-8') as fh:
        data = json.load(fh, object_pairs_hook=collections.OrderedDict)

    added = []
    for key, value in NEW.items():
        if key not in data:
            data[key] = value
            added.append(key)

    if added:
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write('\n')
        changed += 1
    print(f'{name}: +{len(added)} keys (total {len(data)})')

print(f'\nfiles updated: {changed}')
