package za.co.neroland.nerodecor.chroma.server;

/**
 * Absolute block positions packed into a {@code long}, bit-compatible with Minecraft's {@code BlockPos.asLong}
 * (26-bit X, 26-bit Z, 12-bit Y) so the pure network code and the game layer agree without importing Minecraft.
 */
public final class ChromaPos {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final int Z_OFFSET = Y_BITS;
    private static final int X_OFFSET = Y_BITS + XZ_BITS;

    private ChromaPos() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & XZ_MASK) << X_OFFSET | ((long) y & Y_MASK) | ((long) z & XZ_MASK) << Z_OFFSET;
    }

    public static int x(long packed) {
        return (int) (packed << (64 - X_OFFSET - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static int y(long packed) {
        return (int) (packed << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long packed) {
        return (int) (packed << (64 - Z_OFFSET - XZ_BITS) >> (64 - XZ_BITS));
    }
}
