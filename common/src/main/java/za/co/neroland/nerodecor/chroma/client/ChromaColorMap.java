package za.co.neroland.nerodecor.chroma.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's static Chroma colours by block position, held per 16x16x16 section so a change dirties only the
 * sections it touches. Read from the chunk-meshing threads by the tint bridge, written on the client thread; each
 * section is a plain {@code int[4096]} of opaque ARGB values (0 = no hub colour here, the block shows its own
 * {@code COLOR}). Pure Java.
 */
public final class ChromaColorMap {

    private static final int CELLS = 4096;

    private final Map<Long, int[]> sections = new ConcurrentHashMap<>();
    /** Live (non-zero) cells per section, so empty sections are dropped. */
    private final Map<Long, Integer> counts = new ConcurrentHashMap<>();

    /** Section key for block coordinates (same packing as Minecraft's {@code SectionPos.asLong}). */
    public static long sectionKey(int x, int y, int z) {
        return sectionKeyOf(x >> 4, y >> 4, z >> 4);
    }

    /** Section key for section coordinates. */
    public static long sectionKeyOf(int sx, int sy, int sz) {
        return ((long) sx & 0x3FFFFFL) << 42 | ((long) sy & 0xFFFFFL) | ((long) sz & 0x3FFFFFL) << 20;
    }

    public static int sectionX(long key) {
        return (int) (key << 0 >> 42);
    }

    public static int sectionY(long key) {
        return (int) (key << 44 >> 44);
    }

    public static int sectionZ(long key) {
        return (int) (key << 22 >> 42);
    }

    private static int cell(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    /** The hub colour at a block as opaque ARGB, or 0 when none. */
    public int argbAt(int x, int y, int z) {
        int[] section = sections.get(sectionKey(x, y, z));
        return section == null ? 0 : section[cell(x, y, z)];
    }

    /**
     * Sets (rgb >= 0) or clears (rgb < 0) the colour at a block.
     *
     * @return whether the stored value changed (the caller re-meshes the section)
     */
    public boolean set(int x, int y, int z, int rgb) {
        long key = sectionKey(x, y, z);
        int value = rgb < 0 ? 0 : 0xFF000000 | (rgb & 0xFFFFFF);
        int[] section = sections.get(key);
        if (section == null) {
            if (value == 0) {
                return false;
            }
            section = new int[CELLS];
            sections.put(key, section);
        }
        int c = cell(x, y, z);
        int before = section[c];
        if (before == value) {
            return false;
        }
        section[c] = value;
        int delta = (before == 0 ? 1 : 0) - (value == 0 ? 1 : 0);
        if (delta != 0) {
            int count = counts.getOrDefault(key, 0) + delta;
            if (count <= 0) {
                counts.remove(key);
                sections.remove(key);
            } else {
                counts.put(key, count);
            }
        }
        return true;
    }

    public int sectionCount() {
        return sections.size();
    }

    public void clear() {
        sections.clear();
        counts.clear();
    }
}
