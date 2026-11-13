package za.co.neroland.nerodecor.chroma;

/** Procedural effects. Ids are stable wire values: append only. */
public enum EffectType {
    PULSE,
    BREATHE,
    CHASE,
    AXIS_WAVE,
    RADIAL_RIPPLE,
    RAINBOW,
    SPARKLE,
    SKY_MIRROR;

    private static final EffectType[] VALUES = values();

    public int id() {
        return ordinal();
    }

    /** {@code null} for an unknown id. */
    public static EffectType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
