package com.athensmc.fantasticregions.selection;

/**
 * The region shapes offered when creating one.
 *
 * <p>YAWP supports five area types. Three are offered here - the three that can be marked out by
 * clicking two blocks. {@code POLYGON_3D} needs an arbitrary number of points and {@code PRISM} needs
 * a polygon plus a height, so both want a different marking flow with its own undo and "finish" step;
 * offering them behind the same two-click rod would produce broken regions.</p>
 *
 * <p>The names are the ones asked for: cuboide, esfera and círculo. The last is a vertical cylinder in
 * YAWP's own terms, which is what a circle drawn on the ground and extended upwards actually is.</p>
 */
public enum Shape {

    CUBOID("cuboide", "Cuboide", "CUBOID",
            "Una caja recta. Marca dos esquinas opuestas y la zona abarca todo lo que queda entre ellas.",
            "Marca la primera esquina de la caja.",
            "Marca la esquina opuesta, en diagonal y a otra altura."),

    SPHERE("esfera", "Esfera", "SPHERE",
            "Una bola. Marca el centro y luego un bloque del borde, que fija el radio en todas direcciones.",
            "Marca el centro de la esfera.",
            "Marca un bloque del borde para fijar el radio."),

    CYLINDER("círculo", "Círculo", "CYLINDER",
            "Un cilindro vertical. Marca el centro y un bloque del borde; abarca de arriba abajo esa columna.",
            "Marca el centro del círculo.",
            "Marca un bloque del borde para fijar el radio.");

    /** How many blocks have to be clicked before the shape is complete. */
    public static final int POINTS_NEEDED = 2;

    private final String id;
    private final String label;
    private final String yawpAreaType;
    private final String help;
    private final String firstPointHint;
    private final String secondPointHint;

    Shape(String id, String label, String yawpAreaType, String help,
          String firstPointHint, String secondPointHint) {
        this.id = id;
        this.label = label;
        this.yawpAreaType = yawpAreaType;
        this.help = help;
        this.firstPointHint = firstPointHint;
        this.secondPointHint = secondPointHint;
    }

    /** The word typed in {@code /yawp crear <nombre> <forma>}, and the key used on the wire. */
    public String id() {
        return id;
    }

    /** Short Spanish name for buttons and headings. */
    public String label() {
        return label;
    }

    /** The matching {@code AreaType} constant in YAWP, kept as a string to avoid a hard link. */
    public String yawpAreaType() {
        return yawpAreaType;
    }

    /** What this shape is, shown when choosing. */
    public String help() {
        return help;
    }

    /** What to do next, shown above the hotbar while marking. */
    public String hintFor(int pointsAlreadyMarked) {
        return pointsAlreadyMarked <= 0 ? firstPointHint : secondPointHint;
    }

    /** Parses the word from the command, case- and accent-insensitively. */
    public static Shape fromId(String value) {
        if (value == null) {
            return null;
        }
        String needle = normalise(value);
        for (Shape shape : values()) {
            if (normalise(shape.id).equals(needle) || normalise(shape.name()).equals(needle)) {
                return shape;
            }
        }
        return null;
    }

    /**
     * Folds case and strips the accent from "círculo".
     *
     * <p>Typing {@code circulo} has to work. The command is in Spanish and the accent is on the
     * completion, but a player who types the word out is not going to reach for the accent, and
     * failing them over a diacritic would be a silly way to lose a region.</p>
     */
    private static String normalise(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            switch (c) {
                case 'á' -> out.append('a');
                case 'é' -> out.append('e');
                case 'í' -> out.append('i');
                case 'ó' -> out.append('o');
                case 'ú', 'ü' -> out.append('u');
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
