package za.co.neroland.nerodecor.chroma.client;

import java.util.Arrays;

/**
 * A measurement window over the Chroma animation layer, pure Java: per-frame draw cost (all hubs together), per-tick
 * colour-update cost, and whether the {@link ChromaLayerBudget} planner ever degraded a hub (half rate or skipped).
 * Off unless a test opens one ({@link ChromaAnimationClient#startProfile()}); records timings and counts only.
 */
public final class ChromaLayerProfile {

    private long[] frames = new long[1024];
    private int frameCount;
    private long[] updates = new long[256];
    private int updateCount;
    private boolean degraded;
    private int maxBlocks;
    private int maxQuads;

    void frame(long nanos) {
        if (frameCount == frames.length) {
            frames = Arrays.copyOf(frames, frames.length * 2);
        }
        frames[frameCount++] = Math.max(0L, nanos);
    }

    void update(long nanos) {
        if (updateCount == updates.length) {
            updates = Arrays.copyOf(updates, updates.length * 2);
        }
        updates[updateCount++] = Math.max(0L, nanos);
    }

    void mode(ChromaLayerBudget.Mode mode) {
        if (mode != ChromaLayerBudget.Mode.FULL) {
            degraded = true;
        }
    }

    void size(int blocks, int quads) {
        maxBlocks = Math.max(maxBlocks, blocks);
        maxQuads = Math.max(maxQuads, quads);
    }

    public int frames() {
        return frameCount;
    }

    public int updates() {
        return updateCount;
    }

    public boolean degraded() {
        return degraded;
    }

    public int maxBlocks() {
        return maxBlocks;
    }

    public int maxQuads() {
        return maxQuads;
    }

    public double frameAvgMs() {
        return avgMs(frames, frameCount);
    }

    public double frameP99Ms() {
        return percentileMs(frames, frameCount, 0.99);
    }

    public double updateAvgMs() {
        return avgMs(updates, updateCount);
    }

    public double updateP99Ms() {
        return percentileMs(updates, updateCount, 0.99);
    }

    static double avgMs(long[] values, int n) {
        if (n == 0) {
            return 0.0;
        }
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            sum += values[i];
        }
        return sum / (double) n / 1_000_000.0;
    }

    /** Nearest-rank percentile in milliseconds. */
    static double percentileMs(long[] values, int n, double p) {
        if (n == 0) {
            return 0.0;
        }
        long[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(p * n) - 1;
        return sorted[Math.max(0, Math.min(n - 1, rank))] / 1_000_000.0;
    }
}
