package com.athensmc.fantasticregions.selection;

import com.athensmc.fantasticregions.selection.Selection.Point;

import java.util.ArrayList;
import java.util.List;

/** Checks the marking maths that the live readout and the built region both depend on. */
public final class SelectionStaticTest {

    private static final List<String> FAILURES = new ArrayList<>();

    private SelectionStaticTest() {
    }

    public static void main(String[] args) {
        checkSpansIncludeBothEnds();
        checkCornerOrderDoesNotMatter();
        checkRadiusRoundsToTheClickedBlock();
        checkCylinderIgnoresHeightInItsRadius();
        checkUndoStepsBackOnePoint();
        checkFurtherClicksMoveTheFarCorner();
        checkShapeNamesParse();
        checkReadoutsAreWhole();

        if (!FAILURES.isEmpty()) {
            System.err.println("Selection check failed:");
            FAILURES.forEach(failure -> System.err.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("Selection OK: spans, radii, undo and shape names all behave.");
    }

    /** Two corners one block apart span two blocks. The off-by-one here would be invisible in game. */
    private static void checkSpansIncludeBothEnds() {
        Selection selection = Selection.empty(Shape.CUBOID)
                .withPoint(new Point(0, 64, 0))
                .withPoint(new Point(1, 65, 2));
        Selection.Extent extent = selection.extent();
        expect(extent.spanX() == 2, "x span should be 2, was " + extent.spanX());
        expect(extent.spanY() == 2, "y span should be 2, was " + extent.spanY());
        expect(extent.spanZ() == 3, "z span should be 3, was " + extent.spanZ());
        expect(extent.blocks() == 12L, "block count should be 12, was " + extent.blocks());

        Selection single = Selection.empty(Shape.CUBOID).withPoint(new Point(5, 5, 5));
        expect(single.extent().blocks() == 1L, "one marked corner is one block");
    }

    private static void checkCornerOrderDoesNotMatter() {
        Point low = new Point(-10, 60, -10);
        Point high = new Point(10, 80, 10);
        Selection forwards = Selection.empty(Shape.CUBOID).withPoint(low).withPoint(high);
        Selection backwards = Selection.empty(Shape.CUBOID).withPoint(high).withPoint(low);
        expect(forwards.min().equals(backwards.min()), "min should not depend on click order");
        expect(forwards.max().equals(backwards.max()), "max should not depend on click order");
        expect(forwards.extent().equals(backwards.extent()), "extent should not depend on order");
    }

    /**
     * A block clicked at distance 10.4 is meant to be inside the sphere.
     *
     * <p>Truncating would give radius 10 and leave the very block the player pointed at outside the
     * region, which reads as the rod having missed.</p>
     */
    private static void checkRadiusRoundsToTheClickedBlock() {
        Selection selection = Selection.empty(Shape.SPHERE)
                .withPoint(new Point(0, 64, 0))
                .withPoint(new Point(6, 64, 8));
        expect(selection.radius() == 10, "radius of a 6/8 offset should be 10, was "
                + selection.radius());

        Selection rounded = Selection.empty(Shape.SPHERE)
                .withPoint(new Point(0, 0, 0))
                .withPoint(new Point(3, 0, 0));
        expect(rounded.radius() == 3, "radius along one axis is the offset");
    }

    private static void checkCylinderIgnoresHeightInItsRadius() {
        Selection tall = Selection.empty(Shape.CYLINDER)
                .withPoint(new Point(0, 40, 0))
                .withPoint(new Point(5, 100, 0));
        expect(tall.radius() == 5,
                "a cylinder's radius is measured on the ground, was " + tall.radius());
        expect(tall.extent().spanY() == 61,
                "the cylinder's height comes from the two clicks, was " + tall.extent().spanY());
    }

    private static void checkUndoStepsBackOnePoint() {
        Selection two = Selection.empty(Shape.CUBOID)
                .withPoint(new Point(1, 1, 1))
                .withPoint(new Point(2, 2, 2));
        Selection one = two.withoutLastPoint();
        expect(one.markedCount() == 1, "undo from two points leaves one");
        Selection none = one.withoutLastPoint();
        expect(none.markedCount() == 0, "undo from one point leaves none");
        expect(none.withoutLastPoint().markedCount() == 0, "undo on an empty selection is harmless");
    }

    private static void checkFurtherClicksMoveTheFarCorner() {
        Selection selection = Selection.empty(Shape.CUBOID)
                .withPoint(new Point(0, 0, 0))
                .withPoint(new Point(5, 5, 5))
                .withPoint(new Point(9, 9, 9));
        expect(selection.markedCount() == 2, "a third click does not add a third point");
        expect(selection.max().equals(new Point(9, 9, 9)),
                "a third click replaces the far corner, so an overshoot can be corrected");
        expect(selection.first().equals(new Point(0, 0, 0)), "the first corner is left alone");
    }

    private static void checkShapeNamesParse() {
        expect(Shape.fromId("cuboide") == Shape.CUBOID, "cuboide should parse");
        expect(Shape.fromId("esfera") == Shape.SPHERE, "esfera should parse");
        expect(Shape.fromId("círculo") == Shape.CYLINDER, "círculo should parse");
        expect(Shape.fromId("circulo") == Shape.CYLINDER, "circulo without the accent should parse");
        expect(Shape.fromId("CÍRCULO") == Shape.CYLINDER, "case should not matter");
        expect(Shape.fromId("CYLINDER") == Shape.CYLINDER, "the enum name should parse too");
        expect(Shape.fromId("triangulo") == null, "an unknown shape is rejected");
        expect(Shape.fromId(null) == null, "a missing shape is rejected");
    }

    /** No elided text anywhere the player reads a measurement. */
    private static void checkReadoutsAreWhole() {
        for (Shape shape : Shape.values()) {
            Selection selection = Selection.empty(shape)
                    .withPoint(new Point(0, 64, 0))
                    .withPoint(new Point(120, 190, 120));
            String readout = selection.extentDisplay();
            expect(!readout.contains("…") && !readout.contains("..."),
                    shape.label() + " readout is elided: " + readout);
            expect(readout.length() <= 40,
                    shape.label() + " readout is " + readout.length() + " chars: " + readout);
            expect(shape.help().length() <= 110,
                    shape.label() + " help is " + shape.help().length() + " chars, too long");
            expect(!Selection.empty(shape).isComplete(), "an empty selection is not complete");
            expect(selection.isComplete(), "two points completes " + shape.label());
        }
        expect(Selection.empty(Shape.CUBOID).extentDisplay().equals("Sin marcar"),
                "an empty selection says so rather than showing zeroes");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            FAILURES.add(message);
        }
    }
}
