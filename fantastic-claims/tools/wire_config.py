#!/usr/bin/env python3
"""Conecta ClaimConfig con el resto del mod. Falla ruidosamente si un patron no aparece,
para que no quede ninguna opcion de la config sin efecto real."""
import io, sys

EDITS = [
    # ---------------- ClaimManager: la config pasa a ser la fuente de verdad
    ("data/ClaimManager.java",
     "    public static int getMaxClaimsPerPlayer() {\n        return MAX_CLAIMS_PER_PLAYER;\n    }",
     "    public static int getMaxClaimsPerPlayer() {\n        return ClaimConfig.get().maxClaimsPerPlayer;\n    }"),
    ("data/ClaimManager.java",
     "    public static void setMaxClaimsPerPlayer(int n) {\n        MAX_CLAIMS_PER_PLAYER = Math.max(0, n);\n    }",
     "    public static void setMaxClaimsPerPlayer(int n) {\n        ClaimConfig.get().maxClaimsPerPlayer = Math.max(0, n);\n    }"),

    # ---------------- ClaimBlocksMod: intervalos de particulas y bordes
    ("ClaimBlocksMod.java",
     "            if (++particleCounter % 4 == 0) {",
     "            if (++particleCounter % ClaimConfig.get().particleIntervalTicks == 0) {"),
    ("ClaimBlocksMod.java",
     "            if (particleCounter % 20 == 0) {",
     "            if (particleCounter % ClaimConfig.get().borderIntervalTicks == 0) {"),

    # ---------------- ParticleBorder: distancia de render
    ("render/ParticleBorder.java",
     "        return dx <= 24.0 && dz <= 24.0;",
     "        double max = com.claimblocks.data.ClaimConfig.get().particleRenderDistance;\n        return dx <= max && dz <= max;"),

    # ---------------- PlayerTracker: antiespam del aviso de intruso
    ("event/PlayerTracker.java",
     "                    if (last == null || t - last > 600L) {",
     "                    if (last == null || t - last > (long)ClaimConfig.get().trespasserAlertTicks()) {"),
    # ---------------- PlayerTracker: comportamiento del baneo
    ("event/PlayerTracker.java",
     "        double ty = PlayerTracker.safeY(world, tx, player.m_20186_(), tz);\n"
     "        player.m_8999_(world, tx, ty, tz, player.m_146908_(), player.m_146909_());\n"
     "        player.m_20334_(0.0, 0.0, 0.0);\n"
     "        player.f_19864_ = true;",
     "        ClaimConfig cfg = ClaimConfig.get();\n"
     "        if (cfg.banTeleportOut) {\n"
     "            double ty = PlayerTracker.safeY(world, tx, player.m_20186_(), tz);\n"
     "            player.m_8999_(world, tx, ty, tz, player.m_146908_(), player.m_146909_());\n"
     "            player.m_20334_(0.0, 0.0, 0.0);\n"
     "        } else {\n"
     "            double px2 = player.m_20185_() - cx;\n"
     "            double pz2 = player.m_20189_() - cz;\n"
     "            double mag = Math.max(1.0E-4, Math.sqrt(px2 * px2 + pz2 * pz2));\n"
     "            player.m_20334_(px2 / mag * 1.2, 0.42, pz2 / mag * 1.2);\n"
     "        }\n"
     "        player.f_19864_ = true;"),
    ("event/PlayerTracker.java",
     "        if (last == null || now - last >= 40L) {\n"
     "            lastBanHit.put(player.m_20148_(), now);",
     "        if (last == null || now - last >= ClaimConfig.get().banNoticeTicks()) {\n"
     "            lastBanHit.put(player.m_20148_(), now);\n"
     "            if (cfg.banDamage > 0.0f) {\n"
     "                player.f_19802_ = 0;\n"
     "                player.m_6469_(player.m_269291_().m_269425_(), cfg.banDamage);\n"
     "            }"),

    # ---------------- BlockProtectionEvents: barrido de fuego
    ("event/BlockProtectionEvents.java",
     "        if (++fireSweepCounter % 40 == 0) {",
     "        if (++fireSweepCounter % ClaimConfig.get().fireSweepIntervalTicks == 0) {"),
    ("event/BlockProtectionEvents.java",
     "        int var3 = 6;",
     "        int var3 = ClaimConfig.get().fireSweepRadius;"),
    # ---------------- BlockProtectionEvents: decoracion frente a explosiones
    ("event/BlockProtectionEvents.java",
     "            var1.getAffectedEntities().removeIf(entity -> {\n"
     "                if (!DecorationProtection.isDecoration((Entity)entity)) {",
     "            var1.getAffectedEntities().removeIf(entity -> {\n"
     "                if (!ClaimConfig.get().protectDecorationFromExplosions) {\n"
     "                    return false;\n"
     "                }\n"
     "                if (!DecorationProtection.isDecoration((Entity)entity)) {"),

    # ---------------- PassiveEffectsManager: intervalos y duracion
    ("event/PassiveEffectsManager.java",
     "        if (++counter % 20 == 0) {\n            boolean runEffects = counter % 40 == 0;",
     "        if (++counter % 20 == 0) {\n            boolean runEffects = counter % Math.max(20, ClaimConfig.get().passiveEffectIntervalTicks) < 20;"),
    ("event/PassiveEffectsManager.java",
     "MobEffects.f_19605_, 60, 0,", "MobEffects.f_19605_, ClaimConfig.get().effectDurationTicks, 0,"),
    ("event/PassiveEffectsManager.java",
     "MobEffects.f_19606_, 60, 0,", "MobEffects.f_19606_, ClaimConfig.get().effectDurationTicks, 0,"),
    ("event/PassiveEffectsManager.java",
     "MobEffects.f_19596_, 60, 0,", "MobEffects.f_19596_, ClaimConfig.get().effectDurationTicks, 0,"),

    # ---------------- EntityProtectionEvents: barrera de hostiles
    ("event/EntityProtectionEvents.java",
     "        mob.m_20254_(3);\n        mob.f_19802_ = 0;\n        mob.m_6469_(mob.m_269291_().m_269264_(), 3.0f);",
     "        ClaimConfig cfg = ClaimConfig.get();\n"
     "        if (cfg.hostileBurnSeconds > 0) {\n"
     "            mob.m_20254_(cfg.hostileBurnSeconds);\n"
     "        }\n"
     "        if (cfg.hostileDamage > 0.0f) {\n"
     "            mob.f_19802_ = 0;\n"
     "            mob.m_6469_(mob.m_269291_().m_269264_(), cfg.hostileDamage);\n"
     "        }"),

    # ---------------- ClaimMenuHandler: caducidad del prompt y limite de texto
    ("gui/ClaimMenuHandler.java",
     "            return System.currentTimeMillis() - this.createdAtMillis > 90000L;",
     "            return System.currentTimeMillis() - this.createdAtMillis > ClaimConfig.get().chatPromptMillis();"),

    # ---------------- BorderGuard: interruptores
    ("util/BorderGuard.java",
     "        if (level == null || level.m_5776_() || fromPos == null || toPos == null) {\n            return false;\n        }\n        ClaimManager mgr = ClaimManager.getInstance();\n        Claim from = mgr.getClaimAt(level, fromPos);",
     "        if (level == null || level.m_5776_() || fromPos == null || toPos == null) {\n            return false;\n        }\n        if (!ClaimConfig.get().protectHoppers) {\n            return false;\n        }\n        ClaimManager mgr = ClaimManager.getInstance();\n        Claim from = mgr.getClaimAt(level, fromPos);"),
    ("util/BorderGuard.java",
     "        if (level == null || level.m_5776_() || fromPos == null || toPos == null) {\n            return false;\n        }\n        ClaimManager mgr = ClaimManager.getInstance();\n        Claim to = mgr.getClaimAt(level, toPos);",
     "        if (level == null || level.m_5776_() || fromPos == null || toPos == null) {\n            return false;\n        }\n        if (!ClaimConfig.get().protectFluids) {\n            return false;\n        }\n        ClaimManager mgr = ClaimManager.getInstance();\n        Claim to = mgr.getClaimAt(level, toPos);"),

    # ---------------- DecorationProtection: interruptor general
    ("util/DecorationProtection.java",
     "    public static boolean isDecoration(Entity entity) {\n        return entity instanceof HangingEntity || entity instanceof ArmorStand;",
     "    public static boolean isDecoration(Entity entity) {\n        if (!ClaimConfig.get().protectDecoration) {\n            return false;\n        }\n        return entity instanceof HangingEntity || entity instanceof ArmorStand;"),
    ("util/DecorationProtection.java",
     "        if (source != null && source.m_269533_(DamageTypeTags.f_268415_)) {\n            // TNT, creepers, cristales del end...\n            return flags.blockExplosions || flags.publicMode;",
     "        if (source != null && source.m_269533_(DamageTypeTags.f_268415_)) {\n            // TNT, creepers, cristales del end...\n            return ClaimConfig.get().protectDecorationFromExplosions && (flags.blockExplosions || flags.publicMode);"),
]

IMPORTS = {
    "ClaimBlocksMod.java": "import com.claimblocks.data.ClaimConfig;",
    "event/PlayerTracker.java": "import com.claimblocks.data.ClaimConfig;",
    "event/BlockProtectionEvents.java": "import com.claimblocks.data.ClaimConfig;",
    "event/PassiveEffectsManager.java": "import com.claimblocks.data.ClaimConfig;",
    "event/EntityProtectionEvents.java": "import com.claimblocks.data.ClaimConfig;",
    "gui/ClaimMenuHandler.java": "import com.claimblocks.data.ClaimConfig;",
    "util/BorderGuard.java": "import com.claimblocks.data.ClaimConfig;",
    "util/DecorationProtection.java": "import com.claimblocks.data.ClaimConfig;",
}

BASE = "src/com/claimblocks/"
fallos = 0

for rel, old, new in EDITS:
    p = BASE + rel
    txt = io.open(p, encoding="utf-8").read()
    if old not in txt:
        print("FALLA no encontrado en %s: %s" % (rel, old.strip().splitlines()[0][:70]))
        fallos += 1
        continue
    txt = txt.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8").write(txt)
    print("ok  %-38s %s" % (rel, new.strip().splitlines()[0][:60]))

for rel, imp in IMPORTS.items():
    p = BASE + rel
    txt = io.open(p, encoding="utf-8").read()
    if imp in txt:
        continue
    idx = txt.index("\nimport ")
    txt = txt[:idx + 1] + imp + "\n" + txt[idx + 1:]
    io.open(p, "w", encoding="utf-8").write(txt)
    print("import anadido a %s" % rel)

print("\nFALLOS: %d" % fallos)
sys.exit(1 if fallos else 0)
