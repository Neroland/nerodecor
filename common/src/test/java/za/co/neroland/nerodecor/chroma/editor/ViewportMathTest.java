package za.co.neroland.nerodecor.chroma.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.RelativePos;

class ViewportMathTest {

    @Test
    void cameraProjectsTargetToCentreAndRayRoundTrips() {
        OrbitCamera cam = new OrbitCamera();
        cam.setViewport(200, 100);
        cam.setTarget(0.5, 0.5, 0.5);
        OrbitCamera.Projected p = cam.project(0.5, 0.5, 0.5);
        assertNotNull(p);
        assertEquals(100.0, p.x(), 1e-6);
        assertEquals(50.0, p.y(), 1e-6);
        OrbitCamera.Ray ray = cam.ray(100.0, 50.0);
        // The centre ray passes through the target.
        double t = (0.5 - ray.ox()) / ray.dx();
        assertEquals(0.5, ray.oy() + ray.dy() * t, 1e-6);
        assertEquals(0.5, ray.oz() + ray.dz() * t, 1e-6);
    }

    @Test
    void pickerFindsFirstOccupiedCellAndFace() {
        OrbitCamera.Ray ray = new OrbitCamera.Ray(-10.5, 0.5, 0.5, 1, 0, 0);
        VoxelPicker.Hit hit = VoxelPicker.pick(ray, -8, -8, -8, 8, 8, 8, (x, y, z) -> x == 3 && y == 0 && z == 0);
        assertNotNull(hit);
        assertEquals(3, hit.x());
        assertEquals(-1, hit.nx());
        assertNull(VoxelPicker.pick(ray, -8, -8, -8, 8, 8, 8, (x, y, z) -> false));
        // Missing the box entirely.
        assertNull(VoxelPicker.pick(new OrbitCamera.Ray(-10, 50, 0, 1, 0, 0), -8, -8, -8, 8, 8, 8, (x, y, z) -> true));
    }

    @Test
    void pickerRespectsSliceThroughBounds() {
        OrbitCamera.Ray down = new OrbitCamera.Ray(0.5, 20, 0.5, 0, -1, 0);
        VoxelPicker.Hit hit = VoxelPicker.pick(down, -4, -4, -4, 4, 1, 4, (x, y, z) -> x == 0 && z == 0);
        assertNotNull(hit);
        assertEquals(1, hit.y());
        assertEquals(1, hit.ny());
    }

    @Test
    void floodRespectsZoneAndLimit() {
        EditorModel m = EditorModelTest.row(10); // zone 0: x 0-4, zone 1: x 5-9
        assertEquals(5, Selections.flood(m, RelativePos.pack(0, 0, 0), Selections.FloodMatch.ZONE, 64, 100).size());
        assertEquals(10, Selections.flood(m, RelativePos.pack(0, 0, 0), Selections.FloodMatch.LINKED, 64, 100).size());
        assertEquals(3, Selections.flood(m, RelativePos.pack(0, 0, 0), Selections.FloodMatch.LINKED, 64, 3).size());
        assertTrue(Selections.flood(m, RelativePos.pack(0, 0, 0), Selections.FloodMatch.LINKED, -1, 100).isEmpty());
    }

    @Test
    void boxSelectsInclusiveCorners() {
        EditorModel m = EditorModelTest.row(10);
        assertEquals(4, Selections.box(m, 2, 0, 0, 5, 0, 0, 64).size());
    }

    @Test
    void gradientsAreQuantisedAndEndToEnd() {
        EditorModel m = EditorModelTest.row(10);
        java.util.List<Integer> blocks = Selections.flood(m, m.offset(0), Selections.FloodMatch.LINKED, 64, 100);
        var g = za.co.neroland.nerodecor.chroma.ChromaGradient.even(java.util.List.of(ChromaColor.BLACK,
                ChromaColor.WHITE));
        var two = Selections.twoPointGradient(blocks, RelativePos.pack(0, 0, 0), RelativePos.pack(9, 0, 0), g, 4);
        assertEquals(ChromaColor.BLACK.rgb(), two.get(RelativePos.pack(0, 0, 0)).rgb());
        assertEquals(ChromaColor.WHITE.rgb(), two.get(RelativePos.pack(9, 0, 0)).rgb());
        assertTrue(two.values().stream().map(ChromaColor::packed).distinct().count() <= 4);
        var axis = Selections.axisGradient(blocks, 0, g, 16);
        assertEquals(ChromaColor.WHITE.rgb(), axis.get(RelativePos.pack(9, 0, 0)).rgb());
        assertEquals(0.0, Selections.quantise(-1, 5));
        assertEquals(0.5, Selections.quantise(0.49, 3));
    }

    @Test
    void lodSwitchesAboveTwoThousandBlocks() {
        assertFalse(ViewportRaster.useLod(2000));
        assertTrue(ViewportRaster.useLod(2001));
    }

    @Test
    void rasterDrawsOnlyExposedFacesAndHonoursSlice() {
        EditorModel m = EditorModelTest.row(3);
        OrbitCamera cam = new OrbitCamera();
        cam.setViewport(320, 200);
        cam.frame(0, 0, 0, 3, 1, 1);
        RectList out = new RectList(ViewportRaster.MAX_RECTS);
        assertFalse(ViewportRaster.render(m, cam, 64, Set.of(), Integer.MIN_VALUE, out));
        assertTrue(out.size() > 0);
        for (int i = 0; i < out.size(); i++) {
            assertTrue(out.x0(i) >= 0 && out.x1(i) <= 320 && out.y0(i) >= 0 && out.y1(i) <= 200);
        }
        ViewportRaster.render(m, cam, -1, Set.of(), Integer.MIN_VALUE, out);
        assertEquals(0, out.size());
    }

    @Test
    void scanlineFillsASquare() {
        double[][] square = {{10, 10, 1}, {20, 10, 1}, {20, 20, 1}, {10, 20, 1}};
        RectList out = new RectList(100);
        ViewportRaster.fillQuad(square, 100, 100, 0xFFFFFFFF, out);
        assertEquals(1, out.size()); // rows merge into one rectangle
        assertEquals(10, out.x0(0));
        assertEquals(20, out.x1(0));
        assertEquals(10, out.y1(0) - out.y0(0));
    }

    @Test
    void undoStackDropsOldest() {
        UndoStack<Integer> s = new UndoStack<>(2);
        s.push(1);
        s.push(2);
        s.push(3);
        assertEquals(3, s.undo(4));
        assertEquals(2, s.undo(3));
        assertNull(s.undo(2));
        assertEquals(3, s.redo(2));
    }
}
