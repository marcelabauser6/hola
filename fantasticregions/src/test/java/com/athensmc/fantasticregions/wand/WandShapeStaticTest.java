package com.athensmc.fantasticregions.wand;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks the shape names the command has to accept and offer.
 *
 * <p>Small, but it guards the one thing that would make the command unusable in a way testing in game
 * would not reliably catch: an identifier the completion offers and the parser then refuses. Brigadier
 * only accepts an unquoted run of plain characters for a word argument, so an accented id would tab in
 * cleanly and fail on submit.</p>
 */
public final class WandShapeStaticTest {

    private static final List<String> FAILURES = new ArrayList<>();

    private WandShapeStaticTest() {
    }

    public static void main(String[] args) {
        checkEveryShapeCoversAYawpAreaType();
        checkIdsSurviveBrigadier();
        checkSpanishNamesParse();
        checkYawpOwnNamesParse();
        checkCommonSynonymsParse();
        checkNonsenseIsRejected();
        checkEveryIdRoundTrips();
        checkHelpTextIsWhole();

        if (!FAILURES.isEmpty()) {
            System.err.println("Wand shape check failed:");
            FAILURES.forEach(failure -> System.err.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("Wand shapes OK: " + WandShape.values().length
                + " shapes, all parseable and all offerable.");
    }

    /** All five of YAWP's area types are covered, so the wand does not silently drop two of them. */
    private static void checkEveryShapeCoversAYawpAreaType() {
        Set<String> expected = Set.of("CUBOID", "CYLINDER", "SPHERE", "POLYGON_3D", "PRISM");
        List<String> covered = new ArrayList<>();
        for (WandShape shape : WandShape.values()) {
            covered.add(shape.areaTypeName());
        }
        for (String areaType : expected) {
            if (!covered.contains(areaType)) {
                FAILURES.add("no shape maps to YAWP's " + areaType);
            }
        }
        if (covered.size() != expected.size()) {
            FAILURES.add("expected " + expected.size() + " shapes, found " + covered.size());
        }
        if (Set.copyOf(covered).size() != covered.size()) {
            FAILURES.add("two shapes map to the same YAWP area type");
        }
    }

    /**
     * Every offered id is something Brigadier's word argument will actually take.
     *
     * <p>Its unquoted character set is letters, digits, underscore, hyphen, dot and plus. An accent is
     * not in it, which is why the ids are ASCII and the accents live on the labels.</p>
     */
    private static void checkIdsSurviveBrigadier() {
        for (String id : WandShape.ids()) {
            for (char c : id.toCharArray()) {
                boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '+';
                if (!allowed) {
                    FAILURES.add("id '" + id + "' contains '" + c
                            + "', which Brigadier will not parse unquoted");
                }
            }
        }
    }

    private static void checkSpanishNamesParse() {
        expect(WandShape.fromId("cuboide") == WandShape.CUBOID, "cuboide");
        expect(WandShape.fromId("esfera") == WandShape.SPHERE, "esfera");
        expect(WandShape.fromId("circulo") == WandShape.CYLINDER, "circulo");
        expect(WandShape.fromId("poligono") == WandShape.POLYGON, "poligono");
        expect(WandShape.fromId("prisma") == WandShape.PRISM, "prisma");

        // Typed with the accent, which the completion does not offer but a person might write.
        expect(WandShape.fromId("círculo") == WandShape.CYLINDER, "círculo with its accent");
        expect(WandShape.fromId("polígono") == WandShape.POLYGON, "polígono with its accent");
        expect(WandShape.fromId("CUBOIDE") == WandShape.CUBOID, "cuboide in capitals");
    }

    /** YAWP's own constant names work too, since its docs and its other commands use them. */
    private static void checkYawpOwnNamesParse() {
        expect(WandShape.fromId("CUBOID") == WandShape.CUBOID, "CUBOID");
        expect(WandShape.fromId("SPHERE") == WandShape.SPHERE, "SPHERE");
        expect(WandShape.fromId("CYLINDER") == WandShape.CYLINDER, "CYLINDER");
        expect(WandShape.fromId("POLYGON_3D") == WandShape.POLYGON, "POLYGON_3D");
        expect(WandShape.fromId("PRISM") == WandShape.PRISM, "PRISM");
        expect(WandShape.fromId("polygon_3d") == WandShape.POLYGON, "polygon_3d in lower case");
    }

    private static void checkCommonSynonymsParse() {
        expect(WandShape.fromId("circle") == WandShape.CYLINDER, "circle");
        expect(WandShape.fromId("cilindro") == WandShape.CYLINDER, "cilindro");
        expect(WandShape.fromId("polygon") == WandShape.POLYGON, "polygon");
        expect(WandShape.fromId("cubo") == WandShape.CUBOID, "cubo");
        expect(WandShape.fromId("bola") == WandShape.SPHERE, "bola");
    }

    private static void checkNonsenseIsRejected() {
        expect(WandShape.fromId("triangulo") == null, "an unknown shape should be refused");
        expect(WandShape.fromId("") == null, "an empty shape should be refused");
        expect(WandShape.fromId("   ") == null, "a blank shape should be refused");
        expect(WandShape.fromId(null) == null, "a missing shape should be refused");
    }

    /** Whatever the completion offers must parse back to the shape that offered it. */
    private static void checkEveryIdRoundTrips() {
        for (WandShape shape : WandShape.values()) {
            WandShape parsed = WandShape.fromId(shape.id());
            if (parsed != shape) {
                FAILURES.add("id '" + shape.id() + "' parsed back to " + parsed
                        + " instead of " + shape);
            }
        }
        if (WandShape.ids().size() != WandShape.values().length) {
            FAILURES.add("two shapes share an id, so one of them cannot be asked for");
        }
    }

    private static void checkHelpTextIsWhole() {
        for (WandShape shape : WandShape.values()) {
            String help = shape.help();
            expect(!help.contains("…") && !help.contains("..."),
                    shape.label() + " help is elided");
            expect(help.endsWith("."), shape.label() + " help is not a finished sentence");
            expect(help.length() <= 80,
                    shape.label() + " help is " + help.length() + " chars, too long for one line");
            expect(!shape.label().isBlank(), shape.name() + " has no label");
        }
    }

    private static void expect(boolean condition, String what) {
        if (!condition) {
            FAILURES.add("failed: " + what);
        }
    }
}
