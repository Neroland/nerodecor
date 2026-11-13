package za.co.neroland.nerodecor.content.block.lights;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.VoxelShape;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Edge trim: a 3x3 lit moulding hugging the wall {@code facing} at the floor ({@code half=bottom}) or ceiling
 * ({@code top}). {@code shape} works like stairs, with one handedness: {@code inner} / {@code outer} are the corner
 * between {@code facing} and the wall counter-clockwise of it. Straight trims set {@code pos_link} / {@code neg_link}
 * when the run continues at +axis / -axis (the axis along the wall).
 *
 * <p>Corners need a perpendicular trim: a straight trim turns {@code inner} when the trim in front of it runs along
 * the counter-clockwise wall, {@code outer} when the trim behind it does. On placement the clockwise cases are taken
 * by turning the corner's facing, so either wall's trim can be placed first.
 */
public class EdgeTrimBlock extends CollectionBlock {

    public enum TrimShape implements StringRepresentable {
        STRAIGHT, INNER, OUTER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<TrimShape> SHAPE = EnumProperty.create("shape", TrimShape.class);
    public static final BooleanProperty POS_LINK = MountedStripBlock.POS_LINK;
    public static final BooleanProperty NEG_LINK = MountedStripBlock.NEG_LINK;

    private static final double[] RUN = b(0, 0, 0, 16, 3, 3);
    private static final double[] LEG = b(0, 0, 3, 3, 3, 16);
    private static final double[] POST = b(0, 0, 0, 3, 3, 3);

    public EdgeTrimBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM)
                .setValue(SHAPE, TrimShape.STRAIGHT).setValue(POS_LINK, false).setValue(NEG_LINK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SHAPE, POS_LINK, NEG_LINK);
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        int ry = ModelShapes.horizontal(state.getValue(FACING)).y();
        TrimShape shape = state.getValue(SHAPE);
        boolean top = state.getValue(HALF) == Half.TOP;
        int rx = top ? 180 : 0;
        int y = top ? ry + (shape == TrimShape.STRAIGHT ? 180 : 90) : ry;
        ModelShapes.Rot rot = new ModelShapes.Rot(rx, y % 360);
        return switch (shape) {
            case STRAIGHT -> ModelShapes.boxes(rot, RUN);
            case INNER -> ModelShapes.boxes(rot, RUN, LEG);
            case OUTER -> ModelShapes.boxes(rot, POST);
        };
    }

    private boolean trim(BlockState other, Direction facing, Half half) {
        return other.is(this) && other.getValue(HALF) == half && other.getValue(FACING) == facing;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked.getOpposite() : context.getHorizontalDirection();
        Half half = clicked == Direction.DOWN
                || (clicked != Direction.UP && context.getClickLocation().y() - context.getClickedPos().getY() > 0.5)
                ? Half.TOP : Half.BOTTOM;
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(FACING, facing).setValue(HALF, half);
        Direction ccw = facing.getCounterClockWise();
        Direction cw = facing.getClockWise();
        BlockState front = level.getBlockState(pos.relative(facing.getOpposite()));
        BlockState back = level.getBlockState(pos.relative(facing));
        if (trim(front, ccw, half)) {
            state = state.setValue(SHAPE, TrimShape.INNER);
        } else if (trim(front, cw, half)) {
            state = state.setValue(FACING, cw).setValue(SHAPE, TrimShape.INNER);
        } else if (trim(back, ccw, half)) {
            state = state.setValue(SHAPE, TrimShape.OUTER);
        } else if (trim(back, cw, half)) {
            state = state.setValue(FACING, cw).setValue(SHAPE, TrimShape.OUTER);
        }
        return links(state, level, pos);
    }

    /** Corner upkeep and the straight run's links, from the current facing only (stable under repeat updates). */
    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction x = state.getValue(FACING);
        Half half = state.getValue(HALF);
        Direction ccw = x.getCounterClockWise();
        Direction cw = x.getClockWise();
        boolean innerA = trim(level.getBlockState(pos.relative(x.getOpposite())), ccw, half);
        boolean innerB = trim(level.getBlockState(pos.relative(cw)), x, half);
        boolean outerC = trim(level.getBlockState(pos.relative(x)), ccw, half);
        boolean outerD = trim(level.getBlockState(pos.relative(ccw)), x, half);
        TrimShape shape = state.getValue(SHAPE);
        if (innerA || (shape == TrimShape.INNER && innerB)) {
            shape = TrimShape.INNER;
        } else if (outerC || (shape == TrimShape.OUTER && outerD)) {
            shape = TrimShape.OUTER;
        } else {
            shape = TrimShape.STRAIGHT;
        }
        boolean pos1 = false;
        boolean neg1 = false;
        if (shape == TrimShape.STRAIGHT) {
            Direction plus = Direction.fromAxisAndDirection(cw.getAxis(), Direction.AxisDirection.POSITIVE);
            pos1 = continues(level.getBlockState(pos.relative(plus)), x, half);
            neg1 = continues(level.getBlockState(pos.relative(plus.getOpposite())), x, half);
        }
        return state.setValue(SHAPE, shape).setValue(POS_LINK, pos1).setValue(NEG_LINK, neg1);
    }

    /** The run continues into a trim on the same wall, or into any corner piece at the same height. */
    private boolean continues(BlockState other, Direction facing, Half half) {
        return other.is(this) && other.getValue(HALF) == half
                && (other.getValue(FACING) == facing || other.getValue(SHAPE) != TrimShape.STRAIGHT);
    }

    @Override
    protected BlockState refresh(BlockState state, LevelReader level, BlockPos pos, Direction dir, BlockState neighbour) {
        return links(state, level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
