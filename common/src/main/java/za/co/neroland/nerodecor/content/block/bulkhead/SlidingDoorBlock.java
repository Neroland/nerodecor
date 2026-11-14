package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sliding door: 1x2, the leaf slides sideways into a pocket jamb on the hinge side. Placed and broken as one (like
 * vanilla doors); a door placed next to one with the same facing takes the opposite hinge, and the pair opens together.
 * Both halves hold a {@link DoorAnimBlockEntity} so each renders its own leaf; the lower half is the controller.
 */
public class SlidingDoorBlock extends AnimatedDoorBlock {

    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final int DURATION = 10;

    public SlidingDoorBlock(Properties properties, Supplier<? extends BlockEntityType<DoorAnimBlockEntity>> type) {
        super(properties, type, DURATION, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_DOOR_CLOSE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(HINGE, DoorHingeSide.LEFT).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, HINGE, OPEN);
    }

    /** The side the pocket jamb is on (canonical hinge=left, facing north: west). */
    public static Direction jambSide(BlockState state) {
        Direction f = state.getValue(FACING);
        return state.getValue(HINGE) == DoorHingeSide.LEFT ? f.getCounterClockWise() : f.getClockWise();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos up = pos.above();
        if (level.isOutsideBuildHeight(up) || !level.getBlockState(up).canBeReplaced(context)) {
            return null;
        }
        Direction facing = context.getHorizontalDirection();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        BlockState left = level.getBlockState(pos.relative(facing.getCounterClockWise()));
        boolean pairRight = left.is(this) && left.getValue(FACING) == facing
                && left.getValue(HINGE) == DoorHingeSide.LEFT && left.getValue(HALF) == DoubleBlockHalf.LOWER;
        return state.setValue(HINGE, pairRight ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (direction.getAxis() == Direction.Axis.Y && lower == (direction == Direction.UP)) {
            boolean partner = neighbourState.is(this) && neighbourState.getValue(HALF) != state.getValue(HALF);
            return partner ? state.setValue(OPEN, neighbourState.getValue(OPEN)) : Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected List<BlockPos> members(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos lower = controller(pos, state);
        return List.of(lower, lower.above());
    }

    @Override
    protected BlockPos controller(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Override
    protected void afterToggle(Level level, BlockPos pos, BlockState state, boolean open) {
        BlockPos lower = controller(pos, state);
        BlockPos other = lower.relative(jambSide(state).getOpposite());
        BlockState partner = level.getBlockState(other);
        if (partner.is(this) && partner.getValue(FACING) == state.getValue(FACING)
                && partner.getValue(HINGE) != state.getValue(HINGE)
                && partner.getValue(HALF) == DoubleBlockHalf.LOWER
                && jambSide(partner) == jambSide(state).getOpposite()) {
            toggleSelf(level, other, partner, open);
        }
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean right = state.getValue(HINGE) == DoorHingeSide.RIGHT;
        double[] frame = lower ? BhcShapes.box(0, 0, 5, 16, 1, 11) : BhcShapes.box(0, 14, 5, 16, 16, 11);
        double[] body = state.getValue(OPEN) ? BhcShapes.box(0, 0, 5, 4, 16, 11) : BhcShapes.box(0, 0, 5, 16, 16, 11);
        if (right) {
            body = new double[] {16 - body[3], body[1], body[2], 16 - body[0], body[4], body[5]};
        }
        return BhcShapes.horizontal(state.getValue(FACING), frame, body);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state
                : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(HINGE);
    }
}
