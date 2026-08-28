package com.claimblocks.data;

import com.claimblocks.ClaimBlocksMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Configuracion de Fantastic Claims: world/data/claimblocks_config.json
 *
 * Se recarga en caliente con /fsclaimadmin reload (o /fsclaim reload).
 *
 * Todo el codigo lee los valores a traves de ClaimConfig.get() en el momento de usarlos, no los
 * copia al arrancar, que es lo que permite que el reload tenga efecto inmediato sin reiniciar.
 *
 * Si falta una clave se usa el valor por defecto y el fichero se reescribe completo, asi que
 * despues de actualizar el mod aparecen solas las opciones nuevas sin perder lo que ya tenias.
 */
public final class ClaimConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "claimblocks_config.json";
    private static final ClaimConfig INSTANCE = new ClaimConfig();

    // --- limites
    public int maxClaimsPerPlayer = 0;
    public int maxMembersPerClaim = 0;
    // --- protecciones de borde y decoracion
    public boolean protectHoppers = true;
    public boolean protectFluids = true;
    public boolean protectDecoration = true;
    public boolean protectDecorationFromExplosions = true;
    // --- baneados
    public boolean banTeleportOut = true;
    public float banDamage = 0.0f;
    public int banNoticeSeconds = 2;
    // --- avisos y textos
    public int trespasserAlertSeconds = 30;
    public int chatPromptSeconds = 90;
    public int maxWelcomeLength = 60;
    // --- rendimiento
    public int particleIntervalTicks = 4;
    public int borderIntervalTicks = 20;
    public int particleRenderDistance = 24;
    public int fireSweepIntervalTicks = 40;
    public int fireSweepRadius = 6;
    public int passiveEffectIntervalTicks = 40;
    // --- barrera de hostiles
    public int hostileBurnSeconds = 3;
    public float hostileDamage = 3.0f;
    // --- efectos pasivos
    public int effectDurationTicks = 60;
    // --- valores de las zonas nuevas
    public String defaultParticle = "minecraft:happy_villager";
    public int defaultParticleDensity = 10;
    public final Map<ClaimFlags.FlagId, Boolean> defaultFlags = new EnumMap<ClaimFlags.FlagId, Boolean>(ClaimFlags.FlagId.class);

    private Path file;

    private ClaimConfig() {
        this.resetDefaultFlags();
    }

    public static ClaimConfig get() {
        return INSTANCE;
    }

    /** Los flags por defecto arrancan con los mismos valores que tenia el mod de fabrica. */
    private void resetDefaultFlags() {
        ClaimFlags base = new ClaimFlags();
        this.defaultFlags.clear();
        for (ClaimFlags.FlagId id : ClaimFlags.FlagId.values()) {
            this.defaultFlags.put(id, Boolean.valueOf(base.get(id)));
        }
    }

    public void load(MinecraftServer server) {
        if (server == null) {
            return;
        }
        this.file = server.m_129843_(LevelResource.f_78182_).resolve(FILE);
        this.reload();
    }

    /** Recarga desde disco y reescribe el fichero completo. Devuelve true si fue bien. */
    public boolean reload() {
        if (this.file == null) {
            return false;
        }
        JsonObject root = new JsonObject();
        try {
            if (Files.exists(this.file, new LinkOption[0])) {
                String text = Files.readString(this.file, StandardCharsets.UTF_8);
                if (!text.isBlank()) {
                    JsonElement el = JsonParser.parseString((String)text);
                    if (el.isJsonObject()) {
                        root = el.getAsJsonObject();
                    }
                }
            }
        }
        catch (Exception var4) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] No se pudo leer " + this.file + "; se usan los valores por defecto", (Throwable)var4);
        }
        this.readFrom(root);
        this.write();
        return true;
    }

    private void readFrom(JsonObject root) {
        JsonObject limites = ClaimConfig.section(root, "limites");
        // compatibilidad con la config antigua, que tenia la clave en la raiz y en ingles
        int maxLegacy = root.has("maxClaimsPerPlayer") ? root.get("maxClaimsPerPlayer").getAsInt() : this.maxClaimsPerPlayer;
        this.maxClaimsPerPlayer = Math.max(0, ClaimConfig.readInt(limites, "maxZonasPorJugador", maxLegacy));
        this.maxMembersPerClaim = Math.max(0, ClaimConfig.readInt(limites, "maxMiembrosPorZona", this.maxMembersPerClaim));

        JsonObject prot = ClaimConfig.section(root, "protecciones");
        this.protectHoppers = ClaimConfig.readBool(prot, "tolvasNoSacanItemsDeLaZona", this.protectHoppers);
        this.protectFluids = ClaimConfig.readBool(prot, "aguaYLavaNoEntranDesdeFuera", this.protectFluids);
        this.protectDecoration = ClaimConfig.readBool(prot, "cuadrosMarcosYSoportes", this.protectDecoration);
        this.protectDecorationFromExplosions = ClaimConfig.readBool(prot, "cuadrosResistenExplosiones", this.protectDecorationFromExplosions);

        JsonObject ban = ClaimConfig.section(root, "baneados");
        this.banTeleportOut = ClaimConfig.readBool(ban, "expulsarPorTeletransporte", this.banTeleportOut);
        this.banDamage = Math.max(0.0f, ClaimConfig.readFloat(ban, "danoAlEntrar", this.banDamage));
        this.banNoticeSeconds = Math.max(0, ClaimConfig.readInt(ban, "segundosEntreAvisos", this.banNoticeSeconds));

        JsonObject avisos = ClaimConfig.section(root, "avisos");
        this.trespasserAlertSeconds = Math.max(0, ClaimConfig.readInt(avisos, "segundosEntreAvisosDeIntruso", this.trespasserAlertSeconds));
        this.chatPromptSeconds = Math.max(5, ClaimConfig.readInt(avisos, "segundosParaResponderEnChat", this.chatPromptSeconds));
        this.maxWelcomeLength = Math.max(10, ClaimConfig.readInt(avisos, "maxCaracteresDeLosMensajes", this.maxWelcomeLength));

        JsonObject perf = ClaimConfig.section(root, "rendimiento");
        this.particleIntervalTicks = Math.max(1, ClaimConfig.readInt(perf, "ticksEntreParticulas", this.particleIntervalTicks));
        this.borderIntervalTicks = Math.max(1, ClaimConfig.readInt(perf, "ticksEntreActualizacionDeBordes", this.borderIntervalTicks));
        this.particleRenderDistance = Math.max(1, ClaimConfig.readInt(perf, "distanciaParaVerParticulas", this.particleRenderDistance));
        this.fireSweepIntervalTicks = Math.max(1, ClaimConfig.readInt(perf, "ticksEntreBarridoDeFuego", this.fireSweepIntervalTicks));
        this.fireSweepRadius = Math.max(0, ClaimConfig.readInt(perf, "radioDeBarridoDeFuego", this.fireSweepRadius));
        this.passiveEffectIntervalTicks = Math.max(1, ClaimConfig.readInt(perf, "ticksEntreEfectosPasivos", this.passiveEffectIntervalTicks));

        JsonObject barrera = ClaimConfig.section(root, "barreraDeHostiles");
        this.hostileBurnSeconds = Math.max(0, ClaimConfig.readInt(barrera, "segundosDeFuego", this.hostileBurnSeconds));
        this.hostileDamage = Math.max(0.0f, ClaimConfig.readFloat(barrera, "dano", this.hostileDamage));

        JsonObject efectos = ClaimConfig.section(root, "efectosPasivos");
        this.effectDurationTicks = Math.max(20, ClaimConfig.readInt(efectos, "duracionEnTicks", this.effectDurationTicks));

        JsonObject nuevas = ClaimConfig.section(root, "zonasNuevas");
        this.defaultParticle = ClaimConfig.readString(nuevas, "particula", this.defaultParticle);
        this.defaultParticleDensity = Math.max(1, ClaimConfig.readInt(nuevas, "densidadDeParticulas", this.defaultParticleDensity));
        JsonObject flags = ClaimConfig.section(nuevas, "flags");
        for (ClaimFlags.FlagId id : ClaimFlags.FlagId.values()) {
            boolean def = this.defaultFlags.getOrDefault(id, Boolean.FALSE).booleanValue();
            this.defaultFlags.put(id, Boolean.valueOf(ClaimConfig.readBool(flags, id.name(), def)));
        }
    }

    private void write() {
        JsonObject root = new JsonObject();
        root.add("_ayuda", ClaimConfig.doc(
                "Fantastic Claims - configuracion del servidor.",
                "Recarga en caliente con /fsclaimadmin reload (no hace falta reiniciar).",
                "Si borras una clave se rellena con su valor por defecto al recargar.",
                "Los cambios de 'zonasNuevas' solo afectan a las zonas que se creen a partir de ahora."));

        JsonObject limites = new JsonObject();
        limites.add("_doc", ClaimConfig.doc(
                "maxZonasPorJugador: 0 = sin limite. No se aplica a operadores.",
                "maxMiembrosPorZona: 0 = sin limite."));
        limites.addProperty("maxZonasPorJugador", (Number)this.maxClaimsPerPlayer);
        limites.addProperty("maxMiembrosPorZona", (Number)this.maxMembersPerClaim);
        root.add("limites", (JsonElement)limites);

        JsonObject prot = new JsonObject();
        prot.add("_doc", ClaimConfig.doc(
                "Protecciones que actuan sin que nadie pise la zona. Apagalas solo si te chocan con otro mod.",
                "tolvasNoSacanItemsDeLaZona: impide que una tolva o vagoneta-tolva vacie cofres desde fuera del borde.",
                "aguaYLavaNoEntranDesdeFuera: bloquea el flujo que cruza hacia dentro (el flujo interno no se toca).",
                "cuadrosMarcosYSoportes: protege cuadros, marcos y soportes de armadura de flechas, mobs y golpes.",
                "cuadrosResistenExplosiones: saca la decoracion de la lista de afectados por TNT y creepers."));
        prot.addProperty("tolvasNoSacanItemsDeLaZona", Boolean.valueOf(this.protectHoppers));
        prot.addProperty("aguaYLavaNoEntranDesdeFuera", Boolean.valueOf(this.protectFluids));
        prot.addProperty("cuadrosMarcosYSoportes", Boolean.valueOf(this.protectDecoration));
        prot.addProperty("cuadrosResistenExplosiones", Boolean.valueOf(this.protectDecorationFromExplosions));
        root.add("protecciones", (JsonElement)prot);

        JsonObject ban = new JsonObject();
        ban.add("_doc", ClaimConfig.doc(
                "Que le pasa a un jugador baneado de una zona cuando entra.",
                "expulsarPorTeletransporte: true lo saca al borde mas cercano; false solo lo empuja.",
                "danoAlEntrar: 0 = sin dano. Ponlo alto solo si quieres que sea letal.",
                "segundosEntreAvisos: cada cuanto se le repite el mensaje."));
        ban.addProperty("expulsarPorTeletransporte", Boolean.valueOf(this.banTeleportOut));
        ban.addProperty("danoAlEntrar", (Number)Float.valueOf(this.banDamage));
        ban.addProperty("segundosEntreAvisos", (Number)this.banNoticeSeconds);
        root.add("baneados", (JsonElement)ban);

        JsonObject avisos = new JsonObject();
        avisos.add("_doc", ClaimConfig.doc(
                "segundosEntreAvisosDeIntruso: antiespam del aviso al dueno cuando entra alguien.",
                "segundosParaResponderEnChat: tiempo para escribir un nombre cuando el menu lo pide.",
                "maxCaracteresDeLosMensajes: limite del mensaje de bienvenida y de salida."));
        avisos.addProperty("segundosEntreAvisosDeIntruso", (Number)this.trespasserAlertSeconds);
        avisos.addProperty("segundosParaResponderEnChat", (Number)this.chatPromptSeconds);
        avisos.addProperty("maxCaracteresDeLosMensajes", (Number)this.maxWelcomeLength);
        root.add("avisos", (JsonElement)avisos);

        JsonObject perf = new JsonObject();
        perf.add("_doc", ClaimConfig.doc(
                "Sube los intervalos para gastar menos CPU y ancho de banda (20 ticks = 1 segundo).",
                "ticksEntreParticulas: cada cuanto se dibujan las particulas del area.",
                "ticksEntreActualizacionDeBordes: cada cuanto se envia el borde a los clientes.",
                "distanciaParaVerParticulas: a cuantos bloques del borde se empiezan a ver.",
                "ticksEntreBarridoDeFuego y radioDeBarridoDeFuego: apagado de fuego dentro de la zona.",
                "ticksEntreEfectosPasivos: cada cuanto se reaplican regeneracion, resistencia y velocidad."));
        perf.addProperty("ticksEntreParticulas", (Number)this.particleIntervalTicks);
        perf.addProperty("ticksEntreActualizacionDeBordes", (Number)this.borderIntervalTicks);
        perf.addProperty("distanciaParaVerParticulas", (Number)this.particleRenderDistance);
        perf.addProperty("ticksEntreBarridoDeFuego", (Number)this.fireSweepIntervalTicks);
        perf.addProperty("radioDeBarridoDeFuego", (Number)this.fireSweepRadius);
        perf.addProperty("ticksEntreEfectosPasivos", (Number)this.passiveEffectIntervalTicks);
        root.add("rendimiento", (JsonElement)perf);

        JsonObject barrera = new JsonObject();
        barrera.add("_doc", ClaimConfig.doc(
                "Flag BURN_HOSTILES: que le pasa a un mob hostil que entra en la zona.",
                "segundosDeFuego: 0 para no quemarlos. dano: 0 para solo empujarlos."));
        barrera.addProperty("segundosDeFuego", (Number)this.hostileBurnSeconds);
        barrera.addProperty("dano", (Number)Float.valueOf(this.hostileDamage));
        root.add("barreraDeHostiles", (JsonElement)barrera);

        JsonObject efectos = new JsonObject();
        efectos.add("_doc", ClaimConfig.doc(
                "duracionEnTicks: cuanto dura cada aplicacion de los efectos de las zonas grandes.",
                "Debe ser mayor que ticksEntreEfectosPasivos o el efecto parpadeara."));
        efectos.addProperty("duracionEnTicks", (Number)this.effectDurationTicks);
        root.add("efectosPasivos", (JsonElement)efectos);

        JsonObject nuevas = new JsonObject();
        nuevas.add("_doc", ClaimConfig.doc(
                "Con que valores nace una zona nueva. No cambia las zonas ya creadas.",
                "particula: id de particula para el borde, por ejemplo minecraft:happy_villager.",
                "flags: true = la proteccion viene activada de fabrica. Son los mismos botones del menu."));
        nuevas.addProperty("particula", this.defaultParticle);
        nuevas.addProperty("densidadDeParticulas", (Number)this.defaultParticleDensity);
        JsonObject flags = new JsonObject();
        for (ClaimFlags.FlagId id : ClaimFlags.FlagId.values()) {
            flags.addProperty(id.name(), this.defaultFlags.getOrDefault(id, Boolean.FALSE));
        }
        nuevas.add("flags", (JsonElement)flags);
        root.add("zonasNuevas", (JsonElement)nuevas);

        try {
            Files.createDirectories(this.file.getParent(), new FileAttribute[0]);
            Path tmp = this.file.resolveSibling(this.file.getFileName().toString() + ".tmp");
            Files.writeString(tmp, (CharSequence)GSON.toJson((JsonElement)root), StandardCharsets.UTF_8, new OpenOption[0]);
            Files.move(tmp, this.file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException var3) {
            ClaimBlocksMod.LOGGER.error("[FantasticClaims] No se pudo escribir " + this.file, (Throwable)var3);
        }
    }

    /** Aplica los valores de 'zonasNuevas' a una zona recien creada. */
    public void applyDefaultsTo(Claim claim) {
        if (claim == null) {
            return;
        }
        ClaimFlags f = claim.getOwnFlags();
        for (ClaimFlags.FlagId id : ClaimFlags.FlagId.values()) {
            f.set(id, this.defaultFlags.getOrDefault(id, Boolean.FALSE).booleanValue());
        }
        f.borderParticle = this.defaultParticle;
        f.particleDensity = this.defaultParticleDensity;
    }

    public int trespasserAlertTicks() {
        return this.trespasserAlertSeconds * 20;
    }

    public long chatPromptMillis() {
        return (long)this.chatPromptSeconds * 1000L;
    }

    public long banNoticeTicks() {
        return (long)this.banNoticeSeconds * 20L;
    }

    private static JsonArray doc(String ... lines) {
        JsonArray arr = new JsonArray();
        for (String l : lines) {
            arr.add(l);
        }
        return arr;
    }

    private static JsonObject section(JsonObject root, String name) {
        return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : new JsonObject();
    }

    private static int readInt(JsonObject o, String key, int def) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : def;
        }
        catch (Exception var4) {
            return def;
        }
    }

    private static float readFloat(JsonObject o, String key, float def) {
        try {
            return o.has(key) ? o.get(key).getAsFloat() : def;
        }
        catch (Exception var4) {
            return def;
        }
    }

    private static boolean readBool(JsonObject o, String key, boolean def) {
        try {
            return o.has(key) ? o.get(key).getAsBoolean() : def;
        }
        catch (Exception var4) {
            return def;
        }
    }

    private static String readString(JsonObject o, String key, String def) {
        try {
            return o.has(key) && !o.get(key).getAsString().isBlank() ? o.get(key).getAsString() : def;
        }
        catch (Exception var4) {
            return def;
        }
    }
}
