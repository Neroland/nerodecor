package za.co.neroland.nerodecor.chroma;

/**
 * An incremental change to one hub's static layout: palette entries that changed (index and packed colour) and
 * blocks that changed (packed hub-relative offset and new palette index, or {@link #UNLINKED}). {@code clear} means
 * the network no longer colours anything (hub removed, network deleted): the client drops it.
 */
public record ChromaDelta(long network, boolean clear, int[] paletteIndices, int[] paletteColors, int[] offsets,
        int[] indices) {

    /** Index value for a block that left the network. */
    public static final int UNLINKED = -1;

    public ChromaDelta {
        if (paletteIndices.length != paletteColors.length || offsets.length != indices.length) {
            throw new IllegalArgumentException("delta array length mismatch");
        }
    }

    public static ChromaDelta clear(long network) {
        return new ChromaDelta(network, true, new int[0], new int[0], new int[0], new int[0]);
    }

    public boolean isEmpty() {
        return !clear && paletteIndices.length == 0 && offsets.length == 0;
    }
}
