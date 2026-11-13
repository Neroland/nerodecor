package za.co.neroland.nerodecor.chroma.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChromaRateLimiterTest {

    private static final long SECOND = 1_000_000_000L;
    private static final UUID P = new UUID(1, 1);
    private static final UUID Q = new UUID(2, 2);

    @Test
    void tenEditsPerSecond() {
        ChromaRateLimiter limiter = new ChromaRateLimiter(10, 20_000);
        long t = 5 * SECOND;
        for (int i = 0; i < 10; i++) {
            assertEquals(ChromaVerdict.OK, limiter.tryAcquire(P, 1, t));
        }
        assertEquals(ChromaVerdict.RATE_LIMITED, limiter.tryAcquire(P, 1, t));
        assertEquals(ChromaVerdict.OK, limiter.tryAcquire(Q, 1, t), "buckets are per player");
        assertEquals(ChromaVerdict.OK, limiter.tryAcquire(P, 1, t + SECOND / 10), "one token back after 100 ms");
        assertEquals(ChromaVerdict.RATE_LIMITED, limiter.tryAcquire(P, 1, t + SECOND / 10));
    }

    @Test
    void twentyThousandOpsPerMinute() {
        ChromaRateLimiter limiter = new ChromaRateLimiter(10, 20_000);
        long t = 0;
        for (int i = 0; i < 5; i++) {
            assertEquals(ChromaVerdict.OK, limiter.tryAcquire(P, 4_000, t));
            t += SECOND; // stay under the packet limit
        }
        assertEquals(ChromaVerdict.RATE_LIMITED, limiter.tryAcquire(P, 2_000, t),
                "only ~1,667 ops refilled after 5 s");
        assertEquals(ChromaVerdict.OK, limiter.tryAcquire(P, 1_000, t), "a refused request takes nothing");
        assertEquals(ChromaVerdict.RATE_LIMITED, limiter.tryAcquire(P, 20_001, t + 120 * SECOND),
                "never more than the bucket holds");
        assertEquals(ChromaVerdict.OK, limiter.tryAcquire(P, 20_000, t + 120 * SECOND));
    }

    @Test
    void aRefusedRequestDebitsNeitherBucket() {
        ChromaRateLimiter limiter = new ChromaRateLimiter(10, 100);
        assertEquals(ChromaVerdict.RATE_LIMITED, limiter.tryAcquire(P, 101, 0));
        for (int i = 0; i < 10; i++) {
            assertEquals(ChromaVerdict.OK, limiter.tryAcquire(P, 0, 0));
        }
    }

    @Test
    void idlePlayersAreSweptAndForgotten() {
        ChromaRateLimiter limiter = new ChromaRateLimiter(10, 100);
        limiter.tryAcquire(P, 50, 0);
        limiter.tryAcquire(Q, 50, 0);
        assertEquals(2, limiter.tracked());
        limiter.forget(Q);
        assertEquals(1, limiter.tracked());
        limiter.sweep(120 * SECOND);
        assertEquals(0, limiter.tracked());
    }

    @Test
    void tokenBucketRefillsContinuously() {
        TokenBucket bucket = new TokenBucket(60, 60 * SECOND, 0);
        assertEquals(true, bucket.tryTake(60, 0));
        assertEquals(false, bucket.tryTake(1, SECOND / 2));
        assertEquals(true, bucket.tryTake(1, SECOND));
        assertEquals(60.0, bucket.available(Long.MAX_VALUE / 2), 1e-9);
    }
}
