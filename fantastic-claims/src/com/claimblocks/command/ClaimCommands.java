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
 *  net.minecraft.commands.arguments.EntityArgument
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 */
package com.claimblocks.command;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.gui.ClaimMenuHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ClaimCommands {
    private static final SuggestionProvider<CommandSourceStack> CLAIMSTONE_IDS = (ctx, builder) -> {
        String[] ids = new String[ClaimTier.VALUES.length];
        for (int i = 0; i < ClaimTier.VALUES.length; ++i) {
            ids[i] = ClaimTier.VALUES[i].id;
        }
        return SharedSuggestionProvider.m_82967_((String[])ids, (SuggestionsBuilder)builder);
    };
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> SharedSuggestionProvider.m_82970_((Iterable)((CommandSourceStack)ctx.getSource()).m_5982_(), (SuggestionsBuilder)builder);
    private static final SuggestionProvider<CommandSourceStack> MEMBER_NAMES = (ctx, builder) -> {
        Claim c;
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_230896_();
        if (p != null && (c = ClaimManager.getInstance().getClaimAt(p.m_9236_(), p.m_20183_())) != null) {
            return SharedSuggestionProvider.m_82970_(c.getMemberNames(), (SuggestionsBuilder)builder);
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_((String)"fsclaim").executes(ClaimCommands::help)).then(Commands.m_82127_((String)"help").executes(ClaimCommands::help))).then(Commands.m_82127_((String)"menu").executes(ClaimCommands::menu))).then(Commands.m_82127_((String)"info").executes(ClaimCommands::info))).then(Commands.m_82127_((String)"list").executes(ClaimCommands::list))).then(Commands.m_82127_((String)"remove").executes(ClaimCommands::remove))).then(((LiteralArgumentBuilder)Commands.m_82127_((String)"ban").requires(s -> s.m_6761_(2))).then(Commands.m_82129_((String)"jugador", (ArgumentType)EntityArgument.m_91466_()).executes(ClaimCommands::ban)))).then(((LiteralArgumentBuilder)Commands.m_82127_((String)"unban").requires(s -> s.m_6761_(2))).then(Commands.m_82129_((String)"jugador", (ArgumentType)EntityArgument.m_91466_()).executes(ClaimCommands::unban)))).then(((LiteralArgumentBuilder)Commands.m_82127_((String)"transfer").requires(s -> s.m_6761_(2))).then(Commands.m_82129_((String)"jugador", (ArgumentType)EntityArgument.m_91466_()).executes(ClaimCommands::transfer)))).then(((LiteralArgumentBuilder)Commands.m_82127_((String)"removemember").requires(s -> s.m_6761_(2))).then(Commands.m_82129_((String)"jugador", (ArgumentType)EntityArgument.m_91466_()).executes(ClaimCommands::removeMember))).then(Commands.m_82127_((String)"addmember").then(Commands.m_82129_((String)"jugador", (ArgumentType)StringArgumentType.word()).suggests(ONLINE_PLAYERS).executes(ClaimCommands::addMember))).then(Commands.m_82127_((String)"delmember").then(Commands.m_82129_((String)"jugador", (ArgumentType)StringArgumentType.word()).suggests(MEMBER_NAMES).executes(ClaimCommands::delMember))).then(Commands.m_82127_((String)"members").executes(ClaimCommands::members))).then(((LiteralArgumentBuilder)Commands.m_82127_((String)"give").requires(s -> s.m_6761_(2))).then(Commands.m_82129_((String)"jugador", (ArgumentType)EntityArgument.m_91470_()).then(Commands.m_82129_((String)"id", (ArgumentType)StringArgumentType.word()).suggests(CLAIMSTONE_IDS).executes(ClaimCommands::give))))).then(((LiteralArgumentBuilder)Commands.m_82127_((String)"clear").requires(s -> s.m_6761_(2))).then(Commands.m_82129_((String)"jugador", (ArgumentType)EntityArgument.m_91466_()).executes(ClaimCommands::clear))));
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        boolean isOp = ((CommandSourceStack)ctx.getSource()).m_6761_(2);
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> {
            MutableComponent t = Component.m_237113_((String)"=== Fantastic Claims ===\n").m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD}).m_7220_((Component)Component.m_237113_((String)"/fsclaim menu  ").m_130940_(ChatFormatting.AQUA)).m_7220_((Component)Component.m_237113_((String)"- abre el menu de la zona\n").m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)"/fsclaim info  ").m_130940_(ChatFormatting.AQUA)).m_7220_((Component)Component.m_237113_((String)"- info de la zona\n").m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)"/fsclaim list  ").m_130940_(ChatFormatting.AQUA)).m_7220_((Component)Component.m_237113_((String)"- lista tus zonas\n").m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)"/fsclaim remove  ").m_130940_(ChatFormatting.AQUA)).m_7220_((Component)Component.m_237113_((String)"- borra tu zona actual\n").m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)"/fsclaim addmember <jugador>  ").m_130940_(ChatFormatting.AQUA)).m_7220_((Component)Component.m_237113_((String)"- a\u00f1ade un miembro (aunque est\u00e9 offline)\n").m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)"/fsclaim delmember <jugador>  ").m_130940_(ChatFormatting.AQUA)).m_7220_((Component)Component.m_237113_((String)"- quita un miembro\n").m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)"/fsclaim members  ").m_130940_(ChatFormatting.AQUA)).m_7220_((Component)Component.m_237113_((String)"- lista los miembros de la zona\n").m_130940_(ChatFormatting.GRAY));
            if (isOp) {
                t.m_7220_((Component)Component.m_237113_((String)"\n--- Solo Operadores ---\n").m_130940_(ChatFormatting.RED)).m_7220_((Component)Component.m_237113_((String)"/fsclaim give <jugador> <tier>\n").m_130940_(ChatFormatting.YELLOW)).m_7220_((Component)Component.m_237113_((String)"/fsclaim clear <jugador>\n").m_130940_(ChatFormatting.YELLOW)).m_7220_((Component)Component.m_237113_((String)"/fsclaim ban|unban <jugador>\n").m_130940_(ChatFormatting.YELLOW)).m_7220_((Component)Component.m_237113_((String)"/fsclaim transfer <jugador>\n").m_130940_(ChatFormatting.YELLOW)).m_7220_((Component)Component.m_237113_((String)"/fsclaim removemember <jugador>\n").m_130940_(ChatFormatting.YELLOW)).m_7220_((Component)Component.m_237113_((String)"/fsclaimadmin").m_130940_(ChatFormatting.YELLOW));
            }
            return t;
        }, false);
        return 1;
    }

    private static int menu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
        Claim c = ClaimManager.getInstance().getClaimAt(p.m_9236_(), p.m_20183_());
        if (c == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona protegida.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (!c.isOwner((Player)p) && !p.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] Solo el due\u00f1o puede abrir el menu.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        ClaimMenuHandler.open(p, c, 0);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
        Claim c = ClaimManager.getInstance().getClaimAt(p.m_9236_(), p.m_20183_());
        if (c == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona protegida.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("=== Zona " + c.sizeLabel() + " ===\n")).m_130940_(ChatFormatting.YELLOW).m_7220_((Component)Component.m_237113_((String)("Due\u00f1o: " + c.getOwnerName() + "\n")).m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)("Centro: X=" + c.getX() + " Y=" + c.getY() + " Z=" + c.getZ() + "\n")).m_130940_(ChatFormatting.GRAY)).m_7220_((Component)Component.m_237113_((String)("Miembros: " + c.getMembers().size())).m_130940_(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
        List<Claim> claims = ClaimManager.getInstance().getClaimsOf(p.m_20148_());
        if (claims.isEmpty()) {
            ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)"[i] No tienes zonas.").m_130940_(ChatFormatting.GRAY), false);
            return 0;
        }
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> {
            MutableComponent t = Component.m_237113_((String)("=== Tus zonas (" + claims.size() + ") ===\n")).m_130940_(ChatFormatting.YELLOW);
            for (Claim c : claims) {
                t.m_7220_((Component)Component.m_237113_((String)("- " + c.sizeLabel() + " en X=" + c.getX() + " Z=" + c.getZ() + " (" + c.getWorld() + ")\n")).m_130940_(ChatFormatting.GRAY));
            }
            return t;
        }, false);
        return claims.size();
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).m_81375_();
        Claim c = ClaimManager.getInstance().getClaimAt(p.m_9236_(), p.m_20183_());
        if (c == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona protegida.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (!c.isOwner((Player)p) && !p.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] Solo el due\u00f1o puede eliminar esta zona.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        BlockPos centre = c.getCenter();
        ClaimTier tier = c.getTier();
        if (tier != null && ClaimBlocks.isClaimConcreteForTier(p.m_9236_().m_8055_(centre).m_60734_(), tier)) {
            p.m_9236_().m_46961_(centre, false);
        }
        ClaimManager.getInstance().removeClaim(p.m_9236_(), centre);
        p.m_9236_().m_5594_(null, centre, SoundEvents.f_144243_, SoundSource.BLOCKS, 2.0f, 1.0f);
        if (tier != null) {
            ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
            if (!p.m_150109_().m_36054_(stack)) {
                p.m_36176_(stack, false);
            }
        }
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)"\u2714 Zona eliminada. Protecci\u00f3n devuelta a tu inventario.").m_130940_(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int ban(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).m_81375_();
        ServerPlayer target = EntityArgument.m_91474_(ctx, (String)"jugador");
        Claim c = ClaimManager.getInstance().getClaimAt(exec.m_9236_(), exec.m_20183_());
        if (c == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (!c.isOwner((Player)exec) && !exec.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] Solo el due\u00f1o puede banear.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (target.m_20310_(2) && !exec.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No puedes banear a un operador.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        c.banPlayer(target.m_20148_());
        ClaimManager.getInstance().save();
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("\u2714 " + target.m_7755_().getString() + " baneado.")).m_130940_(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int unban(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).m_81375_();
        ServerPlayer target = EntityArgument.m_91474_(ctx, (String)"jugador");
        Claim c = ClaimManager.getInstance().getClaimAt(exec.m_9236_(), exec.m_20183_());
        if (c == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (!c.isOwner((Player)exec) && !exec.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] Solo el due\u00f1o puede desbanear.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        c.unbanPlayer(target.m_20148_());
        ClaimManager.getInstance().save();
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("\u2714 " + target.m_7755_().getString() + " desbaneado.")).m_130940_(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).m_81375_();
        ServerPlayer target = EntityArgument.m_91474_(ctx, (String)"jugador");
        Claim c = ClaimManager.getInstance().getClaimAt(exec.m_9236_(), exec.m_20183_());
        if (c == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (!c.isOwner((Player)exec) && !exec.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] Solo el due\u00f1o puede transferir.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (target.m_20310_(2) && !exec.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No puedes transferir a un operador.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (c.isOwner(target.m_20148_())) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] Ya es el due\u00f1o actual.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        ClaimManager.getInstance().transferOwnership(c, target.m_20148_(), target.m_7755_().getString());
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("\u2714 Zona transferida a " + target.m_7755_().getString())).m_130940_(ChatFormatting.GREEN), true);
        target.m_5661_((Component)Component.m_237113_((String)("[Protecci\u00f3n] Has recibido la propiedad de una zona en X=" + c.getX() + " Z=" + c.getZ())).m_130940_(ChatFormatting.GREEN), false);
        return 1;
    }

    private static Claim ownedClaimAt(CommandSourceStack source, ServerPlayer p) {
        Claim c = ClaimManager.getInstance().getClaimAt(p.m_9236_(), p.m_20183_());
        if (c == null) {
            source.m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona protegida.").m_130940_(ChatFormatting.RED));
            return null;
        }
        if (!c.isOwner((Player)p) && !p.m_20310_(2)) {
            source.m_81352_((Component)Component.m_237113_((String)"[x] Solo el due\u00f1o puede gestionar los miembros de esta zona.").m_130940_(ChatFormatting.RED));
            return null;
        }
        return c;
    }

    private static int addMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).m_81375_();
        Claim c = ClaimCommands.ownedClaimAt((CommandSourceStack)ctx.getSource(), exec);
        if (c == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, (String)"jugador");
        return ClaimMenuHandler.addMemberByName(exec, c, name, 0, false) ? 1 : 0;
    }

    private static int delMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).m_81375_();
        Claim c = ClaimCommands.ownedClaimAt((CommandSourceStack)ctx.getSource(), exec);
        if (c == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, (String)"jugador");
        return ClaimMenuHandler.removeMemberByName(exec, c, name, 0, false) ? 1 : 0;
    }

    private static int members(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).m_81375_();
        Claim c = ClaimCommands.ownedClaimAt((CommandSourceStack)ctx.getSource(), exec);
        if (c == null) {
            return 0;
        }
        Claim claim = c;
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> {
            MutableComponent t = Component.m_237113_((String)("=== Miembros de la zona de " + claim.getOwnerName() + " (" + claim.getMembers().size() + ") ===\n")).m_130940_(ChatFormatting.YELLOW);
            if (claim.getMembers().isEmpty()) {
                t.m_7220_((Component)Component.m_237113_((String)"(sin miembros)").m_130940_(ChatFormatting.DARK_GRAY));
            } else {
                for (int i = 0; i < claim.getMembers().size(); ++i) {
                    String n = i < claim.getMemberNames().size() ? claim.getMemberNames().get(i) : claim.getMembers().get(i).toString();
                    t.m_7220_((Component)Component.m_237113_((String)("- " + n + "\n")).m_130940_(ChatFormatting.WHITE));
                }
            }
            return t;
        }, false);
        return claim.getMembers().size();
    }

    private static int removeMember(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer exec = ((CommandSourceStack)ctx.getSource()).m_81375_();
        ServerPlayer target = EntityArgument.m_91474_(ctx, (String)"jugador");
        Claim c = ClaimManager.getInstance().getClaimAt(exec.m_9236_(), exec.m_20183_());
        if (c == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No est\u00e1s en ninguna zona.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (!c.isOwner((Player)exec) && !exec.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] Solo el due\u00f1o puede gestionar miembros.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (target.m_20310_(2) && !exec.m_20310_(2)) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"[x] No puedes gestionar a un operador.").m_130940_(ChatFormatting.RED));
            return 0;
        }
        if (!c.isMember(target.m_20148_())) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)("[x] " + target.m_7755_().getString() + " no es miembro.")).m_130940_(ChatFormatting.RED));
            return 0;
        }
        c.removeMember(target.m_20148_());
        ClaimManager.getInstance().save();
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("\u2714 " + target.m_7755_().getString() + " eliminado de la zona.")).m_130940_(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, (String)"id");
        ClaimTier tier = ClaimTier.byId(id);
        if (tier == null) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)("[x] ID no v\u00e1lido: " + id)).m_130940_(ChatFormatting.RED));
            return 0;
        }
        Collection<ServerPlayer> targets = (Collection<ServerPlayer>)(Collection<?>)EntityArgument.m_91477_(ctx, (String)"jugador");
        for (ServerPlayer p : targets) {
            ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
            if (!p.m_150109_().m_36054_(stack)) {
                p.m_36176_(stack, false);
            }
            p.m_5661_((Component)Component.m_237113_((String)("[+] Recibiste Protecci\u00f3n " + tier.label())).m_130940_(ChatFormatting.GREEN), false);
        }
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("\u2714 Protecci\u00f3n " + tier.label() + " entregada a " + targets.size() + " jugador(es).")).m_130940_(ChatFormatting.GREEN), true);
        return targets.size();
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.m_91474_(ctx, (String)"jugador");
        int n = ClaimManager.getInstance().clearClaimsOf(target.m_20148_());
        ((CommandSourceStack)ctx.getSource()).m_288197_(() -> Component.m_237113_((String)("\u2714 Eliminadas " + n + " zona(s) de " + target.m_7755_().getString())).m_130940_(ChatFormatting.GREEN), true);
        return n;
    }
}

