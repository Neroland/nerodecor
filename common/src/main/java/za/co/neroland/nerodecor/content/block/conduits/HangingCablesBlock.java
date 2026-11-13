package za.co.neroland.nerodecor.content.block.conduits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.AxisLinkBlock;
import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * Hanging cables: crossed cutout planes, {@code pos_link} (same block above) / {@code neg_link} (below) so a drop
 * reads as one bundle. No collision; walk through it like vines.
 */
public class HangingCablesBlock extends ShapedBlock {

    private static final VoxelShape OUTLINE = ModelShapes.box(2, 0, 2, 14, 16, 14);

    public HangingCablesBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AxisLinkBlock.POS_LINK, false)
                .setValue(AxisLinkBlock.NEG_LINK, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AxisLinkBlock.POS_LINK, AxisLinkBlock.NEG_LINK);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape collision(BlockState state) {
        return Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(AxisLinkBlock.POS_LINK, context.getLevel().getBlockState(pos.above()).is(this))
                .setValue(AxisLinkBlock.NEG_LINK, context.getLevel().getBlockState(pos.below()).is(this));
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return state;
        }
        return state.setValue(direction == Direction.UP ? AxisLinkBlock.POS_LINK : AxisLinkBlock.NEG_LINK,
                neighbourState.is(this));
    }
}
