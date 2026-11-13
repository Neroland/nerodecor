package za.co.neroland.nerodecor.chroma;

/**
 * One edit operation, as the editor and network will send it. {@code target} is a packed
 * {@link RelativePos} for block ops and a palette index for {@link Kind#SET_PALETTE_ENTRY}.
 */
public record ChromaOp(Kind kind, int target, int value) {

    /** Op kinds. Ids are stable wire values: append only. */
    public enum Kind {
        /** Set a block's palette index: target = packed position, value = index 0-255. */
        SET_INDEX,
        /** Set a palette entry: target = index 0-255, value = packed colour. */
        SET_PALETTE_ENTRY,
        /** Assign a block to a zone: target = packed position, value = zone 0-31. */
        ASSIGN_ZONE;

        private static final Kind[] VALUES = values();

        public int id() {
            return ordinal();
        }

        public static Kind byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }
    }

    public static ChromaOp setIndex(int packedPos, int paletteIndex) {
        return new ChromaOp(Kind.SET_INDEX, packedPos, paletteIndex);
    }

    public static ChromaOp setPaletteEntry(int paletteIndex, ChromaColor color) {
        return new ChromaOp(Kind.SET_PALETTE_ENTRY, paletteIndex, color.packed());
    }

    public static ChromaOp assignZone(int packedPos, int zone) {
        return new ChromaOp(Kind.ASSIGN_ZONE, packedPos, zone);
    }

    /** Whether every field is within its range (kind-specific). */
    public boolean isValid() {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case SET_INDEX -> RelativePos.isValid(target) && value >= 0 && value < ChromaPalette.MAX_ENTRIES;
            case SET_PALETTE_ENTRY -> target >= 0 && target < ChromaPalette.MAX_ENTRIES
                    && ChromaColor.isValidPacked(value);
            case ASSIGN_ZONE -> RelativePos.isValid(target) && value >= 0 && value < ZoneAssignment.MAX_ZONES;
        };
    }
}
