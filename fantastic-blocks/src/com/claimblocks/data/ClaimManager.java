/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.storage.LevelResource
 */
package com.claimblocks.data;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

public class ClaimManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "claimblocks_data.json";
    private static final String CONFIG_FILE = "claimblocks_config.json";
    private static int MAX_CLAIMS_PER_PLAYER = 0;
    private static ClaimManager INSTANCE;
    private final Map<String, List<Claim>> claimsByWorld = new ConcurrentHashMap<String, List<Claim>>();
    private MinecraftServer server;
    private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Component>> pendingMessages = new ConcurrentHashMap<UUID, List<Component>>();
    private final Map<UUID, ClaimGroup> groups = new ConcurrentHashMap<UUID, ClaimGroup>();
    private final Map<UUID, Claim> claimIndex = new ConcurrentHashMap<UUID, Claim>();
    /** Ultimo JSON pendiente de escribir; agrupa varias llamadas a save() en una escritura. */
    private final AtomicReference<String> pendingWrite = new AtomicReference<String>();
    /** Hilo de disco: saca la escritura del JSON del hilo principal del servidor. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ClaimBlocks-IO");
        t.setDaemon(true);
        return t;
    });

    private ClaimManager() {
    }

    public static synchronized ClaimManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClaimManager();
        }
        return INSTANCE;
    }

    public static int getMaxClaimsPerPlayer() {
        return MAX_CLAIMS_PER_PLAYER;
    }

    public static void setMaxClaimsPerPlayer(int n) {
        MAX_CLAIMS_PER_PLAYER = Math.max(0, n);
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public Claim createClaim(Level world, BlockPos pos, Player owner, ClaimTier tier) {
        String dim = world.m_46472_().m_135782_().toString();
        Claim c = Claim.create(owner.m_20148_(), owner.m_7755_().getString(), tier, dim, pos);
        if (tier != null) {
            String var8;
            String var7 = tier.id;
            switch (var8 = tier.id) {
                case "claimstone_500x500": {
                    c.getFlags().effectRegeneration = true;
                    c.getFlags().effectResistance = true;
                    c.getFlags().effectSpeed = true;
                    c.getFlags().allowFlight = true;
                    break;
                }
                case "claimstone_300x300": {
                    c.getFlags().effectRegeneration = true;
                    c.getFlags().effectResistance = true;
                    c.getFlags().effectSpeed = true;
                    break;
                }
                case "claimstone_250x250": {
                    c.getFlags().effectRegeneration = true;
                }
            }
        }
        this.claimsByWorld.computeIfAbsent(dim, k -> Collections.synchronizedList(new ArrayList())).add(c);
        this.claimIndex.put(c.getClaimId(), c);
        this.save();
        return c;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean removeClaim(Level world, BlockPos pos) {
        String dim = world.m_46472_().m_135782_().toString();
        List<Claim> list = this.claimsByWorld.get(dim);
        if (list == null) {
            return false;
        }
        Claim found = null;
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim c : list) {
                if (c.getX() != pos.m_123341_() || c.getY() != pos.m_123342_() || c.getZ() != pos.m_123343_()) continue;
                found = c;
                break;
            }
            if (found != null) {
                list.remove(found);
            }
        }
        if (found != null) {
            this.claimIndex.remove(found.getClaimId());
            this.onClaimRemoved(found);
            this.save();
            return true;
        }
        return false;
    }

    private void onClaimRemoved(Claim c) {
        ClaimGroup g;
        if (c.getGroupId() != null && (g = this.groups.get(c.getGroupId())) != null && c.getClaimId().equals(g.getMotherClaimId())) {
            this.dissolveGroupBreaking(g.getGroupId());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public int clearClaimsOf(UUID playerId) {
        int total = 0;
        for (Map.Entry<String, List<Claim>> e : this.claimsByWorld.entrySet()) {
            List<Claim> list = e.getValue();
            ArrayList<Claim> toRemove = new ArrayList<Claim>();
            List<Claim> list2 = list;
            synchronized (list2) {
                for (Claim c : list) {
                    if (!c.isOwner(playerId)) continue;
                    toRemove.add(c);
                }
            }
            for (Claim cx : toRemove) {
                BlockPos p;
                ServerLevel w;
                if (this.server != null && (w = this.worldFor(e.getKey())) != null && ClaimBlocks.isClaimConcreteForTier(w.m_8055_(p = cx.getCenter()).m_60734_(), cx.getTier())) {
                    w.m_46597_(p, Blocks.f_50016_.m_49966_());
                }
                List<Claim> list3 = list;
                synchronized (list3) {
                    list.remove(cx);
                }
                this.claimIndex.remove(cx.getClaimId());
                this.onClaimRemoved(cx);
                ++total;
            }
        }
        if (total > 0) {
            this.save();
        }
        return total;
    }

    public boolean transferOwnership(Claim claim, UUID newOwnerId, String newOwnerName) {
        if (claim != null && newOwnerId != null) {
            claim.setOwner(newOwnerId, newOwnerName);
            this.save();
            return true;
        }
        return false;
    }

    private ServerLevel worldFor(String dimensionKey) {
        if (this.server == null) {
            return null;
        }
        for (ServerLevel w : this.server.m_129785_()) {
            if (!w.m_46472_().m_135782_().toString().equals(dimensionKey)) continue;
            return w;
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Claim getClaimAt(Level world, BlockPos pos) {
        String dim = world.m_46472_().m_135782_().toString();
        List<Claim> list = this.claimsByWorld.get(dim);
        if (list == null) {
            return null;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim c : list) {
                if (!c.contains(pos)) continue;
                return c;
            }
            return null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Claim getClaimByCenter(Level world, BlockPos pos) {
        String dim = world.m_46472_().m_135782_().toString();
        List<Claim> list = this.claimsByWorld.get(dim);
        if (list == null) {
            return null;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim c : list) {
                if (c.getX() != pos.m_123341_() || c.getY() != pos.m_123342_() || c.getZ() != pos.m_123343_()) continue;
                return c;
            }
            return null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean wouldOverlap(Level world, BlockPos pos, int radius, int height) {
        String dim = world.m_46472_().m_135782_().toString();
        List<Claim> list = this.claimsByWorld.get(dim);
        if (list == null) {
            return false;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim c : list) {
                if (!c.overlapsWith(pos, radius, height)) continue;
                return true;
            }
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> overlappingClaims(Level world, BlockPos pos, int radius, int height) {
        ArrayList<Claim> out = new ArrayList<Claim>();
        String dim = world.m_46472_().m_135782_().toString();
        List<Claim> list = this.claimsByWorld.get(dim);
        if (list == null) {
            return out;
        }
        List<Claim> list2 = list;
        synchronized (list2) {
            for (Claim c : list) {
                if (!c.overlapsWith(pos, radius, height)) continue;
                out.add(c);
            }
            return out;
        }
    }

    public ClaimGroup getGroup(UUID groupId) {
        return groupId == null ? null : this.groups.get(groupId);
    }

    public ClaimGroup getGroupOf(Claim claim) {
        return claim == null ? null : this.getGroup(claim.getGroupId());
    }

    public Claim findClaimById(UUID id) {
        return id == null ? null : this.claimIndex.get(id);
    }

    public Claim getMotherClaim(UUID groupId) {
        ClaimGroup g = this.getGroup(groupId);
        return g != null && g.getMotherClaimId() != null ? this.claimIndex.get(g.getMotherClaimId()) : null;
    }

    public ClaimGroup createGroup(Claim mother, String name) {
        UUID gid = UUID.randomUUID();
        ClaimGroup g = new ClaimGroup(gid, name, mother.getClaimId(), mother.getOwnerUUID());
        this.groups.put(gid, g);
        mother.setGroupId(gid);
        this.save();
        return g;
    }

    public void registerPlayer(UUID groupId, UUID playerId) {
        ClaimGroup g = this.getGroup(groupId);
        if (g != null) {
            g.register(playerId);
            this.save();
        }
    }

    public boolean isRegistered(UUID groupId, UUID playerId) {
        ClaimGroup g = this.getGroup(groupId);
        return g != null && g.isRegistered(playerId);
    }

    public ClaimGroup getGroupByRegistered(UUID playerId) {
        for (ClaimGroup g : this.groups.values()) {
            if (!g.isRegistered(playerId)) continue;
            return g;
        }
        return null;
    }

    public void joinClaimToGroup(Claim claim, UUID groupId) {
        if (claim != null && this.groups.containsKey(groupId)) {
            claim.setGroupId(groupId);
            this.save();
        }
    }

    public List<Claim> getGroupClaims(UUID groupId) {
        ArrayList<Claim> out = new ArrayList<Claim>();
        if (groupId == null) {
            return out;
        }
        for (Claim c : this.getAllClaims()) {
            if (!groupId.equals(c.getGroupId())) continue;
            out.add(c);
        }
        return out;
    }

    public void dissolveGroup(UUID groupId) {
        if (this.groups.remove(groupId) != null) {
            for (Claim c : this.getAllClaims()) {
                if (!groupId.equals(c.getGroupId())) continue;
                c.setGroupId(null);
            }
            this.save();
        }
    }

    public void dissolveGroupBreaking(UUID groupId) {
        ClaimGroup g = this.groups.get(groupId);
        if (g != null) {
            Claim mother = this.getMotherClaim(groupId);
            UUID motherClaimId = mother != null ? mother.getClaimId() : g.getMotherClaimId();
            for (Claim c : this.getGroupClaims(groupId)) {
                if (motherClaimId != null && c.getClaimId().equals(motherClaimId)) continue;
                this.breakAndReturn(c);
            }
            this.groups.remove(groupId);
            for (Claim cx : this.getAllClaims()) {
                if (!groupId.equals(cx.getGroupId())) continue;
                cx.setGroupId(null);
            }
            this.save();
        }
    }

    public void leaveGroupBreaking(UUID groupId, UUID playerId) {
        ClaimGroup g = this.getGroup(groupId);
        if (g != null) {
            if (playerId != null && playerId.equals(g.getMotherOwnerId())) {
                this.dissolveGroupBreaking(groupId);
            } else {
                g.unregister(playerId);
                for (Claim c : this.getGroupClaims(groupId)) {
                    if (!c.isOwner(playerId)) continue;
                    this.breakAndReturn(c);
                }
                this.save();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void breakAndReturn(Claim c) {
        List<Claim> list;
        ServerLevel w = this.worldFor(c.getWorld());
        BlockPos p = c.getCenter();
        ClaimTier tier = c.getTier();
        if (w != null && tier != null && ClaimBlocks.isClaimConcreteForTier(w.m_8055_(p).m_60734_(), tier)) {
            w.m_46597_(p, Blocks.f_50016_.m_49966_());
        }
        if (w != null && tier != null) {
            ServerPlayer owner;
            ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
            ServerPlayer serverPlayer = owner = this.server != null && c.getOwnerUUID() != null ? this.server.m_6846_().m_11259_(c.getOwnerUUID()) : null;
            if (owner != null) {
                if (!owner.m_150109_().m_36054_(stack)) {
                    owner.m_36176_(stack, false);
                }
            } else {
                w.m_7967_((Entity)new ItemEntity((Level)w, (double)p.m_123341_() + 0.5, (double)p.m_123342_() + 0.5, (double)p.m_123343_() + 0.5, stack));
            }
        }
        if ((list = this.claimsByWorld.get(c.getWorld())) != null) {
            List<Claim> list2 = list;
            synchronized (list2) {
                list.remove(c);
            }
        }
        this.claimIndex.remove(c.getClaimId());
    }

    public void removePlayerFromGroup(UUID groupId, UUID playerId) {
        ClaimGroup g = this.getGroup(groupId);
        if (g != null) {
            if (playerId != null && playerId.equals(g.getMotherOwnerId())) {
                this.dissolveGroup(groupId);
            } else {
                g.unregister(playerId);
                for (Claim c : this.getGroupClaims(groupId)) {
                    if (!c.isOwner(playerId)) continue;
                    c.setGroupId(null);
                }
                this.save();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> getAllClaims() {
        ArrayList<Claim> all = new ArrayList<Claim>();
        Iterator<List<Claim>> iterator = this.claimsByWorld.values().iterator();
        while (iterator.hasNext()) {
            List<Claim> l;
            List<Claim> list;
            List<Claim> list2 = list = (l = iterator.next());
            synchronized (list2) {
                all.addAll(l);
            }
        }
        return all;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> getClaimsOf(UUID playerId) {
        ArrayList<Claim> r = new ArrayList<Claim>();
        Iterator<List<Claim>> iterator = this.claimsByWorld.values().iterator();
        while (iterator.hasNext()) {
            List<Claim> l;
            List<Claim> list;
            List<Claim> list2 = list = (l = iterator.next());
            synchronized (list2) {
                for (Claim c : l) {
                    if (!c.isOwner(playerId)) continue;
                    r.add(c);
                }
            }
        }
        return r;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<Claim> getClaimsInWorld(String dim) {
        List l;
        List list;
        List list2 = list = (l = this.claimsByWorld.getOrDefault(dim, Collections.emptyList()));
        synchronized (list2) {
            return new ArrayList<Claim>(l);
        }
    }

    /** Serializa el estado actual. Se llama siempre desde el hilo del servidor. */
    private String snapshotJson() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Claim c : this.getAllClaims()) {
            arr.add((JsonElement)c.toJson());
        }
        root.add("claims", (JsonElement)arr);
        JsonArray garr = new JsonArray();
        for (ClaimGroup g : this.groups.values()) {
            garr.add((JsonElement)g.toJson());
        }
        root.add("groups", (JsonElement)garr);
        return GSON.toJson((JsonElement)root);
    }

    /**
     * Guardado.
     *
     * Antes esto escribia el JSON completo directamente sobre el fichero final y en el hilo
     * principal, en CADA cambio (cada clic de flag, cada miembro, cada zona). Dos problemas:
     * tirones con muchas zonas, y perdida total de los datos si el servidor moria a mitad de la
     * escritura. Ahora se serializa en el hilo del servidor (para tener una foto coherente) y la
     * escritura se hace en un hilo aparte, a un fichero temporal que despues se mueve de forma
     * atomica, dejando una copia .bak. Las llamadas seguidas se agrupan en una sola escritura.
     */
    public void save() {
        if (this.server == null) {
            return;
        }
        Path file = this.dataFile(this.server);
        String json = this.snapshotJson();
        boolean schedule = this.pendingWrite.getAndSet(json) == null;
        if (schedule) {
            IO.execute(() -> {
                String data = this.pendingWrite.getAndSet(null);
                if (data != null) {
                    ClaimManager.writeAtomic(file, data);
                }
            });
        }
    }

    /** Guardado sincrono: para el apagado del servidor, donde no puede quedar nada en el aire. */
    public void saveNow() {
        if (this.server == null) {
            return;
        }
        Path file = this.dataFile(this.server);
        String json = this.snapshotJson();
        this.pendingWrite.set(null);
        ClaimManager.writeAtomic(file, json);
    }

    private static void writeAtomic(Path file, String json) {
        try {
            Files.createDirectories(file.getParent(), new FileAttribute[0]);
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(tmp, (CharSequence)json, StandardCharsets.UTF_8, new OpenOption[0]);
            if (Files.exists(file, new LinkOption[0])) {
                try {
                    Files.copy(file, file.resolveSibling(file.getFileName().toString() + ".bak"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException ignored) {
                    // la copia de seguridad es opcional
                }
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException var5) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException var7) {
            ClaimBlocksMod.LOGGER.error("Could not save claims to " + file, (Throwable)var7);
        }
    }

    public void load(MinecraftServer server) {
        this.server = server;
        this.claimsByWorld.clear();
        this.claimIndex.clear();
        this.groups.clear();
        this.loadConfig(server);
        Path file = this.dataFile(server);
        // si el fichero principal quedo corrupto (p.ej. cierre a lo bruto con la version
        // anterior, que escribia sin fichero temporal) se recurre a la copia .bak
        if (!ClaimManager.looksValid(file)) {
            Path bak = file.resolveSibling(file.getFileName().toString() + ".bak");
            if (ClaimManager.looksValid(bak)) {
                ClaimBlocksMod.LOGGER.warn("[ClaimBlocks] {} no se puede leer; restaurando desde {}", file, bak);
                file = bak;
            }
        }
        if (!Files.exists(file, new LinkOption[0])) {
            ClaimBlocksMod.LOGGER.info("No existing claims file at {}, starting fresh.", (Object)file);
        } else {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.isBlank()) {
                    return;
                }
                JsonElement el = JsonParser.parseString((String)text);
                if (!el.isJsonObject()) {
                    return;
                }
                JsonArray arr = el.getAsJsonObject().getAsJsonArray("claims");
                if (arr == null) {
                    return;
                }
                int count = 0;
                int migrated = 0;
                for (JsonElement e : arr) {
                    JsonObject obj = e.getAsJsonObject();
                    boolean wasLegacy = !obj.has("radius") && obj.has("tier");
                    Claim c = Claim.fromJson(obj);
                    this.claimsByWorld.computeIfAbsent(c.getWorld(), k -> Collections.synchronizedList(new ArrayList())).add(c);
                    this.claimIndex.put(c.getClaimId(), c);
                    ++count;
                    if (!wasLegacy) continue;
                    ++migrated;
                }
                JsonArray garr = el.getAsJsonObject().getAsJsonArray("groups");
                if (garr != null) {
                    for (JsonElement ge : garr) {
                        ClaimGroup g = ClaimGroup.fromJson(ge.getAsJsonObject());
                        this.groups.put(g.getGroupId(), g);
                    }
                }
                ArrayList<UUID> dead = new ArrayList<UUID>();
                for (ClaimGroup g : this.groups.values()) {
                    if (g.getMotherClaimId() != null && this.claimIndex.get(g.getMotherClaimId()) != null) continue;
                    dead.add(g.getGroupId());
                }
                for (UUID gid : dead) {
                    this.groups.remove(gid);
                    for (Claim c : this.getAllClaims()) {
                        if (!gid.equals(c.getGroupId())) continue;
                        c.setGroupId(null);
                    }
                }
                ClaimBlocksMod.LOGGER.info("Loaded {} claims from {} (migrated {} legacy)", new Object[]{count, file, migrated});
                if (migrated > 0) {
                    this.save();
                }
            }
            catch (Exception var14) {
                ClaimBlocksMod.LOGGER.error("Could not load claims from " + file, (Throwable)var14);
            }
        }
    }

    private void loadConfig(MinecraftServer s) {
        Path cfg = s.m_129843_(LevelResource.f_78182_).resolve(CONFIG_FILE);
        try {
            JsonObject o;
            if (!Files.exists(cfg, new LinkOption[0])) {
                JsonObject obj = new JsonObject();
                obj.addProperty("maxClaimsPerPlayer", (Number)0);
                obj.addProperty("_doc_maxClaimsPerPlayer", "0 = unlimited; max claims a non-OP player can own");
                Files.createDirectories(cfg.getParent(), new FileAttribute[0]);
                Files.writeString(cfg, (CharSequence)GSON.toJson((JsonElement)obj), StandardCharsets.UTF_8, new OpenOption[0]);
                return;
            }
            JsonElement el = JsonParser.parseString((String)Files.readString(cfg, StandardCharsets.UTF_8));
            if (el != null && el.isJsonObject() && (o = el.getAsJsonObject()).has("maxClaimsPerPlayer")) {
                ClaimManager.setMaxClaimsPerPlayer(o.get("maxClaimsPerPlayer").getAsInt());
            }
        }
        catch (Exception var51) {
            ClaimBlocksMod.LOGGER.error("Could not load config " + cfg, (Throwable)var51);
        }
    }

    /** true si el fichero existe y contiene un JSON con la lista de zonas. */
    private static boolean looksValid(Path p) {
        try {
            if (!Files.exists(p, new LinkOption[0])) {
                return false;
            }
            String text = Files.readString(p, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return false;
            }
            JsonElement el = JsonParser.parseString((String)text);
            return el.isJsonObject() && el.getAsJsonObject().has("claims");
        }
        catch (Exception var2) {
            return false;
        }
    }

    private Path dataFile(MinecraftServer s) {
        return s.m_129843_(LevelResource.f_78182_).resolve(DATA_FILE);
    }

    public boolean isBypassing(UUID id) {
        return this.bypassPlayers.contains(id);
    }

    public boolean toggleBypass(UUID id) {
        if (this.bypassPlayers.contains(id)) {
            this.bypassPlayers.remove(id);
            return false;
        }
        this.bypassPlayers.add(id);
        return true;
    }

    public Set<UUID> getBypassPlayers() {
        return this.bypassPlayers;
    }

    public void queueMessage(UUID owner, Component msg) {
        this.pendingMessages.computeIfAbsent(owner, k -> Collections.synchronizedList(new ArrayList())).add(msg);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void flushPendingTo(ServerPlayer player) {
        List<Component> msgs = this.pendingMessages.remove(player.m_20148_());
        if (msgs != null) {
            List<Component> list = msgs;
            synchronized (list) {
                for (Component t : msgs) {
                    player.m_5661_(t, false);
                }
            }
        }
    }

    public void onPlayerDisconnect(UUID id) {
        this.bypassPlayers.remove(id);
    }
}

