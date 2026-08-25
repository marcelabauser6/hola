package com.athensmc.fsshopkeepers.config;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The server's settings, read from {@code config/fantasticshopkeepers.json}.
 *
 * <p>JSON rather than a Forge config spec, and a small set of fields rather than the two hundred of the original.
 * Shopkeepers' {@code config.yml} is 32 KB and needed a migration system to change, which is a lot of machinery in
 * service of options most servers never touch. What is here is what an operator actually decides: what item makes a
 * shop, how many shops a player may own, whether chests are protected, what cut the server takes, and which mobs
 * are allowed to be shopkeepers.</p>
 *
 * <p>Every field has a working default, so a server with no config file at all behaves sensibly and the file is
 * written out on first start for editing.</p>
 */
public final class ShopConfig {

    private static final String FILE_NAME = "fantasticshopkeepers.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ShopConfig current = new ShopConfig();

    /**
     * How many shops one player may own, or {@code -1} for no limit.
     *
     * <p>A limit exists because shops are entities that stay loaded and a player who can make unlimited ones can
     * make the server unplayable without ever breaking a rule.</p>
     */
    public int maxShopsPerPlayer = 25;

    /** How far a shop may stand from the chest it draws stock from. */
    public int maxContainerDistance = 15;

    /** Whether a shop's chest is protected from anyone but its owner. */
    public boolean protectContainers = true;

    /** Whether breaking a shop's chest deletes the shop rather than leaving it stockless. */
    public boolean deleteShopOnContainerBreak = false;

    /** Whether shop mobs make idle noises. Off by default: a shop district is otherwise unbearable. */
    public boolean silenceShopEntities = true;

    /** Whether the shop's name floats above the mob at all times. */
    public boolean alwaysShowNameplates = false;

    /** Whether sign shops may be created. */
    public boolean enableSignShops = true;

    /** Whether shops with no body, reachable only by command, may be created. */
    public boolean enableVirtualShops = true;

    /**
     * The server's cut of every sale, as a percentage from 0 to 100.
     *
     * <p>A sink for money, which an economy needs if prices are not to inflate forever.</p>
     */
    public int taxPercent = 0;

    /** Whether the tax is rounded up rather than down, in the server's favour. */
    public boolean taxRoundUp = false;

    /** Whether a shop's owner is told in chat when someone buys from them. */
    public boolean notifyOwnerOfTrades = true;

    /** Whether every trade is written to the server log. */
    public boolean logTrades = false;

    /** Whether a player may trade with a shop they own, which is usually a mistake rather than an intent. */
    public boolean preventTradingWithOwnShop = true;

    /** How many rows of trades one shop may hold. */
    public int maxTradesPerShop = 45;

    /**
     * Which mobs may be shopkeepers.
     *
     * <p>A list rather than a flag per mob. Adding a mob here is how a server allows it, and an empty list means
     * every mob the server knows about is allowed, which is the useful default for a modded server where the
     * interesting NPCs are not vanilla.</p>
     */
    public List<String> allowedShopEntities = new ArrayList<>(List.of(
            "minecraft:villager",
            "minecraft:wandering_trader",
            "minecraft:zombie_villager",
            "minecraft:player",
            "minecraft:armor_stand",
            "minecraft:pig",
            "minecraft:sheep",
            "minecraft:cow",
            "minecraft:chicken",
            "minecraft:cat",
            "minecraft:wolf",
            "minecraft:fox",
            "minecraft:panda",
            "minecraft:parrot",
            "minecraft:llama",
            "minecraft:horse",
            "minecraft:skeleton",
            "minecraft:zombie",
            "minecraft:creeper",
            "minecraft:enderman",
            "minecraft:blaze",
            "minecraft:witch",
            "minecraft:snow_golem",
            "minecraft:iron_golem",
            "minecraft:axolotl",
            "minecraft:allay",
            "minecraft:frog",
            "minecraft:goat",
            "minecraft:mooshroom",
            "minecraft:rabbit",
            "minecraft:slime",
            "minecraft:magma_cube",
            "minecraft:shulker",
            "minecraft:pufferfish",
            "minecraft:tropical_fish",
            "minecraft:glow_squid",
            "minecraft:warden",
            "minecraft:camel",
            "minecraft:sniffer"));

    public static ShopConfig get() {
        return current;
    }

    /** True when this mob is allowed to be a shopkeeper. An empty allow-list permits everything. */
    public boolean allowsEntity(String entityId) {
        return allowedShopEntities.isEmpty() || allowedShopEntities.contains(entityId);
    }

    /** The tax on a sale, in cents, leaving the rest for the seller. */
    public long taxOn(long grossCents) {
        if (taxPercent <= 0 || grossCents <= 0L) {
            return 0L;
        }
        int percent = Math.min(100, taxPercent);
        long numerator = grossCents * percent;
        long tax = taxRoundUp ? (numerator + 99L) / 100L : numerator / 100L;
        return Math.min(tax, grossCents);
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /**
     * Loads the config, writing the defaults out when there is no file yet.
     *
     * <p>A malformed file keeps the previous settings and says so, rather than resetting the server's
     * configuration because of one missing comma. Silently overwriting an operator's edited file with defaults is
     * the worst way to report a syntax error.</p>
     */
    public static void load() {
        Path file = path();
        if (!Files.exists(file)) {
            current = new ShopConfig();
            save();
            FantasticShopkeepers.LOGGER.info("Config creada en {}", file);
            return;
        }
        try {
            String json = Files.readString(file);
            ShopConfig parsed = GSON.fromJson(json, ShopConfig.class);
            if (parsed == null) {
                throw new JsonSyntaxException("archivo vacio");
            }
            if (parsed.allowedShopEntities == null) {
                parsed.allowedShopEntities = new ArrayList<>();
            }
            current = parsed;
            FantasticShopkeepers.LOGGER.info("Config cargada desde {}", file);
        } catch (IOException | JsonSyntaxException broken) {
            FantasticShopkeepers.LOGGER.error(
                    "No se pudo leer {}: {}. Se mantiene la configuracion anterior.", file, broken.toString());
        }
    }

    public static void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(current));
        } catch (IOException failed) {
            FantasticShopkeepers.LOGGER.error("No se pudo guardar la config: {}", failed.toString());
        }
    }
}
