package za.co.neroland.nerodecor.content.block.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/**
 * Ticker strip: a face-mounted scrolling text bar, laid out like the kit's strip blocks: {@code face} (the side it is
 * mounted on), {@code axis} (the run, never the face's own axis), {@code pos_link} / {@code neg_link} (the same strip
 * continues at world +/- along the axis). The scroll is an animated texture; nothing ticks.
 */
public class TickerStripBlock extends BhcBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public TickerStripBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACE, Direction.DOWN)
                .setValue(AXIS, Direction.Axis.X).setValue(BhcProperties.POS_LINK, false)
                .setValue(BhcProperties.NEG_LINK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACE, AXIS, BhcProperties.POS_LINK, BhcProperties.NEG_LINK);
    }

    private boolean joins(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(BhcProperties.FACE) == state.getValue(BhcProperties.FACE)
                && other.getValue(AXIS) == state.getValue(AXIS);
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction pos0 = Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
        return state.setValue(BhcProperties.POS_LINK, joins(state, level.getBlockState(pos.relative(pos0))))
                .setValue(BhcProperties.NEG_LINK, joins(state, level.getBlockState(pos.relative(pos0.getOpposite()))));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        Direction.Axis axis = face.getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection().getClockWise().getAxis()
                : face.getClockWise().getAxis();
        BlockState state = defaultBlockState().setValue(BhcProperties.FACE, face).setValue(AXIS, axis);
        return links(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        if (direction.getAxis() != state.getValue(AXIS)) {
            return state;
        }
        return state.setValue(direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? BhcProperties.POS_LINK : BhcProperties.NEG_LINK, joins(state, neighbourState));
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        Direction face = state.getValue(BhcProperties.FACE);
        Direction.Axis run = state.getValue(AXIS);
        double[] lo = new double[3];
        double[] hi = new double[3];
        for (Direction.Axis a : Direction.Axis.values()) {
            int i = a.ordinal();
            if (a == run) {
                lo[i] = 0;
                hi[i] = 16;
            } else if (a == face.getAxis()) {
                boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                lo[i] = positive ? 15 : 0;
                hi[i] = positive ? 16 : 1;
            } else {
                lo[i] = 6;
                hi[i] = 10;
            }
        }
        return BhcShapes.of(BhcShapes.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
    }
}
