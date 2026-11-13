package za.co.neroland.nerodecor.content.block.ancients;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.lights.CollectionBlock;
import za.co.neroland.nerodecor.content.block.lights.ModelShapes;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Arch lintel: a corbelled half-arch whose face looks {@code facing}. The run lies across the facing; {@code shape}
 * follows the neighbours along it: {@code mid} between two lintels, {@code left} / {@code right} at a free end, the
 * corbel stepping down to the support on the viewer's left / right (looking at the face). A lone lintel keeps the
 * shape it had (placed: {@code left}).
 */
public class ArchLintelBlock extends CollectionBlock {

    public enum LintelShape implements StringRepresentable {
        LEFT, MID, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<LintelShape> SHAPE = EnumProperty.create("shape", LintelShape.class);

    private static final double[][] LEFT = {b(0, 10, 0, 16, 16, 16), b(0, 8, 2, 4, 10, 14), b(4, 7, 0, 16, 10, 16),
        b(8, 4, 0, 16, 7, 16), b(12, 0, 0, 16, 4, 16)};
    private static final double[][] RIGHT = mirrorX(LEFT);
    private static final double[][] MID = {b(0, 10, 0, 16, 16, 16), b(0, 8, 2, 16, 10, 14)};

    public ArchLintelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, LintelShape.LEFT));
    }

    private static double[][] mirrorX(double[][] boxes) {
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            double[] q = boxes[i];
            out[i] = b(16 - q[3], q[1], q[2], 16 - q[0], q[4], q[5]);
        }
        return out;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        double[][] boxes = switch (state.getValue(SHAPE)) {
            case LEFT -> LEFT;
            case MID -> MID;
            case RIGHT -> RIGHT;
        };
        return ModelShapes.boxes(ModelShapes.horizontal(state.getValue(FACING)), boxes);
    }

    private BlockState shaped(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getClockWise();          // the model's +x end, the viewer's left
        boolean l = joins(level.getBlockState(pos.relative(left)), facing);
        boolean r = joins(level.getBlockState(pos.relative(left.getOpposite())), facing);
        if (l && r) {
            return state.setValue(SHAPE, LintelShape.MID);
        }
        if (r) {
            return state.setValue(SHAPE, LintelShape.LEFT);
        }
        if (l) {
            return state.setValue(SHAPE, LintelShape.RIGHT);
        }
        return state.getValue(SHAPE) == LintelShape.MID ? state.setValue(SHAPE, LintelShape.LEFT) : state;
    }

    private boolean joins(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(FACING) == facing;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return shaped(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState refresh(BlockState state, LevelReader level, BlockPos pos, Direction dir, BlockState neighbour) {
        return shaped(state, level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        LintelShape s = state.getValue(SHAPE);
        return s == LintelShape.MID ? out
                : out.setValue(SHAPE, s == LintelShape.LEFT ? LintelShape.RIGHT : LintelShape.LEFT);
    }
}
