package za.co.neroland.nerodecor.client.ctm;

/**
 * The simpler one-dimensional connected set used for strips/trim (neon bars, edge trim),
 * where a piece only connects along a single axis. Four states cover every case, so
 * {@code gen_textures} emits four sprites per strip finish.
 */
public enum StripConnection {
    /** Neither side connects — a standalone piece with both caps. */
    SINGLE,
    /** Only the "low" side connects — a start cap on the high side. */
    START,
    /** Both sides connect — a seamless middle segment. */
    MIDDLE,
    /** Only the "high" side connects — an end cap on the low side. */
    END
}
