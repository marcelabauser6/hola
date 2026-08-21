/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.world.level.GameRules
 *  net.minecraft.world.level.GameRules$BooleanValue
 *  net.minecraft.world.level.storage.LevelResource
 */
package com.claimblocks.data;

import com.claimblocks.ClaimBlocksMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;

public final class GlobalFlags {
    private static final String FILE = "global_flags.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile GlobalFlags INSTANCE;
    public volatile boolean globalPVP = true;
    public volatile boolean globalMobGriefing = true;
    public volatile boolean globalFireSpread = true;
    public volatile boolean globalNoMobSpawn = false;

    private GlobalFlags() {
    }

    public static GlobalFlags getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GlobalFlags();
        }
        return INSTANCE;
    }

    public boolean get(String key) {
        return switch (key) {
            case "globalPVP" -> this.globalPVP;
            case "globalMobGriefing" -> this.globalMobGriefing;
            case "globalFireSpread" -> this.globalFireSpread;
            case "globalNoMobSpawn" -> this.globalNoMobSpawn;
            default -> false;
        };
    }

    public void set(String key, boolean value, MinecraftServer server) {
        switch (key) {
            case "globalPVP": {
                this.globalPVP = value;
                break;
            }
            case "globalMobGriefing": {
                this.globalMobGriefing = value;
                break;
            }
            case "globalFireSpread": {
                this.globalFireSpread = value;
                break;
            }
            case "globalNoMobSpawn": {
                this.globalNoMobSpawn = value;
                break;
            }
        }
        this.applyToServer(server);
        this.save(server);
    }

    public void applyToServer(MinecraftServer server) {
        if (server != null) {
            server.m_129997_(this.globalPVP);
            GameRules rules = server.m_129900_();
            ((GameRules.BooleanValue)rules.m_46170_(GameRules.f_46132_)).m_46246_(this.globalMobGriefing, server);
            ((GameRules.BooleanValue)rules.m_46170_(GameRules.f_46131_)).m_46246_(this.globalFireSpread, server);
        }
    }

    public void load(MinecraftServer server) {
        Path file = this.file(server);
        if (!Files.exists(file, new LinkOption[0])) {
            ClaimBlocksMod.LOGGER.info("No global_flags.json, defaults applied.");
            this.applyToServer(server);
        } else {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject o = JsonParser.parseString((String)text).getAsJsonObject();
                if (o.has("globalPVP")) {
                    this.globalPVP = o.get("globalPVP").getAsBoolean();
                }
                if (o.has("globalMobGriefing")) {
                    this.globalMobGriefing = o.get("globalMobGriefing").getAsBoolean();
                }
                if (o.has("globalFireSpread")) {
                    this.globalFireSpread = o.get("globalFireSpread").getAsBoolean();
                }
                if (o.has("globalNoMobSpawn")) {
                    this.globalNoMobSpawn = o.get("globalNoMobSpawn").getAsBoolean();
                }
                this.applyToServer(server);
                ClaimBlocksMod.LOGGER.info("Global flags cargadas: PVP={} MobGrief={} FireSpread={} NoMobSpawn={}", new Object[]{this.globalPVP, this.globalMobGriefing, this.globalFireSpread, this.globalNoMobSpawn});
            }
            catch (Exception var51) {
                ClaimBlocksMod.LOGGER.error("No se pudo cargar global_flags.json", (Throwable)var51);
            }
        }
    }

    public void save(MinecraftServer server) {
        Path file = this.file(server);
        try {
            JsonObject o = new JsonObject();
            o.addProperty("globalPVP", Boolean.valueOf(this.globalPVP));
            o.addProperty("globalMobGriefing", Boolean.valueOf(this.globalMobGriefing));
            o.addProperty("globalFireSpread", Boolean.valueOf(this.globalFireSpread));
            o.addProperty("globalNoMobSpawn", Boolean.valueOf(this.globalNoMobSpawn));
            Files.createDirectories(file.getParent(), new FileAttribute[0]);
            Files.writeString(file, (CharSequence)GSON.toJson((JsonElement)o), StandardCharsets.UTF_8, new OpenOption[0]);
        }
        catch (IOException var41) {
            ClaimBlocksMod.LOGGER.error("No se pudo guardar global_flags.json", (Throwable)var41);
        }
    }

    private Path file(MinecraftServer s) {
        return s.m_129843_(LevelResource.f_78182_).resolve(FILE);
    }
}

