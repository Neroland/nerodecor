package za.co.neroland.nerodecor.content.block.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/**
 * Command console: runs side by side ({@code left} / {@code right}: same console, same facing, on the viewer's
 * left / right) and turns corners like stairs. {@code inner} (operator inside) when the console in front of this one
 * faces this one's counter-clockwise turn; {@code outer} when the console behind faces its clockwise turn. The
 * mirrored corners have no model of their own and stay straight.
 */
public class CommandConsoleBlock extends FacingBlock {

    public static final EnumProperty<ConsoleShape> SHAPE = EnumProperty.create("shape", ConsoleShape.class);

    public CommandConsoleBlock(Properties properties) {
        super(properties, BhcShapes.box(0, 0, 0, 16, 16, 16));
        registerDefaultState(defaultBlockState().setValue(SHAPE, ConsoleShape.STRAIGHT)
                .setValue(BhcProperties.LEFT, false).setValue(BhcProperties.RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE, BhcProperties.LEFT, BhcProperties.RIGHT);
    }

    private boolean facing(BlockState other, Direction d) {
        return other.is(this) && other.getValue(BhcProperties.FACING) == d;
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction f = state.getValue(BhcProperties.FACING);
        ConsoleShape shape = ConsoleShape.STRAIGHT;
        if (facing(level.getBlockState(pos.relative(f)), f.getCounterClockWise())) {
            shape = ConsoleShape.INNER;
        } else if (facing(level.getBlockState(pos.relative(f.getOpposite())), f.getClockWise())) {
            shape = ConsoleShape.OUTER;
        }
        return state.setValue(SHAPE, shape)
                .setValue(BhcProperties.LEFT, facing(level.getBlockState(pos.relative(BhcProperties.leftOf(f))), f))
                .setValue(BhcProperties.RIGHT, facing(level.getBlockState(pos.relative(BhcProperties.rightOf(f))), f));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return links(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        return direction.getAxis().isHorizontal() ? links(state, level, pos) : state;
    }
}
