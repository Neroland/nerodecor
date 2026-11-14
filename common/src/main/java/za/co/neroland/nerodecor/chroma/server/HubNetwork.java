package za.co.neroland.nerodecor.chroma.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import za.co.neroland.nerodecor.chroma.ChromaPalette;
import za.co.neroland.nerodecor.chroma.RelativePos;
import za.co.neroland.nerodecor.chroma.SceneSlots;

/**
 * One hub's network, pure Java: who owns and may use it, where its volume is, its zones, and the blocks it
 * controls. Blocks are stored relative to the hub (the anchor) as {@link RelativePos}-packed ints mapped to a
 * palette index and a zone id, so a hub item that is re-placed elsewhere carries its layout with it.
 *
 * <p>Personal data is limited to the owner UUID and the trusted UUIDs with their roles — never names. Instances are
 * mutated only through {@link ChromaNetworkStore}, which keeps the claim index in step.
 */
public final class HubNetwork {

    /** One named zone and the effect channel it listens on (0-15). */
    public record Zone(String name, int channel) {
        public Zone {
            Objects.requireNonNull(name);
        }
    }

    public static final String DEFAULT_ZONE = "Default";

    /**
     * Owner of an orphaned network (its owner's data was erased). Never stored: the saved form omits the owner field,
     * and {@link #roleOf(UUID)} grants nobody anything on an orphaned network, so this sentinel is never a real role.
     */
    public static final UUID NO_OWNER = new UUID(0L, 0L);

    private final long id;
    private UUID owner;
    private final Map<UUID, ChromaRole> trusted = new LinkedHashMap<>();
    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private boolean active;
    private boolean autoAdopt;
    private long inactiveSince;
    /** Epoch millis the owner's data was erased (0 = owned). Orphaned networks are deleted after a retention period. */
    private long orphanedSince;
    private final List<Zone> zones = new ArrayList<>();
    /** packed relative pos -> (palette index & 0xFF) | (zone << 8). */
    private final Map<Integer, Integer> blocks = new HashMap<>();
    private ChromaPalette palette = new ChromaPalette();
    /** Stage 4: stored scenes, the playing slot and its trigger. */
    private SceneSlots scenes = new SceneSlots();

    public HubNetwork(long id, UUID owner, int sizeX, int sizeY, int sizeZ) {
        if (id == 0L) {
            throw new IllegalArgumentException("network id 0 is reserved");
        }
        this.id = id;
        this.owner = Objects.requireNonNull(owner);
        this.sizeX = ChromaLimits.clamp(sizeX, 1, ChromaLimits.HARD_VOLUME_CAP);
        this.sizeY = ChromaLimits.clamp(sizeY, 1, ChromaLimits.HARD_VOLUME_CAP);
        this.sizeZ = ChromaLimits.clamp(sizeZ, 1, ChromaLimits.HARD_VOLUME_CAP);
        this.zones.add(new Zone(DEFAULT_ZONE, 0));
    }

    public long id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    /** The role {@code player} holds here; {@link ChromaRole#NONE} for strangers and {@code null}. */
    public ChromaRole roleOf(UUID player) {
        if (player == null || orphaned()) {
            return ChromaRole.NONE;
        }
        if (player.equals(owner)) {
            return ChromaRole.OWNER;
        }
        return trusted.getOrDefault(player, ChromaRole.NONE);
    }

    public Map<UUID, ChromaRole> trusted() {
        return Collections.unmodifiableMap(trusted);
    }

    void setTrust(UUID player, ChromaRole role) {
        if (role == null || role == ChromaRole.NONE) {
            trusted.remove(player);
        } else if (role.isGrantable() && !player.equals(owner)) {
            trusted.put(player, role);
        }
    }

    boolean forget(UUID player) {
        return trusted.remove(player) != null;
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    void setAnchor(int x, int y, int z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    void setSize(int x, int y, int z) {
        this.sizeX = x;
        this.sizeY = y;
        this.sizeZ = z;
    }

    /** Lowest offset on an axis of {@code size} blocks centred on the hub (48 -> -24 .. 23). */
    public static int minOffset(int size) {
        return -(size / 2);
    }

    /** Highest offset on an axis of {@code size} blocks centred on the hub. */
    public static int maxOffset(int size) {
        return size - 1 - size / 2;
    }

    /** Whether the hub-relative offset lies inside this network's volume. */
    public boolean containsOffset(int dx, int dy, int dz) {
        return dx >= minOffset(sizeX) && dx <= maxOffset(sizeX)
                && dy >= minOffset(sizeY) && dy <= maxOffset(sizeY)
                && dz >= minOffset(sizeZ) && dz <= maxOffset(sizeZ);
    }

    /** Whether the absolute position lies inside this network's volume around its current anchor. */
    public boolean contains(int x, int y, int z) {
        return containsOffset(x - anchorX, y - anchorY, z - anchorZ);
    }

    public boolean active() {
        return active;
    }

    void setActive(boolean active, long now) {
        this.active = active;
        this.inactiveSince = active ? 0L : now;
    }

    public long inactiveSince() {
        return inactiveSince;
    }

    void restoreInactiveSince(long since) {
        this.inactiveSince = since;
    }

    public boolean autoAdopt() {
        return autoAdopt;
    }

    void setAutoAdopt(boolean on) {
        this.autoAdopt = on;
    }

    public List<Zone> zones() {
        return Collections.unmodifiableList(zones);
    }

    void restoreZones(List<Zone> restored) {
        zones.clear();
        zones.addAll(restored);
        if (zones.isEmpty()) {
            zones.add(new Zone(DEFAULT_ZONE, 0));
        }
    }

    void addZone(Zone zone) {
        zones.add(zone);
    }

    public ChromaPalette palette() {
        return palette;
    }

    void restorePalette(ChromaPalette restored) {
        this.palette = restored;
    }

    /** Linked block count. */
    public int size() {
        return blocks.size();
    }

    public boolean isLinkedOffset(int packedRelative) {
        return blocks.containsKey(packedRelative);
    }

    /** Palette index of a linked block, or -1. */
    public int paletteIndexAt(int packedRelative) {
        Integer value = blocks.get(packedRelative);
        return value == null ? -1 : value & 0xFF;
    }

    /** Zone id of a linked block, or -1. */
    public int zoneAt(int packedRelative) {
        Integer value = blocks.get(packedRelative);
        return value == null ? -1 : value >>> 8;
    }

    /** Snapshot of packed relative positions (stable copy). */
    public List<Integer> linkedOffsets() {
        return new ArrayList<>(blocks.keySet());
    }

    void put(int packedRelative, int paletteIndex, int zone) {
        blocks.put(packedRelative, (paletteIndex & 0xFF) | (zone << 8));
    }

    boolean remove(int packedRelative) {
        return blocks.remove(packedRelative) != null;
    }

    /** The absolute position of a linked offset around the current anchor. */
    public long absolute(int packedRelative) {
        return ChromaPos.pack(anchorX + RelativePos.x(packedRelative), anchorY + RelativePos.y(packedRelative),
                anchorZ + RelativePos.z(packedRelative));
    }

    /** Raw (palette | zone << 8) value, for the codec. */
    Integer rawAt(int packedRelative) {
        return blocks.get(packedRelative);
    }

    // --- erasure / orphaning (POPIA + GDPR) ------------------------------------------------------------------------

    /** Whether the owner's data has been erased: the colours stay, but nobody may edit until an operator claims it. */
    public boolean orphaned() {
        return orphanedSince > 0L;
    }

    /** Epoch millis the network was orphaned, or 0. */
    public long orphanedSince() {
        return orphanedSince;
    }

    void restoreOrphanedSince(long since) {
        this.orphanedSince = Math.max(0L, since);
        if (orphanedSince > 0L) {
            this.owner = NO_OWNER;
        }
    }

    /**
     * The owner's data was erased: the owner is cleared, every trusted entry goes (data minimisation: nobody may use
     * the network any more, so the list has no purpose), auto-adopt stops (it acts for the owner), and every free-text
     * name a player typed is replaced with a neutral default. Linked blocks, palette and zone channels stay, so the
     * build keeps its colours; they are decor, not personal data.
     */
    void orphan(long now) {
        this.owner = NO_OWNER;
        this.orphanedSince = Math.max(1L, now);
        this.trusted.clear();
        this.autoAdopt = false;
        blankNames();
    }

    /** Replaces every player-typed name (zones; scene slots join here) with a neutral default. */
    void blankNames() {
        for (int i = 0; i < zones.size(); i++) {
            zones.set(i, new Zone(blankZoneName(i), zones.get(i).channel()));
        }
        scenes.blankNames(); // scene names become "Scene <n>"; zones and programs stay
    }

    /** The neutral name zone {@code index} gets when its owner is erased ("Default", "Zone 2", ...). */
    public static String blankZoneName(int index) {
        return index == 0 ? DEFAULT_ZONE : "Zone " + (index + 1);
    }

    /** An operator claims an orphaned network for {@code newOwner}. */
    void claimOrphan(UUID newOwner) {
        this.owner = Objects.requireNonNull(newOwner);
        this.orphanedSince = 0L;
        trusted.remove(newOwner);
    }

    // --- scenes (Stage 4) -------------------------------------------------------------------------------------------

    /** The hub's scene slots. Mutate only through {@link ChromaScenes} (it checks roles and the flash clamp). */
    public SceneSlots scenes() {
        return scenes;
    }

    /** Replaces the scene slots (saved-data restore). */
    void replaceScenes(SceneSlots restored) {
        this.scenes = restored == null ? new SceneSlots() : restored;
    }

    void transferOwner(UUID newOwner) {
        this.owner = Objects.requireNonNull(newOwner);
        trusted.remove(newOwner);
    }
}
