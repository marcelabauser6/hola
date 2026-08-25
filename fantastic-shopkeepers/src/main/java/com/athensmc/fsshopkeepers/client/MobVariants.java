package com.athensmc.fsshopkeepers.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The appearance choices a given mob offers.
 *
 * <p>Each option is a label, a list of choices and the pair of functions that read and write the choice on the mob's
 * NBT. Expressing a variant as a read/write pair over NBT is what keeps this to one small file: Shopkeepers needed a
 * class per variant because it set typed fields on typed entities, whereas a sheep's colour is just
 * {@code Color: 4} and can be edited without knowing what a sheep is.</p>
 *
 * <p>A mob with no entry here is not an error - it simply has no variants worth offering, and the editor says so
 * rather than showing an empty control. Mobs from other mods land in that case and are still fully usable as
 * shopkeepers; only their variants are beyond what this can guess.</p>
 */
public final class MobVariants {

    /** Age value vanilla uses for a permanently young animal. */
    private static final int BABY_AGE = -24000;

    private MobVariants() {
    }

    /** One editable appearance choice. */
    public static final class Option {

        private final String label;
        private final List<String> choices;
        private final Reader reader;
        private final Writer writer;

        Option(String label, List<String> choices, Reader reader, Writer writer) {
            this.label = label;
            this.choices = List.copyOf(choices);
            this.reader = reader;
            this.writer = writer;
        }

        public String label() {
            return label;
        }

        public List<String> choices() {
            return choices;
        }

        /** The choice currently stored, clamped into range so bad data shows as the first choice. */
        public int index(CompoundTag data) {
            int raw = reader.read(data);
            if (raw < 0 || raw >= choices.size()) {
                return 0;
            }
            return raw;
        }

        public String currentLabel(CompoundTag data) {
            return choices.get(index(data));
        }

        /** Moves to the next choice, wrapping round. */
        public void cycle(CompoundTag data) {
            writer.write(data, (index(data) + 1) % choices.size());
        }

        interface Reader {
            int read(CompoundTag data);
        }

        interface Writer {
            void write(CompoundTag data, int index);
        }
    }

    private static final List<String> DYE_COLOURS = List.of(
            "Blanco", "Naranja", "Magenta", "Azul claro", "Amarillo", "Lima", "Rosa", "Gris",
            "Gris claro", "Cian", "Purpura", "Azul", "Marron", "Verde", "Rojo", "Negro");

    private static final List<String> VILLAGER_PROFESSIONS = List.of(
            "Ninguna", "Armero", "Carnicero", "Cartografo", "Clerigo", "Granjero", "Pescador",
            "Fletchero", "Peletero", "Bibliotecario", "Albanil", "Aldeano", "Pastor", "Herrero", "Herrero de armas");

    private static final List<String> VILLAGER_TYPES = List.of(
            "Desierto", "Jungla", "Llanura", "Sabana", "Nieve", "Pantano", "Taiga");

    private static final List<String> NO_YES = List.of("No", "Si");

    /**
     * The options a mob offers.
     *
     * <p>Grouped by what the mob actually is rather than by class hierarchy, because the NBT key is the same for every
     * dyeable collar whether the animal is a wolf or a cat.</p>
     */
    public static List<Option> optionsFor(ResourceLocation entityType) {
        List<Option> options = new ArrayList<>();
        String path = entityType.getPath();

        if (isAgeable(path)) {
            options.add(new Option("Bebe", NO_YES,
                    data -> data.getInt("Age") < 0 ? 1 : 0,
                    (data, index) -> data.putInt("Age", index == 1 ? BABY_AGE : 0)));
        }
        if (isZombieLike(path)) {
            options.add(new Option("Bebe", NO_YES,
                    data -> data.getBoolean("IsBaby") ? 1 : 0,
                    (data, index) -> data.putBoolean("IsBaby", index == 1)));
        }
        switch (path) {
            case "sheep" -> options.add(new Option("Lana", DYE_COLOURS,
                    data -> data.getByte("Color"),
                    (data, index) -> data.putByte("Color", (byte) index)));
            case "wolf", "cat" -> options.add(new Option("Collar", DYE_COLOURS,
                    data -> data.getByte("CollarColor"),
                    (data, index) -> data.putByte("CollarColor", (byte) index)));
            case "shulker" -> options.add(new Option("Color", DYE_COLOURS,
                    data -> data.getByte("Color"),
                    (data, index) -> data.putByte("Color", (byte) index)));
            case "villager", "zombie_villager" -> {
                options.add(new Option("Profesion", VILLAGER_PROFESSIONS,
                        data -> professionIndex(data),
                        (data, index) -> setProfession(data, index)));
                options.add(new Option("Bioma", VILLAGER_TYPES,
                        data -> typeIndex(data),
                        (data, index) -> setType(data, index)));
            }
            case "rabbit" -> options.add(new Option("Pelaje",
                    List.of("Marron", "Blanco", "Negro", "Blanco y negro", "Dorado", "Sal y pimienta", "Asesino"),
                    data -> Math.min(6, Math.max(0, data.getInt("RabbitType"))),
                    (data, index) -> data.putInt("RabbitType", index == 6 ? 99 : index)));
            case "panda" -> options.add(new Option("Caracter",
                    List.of("Normal", "Perezoso", "Preocupado", "Jugueton", "Debil", "Agresivo", "Marron"),
                    data -> pandaIndex(data),
                    (data, index) -> setPanda(data, index)));
            case "fox" -> options.add(new Option("Tipo", List.of("Rojo", "Nieve"),
                    data -> "snow".equals(data.getString("Type")) ? 1 : 0,
                    (data, index) -> data.putString("Type", index == 1 ? "snow" : "red")));
            case "parrot" -> options.add(new Option("Color",
                    List.of("Rojo y azul", "Azul", "Verde", "Amarillo y azul", "Gris"),
                    data -> Math.min(4, Math.max(0, data.getInt("Variant"))),
                    (data, index) -> data.putInt("Variant", index)));
            case "llama", "trader_llama" -> options.add(new Option("Pelaje",
                    List.of("Crema", "Blanco", "Marron", "Gris"),
                    data -> Math.min(3, Math.max(0, data.getInt("Variant"))),
                    (data, index) -> data.putInt("Variant", index)));
            case "horse" -> options.add(new Option("Pelaje",
                    List.of("Blanco", "Crema", "Castano", "Marron", "Negro", "Gris", "Alazan"),
                    data -> Math.min(6, Math.max(0, data.getInt("Variant") & 0xFF)),
                    (data, index) -> data.putInt("Variant", index)));
            case "axolotl" -> options.add(new Option("Color",
                    List.of("Rosa", "Marron", "Amarillo", "Cian", "Azul"),
                    data -> Math.min(4, Math.max(0, data.getInt("Variant"))),
                    (data, index) -> data.putInt("Variant", index)));
            case "frog" -> options.add(new Option("Variante",
                    List.of("Templada", "Calida", "Frida"),
                    data -> frogIndex(data),
                    (data, index) -> data.putString("variant", switch (index) {
                        case 1 -> "minecraft:warm";
                        case 2 -> "minecraft:cold";
                        default -> "minecraft:temperate";
                    })));
            case "mooshroom" -> options.add(new Option("Seta", List.of("Roja", "Marron"),
                    data -> "brown".equals(data.getString("Type")) ? 1 : 0,
                    (data, index) -> data.putString("Type", index == 1 ? "brown" : "red")));
            case "cow", "pig", "chicken" -> {
                // Nothing beyond age, already offered above.
            }
            default -> {
                // Unknown or variant-less mob: age only, if it has one.
            }
        }
        if (isSittable(path)) {
            options.add(new Option("Sentado", NO_YES,
                    data -> data.getBoolean("Sitting") ? 1 : 0,
                    (data, index) -> data.putBoolean("Sitting", index == 1)));
        }
        return options;
    }

    private static boolean isAgeable(String path) {
        return switch (path) {
            case "villager", "cow", "pig", "sheep", "chicken", "wolf", "cat", "fox", "panda", "llama",
                    "trader_llama", "horse", "donkey", "mule", "rabbit", "mooshroom", "goat", "axolotl",
                    "bee", "polar_bear", "turtle", "ocelot", "hoglin", "strider", "camel", "sniffer",
                    "armadillo" -> true;
            default -> false;
        };
    }

    private static boolean isZombieLike(String path) {
        return switch (path) {
            case "zombie", "zombie_villager", "husk", "drowned", "zombified_piglin", "piglin" -> true;
            default -> false;
        };
    }

    private static boolean isSittable(String path) {
        return switch (path) {
            case "wolf", "cat", "ocelot", "parrot", "fox" -> true;
            default -> false;
        };
    }

    /** Villager data lives in a nested compound, so profession is read through it. */
    private static int professionIndex(CompoundTag data) {
        String profession = data.getCompound("VillagerData").getString("profession");
        int index = VILLAGER_PROFESSION_IDS.indexOf(stripNamespace(profession));
        return Math.max(0, index);
    }

    private static void setProfession(CompoundTag data, int index) {
        CompoundTag villager = data.getCompound("VillagerData").copy();
        villager.putString("profession", "minecraft:" + VILLAGER_PROFESSION_IDS.get(index));
        if (!villager.contains("type")) {
            villager.putString("type", "minecraft:plains");
        }
        if (!villager.contains("level")) {
            villager.putInt("level", 2);
        }
        data.put("VillagerData", villager);
    }

    private static int typeIndex(CompoundTag data) {
        String type = data.getCompound("VillagerData").getString("type");
        int index = VILLAGER_TYPE_IDS.indexOf(stripNamespace(type));
        return Math.max(0, index);
    }

    private static void setType(CompoundTag data, int index) {
        CompoundTag villager = data.getCompound("VillagerData").copy();
        villager.putString("type", "minecraft:" + VILLAGER_TYPE_IDS.get(index));
        if (!villager.contains("profession")) {
            villager.putString("profession", "minecraft:none");
        }
        if (!villager.contains("level")) {
            villager.putInt("level", 2);
        }
        data.put("VillagerData", villager);
    }

    private static int pandaIndex(CompoundTag data) {
        String gene = data.getString("MainGene");
        int index = PANDA_GENES.indexOf(gene);
        return Math.max(0, index);
    }

    private static void setPanda(CompoundTag data, int index) {
        String gene = PANDA_GENES.get(index);
        data.putString("MainGene", gene);
        data.putString("HiddenGene", gene);
    }

    private static int frogIndex(CompoundTag data) {
        return switch (stripNamespace(data.getString("variant"))) {
            case "warm" -> 1;
            case "cold" -> 2;
            default -> 0;
        };
    }

    private static String stripNamespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private static final List<String> VILLAGER_PROFESSION_IDS = List.of(
            "none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");

    private static final List<String> VILLAGER_TYPE_IDS = List.of(
            "desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga");

    private static final List<String> PANDA_GENES = List.of(
            "normal", "lazy", "worried", "playful", "weak", "aggressive", "brown");
}
