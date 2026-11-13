package za.co.neroland.nerodecor.content.block.conduits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * decor_pipe / coolant_tube: six side flags ({@code north} ... {@code down}, "connected on that side"). Both share
 * one 6px bore, so they join each other, and they join a valve or gauge at either end of its axis. Decorative only:
 * nothing flows.
 */
public class ConduitPipeBlock extends ShapedBlock {

    private static final VoxelShape CORE = ModelShapes.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape ARM_NORTH = ModelShapes.box(5, 5, 0, 11, 11, 5);
    private static final VoxelShape LONE = ModelShapes.box(2, 4, 4, 14, 12, 12);

    public ConduitPipeBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            state = state.setValue(ModelShapes.side(d), false);
        }
        registerDefaultState(state);
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
                BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        VoxelShape s = CORE;
        boolean any = false;
        for (Direction d : Direction.values()) {
            if (state.getValue(ModelShapes.side(d))) {
                s = ModelShapes.or(s, ModelShapes.facing(ARM_NORTH, d));
                any = true;
            }
        }
        return any ? s : LONE;
    }

    /** Whether a conduit pipe joins {@code other} on {@code side}. */
    public static boolean joins(BlockState other, Direction side) {
        if (other.getBlock() instanceof ConduitPipeBlock) {
            return true;
        }
        return other.getBlock() instanceof InlineFittingBlock
                && other.getValue(BlockStateProperties.AXIS) == side.getAxis();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            state = state.setValue(ModelShapes.side(d), joins(level.getBlockState(pos.relative(d)), d));
        }
        return state;
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        return state.setValue(ModelShapes.side(direction), joins(neighbourState, direction));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return ModelShapes.rotateSides(state, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return ModelShapes.mirrorSides(state, mirror);
    }
}
