/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.particles.ParticleOptions
 *  net.minecraft.core.particles.ParticleType
 *  net.minecraft.core.particles.ParticleTypes
 *  net.minecraft.core.particles.SimpleParticleType
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.util.RandomSource
 *  net.minecraftforge.registries.ForgeRegistries
 */
package com.claimblocks.render;

import com.claimblocks.data.Claim;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

public final class ParticleBorder {
    private static final int RENDER_DISTANCE = 24;

    private ParticleBorder() {
    }

    public static SimpleParticleType particleFor(String id) {
        if (id != null && !id.isEmpty()) {
            ParticleType type;
            String rlStr = id.contains(":") ? id : ParticleBorder.legacyToRl(id);
            ResourceLocation rl = ResourceLocation.m_135820_((String)rlStr);
            if (rl != null && (type = (ParticleType)ForgeRegistries.PARTICLE_TYPES.getValue(rl)) instanceof SimpleParticleType) {
                return (SimpleParticleType)type;
            }
            return ParticleTypes.f_123748_;
        }
        return ParticleTypes.f_123748_;
    }

    private static String legacyToRl(String shortId) {
        switch (shortId) {
            case "flame": {
                return "minecraft:flame";
            }
            case "soul": {
                return "minecraft:soul";
            }
            case "heart": {
                return "minecraft:heart";
            }
            case "end_rod": {
                return "minecraft:end_rod";
            }
            case "crit": {
                return "minecraft:crit";
            }
            case "enchant": {
                return "minecraft:enchant";
            }
            case "dragon": {
                return "minecraft:dragon_breath";
            }
            case "portal": {
                return "minecraft:portal";
            }
            case "cloud": {
                return "minecraft:cloud";
            }
            case "spark": {
                return "minecraft:electric_spark";
            }
            case "wax": {
                return "minecraft:wax_on";
            }
        }
        return "minecraft:happy_villager";
    }

    public static String[] availableParticles() {
        return new String[]{"minecraft:happy_villager", "minecraft:heart", "minecraft:flame", "minecraft:small_flame", "minecraft:soul_fire_flame", "minecraft:soul", "minecraft:end_rod", "minecraft:crit", "minecraft:enchanted_hit", "minecraft:enchant", "minecraft:dragon_breath", "minecraft:portal", "minecraft:reverse_portal", "minecraft:cloud", "minecraft:electric_spark", "minecraft:wax_on", "minecraft:glow", "minecraft:totem_of_undying", "minecraft:firework", "minecraft:note", "minecraft:snowflake", "minecraft:cherry_leaves", "minecraft:spore_blossom_air", "minecraft:sculk_soul", "minecraft:lava", "minecraft:splash", "minecraft:witch"};
    }

    public static String particleLabel(String id) {
        if (id == null) {
            return "Aldeano feliz";
        }
        switch (id) {
            case "minecraft:happy_villager": 
            case "happy": {
                return "Aldeano feliz";
            }
            case "minecraft:heart": 
            case "heart": {
                return "Corazones";
            }
            case "minecraft:flame": 
            case "flame": {
                return "Llamas";
            }
            case "minecraft:small_flame": {
                return "Llama peque\u00f1a";
            }
            case "minecraft:soul_fire_flame": 
            case "soul": {
                return "Fuego del alma";
            }
            case "minecraft:soul": {
                return "Almas";
            }
            case "minecraft:end_rod": 
            case "end_rod": {
                return "Vara del End";
            }
            case "minecraft:crit": 
            case "crit": {
                return "Cr\u00edticos";
            }
            case "minecraft:enchanted_hit": {
                return "Golpe encantado";
            }
            case "minecraft:enchant": 
            case "enchant": {
                return "Encantamiento";
            }
            case "minecraft:dragon_breath": 
            case "dragon": {
                return "Aliento de drag\u00f3n";
            }
            case "minecraft:portal": 
            case "portal": {
                return "Portal";
            }
            case "minecraft:reverse_portal": {
                return "Portal inverso";
            }
            case "minecraft:cloud": 
            case "cloud": {
                return "Nube";
            }
            case "minecraft:electric_spark": 
            case "spark": {
                return "Chispa el\u00e9ctrica";
            }
            case "minecraft:wax_on": 
            case "wax": {
                return "Cera brillante";
            }
            case "minecraft:glow": {
                return "Brillo (glow)";
            }
            case "minecraft:totem_of_undying": {
                return "T\u00f3tem";
            }
            case "minecraft:firework": {
                return "Fuegos artificiales";
            }
            case "minecraft:note": {
                return "Nota musical";
            }
            case "minecraft:snowflake": {
                return "Copo de nieve";
            }
            case "minecraft:cherry_leaves": {
                return "P\u00e9talos de cerezo";
            }
            case "minecraft:spore_blossom_air": {
                return "Esporas";
            }
            case "minecraft:sculk_soul": {
                return "Alma de sculk";
            }
            case "minecraft:lava": {
                return "Lava";
            }
            case "minecraft:splash": {
                return "Salpicadura";
            }
            case "minecraft:witch": {
                return "Bruja";
            }
        }
        return id.contains(":") ? id.substring(id.indexOf(58) + 1) : id;
    }

    public static void fillClaim(ServerLevel level, ServerPlayer player, Claim claim) {
        SimpleParticleType particle = ParticleBorder.particleFor(claim.getFlags().borderParticle);
        int density = Math.max(1, Math.min(200, claim.getFlags().particleDensity));
        int r = claim.getRadius();
        int h = claim.getHeight();
        double claimMinX = claim.getX() - r;
        double claimMaxX = claim.getX() + r + 1;
        double claimMinZ = claim.getZ() - r;
        double claimMaxZ = claim.getZ() + r + 1;
        double claimMinY = claim.getY() - h;
        double claimMaxY = claim.getY() + h + 1;
        double loX = Math.max(claimMinX, player.m_20185_() - 24.0);
        double hiX = Math.min(claimMaxX, player.m_20185_() + 24.0);
        double loZ = Math.max(claimMinZ, player.m_20189_() - 24.0);
        double hiZ = Math.min(claimMaxZ, player.m_20189_() + 24.0);
        double loY = Math.max(claimMinY, player.m_20186_() - 24.0);
        double hiY = Math.min(claimMaxY, player.m_20186_() + 24.0);
        if (!(loX > hiX || loZ > hiZ || loY > hiY)) {
            RandomSource random = level.m_213780_();
            for (int i = 0; i < density; ++i) {
                double x = loX + random.m_188500_() * (hiX - loX);
                double y = loY + random.m_188500_() * (hiY - loY);
                double z = loZ + random.m_188500_() * (hiZ - loZ);
                level.m_8624_(player, (ParticleOptions)particle, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    public static boolean withinRenderRange(ServerPlayer player, Claim claim) {
        double dx = Math.max(0.0, Math.abs(player.m_20185_() - ((double)claim.getX() + 0.5)) - (double)claim.getRadius());
        double dz = Math.max(0.0, Math.abs(player.m_20189_() - ((double)claim.getZ() + 0.5)) - (double)claim.getRadius());
        return dx <= 24.0 && dz <= 24.0;
    }
}

