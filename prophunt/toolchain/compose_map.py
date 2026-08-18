#!/usr/bin/env python3
"""Compose obf -> (official class names + SRG member names) TSRG2 mapping.

Forge production namespace = Mojang official class names with SRG member names.
client.txt (ProGuard) gives official<->obf. joined.tsrg gives obf->srg.
"""
import sys

# ---- 1. parse client.txt (ProGuard): obfClass -> officialClass (internal form)
obf2official = {}
with open('client.txt', encoding='utf-8') as fh:
    for line in fh:
        if line.startswith('#') or not line.strip():
            continue
        if line[0].isspace():
            continue  # member line, not needed
        # "official.Class.Name -> obf:"
        left, _, right = line.strip().partition(' -> ')
        if not right.endswith(':'):
            continue
        obf = right[:-1]
        obf2official[obf.replace('.', '/')] = left.replace('.', '/')

# ---- 2. parse joined.tsrg (tsrg2 obf srg id) and emit composed mapping
out = []
out.append('tsrg2 left right')
cur_kept = False
classes = fields = methods = 0

with open('config/joined.tsrg', encoding='utf-8') as fh:
    header = fh.readline()
    if not header.startswith('tsrg2'):
        sys.exit('unexpected joined.tsrg header: ' + header)
    for raw in fh:
        line = raw.rstrip('\n')
        if not line.strip():
            continue
        depth = len(line) - len(line.lstrip('\t'))
        parts = line.strip().split()
        if depth == 0:
            # class: obf srg id
            obf = parts[0]
            official = obf2official.get(obf, obf)
            out.append(f'{obf} {official}')
            classes += 1
            cur_kept = True
        elif depth == 1 and cur_kept:
            if len(parts) == 3:
                # field: obfName srgName id
                out.append(f'\t{parts[0]} {parts[1]}')
                fields += 1
            elif len(parts) == 4:
                # method: obfName obfDesc srgName id
                out.append(f'\t{parts[0]} {parts[1]} {parts[2]}')
                methods += 1
        # depth >= 2 -> params / static markers, skipped

with open('composed.tsrg', 'w', encoding='utf-8') as fh:
    fh.write('\n'.join(out) + '\n')

print(f'official class names parsed : {len(obf2official)}')
print(f'classes emitted             : {classes}')
print(f'fields emitted              : {fields}')
print(f'methods emitted             : {methods}')
