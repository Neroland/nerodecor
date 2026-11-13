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
 * Catwalk: a 3px mesh deck at the top of the block (y 13..16, flush with floor tops) whose
 * {@code north} / {@code east} / {@code south} / {@code west} flags record the same block on that side, so a run
 * drops the frame between neighbours. Cutout.
 */
public class CatwalkBlock extends ShapedBlock {

    private static final VoxelShape DECK = ModelShapes.box(0, 13, 0, 16, 16, 16);

    public CatwalkBlock(Properties properties) {
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

    @Override
    protected VoxelShape shape(BlockState state) {
        return DECK;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            state = state.setValue(ModelShapes.side(d), level.getBlockState(pos.relative(d)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        return direction.getAxis().isHorizontal()
                ? state.setValue(ModelShapes.side(direction), neighbourState.is(this))
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
