package de.z0rdak.yawp.wand;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The shapes the wand can preselect, one per area type YAWP supports.
 *
 * <p>The numbers and the type strings here were read out of YAWP's {@code AreaType} enum in the shipped
 * jar, not copied from its documentation. They are duplicated here rather than read off YAWP at runtime,
 * and that is a deliberate reversal of how this worked at first: reading them from YAWP meant importing
 * YAWP, which meant this mod could not even load unless YAWP loaded first. A wand that vanishes whenever
 * the host mod has a bad day is worse than one carrying five numbers that change about once a year.</p>
 *
 * <p>{@link #yawpTypeName()} is the exact string YAWP writes into the marker tag - {@code Cuboid}, not
 * {@code CUBOID}. It serialises the {@code areaType} field and reads it back through {@code AreaType.of},
 * so the capitalisation is load-bearing: get it wrong and YAWP's create command rejects the rod.</p>
 *
 * <p>Free of any Minecraft or YAWP import, so the naming and the point counts can be checked on a plain
 * JVM.</p>
 */
public enum WandShape {

    CUBOID("cuboide", "Cuboide", "Cuboid", 2, 2,
            "Una caja recta, marcando dos esquinas opuestas."),

    SPHERE("esfera", "Esfera", "Sphere", 2, 2,
            "Una bola, marcando el centro y un bloque del borde."),

    /**
     * Three points, not two.
     *
     * <p>Worth pointing out because it is the one shape whose point count is a surprise: YAWP's
     * {@code VerticalCylinderArea} has a two-argument constructor, but its {@code AreaType} asks the
     * marker for three. The marker's count is the one that governs, so three it is.</p>
     */
    CYLINDER("circulo", "Círculo", "Cylinder", 3, 3,
            "Un cilindro vertical, marcando centro, borde y altura."),

    POLYGON("poligono", "Polígono", "Polygon", 3, 20,
            "Una planta de varios lados, marcando cada vértice."),

    PRISM("prisma", "Prisma", "Prism", 3, 10,
            "Un polígono con altura, marcando la base y el techo.");

    private final String id;
    private final String label;
    private final String yawpTypeName;
    private final int neededBlocks;
    private final int maxBlocks;
    private final String help;

    WandShape(String id, String label, String yawpTypeName, int neededBlocks, int maxBlocks,
              String help) {
        this.id = id;
        this.label = label;
        this.yawpTypeName = yawpTypeName;
        this.neededBlocks = neededBlocks;
        this.maxBlocks = maxBlocks;
        this.help = help;
    }

    /**
     * The word typed in the command and offered by the completion.
     *
     * <p>Plain ASCII on purpose. Brigadier only accepts an unquoted run of letters, digits and a few
     * symbols for a word argument, so an id of {@code círculo} would be offered by the completion and
     * then refused by the parser. The accent lives on {@link #label()}, which is only displayed, and
     * {@link #fromId} takes it either way.</p>
     */
    public String id() {
        return id;
    }

    /** Short Spanish name, for messages and the item name. */
    public String label() {
        return label;
    }

    /** The exact string YAWP stores in the marker's {@code type} field. Capitalisation matters. */
    public String yawpTypeName() {
        return yawpTypeName;
    }

    /** Fewest corners YAWP will accept for this shape. */
    public int neededBlocks() {
        return neededBlocks;
    }

    /** Most corners YAWP will accept. Equal to the minimum for everything but polygon and prism. */
    public int maxBlocks() {
        return maxBlocks;
    }

    /** True when the shape takes a range of corners rather than a fixed number. */
    public boolean takesRange() {
        return maxBlocks > neededBlocks;
    }

    /** One line saying what the shape is. */
    public String help() {
        return help;
    }

    /** Looks up a shape by the string YAWP stores in the marker tag. */
    public static WandShape fromYawpTypeName(String value) {
        if (value == null) {
            return null;
        }
        for (WandShape shape : values()) {
            if (shape.yawpTypeName.equalsIgnoreCase(value)) {
                return shape;
            }
        }
        return null;
    }

    /**
     * Parses a shape name.
     *
     * <p>Accepts the Spanish word, the same word without its accent, YAWP's own constant and the obvious
     * synonyms. The accent matters: the completion offers {@code circulo}, but anyone typing it out will
     * write {@code círculo}, and refusing that would be a silly way to fail a command.</p>
     */
    public static WandShape fromId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String needle = fold(value);
        for (WandShape shape : values()) {
            if (fold(shape.id).equals(needle)
                    || fold(shape.name()).equals(needle)
                    || fold(shape.yawpTypeName).equals(needle)) {
                return shape;
            }
        }
        return switch (needle) {
            case "circle", "cilindro" -> CYLINDER;
            case "polygon", "polygon3d", "poligono3d" -> POLYGON;
            case "box", "caja", "cubo" -> CUBOID;
            case "ball", "bola" -> SPHERE;
            default -> null;
        };
    }

    /** Every name the completion offers, in menu order. */
    public static Set<String> ids() {
        Set<String> ids = new LinkedHashSet<>();
        for (WandShape shape : values()) {
            ids.add(shape.id);
        }
        return ids;
    }

    /** Folds case, strips Spanish accents and drops separators, so POLYGON_3D and poligono both match. */
    private static String fold(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case 'á' -> out.append('a');
                case 'é' -> out.append('e');
                case 'í' -> out.append('i');
                case 'ó' -> out.append('o');
                case 'ú', 'ü' -> out.append('u');
                case 'ñ' -> out.append('n');
                case '_', '-', ' ' -> { }
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
