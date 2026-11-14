package za.co.neroland.nerodecor.chroma.client;

/**
 * Client-only music reactivity for one hub, pure Java: note-block hits near the hub kick a brightness pulse that decays
 * over a few ticks, and a playing jukebox adds a slow swell. The result is a gain on the animated colours only (never
 * on light, never sent anywhere). Photosensitivity: pulses are at most one per {@link #MIN_PULSE_INTERVAL} ticks
 * (2.5 Hz, under the 3 Hz flash limit), at most {@link #PULSE_GAIN} brighter, and the swell is 0.5 Hz.
 */
public final class ChromaMusicPulse {

    public static final int MIN_PULSE_INTERVAL = 8;
    public static final double PULSE_GAIN = 0.25;
    public static final double DECAY_PER_TICK = 0.75;
    public static final double SWELL_GAIN = 0.08;
    public static final int SWELL_PERIOD = 40;

    private long lastPulse = Long.MIN_VALUE / 2;

    /**
     * A note was heard at {@code tick}.
     *
     * @return whether it started a pulse (rate-limited)
     */
    public boolean onNote(long tick) {
        if (tick - lastPulse < MIN_PULSE_INTERVAL) {
            return false;
        }
        lastPulse = tick;
        return true;
    }

    /** Colour gain at {@code tick}: 1 when silent. {@code reducedMotion} halves the effect. */
    public double gain(long tick, boolean jukebox, boolean reducedMotion) {
        double g = 0.0;
        long since = tick - lastPulse;
        if (since >= 0 && since < 32) {
            g += PULSE_GAIN * StrictMath.pow(DECAY_PER_TICK, since);
        }
        if (jukebox) {
            g += SWELL_GAIN * (0.5 - 0.5 * StrictMath.cos(2.0 * StrictMath.PI * Math.floorMod(tick, SWELL_PERIOD)
                    / SWELL_PERIOD));
        }
        return 1.0 + (reducedMotion ? g * 0.5 : g);
    }

    public void reset() {
        lastPulse = Long.MIN_VALUE / 2;
    }
}
