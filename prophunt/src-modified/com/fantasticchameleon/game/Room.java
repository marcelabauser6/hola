package com.fantasticchameleon.game;

import com.fantasticchameleon.FantasticChameleon;
import com.fantasticchameleon.compat.Nbt;
import com.fantasticchameleon.entity.DummyPlayer;
import com.fantasticchameleon.item.ArmorPaintHandler;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.network.ForceExitPayload;
import com.fantasticchameleon.network.MapRollPayload;
import com.fantasticchameleon.network.RoundStatePayload;
import com.fantasticchameleon.network.SeekerDraftPayload;
import com.fantasticchameleon.network.WhistlePayload;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.prophunt.PropHunt;
import com.fantasticchameleon.pose.LockTick;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class Room {
   private static final int SCORE_INTERVAL = 2;
   private static final double FOV_COS = 0.5;
   private static final double SCORE_PER_STEP = 1.0;
   private static final double UNLOCKED_SCORE_FACTOR = 0.25;
   private static final int COUNTDOWN_SECS = 5;
   private static final int SEEKER_GRACE_TICKS = 600;
   private static final int HIDER_GRACE_TICKS = 200;
   private static final int HIDE_WARN_TICKS = 200;
   private static final double SCATTER_RADIUS = 3.0;
   private static final int TP_BATCH = 10;
   private static final int TP_BATCH_TICKS = 20;
   private final String name;
   private final String password;
   private final String objName;
   private final Room.Config config = new Room.Config();
   private UUID owner;
   private Room.Phase phase = Room.Phase.LOBBY;
   private final Set<UUID> roster = new HashSet<>();
   private final Set<UUID> banned = new HashSet<>();
   private final Set<UUID> hiders = new HashSet<>();
   private final Set<UUID> seekers = new HashSet<>();
   private final Map<UUID, Double> scores = new HashMap<>();
   private final Map<UUID, Double> pendingScore = new HashMap<>();
   private final Map<UUID, Double> totalScores = new HashMap<>();
   private final Map<UUID, Room.Role> preAssigned = new HashMap<>();
   private final Set<UUID> chosenRole = new HashSet<>();
   private final Set<UUID> spectators = new HashSet<>();
   private final ArrayDeque<UUID> tpQueue = new ArrayDeque<>();
   private int tpDelay = 0;
   private final Map<UUID, GameType> savedGameType = new HashMap<>();
   private final Map<UUID, Integer> savedReturn = new HashMap<>();
   private final Map<UUID, Integer> spectateIdx = new HashMap<>();
   private final Set<UUID> spectateActive = new HashSet<>();
   private final Set<UUID> revealTargets = new HashSet<>();
   private final Set<UUID> glowTargets = new HashSet<>();
   private final Map<UUID, Integer> ammo = new HashMap<>();
   private int foundCount;
   private boolean seekerless;
   private int seekersOutTicks;
   private final Map<UUID, Integer> hidersOutTicks = new HashMap<>();
   private final Set<UUID> outOfArena = new HashSet<>();
   private int timer;
   private int whistleTimer;
   private boolean whistleUiVisible;
   private int botOnlyTicks;
   private Arena activeArena;
   public static final String SAVED_GM_KEY = "fantastic_saved_gm";
   private static final double SIGHT_SLOW_MIN = 0.2;
   private static final int SIGHT_SLOW_TICKS = 60;
   private static final int SIGHT_SLOW_LEVEL = 1;
   private static final double WHISTLE_SCORE = 25.0;
   private static final double WHISTLE_RANGE = 24.0;
   private static final int POOL_WARN = 10;
   public static final String ADV_GM_KEY = "fantastic_adv_gm";

   Room(String name, String password, int id) {
      this.name = name;
      this.password = password;
      this.objName = "mc" + id;
   }

   public String name() {
      return this.name;
   }

   public Room.Phase phase() {
      return this.phase;
   }

   public int secondsLeft() {
      return this.phase == Room.Phase.LOBBY ? 0 : Math.max(0, this.timer / 20);
   }

   public Room.Config config() {
      return this.config;
   }

   public boolean inProgress() {
      return this.phase != Room.Phase.LOBBY;
   }

   public boolean hasPassword() {
      return this.password != null;
   }

   public boolean checkPassword(String given) {
      return this.password == null || this.password.equals(given);
   }

   public boolean isMember(UUID id) {
      return this.roster.contains(id);
   }

   public UUID owner() {
      return this.owner;
   }

   public boolean isOwner(UUID id) {
      return id != null && id.equals(this.owner);
   }

   public boolean isBanned(UUID id) {
      return id != null && this.banned.contains(id);
   }

   void ban(UUID id) {
      if (id != null && !id.equals(this.owner)) {
         this.banned.add(id);
      }
   }

   void setOwner(UUID id) {
      this.owner = id;
   }

   void reassignOwnerAwayFrom(UUID leaving, MinecraftServer server) {
      UUID next = this.roster.stream().filter(m -> !m.equals(leaving) && !DummyPlayer.isDummy(m) && resolve(server, m) != null).findFirst().orElse(null);
      if (next != null) {
         this.owner = next;
      }
   }

   public List<UUID> roster() {
      return new ArrayList<>(this.roster);
   }

   public boolean isEmpty() {
      return this.roster.isEmpty();
   }

   public int size() {
      return this.roster.size();
   }

   public int capacity() {
      return this.config.maxRoom;
   }

   public boolean isFull() {
      return this.roster.size() >= this.config.maxRoom;
   }

   public boolean isHider(UUID id) {
      return this.hiders.contains(id);
   }

   public boolean isSeeker(UUID id) {
      return this.seekers.contains(id);
   }

   public boolean isSpectator(UUID id) {
      return this.spectators.contains(id);
   }

   /** Autoridad unica para capturar, fijar o animar un prop durante una ronda. */
   public boolean canUseProp(UUID id) {
      return id != null
         && PropHunt.normalize(this.config.gameMode) == PropHunt.MODE_PROP_HUNT
         && this.hiders.contains(id)
         && !this.spectators.contains(id)
         && (this.phase == Room.Phase.COUNTDOWN || this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING);
   }

   void addMember(UUID id) {
      this.roster.add(id);
   }

   void removeMember(MinecraftServer server, UUID id) {
      this.exitSpectator(server, id);
      this.endReturn(server, id);
      boolean wasHider = this.hiders.contains(id);
      ServerPlayer leaving = resolve(server, id);
      if (leaving != null) {
         this.removeRoomGear(leaving);
         Stash.restore(leaving);
         resetAvatar(leaving);
      }

      this.roster.remove(id);
      this.hiders.remove(id);
      this.seekers.remove(id);
      this.preAssigned.remove(id);
      this.chosenRole.remove(id);
      this.spectators.remove(id);
      this.scores.remove(id);
      this.totalScores.remove(id);
      this.ammo.remove(id);
      if (id.equals(this.owner)) {
         this.owner = this.roster.stream().filter(m -> !DummyPlayer.isDummy(m)).findFirst().orElse(null);
      }

      if (wasHider && this.hiders.isEmpty() && (this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING)) {
         this.end(server, true);
      }

      ServerPlayer p = resolve(server, id);
      if (p != null) {
         Sidebar.hide(p, this.objName);
         Services.PLATFORM.sendToClient(p, new RoundStatePayload(List.of(), 0, 0, 0, 0));
      }
   }

   boolean tickBotOnly(MinecraftServer server) {
      if (!this.inProgress() && !this.roster.isEmpty()) {
         boolean anyDummy = false;
         boolean anyHuman = false;

         for (UUID id : this.roster) {
            if (DummyPlayer.isDummy(id)) {
               anyDummy = true;
            } else {
               anyHuman = true;
               if (server.m_6846_().m_11259_(id) != null) {
                  this.botOnlyTicks = 0;
                  return false;
               }
            }
         }

         if (!anyDummy) {
            this.botOnlyTicks = 0;
            return false;
         } else if (!anyHuman) {
            this.removeDummies(server);
            return true;
         } else {
            this.botOnlyTicks += 20;
            if (this.botOnlyTicks >= 1200) {
               this.removeDummies(server);
               return true;
            } else {
               return false;
            }
         }
      } else {
         this.botOnlyTicks = 0;
         return false;
      }
   }

   int removeDummies(MinecraftServer server) {
      int n = 0;

      for (UUID id : new HashSet<>(this.roster)) {
         if (DummyPlayer.isDummy(id)) {
            ServerPlayer dummy = resolve(server, id);
            if (dummy != null) {
               DummyPlayer.remove(server, dummy);
               n++;
            }
         }
      }

      return n;
   }

   void setRole(UUID id, Room.Role role) {
      this.preAssigned.put(id, role);
      this.chosenRole.add(id);
      this.spectators.remove(id);
   }

   private boolean choseOwnRole(UUID id) {
      return this.chosenRole.contains(id) && this.preAssigned.get(id) != null;
   }

   public Room.Role assignedRole(UUID id) {
      return this.preAssigned.get(id);
   }

   public int countRole(Room.Role role) {
      if (this.inProgress()) {
         return role == Room.Role.SEEKER ? this.seekers.size() : this.hiders.size();
      } else {
         int n = 0;

         for (Room.Role r : this.preAssigned.values()) {
            if (r == role) {
               n++;
            }
         }

         return n;
      }
   }

   public int teamMax(Room.Role role) {
      return role == Room.Role.SEEKER ? this.config.maxSeekers : this.maxHiders();
   }

   public int maxHiders() {
      return Math.max(1, this.config.maxRoom - this.config.maxSeekers);
   }

   public boolean canJoinTeam(UUID id, Room.Role role) {
      return this.preAssigned.get(id) == role || this.countRole(role) < this.teamMax(role);
   }

   void start(MinecraftServer server) {
      if (!this.inProgress()) {
         this.seekerless = false;
         List<ServerPlayer> players = this.onlineMembers(server);
         List<ServerPlayer> active = new ArrayList<>();

         for (ServerPlayer p : players) {
            if (!this.spectators.contains(p.m_20148_())) {
               active.add(p);
            }
         }

         if (active.size() < 2) {
            this.broadcast(server, Component.m_237115_("fantastic.game.need_players"), ChatFormatting.RED);
         } else if (GlobalSettings.freeArena() || this.config.arenaSet || this.config.arenaName != null && !this.config.arenaName.isEmpty()) {
            List<ServerPlayer> realActive = new ArrayList<>();

            for (ServerPlayer px : active) {
               if (!DummyPlayer.isDummy(px.m_20148_())) {
                  realActive.add(px);
               }
            }

            if (realActive.isEmpty()) {
               this.broadcast(server, Component.m_237115_("fantastic.game.need_seeker"), ChatFormatting.RED);
            } else {
               if (this.config.manualRoles != 0) {
                  boolean anySeeker = false;
                  boolean anyDraftable = false;

                  for (ServerPlayer pxx : active) {
                     UUID id = pxx.m_20148_();
                     if (this.preAssigned.get(id) == Room.Role.SEEKER && this.choseOwnRole(id)) {
                        anySeeker = true;
                        break;
                     }

                     if (!DummyPlayer.isDummy(id) && !this.choseOwnRole(id)) {
                        anyDraftable = true;
                     }
                  }

                  if (!anySeeker && !anyDraftable) {
                     this.broadcast(server, Component.m_237115_("fantastic.game.need_seeker"), ChatFormatting.RED);
                     return;
                  }
               }

               for (ServerPlayer pxxx : realActive) {
                  if (hasAnyArmor(pxxx)) {
                     this.broadcast(server, Component.m_237110_("fantastic.game.not_ready", new Object[]{pxxx.m_7755_().getString()}), ChatFormatting.RED);
                     return;
                  }
               }

               this.activeArena = null;
               String arenaPick = this.config.arenaName;
               String rolledFrom = null;
               if (Arenas.isRandom(arenaPick)) {
                  List<String> pool = Arenas.availableNames();
                  if (pool.isEmpty()) {
                     this.broadcast(server, Component.m_237115_("fantastic.game.arena_none_free"), ChatFormatting.RED);
                     return;
                  }

                  arenaPick = pool.get(new Random().nextInt(pool.size()));
                  rolledFrom = arenaPick;
               }

               if (arenaPick != null && !arenaPick.isEmpty()) {
                  Arena a = Arenas.get(arenaPick);
                  if (a == null) {
                     this.config.arenaName = "";
                     this.config.arenaSet = false;
                     this.broadcast(server, Component.m_237115_("fantastic.game.arena_gone"), ChatFormatting.YELLOW);
                  } else {
                     Arena inst = Arenas.acquire(server, arenaPick, this.name);
                     if (inst == null) {
                        this.broadcast(
                           server, Component.m_237110_("fantastic.game.arena_busy", new Object[]{a.name, Arenas.occupant(arenaPick)}), ChatFormatting.RED
                        );
                        return;
                     }

                     this.activeArena = inst;
                     this.config.ax1 = inst.x1;
                     this.config.ay1 = inst.y1;
                     this.config.az1 = inst.z1;
                     this.config.ax2 = inst.x2;
                     this.config.ay2 = inst.y2;
                     this.config.az2 = inst.z2;
                     this.config.arenaDim = inst.dim;
                     this.config.arenaSet = true;
                  }
               }

               if (rolledFrom != null && this.activeArena != null) {
                  List<String> pool = Arenas.names();
                  long seed = (long)server.m_129921_() * 31L + (long)this.name.hashCode();

                  for (ServerPlayer pxxxx : players) {
                     Services.PLATFORM.sendToClient(pxxxx, new MapRollPayload(pool, rolledFrom, seed));
                  }
               }

               this.hiders.clear();
               this.seekers.clear();
               this.scores.clear();
               this.revealTargets.clear();
               this.glowTargets.clear();
               this.ammo.clear();
               this.foundCount = 0;
               this.seekersOutTicks = 0;
               this.hidersOutTicks.clear();
               this.outOfArena.clear();

               for (ServerPlayer pxxxx : players) {
                  if (this.spectators.contains(pxxxx.m_20148_())) {
                     this.enterSpectator(pxxxx);
                  }
               }

               for (ServerPlayer pxxxxx : players) {
                  if (!this.spectators.contains(pxxxxx.m_20148_())
                     && DummyPlayer.isDummy(pxxxxx.m_20148_())
                     && this.preAssigned.get(pxxxxx.m_20148_()) == Room.Role.SEEKER) {
                     this.makeSeeker(pxxxxx, false);
                  }
               }

               if (this.config.manualRoles == 0) {
                  List<ServerPlayer> pool = new ArrayList<>();

                  for (ServerPlayer candidate : active) {
                     UUID idx = candidate.m_20148_();
                     if (!this.seekers.contains(idx)) {
                        if (this.choseOwnRole(idx)) {
                           if (this.preAssigned.get(idx) == Room.Role.SEEKER) {
                              this.makeSeeker(candidate, false);
                           } else {
                              this.makeHider(candidate);
                           }
                        } else {
                           pool.add(candidate);
                        }
                     }
                  }

                  Collections.shuffle(pool, new Random(server.m_129783_().m_213780_().m_188505_()));
                  pool.sort(Comparator.comparing(pxxxxxx -> DummyPlayer.isDummy(pxxxxxx.m_20148_())));
                  int nSeek = Math.max(0, Math.max(1, this.config.maxSeekers) - this.seekers.size());
                  List<String> drafted = new ArrayList<>();

                  for (int i = 0; i < pool.size(); i++) {
                     if (i < nSeek) {
                        this.makeSeeker(pool.get(i), false);
                        drafted.add(pool.get(i).m_7755_().getString());
                     } else {
                        this.makeHider(pool.get(i));
                     }
                  }

                  if (!drafted.isEmpty()) {
                     this.broadcast(
                        server, Component.m_237110_("fantastic.game.seekers_drafted", new Object[]{String.join(", ", drafted)}), ChatFormatting.AQUA
                     );
                  }
               } else {
                  List<ServerPlayer> unassigned = new ArrayList<>();

                  for (ServerPlayer pxxxxxx : active) {
                     UUID idx = pxxxxxx.m_20148_();
                     if (!this.seekers.contains(idx)) {
                        if (DummyPlayer.isDummy(idx)) {
                           this.makeHider(pxxxxxx);
                        } else if (this.choseOwnRole(idx) && this.preAssigned.get(idx) == Room.Role.SEEKER) {
                           this.makeSeeker(pxxxxxx, false);
                        } else if (this.choseOwnRole(idx) && this.preAssigned.get(idx) == Room.Role.HIDER) {
                           this.makeHider(pxxxxxx);
                        } else {
                           unassigned.add(pxxxxxx);
                        }
                     }
                  }

                  unassigned.sort((a, b) -> Integer.compare(a.m_20148_().hashCode(), b.m_20148_().hashCode()));
                  int need = Math.max(0, this.config.maxSeekers - this.seekers.size());

                  for (int ix = 0; ix < unassigned.size(); ix++) {
                     if (ix < need) {
                        this.makeSeeker(unassigned.get(ix), false);
                     } else {
                        this.makeHider(unassigned.get(ix));
                     }
                  }
               }

               if (this.hiders.isEmpty() && !this.seekers.isEmpty()) {
                  ServerPlayer demote = resolve(server, this.seekers.iterator().next());
                  if (demote != null) {
                     this.makeHider(demote);
                  }
               }

               this.seekerless = this.seekers.isEmpty();

               for (UUID idx : this.hiders) {
                  ServerPlayer hp = resolve(server, idx);
                  if (hp != null && !DummyPlayer.isDummy(idx)) {
                     this.beginAdventure(hp);
                  }
               }

               for (UUID idxx : this.seekers) {
                  ServerPlayer sp = resolve(server, idxx);
                  if (sp != null && !DummyPlayer.isDummy(idxx)) {
                     this.beginAdventure(sp);
                  }
               }

               if (this.config.arenaSet) {
                  this.teleportGroupToStart(server, new ArrayList<>(this.hiders));
               }

               MinimapRules.hidePlayers(this.onlineMembers(server));
               this.phase = Room.Phase.COUNTDOWN;
               this.timer = 100;
               this.whistleTimer = this.config.whistleSecs * 20;
               this.freezeSeekers(server, (5 + this.config.hideSecs) * 20);
               Sidebar.openFor(server, players, this.objName, this.sidebarTitle());
               this.pushRoundBar(server);
               this.pushSeekerDraft(server);
               this.broadcast(server, Component.m_237110_("fantastic.game.ready", new Object[]{5}), ChatFormatting.GREEN);
            }
         } else {
            this.broadcast(server, Component.m_237115_("fantastic.game.need_arena"), ChatFormatting.RED);
         }
      }
   }

   private void pushSeekerDraft(MinecraftServer server) {
      List<UUID> rosterIds = new ArrayList<>(this.roster);
      List<UUID> seekerIds = new ArrayList<>(this.seekers);
      if (!rosterIds.isEmpty() && !seekerIds.isEmpty()) {
         long seed = server.m_129783_().m_213780_().m_188505_();
         SeekerDraftPayload payload = new SeekerDraftPayload(rosterIds, seekerIds, seed);

         for (ServerPlayer p : this.onlineMembers(server)) {
            if (!DummyPlayer.isDummy(p.m_20148_())) {
               Services.PLATFORM.sendToClient(p, payload);
            }
         }
      }
   }

   private void freezeSeekers(MinecraftServer server, int ticks) {
      for (UUID id : this.seekers) {
         ServerPlayer p = resolve(server, id);
         if (p != null) {
            p.m_7292_(new MobEffectInstance(MobEffects.f_19610_, ticks, 0, false, false));
            p.m_7292_(new MobEffectInstance(MobEffects.f_19597_, ticks, 200, false, false));
         }
      }
   }

   void stop(MinecraftServer server) {
      this.cleanup(server);
      this.broadcast(server, Component.m_237115_("fantastic.game.stopped"), ChatFormatting.GRAY);
   }

   private void restoreMinimaps(MinecraftServer server) {
      MinimapRules.restore(this.onlineMembers(server));
   }

   private void cleanup(MinecraftServer server) {
      for (UUID id : new HashSet<>(this.seekers)) {
         ServerPlayer p = resolve(server, id);
         if (p != null) {
            p.m_21195_(MobEffects.f_19610_);
            p.m_21195_(MobEffects.f_19597_);
         }
      }

      List<ServerPlayer> members = this.onlineMembers(server);

      for (ServerPlayer p : members) {
         Stash.restore(p);
         resetAvatar(p);
      }

      for (UUID idx : this.glowTargets) {
         ServerPlayer p = resolve(server, idx);
         if (p != null) {
            GlowReveal.set(p, members, false);
         }
      }

      for (ServerPlayer p : members) {
         Sidebar.hide(p, this.objName);
      }

      for (UUID idxx : new HashSet<>(this.savedGameType.keySet())) {
         this.exitSpectator(server, idxx);
      }

      for (UUID idxx : new HashSet<>(this.savedReturn.keySet())) {
         this.endReturn(server, idxx);
      }

      for (ServerPlayer p : this.onlineMembers(server)) {
         this.removeRoomGear(p);
      }

      this.phase = Room.Phase.LOBBY;
      this.restoreMinimaps(server);
      this.pushRoundBar(server);
      this.pushWhistle(server, -1);
      this.removeDummies(server);
      Arenas.release(this.name);
      this.activeArena = null;
      this.hiders.clear();
      this.seekers.clear();
      this.scores.clear();
      this.revealTargets.clear();
      this.glowTargets.clear();
      this.ammo.clear();
      this.foundCount = 0;
      this.seekersOutTicks = 0;
      this.hidersOutTicks.clear();
      this.outOfArena.clear();
      this.spectators.clear();
      this.tpQueue.clear();
      this.tpDelay = 0;
      this.pendingScore.clear();
   }

   boolean toggleSpectate(MinecraftServer server, ServerPlayer p) {
      UUID id = p.m_20148_();
      if (DummyPlayer.isDummy(id)) {
         return false;
      } else if (this.spectators.contains(id)) {
         if (this.inProgress()) {
            this.msg(p, Component.m_237115_("fantastic.spectate.cant_rejoin"), ChatFormatting.RED);
            return false;
         } else {
            this.spectators.remove(id);
            this.preAssigned.remove(id);
            this.chosenRole.remove(id);
            this.msg(p, Component.m_237115_("fantastic.spectate.play_next"), ChatFormatting.AQUA);
            return true;
         }
      } else {
         this.spectators.add(id);
         this.preAssigned.remove(id);
         this.chosenRole.remove(id);
         if (this.inProgress()) {
            boolean wasHider = this.hiders.remove(id);
            this.seekers.remove(id);
            this.scores.remove(id);
            if (wasHider) {
               Sidebar.resetLine(server, this.onlineMembers(server), this.objName, p);
            }

            this.enterSpectator(p);
            this.msg(p, Component.m_237115_("fantastic.spectate.now"), ChatFormatting.YELLOW);
            if (wasHider && this.hiders.isEmpty() && (this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING)) {
               this.end(server, true);
            }
         } else {
            this.msg(p, Component.m_237115_("fantastic.spectate.next"), ChatFormatting.YELLOW);
         }

         return true;
      }
   }

   void addSpectatorMidGame(MinecraftServer server, ServerPlayer p) {
      UUID id = p.m_20148_();
      this.roster.add(id);
      this.spectators.add(id);
      this.preAssigned.remove(id);
      this.enterSpectator(p);
      if (this.config.arenaSet) {
         this.teleportToStart(server, p);
      }

      this.msg(p, Component.m_237115_("fantastic.room.join_spectate"), ChatFormatting.YELLOW);
   }

   boolean isInfection() {
      return this.config.infection != 0;
   }

   void knockOut(MinecraftServer server, ServerPlayer p, String reason, boolean alive) {
      if (this.isInfection()) {
         this.infect(server, p, reason, alive);
      } else {
         this.eliminateToSpectate(server, p, reason, alive);
      }
   }

   void infect(MinecraftServer server, ServerPlayer p, String reason, boolean alive) {
      UUID id = p.m_20148_();
      if (this.inProgress() && this.isParticipant(id)) {
         boolean wasHider = this.hiders.contains(id);
         boolean alreadySeeking = !wasHider && this.seekers.contains(id);
         if (wasHider) {
            this.revealTargets.add(id);
         }

         FantasticNetwork.resetPose(p);
         if (alive) {
            if (!alreadySeeking) {
               this.makeSeeker(p, wasHider);
            }

            this.teleportToStart(server, p);
         } else {
            this.hiders.remove(id);
            this.seekers.add(id);
            if (this.config.ammoLimit > 0) {
               this.ammo.put(id, this.config.ammoLimit);
            }

            GlobalSettings.applyNametagTeam(p);
         }

         if (wasHider) {
            this.pushRoundBar(server);
            this.broadcast(server, Component.m_237110_("fantastic.game.infected", new Object[]{p.m_7755_().getString(), reason}), ChatFormatting.LIGHT_PURPLE);
            if (this.hiders.isEmpty() && (this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING)) {
               this.end(server, true);
            }
         }
      }
   }

   void infectAfterRespawn(ServerPlayer p) {
      if (this.inProgress() && this.seekers.contains(p.m_20148_())) {
         MinecraftServer server = p.m_9236_().m_7654_();
         p.m_150109_().m_36054_(FantasticItems.roomGear(FantasticItems.SHOTGUN.get()));
         ArmorPaintHandler.updateShrink(p);
         GlobalSettings.applyNametagTeam(p);
         this.teleportToStart(server, p);
         if (this.revealTargets.contains(p.m_20148_())) {
            this.msg(p, Component.m_237115_("fantastic.role.found"), ChatFormatting.RED);
         }
      }
   }

   void eliminateToSpectate(MinecraftServer server, ServerPlayer p, String reason, boolean alive) {
      this.eliminateToSpectate(server, p, reason, alive, true, true);
   }

   void eliminateToSpectate(MinecraftServer server, ServerPlayer p, String reason, boolean alive, boolean announce, boolean dropScore) {
      UUID id = p.m_20148_();
      boolean wasHider = this.hiders.remove(id);
      this.seekers.remove(id);
      if (dropScore) {
         this.scores.remove(id);
         if (wasHider) {
            Sidebar.resetLine(server, this.onlineMembers(server), this.objName, p);
         }
      }

      this.spectators.add(id);
      if (announce) {
         this.broadcastLeft(server, p, reason);
      }

      if (alive) {
         this.enterSpectator(p);
         if (this.config.arenaSet) {
            this.teleportToStart(server, p);
         }

         this.msg(p, Component.m_237115_("fantastic.room.eliminated_spectate"), ChatFormatting.YELLOW);
      }

      if (wasHider && this.hiders.isEmpty() && (this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING)) {
         this.end(server, true);
      }
   }

   void beginSpectateAfterRespawn(ServerPlayer p) {
      if (this.inProgress() && this.spectators.contains(p.m_20148_())) {
         this.enterSpectator(p);
         if (this.config.arenaSet) {
            this.teleportToStart(p.m_9236_().m_7654_(), p);
         }

         this.msg(p, Component.m_237115_("fantastic.room.eliminated_spectate"), ChatFormatting.YELLOW);
      }
   }

   private static void resetAvatar(ServerPlayer p) {
      FantasticNetwork.resetPose(p);
      Services.PLATFORM.sendToClient(p, ForceExitPayload.INSTANCE);
   }

   private void enterSpectator(ServerPlayer p) {
      resetAvatar(p);
      this.savedGameType.putIfAbsent(p.m_20148_(), p.f_8941_.m_9290_());
      Services.PLATFORM.persistentData(p).m_128405_("fantastic_saved_gm", this.savedGameType.get(p.m_20148_()).m_46392_());
      if (p.f_8941_.m_9290_() != GameType.SPECTATOR) {
         p.m_143403_(GameType.SPECTATOR);
      }
   }

   private void exitSpectator(MinecraftServer server, UUID id) {
      this.spectateIdx.remove(id);
      this.spectateActive.remove(id);
      GameType prev = this.savedGameType.remove(id);
      if (prev != null) {
         ServerPlayer p = resolve(server, id);
         if (p != null) {
            Services.PLATFORM.persistentData(p).m_128473_("fantastic_saved_gm");
            if (p.f_8941_.m_9290_() == GameType.SPECTATOR) {
               p.m_9213_(p);
               p.m_143403_(prev);
            }
         }
      }
   }

   void onLogout(ServerPlayer p) {
      GameType prev = this.savedGameType.get(p.m_20148_());
      if (prev != null && p.f_8941_.m_9290_() == GameType.SPECTATOR) {
         p.m_143403_(prev);
         Services.PLATFORM.persistentData(p).m_128473_("fantastic_saved_gm");
      }

      Integer advGm = this.savedReturn.get(p.m_20148_());
      if (advGm != null && p.f_8941_.m_9290_() == GameType.ADVENTURE) {
         p.m_143403_(GameType.m_46393_(advGm));
      }
   }

   void onLogin(ServerPlayer p) {
      UUID id = p.m_20148_();
      if (this.inProgress() && this.spectators.contains(id)) {
         this.enterSpectator(p);
      }

      if (this.inProgress() && this.isParticipant(id)) {
         CompoundTag d = Services.PLATFORM.persistentData(p);
         if (d.m_128441_("fantastic_adv_gm")) {
            this.savedReturn.putIfAbsent(id, Nbt.getIntOr(d, "fantastic_adv_gm", 0));
         }

         if (GlobalSettings.roundAdventure() && p.f_8941_.m_9290_() != GameType.ADVENTURE) {
            p.m_143403_(GameType.ADVENTURE);
         }

         if (this.seekers.contains(id) && this.phase != Room.Phase.HIDING && this.phase != Room.Phase.COUNTDOWN) {
            p.m_21195_(MobEffects.f_19610_);
            p.m_21195_(MobEffects.f_19597_);
         }
      }

      if (!this.isParticipant(id)) {
         this.removeRoomGear(p);
      }
   }

   void found(ServerPlayer hider, ServerPlayer by, MinecraftServer server) {
      if (this.phase == Room.Phase.SEEKING && this.hiders.contains(hider.m_20148_())) {
         this.revealTargets.add(hider.m_20148_());
         this.foundCount++;
         FantasticNetwork.resetPose(hider);
         if (this.isInfection()) {
            this.makeSeeker(hider, true);
         } else {
            this.eliminateToSpectate(server, hider, "found", true, false, false);
         }

         this.broadcast(
            server, Component.m_237110_("fantastic.game.found", new Object[]{by.m_7755_().getString(), hider.m_7755_().getString()}), ChatFormatting.GOLD
         );
         this.pushRoundBar(server);
         if (this.hiders.isEmpty()) {
            this.end(server, true);
         }
      }
   }

   public int shotCooldownTicks() {
      return Math.max(this.minShotCooldownTicks(), this.config.shotCooldown);
   }

   public int minShotCooldownTicks() {
      int hunters = this.seekers.size();
      if (hunters > 75) {
         return 30;
      } else if (hunters > 50) {
         return 25;
      } else if (hunters > 32) {
         return 20;
      } else if (hunters > 16) {
         return 10;
      } else {
         return hunters > 8 ? 6 : 3;
      }
   }

   public boolean shotPenaltyOn() {
      return this.config.shotPenalty != 0;
   }

   public int shotPenaltyTicks() {
      return this.shotCooldownTicks() * 2;
   }

   public boolean sightSlowOn() {
      return this.config.sightSlow != 0;
   }

   public boolean canShoot(UUID seeker) {
      return this.config.ammoLimit <= 0 || this.ammo.computeIfAbsent(seeker, k -> this.config.ammoLimit) > 0;
   }

   public void onShot(ServerPlayer seeker, boolean hit, MinecraftServer server) {
      if (this.config.ammoLimit > 0 && this.phase == Room.Phase.SEEKING) {
         UUID id = seeker.m_20148_();
         int cur = this.ammo.getOrDefault(id, this.config.ammoLimit);
         cur = hit ? Math.min(this.config.ammoLimit, cur + 1) : Math.max(0, cur - 1);
         this.ammo.put(id, cur);
         seeker.m_240418_(Component.m_237113_("Ammo " + cur + "/" + this.config.ammoLimit).m_130940_(cur == 0 ? ChatFormatting.RED : ChatFormatting.GRAY), true);
         if (!hit && this.allSeekersOutOfAmmo(server)) {
            this.broadcast(server, Component.m_237115_("fantastic.game.ammo_out"), ChatFormatting.LIGHT_PURPLE);
            this.end(server, false);
         }
      }
   }

   private boolean allSeekersOutOfAmmo(MinecraftServer server) {
      boolean any = false;

      for (UUID id : this.seekers) {
         if (resolve(server, id) != null) {
            any = true;
            if (this.ammo.getOrDefault(id, this.config.ammoLimit) > 0) {
               return false;
            }
         }
      }

      return any;
   }

   void tick(MinecraftServer server) {
      if (this.phase != Room.Phase.LOBBY) {
         this.drainTeleportQueue(server);
         if (--this.timer <= 0) {
            switch (this.phase) {
               case HIDING:
                  if (this.seekerless) {
                     this.end(server, false);
                     return;
                  }

                  this.phase = Room.Phase.SEEKING;
                  this.timer = this.config.seekSecs * 20;
                  if (this.config.startPool > 0) {
                     for (UUID id : this.hiders) {
                        this.scores.put(id, (double)this.config.startPool);
                     }
                  }

                  List<UUID> seekerTp = new ArrayList<>();

                  for (UUID id : this.seekers) {
                     ServerPlayer p = resolve(server, id);
                     if (p != null) {
                        p.m_21195_(MobEffects.f_19610_);
                        p.m_21195_(MobEffects.f_19597_);
                        seekerTp.add(id);
                     }
                  }

                  if (this.config.arenaSet) {
                     this.teleportGroupToStart(server, seekerTp);
                  }

                  this.broadcastTo(server, this.seekers, Component.m_237115_("fantastic.game.seek"), ChatFormatting.AQUA);
                  this.titleTo(server, this.seekers, Component.m_237115_("fantastic.game.title.hunt"), null, 5, 50, 12);
                  break;
               case SEEKING:
                  this.end(server, false);
                  return;
               case REVEAL:
                  this.cleanup(server);
                  return;
               case COUNTDOWN:
                  this.phase = Room.Phase.HIDING;
                  this.timer = this.config.hideSecs * 20;
                  this.broadcastTo(
                     server, this.hiders, Component.m_237110_("fantastic.game.hide", new Object[]{this.config.hideSecs}), ChatFormatting.GREEN
                  );
            }
         }

         if (this.phase == Room.Phase.HIDING && this.timer == 200) {
            this.titleTo(server, this.hiders, Component.m_237115_("fantastic.game.title.hidewarn"), null, 5, 40, 10);
         }

         if (this.phase == Room.Phase.SEEKING) {
            if (server.m_129921_() % 2 == 0) {
               this.sampleVisibility(server);
            }

            if (this.config.whistleSecs <= 0 || this.timer > this.config.seekSecs * 20 * this.config.whistleWindow / 100) {
               this.whistleTimer = 0;
               this.pushWhistle(server, -1);
            } else if (--this.whistleTimer <= 0) {
               this.whistleTimer = this.config.whistleSecs * 20;
               this.autoWhistle(server);
            }
         } else if (this.phase == Room.Phase.REVEAL && server.m_129921_() % 10 == 0) {
            this.refreshGlow(server);
         }

         if (server.m_129921_() % 10 == 0) {
            this.tickSpectatorCameras(server);
         }

         if (server.m_129921_() % 20 == 0) {
            if (this.phase != Room.Phase.LOBBY && this.phase != Room.Phase.REVEAL && this.hiders.isEmpty() && this.seekers.isEmpty()) {
               this.broadcast(server, Component.m_237115_("fantastic.game.aborted_empty"), ChatFormatting.LIGHT_PURPLE);
               this.cleanup(server);
               return;
            }

            if (!this.seekerless && this.phase != Room.Phase.LOBBY && this.phase != Room.Phase.REVEAL && this.noOnlineRealSeekers(server)) {
               this.broadcast(server, Component.m_237115_("fantastic.game.no_seeker"), ChatFormatting.LIGHT_PURPLE);
               this.end(server, false);
               return;
            }

            this.flushPendingScore();
            if (this.phase == Room.Phase.SEEKING && this.config.startPool > 0) {
               this.tickPool(server);
            }

            this.showStatus(server);
            this.pushRoundBar(server);
            if (this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING) {
               Sidebar.update(server, this.onlineMembers(server), this.objName, this.hiderScores(server), this.foundScores(server));
            }
         }
      }
   }

   private boolean noOnlineRealSeekers(MinecraftServer server) {
      for (UUID id : this.seekers) {
         if (resolve(server, id) != null) {
            return false;
         }
      }

      return true;
   }

   boolean tickSeekerArena(MinecraftServer server, boolean anyRealSeekerInside, List<ServerPlayer> outSeekers) {
      if (!this.config.arenaSet || this.phase != Room.Phase.SEEKING) {
         this.seekersOutTicks = 0;
         return false;
      } else if (anyRealSeekerInside) {
         this.seekersOutTicks = 0;
         return false;
      } else {
         this.seekersOutTicks += 10;
         int left = Math.max(0, (600 - this.seekersOutTicks) / 20);

         for (ServerPlayer p : outSeekers) {
            p.m_240418_(Component.m_237113_("⚠ Return to the arena! Hiders win in " + left + "s").m_130940_(ChatFormatting.RED), true);
         }

         if (this.seekersOutTicks >= 600) {
            this.broadcast(server, Component.m_237115_("fantastic.game.seekers_out"), ChatFormatting.LIGHT_PURPLE);
            this.end(server, false);
            return true;
         } else {
            return false;
         }
      }
   }

   private void titleTo(MinecraftServer server, Set<UUID> recipients, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
      for (UUID id : new HashSet<>(recipients)) {
         ServerPlayer p = resolve(server, id);
         if (p != null && this.roster.contains(id)) {
            p.f_8906_.m_9829_(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            if (subtitle != null) {
               p.f_8906_.m_9829_(new ClientboundSetSubtitleTextPacket(subtitle));
            }

            p.f_8906_.m_9829_(new ClientboundSetTitleTextPacket(title));
         }
      }
   }

   private void titleAll(MinecraftServer server, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
      for (ServerPlayer p : this.onlineMembers(server)) {
         p.f_8906_.m_9829_(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
         if (subtitle != null) {
            p.f_8906_.m_9829_(new ClientboundSetSubtitleTextPacket(subtitle));
         }

         p.f_8906_.m_9829_(new ClientboundSetTitleTextPacket(title));
      }
   }

   private static void ding(ServerPlayer p, Holder<SoundEvent> sound, float vol, float pitch) {
      p.f_8906_.m_9829_(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, p.m_20185_(), p.m_20186_(), p.m_20189_(), vol, pitch, 0L));
   }

   void arenaCrossSound(ServerPlayer p, boolean inside) {
      UUID id = p.m_20148_();
      if (!inside && this.outOfArena.add(id)) {
         ding(p, SoundEvents.f_12209_, 1.0F, 0.6F);
      } else if (inside && this.outOfArena.remove(id)) {
         ding(p, SoundEvents.f_12211_, 1.0F, 1.4F);
      }
   }

   void outsideBeep(MinecraftServer server, ServerPlayer p, boolean inside, boolean seeker) {
      if (!inside
         && !DummyPlayer.isDummy(p.m_20148_())
         && (seeker ? this.phase == Room.Phase.SEEKING : this.phase != Room.Phase.COUNTDOWN)
         && server.m_129921_() % 20 == 0) {
         ding(p, SoundEvents.f_12209_, 0.9F, 0.7F);
      }
   }

   boolean tickHiderArena(ServerPlayer p, boolean inside) {
      if (inside) {
         this.hidersOutTicks.remove(p.m_20148_());
         return false;
      } else if (this.phase == Room.Phase.COUNTDOWN) {
         return false;
      } else {
         int out = this.hidersOutTicks.merge(p.m_20148_(), 10, Integer::sum);
         if (out >= 200) {
            this.hidersOutTicks.remove(p.m_20148_());
            return true;
         } else {
            p.m_240418_(Component.m_237113_("⚠ Return to the arena! Eliminated in " + Math.max(0, (200 - out) / 20) + "s").m_130940_(ChatFormatting.RED), true);
            return false;
         }
      }
   }

   private void sampleVisibility(MinecraftServer server) {
      List<ServerPlayer> onlineSeekers = new ArrayList<>(this.seekers.size());

      for (UUID sid : this.seekers) {
         ServerPlayer seeker = resolve(server, sid);
         if (seeker != null) {
            onlineSeekers.add(seeker);
         }
      }

      if (!onlineSeekers.isEmpty()) {
         for (UUID hid : this.hiders) {
            ServerPlayer hider = resolve(server, hid);
            if (hider != null) {
               double best = 0.0;

               for (ServerPlayer seeker : onlineSeekers) {
                  if (seeker.m_9236_() == hider.m_9236_()) {
                     best = Math.max(best, this.visibleFraction(seeker, hider));
                     if (best >= 1.0) {
                        break;
                     }
                  }
               }

               if (best > 0.0) {
                  double factor = Services.PLATFORM.get(hider, PaintAttachments.LOCKED) ? 1.0 : 0.25;
                  this.pendingScore.merge(hid, best * 1.0 * factor, Double::sum);
                  if (this.sightSlowOn() && best >= 0.2) {
                     hider.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 60, 1, false, false, true));
                  }
               }
            }
         }
      }
   }

   Room.WhistleResult awardWhistle(ServerPlayer p) {
      if (this.phase != Room.Phase.SEEKING || !this.hiders.contains(p.m_20148_())) {
         return Room.WhistleResult.NOT_SCORING;
      } else if (!this.seekerWithin(p, 24.0)) {
         return Room.WhistleResult.TOO_FAR;
      } else {
         this.scores.merge(p.m_20148_(), 25.0, Double::sum);
         return Room.WhistleResult.PAID;
      }
   }

   private boolean seekerWithin(ServerPlayer p, double range) {
      MinecraftServer server = p.m_9236_().m_7654_();
      if (server == null) {
         return false;
      } else {
         double r2 = range * range;

         for (UUID id : this.seekers) {
            ServerPlayer s = resolve(server, id);
            if (s != null && s.m_9236_() == p.m_9236_() && s.m_20280_(p) <= r2) {
               return true;
            }
         }

         return false;
      }
   }

   private void tickPool(MinecraftServer server) {
      if (this.config.poolDecay > 0) {
         List<ServerPlayer> dry = null;

         for (UUID id : new ArrayList<>(this.hiders)) {
            double left = this.scores.getOrDefault(id, 0.0) - (double)this.config.poolDecay;
            ServerPlayer p = resolve(server, id);
            if (left > 0.0) {
               this.scores.put(id, left);
               if (left <= 10.0 && p != null) {
                  p.m_240418_(Component.m_237110_("fantastic.game.pool_low", new Object[]{(int)Math.ceil(left)}).m_130940_(ChatFormatting.RED), true);
               }
            } else {
               this.scores.put(id, 0.0);
               if (p != null && !DummyPlayer.isDummy(id)) {
                  (dry == null ? (dry = new ArrayList<>()) : dry).add(p);
               }
            }
         }

         if (dry != null) {
            for (ServerPlayer p : dry) {
               this.knockOut(server, p, Component.m_237115_("fantastic.out.no_points").getString(), true);
            }
         }
      }
   }

   private void flushPendingScore() {
      if (!this.pendingScore.isEmpty()) {
         for (Entry<UUID, Double> e : this.pendingScore.entrySet()) {
            this.scores.merge(e.getKey(), e.getValue(), Double::sum);
         }

         this.pendingScore.clear();
      }
   }

   private double visibleFraction(ServerPlayer seeker, ServerPlayer hider) {
      Vec3 eye = seeker.m_146892_();
      Vec3 look = seeker.m_20252_(1.0F);
      double h = (double)hider.m_20206_();
      Vec3 base = hider.m_20182_();
      Vec3[] points = new Vec3[]{
         base.m_82520_(0.0, h * 0.95, 0.0),
         base.m_82520_(0.0, h * 0.5, 0.0),
         base.m_82520_(0.0, h * 0.1, 0.0),
         base.m_82520_((double)hider.m_20205_() * 0.4, h * 0.5, 0.0),
         base.m_82520_((double)(-hider.m_20205_()) * 0.4, h * 0.5, 0.0)
      };
      int visible = 0;

      for (Vec3 pt : points) {
         Vec3 dir = pt.m_82546_(eye);
         double len = dir.m_82553_();
         if (!(len < 0.001) && !(look.m_82526_(dir.m_82490_(1.0 / len)) < 0.5)) {
            HitResult hit = hider.m_9236_().m_45547_(new ClipContext(eye, pt, Block.COLLIDER, Fluid.NONE, seeker));
            if (hit.m_6662_() == Type.MISS || hit.m_82450_().m_82554_(eye) >= len - 0.3) {
               visible++;
            }
         }
      }

      return (double)visible / (double)points.length;
   }

   private void autoWhistle(MinecraftServer server) {
      List<ServerPlayer> online = new ArrayList<>();

      for (UUID hid : this.hiders) {
         ServerPlayer p = resolve(server, hid);
         if (p != null && p.m_9236_() instanceof ServerLevel) {
            online.add(p);
         }
      }

      if (!online.isEmpty()) {
         ServerPlayer p = online.get(server.m_129783_().m_213780_().m_188503_(online.size()));
         ServerLevel level = p.m_284548_();
         level.m_6263_(null, p.m_20185_(), p.m_20186_(), p.m_20189_(), (SoundEvent)SoundEvents.f_12212_.m_203334_(), SoundSource.PLAYERS, 2.5F, 1.6F);
         if (this.config.whistleArrow != 0) {
            WhistlePayload arrow = WhistlePayload.at(p.m_20185_(), p.m_20188_(), p.m_20189_());

            for (ServerPlayer viewer : this.onlineMembers(server)) {
               if (this.isSeeker(viewer.m_20148_())) {
                  Services.PLATFORM.sendToClient(viewer, arrow);
               }
            }
         }

         this.pushWhistle(server, this.config.whistleSecs);
      }
   }

   private void pushWhistle(MinecraftServer server, int seconds) {
      if (seconds < 0) {
         if (!this.whistleUiVisible) {
            return;
         }

         this.whistleUiVisible = false;
      } else {
         this.whistleUiVisible = true;
      }

      WhistlePayload payload = WhistlePayload.countdown(seconds);

      for (ServerPlayer p : this.onlineMembers(server)) {
         Services.PLATFORM.sendToClient(p, payload);
      }
   }

   void pushRoundBar(MinecraftServer server) {
      List<RoundStatePayload.Entry> entries = new ArrayList<>();
      if (this.phase != Room.Phase.LOBBY) {
         LinkedHashSet<UUID> pool = new LinkedHashSet<>(this.hiders);
         pool.addAll(this.revealTargets);

         for (UUID id : pool) {
            if (this.roster.contains(id)) {
               entries.add(new RoundStatePayload.Entry(id, !this.hiders.contains(id)));
            }
         }
      }

      int secs = Math.max(0, this.timer / 20);
      int seekerCount = this.phase == Room.Phase.LOBBY ? 0 : this.seekers.size();

      for (ServerPlayer p : this.onlineMembers(server)) {
         int mine = (int)Math.round(this.scores.getOrDefault(p.m_20148_(), 0.0));
         Services.PLATFORM.sendToClient(p, new RoundStatePayload(entries, this.phase.ordinal(), secs, seekerCount, mine));
      }
   }

   private void refreshGlow(MinecraftServer server) {
      List<ServerPlayer> members = this.onlineMembers(server);

      for (UUID id : this.glowTargets) {
         ServerPlayer p = resolve(server, id);
         if (p != null) {
            GlowReveal.set(p, members, true);
         }
      }
   }

   private void end(MinecraftServer server, boolean seekersWin) {
      if (this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING) {
         this.pushWhistle(server, -1);
         this.glowTargets.addAll(this.hiders);
         this.revealTargets.addAll(this.hiders);
         UUID top = null;
         double best = -1.0;

         for (Entry<UUID, Double> e : this.scores.entrySet()) {
            if (e.getValue() > best) {
               best = e.getValue();
               top = e.getKey();
            }
         }

         this.broadcast(
            server,
            seekersWin ? Component.m_237115_("fantastic.game.seekers_win") : Component.m_237110_("fantastic.game.hiders_win", new Object[]{this.hiders.size()}),
            ChatFormatting.LIGHT_PURPLE
         );

         for (ServerPlayer member : this.onlineMembers(server)) {
            UUID id = member.m_20148_();
            if (!this.seekers.contains(id) && !this.hiders.contains(id)) {
               ding(member, Holder.m_205709_(SoundEvents.f_12496_), 0.5F, 1.0F);
            } else {
               boolean won = seekersWin ? this.seekers.contains(id) : this.hiders.contains(id);
               if (won) {
                  FantasticAdvancements.award(member, seekersWin ? "win_seeker" : "win_hider");
                  ding(member, Holder.m_205709_(SoundEvents.f_12496_), 1.0F, 1.0F);
               } else {
                  ding(member, SoundEvents.f_12209_, 1.0F, 0.5F);
               }
            }
         }

         this.broadcast(server, Component.m_237110_("fantastic.game.found_count", new Object[]{this.foundCount}), ChatFormatting.GRAY);
         if (top != null) {
            ServerPlayer p = resolve(server, top);
            this.broadcast(
               server,
               Component.m_237110_("fantastic.game.top", new Object[]{p != null ? p.m_7755_().getString() : "a chameleon", (int)best}),
               ChatFormatting.GOLD
            );
         }

         for (Entry<UUID, Double> ex : this.scores.entrySet()) {
            this.totalScores.merge(ex.getKey(), ex.getValue(), Double::sum);
         }

         Stats.recordRound(server, seekersWin, this.hiders, this.seekers, this.scores);
         StringBuilder totals = new StringBuilder();
         this.totalScores.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(5L).forEach(ex -> {
            ServerPlayer tp = resolve(server, ex.getKey());
            if (tp != null) {
               if (totals.length() > 0) {
                  totals.append(" §8·§7 ");
               }

               totals.append(tp.m_7755_().getString()).append(" §f").append((int)ex.getValue().doubleValue());
            }
         });
         if (totals.length() > 0) {
            this.broadcast(server, Component.m_237110_("fantastic.game.room_totals", new Object[]{totals.toString()}), ChatFormatting.GRAY);
         }

         for (ServerPlayer p : this.onlineMembers(server)) {
            Sidebar.hide(p, this.objName);
            resetAvatar(p);
         }

         if (this.config.revealSecs > 0 && !this.revealTargets.isEmpty()) {
            this.phase = Room.Phase.REVEAL;
            int secs = this.glowTargets.isEmpty() ? Math.min(10, this.config.revealSecs) : this.config.revealSecs;
            this.timer = secs * 20;
            this.refreshGlow(server);
            this.broadcast(server, Component.m_237110_("fantastic.game.reveal", new Object[]{secs}), ChatFormatting.YELLOW);
         } else {
            this.cleanup(server);
         }
      }
   }

   List<ServerPlayer> onlineMembers(MinecraftServer server) {
      List<ServerPlayer> list = new ArrayList<>();

      for (UUID id : this.roster) {
         ServerPlayer p = resolve(server, id);
         if (p != null) {
            list.add(p);
         }
      }

      return list;
   }

   static ServerPlayer resolve(MinecraftServer server, UUID id) {
      ServerPlayer p = server.m_6846_().m_11259_(id);
      if (p == null) {
         for (ServerLevel lvl : server.m_129785_()) {
            Entity var6 = lvl.m_8791_(id);
            if (var6 instanceof ServerPlayer) {
               return (ServerPlayer)var6;
            }
         }
      }

      return p;
   }

   private Map<ServerPlayer, Integer> hiderScores(MinecraftServer server) {
      Map<ServerPlayer, Integer> out = new HashMap<>();

      for (UUID id : this.hiders) {
         ServerPlayer p = resolve(server, id);
         if (p != null) {
            out.put(p, (int)this.scores.getOrDefault(id, 0.0).doubleValue());
         }
      }

      return out;
   }

   private Map<ServerPlayer, Integer> foundScores(MinecraftServer server) {
      Map<ServerPlayer, Integer> out = new HashMap<>();

      for (UUID id : this.revealTargets) {
         if (this.roster.contains(id)) {
            ServerPlayer p = resolve(server, id);
            if (p != null) {
               out.put(p, (int)this.scores.getOrDefault(id, 0.0).doubleValue());
            }
         }
      }

      return out;
   }

   private Component sidebarTitle() {
      return Component.m_237115_("fantastic.ui.points_title").m_130940_(ChatFormatting.GREEN);
   }

   private void teleportToStart(MinecraftServer server, ServerPlayer p) {
      if (this.config.arenaSet) {
         int minX = Math.min(this.config.ax1, this.config.ax2);
         int maxX = Math.max(this.config.ax1, this.config.ax2);
         int minY = Math.min(this.config.ay1, this.config.ay2);
         int maxY = Math.max(this.config.ay1, this.config.ay2);
         int minZ = Math.min(this.config.az1, this.config.az2);
         int maxZ = Math.max(this.config.az1, this.config.az2);
         boolean startValid = this.activeArena != null
            && this.activeArena.sx >= (double)minX
            && this.activeArena.sx <= (double)(maxX + 1)
            && this.activeArena.sy >= (double)minY
            && this.activeArena.sy <= (double)(maxY + 1)
            && this.activeArena.sz >= (double)minZ
            && this.activeArena.sz <= (double)(maxZ + 1);
         float yaw = 0.0F;
         float pitch = 0.0F;
         ServerLevel lvl;
         double tx;
         double ty;
         double tz;
         if (startValid) {
            lvl = Arenas.level(server, this.activeArena);
            tx = this.activeArena.sx;
            ty = this.activeArena.sy;
            tz = this.activeArena.sz;
            yaw = this.activeArena.syaw;
            pitch = this.activeArena.spitch;
         } else {
            lvl = server.m_129880_(ResourceKey.m_135785_(Registries.f_256858_, new ResourceLocation(this.config.arenaDim)));
            if (lvl == null) {
               return;
            }

            double[] c = defaultStart(lvl, minX, minY, minZ, maxX, maxY, maxZ);
            tx = c[0];
            ty = c[1];
            tz = c[2];
         }

         if (lvl != null) {
            if (DummyPlayer.isDummy(p.m_20148_())) {
               LockTick.release(p);
            } else {
               boolean wasLocked = Services.PLATFORM.get(p, PaintAttachments.LOCKED);
               if (wasLocked || Services.PLATFORM.get(p, PaintAttachments.POSING)) {
                  FantasticNetwork.resetPose(p);
               }

               Services.PLATFORM.sendToClient(p, ForceExitPayload.INSTANCE);
               FantasticChameleon.LOGGER
                  .info(
                     "[fantastic-tp] {} -> arena ({}, {}, {}) in {} (startValid={}, wasLocked={})",
                     new Object[]{p.m_7755_().getString(), tx, ty, tz, this.config.arenaDim, startValid, wasLocked}
                  );
            }

            double[] s = scatter(lvl, tx, ty, tz);
            Teleports.to(server, p, lvl, s[0], s[1], s[2], yaw, pitch);
         }
      }
   }

   private static double[] scatter(Level lvl, double x, double y, double z) {
      RandomSource rng = lvl.m_213780_();
      double ang = rng.m_188500_() * Math.PI * 2.0;
      double r = Math.sqrt(rng.m_188500_()) * 3.0;
      return new double[]{x + Math.cos(ang) * r, y, z + Math.sin(ang) * r};
   }

   private void teleportGroupToStart(MinecraftServer server, List<UUID> ids) {
      int i = 0;

      for (UUID id : ids) {
         if (i < 10) {
            ServerPlayer p = resolve(server, id);
            if (p != null) {
               this.teleportToStart(server, p);
            }
         } else {
            this.tpQueue.add(id);
         }

         i++;
      }

      if (!this.tpQueue.isEmpty()) {
         this.tpDelay = 20;
      }
   }

   private void drainTeleportQueue(MinecraftServer server) {
      if (!this.tpQueue.isEmpty()) {
         if (this.tpDelay > 0) {
            this.tpDelay--;
         } else {
            for (int i = 0; i < 10 && !this.tpQueue.isEmpty(); i++) {
               ServerPlayer p = resolve(server, this.tpQueue.poll());
               if (p != null) {
                  this.teleportToStart(server, p);
               }
            }

            this.tpDelay = 20;
         }
      }
   }

   private static double[] defaultStart(ServerLevel lvl, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      int cx = (minX + maxX + 1) / 2;
      int cz = (minZ + maxZ + 1) / 2;

      for (int y = minY; y < maxY; y++) {
         if (!lvl.m_8055_(new BlockPos(cx, y - 1, cz)).m_60795_()
            && lvl.m_8055_(new BlockPos(cx, y, cz)).m_60795_()
            && lvl.m_8055_(new BlockPos(cx, y + 1, cz)).m_60795_()) {
            return new double[]{(double)cx + 0.5, (double)y, (double)cz + 0.5};
         }
      }

      int surface = lvl.m_6924_(Types.MOTION_BLOCKING, cx, cz);
      return new double[]{(double)cx + 0.5, (double)Math.max(minY, Math.min(maxY, surface)), (double)cz + 0.5};
   }

   private void beginAdventure(ServerPlayer p) {
      Stash.take(p);
      UUID id = p.m_20148_();
      if (this.savedReturn.putIfAbsent(id, p.f_8941_.m_9290_().m_46392_()) == null) {
         Services.PLATFORM.persistentData(p).m_128405_("fantastic_adv_gm", this.savedReturn.get(id));
      }

      if (GlobalSettings.roundAdventure() && p.f_8941_.m_9290_() != GameType.ADVENTURE) {
         p.m_143403_(GameType.ADVENTURE);
      }
   }

   private void endReturn(MinecraftServer server, UUID id) {
      Integer gm = this.savedReturn.remove(id);
      if (gm != null) {
         ServerPlayer p = resolve(server, id);
         if (p != null) {
            Services.PLATFORM.persistentData(p).m_128473_("fantastic_adv_gm");
            if (p.f_8941_.m_9290_() == GameType.ADVENTURE) {
               p.m_143403_(GameType.m_46393_(gm));
            }

            if (GlobalSettings.teleportHome()) {
               teleportHome(server, p);
            }
         }
      }
   }

   public static void recoverStrandedReturn(ServerPlayer p, Room activeRoom) {
      if (activeRoom == null || !activeRoom.inProgress() || !activeRoom.isParticipant(p.m_20148_())) {
         CompoundTag d = Services.PLATFORM.persistentData(p);
         if (d.m_128441_("fantastic_adv_gm")) {
            int gm = Nbt.getIntOr(d, "fantastic_adv_gm", 0);
            d.m_128473_("fantastic_adv_gm");
            if (p.f_8941_.m_9290_() == GameType.ADVENTURE || p.f_8941_.m_9290_() == GameType.SPECTATOR) {
               p.m_143403_(GameType.m_46393_(gm));
            }

            if (GlobalSettings.teleportHome()) {
               teleportHome(p.m_9236_().m_7654_(), p);
            }
         }
      }
   }

   private static void teleportHome(MinecraftServer server, ServerPlayer p) {
      if (!FantasticSpawn.teleport(p)) {
         ServerLevel overworld = server.m_129783_();
         BlockPos spawn = overworld.m_220360_();
         Teleports.to(
            server, p, overworld, (double)spawn.m_123341_() + 0.5, (double)spawn.m_123342_(), (double)spawn.m_123343_() + 0.5, overworld.m_220361_(), 0.0F
         );
      }
   }

   private void tickSpectatorCameras(MinecraftServer server) {
      if (!this.spectators.isEmpty()) {
         List<ServerPlayer> targets = new ArrayList<>();

         for (UUID id : this.hiders) {
            if (!DummyPlayer.isDummy(id)) {
               ServerPlayer t = resolve(server, id);
               if (t != null && t.m_6084_()) {
                  targets.add(t);
               }
            }
         }

         for (UUID idx : this.seekers) {
            if (!DummyPlayer.isDummy(idx)) {
               ServerPlayer t = resolve(server, idx);
               if (t != null && t.m_6084_()) {
                  targets.add(t);
               }
            }
         }

         for (UUID specId : this.spectators) {
            ServerPlayer sp = resolve(server, specId);
            if (sp != null && sp.f_8941_.m_9290_() == GameType.SPECTATOR) {
               if (targets.isEmpty()) {
                  if (this.config.arenaSet && this.outsideArena(sp)) {
                     this.confineToArena(server, sp);
                  }

                  if (sp.m_8954_() != sp) {
                     sp.m_9213_(sp);
                  }

                  this.spectateActive.remove(specId);
               } else {
                  int idxx = Math.floorMod(this.spectateIdx.getOrDefault(specId, 0), targets.size());
                  Entity cam = sp.m_8954_();
                  ServerPlayer target = targets.get(idxx);
                  if (this.config.arenaSet && this.outsideArena(sp)) {
                     Teleports.to(server, sp, target.m_284548_(), target.m_20185_(), target.m_20186_(), target.m_20189_(), sp.m_146908_(), sp.m_146909_());
                     sp.m_9213_(target);
                     this.spectateIdx.put(specId, idxx);
                     this.spectateActive.add(specId);
                  } else if (cam != target) {
                     if (cam == sp && this.spectateActive.contains(specId)) {
                        idxx = Math.floorMod(idxx + 1, targets.size());
                        target = targets.get(idxx);
                     }

                     this.spectateIdx.put(specId, idxx);
                     this.spectateActive.add(specId);
                     sp.m_9213_(target);
                  }
               }
            }
         }
      }
   }

   private void confineToArena(MinecraftServer server, ServerPlayer sp) {
      ServerLevel lvl = server.m_129880_(ResourceKey.m_135785_(Registries.f_256858_, new ResourceLocation(this.config.arenaDim)));
      if (lvl != null) {
         int minX = Math.min(this.config.ax1, this.config.ax2);
         int maxX = Math.max(this.config.ax1, this.config.ax2);
         int minY = Math.min(this.config.ay1, this.config.ay2);
         int maxY = Math.max(this.config.ay1, this.config.ay2);
         int minZ = Math.min(this.config.az1, this.config.az2);
         int maxZ = Math.max(this.config.az1, this.config.az2);
         double x = Math.min(Math.max(sp.m_20185_(), (double)minX + 0.5), (double)maxX + 0.5);
         double y = Math.min(Math.max(sp.m_20186_(), (double)minY), (double)(maxY + 1));
         double z = Math.min(Math.max(sp.m_20189_(), (double)minZ + 0.5), (double)maxZ + 0.5);
         Teleports.to(server, sp, lvl, x, y, z, sp.m_146908_(), sp.m_146909_());
      }
   }

   /** La barrera contra mobs permanece activa durante toda la parte jugable, en ambos modos. */
   public boolean protectsFromWildMobs(UUID id) {
      return id != null
         && (this.hiders.contains(id) || this.seekers.contains(id))
         && !this.spectators.contains(id)
         && (this.phase == Room.Phase.COUNTDOWN || this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING);
   }

   /** True si la sala tiene una arena delimitada con la que comparar posiciones. */
   public boolean hasArenaBounds() {
      return this.config.arenaSet;
   }

   /** Volumen físico de bloques de la arena: máximo exclusivo, como usa una AABB. */
   public AABB arenaBounds() {
      return new AABB(
         (double)Math.min(this.config.ax1, this.config.ax2),
         (double)Math.min(this.config.ay1, this.config.ay2),
         (double)Math.min(this.config.az1, this.config.az2),
         (double)Math.max(this.config.ax1, this.config.ax2) + 1.0,
         (double)Math.max(this.config.ay1, this.config.ay2) + 1.0,
         (double)Math.max(this.config.az1, this.config.az2) + 1.0
      );
   }

   /** True mientras una arena delimitada debe actuar como barrera física para mobs. */
   public boolean hasActiveMobBarrier() {
      return this.config.arenaSet
         && (this.phase == Room.Phase.COUNTDOWN || this.phase == Room.Phase.HIDING || this.phase == Room.Phase.SEEKING);
   }

   public String arenaDimension() {
      return this.config.arenaDim;
   }

   /** Detecta cualquier parte de un mob dentro de una arena activa, no sólo la posición de sus pies. */
   public boolean blocksWildMob(Entity entity) {
      return this.hasActiveMobBarrier()
         && entity != null
         && entity.m_9236_().m_46472_().m_135782_().toString().equals(this.config.arenaDim)
         && this.arenaBounds().m_82381_(entity.m_20191_());
   }

   /** Comprueba la misma caja y dimension usadas por el confinamiento de la arena. */
   public boolean containsArena(Entity entity) {
      if (!this.config.arenaSet || entity == null) {
         return false;
      }
      if (!entity.m_9236_().m_46472_().m_135782_().toString().equals(this.config.arenaDim)) {
         return false;
      }

      int minX = Math.min(this.config.ax1, this.config.ax2);
      int maxX = Math.max(this.config.ax1, this.config.ax2);
      int minY = Math.min(this.config.ay1, this.config.ay2);
      int maxY = Math.max(this.config.ay1, this.config.ay2);
      int minZ = Math.min(this.config.az1, this.config.az2);
      int maxZ = Math.max(this.config.az1, this.config.az2);
      double x = entity.m_20185_();
      double y = entity.m_20186_();
      double z = entity.m_20189_();
      return x >= (double)minX && x <= (double)(maxX + 1)
         && y >= (double)minY && y <= (double)(maxY + 1)
         && z >= (double)minZ && z <= (double)(maxZ + 1);
   }

   public boolean outsideArena(ServerPlayer p) {
      if (!this.config.arenaSet) {
         return false;
      } else if (!p.m_9236_().m_46472_().m_135782_().toString().equals(this.config.arenaDim)) {
         return true;
      } else {
         int minX = Math.min(this.config.ax1, this.config.ax2);
         int maxX = Math.max(this.config.ax1, this.config.ax2);
         int minY = Math.min(this.config.ay1, this.config.ay2);
         int maxY = Math.max(this.config.ay1, this.config.ay2);
         int minZ = Math.min(this.config.az1, this.config.az2);
         int maxZ = Math.max(this.config.az1, this.config.az2);
         double x = p.m_20185_();
         double y = p.m_20186_();
         double z = p.m_20189_();
         return x < (double)minX || x > (double)(maxX + 1) || y < (double)minY || y > (double)(maxY + 1) || z < (double)minZ || z > (double)(maxZ + 1);
      }
   }

   boolean isParticipant(UUID id) {
      return this.hiders.contains(id) || this.seekers.contains(id);
   }

   void removeRoomGear(ServerPlayer p) {
      FantasticItems.stripRoomGear(p);
      if (Boolean.TRUE.equals(Services.PLATFORM.getOrNull(p, PaintAttachments.CRAWLING))) {
         Services.PLATFORM.set(p, PaintAttachments.CRAWLING, false);
         p.m_6210_();
      }

      if (Boolean.TRUE.equals(Services.PLATFORM.getOrNull(p, PaintAttachments.BLOCK_FORM))) {
         Services.PLATFORM.set(p, PaintAttachments.BLOCK_FORM, false);
      }

      Rooms.clearBlockMemory(p.m_20148_());
      GlobalSettings.applyNametagTeam(p);
   }

   static boolean hasAnyArmor(ServerPlayer p) {
      return !p.m_6844_(EquipmentSlot.HEAD).m_41619_()
         || !p.m_6844_(EquipmentSlot.CHEST).m_41619_()
         || !p.m_6844_(EquipmentSlot.LEGS).m_41619_()
         || !p.m_6844_(EquipmentSlot.FEET).m_41619_();
   }

   private void makeHider(ServerPlayer p) {
      FantasticAdvancements.award(p, "join_round");
      this.hiders.add(p.m_20148_());
      this.seekers.remove(p.m_20148_());
      Services.PLATFORM.set(p, PaintAttachments.SIZE_MINI, true);
      if (DummyPlayer.isDummy(p.m_20148_())) {
         DummyPlayer.dressAsHider(p, this.config.gameMode);
      } else {
         p.m_8061_(EquipmentSlot.HEAD, FantasticItems.roomGear(FantasticItems.CHAMELEON_HELMET.get()));
         p.m_8061_(EquipmentSlot.CHEST, FantasticItems.roomGear(FantasticItems.CHAMELEON_CHESTPLATE.get()));
         p.m_8061_(EquipmentSlot.LEGS, FantasticItems.roomGear(FantasticItems.CHAMELEON_LEGGINGS.get()));
         p.m_8061_(EquipmentSlot.FEET, FantasticItems.roomGear(FantasticItems.CHAMELEON_BOOTS.get()));
      }

      ArmorPaintHandler.updateShrink(p);
      this.msg(p, Component.m_237115_("fantastic.role.hider"), ChatFormatting.GREEN);
      GlobalSettings.applyNametagTeam(p);
      DummyPlayer.resyncIfDummy(p);
   }

   private void makeSeeker(ServerPlayer p, boolean wasHider) {
      FantasticAdvancements.award(p, "join_round");
      this.seekers.add(p.m_20148_());
      this.hiders.remove(p.m_20148_());
      if (this.config.ammoLimit > 0) {
         this.ammo.put(p.m_20148_(), this.config.ammoLimit);
      }

      if (wasHider) {
         p.m_8061_(EquipmentSlot.HEAD, ItemStack.f_41583_);
         p.m_8061_(EquipmentSlot.CHEST, ItemStack.f_41583_);
         p.m_8061_(EquipmentSlot.LEGS, ItemStack.f_41583_);
         p.m_8061_(EquipmentSlot.FEET, ItemStack.f_41583_);
      } else if (!DummyPlayer.isDummy(p.m_20148_())) {
         p.m_8061_(EquipmentSlot.HEAD, FantasticItems.roomGear(FantasticItems.CHAMELEON_HELMET.get()));
         p.m_8061_(EquipmentSlot.CHEST, FantasticItems.roomGear(FantasticItems.CHAMELEON_CHESTPLATE.get()));
         p.m_8061_(EquipmentSlot.LEGS, FantasticItems.roomGear(FantasticItems.CHAMELEON_LEGGINGS.get()));
         p.m_8061_(EquipmentSlot.FEET, FantasticItems.roomGear(FantasticItems.CHAMELEON_BOOTS.get()));
      }

      p.m_150109_().m_36054_(FantasticItems.roomGear(FantasticItems.SHOTGUN.get()));
      ArmorPaintHandler.updateShrink(p);
      GlobalSettings.applyNametagTeam(p);
      DummyPlayer.resyncIfDummy(p);
      if (this.phase == Room.Phase.HIDING) {
         int t = this.config.hideSecs * 20;
         p.m_7292_(new MobEffectInstance(MobEffects.f_19610_, t, 0, false, false));
         p.m_7292_(new MobEffectInstance(MobEffects.f_19597_, t, 200, false, false));
      }

      this.msg(p, wasHider ? Component.m_237115_("fantastic.role.found") : Component.m_237115_("fantastic.role.seeker"), ChatFormatting.RED);
   }

   private void showStatus(MinecraftServer server) {
      String head;
      ChatFormatting color;
      if (this.phase == Room.Phase.COUNTDOWN) {
         head = "§l" + (this.timer + 19) / 20 + "…  §rget ready!";
         color = ChatFormatting.GREEN;
      } else {
         if (this.phase != Room.Phase.REVEAL) {
            return;
         }

         head = Component.m_237110_("fantastic.hud.reveal", new Object[]{this.timer / 20}).getString();
         color = ChatFormatting.YELLOW;
      }

      for (ServerPlayer p : this.onlineMembers(server)) {
         p.m_240418_(Component.m_237113_(head).m_130940_(color), true);
      }
   }

   void broadcastLeft(MinecraftServer server, ServerPlayer p, String reason) {
      this.broadcast(server, Component.m_237110_("fantastic.game.eliminated", new Object[]{p.m_7755_().getString(), reason}), ChatFormatting.GRAY);
   }

   void broadcastShaderWarning(MinecraftServer server, ServerPlayer p) {
      this.broadcast(server, Component.m_237110_("fantastic.game.shader_warning", new Object[]{p.m_7755_().getString()}), ChatFormatting.YELLOW);
   }

   private void broadcastTo(MinecraftServer server, Set<UUID> recipients, Component text, ChatFormatting color) {
      Component full = Component.m_237113_("[" + this.name + "] ").m_7220_(text).m_130940_(color);
      for (UUID id : new HashSet<>(recipients)) {
         ServerPlayer p = resolve(server, id);
         if (p != null && this.roster.contains(id)) {
            p.m_213846_(full);
         }
      }
   }

   private void broadcast(MinecraftServer server, Component text, ChatFormatting color) {
      Component full = Component.m_237113_("[" + this.name + "] ").m_7220_(text).m_130940_(color);

      for (ServerPlayer p : this.onlineMembers(server)) {
         p.m_213846_(full);
      }
   }

   private void msg(ServerPlayer p, Component text, ChatFormatting color) {
      p.m_213846_(text.m_6881_().m_130940_(color));
   }

   public static final class Config {
      public int hideSecs = 90;
      public int seekSecs = 170;
      public int maxSeekers = 1;
      public int whistleSecs = 15;
      public int whistleWindow = 30;
      public int whistleParticles = 0;
      public int whistleArrow = 1;
      public int revealSecs = 20;
      public int maxHiders = 24;
      public int maxRoom = 16;
      public int filters;
      public int textureBrush = 1;
      /**
       * Disparos por cazador. Al agotarlos todos, ganan los camaleones.
       *
       * <p>La regla ya existía en el mod pero venía desactivada (0 = munición infinita), así que nunca
       * se llegaba a ver. Ahora arranca en 8: fallar gasta un disparo, acertar lo devuelve, y cuando
       * ningún cazador tiene munición la ronda termina a favor de los escondidos. Sigue siendo
       * configurable en la pestaña Reglas; con 0 vuelve a ser infinita.
       */
      public int ammoLimit = 8;
      public int shotCooldown = 30;
      public int shotPenalty = 1;
      public int sightSlow = 1;
      public boolean arenaSet;
      public int ax1;
      public int ay1;
      public int az1;
      public int ax2;
      public int ay2;
      public int az2;
      public String arenaDim = "";
      public String arenaName = "";
      public int infection = 1;
      public int elimOnDimension = 1;
      public int manualRoles = 1;
      public int blockDisguise;
      public int startPool;
      public int poolDecay = 1;
      /**
       * Modo de juego de la sala: 0 = Meccha Chameleon (pintura corporal),
       * 1 = Prop Hunt (transformarse en el bloque/entidad que tocas).
       */
      public int gameMode;

      public Config() {
      }
   }

   public static enum Phase {
      LOBBY,
      HIDING,
      SEEKING,
      REVEAL,
      COUNTDOWN;

      private Phase() {
      }
   }

   public static enum Role {
      HIDER,
      SEEKER;

      private Role() {
      }
   }

   public static enum WhistleResult {
      NOT_SCORING,
      PAID,
      TOO_FAR;

      private WhistleResult() {
      }
   }
}
