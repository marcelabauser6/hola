package com.athensmc.fantasticregions.flag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every YAWP protection flag, with a Spanish name and an explanation of what it governs.
 *
 * <p>The identifiers are YAWP's own - {@code break-blocks}, {@code melee-wtrader} and so on - because
 * they are the keys its region store is written with. They were read out of the shipped
 * {@code RegionFlag} enum rather than typed from its documentation, so the set here is exactly the set
 * the host mod understands: 93 flags, no more and no fewer, checked on every build.</p>
 *
 * <p>The explanations exist because the names do not survive translation into a usable label. Half of
 * them are phrased as a prohibition ({@code no-pvp}, {@code no-flight}) and half as the action itself
 * ({@code break-blocks}, {@code use-items}), so a column of literal translations reads as a list of
 * contradictions. Each row here says what the flag <em>governs</em>, and the interface shows the state
 * separately, which keeps the two ideas from being confused.</p>
 *
 * <p>Deliberately free of any Minecraft import: the guard that checks the set is complete and every
 * label fits runs on a plain JVM.</p>
 */
public final class FlagCatalogue {

    private static final List<FlagInfo> ALL;
    private static final Map<String, FlagInfo> BY_ID;
    private static final Map<FlagGroup, List<FlagInfo>> BY_GROUP;

    private FlagCatalogue() {
    }

    static {
        List<FlagInfo> flags = new ArrayList<>(93);

        // ---- Bloques -------------------------------------------------------------------------
        flags.add(f("break-blocks", "Romper bloques", FlagGroup.BLOCKS,
                "Romper cualquier bloque de la zona. Es la protección básica contra el saqueo."));
        flags.add(f("place-blocks", "Colocar bloques", FlagGroup.BLOCKS,
                "Poner bloques nuevos dentro de la zona."));
        flags.add(f("use-blocks", "Usar bloques", FlagGroup.BLOCKS,
                "Accionar botones, palancas, puertas, trampillas y placas de presión."));
        flags.add(f("access-container", "Abrir contenedores", FlagGroup.BLOCKS,
                "Abrir cofres, barriles, tolvas, hornos y cualquier bloque con inventario."));
        flags.add(f("access-enderchest", "Abrir cofre de Ender", FlagGroup.BLOCKS,
                "Abrir cofres de Ender. Va aparte porque su contenido es privado de cada jugador."));
        flags.add(f("no-sign-edit", "Editar carteles", FlagGroup.BLOCKS,
                "Cambiar el texto de un cartel ya colocado."));
        flags.add(f("till-farmland", "Arar con azada", FlagGroup.BLOCKS,
                "Convertir tierra o hierba en tierra de cultivo usando una azada."));
        flags.add(f("shovel-path", "Hacer sendero con pala", FlagGroup.BLOCKS,
                "Convertir hierba en camino de tierra usando una pala."));
        flags.add(f("strip-wood", "Descortezar con hacha", FlagGroup.BLOCKS,
                "Quitar la corteza a un tronco usando un hacha."));
        flags.add(f("trample-farmland", "Pisar cultivos", FlagGroup.BLOCKS,
                "Que al saltar sobre tierra de cultivo esta vuelva a ser tierra normal. Afecta a todos."));
        flags.add(f("trample-farmland-player", "Pisar cultivos (jugador)", FlagGroup.BLOCKS,
                "Igual que el anterior pero solo para jugadores, dejando que los animales sí pisen."));
        flags.add(f("tools-secondary", "Uso secundario de útiles", FlagGroup.BLOCKS,
                "Acciones de clic derecho de las herramientas que no tienen su propia casilla."));
        flags.add(f("use-bonemeal", "Usar harina de hueso", FlagGroup.BLOCKS,
                "Abonar plantas con harina de hueso para que crezcan de golpe."));
        flags.add(f("set-spawn", "Fijar punto de aparición", FlagGroup.BLOCKS,
                "Dormir en una cama o usar un ancla de respawn para reaparecer en la zona."));
        flags.add(f("dragon-destruction", "Destrucción del dragón", FlagGroup.BLOCKS,
                "Que el dragón del End rompa los bloques por los que pasa."));
        flags.add(f("wither-destruction", "Destrucción del wither", FlagGroup.BLOCKS,
                "Que el wither rompa bloques con sus disparos y al aparecer."));
        flags.add(f("zombie-destruction", "Zombis rompen puertas", FlagGroup.BLOCKS,
                "Que los zombis derriben puertas de madera en dificultad difícil."));

        // ---- Objetos -------------------------------------------------------------------------
        flags.add(f("use-items", "Usar objetos", FlagGroup.ITEMS,
                "Usar objetos de la mano: cubos, mecheros, comida, perlas y demás."));
        flags.add(f("item-drop", "Tirar objetos", FlagGroup.ITEMS,
                "Soltar objetos al suelo desde el inventario."));
        flags.add(f("item-pickup", "Recoger objetos", FlagGroup.ITEMS,
                "Recoger del suelo los objetos que haya tirados."));
        flags.add(f("no-item-despawn", "Objetos no desaparecen", FlagGroup.ITEMS,
                "Que los objetos tirados en el suelo no se borren pasados los cinco minutos."));
        flags.add(f("drop-loot", "Botín al morir", FlagGroup.ITEMS,
                "Que cualquier criatura o jugador suelte su botín al morir dentro de la zona."));
        flags.add(f("drop-loot-player", "Botín de jugador", FlagGroup.ITEMS,
                "Solo el botín de los jugadores al morir, dejando intacto el de las criaturas."));
        flags.add(f("keep-inv", "Conservar inventario", FlagGroup.ITEMS,
                "Que el jugador conserve su inventario al morir aquí, como keepInventory."));
        flags.add(f("keep-xp", "Conservar experiencia", FlagGroup.ITEMS,
                "Que el jugador conserve sus niveles de experiencia al morir aquí."));
        flags.add(f("drop-xp", "Soltar experiencia", FlagGroup.ITEMS,
                "Que al morir se suelten orbes de experiencia."));
        flags.add(f("xp-pickup", "Recoger experiencia", FlagGroup.ITEMS,
                "Recoger los orbes de experiencia que haya por el suelo."));
        flags.add(f("xp-freeze", "Congelar experiencia", FlagGroup.ITEMS,
                "Que la experiencia del jugador no suba ni baje mientras esté en la zona."));
        flags.add(f("spawning-xp", "Aparición de experiencia", FlagGroup.ITEMS,
                "Que se generen orbes de experiencia por cualquier causa."));
        flags.add(f("scoop-fluids", "Recoger líquidos", FlagGroup.ITEMS,
                "Llenar un cubo con agua o lava de la zona."));
        flags.add(f("place-fluids", "Verter líquidos", FlagGroup.ITEMS,
                "Vaciar un cubo de agua o lava dentro de la zona."));

        // ---- Jugadores -----------------------------------------------------------------------
        flags.add(f("no-pvp", "Sin PvP entre jugadores", FlagGroup.PLAYERS,
                "Que los jugadores no puedan hacerse daño entre ellos por ningún medio."));
        flags.add(f("melee-players", "Golpear jugadores", FlagGroup.PLAYERS,
                "El daño cuerpo a cuerpo concreto contra jugadores. Más fino que la casilla de PvP."));
        flags.add(f("fire-bow", "Disparar arco", FlagGroup.PLAYERS,
                "Usar arcos y ballestas dentro de la zona."));
        flags.add(f("invincible", "Invulnerabilidad", FlagGroup.PLAYERS,
                "Que los jugadores no reciban daño de ninguna fuente mientras estén dentro."));
        flags.add(f("no-hunger", "Sin hambre", FlagGroup.PLAYERS,
                "Que la barra de comida no baje mientras el jugador esté en la zona."));
        flags.add(f("no-knockback", "Sin retroceso", FlagGroup.PLAYERS,
                "Que los golpes no empujen al jugador."));
        flags.add(f("no-flight", "Sin vuelo", FlagGroup.PLAYERS,
                "Que no se pueda volar en modo creativo ni con permisos de vuelo."));
        flags.add(f("use-elytra", "Usar elytra", FlagGroup.PLAYERS,
                "Planear con elytra por encima de la zona."));
        flags.add(f("fall-damage", "Daño por caída", FlagGroup.PLAYERS,
                "El daño de caída para todo el mundo, jugadores y criaturas por igual."));
        flags.add(f("fall-damage-players", "Daño por caída (jugador)", FlagGroup.PLAYERS,
                "Solo el daño de caída de los jugadores."));
        flags.add(f("level-freeze", "Congelar nivel", FlagGroup.PLAYERS,
                "Que el número de nivel del jugador no cambie mientras esté dentro."));
        flags.add(f("sleep", "Dormir", FlagGroup.PLAYERS,
                "Usar camas para pasar la noche."));
        flags.add(f("send-chat", "Escribir en el chat", FlagGroup.PLAYERS,
                "Mandar mensajes al chat estando dentro de la zona."));
        flags.add(f("exec-command", "Ejecutar comandos", FlagGroup.PLAYERS,
                "Usar comandos estando dentro. Útil para impedir teletransportes de salida."));
        flags.add(f("use-entities", "Usar entidades", FlagGroup.PLAYERS,
                "Interactuar con entidades: marcos, soportes de armadura, vagonetas y barcas."));
        flags.add(f("walker-freeze", "Congelar por caminante", FlagGroup.PLAYERS,
                "Que las botas con Andar sobre el hielo conviertan el agua en hielo al pasar."));
        flags.add(f("lightning", "Protección de rayos", FlagGroup.PLAYERS,
                "Que los rayos causen daño e incendios dentro de la zona."));

        // ---- Criaturas -----------------------------------------------------------------------
        flags.add(f("animal-breeding", "Criar animales", FlagGroup.CREATURES,
                "Alimentar a dos animales para que tengan cría."));
        flags.add(f("animal-taming", "Domar animales", FlagGroup.CREATURES,
                "Domesticar lobos, gatos, caballos, loros y demás."));
        flags.add(f("animal-mounting", "Montar animales", FlagGroup.CREATURES,
                "Subirse a caballos, cerdos, burros y otras monturas."));
        flags.add(f("animal-unmounting", "Desmontar animales", FlagGroup.CREATURES,
                "Bajarse de una montura dentro de la zona."));
        flags.add(f("melee-animals", "Golpear animales", FlagGroup.CREATURES,
                "Daño cuerpo a cuerpo contra animales pacíficos."));
        flags.add(f("melee-monsters", "Golpear monstruos", FlagGroup.CREATURES,
                "Daño cuerpo a cuerpo contra criaturas hostiles."));
        flags.add(f("melee-villagers", "Golpear aldeanos", FlagGroup.CREATURES,
                "Daño cuerpo a cuerpo contra aldeanos."));
        flags.add(f("melee-wtrader", "Golpear mercader", FlagGroup.CREATURES,
                "Daño cuerpo a cuerpo contra el mercader ambulante y sus llamas."));
        flags.add(f("mob-griefing", "Criaturas rompen bloques", FlagGroup.CREATURES,
                "Que las criaturas alteren el terreno, como mobGriefing pero solo aquí."));
        flags.add(f("enderman-griefing", "Enderman mueve bloques", FlagGroup.CREATURES,
                "Que los enderman levanten y suelten bloques."));
        flags.add(f("enderman-tp-from", "Enderman sale de la zona", FlagGroup.CREATURES,
                "Que un enderman se teletransporte desde dentro hacia fuera."));
        flags.add(f("shulker-tp-from", "Shulker sale de la zona", FlagGroup.CREATURES,
                "Que un shulker se teletransporte desde dentro hacia fuera."));
        flags.add(f("fall-damage-animals", "Daño por caída (animal)", FlagGroup.CREATURES,
                "Solo el daño de caída de los animales pacíficos."));
        flags.add(f("fall-damage-monsters", "Daño de caída (monstruo)", FlagGroup.CREATURES,
                "Solo el daño de caída de las criaturas hostiles."));
        flags.add(f("fall-damage-villagers", "Daño de caída (aldeano)", FlagGroup.CREATURES,
                "Solo el daño de caída de los aldeanos."));
        flags.add(f("spawning-all", "Aparición de todo", FlagGroup.CREATURES,
                "Que aparezca cualquier criatura en la zona, sea del tipo que sea."));
        flags.add(f("spawning-animal", "Aparición de animales", FlagGroup.CREATURES,
                "Que aparezcan animales pacíficos."));
        flags.add(f("spawning-monster", "Aparición de monstruos", FlagGroup.CREATURES,
                "Que aparezcan criaturas hostiles."));
        flags.add(f("spawning-villager", "Aparición de aldeanos", FlagGroup.CREATURES,
                "Que aparezcan aldeanos, incluidos los que nacen en una aldea."));
        flags.add(f("spawning-golem", "Aparición de gólems", FlagGroup.CREATURES,
                "Que aparezcan gólems de hierro y de nieve."));
        flags.add(f("spawning-slime", "Aparición de slimes", FlagGroup.CREATURES,
                "Que aparezcan slimes, incluso en las porciones donde les toca."));
        flags.add(f("spawning-trader", "Aparición de mercader", FlagGroup.CREATURES,
                "Que aparezca el mercader ambulante."));
        flags.add(f("spawn-portal", "Portales crean criaturas", FlagGroup.CREATURES,
                "Que los portales al Nether generen zombis pigmen y otras criaturas."));

        // ---- Entorno -------------------------------------------------------------------------
        flags.add(f("fire-tick", "Propagación del fuego", FlagGroup.ENVIRONMENT,
                "Que el fuego se extienda de un bloque a otro y consuma lo que toca."));
        flags.add(f("ignite-explosives", "Encender explosivos", FlagGroup.ENVIRONMENT,
                "Activar TNT y otros explosivos dentro de la zona."));
        flags.add(f("explosions-blocks", "Explosiones (bloques)", FlagGroup.ENVIRONMENT,
                "Que cualquier explosión rompa bloques."));
        flags.add(f("explosions-entities", "Explosiones (entidades)", FlagGroup.ENVIRONMENT,
                "Que cualquier explosión dañe a criaturas y jugadores."));
        flags.add(f("creeper-explosion-blocks", "Creeper (bloques)", FlagGroup.ENVIRONMENT,
                "Solo las explosiones de creeper, y solo el daño al terreno."));
        flags.add(f("creeper-explosion-entities", "Creeper (entidades)", FlagGroup.ENVIRONMENT,
                "Solo las explosiones de creeper, y solo el daño a los seres vivos."));
        flags.add(f("fluid-flow", "Flujo de líquidos", FlagGroup.ENVIRONMENT,
                "Que agua y lava corran y entren o salgan de la zona."));
        flags.add(f("water-flow", "Flujo de agua", FlagGroup.ENVIRONMENT,
                "Solo el agua, dejando que la lava siga corriendo."));
        flags.add(f("lava-flow", "Flujo de lava", FlagGroup.ENVIRONMENT,
                "Solo la lava, dejando que el agua siga corriendo."));
        flags.add(f("leaf-decay", "Caída de hojas", FlagGroup.ENVIRONMENT,
                "Que las hojas sin tronco cerca desaparezcan solas."));
        flags.add(f("snow-fall", "Acumulación de nieve", FlagGroup.ENVIRONMENT,
                "Que la nieve se vaya acumulando en el suelo al nevar."));
        flags.add(f("snow-melting", "Deshielo de la nieve", FlagGroup.ENVIRONMENT,
                "Que la nieve y el hielo se derritan con la luz o el calor."));

        // ---- Viaje ---------------------------------------------------------------------------
        flags.add(f("use-portal", "Usar portales", FlagGroup.TRAVEL,
                "Atravesar cualquier portal desde la zona. Las casillas de abajo lo afinan por tipo."));
        flags.add(f("use-portal-players", "Portal (jugadores)", FlagGroup.TRAVEL,
                "Que los jugadores usen los portales de la zona."));
        flags.add(f("use-portal-animals", "Portal (animales)", FlagGroup.TRAVEL,
                "Que los animales atraviesen los portales de la zona."));
        flags.add(f("use-portal-monsters", "Portal (monstruos)", FlagGroup.TRAVEL,
                "Que las criaturas hostiles atraviesen los portales de la zona."));
        flags.add(f("use-portal-villagers", "Portal (aldeanos)", FlagGroup.TRAVEL,
                "Que los aldeanos atraviesen los portales de la zona."));
        flags.add(f("use-portal-items", "Portal (objetos)", FlagGroup.TRAVEL,
                "Que los objetos tirados pasen por los portales de la zona."));
        flags.add(f("use-portal-minecarts", "Portal (vagonetas)", FlagGroup.TRAVEL,
                "Que las vagonetas atraviesen los portales de la zona."));
        flags.add(f("enderpearl-from", "Perla de Ender: salir", FlagGroup.TRAVEL,
                "Lanzar una perla desde dentro de la zona hacia fuera."));
        flags.add(f("enderpearl-to", "Perla de Ender: entrar", FlagGroup.TRAVEL,
                "Lanzar una perla desde fuera para caer dentro de la zona."));
        flags.add(f("enter-dim", "Entrar a la dimensión", FlagGroup.TRAVEL,
                "Llegar a esta dimensión por cualquier vía. Sirve para cerrarla por completo."));

        ALL = Collections.unmodifiableList(flags);

        Map<String, FlagInfo> byId = new LinkedHashMap<>();
        for (FlagInfo flag : flags) {
            FlagInfo clash = byId.put(flag.id(), flag);
            if (clash != null) {
                throw new IllegalStateException("duplicate flag id in catalogue: " + flag.id());
            }
        }
        BY_ID = Collections.unmodifiableMap(byId);

        Map<FlagGroup, List<FlagInfo>> byGroup = new EnumMap<>(FlagGroup.class);
        for (FlagGroup group : FlagGroup.values()) {
            byGroup.put(group, new ArrayList<>());
        }
        for (FlagInfo flag : flags) {
            byGroup.get(flag.group()).add(flag);
        }
        for (FlagGroup group : FlagGroup.values()) {
            byGroup.put(group, Collections.unmodifiableList(byGroup.get(group)));
        }
        BY_GROUP = Collections.unmodifiableMap(byGroup);
    }

    private static FlagInfo f(String id, String label, FlagGroup group, String help) {
        return new FlagInfo(id, label, help, group);
    }

    /** Every flag, in catalogue order. */
    public static List<FlagInfo> all() {
        return ALL;
    }

    /** The flags on one tab, in the order they should be listed. */
    public static List<FlagInfo> group(FlagGroup group) {
        return BY_GROUP.getOrDefault(group, List.of());
    }

    /** Looks up a flag by YAWP's identifier, or null when the host mod knows one we do not. */
    public static FlagInfo byId(String id) {
        return BY_ID.get(id);
    }

    public static boolean contains(String id) {
        return BY_ID.containsKey(id);
    }

    public static int size() {
        return ALL.size();
    }
}
