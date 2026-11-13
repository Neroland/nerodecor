package za.co.neroland.nerodecor.content.block.lights;

import org.jetbrains.annotations.Nullable;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The kit's strip convention: a thin bar mounted on {@code face} of its own block space (down = lying on the
 * floor), running along {@code axis} (in the plane of the face), with {@code pos_link} / {@code neg_link} set when
 * the next block along +axis / -axis is the same block on the same face and axis (the model drops the end cap
 * there). Used by the light strip, glyph strip and hazard stripe.
 */
public class MountedStripBlock extends CollectionBlock {

    public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty POS_LINK = BooleanProperty.create("pos_link");
    public static final BooleanProperty NEG_LINK = BooleanProperty.create("neg_link");

    private final double[][] alongX;
    private final double[][] alongZ;

    /**
     * @param alongX canonical boxes of the strip on DOWN running along x (the module's {@code <id>_c11} model)
     * @param alongZ canonical boxes of the strip on DOWN running along z (the {@code _z} model)
     */
    public MountedStripBlock(Properties properties, double[][] alongX, double[][] alongZ) {
        super(properties);
        this.alongX = alongX;
        this.alongZ = alongZ;
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(AXIS, Direction.Axis.X)
                .setValue(POS_LINK, false).setValue(NEG_LINK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, AXIS, POS_LINK, NEG_LINK);
    }

    /** Canonical boxes for a state; the hazard stripe swaps them by {@code edge}. */
    protected double[][] canonical(BlockState state, boolean z) {
        return z ? alongZ : alongX;
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        Direction face = state.getValue(FACE);
        Direction.Axis axis = state.getValue(AXIS);
        if (!ModelShapes.inPlane(face, axis)) {
            axis = ModelShapes.cross(face, face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y);
        }
        ModelShapes.StripRot r = ModelShapes.strip(face, axis);
        return ModelShapes.boxes(r.rot(), canonical(state, r.z()));
    }

    /** Whether {@code other} continues this strip run. */
    protected boolean linksTo(BlockState other, Direction face, Direction.Axis axis) {
        return other.is(this) && other.getValue(FACE) == face && other.getValue(AXIS) == axis;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        BlockPos pos = context.getClickedPos();
        BlockGetter level = context.getLevel();
        Direction.Axis axis = null;
        for (Direction.Axis a : Direction.Axis.values()) {
            if (a == face.getAxis()) {
                continue;
            }
            for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
                BlockState n = level.getBlockState(pos.relative(Direction.fromAxisAndDirection(a, sign)));
                if (n.is(this) && n.getValue(FACE) == face && n.getValue(AXIS) == a) {
                    axis = a;
                }
            }
        }
        if (axis == null) {
            if (face.getAxis() == Direction.Axis.Y) {
                axis = context.getHorizontalDirection().getAxis();
            } else if (context.getNearestLookingDirection().getAxis() == Direction.Axis.Y) {
                axis = Direction.Axis.Y;
            } else {
                axis = ModelShapes.cross(face, Direction.Axis.Y);
            }
        }
        BlockState state = defaultBlockState().setValue(FACE, face).setValue(AXIS, axis);
        return links(state, level, pos);
    }

    /** Sets {@code pos_link} / {@code neg_link} from the two neighbours along the axis. */
    protected BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction face = state.getValue(FACE);
        Direction.Axis axis = state.getValue(AXIS);
        Direction plus = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return state.setValue(POS_LINK, linksTo(level.getBlockState(pos.relative(plus)), face, axis))
                .setValue(NEG_LINK, linksTo(level.getBlockState(pos.relative(plus.getOpposite())), face, axis));
    }

    @Override
    protected BlockState refresh(BlockState state, LevelReader level, BlockPos pos, Direction dir, BlockState neighbour) {
        return links(state, level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        if (axis != Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        }
        return state.setValue(FACE, rotation.rotate(state.getValue(FACE))).setValue(AXIS, axis);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACE, mirror.mirror(state.getValue(FACE)));
    }
}
