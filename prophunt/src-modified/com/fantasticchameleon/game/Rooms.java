package com.fantasticchameleon.game;

import com.fantasticchameleon.compat.Comp;
import com.fantasticchameleon.compat.Nbt;
import com.fantasticchameleon.donor.DonorFeatures;
import com.fantasticchameleon.entity.DummyPlayer;
import com.fantasticchameleon.item.ArmorPaintHandler;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.network.ArenaPayload;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.network.ForceExitPayload;
import com.fantasticchameleon.network.InvitePayload;
import com.fantasticchameleon.network.RoomMember;
import com.fantasticchameleon.network.RoomSummary;
import com.fantasticchameleon.network.RoomsPayload;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.paint.PaintComponents;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.prophunt.PropHunt;
import com.fantasticchameleon.prophunt.PropHuntRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Rooms {
   private static final Map<String, Room> ROOMS = new LinkedHashMap<>();
   private static final Map<UUID, Room> MEMBER_ROOM = new HashMap<>();
   private static int seq;
   private static final String[] FUN_NAMES = new String[]{
      "TheK-meleons",
      "ILoveChameleons",
      "Chameleons4ever",
      "JoinMyRoomPlz",
      "HelloWorld",
      "NowYouSeeMe",
      "HideAndSneak",
      "NotAScoobySnack",
      "CamoCrew",
      "SneakyLizards",
      "ThisRoomStinks"
   };
   private static final Random NAME_RNG = new Random();
   private static final Predicate<CommandSourceStack> OP = Perms::isStaff;
   private static final Map<UUID, Integer> PWD_FAILS = new HashMap<>();
   private static final Map<UUID, Long> PWD_LOCKED_UNTIL = new HashMap<>();
   private static final int PWD_SOFT_FAILS = 5;
   private static final int PWD_HARD_FAILS = 10;
   private static final long PWD_SOFT_LOCK = 1200L;
   private static final long PWD_HARD_LOCK = 12000L;
   private static final Map<UUID, Rooms.Invite> INVITES = new ConcurrentHashMap<>();
   private static final int INVITE_TTL_TICKS = 3600;
   private static final int INVITE_COOLDOWN_TICKS = 400;
   private static final Map<UUID, Integer> INVITE_LAST = new ConcurrentHashMap<>();
   private static final int ACTION_MIN_TICKS = 2;
   private static final Map<UUID, Integer> LAST_ACTION_TICK = new HashMap<>();
   private static final Map<UUID, Integer> LAST_CFG_TICK = new HashMap<>();
   private static final Map<Room, Integer> PUSH_AT = new HashMap<>();
   private static final Set<Room> PUSH_PENDING = new HashSet<>();
   private static final int PUSH_MIN_TICKS = 2;
   private static final Map<UUID, ItemStack> STASHED_HELMET = new HashMap<>();
   private static String featuredRoom;
   private static final Set<UUID> SHADERED = new HashSet<>();
   private static final Set<UUID> PENDING_KNOCKOUT = new HashSet<>();
   private static final Map<UUID, Integer> GEAR_NAG = new HashMap<>();
   private static final int OFFLINE_GRACE_TICKS = 1200;
   private static final Map<UUID, Integer> OFFLINE_TICKS = new HashMap<>();
   private static final Map<UUID, Integer> WELCOME_DELAY = new HashMap<>();
   private static long barrierCacheTick = Long.MIN_VALUE;
   private static Map<String, List<Room>> barrierRoomsByDimension = Map.of();

   private Rooms() {
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT);
   }

   private static String autoName(ServerPlayer by) {
      String base = NAME_RNG.nextInt(20) == 0 ? FUN_NAMES[NAME_RNG.nextInt(FUN_NAMES.length)] : by.m_6302_();
      base = base.replaceAll("[^A-Za-z0-9_-]", "");
      if (base.isEmpty()) {
         base = "Room";
      }

      if (base.length() > 20) {
         base = base.substring(0, 20);
      }

      String name = base;
      int n = 1;

      while (ROOMS.containsKey(key(name))) {
         name = base + n++;
      }

      return name;
   }

   /** Primera arena activa cuyo volumen está siendo invadido por la entidad. */
   public static Room blockingArena(Entity entity) {
      if (entity == null || entity.m_9236_().f_46443_) return null;
      long tick = entity.m_9236_().m_46467_();
      if (tick != barrierCacheTick) {
         Map<String, List<Room>> byDimension = new HashMap<>();
         for (Room room : ROOMS.values()) {
            if (room.hasActiveMobBarrier()) {
               byDimension.computeIfAbsent(room.arenaDimension(), ignored -> new ArrayList<>()).add(room);
            }
         }
         barrierRoomsByDimension = byDimension;
         barrierCacheTick = tick;
      }

      String dimension = entity.m_9236_().m_46472_().m_135782_().toString();
      for (Room room : barrierRoomsByDimension.getOrDefault(dimension, List.of())) {
         if (room.blocksWildMob(entity)) return room;
      }
      return null;
   }

   /**
    * Arena que protege a quien esté dentro, sin importar la fase ni si es miembro de la sala.
    *
    * <p>Comparte la caché por tick con la barrera de mobs, así que consultarlo por cada evento de daño
    * no recorre todas las salas.
    */
   public static Room arenaProtecting(Entity entity) {
      return blockingArena(entity);
   }

   public static Room roomOf(ServerPlayer p) {
      return p == null ? null : MEMBER_ROOM.get(p.m_20148_());
   }

   public static Room roomOf(UUID id) {
      return MEMBER_ROOM.get(id);
   }

   public static Room byName(String name) {
      return ROOMS.get(key(name));
   }

   public static boolean canManage(ServerPlayer p, Room room) {
      return room != null && (room.isOwner(p.m_20148_()) || OP.test(p.m_20203_()));
   }

   public static boolean canManageOwn(ServerPlayer p) {
      return canManage(p, MEMBER_ROOM.get(p.m_20148_()));
   }

   public static boolean isInRound(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      return room != null && room.inProgress() && room.isParticipant(p.m_20148_());
   }

   public static boolean isInRoom(ServerPlayer p) {
      return MEMBER_ROOM.containsKey(p.m_20148_());
   }

   public static boolean canManageBots(ServerPlayer p) {
      return OP.test(p.m_20203_());
   }

   private static boolean isSafe(String s) {
      return s != null && s.matches("[A-Za-z0-9_-]+");
   }

   public static void create(ServerPlayer by, String name, String password) {
      if (!GlobalSettings.roomCreation() && !OP.test(by.m_20203_())) {
         tell(by, Component.m_237115_("fantastic.room.creation_disabled"), ChatFormatting.RED);
      } else {
         if (name == null || name.isBlank()) {
            name = autoName(by);
         }

         if (!isSafe(name) || name.length() > 24) {
            tell(by, Component.m_237115_("fantastic.room.bad_name"), ChatFormatting.RED);
         } else if (MEMBER_ROOM.containsKey(by.m_20148_())) {
            tell(by, Component.m_237115_("fantastic.room.leave_first"), ChatFormatting.RED);
         } else if (ROOMS.containsKey(key(name))) {
            tell(by, Component.m_237110_("fantastic.room.exists", new Object[]{name}), ChatFormatting.RED);
         } else if (Room.hasAnyArmor(by)) {
            tell(by, Component.m_237115_("fantastic.room.need_no_armor"), ChatFormatting.RED);
         } else {
            Room room = new Room(name, password != null && !password.isEmpty() ? password : null, seq++);
            room.setOwner(by.m_20148_());
            ROOMS.put(key(name), room);
            addMember(room, by);
            tell(
               by,
               Component.m_237110_(password != null && !password.isEmpty() ? "fantastic.room.created_pwd" : "fantastic.room.created", new Object[]{name}),
               ChatFormatting.GREEN
            );
         }
      }
   }

   private static long passwordLockSeconds(ServerPlayer p) {
      Long until = PWD_LOCKED_UNTIL.get(p.m_20148_());
      if (until == null) {
         return 0L;
      } else {
         long left = until - p.m_9236_().m_46467_();
         if (left <= 0L) {
            PWD_LOCKED_UNTIL.remove(p.m_20148_());
            return 0L;
         } else {
            return Math.max(1L, left / 20L);
         }
      }
   }

   private static void onPasswordFail(ServerPlayer p) {
      int fails = PWD_FAILS.merge(p.m_20148_(), 1, Integer::sum);
      if (fails >= 10) {
         PWD_LOCKED_UNTIL.put(p.m_20148_(), p.m_9236_().m_46467_() + 12000L);
      } else if (fails % 5 == 0) {
         PWD_LOCKED_UNTIL.put(p.m_20148_(), p.m_9236_().m_46467_() + 1200L);
      }
   }

   private static int tickOf(ServerPlayer p) {
      MinecraftServer srv = p.m_9236_().m_7654_();
      return srv == null ? 0 : srv.m_129921_();
   }

   public static void invite(ServerPlayer by, String targetName) {
      Room room = MEMBER_ROOM.get(by.m_20148_());
      if (room != null && canManage(by, room)) {
         MinecraftServer srv = by.m_9236_().m_7654_();
         ServerPlayer target = srv != null && targetName != null ? srv.m_6846_().m_11255_(targetName) : null;
         if (target == null || target == by) {
            tell(by, Component.m_237110_("fantastic.room.invite_no_player", new Object[]{String.valueOf(targetName)}), ChatFormatting.RED);
         } else if (room.isMember(target.m_20148_())) {
            tell(by, Component.m_237110_("fantastic.room.invite_already", new Object[]{target.m_36316_().getName()}), ChatFormatting.RED);
         } else {
            int now = tickOf(by);
            Integer last = INVITE_LAST.get(target.m_20148_());
            if (last != null && now - last < 400) {
               tell(by, Component.m_237115_("fantastic.room.invite_cooldown"), ChatFormatting.RED);
            } else {
               INVITE_LAST.put(target.m_20148_(), now);
               INVITES.put(target.m_20148_(), new Rooms.Invite(room.name(), by.m_20148_(), now + 3600));
               String fromName = by.m_36316_().getName();
               Component accept = Component.m_237115_("fantastic.room.invite_accept")
                  .m_130938_(
                     st -> st.m_131140_(ChatFormatting.GREEN)
                           .m_131136_(true)
                           .m_131142_(new ClickEvent(Action.RUN_COMMAND, "/fschameleon unirse \"" + room.name() + "\""))
                           .m_131144_(
                              new HoverEvent(
                                 net.minecraft.network.chat.HoverEvent.Action.f_130831_,
                                 Component.m_237110_("fantastic.room.invite_hover", new Object[]{room.name()})
                              )
                           )
                  );
               target.m_213846_(
                  Component.m_237110_("fantastic.room.invite_chat", new Object[]{fromName, room.name()})
                     .m_130940_(ChatFormatting.YELLOW)
                     .m_130946_(" ")
                     .m_7220_(accept)
               );
               Services.PLATFORM.sendToClient(target, new InvitePayload(room.name(), fromName));
               tell(by, Component.m_237110_("fantastic.room.invite_sent", new Object[]{target.m_36316_().getName()}), ChatFormatting.GREEN);
            }
         }
      } else {
         tell(by, Component.m_237115_("fantastic.room.invite_not_leader"), ChatFormatting.RED);
      }
   }

   public static void acceptInvite(ServerPlayer p, String roomName) {
      Rooms.Invite inv = INVITES.get(p.m_20148_());
      if (inv == null || tickOf(p) > inv.expires() || roomName != null && !inv.room().equalsIgnoreCase(roomName)) {
         tell(p, Component.m_237115_("fantastic.room.invite_expired"), ChatFormatting.RED);
      } else {
         INVITES.remove(p.m_20148_());
         join(p, inv.room(), null, true);
      }
   }

   public static void clearInvite(UUID id) {
      INVITES.remove(id);
      INVITE_LAST.remove(id);
   }

   public static void join(ServerPlayer p, String name, String password) {
      Rooms.Invite inv = INVITES.get(p.m_20148_());
      boolean invited = inv != null && tickOf(p) <= inv.expires() && inv.room().equalsIgnoreCase(name);
      if (invited) {
         INVITES.remove(p.m_20148_());
      }

      join(p, name, password, invited);
   }

   private static void join(ServerPlayer p, String name, String password, boolean invited) {
      Room room = byName(name);
      if (room == null) {
         tell(p, Component.m_237110_("fantastic.room.not_found", new Object[]{name}), ChatFormatting.RED);
      } else if (MEMBER_ROOM.containsKey(p.m_20148_())) {
         tell(p, Component.m_237115_("fantastic.room.already_in"), ChatFormatting.RED);
      } else if (room.isBanned(p.m_20148_()) && !OP.test(p.m_20203_())) {
         tell(p, Component.m_237115_("fantastic.room.is_banned"), ChatFormatting.RED);
      } else {
         if (room.hasPassword() && !invited) {
            long lock = passwordLockSeconds(p);
            if (lock > 0L) {
               tell(p, Component.m_237110_("fantastic.room.password_locked", new Object[]{lock}), ChatFormatting.RED);
               return;
            }
         }

         if (!invited && !room.checkPassword(password == null ? "" : password)) {
            onPasswordFail(p);
            tell(p, Component.m_237110_("fantastic.room.wrong_password", new Object[]{name}), ChatFormatting.RED);
         } else {
            PWD_FAILS.remove(p.m_20148_());
            PWD_LOCKED_UNTIL.remove(p.m_20148_());
            if (room.isFull()) {
               tell(p, Component.m_237110_("fantastic.room.full", new Object[]{name, room.size(), room.capacity()}), ChatFormatting.RED);
            } else if (room.inProgress()) {
               if (!GlobalSettings.allowSpectate()) {
                  tell(p, Component.m_237115_("fantastic.room.in_progress"), ChatFormatting.RED);
               } else {
                  addSpectator(room, p);
               }
            } else if (Room.hasAnyArmor(p)) {
               tell(p, Component.m_237115_("fantastic.room.need_no_armor"), ChatFormatting.RED);
            } else {
               addMember(room, p);
               tell(p, Component.m_237110_("fantastic.room.joined", new Object[]{room.name(), room.size()}), ChatFormatting.YELLOW);
            }
         }
      }
   }

   public static void onArmorEquip(ServerPlayer p, ItemStack equipped) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room != null && !room.inProgress() && !FantasticItems.isRoomGear(equipped) && !DummyPlayer.isDummy(p.m_20148_()) && Room.hasAnyArmor(p)) {
         leaveInternal(p.m_9236_().m_7654_(), p);
         tell(p, Component.m_237110_("fantastic.room.armor_left", new Object[]{room.name()}), ChatFormatting.RED);
      }
   }

   public static void forceJoin(ServerPlayer by, ServerPlayer target, String name) {
      Room room = byName(name);
      if (room == null) {
         tell(by, Component.m_237110_("fantastic.room.not_found", new Object[]{name}), ChatFormatting.RED);
      } else if (room.inProgress()) {
         tell(by, Component.m_237115_("fantastic.room.in_progress"), ChatFormatting.RED);
      } else {
         leaveInternal(by.m_9236_().m_7654_(), target);
         addMember(room, target);
         tell(target, Component.m_237110_("fantastic.room.placed", new Object[]{room.name()}), ChatFormatting.YELLOW);
         tell(by, Component.m_237110_("fantastic.room.added", new Object[]{target.m_7755_().getString(), room.name()}), ChatFormatting.GREEN);
      }
   }

   public static void leave(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room == null) {
         tell(p, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.GRAY);
      } else {
         leaveInternal(p.m_9236_().m_7654_(), p);
         tell(p, Component.m_237110_("fantastic.room.left", new Object[]{room.name()}), ChatFormatting.GRAY);
      }
   }

   public static void kick(ServerPlayer by, ServerPlayer target) {
      Room room = MEMBER_ROOM.get(target.m_20148_());
      if (room == null) {
         tell(by, Component.m_237110_("fantastic.room.target_not_in", new Object[]{target.m_7755_().getString()}), ChatFormatting.RED);
      } else {
         leaveInternal(by.m_9236_().m_7654_(), target);
         tell(target, Component.m_237110_("fantastic.room.removed", new Object[]{room.name()}), ChatFormatting.RED);
         tell(by, Component.m_237110_("fantastic.room.kicked", new Object[]{target.m_7755_().getString(), room.name()}), ChatFormatting.GREEN);
      }
   }

   public static void menuKick(ServerPlayer by, String targetIdStr, boolean ban) {
      Room room = MEMBER_ROOM.get(by.m_20148_());
      if (room == null) {
         tell(by, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.RED);
      } else if (!canManage(by, room)) {
         tell(by, Component.m_237115_("fantastic.room.not_leader"), ChatFormatting.RED);
      } else {
         UUID targetId;
         try {
            targetId = UUID.fromString(targetIdStr);
         } catch (IllegalArgumentException var8) {
            return;
         }

         if (targetId.equals(by.m_20148_())) {
            tell(by, Component.m_237115_("fantastic.room.cant_kick_self"), ChatFormatting.RED);
         } else if (room.isOwner(targetId)) {
            tell(by, Component.m_237115_("fantastic.room.cant_kick_owner"), ChatFormatting.RED);
         } else if (!room.isMember(targetId)) {
            tell(by, Component.m_237110_("fantastic.room.target_not_in", new Object[]{targetIdStr}), ChatFormatting.RED);
         } else {
            MinecraftServer server = by.m_9236_().m_7654_();
            ServerPlayer target = server.m_6846_().m_11259_(targetId);
            String targetName;
            if (target != null) {
               targetName = target.m_7755_().getString();
               leaveInternal(server, target);
               tell(target, Component.m_237110_(ban ? "fantastic.room.banned_target" : "fantastic.room.removed", new Object[]{room.name()}), ChatFormatting.RED);
            } else {
               targetName = targetIdStr;
               MEMBER_ROOM.remove(targetId);
               room.removeMember(server, targetId);
            }

            if (ban) {
               room.ban(targetId);
            }

            tell(
               by, Component.m_237110_(ban ? "fantastic.room.banned_by" : "fantastic.room.kicked", new Object[]{targetName, room.name()}), ChatFormatting.GREEN
            );
            pushAll(server);
         }
      }
   }

   public static void setOwnRole(ServerPlayer p, Room.Role role) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room != null && !room.inProgress()) {
         if (!room.canJoinTeam(p.m_20148_(), role)) {
            tell(p, Component.m_237110_("fantastic.room.team_full", new Object[]{roleName(role), room.countRole(role), room.teamMax(role)}), ChatFormatting.RED);
         } else {
            room.setRole(p.m_20148_(), role);
            tell(p, Component.m_237110_("fantastic.room.role_set", new Object[]{roleName(role)}), ChatFormatting.AQUA);
            pushRoom(p.m_9236_().m_7654_(), room);
         }
      } else {
         tell(p, Component.m_237115_("fantastic.room.role_lobby_only"), ChatFormatting.RED);
      }
   }

   public static void spectate(ServerPlayer p) {
      if (GlobalSettings.allowSpectate() || MEMBER_ROOM.get(p.m_20148_()) != null && MEMBER_ROOM.get(p.m_20148_()).isSpectator(p.m_20148_())) {
         Room room = MEMBER_ROOM.get(p.m_20148_());
         if (room == null) {
            tell(p, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.RED);
         } else if (room.toggleSpectate(p.m_9236_().m_7654_(), p)) {
            pushRoom(p.m_9236_().m_7654_(), room);
         }
      } else {
         tell(p, Component.m_237115_("fantastic.room.spectate_disabled"), ChatFormatting.RED);
      }
   }

   public static Room.WhistleResult whistle(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      return room == null ? Room.WhistleResult.NOT_SCORING : room.awardWhistle(p);
   }

   private static void rescueOrphanedBlockHelmet(ServerPlayer p) {
      if (!STASHED_HELMET.containsKey(p.m_20148_()) && !isInRound(p)) {
         ItemStack head = p.m_6844_(EquipmentSlot.HEAD);
         if (FantasticItems.isRoomGear(head) && head.m_150930_(FantasticItems.CHAMELEON_HELMET.get()) && ChameleonArmor.resolutionOf(head) > 64) {
            ItemStack plain = new ItemStack((ItemLike)FantasticItems.CHAMELEON_HELMET.get());
            BodyCanvas canvas = Comp.get(head, PaintComponents.CANVAS.get());
            if (canvas != null) {
               Comp.set(plain, PaintComponents.CANVAS.get(), canvas);
            }

            p.m_8061_(EquipmentSlot.HEAD, plain);
         }
      }
   }

   public static void onLogin(ServerPlayer p) {
      rescueOrphanedBlockHelmet(p);
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room != null) {
         room.onLogin(p);
      } else {
         FantasticItems.stripRoomGear(p);
      }

      if (room == null || !room.canUseProp(p.m_20148_())) {
         FantasticNetwork.resetPose(p);
         Services.PLATFORM.sendToClient(p, ForceExitPayload.INSTANCE);
      }

      boolean activeSpectator = room != null && room.inProgress() && room.isSpectator(p.m_20148_());
      CompoundTag data = Services.PLATFORM.persistentData(p);
      if (data.m_128441_("fantastic_saved_gm") && !activeSpectator && p.f_8941_.m_9290_() == GameType.SPECTATOR) {
         p.m_143403_(GameType.m_46393_(Nbt.getIntOr(data, "fantastic_saved_gm", 0)));
         data.m_128473_("fantastic_saved_gm");
      }

      Room.recoverStrandedReturn(p, room);
   }

   public static void onLogout(ServerPlayer p) {
      clearInvite(p.m_20148_());
      SHADERED.remove(p.m_20148_());
      Arenas.clearEdit(p);
      Arenas.clearWand(p);
      Stats.onLogout(p);
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room != null) {
         room.onLogout(p);
         if (!room.inProgress()) {
            MinecraftServer server = p.m_9236_().m_7654_();
            leaveInternal(server, p);
            pushRoom(server, room);
         } else if (room.isOwner(p.m_20148_())) {
            room.reassignOwnerAwayFrom(p.m_20148_(), p.m_9236_().m_7654_());
            pushRoom(p.m_9236_().m_7654_(), room);
         }
      }
   }

   public static void forceJoinAllDummies(ServerPlayer by, String name) {
      Room room = byName(name);
      if (room == null) {
         tell(by, Component.m_237110_("fantastic.room.not_found", new Object[]{name}), ChatFormatting.RED);
      } else if (room.inProgress()) {
         tell(by, Component.m_237115_("fantastic.room.in_progress"), ChatFormatting.RED);
      } else {
         MinecraftServer server = by.m_9236_().m_7654_();
         int added = 0;

         for (UUID id : DummyPlayer.all()) {
            if (room.isFull()) {
               break;
            }

            ServerPlayer bot = server.m_6846_().m_11259_(id);
            if (bot != null && MEMBER_ROOM.get(id) != room) {
               leaveInternal(server, bot);
               addMember(room, bot);
               added++;
            }
         }

         tell(by, Component.m_237110_("fantastic.dummy.forcejoined", new Object[]{added, room.name()}), ChatFormatting.GREEN);
      }
   }

   public static void setRole(ServerPlayer by, ServerPlayer target, Room.Role role) {
      Room room = MEMBER_ROOM.get(target.m_20148_());
      if (room != null && !room.inProgress()) {
         room.setRole(target.m_20148_(), role);
         tell(by, Component.m_237110_("fantastic.room.set_role", new Object[]{target.m_7755_().getString(), roleName(role)}), ChatFormatting.GREEN);
         tell(target, Component.m_237110_("fantastic.room.role_set_in", new Object[]{roleName(role), room.name()}), ChatFormatting.AQUA);
         pushRoom(by.m_9236_().m_7654_(), room);
      } else {
         tell(by, Component.m_237110_("fantastic.room.target_not_lobby", new Object[]{target.m_7755_().getString()}), ChatFormatting.RED);
      }
   }

   public static void start(ServerPlayer by, String name) {
      Room room = name == null ? MEMBER_ROOM.get(by.m_20148_()) : byName(name);
      if (room == null) {
         tell(
            by,
            name == null ? Component.m_237115_("fantastic.room.not_in") : Component.m_237110_("fantastic.room.not_found", new Object[]{name}),
            ChatFormatting.RED
         );
      } else if (!canManage(by, room)) {
         tell(by, Component.m_237115_("fantastic.room.start_denied"), ChatFormatting.RED);
      } else {
         room.start(by.m_9236_().m_7654_());
      }
   }

   public static void stop(ServerPlayer by, String name) {
      Room room = name == null ? MEMBER_ROOM.get(by.m_20148_()) : byName(name);
      if (room == null) {
         tell(
            by,
            name == null ? Component.m_237115_("fantastic.room.not_in") : Component.m_237110_("fantastic.room.not_found", new Object[]{name}),
            ChatFormatting.RED
         );
      } else if (!canManage(by, room)) {
         tell(by, Component.m_237115_("fantastic.room.stop_denied"), ChatFormatting.RED);
      } else {
         room.stop(by.m_9236_().m_7654_());
         tell(by, Component.m_237110_("fantastic.room.stopped", new Object[]{room.name()}), ChatFormatting.GRAY);
      }
   }

   public static List<String> names() {
      List<String> out = new ArrayList<>(ROOMS.size());

      for (Room r : ROOMS.values()) {
         out.add(r.name());
      }

      return out;
   }

   public static void list(ServerPlayer to) {
      if (ROOMS.isEmpty()) {
         tell(to, Component.m_237115_("fantastic.room.list_empty"), ChatFormatting.GRAY);
      } else {
         tell(to, Component.m_237115_("fantastic.room.list_header"), ChatFormatting.YELLOW);
         MinecraftServer server = to.m_9236_().m_7654_();
         boolean op = OP.test(to.m_20203_());

         for (Room r : ROOMS.values()) {
            StringBuilder members = new StringBuilder();

            for (UUID id : r.roster()) {
               ServerPlayer mp = server.m_6846_().m_11259_(id);
               if (members.length() > 0) {
                  members.append(", ");
               }

               members.append(r.isOwner(id) ? "★" : "").append(mp != null ? mp.m_36316_().getName() : id.toString().substring(0, 8));
            }

            MutableComponent row = Component.m_237113_("  ")
               .m_7220_(
                  Component.m_237113_("[" + r.name() + (r.hasPassword() ? " \ud83d\udd12" : "") + "]")
                     .m_130940_(r.inProgress() ? ChatFormatting.AQUA : ChatFormatting.GREEN)
                     .m_130938_(
                        s -> s.m_131144_(
                              new HoverEvent(
                                 net.minecraft.network.chat.HoverEvent.Action.f_130831_,
                                 Component.m_237113_(members.length() > 0 ? members.toString() : "(empty)")
                              )
                           )
                     )
               )
               .m_7220_(Component.m_237113_("  §7" + r.size() + "/" + r.capacity() + " · " + (r.inProgress() ? "§brunning" : "§7lobby")));
            if (op || r.isOwner(to.m_20148_())) {
               if (r.inProgress()) {
                  row.m_7220_(Component.m_237113_(" "));
               }

               row.m_7220_(Component.m_237113_(" "));
            }

            to.m_213846_(row);
         }
      }
   }

   public static void showConfig(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room == null) {
         tell(p, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.RED);
      } else {
         Room.Config c = room.config();
         tell(
            p,
            Component.m_237110_(
               "fantastic.cfg.show",
               new Object[]{
                  room.name(),
                  c.hideSecs,
                  c.seekSecs,
                  c.maxSeekers,
                  c.maxHiders,
                  c.maxRoom,
                  c.whistleSecs,
                  c.revealSecs,
                  c.ammoLimit == 0 ? "∞" : Integer.toString(c.ammoLimit),
                  String.format("%.2fs", (double)c.shotCooldown / 20.0)
               }
            ),
            ChatFormatting.AQUA
         );
         tell(
            p,
            Component.m_237110_(
               "fantastic.cfg.show_filters", new Object[]{(c.filters & 1) != 0, (c.filters & 2) != 0, (c.filters & 4) != 0, c.textureBrush != 0}
            ),
            ChatFormatting.AQUA
         );
         tell(
            p,
            Component.m_237115_("fantastic.ui.gamemode").m_130946_(": ").m_7220_(Component.m_237115_(PropHunt.nameKey(c.gameMode))),
            ChatFormatting.AQUA
         );
      }
   }

   public static void setConfig(ServerPlayer by, String field, int value) {
      setConfig(by, field, value, false);
   }

   public static void setConfig(ServerPlayer by, String field, int value, boolean quiet) {
      MinecraftServer srv = by.m_9236_().m_7654_();
      if (quiet && srv != null) {
         Integer last = LAST_CFG_TICK.put(by.m_20148_(), srv.m_129921_());
         if (last != null && last == srv.m_129921_()) {
            return;
         }
      }

      Room room = MEMBER_ROOM.get(by.m_20148_());
      if (room == null) {
         tell(by, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.RED);
      } else if (!canManage(by, room)) {
         tell(by, Component.m_237115_("fantastic.cfg.denied"), ChatFormatting.RED);
      } else {
         Room.Config c = room.config();
         switch (field) {
            case "hide":
               c.hideSecs = Math.max(0, value);
               break;
            case "seek":
               c.seekSecs = Mth.m_14045_(value, 5, 86400);
               break;
            case "maxseekers":
               c.maxSeekers = Mth.m_14045_(value, 1, 128);
               break;
            case "maxhiders":
               tell(by, Component.m_237110_("fantastic.cfg.derived", new Object[]{field}), ChatFormatting.RED);
               return;
            case "maxplayers":
            case "maxroom":
               int cap = DonorFeatures.roomCap(by.m_20148_());
               if (value > cap) {
                  tell(by, Component.m_237115_("fantastic.room.cap_donor"), ChatFormatting.GOLD);
                  c.maxRoom = Math.max(Math.max(2, room.size()), cap);
               } else {
                  c.maxRoom = Math.max(Math.max(2, room.size()), value);
               }
               break;
            case "whistle":
               c.whistleSecs = Math.max(0, value);
               break;
            case "whistlefx":
               c.whistleParticles = value != 0 ? 1 : 0;
               break;
            case "whistlearrow":
               c.whistleArrow = value != 0 ? 1 : 0;
               break;
            case "whistlewindow":
               c.whistleWindow = Math.max(5, Math.min(100, value));
               break;
            case "reveal":
               c.revealSecs = Math.max(0, value);
               break;
            case "ammo":
               c.ammoLimit = Mth.m_14045_(value, 0, 9999);
               break;
            case "shotcd":
               c.shotCooldown = Mth.m_14045_(value, 1, 6000);
               break;
            case "shotpenalty":
               c.shotPenalty = value != 0 ? 1 : 0;
               break;
            case "sightslow":
               c.sightSlow = value != 0 ? 1 : 0;
               break;
            case "infection":
               c.infection = value != 0 ? 1 : 0;
               break;
            case "elimdimension":
               c.elimOnDimension = value != 0 ? 1 : 0;
               break;
            case "manualroles":
               c.manualRoles = value != 0 ? 1 : 0;
               break;
            case "gamemode":
               if (room.phase() != Room.Phase.LOBBY) {
                  tell(by, Component.m_237115_("fantastic.cfg.gamemode_locked"), ChatFormatting.RED);
                  return;
               }

               c.gameMode = PropHunt.normalize(value);
               PropHuntRules.onGameModeChanged(srv, room, c.gameMode);
               break;
            case "monochrome":
               c.filters = setBit(c.filters, 1, value != 0);
               break;
            case "pixelize":
               c.filters = setBit(c.filters, 2, value != 0);
               break;
            case "eightbit":
               c.filters = setBit(c.filters, 4, value != 0);
               break;
            case "texturebrush":
               c.textureBrush = value != 0 ? 1 : 0;
               break;
            case "pool":
            case "battleroyale":
               c.startPool = Mth.m_14045_(value, 0, 100000);
               break;
            case "pooldecay":
            case "brdrain":
               c.poolDecay = Mth.m_14045_(value, 0, 1000);
               break;
            default:
               tell(by, Component.m_237110_("fantastic.cfg.unknown", new Object[]{field}), ChatFormatting.RED);
               return;
         }

         if (!quiet) {
            tell(by, Component.m_237110_("fantastic.cfg.set", new Object[]{field, currentValue(c, field), room.name()}), ChatFormatting.GREEN);
         }

         if (quiet) {
            pushRoomThrottled(srv, room);
         } else {
            pushRoom(srv, room);
         }

         if (field.equals("monochrome") || field.equals("pixelize") || field.equals("eightbit")) {
            warnShaderedMembers(srv, room);
         }
      }
   }

   public static void menuAction(ServerPlayer p, String action, String a, String b) {
      MinecraftServer srv = p.m_9236_().m_7654_();
      if (srv != null) {
         Integer last = LAST_ACTION_TICK.put(p.m_20148_(), srv.m_129921_());
         if (last != null && srv.m_129921_() - last < 2) {
            return;
         }
      }

      String name = a.isBlank() ? null : a;
      String pwd = b.isBlank() ? null : b;
      switch (action) {
         case "create":
            create(p, name, pwd);
            break;
         case "join":
            if (name != null) {
               join(p, name, pwd);
            }
            break;
         case "leave":
            leave(p);
            break;
         case "delete":
            if (name != null) {
               delete(p, name, true);
            }
            break;
         case "kick":
            if (name != null) {
               menuKick(p, name, false);
            }
            break;
         case "ban":
            if (name != null) {
               menuKick(p, name, true);
            }
            break;
         case "role":
            setOwnRole(p, "seeker".equals(a) ? Room.Role.SEEKER : Room.Role.HIDER);
            break;
         case "invite":
            if (name != null) {
               invite(p, name);
            }
            break;
         case "spectate":
            spectate(p);
            break;
         case "start":
            start(p, null);
            break;
         case "stop":
            stop(p, null);
            break;
         case "arena":
            switch (a) {
               case "marker":
                  Room r = MEMBER_ROOM.get(p.m_20148_());
                  if (r != null && canManage(p, r)) {
                     Arenas.markRoomArea(p);
                  }
                  break;
               case "pos1":
                  setArenaCorner(p, 1);
                  break;
               case "pos2":
                  setArenaCorner(p, 2);
                  break;
               case "clear":
                  clearArena(p);
                  break;
               case "use":
                  if (pwd != null) {
                     selectArena(p, pwd, true);
                  }
            }
      }
   }

   private static void pushRoomThrottled(MinecraftServer server, Room room) {
      if (server != null) {
         int now = server.m_129921_();
         Integer last = PUSH_AT.get(room);
         if (last != null && now - last < 2) {
            PUSH_PENDING.add(room);
         } else {
            PUSH_AT.put(room, now);
            PUSH_PENDING.remove(room);
            pushRoom(server, room);
         }
      }
   }

   private static void flushRoomPushes(MinecraftServer server) {
      if (!PUSH_PENDING.isEmpty()) {
         for (Room room : new ArrayList<>(PUSH_PENDING)) {
            if (ROOMS.containsValue(room)) {
               pushRoomThrottled(server, room);
            } else {
               PUSH_PENDING.remove(room);
            }
         }
      }
   }

   private static int currentValue(Room.Config c, String field) {
      return switch (field) {
         case "hide" -> c.hideSecs;
         case "seek" -> c.seekSecs;
         case "maxseekers" -> c.maxSeekers;
         case "maxhiders" -> Math.max(1, c.maxRoom - c.maxSeekers);
         case "maxplayers", "maxroom" -> c.maxRoom;
         case "whistle" -> c.whistleSecs;
         case "whistlefx" -> c.whistleParticles;
         case "whistlearrow" -> c.whistleArrow;
         case "whistlewindow" -> c.whistleWindow;
         case "reveal" -> c.revealSecs;
         case "ammo" -> c.ammoLimit;
         case "shotcd" -> c.shotCooldown;
         case "shotpenalty" -> c.shotPenalty;
         case "sightslow" -> c.sightSlow;
         case "infection" -> c.infection;
         case "elimdimension" -> c.elimOnDimension;
         case "manualroles" -> c.manualRoles;
         case "gamemode" -> c.gameMode;
         case "texturebrush" -> c.textureBrush;
         case "pool", "battleroyale" -> c.startPool;
         case "pooldecay", "brdrain" -> c.poolDecay;
         case "monochrome" -> (c.filters & 1) != 0 ? 1 : 0;
         case "pixelize" -> (c.filters & 2) != 0 ? 1 : 0;
         case "eightbit" -> (c.filters & 4) != 0 ? 1 : 0;
         default -> 0;
      };
   }

   private static int setBit(int mask, int bit, boolean on) {
      return on ? mask | bit : mask & ~bit;
   }

   static void renameArenaRefs(String oldName, String newName) {
      for (Room room : ROOMS.values()) {
         Room.Config c = room.config();
         if (oldName.equalsIgnoreCase(c.arenaName)) {
            c.arenaName = newName;
         }
      }
   }

   public static void leaveBlockPose(ServerPlayer p) {
      boolean wasBlock = Boolean.TRUE.equals(Services.PLATFORM.getOrNull(p, PaintAttachments.BLOCK_FORM));
      Services.PLATFORM.set(p, PaintAttachments.BLOCK_FORM, false);
      restoreStashedHelmet(p);
      if (wasBlock) {
         ArmorPaintHandler.updateShrink(p);
         GlobalSettings.applyNametagTeam(p);
      }
   }

   private static void restoreStashedHelmet(ServerPlayer p) {
      ItemStack orig = STASHED_HELMET.remove(p.m_20148_());
      if (orig != null) {
         ItemStack head = p.m_6844_(EquipmentSlot.HEAD);
         if (head.m_150930_(FantasticItems.CHAMELEON_HELMET.get()) && ChameleonArmor.resolutionOf(head) > 64) {
            BodyCanvas master = Services.PLATFORM.get(p, PaintAttachments.BODY_CANVAS);
            if (!master.isEmpty()) {
               Comp.set(orig, PaintComponents.CANVAS.get(), master);
            }

            p.m_8061_(EquipmentSlot.HEAD, orig);
         }
      }
   }

   private static BodyCanvas seedBlockCanvas(ItemStack helmet) {
      BodyCanvas base = Comp.get(helmet, PaintComponents.CANVAS.get());
      int[] src = base != null ? base.pixels() : BodyCanvas.EMPTY.pixels();
      return new BodyCanvas(BodyCanvas.resample(src, 128));
   }

   public static void clearBlockMemory(UUID id) {
      STASHED_HELMET.remove(id);
   }

   public static void enforceBlockHelmet(ServerPlayer p) {
      if (Boolean.TRUE.equals(Services.PLATFORM.getOrNull(p, PaintAttachments.BLOCK_FORM)) && !ChameleonArmor.wearsAnyHelmet(p)) {
         FantasticNetwork.resetPose(p);
         Services.PLATFORM.sendToClient(p, ForceExitPayload.INSTANCE);
         tell(p, Component.m_237115_("fantastic.block.off_helmet"), ChatFormatting.GRAY);
      }
   }

   public static boolean inActiveRound(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      return room != null && room.inProgress();
   }

   public static boolean isRoundInteractAllowed(BlockState state) {
      Block b = state.m_60734_();
      return b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock;
   }

   public static boolean blocksSpawnPoint(ServerPlayer p, BlockState state) {
      Block b = state.m_60734_();
      return (b instanceof BedBlock || b instanceof RespawnAnchorBlock) && p.m_9236_().m_46472_().equals(Arenas.ARENA_DIM);
   }

   public static void warnSpawnPoint(ServerPlayer p) {
      tell(p, Component.m_237115_("fantastic.bed.no_arena"), ChatFormatting.GRAY);
   }

   public static boolean blocksEntityUse(ServerPlayer p, Entity target) {
      if (target instanceof Player) {
         return false;
      } else if (inActiveRound(p)) {
         return true;
      } else {
         return p.m_7500_() ? false : SpongeSchematic.isStatue(target) || p.m_9236_().m_46472_().equals(Arenas.ARENA_DIM);
      }
   }

   public static void toggleCrawl(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      boolean allowed = room != null && room.inProgress() && room.isSeeker(p.m_20148_());
      boolean on = allowed && !Services.PLATFORM.get(p, PaintAttachments.CRAWLING);
      if (on && (Services.PLATFORM.get(p, PaintAttachments.POSING) || Services.PLATFORM.get(p, PaintAttachments.LOCKED))) {
         FantasticNetwork.resetPose(p);
         Services.PLATFORM.sendToClient(p, ForceExitPayload.INSTANCE);
      }

      Services.PLATFORM.set(p, PaintAttachments.CRAWLING, on);
      p.m_6210_();
   }

   public static boolean managesBot(ServerPlayer caller, UUID bot) {
      Room room = MEMBER_ROOM.get(caller.m_20148_());
      return room != null && !room.inProgress() && room.isMember(bot) && canManageBots(caller);
   }

   public static boolean addDummy(ServerPlayer dummy, ServerPlayer caller) {
      Room room = MEMBER_ROOM.get(caller.m_20148_());
      if (room != null && !room.inProgress() && !room.isFull() && canManageBots(caller)) {
         addMember(room, dummy);
         return true;
      } else {
         return false;
      }
   }

   public static int clearDummies(ServerPlayer caller) {
      Room room = MEMBER_ROOM.get(caller.m_20148_());
      return room != null && canManageBots(caller) ? room.removeDummies(caller.m_9236_().m_7654_()) : 0;
   }

   public static void feature(ServerPlayer by, String name) {
      Room room = byName(name);
      if (room == null) {
         tell(by, Component.m_237110_("fantastic.room.not_found", new Object[]{name}), ChatFormatting.RED);
      } else {
         String k = key(room.name());
         boolean on = !k.equals(featuredRoom);
         featuredRoom = on ? k : null;
         tell(by, Component.m_237110_(on ? "fantastic.room.featured" : "fantastic.room.unfeatured", new Object[]{room.name()}), ChatFormatting.GREEN);
         MinecraftServer srv = by.m_9236_().m_7654_();
         if (srv != null) {
            pushAll(srv);
         }
      }
   }

   public static RoomsPayload snapshot(ServerPlayer viewer) {
      Room mine = MEMBER_ROOM.get(viewer.m_20148_());
      MinecraftServer server = viewer.m_9236_().m_7654_();
      List<RoomSummary> list = new ArrayList<>();

      for (Room r : ROOMS.values()) {
         int role = -1;
         if (r == mine) {
            UUID vid = viewer.m_20148_();
            role = r.isSpectator(vid) ? 2 : (r.isSeeker(vid) ? 1 : (r.isHider(vid) ? 0 : -1));
         }

         List<RoomMember> members = new ArrayList<>();

         for (UUID id : r.roster()) {
            ServerPlayer mp = server == null ? null : server.m_6846_().m_11259_(id);
            String name = mp != null ? mp.m_36316_().getName() : id.toString().substring(0, 8);
            members.add(new RoomMember(id, name, r.isOwner(id)));
         }

         list.add(
            new RoomSummary(
               r.name(),
               r.hasPassword(),
               r.size(),
               r.phase().ordinal(),
               r == mine,
               role,
               r.capacity(),
               members,
               key(r.name()).equals(featuredRoom),
               r.secondsLeft()
            )
         );
      }

      if (featuredRoom != null && !ROOMS.containsKey(featuredRoom)) {
         featuredRoom = null;
      }

      list.sort(Comparator.comparing(rs -> !rs.featured()));
      Room.Config c = mine != null ? mine.config() : new Room.Config();
      int derivedHiders = Math.max(1, c.maxRoom - c.maxSeekers);
      int[] cfg = new int[]{
         c.hideSecs,
         c.seekSecs,
         c.maxSeekers,
         c.whistleSecs,
         c.revealSecs,
         derivedHiders,
         c.maxRoom,
         c.textureBrush,
         c.ammoLimit,
         c.shotCooldown,
         c.infection,
         c.elimOnDimension,
         c.arenaSet ? 1 : 0,
         c.manualRoles,
         c.blockDisguise,
         c.whistleWindow,
         c.shotPenalty,
         c.sightSlow,
         c.whistleArrow,
         c.startPool,
         c.poolDecay,
         c.gameMode
      };
      int filters = mine != null ? c.filters : 0;
      int hiderCount = mine != null ? mine.countRole(Room.Role.HIDER) : 0;
      int seekerCount = mine != null ? mine.countRole(Room.Role.SEEKER) : 0;
      boolean canManage = mine != null && canManage(viewer, mine);
      boolean isOp = OP.test(viewer.m_20203_());
      List<String> arenaNames = Arenas.names();
      List<Boolean> arenaBusy = new ArrayList<>(arenaNames.size());
      List<Integer> recMin = new ArrayList<>(arenaNames.size());
      List<Integer> recMax = new ArrayList<>(arenaNames.size());
      String mineName = mine != null ? mine.name() : "";

      for (String an : arenaNames) {
         String occ = Arenas.occupant(an);
         arenaBusy.add(occ != null && !occ.equalsIgnoreCase(mineName));
         int[] rec = Arenas.recommendedPlayers(Arenas.get(an));
         recMin.add(rec[0]);
         recMax.add(rec[1]);
      }

      String selectedArena = mine != null ? mine.config().arenaName : "";
      return new RoomsPayload(
         list,
         mine != null ? mine.name() : "",
         cfg,
         filters,
         hiderCount,
         seekerCount,
         canManage,
         isOp,
         GlobalSettings.freeArena(),
         arenaNames,
         arenaBusy,
         recMin,
         recMax,
         selectedArena == null ? "" : selectedArena
      );
   }

   public static void pushAll(MinecraftServer server) {
      for (ServerPlayer p : server.m_6846_().m_11314_()) {
         pushTo(p);
      }
   }

   public static void pushTo(ServerPlayer p) {
      if (p != null) {
         Services.PLATFORM.sendToClient(p, snapshot(p));
      }
   }

   private static void pushRoom(MinecraftServer server, Room room) {
      for (ServerPlayer p : room.onlineMembers(server)) {
         pushTo(p);
      }
   }

   public static void onShaderState(ServerPlayer p, boolean active) {
      boolean was = SHADERED.contains(p.m_20148_());
      if (active) {
         SHADERED.add(p.m_20148_());
      } else {
         SHADERED.remove(p.m_20148_());
      }

      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (active && !was && room != null && room.config().filters != 0) {
         room.broadcastShaderWarning(p.m_9236_().m_7654_(), p);
      }
   }

   private static void warnShaderedMembers(MinecraftServer server, Room room) {
      if (room.config().filters != 0) {
         for (ServerPlayer p : room.onlineMembers(server)) {
            if (SHADERED.contains(p.m_20148_())) {
               room.broadcastShaderWarning(server, p);
            }
         }
      }
   }

   public static void setArenaCorner(ServerPlayer p, int which) {
      setArenaCornerAt(p, which, p.m_20183_());
   }

   public static boolean setArenaCornerAt(ServerPlayer p, int which, BlockPos at) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room == null) {
         tell(p, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.RED);
         return false;
      } else if (!canManage(p, room)) {
         tell(p, Component.m_237115_("fantastic.room.arena_denied"), ChatFormatting.RED);
         return false;
      } else {
         Room.Config c = room.config();
         String dim = p.m_9236_().m_46472_().m_135782_().toString();
         c.arenaName = "";
         if (which == 1) {
            c.ax1 = at.m_123341_();
            c.ay1 = at.m_123342_();
            c.az1 = at.m_123343_();
            c.arenaDim = dim;
            tell(
               p, Component.m_237110_("fantastic.room.arena_corner1", new Object[]{at.m_123341_(), at.m_123342_(), at.m_123343_(), dim}), ChatFormatting.GREEN
            );
         } else {
            if (c.arenaDim.isEmpty()) {
               c.arenaDim = dim;
            } else if (!c.arenaDim.equals(dim)) {
               tell(p, Component.m_237110_("fantastic.room.arena_corner2_dim", new Object[]{c.arenaDim}), ChatFormatting.RED);
               return false;
            }

            c.ax2 = at.m_123341_();
            c.ay2 = at.m_123342_();
            c.az2 = at.m_123343_();
            c.arenaSet = true;
            tell(p, Component.m_237115_("fantastic.room.arena_armed"), ChatFormatting.GREEN);
         }

         sendArena(p.m_9236_().m_7654_(), room);
         return true;
      }
   }

   public static void clearArena(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room == null || !canManage(p, room)) {
         tell(p, Component.m_237115_("fantastic.room.arena_change_denied"), ChatFormatting.RED);
      } else if (!GlobalSettings.freeArena()) {
         tell(p, Component.m_237115_("fantastic.room.free_disabled"), ChatFormatting.RED);
      } else {
         room.config().arenaSet = false;
         room.config().arenaDim = "";
         room.config().arenaName = "";
         tell(p, Component.m_237115_("fantastic.room.arena_cleared"), ChatFormatting.YELLOW);
         sendArena(p.m_9236_().m_7654_(), room);
      }
   }

   public static void selectArena(ServerPlayer p, String name) {
      selectArena(p, name, false);
   }

   public static void selectArena(ServerPlayer p, String name, boolean quiet) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room == null) {
         tell(p, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.RED);
      } else if (!canManage(p, room)) {
         tell(p, Component.m_237115_("fantastic.room.arena_denied"), ChatFormatting.RED);
      } else {
         Room.Config c = room.config();
         if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("none")) {
            if (Arenas.isRandom(name)) {
               c.arenaName = "*random*";
               c.arenaSet = false;
               if (!quiet) {
                  tell(p, Component.m_237115_("fantastic.room.arena_random"), ChatFormatting.GREEN);
               }

               sendArena(p.m_9236_().m_7654_(), room);
               pushRoom(p.m_9236_().m_7654_(), room);
            } else {
               Arena a = Arenas.get(name);
               if (a == null) {
                  tell(p, Component.m_237110_("fantastic.room.arena_no_saved", new Object[]{name}), ChatFormatting.RED);
               } else {
                  c.arenaName = a.name;
                  c.ax1 = a.x1;
                  c.ay1 = a.y1;
                  c.az1 = a.z1;
                  c.ax2 = a.x2;
                  c.ay2 = a.y2;
                  c.az2 = a.z2;
                  c.arenaDim = a.dim;
                  c.arenaSet = true;
                  boolean busy = Arenas.isOccupied(a.name) && !Arenas.occupant(a.name).equalsIgnoreCase(room.name());
                  if (!quiet || busy) {
                     tell(
                        p,
                        Component.m_237110_(busy ? "fantastic.room.arena_selected_busy" : "fantastic.room.arena_selected", new Object[]{a.name}),
                        ChatFormatting.GREEN
                     );
                  }

                  sendArena(p.m_9236_().m_7654_(), room);
                  pushRoom(p.m_9236_().m_7654_(), room);
               }
            }
         } else if (!GlobalSettings.freeArena()) {
            tell(p, Component.m_237115_("fantastic.room.free_disabled"), ChatFormatting.RED);
         } else {
            c.arenaName = "";
            c.arenaSet = false;
            c.arenaDim = "";
            if (!quiet) {
               tell(p, Component.m_237115_("fantastic.room.arena_cleared"), ChatFormatting.YELLOW);
            }

            sendArena(p.m_9236_().m_7654_(), room);
            pushRoom(p.m_9236_().m_7654_(), room);
         }
      }
   }

   private static void sendArena(MinecraftServer server, Room room) {
      Room.Config c = room.config();
      int state = c.arenaSet ? 2 : (c.arenaDim.isEmpty() ? 0 : 1);
      ArenaPayload payload = new ArenaPayload(state, c.ax1, c.ay1, c.az1, c.ax2, c.ay2, c.az2, c.arenaDim);

      for (ServerPlayer p : room.onlineMembers(server)) {
         if (!editingArena(p)) {
            Services.PLATFORM.sendToClient(p, payload);
         }
      }
   }

   private static void syncArenas(MinecraftServer server) {
      for (Room room : ROOMS.values()) {
         Room.Config c = room.config();
         if (c.arenaSet || !c.arenaDim.isEmpty()) {
            sendArena(server, room);
         }
      }
   }

   static boolean editingArena(ServerPlayer p) {
      return !Arenas.target(p).isEmpty();
   }

   public static void showArena(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room == null) {
         tell(p, Component.m_237115_("fantastic.room.not_in"), ChatFormatting.RED);
      } else {
         Room.Config c = room.config();
         if (!c.arenaSet) {
            tell(p, Component.m_237115_("fantastic.room.arena_show_none"), ChatFormatting.AQUA);
         } else {
            String tag = c.arenaName != null && !c.arenaName.isEmpty() ? "'" + c.arenaName + "' " : "";
            tell(
               p,
               Component.m_237110_("fantastic.room.arena_show", new Object[]{tag, c.ax1, c.ay1, c.az1, c.ax2, c.ay2, c.az2, c.arenaDim}),
               ChatFormatting.AQUA
            );
         }

         tell(p, Component.m_237110_("fantastic.room.arena_elim", new Object[]{c.infection != 0, c.elimOnDimension != 0}), ChatFormatting.AQUA);
      }
   }

   private static void eliminate(MinecraftServer server, ServerPlayer p, String reason) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (DummyPlayer.isDummy(p.m_20148_())) {
         if (room != null) {
            room.broadcastLeft(server, p, reason);
         }

         DummyPlayer.remove(server, p);
      } else if (room != null && room.inProgress() && room.isParticipant(p.m_20148_())) {
         room.knockOut(server, p, reason, true);
      } else {
         if (room != null) {
            room.broadcastLeft(server, p, reason);
         }

         tell(p, Component.m_237110_("fantastic.room.eliminated", new Object[]{reason}), ChatFormatting.RED);
         leaveInternal(server, p);
      }
   }

   public static void onDeath(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room != null && room.inProgress() && room.isParticipant(p.m_20148_())) {
         room.removeRoomGear(p);
         tell(p, Component.m_237115_("fantastic.room.died"), ChatFormatting.RED);
         PENDING_KNOCKOUT.add(p.m_20148_());
         room.knockOut(p.m_9236_().m_7654_(), p, "died", false);
      }
   }

   public static void onRespawn(ServerPlayer p) {
      FantasticItems.stripRoomGear(p);
      if (!PENDING_KNOCKOUT.contains(p.m_20148_())) {
         FantasticSpawn.onRespawn(p);
      }

      if (PENDING_KNOCKOUT.remove(p.m_20148_())) {
         Room room = MEMBER_ROOM.get(p.m_20148_());
         if (room != null) {
            if (room.isInfection()) {
               room.infectAfterRespawn(p);
            } else {
               room.beginSpectateAfterRespawn(p);
            }
         }
      }
   }

   public static boolean blocksEquip(ServerPlayer p, ItemStack held) {
      if (!held.m_41619_() && gearLocked(p)) {
         EquipmentSlot slot = ServerPlayer.m_147233_(held);
         if (slot.m_254934_() && FantasticItems.isRoomGear(p.m_6844_(slot))) {
            tellGearLocked(p);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean gearLocked(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      return room != null && room.inProgress() && room.isParticipant(p.m_20148_());
   }

   public static void tellGearLocked(ServerPlayer p) {
      MinecraftServer srv = p.m_9236_().m_7654_();
      int now = srv != null ? srv.m_129921_() : 0;
      Integer last = GEAR_NAG.put(p.m_20148_(), now);
      if (last == null || now - last > 60) {
         tell(p, Component.m_237115_("fantastic.room.gear_locked"), ChatFormatting.RED);
      }
   }

   public static boolean isStrayRoomGear(ServerPlayer p, ItemStack stack) {
      if (!FantasticItems.isRoomGear(stack)) {
         return false;
      } else {
         Room room = MEMBER_ROOM.get(p.m_20148_());
         return room == null || !room.inProgress() || !room.isParticipant(p.m_20148_());
      }
   }

   public static void onDimensionChange(ServerPlayer p) {
      Room room = MEMBER_ROOM.get(p.m_20148_());
      if (room != null && room.inProgress() && room.config().elimOnDimension != 0 && room.isParticipant(p.m_20148_()) && !room.config().arenaSet) {
         eliminate(p.m_9236_().m_7654_(), p, Component.m_237115_("fantastic.out.left_dimension").getString());
      }
   }

   private static void enforceArenas(MinecraftServer server) {
      for (Room room : ROOMS.values()) {
         if (room.inProgress() && room.config().arenaSet) {
            boolean anyRealSeekerInside = false;
            List<ServerPlayer> outSeekers = new ArrayList<>();

            for (UUID id : room.roster()) {
               ServerPlayer p = server.m_6846_().m_11259_(id);
               if (p != null) {
                  boolean inside = !room.outsideArena(p);
                  room.arenaCrossSound(p, inside);
                  if (room.isHider(id)) {
                     room.outsideBeep(server, p, inside, false);
                     if (room.tickHiderArena(p, inside)) {
                        eliminate(server, p, Component.m_237115_("fantastic.out.left_arena").getString());
                     }
                  } else if (room.isSeeker(id)) {
                     room.outsideBeep(server, p, inside, true);
                     boolean real = !DummyPlayer.isDummy(id);
                     if (inside && real) {
                        anyRealSeekerInside = true;
                     } else if (!inside && real) {
                        outSeekers.add(p);
                     }
                  }
               }
            }

            room.tickSeekerArena(server, anyRealSeekerInside, outSeekers);
         }
      }
   }

   public static void found(ServerPlayer hider, ServerPlayer by) {
      Room room = MEMBER_ROOM.get(by.m_20148_());
      if (room != null && room == MEMBER_ROOM.get(hider.m_20148_()) && room.isSeeker(by.m_20148_()) && room.isHider(hider.m_20148_())) {
         room.found(hider, by, by.m_9236_().m_7654_());
      }
   }

   public static void scheduleWelcome(ServerPlayer p) {
      WELCOME_DELAY.put(p.m_20148_(), 20);
   }

   private static void tickWelcome(MinecraftServer server) {
      if (!WELCOME_DELAY.isEmpty()) {
         Iterator<Entry<UUID, Integer>> it = WELCOME_DELAY.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> e = it.next();
            if (e.getValue() > 1) {
               e.setValue(e.getValue() - 1);
            } else {
               it.remove();
               ServerPlayer p = server.m_6846_().m_11259_(e.getKey());
               if (p != null) {
                  onLogin(p);
                  pushTo(p);
                  GlobalSettings.syncTo(p);
                  Stats.refreshSidebars(server);
               }
            }
         }
      }
   }

   public static void tick(MinecraftServer server) {
      for (Room room : ROOMS.values()) {
         room.tick(server);
      }

      flushRoomPushes(server);
      tickWelcome(server);
      Announcer.tick(server);
      Stats.tickSidebar(server);
      if (server.m_129921_() % 10 == 0) {
         enforceArenas(server);
         FantasticSpawn.rescueFromVoid(server);
      }

      if (server.m_129921_() % 20 == 0) {
         syncArenas(server);
         Arenas.tickViewers(server);

         for (Room room : new ArrayList<>(ROOMS.values())) {
            if (room.tickBotOnly(server)) {
               ROOMS.remove(key(room.name()));
               MEMBER_ROOM.values().removeIf(r -> r == room);
            }
         }
      }

      if (server.m_129921_() % 20 == 0) {
         sweepOfflineMembers(server);
         ROOMS.values().removeIf(r -> r.isEmpty() && !r.inProgress());
         PUSH_AT.keySet().removeIf(r -> !ROOMS.containsValue(r));
         LAST_CFG_TICK.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
         LAST_ACTION_TICK.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
         GEAR_NAG.keySet().removeIf(id -> server.m_6846_().m_11259_(id) == null);
         PWD_LOCKED_UNTIL.values().removeIf(until -> until <= server.m_129783_().m_46467_());
         PWD_FAILS.keySet().removeIf(id -> !PWD_LOCKED_UNTIL.containsKey(id) && server.m_6846_().m_11259_(id) == null);
         FantasticNetwork.sweepOfflinePlayers(server);
      }
   }

   private static void sweepOfflineMembers(MinecraftServer server) {
      for (Room room : new ArrayList<>(ROOMS.values())) {
         if (!room.inProgress()) {
            for (UUID id : room.roster()) {
               if (!DummyPlayer.isDummy(id)) {
                  if (server.m_6846_().m_11259_(id) != null) {
                     OFFLINE_TICKS.remove(id);
                  } else if (OFFLINE_TICKS.merge(id, 20, Integer::sum) >= 1200) {
                     OFFLINE_TICKS.remove(id);
                     MEMBER_ROOM.remove(id);
                     room.removeMember(server, id);
                  }
               }
            }
         }
      }

      OFFLINE_TICKS.keySet().removeIf(idx -> !MEMBER_ROOM.containsKey(idx));
   }

   public static void clear() {
      ROOMS.clear();
      MEMBER_ROOM.clear();
      SHADERED.clear();
      OFFLINE_TICKS.clear();
      LAST_CFG_TICK.clear();
      LAST_ACTION_TICK.clear();
      PUSH_AT.clear();
      PUSH_PENDING.clear();
      barrierCacheTick = Long.MIN_VALUE;
      barrierRoomsByDimension = Map.of();
      seq = 0;
   }

   private static void addMember(Room room, ServerPlayer p) {
      room.addMember(p.m_20148_());
      MEMBER_ROOM.put(p.m_20148_(), room);
      pushAll(p.m_9236_().m_7654_());
      if (room.config().filters != 0 && SHADERED.contains(p.m_20148_())) {
         room.broadcastShaderWarning(p.m_9236_().m_7654_(), p);
      }
   }

   private static void addSpectator(Room room, ServerPlayer p) {
      MEMBER_ROOM.put(p.m_20148_(), room);
      room.addSpectatorMidGame(p.m_9236_().m_7654_(), p);
      pushAll(p.m_9236_().m_7654_());
   }

   private static void leaveInternal(MinecraftServer server, ServerPlayer p) {
      Room room = MEMBER_ROOM.remove(p.m_20148_());
      leaveBlockPose(p);
      if (room != null) {
         room.removeMember(server, p.m_20148_());
         pushAll(server);
      }
   }

   public static void delete(ServerPlayer by, String name) {
      delete(by, name, false);
   }

   public static void delete(ServerPlayer by, String name, boolean confirmed) {
      Room room = byName(name);
      if (room == null) {
         tell(by, Component.m_237110_("fantastic.room.not_found", new Object[]{name}), ChatFormatting.RED);
      } else if (!room.isOwner(by.m_20148_()) && !OP.test(by.m_20203_())) {
         tell(by, Component.m_237115_("fantastic.room.delete_denied"), ChatFormatting.RED);
      } else if (!confirmed) {
         by.m_213846_(
            Component.m_237110_("fantastic.room.delete_confirm", new Object[]{room.name()})
               .m_130940_(ChatFormatting.YELLOW)
               .m_7220_(Component.m_237113_(" "))
               .m_7220_(
                  Arenas.button(
                     Component.m_237115_("fantastic.arena.btn.confirm_delete"),
                     "/fschameleon borrar \"" + room.name() + "\" confirmar",
                     Component.m_237110_("fantastic.arena.btn.confirm_delete_hover", new Object[]{room.name()}),
                     ChatFormatting.RED
                  )
               )
         );
      } else {
         MinecraftServer server = by.m_9236_().m_7654_();
         room.stop(server);

         for (UUID id : new ArrayList<>(MEMBER_ROOM.keySet())) {
            if (MEMBER_ROOM.get(id) == room) {
               MEMBER_ROOM.remove(id);
               room.removeMember(server, id);
               pushTo(server.m_6846_().m_11259_(id));
            }
         }

         ROOMS.remove(key(name));
         pushAll(server);
         tell(by, Component.m_237110_("fantastic.room.deleted", new Object[]{name}), ChatFormatting.GREEN);
      }
   }

   private static void tell(ServerPlayer p, String text, ChatFormatting color) {
      tell(p, Component.m_237113_(text), color);
   }

   private static void tell(ServerPlayer p, Component text, ChatFormatting color) {
      p.m_5661_(text.m_6881_().m_130940_(color), true);
   }

   private static Component roleName(Room.Role role) {
      return Component.m_237115_(role == Room.Role.SEEKER ? "fantastic.roleword.seeker" : "fantastic.roleword.hider");
   }

   private static record Invite(String room, UUID from, int expires) {
   }
}
