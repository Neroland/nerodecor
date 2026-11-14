package za.co.neroland.nerodecor.chroma.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaOp;
import za.co.neroland.nerodecor.chroma.ChromaOpBatch;
import za.co.neroland.nerodecor.chroma.RelativePos;

class EditorModelTest {

    private static final ChromaColor RED = ChromaColor.of(255, 0, 0);
    private static final ChromaColor BLUE = ChromaColor.of(0, 0, 255);

    /** A row of {@code n} blocks along X, all palette 0, zone 0 for the first half and 1 for the rest. */
    static EditorModel row(int n) {
        int[] offsets = new int[n];
        int[] values = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = RelativePos.pack(i, 0, 0);
            values[i] = (i < n / 2 ? 0 : 1) << 8;
        }
        return new EditorModel(7L, offsets, values, new int[] {ChromaColor.WHITE.packed()});
    }

    @Test
    void cleanModelHasNoOps() {
        EditorModel m = row(4);
        assertFalse(m.isDirty());
        assertTrue(m.buildBatches().isEmpty());
    }

    @Test
    void paintingAppendsPaletteEntryFirstThenIndices() {
        EditorModel m = row(4);
        m.begin();
        assertEquals(2, m.paint(List.of(RelativePos.pack(0, 0, 0), RelativePos.pack(1, 0, 0)), RED));
        List<ChromaOp> ops = m.buildOps();
        assertEquals(3, ops.size());
        assertEquals(ChromaOp.Kind.SET_PALETTE_ENTRY, ops.get(0).kind());
        assertEquals(1, ops.get(0).target());
        assertEquals(ChromaOp.Kind.SET_INDEX, ops.get(1).kind());
        List<ChromaOpBatch> batches = m.buildBatches();
        assertEquals(1, batches.size());
        assertEquals(-1, batches.get(0).firstUnresolvedIndex(1));
    }

    @Test
    void reusesExactColourAndUnusedEntriesButNeverIndexZero() {
        EditorModel m = new EditorModel(1L, new int[] {RelativePos.pack(0, 0, 0)}, new int[] {1},
                new int[] {ChromaColor.WHITE.packed(), RED.packed(), BLUE.packed()});
        assertEquals(1, m.allocate(RED));
        // Entry 2 is unused: a new colour takes it instead of growing the palette.
        ChromaColor green = ChromaColor.of(0, 255, 0);
        assertEquals(2, m.allocate(green));
        assertEquals(3, m.paletteSize());
        m.setIndex(RelativePos.pack(0, 0, 0), 2);
        // Now 1 is unused (0 is reserved for newly linked blocks).
        assertEquals(1, m.allocate(ChromaColor.of(1, 2, 3)));
    }

    @Test
    void undoAndRedoRestoreState() {
        EditorModel m = row(4);
        int a = RelativePos.pack(0, 0, 0);
        m.begin();
        m.paint(List.of(a), RED);
        m.begin();
        m.paint(List.of(a), BLUE);
        assertEquals(BLUE, m.colourAt(a));
        assertTrue(m.undo());
        assertEquals(RED, m.colourAt(a));
        assertTrue(m.undo());
        assertEquals(ChromaColor.WHITE, m.colourAt(a));
        assertFalse(m.isDirty());
        assertTrue(m.redo());
        assertEquals(RED, m.colourAt(a));
    }

    @Test
    void undoIsBoundedToThirtyTwoSteps() {
        EditorModel m = row(2);
        for (int i = 0; i < 40; i++) {
            m.begin();
            m.paint(List.of(RelativePos.pack(0, 0, 0)), ChromaColor.of(i, 0, 0));
        }
        int undone = 0;
        while (m.undo()) {
            undone++;
        }
        assertEquals(EditorModel.UNDO_DEPTH, undone);
    }

    @Test
    void recolourZoneChangesASharedEntryInPlace() {
        EditorModel m = row(4);
        m.begin();
        m.paint(m.zoneBlocks(1), RED);
        int before = m.paletteSize();
        m.begin();
        assertEquals(2, m.recolourZone(1, BLUE));
        assertEquals(before, m.paletteSize());
        assertEquals(BLUE, m.colourAt(RelativePos.pack(3, 0, 0)));
        assertEquals(ChromaColor.WHITE, m.colourAt(RelativePos.pack(0, 0, 0)));
    }

    @Test
    void zoneAssignmentsBecomeAssignOps() {
        EditorModel m = row(4);
        m.begin();
        m.setZone(RelativePos.pack(0, 0, 0), 3);
        List<ChromaOp> ops = m.buildOps();
        assertEquals(1, ops.size());
        assertEquals(ChromaOp.Kind.ASSIGN_ZONE, ops.get(0).kind());
        assertEquals(3, ops.get(0).value());
    }

    @Test
    void largeEditsSplitIntoOrderedBatches() {
        // 40 x 30 blocks: offsets must stay inside RelativePos' +-512 range.
        int[] offsets = new int[1200];
        int[] values = new int[1200];
        for (int i = 0; i < 1200; i++) {
            offsets[i] = RelativePos.pack(i % 40, 0, i / 40);
        }
        EditorModel m = new EditorModel(7L, offsets, values, new int[] {ChromaColor.WHITE.packed()});
        m.begin();
        List<Integer> all = new java.util.ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            all.add(m.offset(i));
        }
        m.paint(all, RED);
        List<ChromaOpBatch> batches = m.buildBatches();
        assertEquals(3, batches.size());
        assertEquals(ChromaOp.Kind.SET_PALETTE_ENTRY, batches.get(0).ops().get(0).kind());
        assertTrue(batches.stream().allMatch(b -> b.size() <= ChromaOpBatch.MAX_OPS));
    }

    @Test
    void revertDropsEdits() {
        EditorModel m = row(4);
        m.begin();
        m.paint(List.of(m.offset(0)), RED);
        m.revert();
        assertFalse(m.isDirty());
    }
}
