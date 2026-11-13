package za.co.neroland.nerodecor.chroma;

/** Keyframe easing curves. Ids are stable wire values. */
public enum Easing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    /** Holds the previous key until the next key's tick, then jumps. */
    STEP;

    private static final Easing[] VALUES = values();

    public int id() {
        return ordinal();
    }

    /** {@code null} for an unknown id. */
    public static Easing byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /** Maps segment progress {@code t} (clamped to 0-1) to eased progress; endpoints are exact. */
    public double apply(double t) {
        double u = ChromaColor.clamp01(t);
        return switch (this) {
            case LINEAR -> u;
            case EASE_IN -> u * u;
            case EASE_OUT -> 1.0 - (1.0 - u) * (1.0 - u);
            case EASE_IN_OUT -> u * u * (3.0 - 2.0 * u);
            case STEP -> u >= 1.0 ? 1.0 : 0.0;
        };
    }
}
