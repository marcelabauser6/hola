/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.exceptions.CommandSyntaxException
 *  com.mojang.brigadier.suggestion.SuggestionProvider
 *  com.mojang.brigadier.suggestion.SuggestionsBuilder
 *  net.minecraft.ChatFormatting
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.commands.SharedSuggestionProvider
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.level.ServerPlayer
 */
package com.claimblocks.command;

import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import com.claimblocks.gui.AdminPanelHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class ClaimAdminCommands {
    private static final SuggestionProvider<CommandSourceStack> GLOBAL_FLAGS = (ctx, builder) -> SharedSuggestionProvider.m_82967_((String[])new String[]{"globalPVP", "globalMobGriefing", "globalFireSpread", "globalNoMobSpawn"}, (SuggestionsBuilder)builder);
    private static final SuggestionProvider<CommandSourceStack> ON_OFF = (ctx, builder) -> SharedSuggestionProvider.m_82967_((String[])new String[]{"on", "off"}, (SuggestionsBuilder)builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_((String)"fsclaimadmin").requires(s -> s.m_6761_(2))).executes(ClaimAdminCommands::openPanel)).then(Commands.m_82127_((String)"bypass").executes(ClaimAdminCommands::toggleBypass))).then(Commands.m_82127_((String)"list").executes(ClaimAdminCommands::list))).then(Commands.m_82127_((String)"stats").executes(ClaimAdminCommands::stats))).then(Commands.m_82127_((String)"globalflag").then(Commands.m_82129_((String)"flag", (ArgumentType)StringArgumentType.word()).suggests(GLOBAL_FLAGS).then(Commands.m_82129_((String)"value", (ArgumentType)StringArgumentType.word()).suggests(ON_OFF).executes(ClaimAdminCommands::globalFlag)))));
    }

    private static int openPanel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
        AdminPanelHandler.open(p, 0);
        return 1;
    }

    private static int toggleBypass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
        boolean on = ClaimManager.getInstance().toggleBypass(p.m_20148_());
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)(on ? "\u2714 Bypass ACTIVADO (ignoras protecciones)." : "[i] Bypass desactivado.")).m_130940_(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<Claim> all = ClaimManager.getInstance().getAllClaims();
        if (all.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)"[i] No hay zonas en el servidor.").m_130940_(ChatFormatting.GRAY), false);
            return 0;
        }
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> {
            MutableComponent t = Component.m_237113_((String)("=== Zonas (" + all.size() + ") ===\n")).m_130940_(ChatFormatting.YELLOW);
            int shown = 0;
            for (Claim c : all) {
                if (shown++ >= 30) {
                    t.m_7220_((Component)Component.m_237113_((String)("... y " + (all.size() - 30) + " m\u00e1s")).m_130940_(ChatFormatting.GRAY));
                    break;
                }
                t.m_7220_((Component)Component.m_237113_((String)(c.getOwnerName() + " - " + c.sizeLabel() + " @ X=" + c.getX() + " Z=" + c.getZ() + "\n")).m_130940_(ChatFormatting.GRAY));
            }
            return t;
        }, false);
        return all.size();
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        List<Claim> all = ClaimManager.getInstance().getAllClaims();
        GlobalFlags gf = GlobalFlags.getInstance();
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)"=== Stats ClaimBlocks ===\n").m_130940_(ChatFormatting.YELLOW).m_7220_((Component)Component.m_237113_((String)("Total zonas: " + all.size() + "\n")).m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)("PVP global: " + (gf.globalPVP ? "ON" : "OFF") + "\n")).m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)("MobGriefing: " + (gf.globalMobGriefing ? "ON" : "OFF") + "\n")).m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)("FireSpread: " + (gf.globalFireSpread ? "ON" : "OFF") + "\n")).m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)("Sin spawn de mobs (global): " + (gf.globalNoMobSpawn ? "ON" : "OFF") + "\n")).m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)("Captura de chat por paquete: " + (ChatPromptRouter.isPacketCaptureActive() ? "ACTIVA" : "sin usar a\u00fan"))).m_130940_(ChatFormatting.DARK_GRAY)), false);
        return 1;
    }

    private static int globalFlag(CommandContext<CommandSourceStack> ctx) {
        String flag = StringArgumentType.getString(ctx, (String)"flag");
        String value = StringArgumentType.getString(ctx, (String)"value");
        if (!(flag.equals("globalPVP") || flag.equals("globalMobGriefing") || flag.equals("globalFireSpread") || flag.equals("globalNoMobSpawn"))) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)("[x] Flag desconocida: " + flag)).m_130940_(ChatFormatting.RED));
            return 0;
        }
        boolean on = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
        GlobalFlags.getInstance().set(flag, on, ((CommandSourceStack)ctx.getSource()).m_81377_());
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("\u2714 " + flag + " = " + (on ? "ON" : "OFF"))).m_130940_(ChatFormatting.GREEN), true);
        return 1;
    }
}

