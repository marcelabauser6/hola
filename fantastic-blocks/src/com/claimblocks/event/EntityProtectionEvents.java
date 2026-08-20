/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.tags.DamageTypeTags
 *  net.minecraft.world.Container
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.entity.AgeableMob
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.MobSpawnType
 *  net.minecraft.world.entity.TamableAnimal
 *  net.minecraft.world.entity.ambient.AmbientCreature
 *  net.minecraft.world.entity.animal.Animal
 *  net.minecraft.world.entity.animal.IronGolem
 *  net.minecraft.world.entity.animal.SnowGolem
 *  net.minecraft.world.entity.animal.WaterAnimal
 *  net.minecraft.world.entity.decoration.ItemFrame
 *  net.minecraft.world.entity.monster.Enemy
 *  net.minecraft.world.entity.monster.Monster
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraftforge.event.entity.EntityJoinLevelEvent
 *  net.minecraftforge.event.entity.living.LivingDropsEvent
 *  net.minecraftforge.event.entity.living.LivingEvent$LivingTickEvent
 *  net.minecraftforge.event.entity.living.LivingExperienceDropEvent
 *  net.minecraftforge.event.entity.living.LivingHurtEvent
 *  net.minecraftforge.event.entity.living.MobSpawnEvent$FinalizeSpawn
 *  net.minecraftforge.event.entity.player.AttackEntityEvent
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$EntityInteract
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 */
package com.claimblocks.event;

import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class EntityProtectionEvents {
    private static final String BARRIER_TAG = "claimblocks_barrier_tick";
    private static final long BARRIER_WINDOW = 200L;
    private static final String SPAWN_OK_TAG = "claimblocks_spawn_allowed";

    private static boolean isBypassing(Player player) {
        return player.m_20310_(2) && ClaimManager.getInstance().isBypassing(player.m_20148_());
    }

    private static void deny(Player player, String msg) {
        if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            sp.m_5661_((Component)Component.m_237113_((String)msg).m_130940_(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public void onHostileTick(LivingEvent.LivingTickEvent event) {
        Claim claim;
        LivingEntity entity = event.getEntity();
        Level level = entity.m_9236_();
        if (!level.m_5776_() && entity instanceof Enemy && entity.f_19797_ % 5 == 0 && (claim = ClaimManager.getInstance().getClaimAt(level, entity.m_20183_())) != null && claim.getFlags().burnHostiles) {
            EntityProtectionEvents.repelHostile(claim, entity);
        }
    }

    private static void repelHostile(Claim claim, LivingEntity mob) {
        double ex = mob.m_20185_();
        double ez = mob.m_20189_();
        int r = claim.getRadius();
        double cx = (double)claim.getX() + 0.5;
        double cz = (double)claim.getZ() + 0.5;
        double toWest = ex - (cx - (double)r);
        double toEast = cx + (double)r - ex;
        double toNorth = ez - (cz - (double)r);
        double toSouth = cz + (double)r - ez;
        double dirX = 0.0;
        double dirZ = 0.0;
        double min = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
        if (min == toWest) {
            dirX = -1.0;
        } else if (min == toEast) {
            dirX = 1.0;
        } else {
            dirZ = min == toNorth ? -1.0 : 1.0;
        }
        mob.m_20334_(dirX * 1.1, 0.42, dirZ * 1.1);
        mob.f_19812_ = true;
        mob.f_19864_ = true;
        mob.m_20254_(3);
        mob.f_19802_ = 0;
        mob.m_6469_(mob.m_269291_().m_269264_(), 3.0f);
        mob.getPersistentData().m_128356_(BARRIER_TAG, mob.m_9236_().m_46467_());
    }

    private static boolean killedByBarrier(LivingEntity entity, Level level) {
        long tick = entity.getPersistentData().m_128454_(BARRIER_TAG);
        if (tick > 0L && level.m_46467_() - tick <= 200L) {
            return true;
        }
        Claim claim = ClaimManager.getInstance().getClaimAt(level, entity.m_20183_());
        return claim != null && claim.getFlags().burnHostiles;
    }

    @SubscribeEvent
    public void onHostileDrops(LivingDropsEvent event) {
        DamageSource src;
        LivingEntity entity = event.getEntity();
        Level level = entity.m_9236_();
        if (!level.m_5776_() && entity instanceof Enemy && ((src = event.getSource()) == null || !(src.m_7639_() instanceof Player)) && EntityProtectionEvents.killedByBarrier(entity, level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onHostileXp(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Enemy && event.getAttackingPlayer() == null && EntityProtectionEvents.killedByBarrier(entity, entity.m_9236_())) {
            event.setCanceled(true);
        }
    }

    private static boolean isPlayerDrivenSpawn(MobSpawnType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case SPAWN_EGG, BUCKET, BREEDING, COMMAND, DISPENSER, CONVERSION -> true;
            default -> false;
        };
    }

    private static boolean isPassiveAnimal(Mob mob) {
        return mob instanceof Animal || mob instanceof WaterAnimal || mob instanceof AmbientCreature;
    }

    private static boolean isPlayerOwnedMob(Mob mob) {
        AgeableMob ageable;
        TamableAnimal tamable;
        if (mob.m_8077_() || mob.m_21532_()) {
            return true;
        }
        if (mob instanceof TamableAnimal && (tamable = (TamableAnimal)mob).m_21824_()) {
            return true;
        }
        if (mob instanceof IronGolem || mob instanceof SnowGolem) {
            return true;
        }
        return mob instanceof AgeableMob && (ageable = (AgeableMob)mob).m_6162_();
    }

    private static boolean shouldBlockSpawn(Level level, BlockPos pos, Mob mob, boolean includeMonsterFlag) {
        if (GlobalFlags.getInstance().globalNoMobSpawn) {
            return true;
        }
        Claim claim = ClaimManager.getInstance().getClaimAt(level, pos);
        if (claim == null) {
            return false;
        }
        ClaimFlags flags = claim.getFlags();
        if (flags.blockAllMobSpawn) {
            return true;
        }
        if (flags.blockPassiveMobSpawn && EntityProtectionEvents.isPassiveAnimal(mob)) {
            return true;
        }
        return includeMonsterFlag && mob instanceof Monster && (flags.blockMobSpawn || flags.publicMode);
    }

    @SubscribeEvent
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        BlockPos pos;
        Mob mob = event.getEntity();
        if (EntityProtectionEvents.isPlayerDrivenSpawn(event.getSpawnType())) {
            mob.getPersistentData().m_128379_(SPAWN_OK_TAG, true);
            return;
        }
        ServerLevel level = event.getLevel().m_6018_();
        if (EntityProtectionEvents.shouldBlockSpawn((Level)level, pos = BlockPos.m_274561_((double)event.getX(), (double)event.getY(), (double)event.getZ()), mob, true)) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        Claim claim;
        Mob mob;
        Level level;
        block8: {
            block7: {
                Entity entity;
                level = event.getLevel();
                if (level.m_5776_() || !((entity = event.getEntity()) instanceof Mob)) break block7;
                mob = (Mob)entity;
                if (mob.f_19797_ == 0) break block8;
            }
            return;
        }
        if (mob instanceof Monster && (claim = ClaimManager.getInstance().getClaimAt(level, mob.m_20183_())) != null && (claim.getFlags().blockMobSpawn || claim.getFlags().publicMode)) {
            event.setCanceled(true);
            return;
        }
        if (!(mob instanceof Enemy)) {
            return;
        }
        if (event.loadedFromDisk() || mob.getPersistentData().m_128471_(SPAWN_OK_TAG) || EntityProtectionEvents.isPlayerOwnedMob(mob)) {
            return;
        }
        if (EntityProtectionEvents.shouldBlockSpawn(level, mob.m_20183_(), mob, false)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.m_9236_();
        if (!level.m_5776_()) {
            DamageSource source = event.getSource();
            Entity attacker = source.m_7639_();
            Claim claim = ClaimManager.getInstance().getClaimAt(level, victim.m_20183_());
            if (victim instanceof Player && attacker instanceof Player && !GlobalFlags.getInstance().globalPVP) {
                EntityProtectionEvents.deny((Player)attacker, "[!] El PVP est\u00e1 desactivado en este servidor.");
                event.setCanceled(true);
            } else if (claim != null) {
                Player pAttacker;
                if (victim instanceof Player && attacker instanceof Player) {
                    pAttacker = (Player)attacker;
                    if (EntityProtectionEvents.isBypassing(pAttacker)) {
                        return;
                    }
                    // pvpAll ("todos se pueden atacar aqui") manda sobre blockPVP.
                    // Antes dos miembros de la zona podian pegarse entre ellos aunque el PVP
                    // estuviese desactivado, porque la condicion exigia que alguno NO fuese miembro.
                    if (!claim.getFlags().pvpAll && claim.getFlags().blockPVP) {
                        EntityProtectionEvents.deny(pAttacker, "[!] El PVP est\u00e1 desactivado en esta zona.");
                        event.setCanceled(true);
                        return;
                    }
                }
                if (victim instanceof Player && attacker instanceof LivingEntity && !(attacker instanceof Player) && (claim.getFlags().blockMobDamage || claim.getFlags().publicMode)) {
                    event.setCanceled(true);
                } else {
                    if (victim instanceof Animal && attacker instanceof Player && !claim.canModify(pAttacker = (Player)attacker) && !EntityProtectionEvents.isBypassing(pAttacker) && claim.getFlags().blockAnimalKilling) {
                        EntityProtectionEvents.deny(pAttacker, "[!] No puedes matar animales en esta zona.");
                        event.setCanceled(true);
                        return;
                    }
                    if (claim.getFlags().blockExplosions && source.m_269533_(DamageTypeTags.f_268415_)) {
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    /**
     * Golpear entidades dentro de una zona ajena.
     *
     * Antes bastaba con que uno cualquiera de cuatro flags estuviese activo (y todos vienen
     * activos por defecto) para negar el golpe a CUALQUIER entidad: un visitante no podia ni
     * defenderse de un zombi dentro de la zona de otro. Ahora se mira el flag que corresponde
     * al tipo de entidad, y los mobs hostiles nunca estan protegidos.
     */
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        Level level = player.m_9236_();
        if (level.m_5776_() || EntityProtectionEvents.isBypassing(player)) {
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof Enemy || target instanceof Player) {
            // hostiles: defensa propia siempre permitida.
            // jugadores: lo gestiona el control de PVP en onLivingHurt.
            return;
        }
        Claim claim = ClaimManager.getInstance().getClaimAt(level, target.m_20183_());
        if (claim == null || claim.canModify(player)) {
            return;
        }
        ClaimFlags flags = claim.getFlags();
        if (flags.blockAllInteractions) {
            EntityProtectionEvents.deny(player, "[!] No tienes ning\u00fan permiso de interacci\u00f3n en esta zona.");
            event.setCanceled(true);
            return;
        }
        if (target instanceof Animal || target instanceof WaterAnimal || target instanceof AmbientCreature) {
            if (flags.blockAnimalKilling) {
                EntityProtectionEvents.deny(player, "[!] No puedes matar animales en esta zona.");
                event.setCanceled(true);
            }
            return;
        }
        if (flags.blockEntityInteract) {
            EntityProtectionEvents.deny(player, "[!] No puedes da\u00f1ar entidades aqu\u00ed.");
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        if (!level.m_5776_() && !EntityProtectionEvents.isBypassing(player)) {
            Entity target = event.getTarget();
            Claim claim = ClaimManager.getInstance().getClaimAt(level, target.m_20183_());
            if (claim != null && !claim.canModify(player)) {
                ClaimFlags flags = claim.getFlags();
                // La cadena anterior estaba mal encadenada y terminaba negando siempre cualquier
                // interaccion con entidades, sin mirar los flags.
                if (flags.blockAllInteractions) {
                    EntityProtectionEvents.deny(player, "[!] No tienes ning\u00fan permiso de interacci\u00f3n en esta zona.");
                    event.setCanceled(true);
                } else if (target instanceof Container) {
                    if (flags.blockChestAccess) {
                        EntityProtectionEvents.deny(player, "[!] No puedes abrir este contenedor aqu\u00ed.");
                        event.setCanceled(true);
                    }
                } else if (flags.blockEntityInteract) {
                    EntityProtectionEvents.deny(player, "[!] No puedes interactuar con entidades aqu\u00ed.");
                    event.setCanceled(true);
                }
            }
        }
    }
}

