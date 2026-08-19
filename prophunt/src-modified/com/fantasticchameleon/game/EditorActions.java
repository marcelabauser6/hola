package com.fantasticchameleon.game;

import com.fantasticchameleon.entity.DummyPlayer;
import com.fantasticchameleon.item.ArenaWandItem;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.network.CreatorSkinPayload;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.network.SchematicsPayload;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PropShapes;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class EditorActions {
   public static final String[] GLOBAL_NAMES = new String[]{
      "seenametags",
      "locator",
      "shadow",
      "suffocation",
      "adventure",
      "tphome",
      "freearena",
      "topsidebar",
      "clipguard",
      "announcements",
      "kits",
      "bots",
      "spectate",
      "roomcreation"
   };
   private static final Set<String> OPEN_TO_EVERYONE = Set.of(
      "room.join", "room.leave", "room.list", "room.accept", "room.spectate", "room.save", "pick.cancel"
   );

   private EditorActions() {
   }

   public static void handle(ServerPlayer p, String action, String a, String b, int value) {
      MinecraftServer server = p.m_9236_().m_7654_();
      if (server != null) {
         if (!OPEN_TO_EVERYONE.contains(action) && !Perms.isStaff(p)) {
            p.m_5661_(Component.m_237115_("fantastic.ui.staff_only").m_130940_(ChatFormatting.RED), true);
         } else {
            switch (action) {
               case "pick":
                  WorldPick.Kind kind = kindOf(a);
                  if (kind != null && (!staffOnlyPick(kind) || requireStaff(p))) {
                     WorldPick.begin(p, kind, b, value);
                  }
                  break;
               case "pick.cancel":
                  WorldPick.cancel(p);
                  break;
               case "room.create":
                  Rooms.create(p, a, b.isBlank() ? null : b);
                  break;
               case "room.join":
                  Rooms.join(p, a, b.isBlank() ? null : b);
                  break;
               case "room.leave":
                  Rooms.leave(p);
                  break;
               case "room.start":
                  Rooms.start(p, a.isBlank() ? null : a);
                  break;
               case "room.stop":
                  Rooms.stop(p, a.isBlank() ? null : a);
                  break;
               case "room.delete":
                  Rooms.delete(p, a, value != 0);
                  break;
               case "room.accept":
                  Rooms.acceptInvite(p, a.isBlank() ? null : a);
                  break;
               case "room.invite":
                  Rooms.invite(p, a);
                  break;
               case "room.spectate":
                  Rooms.spectate(p);
                  break;
               case "room.role":
                  Rooms.setOwnRole(p, "seeker".equals(a) ? Room.Role.SEEKER : Room.Role.HIDER);
                  break;
               case "room.list":
                  Rooms.list(p);
                  break;
               case "room.save":
                  Rooms.pushTo(p);
                  break;
               case "room.config":
                  Rooms.setConfig(p, a, value, true);
                  break;
               case "room.kick":
                  Rooms.menuAction(p, "kick", a, "");
                  break;
               case "room.ban":
                  Rooms.menuAction(p, "ban", a, "");
                  break;
               case "room.setrole":
                  if (requireStaff(p)) {
                     ServerPlayer target = byName(server, a);
                     if (target != null) {
                        Rooms.setRole(p, target, "seeker".equals(b) ? Room.Role.SEEKER : Room.Role.HIDER);
                     }
                  }
                  break;
               case "room.forcejoin":
                  if (requireStaff(p)) {
                     ServerPlayer target = byName(server, a);
                     if (target != null) {
                        Rooms.forceJoin(p, target, b);
                     }
                  }
                  break;
               case "room.forcejoin.all":
                  if (requireStaff(p)) {
                     Rooms.forceJoinAllDummies(p, a);
                  }
                  break;
               case "room.feature":
                  if (requireStaff(p)) {
                     Rooms.feature(p, a);
                  }
                  break;
               case "wand":
                  if (requireStaff(p)) {
                     ItemStack wand = ArenaWandItem.create();
                     if (!p.m_150109_().m_36054_(wand)) {
                        p.m_36176_(wand, false);
                     }

                     p.m_5661_(Component.m_237115_("fantastic.wand.given").m_130940_(ChatFormatting.GREEN), true);
                  }
                  break;
               case "room.arena.use":
                  Rooms.selectArena(p, a);
                  break;
               case "room.arena.clear":
                  Rooms.clearArena(p);
                  break;
               case "room.arena.show":
                  Rooms.showArena(p);
                  break;
               case "dummy.spawn":
                  if (requireStaff(p)) {
                     spawnDummies(p, Math.max(1, Math.min(200, value)));
                  }
                  break;
               case "dummy.clear":
                  if (requireStaff(p)) {
                     int n = Rooms.clearDummies(p);
                     p.m_5661_(Component.m_237110_("fantastic.dummy.cleared", new Object[]{n}), true);
                  }
                  break;
               case "arena.tp":
                  if (requireStaff(p)) {
                     Arenas.tpTo(p, a);
                  }
                  break;
               case "arena.rename":
                  if (requireStaff(p)) {
                     Arenas.rename(p, a, b);
                  }
                  break;
               case "arena.reload":
                  if (requireStaff(p)) {
                     Arenas.reload(p, a);
                  }
                  break;
               case "arena.refit":
                  if (requireStaff(p)) {
                     Arenas.refit(p, a);
                  }
                  break;
               case "arena.delete":
                  if (requireStaff(p)) {
                     if (value != 0) {
                        Arenas.deleteArena(p, a);
                     } else {
                        Arenas.confirmDelete(p, a);
                     }
                  }
                  break;
               case "arena.adjust":
                  if (requireStaff(p)) {
                     Arenas.adjust(p, a, b, value);
                  }
                  break;
               case "arena.schematics":
                  if (requireStaff(p)) {
                     Services.PLATFORM.sendToClient(p, new SchematicsPayload(Arenas.schematicFiles()));
                  }
                  break;
               case "arena.import":
                  if (requireStaff(p)) {
                     Arenas.importSchematic(p, a, b.isBlank() ? null : b);
                  }
                  break;
               case "global":
                  if (requireStaff(p)) {
                     applyGlobal(p.m_20203_(), a, value != 0);
                  }
                  break;
               case "global.status":
                  if (requireStaff(p)) {
                     printGlobalStatus(p.m_20203_());
                  }
                  break;
               case "kitcooldown":
                  if (requireStaff(p)) {
                     GlobalSettings.setKitCooldownHours(Math.max(0, Math.min(720, value)));
                     GlobalSettings.syncAll(server);
                     p.m_5661_(Component.m_237110_("fantastic.kit.cooldown_set", new Object[]{GlobalSettings.kitCooldownHours()}), true);
                  }
                  break;
               case "spawn.set":
                  if (requireStaff(p)) {
                     FantasticSpawn.set(p);
                  }
                  break;
               case "spawn.tp":
                  if (!FantasticSpawn.teleport(p)) {
                     p.m_5661_(Component.m_237115_("fantastic.spawn.unset").m_130940_(ChatFormatting.RED), true);
                  }
                  break;
               case "preset.apply":
                  if (!Kits.applyPresetToWorn(p, a)) {
                     p.m_213846_(Component.m_237115_("fantastic.preset.need_armor").m_130940_(ChatFormatting.YELLOW));
                  }
                  break;
               case "board.clear":
                  if (requireStaff(p)) {
                     Boards.clear(p);
                  }
                  break;
               case "kit":
                  Kits.give(p.m_20203_(), p, false);
                  break;
               case "kit.other":
                  if (requireStaff(p)) {
                     ServerPlayer target = byName(server, a);
                     if (target != null) {
                        Kits.give(p.m_20203_(), target, true);
                     }
                  }
                  break;
               case "crate":
                  if (requireStaff(p)) {
                     ServerPlayer target = a.isBlank() ? p : byName(server, a);
                     if (target != null) {
                        giveCrates(target, Math.max(1, Math.min(64, value)));
                     }
                  }
                  break;
               case "prop":
                  setProp(p, a, value);
                  break;
               case "skin":
                  Services.PLATFORM.sendToClient(p, new CreatorSkinPayload(a));
                  break;
               case "stats":
                  Stats.showPersonal(p);
                  break;
               case "top":
                  Stats.show(p);
            }
         }
      }
   }

   private static WorldPick.Kind kindOf(String name) {
      for (WorldPick.Kind k : WorldPick.Kind.values()) {
         if (k.name().equalsIgnoreCase(name)) {
            return k;
         }
      }

      return null;
   }

   private static boolean staffOnlyPick(WorldPick.Kind kind) {
      return switch (kind) {
         case ARENA_CORNER_1, ARENA_CORNER_2, ARENA_START, LOBBY_SPAWN, BOARD -> true;
         case ROOM_CORNER_1, ROOM_CORNER_2 -> false;
      };
   }

   private static boolean requireStaff(ServerPlayer p) {
      if (Perms.isStaff(p)) {
         return true;
      } else {
         p.m_5661_(Component.m_237115_("fantastic.ui.staff_only").m_130940_(ChatFormatting.RED), true);
         return false;
      }
   }

   private static ServerPlayer byName(MinecraftServer server, String name) {
      return name != null && !name.isBlank() ? server.m_6846_().m_11255_(name) : null;
   }

   private static void giveCrates(ServerPlayer target, int count) {
      ItemStack stack = new ItemStack((ItemLike)FantasticItems.CREATOR_CRATE.get(), count);
      if (!target.m_150109_().m_36054_(stack) && !stack.m_41619_()) {
         target.m_36176_(stack, false);
      }

      target.m_213846_(Component.m_237110_("fantastic.crate.received", new Object[]{count}));
   }

   private static void setProp(ServerPlayer p, String key, int placement) {
      if (!key.isBlank() && !key.equalsIgnoreCase("off") && !key.equalsIgnoreCase("none")) {
         int idx = PropShapes.indexOf(key);
         if (idx < 0) {
            p.m_213846_(Component.m_237110_("fantastic.block.shape_unknown", new Object[]{key}).m_130940_(ChatFormatting.RED));
         } else if (!Perms.isStaff(p) && !Rooms.isInRoom(p)) {
            p.m_213846_(Component.m_237115_("fantastic.prop.room_only").m_130940_(ChatFormatting.RED));
         } else {
            FantasticNetwork.applyProp(p, idx, placement);
            p.m_213846_(Component.m_237110_("fantastic.prop.set", new Object[]{Component.m_237115_(PropShapes.nameKey(idx))}));
         }
      } else {
         FantasticNetwork.clearProp(p);
         p.m_213846_(Component.m_237115_("fantastic.prop.off"));
      }
   }

   private static void spawnDummies(ServerPlayer caller, int count) {
      if (!GlobalSettings.bots()) {
         caller.m_213846_(Component.m_237115_("fantastic.dummy.disabled"));
      } else if (DummyPlayer.bukkitBridgePresent()) {
         caller.m_213846_(Component.m_237115_("fantastic.dummy.no_hybrid").m_130940_(ChatFormatting.RED));
      } else if (Rooms.canManageBots(caller) && Rooms.roomOf(caller) != null) {
         int joined = 0;

         for (int i = 0; i < count; i++) {
            ServerPlayer dummy = DummyPlayer.spawn(caller, i);
            if (Rooms.addDummy(dummy, caller)) {
               joined++;
            } else {
               DummyPlayer.remove(caller.m_9236_().m_7654_(), dummy);
            }
         }

         caller.m_213846_(Component.m_237110_("fantastic.dummy.spawned_added", new Object[]{joined}));
      } else {
         caller.m_213846_(Component.m_237115_("fantastic.dummy.need_room"));
      }
   }

   public static void applyGlobal(CommandSourceStack src, String name, boolean on) {
      MinecraftServer server = src.m_81377_();
      String labelKey;
      switch (name) {
         case "locator":
            GlobalSettings.setLocatorHiding(on);
            GlobalSettings.reapplyLocatorAll(server);
            labelKey = "fantastic.global.locator";
            break;
         case "shadow":
            GlobalSettings.setShadowHiding(!on);
            labelKey = "fantastic.global.shadow";
            break;
         case "suffocation":
            GlobalSettings.setSuffocationImmunity(on);
            labelKey = "fantastic.global.suffocation";
            break;
         case "adventure":
            GlobalSettings.setRoundAdventure(on);
            labelKey = "fantastic.global.adventure";
            break;
         case "tphome":
            GlobalSettings.setTeleportHome(on);
            labelKey = "fantastic.global.tphome";
            break;
         case "freearena":
            GlobalSettings.setFreeArena(on);
            Rooms.pushAll(server);
            labelKey = "fantastic.global.freearena";
            break;
         case "topsidebar":
            GlobalSettings.setTopSidebar(on);
            if (on) {
               Stats.refreshSidebars(server);
            } else {
               Stats.hideSidebarAll(server);
            }

            labelKey = "fantastic.global.topsidebar";
            break;
         case "clipguard":
            GlobalSettings.setClipGuard(on);
            labelKey = "fantastic.global.clipguard";
            break;
         case "announcements":
            GlobalSettings.setAnnouncements(on);
            labelKey = "fantastic.global.announcements";
            break;
         case "kits":
            GlobalSettings.setKits(on);
            labelKey = "fantastic.global.kits";
            break;
         case "bots":
            GlobalSettings.setBots(on);
            labelKey = "fantastic.global.bots";
            break;
         case "roomcreation":
            GlobalSettings.setRoomCreation(on);
            labelKey = "fantastic.global.roomcreation";
            break;
         case "spectate":
            GlobalSettings.setAllowSpectate(on);
            labelKey = "fantastic.global.spectate";
            break;
         case "seenametags":
            GlobalSettings.setNametagHiding(!on);
            GlobalSettings.reapplyNametagAll(server);
            labelKey = "fantastic.global.seenametags";
            break;
         default:
            return;
      }

      GlobalSettings.syncAll(server);
      String key = labelKey;
      src.m_288197_(() -> Component.m_237110_(key, new Object[]{Component.m_237115_(on ? "fantastic.global.on" : "fantastic.global.off")}), true);
   }

   private static void printGlobalStatus(CommandSourceStack src) {
      src.m_288197_(() -> Component.m_237115_("fantastic.global.status").m_130940_(ChatFormatting.GOLD), false);

      for (String name : GLOBAL_NAMES) {
         boolean on = globalValue(name);
         src.m_288197_(
            () -> Component.m_237113_("  " + name + ": ")
                  .m_7220_(Component.m_237115_(on ? "fantastic.global.on" : "fantastic.global.off").m_130940_(on ? ChatFormatting.GREEN : ChatFormatting.RED)),
            false
         );
      }
   }

   public static boolean globalValue(String name) {
      return switch (name) {
         case "seenametags" -> !GlobalSettings.nametagHiding();
         case "locator" -> GlobalSettings.locatorHiding();
         case "shadow" -> !GlobalSettings.shadowHiding();
         case "suffocation" -> GlobalSettings.suffocationImmunity();
         case "adventure" -> GlobalSettings.roundAdventure();
         case "tphome" -> GlobalSettings.teleportHome();
         case "freearena" -> GlobalSettings.freeArena();
         case "topsidebar" -> GlobalSettings.topSidebar();
         case "clipguard" -> GlobalSettings.clipGuard();
         case "announcements" -> GlobalSettings.announcements();
         case "kits" -> GlobalSettings.kits();
         case "bots" -> GlobalSettings.bots();
         case "roomcreation" -> GlobalSettings.roomCreation();
         case "spectate" -> GlobalSettings.allowSpectate();
         default -> false;
      };
   }
}
