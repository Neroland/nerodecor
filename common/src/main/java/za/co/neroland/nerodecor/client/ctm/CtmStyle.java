package za.co.neroland.nerodecor.client.ctm;

/**
 * How a surface connects — selects which sprite set and solver path the renderer uses.
 */
public enum CtmStyle {
    /** Full 2-D connected surface (hull, panels): the 47-tile corner method. */
    FULL,
    /** One-dimensional strip (neon bars, trim): the 4-state {@link StripConnection} method. */
    STRIP,
    /** Translucent connected glass: like {@link #FULL} but on the cutout/translucent layer. */
    GLASS
}
