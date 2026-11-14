package za.co.neroland.nerodecor.chroma;

/**
 * Evaluates one scene for many blocks, as the client's animation layer does once per game tick. Deterministic: the
 * same scene, elapsed tick, positions and seed always give the same colours (it only calls the Stage 1 pure
 * functions). Position-independent zones are evaluated once per call, not once per block. Pure Java.
 */
public final class SceneAnimator {

    /** Lowest brightness factor, so brightness 0 dims a block instead of blacking it out. */
    public static final double MIN_BRIGHTNESS_FACTOR = 0.2;

    private final Scene scene;
    private final ZoneProgram[] programs = new ZoneProgram[ZoneAssignment.MAX_ZONES];
    private final boolean reducedMotion;

    /** @param reducedMotion run calmer variants ({@link ReducedMotion#calmer(ZoneProgram)}) */
    public SceneAnimator(Scene scene, boolean reducedMotion) {
        this.scene = scene;
        this.reducedMotion = reducedMotion;
        for (ZoneAssignment z : scene.zones()) {
            programs[z.zone()] = reducedMotion ? ReducedMotion.calmer(z.program()) : z.program();
        }
    }

    public Scene scene() {
        return scene;
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    /** Whether blocks in {@code zone} are animated by this scene (others keep their static colour). */
    public boolean animates(int zone) {
        return zone >= 0 && zone < programs.length && programs[zone] != null;
    }

    /** The colour of one block, or {@code null} when its zone is not animated. */
    public ChromaColor colourAt(int zone, long elapsed, int x, int y, int z, long seed) {
        return animates(zone) ? programs[zone].colourAt(elapsed, x, y, z, seed) : null;
    }

    /**
     * Fills {@code out[i]} with the opaque RGB of block {@code i} ({@link RelativePos}-packed offset {@code rel[i]},
     * zone {@code zone[i]}), brightness folded in and scaled by {@code gain} (music reactivity; 1 = none). A block whose
     * zone the scene does not animate gets -1.
     */
    public void fill(long elapsed, long seed, int[] rel, int[] zone, int count, double gain, int[] out) {
        ChromaColor[] shared = new ChromaColor[programs.length];
        for (int i = 0; i < count; i++) {
            int zn = zone[i];
            if (!animates(zn)) {
                out[i] = -1;
                continue;
            }
            ZoneProgram p = programs[zn];
            ChromaColor c;
            if (p.positionIndependent()) {
                c = shared[zn];
                if (c == null) {
                    c = p.colourAt(elapsed, 0, 0, 0, seed);
                    shared[zn] = c;
                }
            } else {
                int r = rel[i];
                c = p.colourAt(elapsed, RelativePos.x(r), RelativePos.y(r), RelativePos.z(r), seed);
            }
            out[i] = toRgb(c, gain);
        }
    }

    /** RGB with the colour's brightness (0-15) folded in, times {@code gain}, clamped to 0-255 per channel. */
    public static int toRgb(ChromaColor c, double gain) {
        double f = (MIN_BRIGHTNESS_FACTOR + (1.0 - MIN_BRIGHTNESS_FACTOR) * c.brightness()
                / ChromaColor.MAX_BRIGHTNESS) * gain;
        int r = (int) Math.min(255L, Math.max(0L, Math.round(c.red() * f)));
        int g = (int) Math.min(255L, Math.max(0L, Math.round(c.green() * f)));
        int b = (int) Math.min(255L, Math.max(0L, Math.round(c.blue() * f)));
        return r << 16 | g << 8 | b;
    }

    /**
     * The scene's dominant colour: the primary colour (or first key) used by the most zones; ties go to the lowest
     * zone. White for an empty scene. Used for the Scene Chip's stripe.
     */
    public static int dominantRgb(Scene scene) {
        int best = 0xFFFFFF;
        int bestCount = 0;
        for (ZoneAssignment a : scene.zones()) {
            int rgb = baseColour(a.program()).rgb();
            int count = 0;
            for (ZoneAssignment b : scene.zones()) {
                if (baseColour(b.program()).rgb() == rgb) {
                    count++;
                }
            }
            if (count > bestCount) {
                best = rgb;
                bestCount = count;
            }
        }
        return best;
    }

    private static ChromaColor baseColour(ZoneProgram program) {
        if (program instanceof EffectProgram e) {
            return e.params().primary();
        }
        return ((Timeline) program).keys().get(0).color();
    }
}
