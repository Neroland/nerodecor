package za.co.neroland.nerodecor.link;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import za.co.neroland.nerodecor.chroma.server.TokenBucket;

/**
 * The link actions' own limit: one Chroma action per second per requester, on top of (not instead of) the shared
 * Chroma edit limiter the GUI is charged against. A remote control is a slower, coarser thing than a player at the
 * hub, and one a second is plenty to tap through scenes.
 *
 * <p>Keyed by the requester's UUID in memory only; a bucket that has refilled is swept, and nothing is persisted or
 * logged. Thread-safe.
 */
final class LinkRateLimiter {

    static final long PERIOD_NANOS = 1_000_000_000L;
    private static final int SWEEP_EVERY = 64;

    private final LongSupplier clock;
    private final Map<UUID, TokenBucket> buckets = new HashMap<>();
    private int calls;

    LinkRateLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    /** Takes the requester's token if one is available. */
    synchronized boolean tryAcquire(UUID requester) {
        if (requester == null) {
            return false;
        }
        long now = clock.getAsLong();
        if (++calls % SWEEP_EVERY == 0) {
            Iterator<TokenBucket> it = buckets.values().iterator();
            while (it.hasNext()) {
                if (it.next().isFull(now)) {
                    it.remove();
                }
            }
        }
        return buckets.computeIfAbsent(requester, ignored -> new TokenBucket(1, PERIOD_NANOS, now)).tryTake(1, now);
    }

    synchronized void clear() {
        buckets.clear();
    }

    synchronized int tracked() {
        return buckets.size();
    }
}
