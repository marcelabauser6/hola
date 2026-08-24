package de.z0rdak.yawp.wand;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks the shape table and the outline geometry on a plain JVM.
 *
 * <p>Both are things that would otherwise only be wrong in game, and quietly. A shape whose point count
 * disagrees with YAWP's means a rod that never reports itself complete; an outline off by one means a
 * perimeter that does not match the region that gets created.</p>
 */
public final class WandStaticTest {

    private static final List<String> FAILURES = new ArrayList<>();

    private WandStaticTest() {
    }

    public static void main(String[] args) {
        checkShapesMatchYawp();
        checkIdsSurviveBrigadier();
        checkNamesParse();
        checkBoxEdgesAreTheBoxEdges();
        checkRadiusRoundsToTheClickedBlock();
        checkCylinderIgnoresHeightInItsRadius();
        checkMeasurementsAreWhole();
        checkCornerBookkeeping();

        if (!FAILURES.isEmpty()) {
            System.err.println("Wand check failed:");
            FAILURES.forEach(failure -> System.err.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("Wand OK: " + WandShape.values().length
                + " shapes, point counts and outline geometry all agree.");
    }

    /**
     * The five shapes, with the point counts and type strings read out of YAWP's AreaType enum.
     *
     * <p>The cylinder wanting three points rather than two is the one that would be assumed wrong, and the
     * capitalisation of the type string is load-bearing: YAWP serialises {@code Cuboid} and reads it back
     * through {@code AreaType.of}, so {@code CUBOID} would not round-trip.</p>
     */
    private static void checkShapesMatchYawp() {
        expectShape(WandShape.CUBOID, "Cuboid", 2, 2);
        expectShape(WandShape.SPHERE, "Sphere", 2, 2);
        expectShape(WandShape.CYLINDER, "Cylinder", 3, 3);
        expectShape(WandShape.POLYGON, "Polygon", 3, 20);
        expectShape(WandShape.PRISM, "Prism", 3, 10);

        if (WandShape.values().length != 5) {
            FAILURES.add("YAWP has five area types, this has " + WandShape.values().length);
        }
        for (WandShape shape : WandShape.values()) {
            if (WandShape.fromYawpTypeName(shape.yawpTypeName()) != shape) {
                FAILURES.add(shape + " does not round-trip through its YAWP type name");
            }
        }
    }

    private static void expectShape(WandShape shape, String typeName, int needed, int max) {
        if (!shape.yawpTypeName().equals(typeName)) {
            FAILURES.add(shape + " should serialise as '" + typeName + "', not '"
                    + shape.yawpTypeName() + "'");
        }
        if (shape.neededBlocks() != needed) {
            FAILURES.add(shape + " needs " + needed + " corners, table says "
                    + shape.neededBlocks());
        }
        if (shape.maxBlocks() != max) {
            FAILURES.add(shape + " accepts up to " + max + ", table says " + shape.maxBlocks());
        }
    }

    /** Brigadier will not parse an accent unquoted, so no offered id may contain one. */
    private static void checkIdsSurviveBrigadier() {
        for (String id : WandShape.ids()) {
            for (char c : id.toCharArray()) {
                boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_'
                        || c == '-' || c == '.' || c == '+';
                if (!allowed) {
                    FAILURES.add("id '" + id + "' has '" + c + "', which Brigadier refuses unquoted");
                }
            }
        }
        if (WandShape.ids().size() != WandShape.values().length) {
            FAILURES.add("two shapes share an id, so one cannot be asked for");
        }
    }

    private static void checkNamesParse() {
        expect(WandShape.fromId("cuboide") == WandShape.CUBOID, "cuboide");
        expect(WandShape.fromId("circulo") == WandShape.CYLINDER, "circulo");
        expect(WandShape.fromId("círculo") == WandShape.CYLINDER, "círculo with the accent");
        expect(WandShape.fromId("poligono") == WandShape.POLYGON, "poligono");
        expect(WandShape.fromId("polígono") == WandShape.POLYGON, "polígono with the accent");
        expect(WandShape.fromId("Cylinder") == WandShape.CYLINDER, "YAWP's own Cylinder");
        expect(WandShape.fromId("circle") == WandShape.CYLINDER, "circle");
        expect(WandShape.fromId("cubo") == WandShape.CUBOID, "cubo");
        expect(WandShape.fromId("triangulo") == null, "an unknown shape is refused");
        expect(WandShape.fromId(null) == null, "a missing shape is refused");
        for (WandShape shape : WandShape.values()) {
            expect(WandShape.fromId(shape.id()) == shape, shape + " round-trips through its id");
        }
    }

    /**
     * A 3x3x3 wireframe is 20 blocks: 8 corners plus one midpoint on each of the 12 edges.
     *
     * <p>Worth being precise about, because the tempting figure is 26 - all 27 blocks less the middle - and
     * that is the count for the whole surface shell, not the twelve edges. Drawing the shell would put a
     * particle on every face, which for a large region is a wall of dust rather than an outline. The face
     * centre is checked explicitly, since it is the block that separates the two readings.</p>
     */
    private static void checkBoxEdgesAreTheBoxEdges() {
        List<int[]> edges = Outlines.boxEdges(new int[]{0, 0, 0}, new int[]{2, 2, 2});
        if (edges.size() != 20) {
            FAILURES.add("a 3x3x3 wireframe is 20 blocks, got " + edges.size());
        }
        for (int[] point : edges) {
            if (point[0] == 1 && point[1] == 1 && point[2] == 1) {
                FAILURES.add("the wireframe included the middle block");
            }
            // (1,1,0) is the centre of a face: on the shell, but not on any edge.
            if (point[0] == 1 && point[1] == 1 && point[2] == 0) {
                FAILURES.add("the wireframe included a face centre, so it is drawing the shell");
            }
        }

        // No duplicates: the twelve edges share eight corners, and each must be listed once.
        Set<String> unique = new java.util.HashSet<>();
        for (int[] point : edges) {
            if (!unique.add(point[0] + "," + point[1] + "," + point[2])) {
                FAILURES.add("the frame listed a block twice");
            }
        }

        List<int[]> single = Outlines.boxEdges(new int[]{5, 5, 5}, new int[]{5, 5, 5});
        if (single.size() != 1) {
            FAILURES.add("a one-block box is one point, got " + single.size());
        }
    }

    private static void checkRadiusRoundsToTheClickedBlock() {
        // 6 and 8 apart is exactly 10, the classic triple, so this pins the maths not the rounding.
        int exact = Outlines.radius(new int[]{0, 64, 0}, new int[]{6, 64, 8}, true);
        expect(exact == 10, "a 6/8 offset is radius 10, got " + exact);

        // Truncating would give 3 and leave the clicked block outside the sphere.
        int rounded = Outlines.radius(new int[]{0, 0, 0}, new int[]{2, 2, 2}, true);
        expect(rounded == 3, "a 2/2/2 offset rounds to 3, got " + rounded);
    }

    private static void checkCylinderIgnoresHeightInItsRadius() {
        int flat = Outlines.radius(new int[]{0, 40, 0}, new int[]{5, 100, 0}, false);
        expect(flat == 5, "a cylinder's radius is measured on the ground, got " + flat);
        int spherical = Outlines.radius(new int[]{0, 40, 0}, new int[]{5, 100, 0}, true);
        expect(spherical > 50, "a sphere's radius does count height, got " + spherical);
    }

    private static void checkMeasurementsAreWhole() {
        List<int[]> two = List.of(new int[]{0, 64, 0}, new int[]{9, 74, 19});
        for (WandShape shape : WandShape.values()) {
            String measure = Outlines.measure(shape, two);
            expect(!measure.contains("…") && !measure.contains("..."),
                    shape + " measurement is elided: " + measure);
            expect(measure.length() <= 44,
                    shape + " measurement is " + measure.length() + " chars: " + measure);
            expect(shape.help().length() <= 70,
                    shape + " help is " + shape.help().length() + " chars, too long for a line");
        }
        expect(Outlines.measure(WandShape.CUBOID, List.of()).equals("Sin marcar"),
                "an empty selection says so rather than showing zeroes");

        // A 10x11x20 box, spans inclusive of both end blocks.
        expect(Outlines.measure(WandShape.CUBOID, two).equals("10 x 11 x 20 bloques"),
                "cuboid spans should count both ends, got "
                        + Outlines.measure(WandShape.CUBOID, two));
    }

    /** Adding, replacing and completing corners, which the marking depends on. */
    private static void checkCornerBookkeeping() {
        expect(!MarkerData.isComplete(WandShape.CUBOID, 1), "one corner is not a cuboid");
        expect(MarkerData.isComplete(WandShape.CUBOID, 2), "two corners is a cuboid");
        expect(!MarkerData.isComplete(WandShape.CYLINDER, 2), "two corners is not a cylinder");
        expect(MarkerData.isComplete(WandShape.CYLINDER, 3), "three corners is a cylinder");
        expect(MarkerData.isComplete(WandShape.POLYGON, 3), "three vertices is a polygon");
        expect(MarkerData.isComplete(WandShape.POLYGON, 20), "twenty vertices is still a polygon");
        expect(!MarkerData.isComplete(WandShape.POLYGON, 21), "twenty-one vertices is too many");
    }

    private static void expect(boolean condition, String what) {
        if (!condition) {
            FAILURES.add("failed: " + what);
        }
    }
}
