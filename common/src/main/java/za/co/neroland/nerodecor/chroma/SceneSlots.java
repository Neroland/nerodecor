package za.co.neroland.nerodecor.chroma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One hub's scene slots (Stage 4): up to {@link #MAX_SLOTS} stored scenes, the slot that is playing (or
 * {@link #STATIC}) with the game tick it started at, and the trigger that picks the slot. Pure Java.
 *
 * <p>Colours are never sent per block or per tick: the client evaluates the active scene as a pure function of
 * {@code gameTime - startTick}, so a scene change is one small packet. {@link #revision()} increases on every change
 * so the sync layer can tell when to send.
 *
 * <p>Privacy: a scene holds no author, player id or coordinates (see {@link Scene}).
 */
public final class SceneSlots {

    public static final int MAX_SLOTS = 8;
    /** "No scene": the hub shows its static colours. */
    public static final int STATIC = -1;
    /** Redstone strengths 0-15. */
    public static final int REDSTONE_LEVELS = 16;

    /** What chooses the active slot. Ids are stable (saved data): append only. */
    public enum Trigger {
        /** Players pick the slot (scene chip, editor). */
        MANUAL,
        /** The strongest redstone signal into the hub (0-15) picks the slot through the redstone map. */
        REDSTONE,
        /** The time of day picks the slot through the daylight map. */
        DAYLIGHT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Trigger byId(String id) {
            for (Trigger t : values()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
            return MANUAL;
        }
    }

    /** Daylight bands of the 24,000-tick Minecraft day. */
    public enum DaylightBand {
        /** 23000-999. */
        DAWN,
        /** 1000-11999. */
        DAY,
        /** 12000-12999. */
        DUSK,
        /** 13000-22999. */
        NIGHT;

        /** The band of a level's day time (any value; wrapped to one day). */
        public static DaylightBand of(long dayTime) {
            long t = Math.floorMod(dayTime, 24000L);
            if (t >= 23000L || t < 1000L) {
                return DAWN;
            }
            if (t < 12000L) {
                return DAY;
            }
            if (t < 13000L) {
                return DUSK;
            }
            return NIGHT;
        }
    }

    private final Scene[] slots = new Scene[MAX_SLOTS];
    /** Whether the slot's scene has passed the flash clamp for this hub (false = not yet checked). */
    private final boolean[] clampPassed = new boolean[MAX_SLOTS];
    private int active = STATIC;
    private long startTick;
    private Trigger trigger = Trigger.MANUAL;
    private final int[] redstoneMap = defaultRedstoneMap();
    private final int[] daylightMap = defaultDaylightMap();
    private int revision;

    /** Default redstone mapping: strength 0 is static, otherwise {@code min(strength - 1, slots - 1)}, so slot 0 is reachable. */
    public static int defaultRedstoneSlot(int strength, int slots) {
        if (strength <= 0 || slots <= 0) {
            return STATIC;
        }
        return Math.min(strength - 1, slots - 1);
    }

    public static int[] defaultRedstoneMap() {
        int[] map = new int[REDSTONE_LEVELS];
        for (int s = 0; s < REDSTONE_LEVELS; s++) {
            map[s] = defaultRedstoneSlot(s, MAX_SLOTS);
        }
        return map;
    }

    /** Default daylight mapping: dawn 0, day 1, dusk 2, night 3. */
    public static int[] defaultDaylightMap() {
        return new int[] {0, 1, 2, 3};
    }

    /** A slot index or {@link #STATIC}; anything else becomes {@link #STATIC}. */
    public static int sanitiseSlot(int slot) {
        return slot >= 0 && slot < MAX_SLOTS ? slot : STATIC;
    }

    public static boolean isSlot(int slot) {
        return slot >= 0 && slot < MAX_SLOTS;
    }

    // --- reads ----------------------------------------------------------------------------------------------------

    /** The scene in {@code slot}, or {@code null}. */
    public Scene scene(int slot) {
        return isSlot(slot) ? slots[slot] : null;
    }

    public boolean clampPassed(int slot) {
        return isSlot(slot) && clampPassed[slot];
    }

    /** The playing slot, or {@link #STATIC}. */
    public int active() {
        return active;
    }

    /** The playing scene, or {@code null}. */
    public Scene activeScene() {
        return scene(active);
    }

    public long startTick() {
        return startTick;
    }

    public Trigger trigger() {
        return trigger;
    }

    public int[] redstoneMap() {
        return redstoneMap.clone();
    }

    public int[] daylightMap() {
        return daylightMap.clone();
    }

    public int revision() {
        return revision;
    }

    /** Number of filled slots. */
    public int filled() {
        int n = 0;
        for (Scene s : slots) {
            if (s != null) {
                n++;
            }
        }
        return n;
    }

    /** First empty slot, or {@link #STATIC} when all are filled. */
    public int firstEmpty() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        return STATIC;
    }

    /** The slot the redstone map picks for {@code strength} (0-15). */
    public int redstoneSlot(int strength) {
        return redstoneMap[Math.max(0, Math.min(REDSTONE_LEVELS - 1, strength))];
    }

    /** The slot the daylight map picks for {@code dayTime}. */
    public int daylightSlot(long dayTime) {
        return daylightMap[DaylightBand.of(dayTime).ordinal()];
    }

    /**
     * The slot the trigger wants now ({@link #active()} for {@link Trigger#MANUAL}). An empty slot resolves to
     * {@link #STATIC}.
     */
    public int desiredSlot(int redstoneStrength, long dayTime) {
        int slot = switch (trigger) {
            case MANUAL -> active;
            case REDSTONE -> redstoneSlot(redstoneStrength);
            case DAYLIGHT -> daylightSlot(dayTime);
        };
        return scene(slot) == null ? STATIC : slot;
    }

    // --- writes ---------------------------------------------------------------------------------------------------

    /**
     * Stores (or clears, with {@code null}) a scene. Clearing the playing slot stops it.
     *
     * @param clampChecked whether the caller ran the flash clamp for this hub and it passed
     */
    public void set(int slot, Scene scene, boolean clampChecked) {
        if (!isSlot(slot)) {
            throw new IllegalArgumentException("slot 0-" + (MAX_SLOTS - 1));
        }
        slots[slot] = scene;
        clampPassed[slot] = scene != null && clampChecked;
        if (scene == null && active == slot) {
            active = STATIC;
        }
        revision++;
    }

    /** Records that the slot's scene passed the flash clamp (lazy check of restored scenes). */
    public void markClampPassed(int slot) {
        if (isSlot(slot) && slots[slot] != null) {
            clampPassed[slot] = true;
        }
    }

    /**
     * Plays {@code slot} from {@code gameTime} ({@link #STATIC} stops). An empty slot stops too.
     *
     * @return whether anything changed
     */
    public boolean activate(int slot, long gameTime) {
        int target = scene(slot) == null ? STATIC : slot;
        if (target == active) {
            return false;
        }
        active = target;
        startTick = gameTime;
        revision++;
        return true;
    }

    public void setTrigger(Trigger trigger) {
        if (trigger != null && trigger != this.trigger) {
            this.trigger = trigger;
            revision++;
        }
    }

    /** Sets the redstone map (16 entries; out-of-range entries become {@link #STATIC}). */
    public void setRedstoneMap(int[] map) {
        if (map == null || map.length != REDSTONE_LEVELS) {
            throw new IllegalArgumentException("redstone map needs 16 entries");
        }
        for (int i = 0; i < REDSTONE_LEVELS; i++) {
            redstoneMap[i] = sanitiseSlot(map[i]);
        }
        revision++;
    }

    /** Sets the daylight map (4 entries: dawn, day, dusk, night). */
    public void setDaylightMap(int[] map) {
        if (map == null || map.length != DaylightBand.values().length) {
            throw new IllegalArgumentException("daylight map needs 4 entries");
        }
        for (int i = 0; i < map.length; i++) {
            daylightMap[i] = sanitiseSlot(map[i]);
        }
        revision++;
    }

    /**
     * Data minimisation for an orphaned network: every stored scene's free-text name becomes {@code "Scene <n>"}
     * ({@code n} = slot + 1); zones and programs are kept.
     */
    public void blankNames() {
        boolean any = false;
        for (int i = 0; i < MAX_SLOTS; i++) {
            Scene s = slots[i];
            String generic = "Scene " + (i + 1);
            if (s != null && !s.name().equals(generic)) {
                slots[i] = new Scene(generic, s.zones());
                any = true;
            }
        }
        if (any) {
            revision++;
        }
    }

    /** Restores the playing state from saved data (no revision bump beyond construction). */
    public void restoreActive(int slot, long tick) {
        this.active = scene(slot) == null ? STATIC : slot;
        this.startTick = tick;
    }

    // --- persistence helpers --------------------------------------------------------------------------------------

    /** Every slot as a share code ({@code ""} for an empty slot). */
    public List<String> slotCodes() {
        List<String> out = new ArrayList<>(MAX_SLOTS);
        for (Scene s : slots) {
            if (s == null) {
                out.add("");
            } else {
                ChromaResult<String> code = SceneCodec.toShareCode(s);
                out.add(code.isOk() ? code.value() : "");
            }
        }
        return out;
    }

    /**
     * Rebuilds slots from saved data, dropping anything malformed. Restored scenes are marked unchecked: the flash
     * clamp runs again before they play.
     */
    public static SceneSlots restore(List<String> codes, int active, long startTick, String trigger, int[] redstone,
            int[] daylight) {
        SceneSlots out = new SceneSlots();
        if (codes != null) {
            for (int i = 0; i < Math.min(codes.size(), MAX_SLOTS); i++) {
                String code = codes.get(i);
                if (code != null && !code.isEmpty()) {
                    ChromaResult<Scene> scene = SceneCodec.fromShareCode(code);
                    if (scene.isOk()) {
                        out.slots[i] = scene.value();
                    }
                }
            }
        }
        out.trigger = Trigger.byId(trigger);
        if (redstone != null && redstone.length == REDSTONE_LEVELS) {
            for (int i = 0; i < REDSTONE_LEVELS; i++) {
                out.redstoneMap[i] = sanitiseSlot(redstone[i]);
            }
        }
        if (daylight != null && daylight.length == DaylightBand.values().length) {
            for (int i = 0; i < daylight.length; i++) {
                out.daylightMap[i] = sanitiseSlot(daylight[i]);
            }
        }
        out.restoreActive(active, startTick);
        return out;
    }

    /** Whether this holds only defaults (nothing worth saving). */
    public boolean isDefault() {
        return filled() == 0 && active == STATIC && trigger == Trigger.MANUAL
                && Arrays.equals(redstoneMap, defaultRedstoneMap()) && Arrays.equals(daylightMap, defaultDaylightMap());
    }

    /** A deep copy (scenes are immutable). */
    public SceneSlots copy() {
        SceneSlots out = new SceneSlots();
        System.arraycopy(slots, 0, out.slots, 0, MAX_SLOTS);
        System.arraycopy(clampPassed, 0, out.clampPassed, 0, MAX_SLOTS);
        out.active = active;
        out.startTick = startTick;
        out.trigger = trigger;
        System.arraycopy(redstoneMap, 0, out.redstoneMap, 0, REDSTONE_LEVELS);
        System.arraycopy(daylightMap, 0, out.daylightMap, 0, daylightMap.length);
        out.revision = revision;
        return out;
    }
}
