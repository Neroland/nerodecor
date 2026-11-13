package za.co.neroland.nerodecor.chroma;

/**
 * Immutable effect parameters. Every field has a fixed wire range, checked on construction.
 *
 * <ul>
 *   <li>{@code primary}/{@code secondary}: the two colours an effect moves between (rainbow uses
 *       primary's brightness; sky mirror uses primary as the night colour).</li>
 *   <li>{@code periodTicks}: one cycle, 1-65535 ticks (sparkle: how long one twinkle lasts).</li>
 *   <li>{@code amplitude}: 0-255, how far towards secondary the effect swings (rainbow: saturation).</li>
 *   <li>{@code axis}: 0=x, 1=y, 2=z for chase, axis wave and rainbow.</li>
 *   <li>{@code wavelength}: 1-255 blocks per spatial cycle.</li>
 *   <li>{@code density}: 0-255, the fraction of blocks lit by sparkle.</li>
 *   <li>{@code sky}/{@code daylight}: supplied by the caller for sky mirror (0-15 daylight).</li>
 * </ul>
 */
public final class EffectParams {

    public static final int MIN_PERIOD = 1;
    public static final int MAX_PERIOD = 0xFFFF;
    public static final int MAX_UNIT = 255;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;
    public static final int MIN_WAVELENGTH = 1;
    public static final int MAX_WAVELENGTH = 255;
    public static final int MAX_DAYLIGHT = 15;

    private final ChromaColor primary;
    private final ChromaColor secondary;
    private final int periodTicks;
    private final int amplitude;
    private final int axis;
    private final int wavelength;
    private final int density;
    private final ChromaColor sky;
    private final int daylight;
    final double[] primaryLab;
    final double[] secondaryLab;
    final double[] skyLab;

    private EffectParams(Builder b) {
        String problem = validate(b.periodTicks, b.amplitude, b.axis, b.wavelength, b.density, b.daylight);
        if (problem != null || b.primary == null || b.secondary == null || b.sky == null) {
            throw new IllegalArgumentException(problem != null ? problem : "null colour");
        }
        this.primary = b.primary;
        this.secondary = b.secondary;
        this.periodTicks = b.periodTicks;
        this.amplitude = b.amplitude;
        this.axis = b.axis;
        this.wavelength = b.wavelength;
        this.density = b.density;
        this.sky = b.sky;
        this.daylight = b.daylight;
        this.primaryLab = OkLab.fromColor(primary);
        this.secondaryLab = OkLab.fromColor(secondary);
        this.skyLab = OkLab.fromColor(sky);
    }

    /** Reason the scalar fields are invalid, or {@code null}. */
    public static String validate(int periodTicks, int amplitude, int axis, int wavelength, int density, int daylight) {
        if (periodTicks < MIN_PERIOD || periodTicks > MAX_PERIOD) {
            return "period out of range";
        }
        if (amplitude < 0 || amplitude > MAX_UNIT || density < 0 || density > MAX_UNIT) {
            return "amplitude/density out of range";
        }
        if (axis < AXIS_X || axis > AXIS_Z) {
            return "axis out of range";
        }
        if (wavelength < MIN_WAVELENGTH || wavelength > MAX_WAVELENGTH) {
            return "wavelength out of range";
        }
        if (daylight < 0 || daylight > MAX_DAYLIGHT) {
            return "daylight out of range";
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.primary = primary;
        b.secondary = secondary;
        b.periodTicks = periodTicks;
        b.amplitude = amplitude;
        b.axis = axis;
        b.wavelength = wavelength;
        b.density = density;
        b.sky = sky;
        b.daylight = daylight;
        return b;
    }

    public ChromaColor primary() {
        return primary;
    }

    public ChromaColor secondary() {
        return secondary;
    }

    public int periodTicks() {
        return periodTicks;
    }

    public int amplitude() {
        return amplitude;
    }

    public int axis() {
        return axis;
    }

    public int wavelength() {
        return wavelength;
    }

    public int density() {
        return density;
    }

    public ChromaColor sky() {
        return sky;
    }

    public int daylight() {
        return daylight;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EffectParams p && p.primary.equals(primary) && p.secondary.equals(secondary)
                && p.periodTicks == periodTicks && p.amplitude == amplitude && p.axis == axis
                && p.wavelength == wavelength && p.density == density && p.sky.equals(sky)
                && p.daylight == daylight;
    }

    @Override
    public int hashCode() {
        int h = primary.hashCode();
        h = h * 31 + secondary.hashCode();
        h = h * 31 + periodTicks;
        h = h * 31 + amplitude;
        h = h * 31 + axis;
        h = h * 31 + wavelength;
        h = h * 31 + density;
        h = h * 31 + sky.hashCode();
        return h * 31 + daylight;
    }

    /** Mutable builder with sensible defaults (white to black, 2 s period, full amplitude). */
    public static final class Builder {
        private ChromaColor primary = ChromaColor.WHITE;
        private ChromaColor secondary = ChromaColor.BLACK;
        private int periodTicks = 40;
        private int amplitude = MAX_UNIT;
        private int axis = AXIS_X;
        private int wavelength = 8;
        private int density = 32;
        private ChromaColor sky = ChromaColor.of(0x78, 0xA7, 0xFF);
        private int daylight = MAX_DAYLIGHT;

        private Builder() {
        }

        public Builder primary(ChromaColor c) {
            this.primary = c;
            return this;
        }

        public Builder secondary(ChromaColor c) {
            this.secondary = c;
            return this;
        }

        public Builder periodTicks(int v) {
            this.periodTicks = v;
            return this;
        }

        public Builder amplitude(int v) {
            this.amplitude = v;
            return this;
        }

        public Builder axis(int v) {
            this.axis = v;
            return this;
        }

        public Builder wavelength(int v) {
            this.wavelength = v;
            return this;
        }

        public Builder density(int v) {
            this.density = v;
            return this;
        }

        public Builder sky(ChromaColor c) {
            this.sky = c;
            return this;
        }

        public Builder daylight(int v) {
            this.daylight = v;
            return this;
        }

        /** @throws IllegalArgumentException when a field is out of range */
        public EffectParams build() {
            return new EffectParams(this);
        }
    }
}
