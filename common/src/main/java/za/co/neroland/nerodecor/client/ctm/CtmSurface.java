package za.co.neroland.nerodecor.client.ctm;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by any NeroDecor block that participates in connected textures. The
 * renderer discovers CTM blocks by {@code instanceof CtmSurface}, reads their
 * {@link #ctmKey(BlockState)} to decide connectivity, and picks sub-tiles via
 * {@link CtmSolver}. Keeping this a tiny interface (no rendering types) lets it live in
 * {@code common/} and be implemented by plain block classes; the per-loader render
 * binding (Stage E) does the quad work.
 */
public interface CtmSurface {

    /** The connection identity for this state — same family + painted colour connect. */
    CtmKey ctmKey(BlockState state);

    /** Which connected set this surface uses. Defaults to the full 2-D method. */
    default CtmStyle ctmStyle() {
        return CtmStyle.FULL;
    }
}
