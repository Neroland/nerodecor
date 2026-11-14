package za.co.neroland.nerodecor.chroma.client;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Budget planner for the client animation layer, pure Java. Each client tick it ranks the hubs that play a scene by
 * distance and decides, from measured costs, which are drawn and how often their colours are re-evaluated:
 *
 * <ul>
 *   <li>at most {@link #MAX_HUBS} hubs are drawn, nearest first; at most {@link #MAX_BLOCKS_PER_HUB} blocks per hub;</li>
 *   <li>the frame budget is {@link #FRAME_BUDGET_NANOS} (0.5 ms) for all hubs together. Walking nearest first, a hub
 *       whose draw would push the running total past the budget is {@link Mode#SKIP}ped (it shows its static colour);
 *       the nearest hub is always drawn. A hub that fits its draw but not also its colour update is
 *       {@link Mode#HALF_RATE}: it re-evaluates colours every other tick.</li>
 * </ul>
 */
public final class ChromaLayerBudget {

    public static final int MAX_BLOCKS_PER_HUB = 4096;
    public static final int MAX_HUBS = 32;
    public static final long FRAME_BUDGET_NANOS = 500_000L;

    public enum Mode {
        FULL,
        HALF_RATE,
        SKIP
    }

    private ChromaLayerBudget() {
    }

    /**
     * @param distSq      squared camera distance per hub
     * @param drawNanos   measured (smoothed) per-frame draw cost per hub; 0 when unknown
     * @param updateNanos measured (smoothed) colour-update cost per hub; 0 when unknown
     * @param budget      frame budget in nanoseconds
     * @return one mode per hub, aligned with the inputs
     */
    public static Mode[] plan(double[] distSq, long[] drawNanos, long[] updateNanos, long budget) {
        int n = distSq.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> distSq[i]));
        Mode[] out = new Mode[n];
        long spent = 0L;
        for (int rank = 0; rank < n; rank++) {
            int i = order[rank];
            if (rank >= MAX_HUBS) {
                out[i] = Mode.SKIP;
                continue;
            }
            long draw = Math.max(0L, drawNanos[i]);
            if (rank > 0 && spent + draw > budget) {
                out[i] = Mode.SKIP;
                continue;
            }
            spent += draw;
            long update = Math.max(0L, updateNanos[i]);
            if (rank > 0 && spent + update > budget) {
                out[i] = Mode.HALF_RATE;
                spent += update / 2;
            } else {
                out[i] = Mode.FULL;
                spent += update;
            }
        }
        return out;
    }

    /** Exponential moving average with weight 1/4 for the new sample (the first sample is taken as is). */
    public static long smooth(long previous, long sample) {
        if (previous <= 0L) {
            return Math.max(0L, sample);
        }
        return previous + (sample - previous) / 4;
    }

    /** Whether a {@link Mode#HALF_RATE} hub re-evaluates on this tick. */
    public static boolean updatesOn(Mode mode, long tick, long hubSeed) {
        return switch (mode) {
            case FULL -> true;
            case HALF_RATE -> ((tick + (hubSeed & 1L)) & 1L) == 0L;
            case SKIP -> false;
        };
    }
}
