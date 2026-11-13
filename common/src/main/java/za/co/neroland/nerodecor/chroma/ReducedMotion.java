package za.co.neroland.nerodecor.chroma;

/** Calmer variants of effects, for the client's reduced-motion option. */
public final class ReducedMotion {

    /** Cycles run this many times slower. */
    public static final int PERIOD_FACTOR = 4;
    /** Calmer effects never cycle faster than this (2 s). */
    public static final int MIN_PERIOD_TICKS = 40;
    /** Amplitude and sparkle density are scaled by this. */
    public static final double INTENSITY_FACTOR = 0.5;

    private ReducedMotion() {
    }

    /** Lower frequency and amplitude (and sparkle density); colours and shape are kept. */
    public static EffectParams calmer(EffectParams params) {
        long period = StrictMath.max((long) params.periodTicks() * PERIOD_FACTOR, MIN_PERIOD_TICKS);
        int newPeriod = (int) StrictMath.min(period, EffectParams.MAX_PERIOD);
        return params.toBuilder()
                .periodTicks(newPeriod)
                .amplitude((int) StrictMath.round(params.amplitude() * INTENSITY_FACTOR))
                .density((int) StrictMath.round(params.density() * INTENSITY_FACTOR))
                .build();
    }

    /** Calmer program: effects get {@link #calmer(EffectParams)}; timelines are returned unchanged. */
    public static ZoneProgram calmer(ZoneProgram program) {
        if (program instanceof EffectProgram effect) {
            return new EffectProgram(effect.type(), calmer(effect.params()));
        }
        return program;
    }
}
