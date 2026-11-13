package za.co.neroland.nerodecor.content.block.gantry;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Catwalk stairs: four 4px treads rising towards {@code facing} (the high end, as vanilla stairs). Collision is the
 * solid staircase under the treads, so it climbs like stairs. Cutout.
 */
public class CatwalkStairsBlock extends ShapedBlock {

    private static final VoxelShape NORTH = ModelShapes.or(
            ModelShapes.box(0, 0, 12, 16, 4, 16),
            ModelShapes.box(0, 0, 8, 16, 8, 12),
            ModelShapes.box(0, 0, 4, 16, 12, 8),
            ModelShapes.box(0, 0, 0, 16, 16, 4));

    public CatwalkStairsBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return ModelShapes.rotate(NORTH, 0, ModelShapes.yRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                mirror.mirror(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
}
