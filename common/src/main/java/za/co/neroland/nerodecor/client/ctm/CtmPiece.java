package za.co.neroland.nerodecor.client.ctm;

/**
 * The five sub-tiles a quadrant can draw in the corner-based connected-texture method.
 * The {@code gen_textures} pipeline emits exactly these per finish (a {@code base} fill
 * plus the border/corner sprites); the renderer picks one per {@link Quadrant} and
 * rotates it into place, so a large same-colour surface reads as one seamless sheet.
 *
 * <p>This 5-piece corner method is mathematically equivalent to the classic 47-tile
 * connected set: enumerating all 256 neighbourhoods yields exactly 47 distinct
 * whole-face appearances (asserted by {@link CtmSolver#distinctFaceCount()}).
 */
public enum CtmPiece {
    /** Interior fill — this quadrant is fully surrounded, no border drawn. */
    FILL,
    /** A straight border along the quadrant's horizontal (top/bottom) edge. */
    EDGE_HORIZONTAL,
    /** A straight border along the quadrant's vertical (left/right) edge. */
    EDGE_VERTICAL,
    /** Both edges are borders — an outer (convex) corner. */
    OUTER_CORNER,
    /** Both edges connect but the diagonal does not — an inner (concave) corner notch. */
    INNER_CORNER
}
