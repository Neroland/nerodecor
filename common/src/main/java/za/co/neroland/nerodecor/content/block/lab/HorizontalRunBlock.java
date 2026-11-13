package za.co.neroland.nerodecor.content.block.lab;

import java.util.function.Function;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.AxisLinkBlock;
import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * A horizontal furniture run ({@code axis} x / z) that links to the same block on the same axis at either end
 * ({@code pos_link} / {@code neg_link}), so end pieces are drawn only on unlinked ends. The run is placed across the
 * placer's view, so the front faces them. Used by lab_bench; {@link PlanterBlock} adds the plant.
 */
public class HorizontalRunBlock extends ShapedBlock {

    private final Function<BlockState, VoxelShape> shapes;

    public HorizontalRunBlock(Properties properties, Function<BlockState, VoxelShape> shapes) {
        super(properties);
        this.shapes = shapes;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X)
                .setValue(AxisLinkBlock.POS_LINK, false).setValue(AxisLinkBlock.NEG_LINK, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_AXIS, AxisLinkBlock.POS_LINK, AxisLinkBlock.NEG_LINK);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return shapes.apply(state);
    }

    protected boolean linksTo(BlockState other, Direction.Axis axis) {
        return other.is(this) && other.getValue(BlockStateProperties.HORIZONTAL_AXIS) == axis;
    }

    protected BlockState withLinks(BlockState state, BlockGetter level, BlockPos pos) {
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return state.setValue(AxisLinkBlock.POS_LINK, linksTo(level.getBlockState(pos.relative(positive)), axis))
                .setValue(AxisLinkBlock.NEG_LINK, linksTo(level.getBlockState(pos.relative(positive.getOpposite())), axis));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getHorizontalDirection().getClockWise().getAxis();
        return withLinks(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, axis),
                context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        if (direction.getAxis() != axis) {
            return state;
        }
        BooleanProperty link = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? AxisLinkBlock.POS_LINK : AxisLinkBlock.NEG_LINK;
        return state.setValue(link, linksTo(neighbourState, axis));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        Direction turned = rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
        BlockState out = state.setValue(BlockStateProperties.HORIZONTAL_AXIS, turned.getAxis());
        return swap(out, state, turned);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction.Axis axis = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
        return swap(state, state, mirror.mirror(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)));
    }

    private static BlockState swap(BlockState after, BlockState before, Direction newPositive) {
        if (newPositive.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return after;
        }
        return after.setValue(AxisLinkBlock.POS_LINK, before.getValue(AxisLinkBlock.NEG_LINK))
                .setValue(AxisLinkBlock.NEG_LINK, before.getValue(AxisLinkBlock.POS_LINK));
    }

    /** Lab bench: worktop + apron (y 10..16), lower shelf, legs only on unlinked ends (drawn on x, z turned y=90). */
    public static VoxelShape benchShape(BlockState state) {
        VoxelShape s = ModelShapes.or(ModelShapes.box(0, 10, 0, 16, 16, 16), ModelShapes.box(0, 3, 2, 16, 5, 14));
        if (!state.getValue(AxisLinkBlock.POS_LINK)) {
            s = ModelShapes.or(s, ModelShapes.box(13, 0, 1, 15, 10, 15));
        }
        if (!state.getValue(AxisLinkBlock.NEG_LINK)) {
            s = ModelShapes.or(s, ModelShapes.box(1, 0, 1, 3, 10, 15));
        }
        return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.Z ? ModelShapes.rotate(s, 0, 90) : s;
    }

    /** Planter: an 8px trough, 12px wide (drawn on x, z turned y=90). */
    public static VoxelShape planterShape(BlockState state) {
        VoxelShape s = ModelShapes.box(0, 0, 2, 16, 8, 14);
        return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.Z ? ModelShapes.rotate(s, 0, 90) : s;
    }
}
