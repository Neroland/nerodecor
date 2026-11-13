package za.co.neroland.nerodecor.content.block.gantry;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A run along one axis ({@code axis} x / y / z) that links to the same block on the same axis at either end:
 * {@code pos_link} / {@code neg_link} as in {@code DecorPillarBlock}. The axis follows the clicked face. Used by
 * strut, truss_beam and duct; the shape comes from the caller.
 */
public class AxisLinkBlock extends ShapedBlock {

    public static final BooleanProperty POS_LINK = BooleanProperty.create("pos_link");
    public static final BooleanProperty NEG_LINK = BooleanProperty.create("neg_link");

    private final Function<BlockState, VoxelShape> shapes;

    public AxisLinkBlock(Properties properties, Function<BlockState, VoxelShape> shapes) {
        super(properties);
        this.shapes = shapes;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y)
                .setValue(POS_LINK, false).setValue(NEG_LINK, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.AXIS, POS_LINK, NEG_LINK);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return shapes.apply(state);
    }

    private boolean linksTo(BlockState other, Direction.Axis axis) {
        return other.is(this) && other.getValue(BlockStateProperties.AXIS) == axis;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getClickedFace().getAxis();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return defaultBlockState().setValue(BlockStateProperties.AXIS, axis)
                .setValue(POS_LINK, linksTo(level.getBlockState(pos.relative(positive)), axis))
                .setValue(NEG_LINK, linksTo(level.getBlockState(pos.relative(positive.getOpposite())), axis));
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        if (direction.getAxis() != axis) {
            return state;
        }
        BooleanProperty link = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? POS_LINK : NEG_LINK;
        return state.setValue(link, linksTo(neighbourState, axis));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        if (axis == Direction.Axis.Y) {
            return state;
        }
        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Direction turned = rotation.rotate(positive);
        return links(state.setValue(BlockStateProperties.AXIS, turned.getAxis()), state, turned);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        return links(state, state, mirror.mirror(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)));
    }

    /** Swaps the link flags when the old positive end now faces a negative direction. */
    static BlockState links(BlockState after, BlockState before, Direction newPositive) {
        if (newPositive.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return after;
        }
        return after.setValue(POS_LINK, before.getValue(NEG_LINK)).setValue(NEG_LINK, before.getValue(POS_LINK));
    }

    /** Strut: a 4px square post with 8px flanges (2px thick) at unlinked ends. */
    public static VoxelShape strutShape(BlockState state) {
        Direction.Axis a = state.getValue(BlockStateProperties.AXIS);
        VoxelShape s = ModelShapes.along(a, 0, 16, 6, 10);
        if (!state.getValue(NEG_LINK)) {
            s = ModelShapes.or(s, ModelShapes.along(a, 0, 2, 4, 12));
        }
        if (!state.getValue(POS_LINK)) {
            s = ModelShapes.or(s, ModelShapes.along(a, 14, 16, 4, 12));
        }
        return s;
    }

    /** Truss beam: a 12px square lattice. */
    public static VoxelShape trussShape(BlockState state) {
        return ModelShapes.along(state.getValue(BlockStateProperties.AXIS), 0, 16, 2, 14);
    }

    /** Full cube (duct). */
    public static VoxelShape fullShape(BlockState state) {
        return ModelShapes.box(0, 0, 0, 16, 16, 16);
    }
}
