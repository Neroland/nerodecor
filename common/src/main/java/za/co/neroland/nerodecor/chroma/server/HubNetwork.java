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
    private final List<Zone> zones = new ArrayList<>();
    /** packed relative pos -> (palette index & 0xFF) | (zone << 8). */
    private final Map<Integer, Integer> blocks = new HashMap<>();
    private ChromaPalette palette = new ChromaPalette();

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
        if (player == null) {
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

    void transferOwner(UUID newOwner) {
        this.owner = Objects.requireNonNull(newOwner);
        trusted.remove(newOwner);
    }
}
