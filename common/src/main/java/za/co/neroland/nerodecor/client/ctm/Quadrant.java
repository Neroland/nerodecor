package za.co.neroland.nerodecor.client.ctm;

/**
 * The four quadrants a block face is split into for the corner-based connected-texture
 * method. Each quadrant's appearance is decided independently from the connectivity of
 * the two edges and the one diagonal that touch it (see {@link CtmSolver}).
 */
public enum Quadrant {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}
