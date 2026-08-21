/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.logging.LogUtils
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraftforge.common.MinecraftForge
 *  net.minecraftforge.event.CommandEvent
 *  net.minecraftforge.event.RegisterCommandsEvent
 *  net.minecraftforge.event.ServerChatEvent
 *  net.minecraftforge.event.TickEvent$Phase
 *  net.minecraftforge.event.TickEvent$ServerTickEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent
 *  net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent
 *  net.minecraftforge.event.server.ServerStartedEvent
 *  net.minecraftforge.event.server.ServerStoppingEvent
 *  net.minecraftforge.eventbus.api.IEventBus
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod
 *  net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
 *  net.minecraftforge.server.ServerLifecycleHooks
 *  org.slf4j.Logger
 */
package com.claimblocks;

import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.command.ClaimAdminCommands;
import com.claimblocks.command.ClaimCommands;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import com.claimblocks.event.BlockProtectionEvents;
import com.claimblocks.event.EntityProtectionEvents;
import com.claimblocks.event.PassiveEffectsManager;
import com.claimblocks.event.PlayerTracker;
import com.claimblocks.gui.AdminClaimSubMenuHandler;
import com.claimblocks.gui.ClaimMenuHandler;
import com.claimblocks.item.ClaimItems;
import com.claimblocks.net.ClaimBordersPacket;
import com.claimblocks.net.ClaimNetwork;
import com.claimblocks.render.ParticleBorder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(value="claimblocks")
public class ClaimBlocksMod {
    public static final String MOD_ID = "claimblocks";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static int particleCounter = 0;
    private static final java.util.Map<UUID, Integer> lastBorderHash = new java.util.concurrent.ConcurrentHashMap<UUID, Integer>();

    private static int borderHash(List<double[]> boxes) {
        int h = 1;
        for (double[] b : boxes) {
            h = 31 * h + java.util.Arrays.hashCode(b);
        }
        return h;
    }

    public ClaimBlocksMod() {
        LOGGER.info("[FantasticClaims] Fantastic Claims v7.8.0 (Forge 1.20.1)...");
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ClaimItems.register(modBus);
        ClaimNetwork.init();
        MinecraftForge.EVENT_BUS.register((Object)this);
        MinecraftForge.EVENT_BUS.register((Object)new BlockProtectionEvents());
        MinecraftForge.EVENT_BUS.register((Object)new EntityProtectionEvents());
        MinecraftForge.EVENT_BUS.register((Object)new PlayerTracker());
        LOGGER.info("[FantasticClaims] Eventos, items y red registrados.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ClaimCommands.register((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
        ClaimAdminCommands.register((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
        ClaimBlocksMod.registerMergeCommand((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
    }

    private static void registerMergeCommand(CommandDispatcher<CommandSourceStack> d) {
        d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_((String)"fsclaimmerge").then(Commands.m_82127_((String)"accept").then(Commands.m_82129_((String)"code", (ArgumentType)StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
            ClaimMenuHandler.acceptMerge(p, StringArgumentType.getString((CommandContext)ctx, (String)"code"));
            return 1;
        })))).then(Commands.m_82127_((String)"reject").then(Commands.m_82129_((String)"code", (ArgumentType)StringArgumentType.word()).executes(ctx -> {
            ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
            ClaimMenuHandler.rejectMerge(p, StringArgumentType.getString((CommandContext)ctx, (String)"code"));
            return 1;
        })))).then(Commands.m_82127_((String)"leave").executes(ctx -> {
            ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
            ClaimMenuHandler.leaveMerge(p);
            return 1;
        })));
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ClaimManager.getInstance().load(event.getServer());
        GlobalFlags.getInstance().load(event.getServer());
        LOGGER.info("[FantasticClaims] Datos cargados.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // saveNow: escritura sincrona, para que el apagado no deje datos en el hilo de disco
        ClaimManager.getInstance().saveNow();
        GlobalFlags.getInstance().save(event.getServer());
        LOGGER.info("[FantasticClaims] Datos guardados al apagar.");
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer)player;
            ClaimManager.getInstance().flushPendingTo(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        ClaimMenuHandler.clearPrompt(event.getEntity().m_20148_());
        lastBorderHash.remove(event.getEntity().m_20148_());
        AdminClaimSubMenuHandler.clearPendingTransfer(event.getEntity().m_20148_());
        ChatPromptRouter.onPlayerDisconnect(event.getEntity().m_20148_());
        PlayerTracker.onDisconnect(event.getEntity().m_20148_());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server;
        if (event.phase == TickEvent.Phase.END && (server = ServerLifecycleHooks.getCurrentServer()) != null) {
            PlayerTracker.tick(server);
            BlockProtectionEvents.tickFireSweep(server);
            PassiveEffectsManager.tick(server);
            if (++particleCounter % 4 == 0) {
                ClaimBlocksMod.renderClaimParticles(server);
            }
            if (particleCounter % 20 == 0) {
                ClaimBlocksMod.sendBorderPackets(server);
            }
        }
    }

    private static void sendBorderPackets(MinecraftServer server) {
        for (ServerLevel level : server.m_129785_()) {
            String dim = level.m_46472_().m_135782_().toString();
            for (ServerPlayer player : level.m_6907_()) {
                ArrayList<double[]> boxes = new ArrayList<double[]>();
                HashSet<UUID> doneClaims = new HashSet<UUID>();
                HashSet<UUID> doneGroups = new HashSet<UUID>();
                Claim here = ClaimManager.getInstance().getClaimAt((Level)level, player.m_20183_());
                if (here != null && here.getFlags().showBorder && here.canModify((Player)player)) {
                    ClaimBlocksMod.addBorder(boxes, here, player, dim, doneClaims, doneGroups);
                }
                for (Claim owned : ClaimManager.getInstance().getClaimsOf(player.m_20148_())) {
                    if (!owned.getWorld().equals(dim) || !owned.getFlags().showBorder || !ParticleBorder.withinRenderRange(player, owned)) continue;
                    ClaimBlocksMod.addBorder(boxes, owned, player, dim, doneClaims, doneGroups);
                }
                // Antes se enviaba el paquete de bordes a todos los jugadores cada segundo
                // aunque la lista fuese identica (o vacia). Ahora solo cuando cambia.
                int hash = ClaimBlocksMod.borderHash(boxes);
                Integer last = lastBorderHash.get(player.m_20148_());
                if (last != null && last == hash) continue;
                lastBorderHash.put(player.m_20148_(), hash);
                ClaimNetwork.sendTo(player, new ClaimBordersPacket(boxes));
            }
        }
    }

    private static void addBorder(ArrayList<double[]> boxes, Claim claim, ServerPlayer player, String dim, HashSet<UUID> doneClaims, HashSet<UUID> doneGroups) {
        if (claim.getGroupId() != null) {
            UUID gid = claim.getGroupId();
            if (doneGroups.contains(gid)) {
                return;
            }
            doneGroups.add(gid);
            ClaimBlocksMod.addGroupOutline(boxes, gid, player, dim);
        } else {
            if (doneClaims.contains(claim.getClaimId())) {
                return;
            }
            doneClaims.add(claim.getClaimId());
            boxes.add(ClaimBlocksMod.boxOf(claim));
        }
    }

    private static void addGroupOutline(ArrayList<double[]> boxes, UUID gid, ServerPlayer player, String dim) {
        ClaimManager mgr = ClaimManager.getInstance();
        Claim mother = mgr.getMotherClaim(gid);
        if (mother != null) {
            ArrayList<Claim> gc = new ArrayList<Claim>();
            for (Claim c : mgr.getGroupClaims(gid)) {
                if (!c.getWorld().equals(dim)) continue;
                gc.add(c);
            }
            if (!gc.isEmpty()) {
                double minY = mother.getY() - mother.getOwnHeight();
                double maxY = mother.getY() + mother.getOwnHeight() + 1;
                float cr = 1.0f;
                float cg = 1.0f;
                float cb = 1.0f;
                if (mother.getTier() != null) {
                    cr = mother.getTier().r;
                    cg = mother.getTier().g;
                    cb = mother.getTier().b;
                }
                int n = gc.size();
                int[] rx1 = new int[n];
                int[] rx2 = new int[n];
                int[] rz1 = new int[n];
                int[] rz2 = new int[n];
                TreeSet<Integer> xsSet = new TreeSet<Integer>();
                TreeSet<Integer> zsSet = new TreeSet<Integer>();
                for (int i = 0; i < n; ++i) {
                    Claim cx = (Claim)gc.get(i);
                    int r = cx.getRadius();
                    rx1[i] = cx.getX() - r;
                    rx2[i] = cx.getX() + r + 1;
                    rz1[i] = cx.getZ() - r;
                    rz2[i] = cx.getZ() + r + 1;
                    xsSet.add(rx1[i]);
                    xsSet.add(rx2[i]);
                    zsSet.add(rz1[i]);
                    zsSet.add(rz2[i]);
                }
                Integer[] xs = xsSet.toArray(new Integer[0]);
                Integer[] zs = zsSet.toArray(new Integer[0]);
                int nx = xs.length;
                int nz = zs.length;
                if (nx >= 2 && nz >= 2) {
                    int i;
                    boolean[][] cov = new boolean[nx - 1][nz - 1];
                    for (i = 0; i < nx - 1; ++i) {
                        double cxm = (double)(xs[i] + xs[i + 1]) / 2.0;
                        for (int j = 0; j < nz - 1; ++j) {
                            double czm = (double)(zs[j] + zs[j + 1]) / 2.0;
                            boolean cx = false;
                            for (int k = 0; k < n; ++k) {
                                if (!(cxm >= (double)rx1[k]) || !(cxm < (double)rx2[k]) || !(czm >= (double)rz1[k]) || !(czm < (double)rz2[k])) continue;
                                cx = true;
                                break;
                            }
                            cov[i][j] = cx;
                        }
                    }
                    for (i = 0; i < nx; ++i) {
                        int j = 0;
                        while (j < nz - 1) {
                            boolean right;
                            boolean left = i > 0 && cov[i - 1][j];
                            boolean bl = right = i < nx - 1 && cov[i][j];
                            if (left != right) {
                                int j0 = j;
                                while (j < nz - 1 && (i > 0 && cov[i - 1][j]) != (i < nx - 1 && cov[i][j])) {
                                    ++j;
                                }
                                boxes.add(new double[]{(double)xs[i].intValue() - 0.03, minY, zs[j0].intValue(), (double)xs[i].intValue() + 0.03, maxY, zs[j].intValue(), cr, cg, cb});
                                continue;
                            }
                            ++j;
                        }
                    }
                    for (int j = 0; j < nz; ++j) {
                        int i2 = 0;
                        while (i2 < nx - 1) {
                            boolean above;
                            boolean below = j > 0 && cov[i2][j - 1];
                            boolean bl = above = j < nz - 1 && cov[i2][j];
                            if (below != above) {
                                int i0 = i2;
                                while (i2 < nx - 1 && (j > 0 && cov[i2][j - 1]) != (j < nz - 1 && cov[i2][j])) {
                                    ++i2;
                                }
                                boxes.add(new double[]{xs[i0].intValue(), minY, (double)zs[j].intValue() - 0.03, xs[i2].intValue(), maxY, (double)zs[j].intValue() + 0.03, cr, cg, cb});
                                continue;
                            }
                            ++i2;
                        }
                    }
                }
            }
        }
    }

    private static boolean covered(List<Claim> gc, int x, int z) {
        for (Claim c : gc) {
            if (Math.abs(x - c.getX()) > c.getRadius() || Math.abs(z - c.getZ()) > c.getRadius()) continue;
            return true;
        }
        return false;
    }

    private static double[] boxOf(Claim claim) {
        int r = claim.getRadius();
        int h = claim.getHeight();
        float cr = 1.0f;
        float cg = 1.0f;
        float cb = 1.0f;
        if (claim.getTier() != null) {
            cr = claim.getTier().r;
            cg = claim.getTier().g;
            cb = claim.getTier().b;
        }
        return new double[]{claim.getX() - r, claim.getY() - h, claim.getZ() - r, claim.getX() + r + 1, claim.getY() + h + 1, claim.getZ() + r + 1, cr, cg, cb};
    }

    private static void renderClaimParticles(MinecraftServer server) {
        for (ServerLevel level : server.m_129785_()) {
            String dim = level.m_46472_().m_135782_().toString();
            for (ServerPlayer player : level.m_6907_()) {
                HashSet<UUID> rendered = new HashSet<UUID>();
                Claim here = ClaimManager.getInstance().getClaimAt((Level)level, player.m_20183_());
                if (here != null && here.getFlags().showParticles && here.canModify((Player)player)) {
                    ParticleBorder.fillClaim(level, player, here);
                    rendered.add(here.getClaimId());
                }
                for (Claim owned : ClaimManager.getInstance().getClaimsOf(player.m_20148_())) {
                    if (rendered.contains(owned.getClaimId()) || !owned.getFlags().showParticles || !owned.getWorld().equals(dim) || !ParticleBorder.withinRenderRange(player, owned)) continue;
                    ParticleBorder.fillClaim(level, player, owned);
                    rendered.add(owned.getClaimId());
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        ClaimMenuHandler.handleChat(event);
    }

    @SubscribeEvent
    public void onCommandEvent(CommandEvent var1) {
    }
}

