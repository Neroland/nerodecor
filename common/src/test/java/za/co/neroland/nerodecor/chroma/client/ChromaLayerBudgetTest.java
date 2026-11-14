package za.co.neroland.nerodecor.chroma.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChromaLayerBudgetTest {

    private static final ChromaLayerBudget.Mode FULL = ChromaLayerBudget.Mode.FULL;
    private static final ChromaLayerBudget.Mode HALF = ChromaLayerBudget.Mode.HALF_RATE;
    private static final ChromaLayerBudget.Mode SKIP = ChromaLayerBudget.Mode.SKIP;

    @Test
    void cheapHubsAreAllDrawnUpTo32NearestFirst() {
        int n = 40;
        double[] dist = new double[n];
        for (int i = 0; i < n; i++) {
            dist[i] = (n - i) * 10.0; // hub 39 is nearest
        }
        ChromaLayerBudget.Mode[] modes = ChromaLayerBudget.plan(dist, new long[n], new long[n],
                ChromaLayerBudget.FRAME_BUDGET_NANOS);
        int drawn = 0;
        for (int i = 0; i < n; i++) {
            if (modes[i] != SKIP) {
                drawn++;
            }
        }
        assertEquals(ChromaLayerBudget.MAX_HUBS, drawn);
        for (int i = 0; i < n - ChromaLayerBudget.MAX_HUBS; i++) {
            assertEquals(SKIP, modes[i], "far hub " + i);
        }
    }

    @Test
    void overBudgetSkipsFarHubsAndHalvesUpdates() {
        double[] dist = {1.0, 4.0, 9.0, 16.0};
        long[] draw = {200_000L, 150_000L, 100_000L, 100_000L};
        long[] update = {50_000L, 120_000L, 0L, 0L};
        ChromaLayerBudget.Mode[] m = ChromaLayerBudget.plan(dist, draw, update, 500_000L);
        // 200+50 = 250; +150 = 400, +120 > 500 -> half (460); +100 > 500 -> skip; +100 -> skip
        assertEquals(FULL, m[0]);
        assertEquals(HALF, m[1]);
        assertEquals(SKIP, m[2]);
        assertEquals(SKIP, m[3]);
    }

    @Test
    void nearestHubIsAlwaysDrawn() {
        ChromaLayerBudget.Mode[] m = ChromaLayerBudget.plan(new double[] {5.0, 1.0}, new long[] {1L, 9_000_000L},
                new long[] {0L, 9_000_000L}, 500_000L);
        assertEquals(FULL, m[1]);
        assertEquals(SKIP, m[0]);
    }

    @Test
    void smoothingAndHalfRate() {
        assertEquals(400L, ChromaLayerBudget.smooth(0L, 400L));
        assertEquals(500L, ChromaLayerBudget.smooth(400L, 800L));
        int updates = 0;
        for (long t = 0; t < 10; t++) {
            if (ChromaLayerBudget.updatesOn(HALF, t, 3L)) {
                updates++;
            }
        }
        assertEquals(5, updates);
        assertTrue(ChromaLayerBudget.updatesOn(FULL, 1L, 0L));
        assertFalse(ChromaLayerBudget.updatesOn(SKIP, 0L, 0L));
    }

    @Test
    void musicPulseIsRateLimitedAndDecays() {
        ChromaMusicPulse pulse = new ChromaMusicPulse();
        assertEquals(1.0, pulse.gain(0L, false, false));
        assertTrue(pulse.onNote(100L));
        assertFalse(pulse.onNote(104L), "under 3 Hz");
        assertTrue(pulse.onNote(108L));
        double peak = pulse.gain(108L, false, false);
        assertEquals(1.0 + ChromaMusicPulse.PULSE_GAIN, peak, 1e-9);
        assertTrue(pulse.gain(112L, false, false) < peak);
        assertTrue(pulse.gain(108L, false, true) < peak, "reduced motion halves it");
    }
}
