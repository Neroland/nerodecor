package za.co.neroland.nerodecor.content.block.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/** Server / drive rack: stacks vertically into one cabinet ({@code up} / {@code down} = same rack, same facing). */
public class RackBlock extends FacingBlock {

    public RackBlock(Properties properties) {
        super(properties, BhcShapes.box(0, 0, 0, 16, 16, 16));
        registerDefaultState(defaultBlockState().setValue(BhcProperties.UP, false).setValue(BhcProperties.DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BhcProperties.UP, BhcProperties.DOWN);
    }

    private boolean joins(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(BhcProperties.FACING) == state.getValue(BhcProperties.FACING);
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(BhcProperties.UP, joins(state, level.getBlockState(pos.above())))
                .setValue(BhcProperties.DOWN, joins(state, level.getBlockState(pos.below())));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return links(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        return direction.getAxis() == Direction.Axis.Y ? links(state, level, pos) : state;
    }
}
