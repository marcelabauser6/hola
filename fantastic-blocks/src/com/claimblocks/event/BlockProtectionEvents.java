/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.tags.BlockTags
 *  net.minecraft.world.Container
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.BucketItem
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.AnvilBlock
 *  net.minecraft.world.level.block.BarrelBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.ChestBlock
 *  net.minecraft.world.level.block.DispenserBlock
 *  net.minecraft.world.level.block.HopperBlock
 *  net.minecraft.world.level.block.ShulkerBoxBlock
 *  net.minecraft.world.level.block.SignBlock
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.piston.PistonStructureResolver
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraftforge.event.entity.EntityTeleportEvent$EnderPearl
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickBlock
 *  net.minecraftforge.event.entity.player.PlayerInteractEvent$RightClickItem
 *  net.minecraftforge.event.level.BlockEvent$BreakEvent
 *  net.minecraftforge.event.level.BlockEvent$EntityPlaceEvent
 *  net.minecraftforge.event.level.BlockEvent$FarmlandTrampleEvent
 *  net.minecraftforge.event.level.ExplosionEvent$Detonate
 *  net.minecraftforge.event.level.PistonEvent$Pre
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 */
package com.claimblocks.event;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.gui.ClaimMenuHandler;
import com.claimblocks.util.DecorationProtection;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BlockProtectionEvents {
    private static int fireSweepCounter = 0;

    private static boolean isBypassing(Player var0) {
        return var0.m_20310_(2) && ClaimManager.getInstance().isBypassing(var0.m_20148_());
    }

    /**
     * Devuelve true si hay que bloquear la accion a este jugador.
     *
     * Antes esta funcion terminaba en "return publicMode ? true : true", es decir, negaba
     * cualquier accion a cualquier visitante e ignoraba por completo el flag: encender o apagar
     * los botones del menu daba exactamente el mismo resultado. Ahora manda el flag.
     */
    private static boolean denyForVisitor(Claim claim, Player player, boolean flagEnabled) {
        if (claim.canModify(player)) {
            return false;
        }
        if (BlockProtectionEvents.isBypassing(player)) {
            return false;
        }
        return flagEnabled;
    }

    private static void deny(Player var0, String var1) {
        if (var0 instanceof ServerPlayer) {
            ServerPlayer var2 = (ServerPlayer)var0;
            if (!var1.isEmpty()) {
                var2.m_5661_((Component)Component.m_237113_((String)var1).m_130940_(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent var1) {
        LevelAccessor levelAccessor = var1.getLevel();
        if (levelAccessor instanceof Level) {
            Player var4;
            Level var3 = (Level)levelAccessor;
            if (!var3.f_46443_ && (var4 = var1.getPlayer()) != null && !BlockProtectionEvents.isBypassing(var4)) {
                ClaimTier var8;
                BlockPos var5 = var1.getPos();
                BlockState var6 = var1.getState();
                Claim var7 = ClaimManager.getInstance().getClaimByCenter(var3, var5);
                if (var7 != null && (var8 = var7.getTier()) != null && ClaimBlocks.isClaimConcreteForTier(var6.m_60734_(), var8)) {
                    if (!var7.isOwner(var4) && !var4.m_20310_(2)) {
                        BlockProtectionEvents.deny(var4, "[!] Solo el due\u00f1o puede romper esta protecci\u00f3n.");
                        var1.setCanceled(true);
                    } else {
                        ClaimManager.getInstance().removeClaim(var3, var5);
                        if (!var4.m_150110_().f_35937_) {
                            ItemStack var9 = ClaimBlocks.createTierItem(var8, 1);
                            if (!var4.m_150109_().m_36054_(var9)) {
                                var4.m_36176_(var9, false);
                            }
                        }
                        var3.m_5594_(null, var5, SoundEvents.f_144243_, SoundSource.BLOCKS, 2.0f, 1.0f);
                        if (var4 instanceof ServerPlayer) {
                            ((ServerPlayer)var4).m_5661_((Component)Component.m_237113_((String)"\u2714 Zona eliminada. Protecci\u00f3n devuelta a tu inventario.").m_130940_(ChatFormatting.GREEN), false);
                        }
                        var3.m_46597_(var5, Blocks.f_50016_.m_49966_());
                        var1.setCanceled(false);
                    }
                    return;
                }
                Claim var10 = ClaimManager.getInstance().getClaimAt(var3, var5);
                if (var10 != null && !var10.canModify(var4)) {
                    if (!var6.m_204336_(BlockTags.f_13106_) || !var10.getFlags().publicMode && !var10.getFlags().blockTreeChopping) {
                        if (!BlockProtectionEvents.isMatureCrop(var6) || !var10.getFlags().publicMode && !var10.getFlags().blockCropHarvest) {
                            if (BlockProtectionEvents.denyForVisitor(var10, var4, var10.getFlags().blockBreaking || var10.getFlags().publicMode)) {
                                BlockProtectionEvents.deny(var4, "[!] No puedes romper bloques aqu\u00ed.");
                                var1.setCanceled(true);
                            }
                        } else {
                            BlockProtectionEvents.deny(var4, "[!] No puedes cosechar cultivos aqu\u00ed.");
                            var1.setCanceled(true);
                        }
                    } else {
                        BlockProtectionEvents.deny(var4, "[!] No puedes talar \u00e1rboles en esta zona.");
                        var1.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent var1) {
        LevelAccessor levelAccessor = var1.getLevel();
        if (levelAccessor instanceof Level) {
            Claim var6;
            Player var5;
            Entity entity;
            Level var3 = (Level)levelAccessor;
            if (!var3.f_46443_ && (entity = var1.getEntity()) instanceof Player && !BlockProtectionEvents.isBypassing(var5 = (Player)entity) && (var6 = ClaimManager.getInstance().getClaimAt(var3, var1.getPos())) != null && BlockProtectionEvents.denyForVisitor(var6, var5, var6.getFlags().blockBuilding || var6.getFlags().publicMode)) {
                BlockProtectionEvents.deny(var5, "[!] No puedes construir aqu\u00ed.");
                var1.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock var1) {
        Level var2 = var1.getLevel();
        if (!var2.f_46443_) {
            ClaimTier var9;
            Player var3 = var1.getEntity();
            BlockPos var4 = var1.getPos();
            ItemStack var5 = var1.getItemStack();
            Claim var6 = ClaimManager.getInstance().getClaimByCenter(var2, var4);
            if (var6 != null) {
                ClaimTier var7 = var6.getTier();
                BlockState var8 = var2.m_8055_(var4);
                if (var7 != null && ClaimBlocks.isClaimConcreteForTier(var8.m_60734_(), var7) && !var3.m_6144_()) {
                    if (var1.getHand() == InteractionHand.MAIN_HAND) {
                        if (!var6.isOwner(var3) && !var3.m_20310_(2)) {
                            BlockProtectionEvents.deny(var3, "[x] Solo el due\u00f1o puede administrar esta zona.");
                        } else if (var3 instanceof ServerPlayer) {
                            ClaimMenuHandler.open((ServerPlayer)var3, var6, 0);
                        }
                    }
                    var1.setCanceled(true);
                    var1.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }
            }
            if ((var9 = ClaimBlocks.readTier(var5)) != null && !BlockProtectionEvents.isBypassing(var3)) {
                InteractionResult var11 = this.tryPlaceClaim(var3, var2, var1.getHand(), var1.getFace(), var4, var5, var9);
                var1.setCanceled(true);
                var1.setCancellationResult(var11);
            } else {
                InteractionResult var10 = this.regularChecks(var3, var2, var4, var1.getFace(), var5);
                if (var10 != InteractionResult.PASS) {
                    var1.setCanceled(true);
                    var1.setCancellationResult(var10);
                }
            }
        }
    }

    private InteractionResult tryPlaceClaim(Player var1, Level var2, InteractionHand var3, Direction var4, BlockPos var5, ItemStack var6, ClaimTier var7) {
        int var13;
        BlockState var8 = var2.m_8055_(var5);
        BlockPos var9 = var8.m_247087_() ? var5 : var5.m_121945_(var4);
        BlockState var10 = var2.m_8055_(var9);
        if (!var10.m_60795_() && !var10.m_247087_()) {
            return InteractionResult.PASS;
        }
        ClaimManager var11 = ClaimManager.getInstance();
        Claim var12 = var11.getClaimAt(var2, var9);
        if (var12 != null && !var12.canModify(var1) && !var1.m_20310_(2)) {
            BlockProtectionEvents.deny(var1, "[x] No puedes construir en esta zona.");
            return InteractionResult.SUCCESS;
        }
        List<Claim> overlaps = var11.overlappingClaims(var2, var9, var7.radius, var7.height);
        UUID joinGroup = null;
        if (!overlaps.isEmpty()) {
            UUID gid = null;
            boolean sameGroup = true;
            for (Claim oc : overlaps) {
                if (oc.getGroupId() == null) {
                    sameGroup = false;
                    break;
                }
                if (gid == null) {
                    gid = oc.getGroupId();
                    continue;
                }
                if (gid.equals(oc.getGroupId())) continue;
                sameGroup = false;
                break;
            }
            if (!sameGroup || gid == null || !var11.isRegistered(gid, var1.m_20148_())) {
                BlockProtectionEvents.deny(var1, "[x] Esta zona se solapar\u00eda con otra existente.");
                return InteractionResult.SUCCESS;
            }
            joinGroup = gid;
        }
        if ((var13 = ClaimManager.getMaxClaimsPerPlayer()) > 0 && !var1.m_20310_(2) && var11.getClaimsOf(var1.m_20148_()).size() >= var13) {
            BlockProtectionEvents.deny(var1, "[x] Has alcanzado el l\u00edmite de zonas (" + var13 + ").");
            return InteractionResult.SUCCESS;
        }
        Block var14 = ClaimBlocks.blockForTier(var7);
        var2.m_46597_(var9, var14.m_49966_());
        var2.m_5594_(null, var9, SoundEvents.f_144048_, SoundSource.BLOCKS, 0.8f, 1.2f);
        Claim newClaim = var11.createClaim(var2, var9, var1, var7);
        if (joinGroup != null && newClaim != null) {
            var11.joinClaimToGroup(newClaim, joinGroup);
        }
        if (!var1.m_150110_().f_35937_) {
            var6.m_41774_(1);
        }
        var1.m_6674_(var3);
        if (var1 instanceof ServerPlayer) {
            if (joinGroup != null) {
                ClaimGroup jg = var11.getGroup(joinGroup);
                String gname = jg != null ? jg.getName() : "grupo";
                ((ServerPlayer)var1).m_5661_((Component)Component.m_237113_((String)("\u2714 Piedra unida a la zona \"" + gname + "\".")).m_130940_(ChatFormatting.GREEN), false);
            } else {
                ((ServerPlayer)var1).m_5661_((Component)Component.m_237113_((String)("\u2714 Zona creada: " + var7.label() + " bloques | Altura: +/-" + var7.height)).m_130940_(ChatFormatting.GREEN), false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Chequeos de interaccion con clic derecho.
     *
     * Cambios respecto a la version anterior:
     *  - la zona se resuelve UNA sola vez (antes se llamaba a getClaimAt hasta 8 veces por clic,
     *    y getClaimAt recorre linealmente todas las zonas del mundo).
     *  - cada chequeo consulta su propio flag (cofres, yunques, letreros, puertas, fluidos...).
     *  - se elimina el "catch-all" final que negaba toda interaccion a cualquier visitante,
     *    que era lo que hacia inutiles los flags del menu y rompia el modo publico.
     */
    private InteractionResult regularChecks(Player player, Level level, BlockPos pos, Direction face, ItemStack held) {
        if (BlockProtectionEvents.isBypassing(player)) {
            return InteractionResult.PASS;
        }
        ClaimManager mgr = ClaimManager.getInstance();
        Claim claim = mgr.getClaimAt(level, pos);
        boolean visitor = claim != null && !claim.canModify(player);
        // un cubo de agua/lava se coloca en el bloque de al lado, que puede pertenecer a otra zona
        if (held.m_41720_() instanceof BucketItem) {
            Claim target = mgr.getClaimAt(level, pos.m_121945_(face));
            if (target != null && !target.canModify(player) && target.getFlags().blockFluids) {
                BlockProtectionEvents.deny(player, "[!] No puedes colocar fluidos aqu\u00ed.");
                return InteractionResult.FAIL;
            }
        }
        if (!visitor) {
            return InteractionResult.PASS;
        }
        ClaimFlags flags = claim.getFlags();
        if (flags.blockAllInteractions) {
            BlockProtectionEvents.deny(player, "[!] No tienes ning\u00fan permiso de interacci\u00f3n en esta zona.");
            return InteractionResult.FAIL;
        }
        BlockState state = level.m_8055_(pos);
        Block block = state.m_60734_();
        if (flags.blockChestAccess && BlockProtectionEvents.isContainer(level, pos)) {
            BlockProtectionEvents.deny(player, "[!] No puedes abrir contenedores aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (flags.blockAnvilUse && block instanceof AnvilBlock) {
            BlockProtectionEvents.deny(player, "[!] No puedes usar yunques aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (flags.blockSignEditing && block instanceof SignBlock) {
            BlockProtectionEvents.deny(player, "[!] No puedes editar letreros aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (flags.blockDoorsAccess && BlockProtectionEvents.isDoorLike(state)) {
            BlockProtectionEvents.deny(player, "[!] No puedes usar puertas, botones ni placas aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        if (flags.blockEntityInteract && BlockProtectionEvents.isInteractiveBlock(state)) {
            BlockProtectionEvents.deny(player, "[!] No puedes interactuar aqu\u00ed.");
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    /**
     * Uso de items en el aire (comer, arco, perlas, etc.).
     *
     * Antes se bloqueaba a cualquier visitante sin mirar el flag blockItemUse: un jugador dentro
     * de una zona ajena no podia ni comer ni usar nada.
     */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem var1) {
        Claim var5;
        Player var3;
        Level var2 = var1.getLevel();
        if (!(var2.f_46443_ || BlockProtectionEvents.isBypassing(var3 = var1.getEntity()) || ClaimBlocks.readTierId(var1.getItemStack()) != null || (var5 = ClaimManager.getInstance().getClaimAt(var2, var3.m_20183_())) == null || var5.canModify(var3) || !var5.getFlags().blockItemUse)) {
            BlockProtectionEvents.deny(var3, "[!] No puedes usar items en esta zona.");
            var1.setCanceled(true);
            var1.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onTrample(BlockEvent.FarmlandTrampleEvent var1) {
        LevelAccessor levelAccessor = var1.getLevel();
        if (levelAccessor instanceof Level) {
            Claim var4;
            Level var3 = (Level)levelAccessor;
            if (!var3.f_46443_ && (var4 = ClaimManager.getInstance().getClaimAt(var3, var1.getPos())) != null && (var4.getFlags().blockTrampling || var4.getFlags().publicMode)) {
                var1.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate var1) {
        Level var2 = var1.getLevel();
        if (!var2.f_46443_) {
            var1.getAffectedBlocks().removeIf(var1x -> {
                Claim var2x = ClaimManager.getInstance().getClaimAt(var2, (BlockPos)var1x);
                return var2x != null && (var2x.getFlags().blockExplosions || var2x.getFlags().publicMode);
            });
            // Antes solo se filtraban los BLOQUES afectados: una TNT o un creeper seguia
            // destruyendo cuadros, marcos y soportes de armadura dentro de la zona, y el item
            // caia al suelo. Ahora la decoracion tambien queda fuera de la explosion.
            var1.getAffectedEntities().removeIf(entity -> {
                if (!DecorationProtection.isDecoration((Entity)entity)) {
                    return false;
                }
                Claim claim = DecorationProtection.claimFor(var2, (Entity)entity);
                return claim != null && (claim.getFlags().blockExplosions || claim.getFlags().publicMode);
            });
        }
    }

    @SubscribeEvent
    public void onPiston(PistonEvent.Pre var1) {
        LevelAccessor levelAccessor = var1.getLevel();
        if (levelAccessor instanceof Level) {
            Level var3 = (Level)levelAccessor;
            if (!var3.f_46443_) {
                BlockPos var4 = var1.getPos();
                Direction var5 = var1.getDirection();
                Claim var6 = ClaimManager.getInstance().getClaimAt(var3, var4);
                PistonStructureResolver var7 = var1.getStructureHelper();
                if (var7 != null && var7.m_60422_()) {
                    for (BlockPos var11 : var7.m_60436_()) {
                        if (!BlockProtectionEvents.crossClaimBlocked(var3, var6, var11, var11.m_121945_(var5))) continue;
                        var1.setCanceled(true);
                        return;
                    }
                    for (BlockPos var12 : var7.m_60437_()) {
                        if (!BlockProtectionEvents.crossClaimBlocked(var3, var6, var12, var12)) continue;
                        var1.setCanceled(true);
                        return;
                    }
                } else {
                    BlockPos var8 = var4.m_121945_(var5);
                    if (BlockProtectionEvents.crossClaimBlocked(var3, var6, var8, var8.m_121945_(var5))) {
                        var1.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onEnderPearl(EntityTeleportEvent.EnderPearl var1) {
        ServerPlayer var2 = var1.getPlayer();
        if (var2 != null) {
            Level var3 = var2.m_9236_();
            BlockPos var4 = BlockPos.m_274561_((double)var1.getTargetX(), (double)var1.getTargetY(), (double)var1.getTargetZ());
            Claim var5 = ClaimManager.getInstance().getClaimAt(var3, var4);
            if (var5 != null && !var5.canModify((Player)var2) && !BlockProtectionEvents.isBypassing((Player)var2) && (var5.getFlags().blockEnderPearl || var5.getFlags().publicMode)) {
                var1.setCanceled(true);
                BlockProtectionEvents.deny((Player)var2, "[!] No puedes teletransportarte a esta zona.");
            }
        }
    }

    private static boolean crossClaimBlocked(Level var0, Claim var1, BlockPos var2, BlockPos var3) {
        Claim var5;
        Claim var4 = ClaimManager.getInstance().getClaimAt(var0, var2);
        return BlockProtectionEvents.sameClaim(var4, var5 = ClaimManager.getInstance().getClaimAt(var0, var3)) && BlockProtectionEvents.sameClaim(var1, var4) ? false : BlockProtectionEvents.protectsBuilding(var4) || BlockProtectionEvents.protectsBuilding(var5) || BlockProtectionEvents.protectsBuilding(var1);
    }

    private static boolean sameClaim(Claim var0, Claim var1) {
        if (var0 == null && var1 == null) {
            return true;
        }
        return var0 != null && var1 != null ? var0.getClaimId().equals(var1.getClaimId()) : false;
    }

    private static boolean protectsBuilding(Claim var0) {
        return var0 == null ? false : var0.getFlags().publicMode || var0.getFlags().blockBuilding;
    }

    public static boolean isContainer(Level var0, BlockPos var1) {
        BlockState var2 = var0.m_8055_(var1);
        Block var3 = var2.m_60734_();
        if (!(var3 instanceof ChestBlock || var3 instanceof BarrelBlock || var3 instanceof ShulkerBoxBlock || var3 instanceof DispenserBlock || var3 instanceof HopperBlock)) {
            BlockEntity var4 = var0.m_7702_(var1);
            return var4 instanceof Container;
        }
        return true;
    }

    /**
     * Antes devolvia siempre false, por lo que el flag "Intrusos no cosechan cultivos" nunca
     * llegaba a aplicarse por su propia rama.
     */
    private static boolean isMatureCrop(BlockState var0) {
        Block block = var0.m_60734_();
        if (block instanceof CropBlock) {
            CropBlock crop = (CropBlock)block;
            return crop.m_52307_(var0);
        }
        return false;
    }

    private static boolean isDoorLike(BlockState var0) {
        if (var0.m_204336_(BlockTags.f_13103_)) {
            return true;
        }
        if (var0.m_204336_(BlockTags.f_13036_)) {
            return true;
        }
        if (var0.m_204336_(BlockTags.f_13055_)) {
            return true;
        }
        return var0.m_204336_(BlockTags.f_13093_) ? true : var0.m_60734_() == Blocks.f_50164_;
    }

    private static boolean isInteractiveBlock(BlockState var0) {
        Block var1 = var0.m_60734_();
        return var1 == Blocks.f_50091_ || var1 == Blocks.f_50201_ || var1 == Blocks.f_50623_ || var1 == Blocks.f_50255_;
    }

    public static void tickFireSweep(MinecraftServer var0) {
        if (++fireSweepCounter % 40 == 0) {
            for (ServerLevel var1 : var0.m_129785_()) {
                for (Claim var3 : ClaimManager.getInstance().getClaimsInWorld(var1.m_46472_().m_135782_().toString())) {
                    if (!var3.getFlags().blockFire && !var3.getFlags().publicMode) continue;
                    for (ServerPlayer var5 : var1.m_6907_()) {
                        if (!var3.contains(var5.m_20183_())) continue;
                        BlockProtectionEvents.extinguishAround(var1, var5.m_20183_(), var3);
                    }
                }
            }
        }
    }

    private static void extinguishAround(ServerLevel var0, BlockPos var1, Claim var2) {
        int var3 = 6;
        BlockPos.MutableBlockPos var4 = new BlockPos.MutableBlockPos();
        for (int var5 = -var3; var5 <= var3; ++var5) {
            for (int var6 = -var3; var6 <= var3; ++var6) {
                for (int var7 = -var3; var7 <= var3; ++var7) {
                    Block var8;
                    var4.m_122178_(var1.m_123341_() + var5, var1.m_123342_() + var6, var1.m_123343_() + var7);
                    if (!var2.contains((BlockPos)var4) || (var8 = var0.m_8055_((BlockPos)var4).m_60734_()) != Blocks.f_50083_ && var8 != Blocks.f_50084_) continue;
                    var0.m_7731_(var4.m_7949_(), Blocks.f_50016_.m_49966_(), 3);
                }
            }
        }
    }
}

