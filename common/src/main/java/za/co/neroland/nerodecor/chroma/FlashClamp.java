package za.co.neroland.nerodecor.chroma;

/**
 * Photosensitivity clamp. Samples a program over time at {@link #SAMPLE_TPS} across a representative
 * grid of hub-relative positions and rejects any <em>large-area</em> luminance or saturation
 * <em>change</em> that repeats faster than {@link #MAX_FLASH_HZ}.
 *
 * <p>Modelled on the spirit of WCAG 2.x general-flash and red-flash thresholds: a flash is a pair of
 * opposing changes, and more than three flashes in any one second fails. Here:
 * <ul>
 *   <li>A <b>change</b> at a position is a reversal of direction after the running extreme moved by at
 *       least {@link #LUMINANCE_DELTA} of relative luminance (brightness-scaled) or
 *       {@link #SATURATION_DELTA} of HSV saturation. Slow monotonic fades never count; only swings.</li>
 *   <li>A position <b>flashes too fast</b> at tick {@code t} when it made more than
 *       {@link #MAX_TRANSITIONS_PER_SECOND} changes (three flashes, each two opposing changes) within
 *       the {@link #SAMPLE_TPS} ticks ending at {@code t}.</li>
 *   <li>Failing means more than {@link #LARGE_AREA_FRACTION} of the sampled positions flash too fast
 *       at the same tick (a large area). A small fraction twinkling quickly, or a wave whose blocks each
 *       change slowly but out of step, passes.</li>
 * </ul>
 */
public final class FlashClamp {

    /** Samples per second: one per game tick. */
    public static final int SAMPLE_TPS = 20;
    /** How long effects are sampled. Effects are periodic, so a few seconds exposes their rate. */
    public static final int EFFECT_SAMPLE_SECONDS = 6;
    /** Cap on how long a scene's interleaved (all zones together) pass samples, in ticks. */
    public static final int COMBINED_SAMPLE_TICKS_CAP = 30 * SAMPLE_TPS;
    /** WCAG: a flash is a pair of opposing changes of 10% or more relative luminance. */
    public static final double LUMINANCE_DELTA = 0.1;
    /** Saturation swing that counts as a change (stand-in for WCAG's saturated-red transition). */
    public static final double SATURATION_DELTA = 0.2;
    /** More than this fraction of sampled positions flashing too fast at once is a large-area flash. */
    public static final double LARGE_AREA_FRACTION = 0.25;
    /** WCAG: no more than three flashes in any one second. */
    public static final int MAX_FLASH_HZ = 3;
    /** Each flash is two opposing changes. */
    public static final int MAX_TRANSITIONS_PER_SECOND = 2 * MAX_FLASH_HZ;

    /** Sample grid: x and y in [-6, 5], z in {-4, 0, 4} (432 positions). */
    private static final int GRID_MIN = -6;
    private static final int GRID_SIZE = 12;
    private static final int[] GRID_Z = {-4, 0, 4};
    public static final int SAMPLE_POSITIONS = GRID_SIZE * GRID_SIZE * GRID_Z.length;

    private FlashClamp() {
    }

    /** Outcome. {@code zone} is the failing zone, or -1 for a single program or the combined pass. */
    public record Verdict(boolean passed, int zone, long tick, int changesInWindow) {
        static final Verdict PASS = new Verdict(true, -1, -1, 0);
    }

    @FunctionalInterface
    private interface Sampler {
        ChromaColor at(int positionIndex, int x, int y, int z, long tick);
    }

    /** Checks one program. */
    public static Verdict check(ZoneProgram program, long seed) {
        long ticks = sampleTicks(program);
        int positions = program.positionIndependent() ? 1 : SAMPLE_POSITIONS;
        return run(positions, ticks, (i, x, y, z, t) -> program.colourAt(t, x, y, z, seed), -1);
    }

    /**
     * Checks every zone on its own (a zone may fill the whole view), then all zones interleaved across
     * the grid so zones flashing in step are caught too.
     */
    public static Verdict check(Scene scene, long seed) {
        for (ZoneAssignment z : scene.zones()) {
            Verdict v = check(z.program(), seed);
            if (!v.passed()) {
                return new Verdict(false, z.zone(), v.tick(), v.changesInWindow());
            }
        }
        int n = scene.zones().size();
        if (n < 2) {
            return Verdict.PASS;
        }
        ZoneProgram[] programs = new ZoneProgram[n];
        long ticks = 0;
        for (int i = 0; i < n; i++) {
            programs[i] = scene.zones().get(i).program();
            ticks = StrictMath.max(ticks, sampleTicks(programs[i]));
        }
        ticks = StrictMath.min(ticks, COMBINED_SAMPLE_TICKS_CAP);
        return run(SAMPLE_POSITIONS, ticks, (i, x, y, z, t) -> programs[i % n].colourAt(t, x, y, z, seed), -1);
    }

    private static long sampleTicks(ZoneProgram program) {
        if (program instanceof Timeline tl) {
            // Cover the whole timeline, plus one second past the loop point for the wrap segment.
            return tl.length() + (tl.loops() ? SAMPLE_TPS : 1);
        }
        return (long) EFFECT_SAMPLE_SECONDS * SAMPLE_TPS;
    }

    private static Verdict run(int positions, long ticks, Sampler sampler, int zone) {
        int[] xs = new int[positions];
        int[] ys = new int[positions];
        int[] zs = new int[positions];
        // A single position (position-independent program) stays at the hub origin.
        for (int i = 0; positions > 1 && i < positions; i++) {
            xs[i] = GRID_MIN + i % GRID_SIZE;
            ys[i] = GRID_MIN + (i / GRID_SIZE) % GRID_SIZE;
            zs[i] = GRID_Z[i / (GRID_SIZE * GRID_SIZE)];
        }
        // Luminance and saturation flashes are counted separately; either one too fast counts.
        Tracker lum = new Tracker(positions, LUMINANCE_DELTA);
        Tracker sat = new Tracker(positions, SATURATION_DELTA);
        double limit = LARGE_AREA_FRACTION * positions;
        for (long t = 0; t < ticks; t++) {
            int tooFast = 0;
            for (int i = 0; i < positions; i++) {
                ChromaColor c = sampler.at(i, xs[i], ys[i], zs[i], t);
                boolean a = lum.update(i, c.emittedLuminance(), t);
                boolean b = sat.update(i, c.saturation(), t);
                if (a || b) {
                    tooFast++;
                }
            }
            if (tooFast > limit) {
                return new Verdict(false, zone, t, MAX_TRANSITIONS_PER_SECOND + 1);
            }
        }
        return Verdict.PASS;
    }

    /**
     * Per-position reversal detector with hysteresis, plus a ring of each position's last
     * {@code MAX_TRANSITIONS_PER_SECOND + 1} change ticks.
     */
    private static final class Tracker {
        private static final int WINDOW = MAX_TRANSITIONS_PER_SECOND + 1;
        private final double[] extreme;
        private final byte[] direction;
        private final long[] ring;
        private final int[] head;
        private final int[] count;
        private final double delta;

        Tracker(int positions, double delta) {
            this.extreme = new double[positions];
            this.direction = new byte[positions];
            this.ring = new long[positions * WINDOW];
            this.head = new int[positions];
            this.count = new int[positions];
            this.delta = delta;
        }

        /** Feeds one sample; returns whether position {@code i} is now changing too fast. */
        boolean update(int i, double v, long tick) {
            if (tick == 0) {
                extreme[i] = v;
            } else if (changed(i, v)) {
                ring[i * WINDOW + head[i]] = tick;
                head[i] = (head[i] + 1) % WINDOW;
                if (count[i] < WINDOW) {
                    count[i]++;
                }
            }
            // ring[i * WINDOW + head[i]] is the oldest of the last WINDOW changes.
            return count[i] == WINDOW && tick - ring[i * WINDOW + head[i]] < SAMPLE_TPS;
        }

        private boolean changed(int i, double v) {
            double e = extreme[i];
            byte d = direction[i];
            if (d > 0 && v > e || d < 0 && v < e) {
                extreme[i] = v;
                return false;
            }
            if (d >= 0 && e - v >= delta) {
                direction[i] = -1;
                extreme[i] = v;
                return true;
            }
            if (d <= 0 && v - e >= delta) {
                direction[i] = 1;
                extreme[i] = v;
                return true;
            }
            return false;
        }
    }
}
