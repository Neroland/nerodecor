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

/** Patch panel: a wall-mounted horizontal run; {@code left} / {@code right} = same panel, same facing (viewer's). */
public class PatchPanelBlock extends FacingBlock {

    public PatchPanelBlock(Properties properties) {
        super(properties, BhcShapes.box(0, 0, 13, 16, 16, 16));
        registerDefaultState(defaultBlockState().setValue(BhcProperties.LEFT, false).setValue(BhcProperties.RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BhcProperties.LEFT, BhcProperties.RIGHT);
    }

    private boolean joins(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(BhcProperties.FACING) == state.getValue(BhcProperties.FACING);
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction f = state.getValue(BhcProperties.FACING);
        return state.setValue(BhcProperties.LEFT, joins(state, level.getBlockState(pos.relative(BhcProperties.leftOf(f)))))
                .setValue(BhcProperties.RIGHT, joins(state, level.getBlockState(pos.relative(BhcProperties.rightOf(f)))));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState state = clicked.getAxis().isHorizontal()
                ? defaultBlockState().setValue(BhcProperties.FACING, clicked) : super.getStateForPlacement(context);
        return links(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        return direction.getAxis().isHorizontal() ? links(state, level, pos) : state;
    }
}
