package za.co.neroland.nerodecor.client.ctm;

import java.util.HashSet;
import java.util.Set;

/**
 * The deterministic heart of NeroDecor's in-house connected textures — a pure function
 * from a face's {@link Neighbourhood} to the sub-tile drawn in each {@link Quadrant}. No
 * Minecraft types, no third-party dependency, no per-frame allocation on the hot path:
 * the renderer calls {@link #piece(Quadrant, Neighbourhood)} four times per face.
 *
 * <p><b>Corner method.</b> Each quadrant looks only at the two edges and the one diagonal
 * that touch it. With both edges connected the quadrant is interior ({@link CtmPiece#FILL})
 * unless the diagonal is missing (an {@link CtmPiece#INNER_CORNER} notch); with one edge
 * connected it draws that axis's border; with neither it draws an
 * {@link CtmPiece#OUTER_CORNER}. Enumerating all 256 neighbourhoods yields exactly 47
 * distinct whole-face appearances — the classic "47-tile" connected set
 * ({@link #distinctFaceCount()} asserts this).
 */
public final class CtmSolver {

    private CtmSolver() {
    }

    /** The sub-tile for one quadrant of a face with the given neighbour connectivity. */
    public static CtmPiece piece(Quadrant quadrant, Neighbourhood n) {
        boolean edgeH;   // the horizontal (top/bottom) edge neighbour touching this quadrant
        boolean edgeV;   // the vertical (left/right) edge neighbour touching this quadrant
        boolean diagonal;
        switch (quadrant) {
            case TOP_LEFT -> {
                edgeH = n.up();
                edgeV = n.left();
                diagonal = n.upLeft();
            }
            case TOP_RIGHT -> {
                edgeH = n.up();
                edgeV = n.right();
                diagonal = n.upRight();
            }
            case BOTTOM_LEFT -> {
                edgeH = n.down();
                edgeV = n.left();
                diagonal = n.downLeft();
            }
            case BOTTOM_RIGHT -> {
                edgeH = n.down();
                edgeV = n.right();
                diagonal = n.downRight();
            }
            default -> throw new IllegalStateException("Unknown quadrant: " + quadrant);
        }
        if (edgeH && edgeV) {
            return diagonal ? CtmPiece.FILL : CtmPiece.INNER_CORNER;
        }
        if (edgeH) {
            return CtmPiece.EDGE_VERTICAL;    // vertical edge is open -> border runs vertically
        }
        if (edgeV) {
            return CtmPiece.EDGE_HORIZONTAL;  // horizontal edge is open -> border runs horizontally
        }
        return CtmPiece.OUTER_CORNER;
    }

    /** The four quadrant pieces of a face, in {@link Quadrant} declaration order. */
    public static CtmPiece[] face(Neighbourhood n) {
        Quadrant[] quadrants = Quadrant.values();
        CtmPiece[] pieces = new CtmPiece[quadrants.length];
        for (int i = 0; i < quadrants.length; i++) {
            pieces[i] = piece(quadrants[i], n);
        }
        return pieces;
    }

    /** The one-dimensional strip state from its two along-axis neighbours. */
    public static StripConnection strip(boolean lowConnected, boolean highConnected) {
        if (lowConnected && highConnected) {
            return StripConnection.MIDDLE;
        }
        if (lowConnected) {
            return StripConnection.END;
        }
        if (highConnected) {
            return StripConnection.START;
        }
        return StripConnection.SINGLE;
    }

    /**
     * The number of visually distinct whole-face appearances over all 256 neighbourhoods.
     * For this corner method the value is 47 — the classic connected-textures tile count.
     * Exposed so a smoke-test can assert the invariant without a Minecraft runtime.
     */
    public static int distinctFaceCount() {
        Set<String> distinct = new HashSet<>();
        for (int mask = 0; mask < 256; mask++) {
            CtmPiece[] pieces = face(Neighbourhood.ofMask(mask));
            distinct.add(pieces[0].name() + '|' + pieces[1].name() + '|'
                    + pieces[2].name() + '|' + pieces[3].name());
        }
        return distinct.size();
    }
}
