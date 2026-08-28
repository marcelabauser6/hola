#!/usr/bin/env python3
"""Comprueba que cada opcion de ClaimConfig se lee de verdad en algun sitio del mod.
Si una opcion solo existe dentro de ClaimConfig, es decorativa y la config estaria mintiendo."""
import io, os, re, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "src"
CFG = None
for root, _, names in os.walk(SRC):
    for n in names:
        if n == "ClaimConfig.java":
            CFG = os.path.join(root, n)
if CFG is None:
    print("no encuentro ClaimConfig.java")
    sys.exit(2)

texto_cfg = io.open(CFG, encoding="utf-8").read()

# campos publicos de la config
campos = re.findall(r"public (?:final )?(?:int|boolean|float|String|Map<[^>]*>) (\w+)", texto_cfg)
# claves del JSON tal y como se leen/escriben
claves = sorted(set(re.findall(r'read(?:Int|Bool|Float|String)\([^,]+, "([^"]+)"', texto_cfg)))
flags_dinamicos = "id.name()" in texto_cfg

# el resto del codigo
otros = {}
for root, _, names in os.walk(SRC):
    for n in names:
        if n.endswith(".java") and n != "ClaimConfig.java":
            p = os.path.join(root, n)
            otros[p] = io.open(p, encoding="utf-8", errors="replace").read()

print("%-34s %-9s %s" % ("OPCION (campo)", "ESTADO", "USADA EN"))
print("-" * 92)
muertos = []
for c in sorted(set(campos)):
    usos = []
    for p, t in otros.items():
        n = t.count("." + c)
        if n:
            usos.append("%s(%d)" % (os.path.basename(p).replace(".java", ""), n))
    # los helpers derivados cuentan como uso del campo base
    derivados = {
        "trespasserAlertSeconds": "trespasserAlertTicks",
        "chatPromptSeconds": "chatPromptMillis",
        "banNoticeSeconds": "banNoticeTicks",
        "defaultFlags": "applyDefaultsTo",
        "defaultParticle": "applyDefaultsTo",
        "defaultParticleDensity": "applyDefaultsTo",
    }
    if not usos and c in derivados:
        for p, t in otros.items():
            if derivados[c] + "(" in t:
                usos.append("%s [via %s()]" % (os.path.basename(p).replace(".java", ""), derivados[c]))
    if usos:
        print("%-34s %-9s %s" % (c, "OK", ", ".join(sorted(usos))))
    else:
        print("%-34s %-9s -" % (c, "MUERTA"))
        muertos.append(c)

print("-" * 92)
print("Claves JSON declaradas: %d%s" % (len(claves), "  (+ los 34 flags de zonasNuevas, generados del enum)" if flags_dinamicos else ""))
if muertos:
    print("OPCIONES SIN EFECTO (%d): %s" % (len(muertos), ", ".join(muertos)))
    sys.exit(1)
print("Todas las opciones de la config tienen efecto real.")
