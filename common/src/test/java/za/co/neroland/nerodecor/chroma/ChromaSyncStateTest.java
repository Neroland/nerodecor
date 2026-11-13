package za.co.neroland.nerodecor.chroma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChromaSyncStateTest {

    private static final long MS = 1_000_000L;
    private static final int A = RelativePos.pack(1, 0, 0);
    private static final int B = RelativePos.pack(2, 0, 0);
    private static final int C = RelativePos.pack(3, 0, 0);

    @Test
    void firstFlushIsSnapshotThenDeltasAreCoalescedAndRateLimited() {
        ChromaSyncState state = new ChromaSyncState(1L);
        Map<Integer, Integer> layout = new HashMap<>(Map.of(A, 0, B, 0));
        int[] palette = {ChromaColor.WHITE.packed()};
        assertTrue(state.due(0));
        assertEquals(ChromaSyncState.Kind.SNAPSHOT, state.flush(0, 0, 0, 0, layout, palette).kind());

        // Three edits inside one window: not due until 500 ms have passed, then one delta with the net result.
        layout.put(A, 1);
        state.markDirty();
        layout.put(C, 0);
        state.markDirty();
        layout.put(C, 1);
        layout.remove(B);
        state.markDirty();
        assertFalse(state.due(100 * MS));
        assertFalse(state.due(499 * MS));
        assertTrue(state.due(500 * MS));
        int[] palette2 = {ChromaColor.WHITE.packed(), ChromaColor.of(9, 9, 9).packed()};
        ChromaSyncState.Flush flush = state.flush(500 * MS, 0, 0, 0, layout, palette2);
        assertEquals(ChromaSyncState.Kind.DELTA, flush.kind());
        ChromaDelta d = flush.delta();
        assertArrayEquals(new int[] {1}, d.paletteIndices());
        Map<Integer, Integer> changes = new HashMap<>();
        for (int k = 0; k < d.offsets().length; k++) {
            changes.put(d.offsets()[k], d.indices()[k]);
        }
        assertEquals(Map.of(A, 1, B, ChromaDelta.UNLINKED, C, 1), changes);
        assertFalse(state.isDirty());
    }

    @Test
    void revertedChangeProducesNothingAndMoveForcesSnapshot() {
        ChromaSyncState state = new ChromaSyncState(2L);
        Map<Integer, Integer> layout = new HashMap<>(Map.of(A, 0));
        state.flush(0, 5, 5, 5, layout, new int[0]);
        layout.put(A, 3);
        layout.put(A, 0);
        state.markDirty();
        assertEquals(ChromaSyncState.Kind.NONE, state.flush(600 * MS, 5, 5, 5, layout, new int[0]).kind());
        state.markDirty();
        assertEquals(ChromaSyncState.Kind.SNAPSHOT, state.flush(1200 * MS, 6, 5, 5, layout, new int[0]).kind());
        assertEquals(6, state.anchorX());
    }

    @Test
    void remeshBudgetCapsSectionsAndIntervals() {
        ChromaRemeshBudget budget = new ChromaRemeshBudget();
        for (long s = 0; s < 100; s++) {
            budget.mark(s);
        }
        budget.mark(5);
        assertEquals(100, budget.pendingCount());
        assertEquals(ChromaRemeshBudget.MAX_SECTIONS_PER_CHANGE, budget.poll(0).size());
        assertEquals(36, budget.poll(MS).size());
        // A section re-meshed at t=0 waits for its 500 ms window, however often it is marked.
        budget.mark(5);
        budget.mark(5);
        assertTrue(budget.poll(100 * MS).isEmpty());
        assertTrue(budget.poll(499 * MS).isEmpty());
        assertEquals(java.util.List.of(5L), budget.poll(500 * MS));
    }

    @Test
    void lightScheduleKeepsOneSecondPerBlockAndLatestLevel() {
        ChromaLightSchedule light = new ChromaLightSchedule();
        light.request(10L, 4);
        assertEquals(1, light.drain(0, 10).size());
        light.request(10L, 7);
        light.request(10L, 9);
        assertTrue(light.drain(999 * MS, 10).isEmpty());
        ChromaLightSchedule.Update u = light.drain(1000 * MS, 10).get(0);
        assertEquals(9, u.level());
    }
}
