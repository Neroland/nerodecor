package za.co.neroland.nerodecor.chroma;

/** Typed failure reasons for decoding and validation. Messages never carry player data. */
public enum ChromaError {
    BAD_MAGIC,
    UNSUPPORTED_VERSION,
    TRUNCATED,
    OVERSIZED,
    BAD_CRC,
    OUT_OF_RANGE,
    TRAILING_DATA,
    BAD_NAME,
    BAD_SHARE_CODE,
    TOO_MANY_OPS,
    BAD_OP
}
