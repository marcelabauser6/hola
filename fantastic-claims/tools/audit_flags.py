#!/usr/bin/env python3
"""Auditoria de flags: comprueba que CADA flag del menu se consulta de verdad en el codigo
que aplica las protecciones, y no solo se dibuja en el GUI.

Se descompila el jar publicado y se cuentan las lecturas del campo separando:
  - APLICACION  -> event/, mixin/, util/, data/Claim.java   (codigo que protege)
  - solo GUI    -> gui/, data/ClaimFlags.java               (dibujar y guardar el boton)
"""
import io, os, re, sys, subprocess

SRC = sys.argv[1] if len(sys.argv) > 1 else "verify2"

FLAGS = [
    ("blockBuilding", "BUILDING"), ("blockBreaking", "BREAKING"),
    ("blockExplosions", "EXPLOSIONS"), ("blockFire", "FIRE"),
    ("blockMobSpawn", "MOB_SPAWN"), ("blockPVP", "PVP"),
    ("blockMobDamage", "MOB_DAMAGE"), ("trespasserAlerts", "ALERTS"),
    ("blockItemUse", "ITEM_USE"), ("blockEntityInteract", "ENTITY_INTERACT"),
    ("blockTrampling", "TRAMPLING"), ("blockFluids", "FLUIDS"),
    ("pvpAll", "PVP_ALL"), ("blockTreeChopping", "TREE_CHOPPING"),
    ("publicMode", "PUBLIC_MODE"), ("showWelcome", "SHOW_WELCOME"),
    ("welcomeMessage", "(texto bienvenida)"), ("showLeave", "SHOW_LEAVE"),
    ("leaveMessage", "(texto salida)"), ("showBorder", "SHOW_BORDER"),
    ("showParticles", "SHOW_PARTICLES"), ("borderParticle", "(particula)"),
    ("particleDensity", "(densidad)"), ("burnHostiles", "BURN_HOSTILES"),
    ("effectRegeneration", "EFFECT_REGEN"), ("effectResistance", "EFFECT_RESIST"),
    ("effectSpeed", "EFFECT_SPEED"), ("blockAnimalKilling", "ANIMAL_KILLING"),
    ("blockChestAccess", "CHEST_ACCESS"), ("blockCropHarvest", "CROP_HARVEST"),
    ("blockAnvilUse", "ANVIL_USE"), ("blockEnderPearl", "ENDER_PEARL"),
    ("blockSignEditing", "SIGN_EDITING"), ("allowFlight", "ALLOW_FLIGHT"),
    ("blockDoorsAccess", "DOORS_ACCESS"), ("blockAllInteractions", "BLOCK_ALL_INTERACT"),
    ("blockAllMobSpawn", "ALL_MOB_SPAWN"), ("blockPassiveMobSpawn", "PASSIVE_MOB_SPAWN"),
]

# ficheros que APLICAN la proteccion (si un flag solo aparece fuera de aqui, es decorativo)
def is_enforcement(path):
    p = path.replace(os.sep, "/")
    if "/data/ClaimFlags.java" in p:
        return False
    return ("/event/" in p or "/mixin/" in p or "/util/" in p
            or "/data/Claim.java" in p or "/render/" in p or "ClaimBlocksMod.java" in p)


def main():
    files = {}
    for root, _, names in os.walk(SRC):
        for n in names:
            if n.endswith(".java"):
                fp = os.path.join(root, n)
                files[fp] = io.open(fp, encoding="utf-8", errors="replace").read()

    print("%-22s %-20s %-9s %s" % ("CAMPO", "BOTON", "ESTADO", "DONDE SE APLICA"))
    print("-" * 100)
    decorativos = []
    for field, label in FLAGS:
        pat = re.compile(r"\.%s\b" % re.escape(field))
        enforce, gui = [], []
        for fp, text in files.items():
            hits = len(pat.findall(text))
            if not hits:
                continue
            name = os.path.basename(fp)
            (enforce if is_enforcement(fp) else gui).append("%s(%d)" % (name.replace(".java", ""), hits))
        if enforce:
            estado = "OK"
        else:
            estado = "SOLO GUI"
            decorativos.append(field)
        print("%-22s %-20s %-9s %s" % (field, label, estado, ", ".join(sorted(enforce)) or "-"))

    print("-" * 100)
    if decorativos:
        print("DECORATIVOS (%d): %s" % (len(decorativos), ", ".join(decorativos)))
    else:
        print("Los %d flags se consultan en el codigo de proteccion." % len(FLAGS))
    return 1 if decorativos else 0


if __name__ == "__main__":
    sys.exit(main())
