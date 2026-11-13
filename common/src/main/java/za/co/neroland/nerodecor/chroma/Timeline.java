package za.co.neroland.nerodecor.chroma;

import java.util.List;

/**
 * A keyframe timeline: 1 to {@link #MAX_KEYS} keys at strictly increasing ticks within
 * {@code [0, length]}, either looping with period {@code length} or one-shot (holding the last key
 * after it ends and the first key before it starts).
 */
public final class Timeline implements ZoneProgram {

    public static final int MAX_KEYS = 32;
    /** Ticks fit an unsigned 16-bit field on the wire (about 54 minutes). */
    public static final int MAX_TICKS = 0xFFFF;

    private final List<Keyframe> keys;
    private final int length;
    private final boolean loop;

    /** @throws IllegalArgumentException when any bound is violated */
    public Timeline(List<Keyframe> keys, int length, boolean loop) {
        String problem = validate(keys, length);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.keys = List.copyOf(keys);
        this.length = length;
        this.loop = loop;
    }

    /** Reason the inputs are invalid, or {@code null} when they are fine. */
    public static String validate(List<Keyframe> keys, int length) {
        if (keys == null || keys.isEmpty() || keys.size() > MAX_KEYS) {
            return "timeline needs 1-" + MAX_KEYS + " keys";
        }
        if (length < 1 || length > MAX_TICKS) {
            return "timeline length out of range";
        }
        int prev = -1;
        for (Keyframe k : keys) {
            if (k == null || k.tick() <= prev || k.tick() > length) {
                return "key ticks must be strictly increasing within [0, length]";
            }
            prev = k.tick();
        }
        return null;
    }

    public List<Keyframe> keys() {
        return keys;
    }

    public int length() {
        return length;
    }

    public boolean loops() {
        return loop;
    }

    @Override
    public boolean positionIndependent() {
        return true;
    }

    @Override
    public ChromaColor colourAt(long elapsedTicks, int x, int y, int z, long seed) {
        return colourAt(elapsedTicks);
    }

    /** Colour at {@code elapsedTicks} since the timeline started. */
    public ChromaColor colourAt(long elapsedTicks) {
        Keyframe first = keys.get(0);
        int n = keys.size();
        Keyframe last = keys.get(n - 1);
        long t;
        if (loop) {
            t = Math.floorMod(elapsedTicks, (long) length);
        } else {
            t = elapsedTicks < 0 ? 0 : StrictMath.min(elapsedTicks, length);
        }
        if (n == 1) {
            return first.color();
        }
        if (t < first.tick()) {
            if (!loop) {
                return first.color();
            }
            // Wrap segment: last key -> first key across the loop boundary.
            return blend(last, first, t + length - last.tick(), (long) length - last.tick() + first.tick());
        }
        if (t >= last.tick()) {
            if (!loop) {
                return last.color();
            }
            long span = (long) length - last.tick() + first.tick();
            return span <= 0 ? first.color() : blend(last, first, t - last.tick(), span);
        }
        int i = 0;
        while (keys.get(i + 1).tick() <= t) {
            i++;
        }
        Keyframe a = keys.get(i);
        Keyframe b = keys.get(i + 1);
        return blend(a, b, t - a.tick(), (long) b.tick() - a.tick());
    }

    private static ChromaColor blend(Keyframe from, Keyframe to, long offset, long span) {
        if (span <= 0) {
            return to.color();
        }
        double eased = to.easing().apply(offset / (double) span);
        if (eased <= 0.0) {
            return from.color();
        }
        if (eased >= 1.0) {
            return to.color();
        }
        return OkLab.mix(from.lab(), from.color().brightness(), to.lab(), to.color().brightness(), eased);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Timeline tl && tl.length == length && tl.loop == loop && tl.keys.equals(keys);
    }

    @Override
    public int hashCode() {
        return (keys.hashCode() * 31 + length) * 2 + (loop ? 1 : 0);
    }
}
