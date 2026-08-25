package com.fantasticicons.command;

import com.fantasticicons.data.IconStore;
import com.fantasticicons.icon.IconRegistry;
import com.fantasticicons.net.IconNetwork;
import com.fantasticicons.net.SyncIconsPacket;
import com.fantasticicons.server.PlayerResolver;
import com.fantasticicons.server.ServerNames;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/** /fsicons — solo OP (nivel 2). */
public final class IconCommands {
   private static final int PER_PAGE = 15;

   private static final SuggestionProvider<CommandSourceStack> ICONOS = (context, builder) -> {
      String typed = builder.getRemainingLowerCase();

      for (IconRegistry.Icon icon : IconRegistry.all()) {
         if (icon.id().startsWith(typed)) {
            builder.suggest(icon.id(), Component.literal(icon.name()));
         }
      }

      return builder.buildFuture();
   };

   private static final SuggestionProvider<CommandSourceStack> JUGADORES = (context, builder) -> {
      MinecraftServer server = context.getSource().getServer();
      List<String> names = new ArrayList<>(List.of(server.getPlayerNames()));

      for (UUID id : IconStore.get().snapshot().keySet()) {
         String name = PlayerResolver.nameOf(server, id);
         if (!names.contains(name)) {
            names.add(name);
         }
      }

      return SharedSuggestionProvider.suggest(names, builder);
   };

   private IconCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("fsicons")
            .requires(source -> source.hasPermission(2))
            .executes(context -> help(context.getSource()))
            .then(
               Commands.literal("poner")
                  .then(
                     Commands.argument("icono", StringArgumentType.word())
                        .suggests(ICONOS)
                        .then(
                           Commands.argument("jugador", StringArgumentType.word())
                              .suggests(JUGADORES)
                              .executes(context -> assign(context, StringArgumentType.getString(context, "icono"), StringArgumentType.getString(context, "jugador")))
                        )
                  )
            )
            .then(
               Commands.literal("cambiar")
                  .then(
                     Commands.argument("jugador", StringArgumentType.word())
                        .suggests(JUGADORES)
                        .then(
                           Commands.argument("icono", StringArgumentType.word())
                              .suggests(ICONOS)
                              .executes(context -> assign(context, StringArgumentType.getString(context, "icono"), StringArgumentType.getString(context, "jugador")))
                        )
                  )
            )
            .then(
               Commands.literal("quitar")
                  .then(
                     Commands.argument("jugador", StringArgumentType.word())
                        .suggests(JUGADORES)
                        .executes(context -> clear(context, StringArgumentType.getString(context, "jugador")))
                  )
            )
            .then(
               Commands.literal("lista")
                  .executes(context -> list(context.getSource(), 1))
                  .then(
                     Commands.argument("pagina", IntegerArgumentType.integer(1, pages()))
                        .executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "pagina")))
                  )
            )
            .then(
               Commands.literal("ver")
                  .then(
                     Commands.argument("jugador", StringArgumentType.word())
                        .suggests(JUGADORES)
                        .executes(context -> show(context.getSource(), StringArgumentType.getString(context, "jugador")))
                  )
            )
            .then(Commands.literal("jugadores").executes(context -> listPlayers(context.getSource())))
      );
   }

   private static int assign(CommandContext<CommandSourceStack> context, String rawIcon, String rawPlayer) {
      CommandSourceStack source = context.getSource();
      String iconId = rawIcon == null ? "" : rawIcon.toLowerCase(Locale.ROOT).trim();
      IconRegistry.Icon icon = IconRegistry.get(iconId);
      if (icon == null) {
         source.sendFailure(
            Component.literal("[!] El icono '" + rawIcon + "' no existe. Mira /fsicons lista.").withStyle(ChatFormatting.RED)
         );
         return 0;
      }

      PlayerResolver.Target target = PlayerResolver.resolve(source.getServer(), rawPlayer);
      if (target == null) {
         source.sendFailure(Component.literal("[!] No encuentro al jugador '" + rawPlayer + "'.").withStyle(ChatFormatting.RED));
         return 0;
      }

      IconStore.get().set(target.id(), icon.id());
      broadcast(source.getServer());
      if (target.isOnline()) {
         ServerNames.refresh(target.online());
         target.online()
            .sendSystemMessage(
               Component.literal("Ahora llevas el icono ").withStyle(ChatFormatting.GRAY).append(IconRegistry.label(icon.id()))
            );
      }

      MutableComponent feedback = Component.literal("[LISTO] ")
         .withStyle(ChatFormatting.GREEN)
         .append(Component.literal(target.name()).withStyle(ChatFormatting.WHITE))
         .append(Component.literal(" ahora usa ").withStyle(ChatFormatting.GRAY))
         .append(IconRegistry.label(icon.id()));
      source.sendSuccess(() -> feedback, true);
      return 1;
   }

   private static int clear(CommandContext<CommandSourceStack> context, String rawPlayer) {
      CommandSourceStack source = context.getSource();
      PlayerResolver.Target target = PlayerResolver.resolve(source.getServer(), rawPlayer);
      if (target == null) {
         source.sendFailure(Component.literal("[!] No encuentro al jugador '" + rawPlayer + "'.").withStyle(ChatFormatting.RED));
         return 0;
      }

      if (!IconStore.get().remove(target.id())) {
         source.sendFailure(Component.literal("[!] " + target.name() + " no tiene icono.").withStyle(ChatFormatting.RED));
         return 0;
      }

      broadcast(source.getServer());
      if (target.isOnline()) {
         ServerNames.refresh(target.online());
         target.online().sendSystemMessage(Component.literal("Te han quitado el icono.").withStyle(ChatFormatting.GRAY));
      }

      MutableComponent feedback = Component.literal("[LISTO] ")
         .withStyle(ChatFormatting.GREEN)
         .append(Component.literal("Icono quitado a " + target.name() + ".").withStyle(ChatFormatting.GRAY));
      source.sendSuccess(() -> feedback, true);
      return 1;
   }

   private static int show(CommandSourceStack source, String rawPlayer) {
      PlayerResolver.Target target = PlayerResolver.resolve(source.getServer(), rawPlayer);
      if (target == null) {
         source.sendFailure(Component.literal("[!] No encuentro al jugador '" + rawPlayer + "'.").withStyle(ChatFormatting.RED));
         return 0;
      }

      String iconId = IconStore.get().iconOf(target.id());
      MutableComponent message = Component.literal(target.name() + ": ").withStyle(ChatFormatting.WHITE);
      if (iconId == null) {
         message.append(Component.literal("sin icono").withStyle(ChatFormatting.DARK_GRAY));
      } else {
         message.append(IconRegistry.label(iconId));
      }

      source.sendSuccess(() -> message, false);
      return 1;
   }

   private static int listPlayers(CommandSourceStack source) {
      Map<UUID, String> all = IconStore.get().snapshot();
      if (all.isEmpty()) {
         source.sendSuccess(() -> Component.literal("Todavia no hay iconos asignados.").withStyle(ChatFormatting.GRAY), false);
         return 1;
      }

      MinecraftServer server = source.getServer();
      MutableComponent message = Component.literal("Iconos asignados (" + all.size() + "):").withStyle(ChatFormatting.GOLD);

      for (Map.Entry<UUID, String> entry : all.entrySet()) {
         message.append(Component.literal("\n  " + PlayerResolver.nameOf(server, entry.getKey()) + " ").withStyle(ChatFormatting.WHITE))
            .append(IconRegistry.label(entry.getValue()));
      }

      source.sendSuccess(() -> message, false);
      return 1;
   }

   private static int list(CommandSourceStack source, int page) {
      List<IconRegistry.Icon> icons = IconRegistry.all();
      int total = pages();
      int current = Math.max(1, Math.min(total, page));
      int from = (current - 1) * PER_PAGE;
      int to = Math.min(icons.size(), from + PER_PAGE);
      MutableComponent message = Component.literal("=== Iconos " + current + "/" + total + " (" + icons.size() + " en total) ===")
         .withStyle(ChatFormatting.GOLD);

      for (int i = from; i < to; i++) {
         IconRegistry.Icon icon = icons.get(i);
         MutableComponent line = Component.literal("\n ")
            .append(icon.glyph())
            .append(Component.literal("  " + icon.name()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("  " + icon.id()).withStyle(ChatFormatting.DARK_GRAY));
         message.append(
            line.withStyle(
               style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/fsicons poner " + icon.id() + " "))
                  .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clic para escribir el comando con " + icon.name())))
            )
         );
      }

      if (current < total) {
         message.append(
            Component.literal("\n[Siguiente pagina: /fsicons lista " + (current + 1) + "]")
               .withStyle(
                  style -> style.withColor(ChatFormatting.YELLOW)
                     .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fsicons lista " + (current + 1)))
               )
         );
      }

      source.sendSuccess(() -> message, false);
      return 1;
   }

   private static int help(CommandSourceStack source) {
      MutableComponent message = Component.literal("=== Fantastic Icons ===")
         .withStyle(ChatFormatting.GOLD)
         .append(Component.literal("\n /fsicons poner <icono> <jugador>").withStyle(ChatFormatting.YELLOW))
         .append(Component.literal("  pone el icono").withStyle(ChatFormatting.GRAY))
         .append(Component.literal("\n /fsicons cambiar <jugador> <icono>").withStyle(ChatFormatting.YELLOW))
         .append(Component.literal("  cambia el icono").withStyle(ChatFormatting.GRAY))
         .append(Component.literal("\n /fsicons quitar <jugador>").withStyle(ChatFormatting.YELLOW))
         .append(Component.literal("  quita el icono").withStyle(ChatFormatting.GRAY))
         .append(Component.literal("\n /fsicons lista [pagina]").withStyle(ChatFormatting.YELLOW))
         .append(Component.literal("  catalogo de los " + IconRegistry.count() + " iconos").withStyle(ChatFormatting.GRAY))
         .append(Component.literal("\n /fsicons ver <jugador>").withStyle(ChatFormatting.YELLOW))
         .append(Component.literal("  icono actual").withStyle(ChatFormatting.GRAY))
         .append(Component.literal("\n /fsicons jugadores").withStyle(ChatFormatting.YELLOW))
         .append(Component.literal("  todos los asignados").withStyle(ChatFormatting.GRAY));
      source.sendSuccess(() -> message, false);
      return 1;
   }

   private static void broadcast(MinecraftServer server) {
      IconNetwork.sendToAll(new SyncIconsPacket(IconStore.get().snapshot()));
   }

   private static int pages() {
      return Math.max(1, (IconRegistry.count() + PER_PAGE - 1) / PER_PAGE);
   }
}
