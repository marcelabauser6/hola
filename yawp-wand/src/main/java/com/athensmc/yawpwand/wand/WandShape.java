package com.athensmc.yawpwand.wand;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The shapes the wand can preselect, one per area type YAWP actually supports.
 *
 * <p>All five are here, not a chosen three. The wand does not build the area itself - it hands each
 * click to YAWP's own marker logic, which already knows that a polygon takes between three and twenty
 * points and a prism between three and ten. Leaving two shapes out would have been a limitation this
 * mod invented rather than one the host mod has.</p>
 *
 * <p>How many blocks each shape needs is <em>not</em> recorded here. {@code AreaType} exposes
 * {@code neededBlocks} and {@code maxBlocks} as public fields, so the wand reads them off YAWP at
 * runtime. A copy here would be one more thing to get out of step the first time YAWP changes what a
 * cylinder takes.</p>
 *
 * <p>Deliberately free of any Minecraft or YAWP import, so the name parsing can be checked on a plain
 * JVM. The link to {@code AreaType} is by its constant name, resolved when the command runs.</p>
 */
public enum WandShape {

    CUBOID("cuboide", "Cuboide", "CUBOID",
            "Una caja recta, marcando dos esquinas opuestas."),

    SPHERE("esfera", "Esfera", "SPHERE",
            "Una bola, marcando el centro y un bloque del borde."),

    CYLINDER("circulo", "Círculo", "CYLINDER",
            "Un cilindro vertical, marcando el centro y el borde."),

    POLYGON("poligono", "Polígono", "POLYGON_3D",
            "Una planta de varios lados, marcando cada vértice."),

    PRISM("prisma", "Prisma", "PRISM",
            "Un polígono con altura, marcando la base y el techo.");

    private final String id;
    private final String label;
    private final String areaTypeName;
    private final String help;

    WandShape(String id, String label, String areaTypeName, String help) {
        this.id = id;
        this.label = label;
        this.areaTypeName = areaTypeName;
        this.help = help;
    }

    /**
     * The word typed in {@code /yawp wand <forma>} and offered by the completion.
     *
     * <p>Kept to plain ASCII on purpose. Brigadier's word and string argument types only accept an
     * unquoted run of letters, digits and a few symbols, so an id of {@code círculo} would be offered
     * by the completion and then refused by the parser the moment it was accepted. The accent lives on
     * {@link #label()}, which is only ever displayed, and {@link #fromId} takes it either way.</p>
     */
    public String id() {
        return id;
    }

    /** Short Spanish name for messages. */
    public String label() {
        return label;
    }

    /** The matching {@code de.z0rdak.yawp.core.area.AreaType} constant. */
    public String areaTypeName() {
        return areaTypeName;
    }

    /** One line saying what the shape is, shown when the wand is handed over. */
    public String help() {
        return help;
    }

    /**
     * Parses a shape name.
     *
     * <p>Accepts the Spanish word, the same word without its accent, and YAWP's own constant. The
     * accent matters: the completion offers {@code círculo}, but anyone typing it out will write
     * {@code circulo}, and refusing that would be a silly way to fail a command. YAWP's own names are
     * accepted too, because they are what its documentation and its other commands use.</p>
     */
    public static WandShape fromId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String needle = fold(value);
        for (WandShape shape : values()) {
            if (fold(shape.id).equals(needle)
                    || fold(shape.name()).equals(needle)
                    || fold(shape.areaTypeName).equals(needle)) {
                return shape;
            }
        }
        // "cylinder" and "circle" both mean the vertical cylinder; "polygon" is spelled with the 3d
        // suffix in YAWP's enum, which nobody is going to type.
        return switch (needle) {
            case "circle", "cilindro" -> CYLINDER;
            case "polygon", "poligono3d", "polygon3d" -> POLYGON;
            case "box", "caja", "cubo" -> CUBOID;
            case "ball", "bola" -> SPHERE;
            default -> null;
        };
    }

    /** Every name the completion should offer, in menu order. */
    public static Set<String> ids() {
        Set<String> ids = new LinkedHashSet<>();
        for (WandShape shape : values()) {
            ids.add(shape.id);
        }
        return ids;
    }

    /** Folds case, strips Spanish accents and drops the underscore in {@code POLYGON_3D}. */
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
