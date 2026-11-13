package za.co.neroland.nerodecor.chroma;

/**
 * One hub's static colour layout as the server sends it to tracking clients: the palette plus the linked blocks
 * (hub-relative {@link RelativePos}-packed offsets with their palette index). {@code replace} says the client drops
 * what it held for this network first (the first part of a split snapshot); later parts merge.
 *
 * <p>Carries no player data: a network id, positions and colours only.
 */
public record ChromaSnapshot(long network, boolean replace, int anchorX, int anchorY, int anchorZ, int[] palette,
        int[] offsets, int[] indices) {

    public ChromaSnapshot {
        if (offsets.length != indices.length) {
            throw new IllegalArgumentException("offsets/indices length mismatch");
        }
    }

    public int size() {
        return offsets.length;
    }
}
