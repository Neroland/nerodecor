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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Floor grate: a full cutout cube with six side flags ({@code mask16} connected faces). The face shared with another
 * grate is skipped, and light passes through like glass.
 */
public class FloorGrateBlock extends ShapedBlock {

    public FloorGrateBlock(Properties properties) {
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
        return Shapes.block();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            state = state.setValue(ModelShapes.side(d), level.getBlockState(pos.relative(d)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        return state.setValue(ModelShapes.side(direction), neighbourState.is(this));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbourState, Direction direction) {
        return neighbourState.is(this) || super.skipRendering(state, neighbourState, direction);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    //? if >=1.21.2 {
    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
    //?} else {
    /*@Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
    *///?}

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
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
