package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.2 {
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.redstone.Orientation;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;
*///?}
//? if >=1.21.5 {
import net.minecraft.server.level.ServerLevel;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared base for the bulkhead, holo and command collections: it irons out the block-method signatures that moved
 * between 1.21.1 and 26.x, so each block overrides one stable hook instead of carrying its own {@code //?} blocks.
 *
 * <ul>
 *   <li>{@link #updateLinks}: neighbour-shape updates (the 1.21.2 {@code updateShape} reorder).</li>
 *   <li>{@link #onNeighbourChanged}: redstone / neighbour notifications (1.21.2 added {@code Orientation}).</li>
 *   <li>{@link #onRemovedFromWorld}: the block was replaced by a different block, server side (multiblock clean-up;
 *       {@code onRemove} before 1.21.5, {@code affectNeighborsAfterRemoval} from 1.21.5).</li>
 *   <li>{@link #computeShape}: the outline (and, unless {@link #computeCollision} is overridden, the collision) shape,
 *       cached per state.</li>
 * </ul>
 */
public abstract class BhcBlock extends Block {

    protected BhcBlock(Properties properties) {
        super(properties);
    }

    private final Map<BlockState, VoxelShape> shapes = new ConcurrentHashMap<>();
    private final Map<BlockState, VoxelShape> collisions = new ConcurrentHashMap<>();

    /** Outline shape for {@code state} (computed once per state); full cube unless overridden. */
    protected VoxelShape computeShape(BlockState state) {
        return Shapes.block();
    }

    /** Collision shape (computed once per state); the outline shape unless overridden. */
    protected VoxelShape computeCollision(BlockState state) {
        return shape(state);
    }

    protected final VoxelShape shape(BlockState state) {
        return shapes.computeIfAbsent(state, this::computeShape);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collisions.computeIfAbsent(state, this::computeCollision);
    }

    /** A neighbour's state changed on {@code direction}; return this block's new state (link booleans etc.). */
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        return state;
    }

    /** A neighbour changed (redstone and the like). Server and client; most implementations check the side. */
    protected void onNeighbourChanged(BlockState state, Level level, BlockPos pos) {
    }

    /** This block was replaced by a different block (server side only). */
    protected void onRemovedFromWorld(BlockState state, Level level, BlockPos pos) {
    }

    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return updateLinks(state, level, pos, direction, neighbourPos, neighbourState);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        onNeighbourChanged(state, level, pos);
    }
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return updateLinks(state, level, pos, direction, neighbourPos, neighbourState);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean movedByPiston) {
        onNeighbourChanged(state, level, pos);
    }
    *///?}

    //? if >=1.21.5 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        onRemovedFromWorld(state, level, pos);
    }
    //?} else {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean replaced = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (replaced && !level.isClientSide()) {
            onRemovedFromWorld(state, level, pos);
        }
    }
    *///?}

    /** Whether any of {@code positions} receives a redstone signal. */
    protected static boolean powered(Level level, BlockPos... positions) {
        for (BlockPos p : positions) {
            if (level.hasNeighborSignal(p)) {
                return true;
            }
        }
        return false;
    }

    /** Silently clears {@code pos} (no drops, neighbours notified) when it still holds {@code expected}'s block. */
    protected static void clearSilently(Level level, BlockPos pos, Block expected) {
        if (level.getBlockState(pos).is(expected)) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }
}
