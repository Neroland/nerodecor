package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Roller shutter: a curtain between two guide rails. A vertical stack (same axis) shares one roller housing at the top
 * and opens and closes as one; the bottom block of the stack is the controller.
 */
public class ShutterBlock extends AnimatedDoorBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final int DURATION = 16;
    private static final int MAX_STACK = 32;

    public ShutterBlock(Properties properties, Supplier<? extends BlockEntityType<DoorAnimBlockEntity>> type) {
        super(properties, type, DURATION, SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(OPEN, false)
                .setValue(BhcProperties.POS_LINK, false).setValue(BhcProperties.NEG_LINK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN, BhcProperties.POS_LINK, BhcProperties.NEG_LINK);
    }

    private boolean stacks(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(AXIS) == state.getValue(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState()
                .setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
        BlockState below = level.getBlockState(pos.below());
        if (stacks(state, below)) {
            state = state.setValue(OPEN, below.getValue(OPEN));
        }
        return state.setValue(BhcProperties.POS_LINK, stacks(state, level.getBlockState(pos.above())))
                .setValue(BhcProperties.NEG_LINK, stacks(state, below));
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        if (direction == Direction.UP) {
            return state.setValue(BhcProperties.POS_LINK, stacks(state, neighbourState));
        }
        if (direction == Direction.DOWN) {
            return state.setValue(BhcProperties.NEG_LINK, stacks(state, neighbourState));
        }
        return state;
    }

    @Override
    protected List<BlockPos> members(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos bottom = controller(level, pos, state);
        List<BlockPos> out = new ArrayList<>();
        BlockPos p = bottom;
        for (int i = 0; i < MAX_STACK && stacks(state, level.getBlockState(p)); i++) {
            out.add(p);
            p = p.above();
        }
        return out;
    }

    private BlockPos controller(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos p = pos;
        for (int i = 0; i < MAX_STACK && stacks(state, level.getBlockState(p.below())); i++) {
            p = p.below();
        }
        return p;
    }

    @Override
    protected BlockPos controller(BlockPos pos, BlockState state) {
        return pos;
    }

    @Override
    public void toggle(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, boolean open) {
        BlockPos bottom = controller(level, pos, state);
        BlockState bottomState = level.getBlockState(bottom);
        if (bottom.equals(pos) || !bottomState.is(this)) {
            super.toggle(level, pos, state, open);
        } else {
            super.toggle(level, bottom, bottomState, open);
        }
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        boolean top = !state.getValue(BhcProperties.POS_LINK);
        double h = top ? 12 : 16;
        List<double[]> boxes = new ArrayList<>();
        boxes.add(BhcShapes.box(0, 0, 6, 1, h, 10));
        boxes.add(BhcShapes.box(15, 0, 6, 16, h, 10));
        if (top) {
            boxes.add(BhcShapes.box(0, 12, 5, 16, 16, 11));
        }
        if (!state.getValue(OPEN)) {
            boxes.add(BhcShapes.box(1, 0, 7, 15, h, 9));
        }
        Direction turn = state.getValue(AXIS) == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
        return BhcShapes.horizontal(turn, boxes.toArray(new double[0][]));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
