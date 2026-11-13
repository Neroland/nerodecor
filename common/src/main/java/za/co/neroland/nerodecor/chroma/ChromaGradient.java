package za.co.neroland.nerodecor.chroma;

import java.util.Arrays;
import java.util.List;

/** An immutable multi-stop gradient interpolated in OKLab. Stops are precomputed once. */
public final class ChromaGradient {

    public static final int MAX_STOPS = 16;

    private final double[] positions;
    private final ChromaColor[] colors;
    private final double[][] labs;

    private ChromaGradient(double[] positions, ChromaColor[] colors) {
        this.positions = positions;
        this.colors = colors;
        this.labs = new double[colors.length][];
        for (int i = 0; i < colors.length; i++) {
            labs[i] = OkLab.fromColor(colors[i]);
        }
    }

    /** Evenly spaced stops (2 to {@link #MAX_STOPS}). */
    public static ChromaGradient even(List<ChromaColor> stops) {
        int n = stops.size();
        double[] pos = new double[n];
        for (int i = 0; i < n; i++) {
            pos[i] = n == 1 ? 0.0 : i / (double) (n - 1);
        }
        return of(pos, stops);
    }

    /**
     * Stops at explicit positions in {@code [0,1]}, non-decreasing.
     *
     * @throws IllegalArgumentException on a bad stop count, position or ordering
     */
    public static ChromaGradient of(double[] positions, List<ChromaColor> stops) {
        int n = stops.size();
        if (n < 2 || n > MAX_STOPS || positions.length != n) {
            throw new IllegalArgumentException("gradient needs 2-" + MAX_STOPS + " stops");
        }
        double prev = 0.0;
        for (double p : positions) {
            if (!(p >= prev && p <= 1.0)) {
                throw new IllegalArgumentException("stop positions must be ascending within [0,1]");
            }
            prev = p;
        }
        for (ChromaColor c : stops) {
            if (c == null) {
                throw new IllegalArgumentException("null stop");
            }
        }
        return new ChromaGradient(Arrays.copyOf(positions, n), stops.toArray(new ChromaColor[0]));
    }

    public int stopCount() {
        return colors.length;
    }

    /** Colour at {@code t} (clamped to 0-1). */
    public ChromaColor sample(double t) {
        double u = ChromaColor.clamp01(t);
        int last = colors.length - 1;
        if (u <= positions[0]) {
            return colors[0];
        }
        if (u >= positions[last]) {
            return colors[last];
        }
        int i = 0;
        while (i < last - 1 && u >= positions[i + 1]) {
            i++;
        }
        double span = positions[i + 1] - positions[i];
        double f = span <= 0.0 ? 1.0 : (u - positions[i]) / span;
        return OkLab.mix(labs[i], colors[i].brightness(), labs[i + 1], colors[i + 1].brightness(), f);
    }
}
