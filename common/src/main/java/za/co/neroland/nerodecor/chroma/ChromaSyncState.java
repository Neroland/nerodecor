package za.co.neroland.nerodecor.chroma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The server's record of what one hub's watchers were last sent (the shadow), and the delta coalescer: any number
 * of edits between two flushes collapse into one delta of net changes, and a hub flushes at most once per
 * {@link #MIN_INTERVAL_NANOS} (two deltas a second). The first flush, and any flush after the hub moved, is a full
 * snapshot instead. New watchers are sent the shadow, so every watcher of a hub sees the same state.
 */
public final class ChromaSyncState {

    /** At most two deltas per second per hub. */
    public static final long MIN_INTERVAL_NANOS = 500_000_000L;

    /** What a flush produced. */
    public enum Kind {
        NONE,
        SNAPSHOT,
        DELTA
    }

    public record Flush(Kind kind, ChromaDelta delta) {
    }

    private final long network;
    private boolean sent;
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private final Map<Integer, Integer> shadow = new HashMap<>();
    private int[] palette = new int[0];
    private boolean dirty = true;
    private boolean flushed;
    private long lastFlush;

    public ChromaSyncState(long network) {
        this.network = network;
    }

    public long network() {
        return network;
    }

    /** Something about the hub changed; the next due flush will look. */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Whether a snapshot has been taken (so the shadow is what the watchers hold). */
    public boolean hasSent() {
        return sent;
    }

    /** Dirty, and the rate gate is open. */
    public boolean due(long nowNanos) {
        return dirty && (!flushed || nowNanos - lastFlush >= MIN_INTERVAL_NANOS);
    }

    /**
     * Compares the hub's current layout with the shadow, adopts it, and says what to send.
     *
     * @param current packed hub-relative offset to palette index
     */
    public Flush flush(long nowNanos, int ax, int ay, int az, Map<Integer, Integer> current, int[] currentPalette) {
        dirty = false;
        flushed = true;
        lastFlush = nowNanos;
        boolean moved = ax != anchorX || ay != anchorY || az != anchorZ;
        if (!sent || moved || currentPalette.length < palette.length) {
            adopt(ax, ay, az, current, currentPalette);
            sent = true;
            return new Flush(Kind.SNAPSHOT, null);
        }
        List<Integer> pIdx = new ArrayList<>();
        List<Integer> pCol = new ArrayList<>();
        for (int i = 0; i < currentPalette.length; i++) {
            if (i >= palette.length || palette[i] != currentPalette[i]) {
                pIdx.add(i);
                pCol.add(currentPalette[i]);
            }
        }
        List<Integer> offsets = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : current.entrySet()) {
            Integer before = shadow.get(e.getKey());
            if (!e.getValue().equals(before)) {
                offsets.add(e.getKey());
                indices.add(e.getValue());
            }
        }
        for (Integer rel : shadow.keySet()) {
            if (!current.containsKey(rel)) {
                offsets.add(rel);
                indices.add(ChromaDelta.UNLINKED);
            }
        }
        adopt(ax, ay, az, current, currentPalette);
        if (pIdx.isEmpty() && offsets.isEmpty()) {
            return new Flush(Kind.NONE, null);
        }
        return new Flush(Kind.DELTA, new ChromaDelta(network, false, ints(pIdx), ints(pCol), ints(offsets),
                ints(indices)));
    }

    /** Forget everything sent; the next flush is a snapshot. */
    public void reset() {
        sent = false;
        dirty = true;
    }

    private void adopt(int ax, int ay, int az, Map<Integer, Integer> current, int[] currentPalette) {
        anchorX = ax;
        anchorY = ay;
        anchorZ = az;
        shadow.clear();
        shadow.putAll(current);
        palette = currentPalette.clone();
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    public int[] shadowPalette() {
        return Arrays.copyOf(palette, palette.length);
    }

    /** The shadow as parallel arrays {offsets, indices}. */
    public int[][] shadowBlocks() {
        int[] offsets = new int[shadow.size()];
        int[] indices = new int[shadow.size()];
        int i = 0;
        for (Map.Entry<Integer, Integer> e : shadow.entrySet()) {
            offsets[i] = e.getKey();
            indices[i++] = e.getValue();
        }
        return new int[][] {offsets, indices};
    }

    private static int[] ints(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
