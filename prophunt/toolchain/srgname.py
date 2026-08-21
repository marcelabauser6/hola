#!/usr/bin/env python3
"""Look up Forge production (SRG) names for Minecraft members, matching on full descriptors.

The mod jar is reobfuscated to SRG, so new code must call Minecraft with SRG member names
(m_12345_ / f_12345_). Chain:  official --(client.txt)--> obf --(joined.tsrg)--> srg

Overloads share obf names, so matching by name alone is wrong. We rebuild the obf JVM
descriptor from the ProGuard signature and match on (obfName, obfDescriptor).

Usage:
    python3 srgname.py <OfficialClassName> [memberNameFilter]
"""
import sys
import os

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'forge')
PROGUARD = os.path.join(BASE, 'client.txt')
TSRG = os.path.join(BASE, 'config', 'joined.tsrg')

PRIMS = {
    'void': 'V', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S',
    'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D',
}


def parse_proguard():
    """-> classes{official: {'obf':str,'members':[(kind,name,obf,ret,args)]}}, obf_of{official:obf}"""
    classes = {}
    obf_of = {}
    cur = None
    with open(PROGUARD, encoding='utf-8') as fh:
        for line in fh:
            if line.startswith('#') or not line.strip():
                continue
            if not line[0].isspace():
                left, _, right = line.strip().partition(' -> ')
                obf = right[:-1] if right.endswith(':') else right
                cur = left
                classes[left] = {'obf': obf, 'members': []}
                obf_of[left] = obf
            elif cur is not None:
                body = line.strip()
                left, _, obf_member = body.partition(' -> ')
                left = left.split(':')[-1]
                if '(' in left:
                    head, _, rest = left.partition('(')
                    args = rest.rstrip(')')
                    ret = head.split()[0]
                    name = head.split()[-1]
                    arglist = [a for a in args.split(',') if a]
                    classes[cur]['members'].append(('method', name, obf_member, ret, arglist))
                else:
                    parts = left.split()
                    classes[cur]['members'].append(('field', parts[-1], obf_member, parts[0], None))
    return classes, obf_of


def to_desc(type_name, obf_of):
    """official java type -> obf JVM descriptor"""
    dims = 0
    while type_name.endswith('[]'):
        dims += 1
        type_name = type_name[:-2]
    if type_name in PRIMS:
        base = PRIMS[type_name]
    else:
        obf = obf_of.get(type_name, type_name)
        base = 'L' + obf.replace('.', '/') + ';'
    return '[' * dims + base


def parse_tsrg():
    """-> {obf class: {('field',obfName): srg, ('method',obfName,desc): srg}}"""
    out = {}
    cur = None
    with open(TSRG, encoding='utf-8') as fh:
        fh.readline()
        for raw in fh:
            line = raw.rstrip('\n')
            if not line.strip():
                continue
            depth = len(line) - len(line.lstrip('\t'))
            parts = line.strip().split()
            if depth == 0:
                cur = parts[0]
                out.setdefault(cur, {})
            elif depth == 1 and cur is not None:
                if len(parts) == 3:
                    out[cur][('field', parts[0])] = parts[1]
                elif len(parts) == 4:
                    out[cur][('method', parts[0], parts[1])] = parts[2]
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    want, filt = sys.argv[1], (sys.argv[2].lower() if len(sys.argv) > 2 else None)

    classes, obf_of = parse_proguard()
    tsrg = parse_tsrg()

    targets = [c for c in classes if c == want or c.split('.')[-1] == want]
    if not targets:
        targets = [c for c in classes if want.lower() in c.lower()][:5]
    if not targets:
        print('No class matched: ' + want)
        return 1

    for cls in targets:
        info = classes[cls]
        table = tsrg.get(info['obf'], {})
        print('=' * 78)
        print(f"{cls}   (obf {info['obf']})")
        print('=' * 78)
        for kind, name, obf_member, ret, arglist in info['members']:
            if filt and filt not in name.lower():
                continue
            if kind == 'field':
                srg = table.get(('field', obf_member))
                print(f'  field  {name:32} srg={srg}')
            else:
                desc = '(' + ''.join(to_desc(a, obf_of) for a in arglist) + ')' + to_desc(ret, obf_of)
                srg = table.get(('method', obf_member, desc))
                sig = '(' + ', '.join(a.split('.')[-1] for a in arglist) + ') -> ' + ret.split('.')[-1]
                print(f'  method {name:32} srg={srg}   {sig}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
