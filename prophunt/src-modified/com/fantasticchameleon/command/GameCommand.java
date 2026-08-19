package com.fantasticchameleon.command;

import com.fantasticchameleon.game.Arenas;
import com.fantasticchameleon.game.Perms;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.game.WorldPick;
import com.fantasticchameleon.network.OpenEditorPayload;
import com.fantasticchameleon.platform.Services;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

public final class GameCommand {
   private static final int TAB_ROOMS = 0;
   private static final int TAB_MATCH = 1;
   private static final int TAB_ARENA = 3;
   private static final SuggestionProvider<CommandSourceStack> ROOM_NAMES = (ctx, builder) -> SharedSuggestionProvider.m_82970_(Rooms.names(), builder);
   private static final SuggestionProvider<CommandSourceStack> ARENA_NAMES = (ctx, builder) -> SharedSuggestionProvider.m_82970_(Arenas.names(), builder);

   private GameCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_(
                                 "fschameleon"
                              )
                              .requires(Perms::isStaff)
                              .executes(ctx -> openEditor(ctx, 0)))
                           .then(
                              Commands.m_82127_("crear")
                                 .then(
                                    ((RequiredArgumentBuilder)Commands.m_82129_("sala", StringArgumentType.string()).executes(ctx -> create(ctx, null)))
                                       .then(
                                          Commands.m_82129_("contrasena", StringArgumentType.string())
                                             .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "contrasena")))
                                       )
                                 )
                           ))
                        .then(
                           Commands.m_82127_("unirse")
                              .then(
                                 ((RequiredArgumentBuilder)Commands.m_82129_("sala", StringArgumentType.string())
                                       .suggests(ROOM_NAMES)
                                       .executes(ctx -> join(ctx, null)))
                                    .then(
                                       Commands.m_82129_("contrasena", StringArgumentType.string())
                                          .executes(ctx -> join(ctx, StringArgumentType.getString(ctx, "contrasena")))
                                    )
                              )
                        ))
                     .then(Commands.m_82127_("salir").executes(ctx -> {
                        Rooms.leave(player(ctx));
                        return 1;
                     })))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("editar").requires(Perms::isStaff))
                              .executes(ctx -> openEditor(ctx, 0)))
                           .then(Commands.m_82129_("sala", StringArgumentType.string()).suggests(ROOM_NAMES).executes(ctx -> {
                              ServerPlayer p = player(ctx);
                              Rooms.forceJoin(p, p, StringArgumentType.getString(ctx, "sala"));
                              return openEditor(ctx, 1);
                           })))
                        .then(Commands.m_82127_("arena").then(Commands.m_82129_("arena", StringArgumentType.string()).suggests(ARENA_NAMES).executes(ctx -> {
                           ServerPlayer p = player(ctx);
                           Arenas.select(p, StringArgumentType.getString(ctx, "arena"));
                           return openEditor(ctx, 3);
                        })))
                  ))
               .then(((LiteralArgumentBuilder)Commands.m_82127_("iniciar").executes(ctx -> {
                  Rooms.start(player(ctx), null);
                  return 1;
               })).then(Commands.m_82129_("sala", StringArgumentType.string()).suggests(ROOM_NAMES).executes(ctx -> {
                  Rooms.start(player(ctx), StringArgumentType.getString(ctx, "sala"));
                  return 1;
               }))))
            .then(
               Commands.m_82127_("borrar")
                  .then(((RequiredArgumentBuilder)Commands.m_82129_("sala", StringArgumentType.string()).suggests(ROOM_NAMES).executes(ctx -> {
                     Rooms.delete(player(ctx), StringArgumentType.getString(ctx, "sala"), false);
                     return 1;
                  })).then(Commands.m_82127_("confirmar").executes(ctx -> {
                     Rooms.delete(player(ctx), StringArgumentType.getString(ctx, "sala"), true);
                     return 1;
                  })))
            )
      );
   }

   private static int create(CommandContext<CommandSourceStack> ctx, String password) throws CommandSyntaxException {
      ServerPlayer p = player(ctx);
      Rooms.create(p, StringArgumentType.getString(ctx, "sala"), password);
      return openEditor(ctx, 1);
   }

   private static int join(CommandContext<CommandSourceStack> ctx, String password) throws CommandSyntaxException {
      Rooms.join(player(ctx), StringArgumentType.getString(ctx, "sala"), password);
      return 1;
   }

   private static int openEditor(CommandContext<CommandSourceStack> ctx, int tab) throws CommandSyntaxException {
      ServerPlayer p = player(ctx);
      WorldPick.forget(p);
      Services.PLATFORM.sendToClient(p, new OpenEditorPayload(tab));
      return 1;
   }

   private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
      return ((CommandSourceStack)ctx.getSource()).m_81375_();
   }
}
