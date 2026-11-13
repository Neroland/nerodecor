package za.co.neroland.nerodecor.content.block.gantry;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.2 {
import net.minecraft.world.level.ScheduledTickAccess;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;
*///?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Base for the lab_habitat, gantry and conduits blocks: model-shaped outline and collision shapes baked once per
 * state (call {@link #bakeShapes()} at the end of the subclass constructor), plus one version-neutral neighbour
 * hook in place of {@code updateShape}, whose signature changed in 1.21.2. No block entity, no ticking.
 */
public abstract class ShapedBlock extends Block {

    private Map<BlockState, VoxelShape> outlines = Map.of();
    private Map<BlockState, VoxelShape> collisions = Map.of();

    protected ShapedBlock(Properties properties) {
        super(properties);
    }

    /** Outline (selection) shape of a state, in block units. */
    protected abstract VoxelShape shape(BlockState state);

    /** Collision shape of a state; the outline unless overridden. */
    protected VoxelShape collision(BlockState state) {
        return shape(state);
    }

    /** Bakes every state's shapes. Call last in the subclass constructor, once its fields are set. */
    protected final void bakeShapes() {
        Map<BlockState, VoxelShape> o = new IdentityHashMap<>();
        Map<BlockState, VoxelShape> c = new IdentityHashMap<>();
        for (BlockState state : getStateDefinition().getPossibleStates()) {
            o.put(state, shape(state).optimize());
            c.put(state, collision(state).optimize());
        }
        outlines = o;
        collisions = c;
    }

    /**
     * Called when the neighbour on {@code direction} changes shape; returns the new state. Default: unchanged.
     */
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        return state;
    }

    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return onNeighbourShape(state, level, pos, direction, neighbourPos, neighbourState);
    }
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return onNeighbourShape(state, level, pos, direction, neighbourPos, neighbourState);
    }
    *///?}

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = outlines.get(state);
        return s != null ? s : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = collisions.get(state);
        return s != null ? s : Shapes.block();
    }
}
