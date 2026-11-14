package za.co.neroland.nerodecor.chroma.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player Chroma edit limits: {@value #DEFAULT_EDITS_PER_SECOND} edit requests per second and
 * {@value #DEFAULT_BLOCK_OPS_PER_MINUTE} block operations per minute by default, as two token buckets. A request must
 * fit both or it takes from neither. Held in memory only (never saved); idle players are swept out and erasure
 * calls {@link #forget}.
 */
public final class ChromaRateLimiter {

    public static final int DEFAULT_EDITS_PER_SECOND = 10;
    public static final int DEFAULT_BLOCK_OPS_PER_MINUTE = 20_000;

    private static final long SECOND = 1_000_000_000L;
    private static final long MINUTE = 60L * SECOND;
    private static final int SWEEP_EVERY = 256;

    private record Buckets(TokenBucket edits, TokenBucket ops) {
    }

    private final int editsPerSecond;
    private final int opsPerMinute;
    private final Map<UUID, Buckets> players = new HashMap<>();
    private int calls;

    public ChromaRateLimiter(int editsPerSecond, int opsPerMinute) {
        this.editsPerSecond = Math.max(1, editsPerSecond);
        this.opsPerMinute = Math.max(1, opsPerMinute);
    }

    public int editsPerSecond() {
        return editsPerSecond;
    }

    public int opsPerMinute() {
        return opsPerMinute;
    }

    /**
     * One edit request carrying {@code blockOps} block operations.
     *
     * @return {@link ChromaVerdict#OK} (both buckets debited) or {@link ChromaVerdict#RATE_LIMITED} (neither)
     */
    public synchronized ChromaVerdict tryAcquire(UUID player, int blockOps, long nowNanos) {
        if (player == null || blockOps < 0) {
            return ChromaVerdict.RATE_LIMITED;
        }
        if (++calls % SWEEP_EVERY == 0) {
            sweep(nowNanos);
        }
        Buckets b = players.computeIfAbsent(player, ignored -> new Buckets(
                new TokenBucket(editsPerSecond, SECOND, nowNanos), new TokenBucket(opsPerMinute, MINUTE, nowNanos)));
        if (!b.edits().canTake(1, nowNanos) || !b.ops().canTake(blockOps, nowNanos)) {
            return ChromaVerdict.RATE_LIMITED;
        }
        b.edits().tryTake(1, nowNanos);
        b.ops().tryTake(blockOps, nowNanos);
        return ChromaVerdict.OK;
    }

    /** Block operations {@code player} could spend right now (for capping a selection before it runs). */
    public synchronized int opsAvailable(UUID player, long nowNanos) {
        Buckets b = players.get(player);
        return b == null ? opsPerMinute : (int) Math.floor(b.ops().available(nowNanos));
    }

    /** Drops a player's buckets (logout or erasure). */
    public synchronized void forget(UUID player) {
        players.remove(player);
    }

    /** Drops the buckets of every player not in {@code online} (logged-out players keep no rate state). */
    public synchronized void retainOnly(java.util.Set<UUID> online) {
        players.keySet().retainAll(online);
    }

    /** Players currently tracked (tests). */
    public synchronized int tracked() {
        return players.size();
    }

    /** Removes players whose buckets have refilled completely: they hold no state worth keeping. */
    public synchronized void sweep(long nowNanos) {
        Iterator<Buckets> it = players.values().iterator();
        while (it.hasNext()) {
            Buckets b = it.next();
            if (b.edits().isFull(nowNanos) && b.ops().isFull(nowNanos)) {
                it.remove();
            }
        }
    }
}
