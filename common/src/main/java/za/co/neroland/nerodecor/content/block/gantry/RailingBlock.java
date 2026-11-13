package za.co.neroland.nerodecor.content.block.gantry;

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

/**
 * Railing: fence-style arms ({@code north} / {@code east} / {@code south} / {@code west}) towards other railings or
 * sturdy faces. Collision is 1.5 blocks tall like a fence, so a catwalk edge cannot be hopped. Cutout.
 */
public class RailingBlock extends ShapedBlock {

    private static final VoxelShape POST = ModelShapes.box(6.5, 0, 6.5, 9.5, 16, 9.5);
    private static final VoxelShape POST_TALL = ModelShapes.box(6.5, 0, 6.5, 9.5, 24, 9.5);
    private static final VoxelShape ARM = ModelShapes.box(7, 0, 0, 9, 15, 8);
    private static final VoxelShape ARM_TALL = ModelShapes.box(7, 0, 0, 9, 24, 8);

    public RailingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.NORTH, false)
                .setValue(BlockStateProperties.EAST, false).setValue(BlockStateProperties.SOUTH, false)
                .setValue(BlockStateProperties.WEST, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
                BlockStateProperties.WEST);
    }

    private static VoxelShape build(BlockState state, VoxelShape post, VoxelShape arm) {
        VoxelShape s = post;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (state.getValue(ModelShapes.side(d))) {
                s = ModelShapes.or(s, ModelShapes.rotate(arm, 0, ModelShapes.yRot(d)));
            }
        }
        return s;
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return build(state, POST, ARM);
    }

    @Override
    protected VoxelShape collision(BlockState state) {
        return build(state, POST_TALL, ARM_TALL);
    }

    private boolean connects(BlockGetter level, BlockPos pos, Direction d) {
        BlockPos at = pos.relative(d);
        BlockState other = level.getBlockState(at);
        return other.is(this) || other.isFaceSturdy(level, at, d.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            state = state.setValue(ModelShapes.side(d), connects(context.getLevel(), context.getClickedPos(), d));
        }
        return state;
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        return direction.getAxis().isHorizontal()
                ? state.setValue(ModelShapes.side(direction), connects(level, pos, direction))
                : state;
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
