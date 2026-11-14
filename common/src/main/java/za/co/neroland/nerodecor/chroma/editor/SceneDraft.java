package za.co.neroland.nerodecor.chroma.editor;

import java.util.ArrayList;
import java.util.List;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.Easing;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Keyframe;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.Timeline;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.ZoneProgram;

/**
 * The scene composer behind the hub screen's Scenes tab: one {@link ZoneDraft} per hub zone, each playing either a
 * procedural effect (type + {@link EffectParams}) or a keyframe {@link Timeline} (1 to {@link Timeline#MAX_KEYS} keys:
 * tick offset, colour, easing; a length and a loop flag). Mutable, pure Java; {@link #build()} turns it into the
 * immutable {@link Scene} the existing scene-slot path stores. Preview sampling uses the same {@code ZoneProgram}
 * code the server and the client renderer run, so what the strip shows is what the blocks will play.
 *
 * <p>Keys are always kept in strictly increasing tick order. "Moving" a key up or down swaps its colour and easing
 * with the neighbouring key and leaves the ticks where they are, so the order of colours changes while the timing
 * stays valid.
 */
public final class SceneDraft {

    /** What a zone plays. */
    public enum Kind {
        EFFECT, TIMELINE
    }

    /** One timeline key as edited. */
    public record Key(int tick, ChromaColor colour, Easing easing) {
        public Key {
            if (tick < 0 || tick > Timeline.MAX_TICKS || colour == null || easing == null) {
                throw new IllegalArgumentException("bad key");
            }
        }
    }

    /** Ticks added after the last key when a key is appended (one second). */
    public static final int APPEND_STEP = 20;
    /** Default timeline length: four seconds. */
    public static final int DEFAULT_LENGTH = 80;

    private final List<ZoneDraft> zones = new ArrayList<>();
    private String name = "";

    /** A draft with one zone per entry of {@code channels} (the hub's zones, in order), each a default breathe. */
    public SceneDraft(List<Integer> channels) {
        int n = Math.min(channels.size(), ZoneAssignment.MAX_ZONES);
        for (int i = 0; i < n; i++) {
            zones.add(new ZoneDraft(clampChannel(channels.get(i))));
        }
        if (zones.isEmpty()) {
            zones.add(new ZoneDraft(0));
        }
    }

    /**
     * A draft for the hub's zones loaded from {@code scene}: zones the scene assigns take its program, the rest stay
     * the default. Zones beyond the hub's count are dropped.
     */
    public static SceneDraft fromScene(Scene scene, List<Integer> channels) {
        SceneDraft draft = new SceneDraft(channels);
        draft.name = scene.name();
        for (ZoneAssignment z : scene.zones()) {
            if (z.zone() < draft.zones.size()) {
                draft.zones.get(z.zone()).load(z.program());
            }
        }
        return draft;
    }

    private static int clampChannel(Integer channel) {
        int c = channel == null ? 0 : channel;
        return Math.max(0, Math.min(ZoneAssignment.MAX_CHANNELS - 1, c));
    }

    public int zoneCount() {
        return zones.size();
    }

    public ZoneDraft zone(int index) {
        return zones.get(Math.max(0, Math.min(zones.size() - 1, index)));
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /** Copies zone {@code from}'s program (kind, effect, keys, length, loop) to every other zone; channels stay. */
    public void copyToAll(int from) {
        ZoneDraft source = zone(from);
        for (ZoneDraft z : zones) {
            if (z != source) {
                z.copyFrom(source);
            }
        }
    }

    /** Why {@link #build()} would fail, or {@code null}. */
    public String validate() {
        for (int i = 0; i < zones.size(); i++) {
            String problem = zones.get(i).validate();
            if (problem != null) {
                return "zone " + (i + 1) + ": " + problem;
            }
        }
        return null;
    }

    /**
     * The scene this draft describes, named {@code fallbackName} when no name was typed.
     *
     * @throws IllegalArgumentException when a zone is invalid (see {@link #validate()})
     */
    public Scene build(String fallbackName) {
        List<ZoneAssignment> out = new ArrayList<>(zones.size());
        for (int i = 0; i < zones.size(); i++) {
            ZoneDraft z = zones.get(i);
            out.add(new ZoneAssignment(i, z.channel, z.program()));
        }
        String n = Scene.sanitiseName(name).isEmpty() ? fallbackName : name;
        return new Scene(n, out);
    }

    /** One zone's program while it is being edited. */
    public static final class ZoneDraft {
        private final int channel;
        private Kind kind = Kind.EFFECT;
        private EffectType type = EffectType.BREATHE;
        private ChromaColor primary = ChromaColor.WHITE;
        private ChromaColor secondary = ChromaColor.of(0x10, 0x18, 0x30);
        private int period = 40;
        private int amplitude = EffectParams.MAX_UNIT;
        private int axis = EffectParams.AXIS_X;
        private int wavelength = 8;
        private int density = 32;
        private final List<Key> keys = new ArrayList<>();
        private int length = DEFAULT_LENGTH;
        private boolean loop = true;

        ZoneDraft(int channel) {
            this.channel = channel;
            keys.add(new Key(0, ChromaColor.of(0x22, 0xE8, 0xFF), Easing.LINEAR));
            keys.add(new Key(40, ChromaColor.of(0x8A, 0x5C, 0xFF), Easing.EASE_IN_OUT));
        }

        void copyFrom(ZoneDraft o) {
            kind = o.kind;
            type = o.type;
            primary = o.primary;
            secondary = o.secondary;
            period = o.period;
            amplitude = o.amplitude;
            axis = o.axis;
            wavelength = o.wavelength;
            density = o.density;
            keys.clear();
            keys.addAll(o.keys);
            length = o.length;
            loop = o.loop;
        }

        void load(ZoneProgram program) {
            if (program instanceof Timeline tl) {
                kind = Kind.TIMELINE;
                keys.clear();
                for (Keyframe k : tl.keys()) {
                    keys.add(new Key(k.tick(), k.color(), k.easing()));
                }
                length = tl.length();
                loop = tl.loops();
            } else if (program instanceof EffectProgram ep) {
                kind = Kind.EFFECT;
                type = ep.type();
                EffectParams p = ep.params();
                primary = p.primary();
                secondary = p.secondary();
                period = p.periodTicks();
                amplitude = p.amplitude();
                axis = p.axis();
                wavelength = p.wavelength();
                density = p.density();
            }
        }

        public int channel() {
            return channel;
        }

        public Kind kind() {
            return kind;
        }

        public void setKind(Kind kind) {
            this.kind = kind == null ? Kind.EFFECT : kind;
        }

        // --- effect ---------------------------------------------------------------------------------------------

        public EffectType type() {
            return type;
        }

        public void cycleType(int dir) {
            EffectType[] all = EffectType.values();
            type = all[Math.floorMod(type.ordinal() + dir, all.length)];
        }

        public ChromaColor primary() {
            return primary;
        }

        public ChromaColor secondary() {
            return secondary;
        }

        public void setColours(ChromaColor a, ChromaColor b) {
            if (a != null) {
                primary = a;
            }
            if (b != null) {
                secondary = b;
            }
        }

        public int period() {
            return period;
        }

        public void setPeriod(int ticks) {
            period = clamp(ticks, EffectParams.MIN_PERIOD, EffectParams.MAX_PERIOD);
        }

        public int amplitude() {
            return amplitude;
        }

        public void setAmplitude(int v) {
            amplitude = clamp(v, 0, EffectParams.MAX_UNIT);
        }

        public int axis() {
            return axis;
        }

        public void cycleAxis() {
            axis = (axis + 1) % 3;
        }

        public int wavelength() {
            return wavelength;
        }

        public void setWavelength(int v) {
            wavelength = clamp(v, EffectParams.MIN_WAVELENGTH, EffectParams.MAX_WAVELENGTH);
        }

        public int density() {
            return density;
        }

        public void setDensity(int v) {
            density = clamp(v, 0, EffectParams.MAX_UNIT);
        }

        public EffectParams params() {
            return EffectParams.builder().primary(primary).secondary(secondary).periodTicks(period)
                    .amplitude(amplitude).axis(axis).wavelength(wavelength).density(density).build();
        }

        // --- timeline -------------------------------------------------------------------------------------------

        public List<Key> keys() {
            return List.copyOf(keys);
        }

        public int keyCount() {
            return keys.size();
        }

        public Key key(int index) {
            return keys.get(index);
        }

        public int length() {
            return length;
        }

        public boolean loops() {
            return loop;
        }

        public void setLoop(boolean loop) {
            this.loop = loop;
        }

        /** Sets the length, never below the last key's tick (or 1) nor above {@link Timeline#MAX_TICKS}. */
        public int setLength(int ticks) {
            int floor = Math.max(1, keys.isEmpty() ? 1 : keys.get(keys.size() - 1).tick());
            length = clamp(ticks, floor, Timeline.MAX_TICKS);
            return length;
        }

        /**
         * Adds a key after {@code index} coloured {@code colour}: halfway to the next key, or {@link #APPEND_STEP}
         * ticks after the last one (growing the length to fit). Returns the new key's index, or -1 when the
         * timeline is full or there is no free tick between the two keys.
         */
        public int addKeyAfter(int index, ChromaColor colour) {
            if (keys.size() >= Timeline.MAX_KEYS || colour == null) {
                return -1;
            }
            if (keys.isEmpty()) {
                keys.add(new Key(0, colour, Easing.LINEAR));
                return 0;
            }
            int i = clamp(index, 0, keys.size() - 1);
            int tick;
            if (i < keys.size() - 1) {
                int a = keys.get(i).tick();
                int b = keys.get(i + 1).tick();
                if (b - a < 2) {
                    return -1;
                }
                tick = a + (b - a) / 2;
            } else {
                tick = keys.get(i).tick() + APPEND_STEP;
                if (tick > Timeline.MAX_TICKS) {
                    return -1;
                }
                if (tick > length) {
                    length = tick;
                }
            }
            keys.add(i + 1, new Key(tick, colour, Easing.LINEAR));
            return i + 1;
        }

        /** Removes key {@code index} unless it is the only one. */
        public boolean removeKey(int index) {
            if (keys.size() <= 1 || index < 0 || index >= keys.size()) {
                return false;
            }
            keys.remove(index);
            return true;
        }

        /**
         * Moves key {@code index} one place up ({@code dir < 0}) or down: swaps its colour and easing with the
         * neighbour, keeping both ticks. Returns the key's new index, or -1 at either end.
         */
        public int moveKey(int index, int dir) {
            int j = index + Integer.signum(dir);
            if (dir == 0 || index < 0 || index >= keys.size() || j < 0 || j >= keys.size()) {
                return -1;
            }
            Key a = keys.get(index);
            Key b = keys.get(j);
            keys.set(index, new Key(a.tick(), b.colour(), b.easing()));
            keys.set(j, new Key(b.tick(), a.colour(), a.easing()));
            return j;
        }

        /** Sets key {@code index}'s tick, clamped between its neighbours and to {@code [0, length]}. */
        public int setTick(int index, int tick) {
            int lo = index == 0 ? 0 : keys.get(index - 1).tick() + 1;
            int hi = index == keys.size() - 1 ? length : keys.get(index + 1).tick() - 1;
            if (hi < lo) {
                return keys.get(index).tick();
            }
            int t = clamp(tick, lo, hi);
            Key k = keys.get(index);
            keys.set(index, new Key(t, k.colour(), k.easing()));
            return t;
        }

        public void setKeyColour(int index, ChromaColor colour) {
            if (colour != null) {
                Key k = keys.get(index);
                keys.set(index, new Key(k.tick(), colour, k.easing()));
            }
        }

        public void cycleEasing(int index, int dir) {
            Key k = keys.get(index);
            Easing[] all = Easing.values();
            keys.set(index, new Key(k.tick(), k.colour(), all[Math.floorMod(k.easing().ordinal() + dir, all.length)]));
        }

        // --- program + preview ----------------------------------------------------------------------------------

        /** Why {@link #program()} would fail, or {@code null}. */
        public String validate() {
            if (kind == Kind.TIMELINE) {
                List<Keyframe> frames = new ArrayList<>();
                for (Key k : keys) {
                    frames.add(new Keyframe(k.tick(), k.colour(), k.easing()));
                }
                return Timeline.validate(frames, length);
            }
            return EffectParams.validate(period, amplitude, axis, wavelength, density, EffectParams.MAX_DAYLIGHT);
        }

        /** @throws IllegalArgumentException when invalid */
        public ZoneProgram program() {
            if (kind == Kind.TIMELINE) {
                List<Keyframe> frames = new ArrayList<>(keys.size());
                for (Key k : keys) {
                    frames.add(new Keyframe(k.tick(), k.colour(), k.easing()));
                }
                return new Timeline(frames, length, loop);
            }
            return new EffectProgram(type, params());
        }

        /** Ticks in one cycle of the program: the timeline length or the effect period. */
        public int cycleTicks() {
            return kind == Kind.TIMELINE ? length : period;
        }

        /**
         * {@code samples} colours across one cycle at the hub-relative origin, as the block at the hub would show
         * them; {@code null} entries if the zone is invalid.
         */
        public ChromaColor[] timeStrip(int samples, long seed) {
            ChromaColor[] out = new ChromaColor[Math.max(0, samples)];
            ZoneProgram program;
            try {
                program = program();
            } catch (IllegalArgumentException invalid) {
                return out;
            }
            int cycle = Math.max(1, cycleTicks());
            for (int s = 0; s < out.length; s++) {
                long t = out.length <= 1 ? 0 : Math.round(s * (double) cycle / out.length);
                out[s] = program.colourAt(t, 0, 0, 0, seed);
            }
            return out;
        }

        /**
         * {@code samples} blocks in a row along the effect axis (x for timelines) at {@code elapsed} ticks: the live
         * spatial view of a chase, wave, ripple, rainbow or sparkle. {@code null} entries if the zone is invalid.
         */
        public ChromaColor[] spaceStrip(int samples, long elapsed, long seed) {
            ChromaColor[] out = new ChromaColor[Math.max(0, samples)];
            ZoneProgram program;
            try {
                program = program();
            } catch (IllegalArgumentException invalid) {
                return out;
            }
            int ax = kind == Kind.TIMELINE ? EffectParams.AXIS_X : axis;
            for (int s = 0; s < out.length; s++) {
                int x = ax == EffectParams.AXIS_X ? s : 0;
                int y = ax == EffectParams.AXIS_Y ? s : 0;
                int z = ax == EffectParams.AXIS_Z ? s : 0;
                out[s] = program.colourAt(elapsed, x, y, z, seed);
            }
            return out;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
