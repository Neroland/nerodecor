package za.co.neroland.nerodecor.chroma;

/**
 * One timeline key: a tick offset, a colour, and the easing used to arrive at this key from the
 * previous one.
 */
public final class Keyframe {

    private final int tick;
    private final ChromaColor color;
    private final Easing easing;
    private final double[] lab;

    /** @throws IllegalArgumentException when the tick is outside {@code [0, Timeline.MAX_TICKS]} */
    public Keyframe(int tick, ChromaColor color, Easing easing) {
        if (tick < 0 || tick > Timeline.MAX_TICKS || color == null || easing == null) {
            throw new IllegalArgumentException("bad keyframe");
        }
        this.tick = tick;
        this.color = color;
        this.easing = easing;
        this.lab = OkLab.fromColor(color);
    }

    public int tick() {
        return tick;
    }

    public ChromaColor color() {
        return color;
    }

    public Easing easing() {
        return easing;
    }

    double[] lab() {
        return lab;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Keyframe k && k.tick == tick && k.color.equals(color) && k.easing == easing;
    }

    @Override
    public int hashCode() {
        return (tick * 31 + color.hashCode()) * 31 + easing.hashCode();
    }

    @Override
    public String toString() {
        return "Key[" + tick + " " + color + " " + easing + "]";
    }
}
