package za.co.neroland.nerodecor.client.ctm;

/**
 * The eight in-plane neighbours of a block face, each flagged {@code true} when it
 * <i>connects</i> (same CTM family + colour, presents a face on the same side, not
 * occluded). Purely data — no Minecraft types — so the tile-selection logic in
 * {@link CtmSolver} is deterministic and unit-testable in isolation.
 *
 * <p>Orientation is relative to the face as seen head-on: {@code up} is toward the top of
 * the rendered quad, {@code left} toward its left, and the diagonals accordingly.
 */
public record Neighbourhood(boolean up, boolean down, boolean left, boolean right,
                            boolean upLeft, boolean upRight, boolean downLeft, boolean downRight) {

    /** A face with no connected neighbours (a lone block) — all borders drawn. */
    public static final Neighbourhood ISOLATED =
            new Neighbourhood(false, false, false, false, false, false, false, false);

    /** A fully-surrounded face (interior of a large wall) — no borders. */
    public static final Neighbourhood SURROUNDED =
            new Neighbourhood(true, true, true, true, true, true, true, true);

    /**
     * Build from an 8-bit mask in the fixed order
     * {@code up, down, left, right, upLeft, upRight, downLeft, downRight} (bit 0 = up).
     * Used by the exhaustive self-check and tests.
     */
    public static Neighbourhood ofMask(int mask) {
        return new Neighbourhood(
                (mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0,
                (mask & 16) != 0, (mask & 32) != 0, (mask & 64) != 0, (mask & 128) != 0);
    }
}
