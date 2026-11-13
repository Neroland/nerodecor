package za.co.neroland.nerodecor.chroma;

/**
 * Procedural effects as pure, deterministic functions of elapsed ticks, hub-relative position, a seed
 * and {@link EffectParams}. No state, no world access; the only allocation is the returned colour.
 */
public final class ChromaEffects {

    private static final double TAU = 2.0 * StrictMath.PI;

    private ChromaEffects() {
    }

    /**
     * Colour of the block at hub-relative {@code (x, y, z)}.
     *
     * @param elapsedTicks {@code gameTime - startTick}; negative values are handled
     */
    public static ChromaColor colourAt(EffectType type, long elapsedTicks, int x, int y, int z, long seed,
            EffectParams p) {
        double amp = p.amplitude() / (double) EffectParams.MAX_UNIT;
        int period = p.periodTicks();
        double phase = Math.floorMod(elapsedTicks, (long) period) / (double) period;
        switch (type) {
            case PULSE: {
                // Sharp attack at the start of each cycle, quadratic decay back to secondary.
                double decay = (1.0 - phase) * (1.0 - phase);
                return mixPrimary(p, amp * (1.0 - decay));
            }
            case BREATHE: {
                double w = 0.5 - 0.5 * StrictMath.cos(TAU * phase);
                return mixPrimary(p, amp * w);
            }
            case CHASE: {
                // Sawtooth along the axis: a bright head with a fading tail, moving one wavelength per period.
                double s = frac(axisCoord(p.axis(), x, y, z) / (double) p.wavelength() - phase);
                return mixPrimary(p, amp * s);
            }
            case AXIS_WAVE: {
                double s = axisCoord(p.axis(), x, y, z) / (double) p.wavelength() - phase;
                return mixPrimary(p, amp * (0.5 - 0.5 * StrictMath.cos(TAU * s)));
            }
            case RADIAL_RIPPLE: {
                double d = StrictMath.sqrt((double) x * x + (double) y * y + (double) z * z);
                double s = d / p.wavelength() - phase;
                return mixPrimary(p, amp * (0.5 - 0.5 * StrictMath.cos(TAU * s)));
            }
            case RAINBOW: {
                double hue = frac(phase + axisCoord(p.axis(), x, y, z) / (double) p.wavelength());
                return ChromaColor.fromHsv(hue, amp, 1.0, p.primary().brightness());
            }
            case SPARKLE:
                return sparkle(elapsedTicks, x, y, z, seed, p);
            case SKY_MIRROR: {
                double daylight = p.daylight() / (double) EffectParams.MAX_DAYLIGHT;
                return OkLab.mix(p.primaryLab, p.primary().brightness(), p.skyLab, p.sky().brightness(),
                        daylight);
            }
            default:
                throw new IllegalStateException("unknown effect");
        }
    }

    private static ChromaColor sparkle(long elapsed, int x, int y, int z, long seed, EffectParams p) {
        long period = p.periodTicks();
        long posHash = mix64(seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
        // Stagger each block's cycle so the whole area never re-rolls on the same tick.
        long offset = Math.floorMod(posHash, period);
        long cycle = Math.floorDiv(elapsed + offset, period);
        long h = mix64(posHash ^ cycle * 0xD6E8FEB86659FD93L);
        double roll = (h >>> 40) / (double) (1L << 24);
        boolean lit = roll < p.density() / (double) EffectParams.MAX_UNIT;
        return lit ? p.secondary() : p.primary();
    }

    private static ChromaColor mixPrimary(EffectParams p, double towardsSecondary) {
        if (towardsSecondary <= 0.0) {
            return p.primary();
        }
        if (towardsSecondary >= 1.0) {
            return p.secondary();
        }
        return OkLab.mix(p.primaryLab, p.primary().brightness(), p.secondaryLab, p.secondary().brightness(),
                towardsSecondary);
    }

    static int axisCoord(int axis, int x, int y, int z) {
        return axis == EffectParams.AXIS_X ? x : axis == EffectParams.AXIS_Y ? y : z;
    }

    static double frac(double v) {
        return v - StrictMath.floor(v);
    }

    /** SplitMix64 finaliser. */
    static long mix64(long v) {
        long z = v + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
