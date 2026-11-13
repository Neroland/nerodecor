package za.co.neroland.nerodecor.chroma.server;

/**
 * A classic token bucket on a caller-supplied nanosecond clock: starts full, refills continuously at
 * {@code capacity / periodNanos}, never above {@code capacity}. Not thread-safe; {@link ChromaRateLimiter} guards it.
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerNano;
    private double tokens;
    private long last;

    public TokenBucket(long capacity, long periodNanos, long now) {
        if (capacity <= 0 || periodNanos <= 0) {
            throw new IllegalArgumentException("capacity and period must be positive");
        }
        this.capacity = capacity;
        this.refillPerNano = (double) capacity / (double) periodNanos;
        this.tokens = capacity;
        this.last = now;
    }

    private void refill(long now) {
        long elapsed = now - last;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            last = now;
        }
    }

    /** Whether {@code n} tokens are available now (refills first, takes nothing). */
    public boolean canTake(long n, long now) {
        refill(now);
        return n <= tokens;
    }

    /** Takes {@code n} tokens if available. */
    public boolean tryTake(long n, long now) {
        if (!canTake(n, now)) {
            return false;
        }
        tokens -= n;
        return true;
    }

    /** Whether the bucket has refilled completely (an idle player). */
    public boolean isFull(long now) {
        refill(now);
        return tokens >= capacity;
    }

    public double available(long now) {
        refill(now);
        return tokens;
    }
}
