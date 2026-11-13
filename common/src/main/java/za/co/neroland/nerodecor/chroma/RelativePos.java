package za.co.neroland.nerodecor.chroma;

/**
 * Hub-relative block offsets packed into one int: three signed 10-bit fields (x high, y, z low),
 * each in {@code [-512, 511]}. Bits 30-31 are always zero in a valid packed value.
 */
public final class RelativePos {

    public static final int MIN = -512;
    public static final int MAX = 511;
    private static final int BITS = 10;
    private static final int MASK = (1 << BITS) - 1;

    private RelativePos() {
    }

    public static boolean inRange(int dx, int dy, int dz) {
        return dx >= MIN && dx <= MAX && dy >= MIN && dy <= MAX && dz >= MIN && dz <= MAX;
    }

    /** @throws IllegalArgumentException when an offset is out of range */
    public static int pack(int dx, int dy, int dz) {
        if (!inRange(dx, dy, dz)) {
            throw new IllegalArgumentException("offset out of range");
        }
        return (dx & MASK) << (2 * BITS) | (dy & MASK) << BITS | dz & MASK;
    }

    public static boolean isValid(int packed) {
        return (packed >>> (3 * BITS)) == 0;
    }

    public static int x(int packed) {
        return signExtend(packed >>> (2 * BITS));
    }

    public static int y(int packed) {
        return signExtend(packed >>> BITS);
    }

    public static int z(int packed) {
        return signExtend(packed);
    }

    private static int signExtend(int field) {
        return (field & MASK) << (32 - BITS) >> (32 - BITS);
    }
}
