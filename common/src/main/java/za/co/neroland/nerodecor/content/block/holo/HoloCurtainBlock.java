package za.co.neroland.nerodecor.content.block.holo;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/**
 * Holo curtain: an intangible sheet of light. {@code axis} is the horizontal axis the sheet runs along; the six
 * booleans say the same curtain (same axis) continues on that side, and only the four in the sheet's plane are set.
 */
public class HoloCurtainBlock extends BhcBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;

    public HoloCurtainBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(AXIS, Direction.Axis.X);
        for (BooleanProperty p : SIDES.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
        SIDES.values().forEach(builder::add);
    }

    private static boolean inPlane(BlockState state, Direction d) {
        return d.getAxis() == Direction.Axis.Y || d.getAxis() == state.getValue(AXIS);
    }

    private boolean joins(BlockState state, Direction d, BlockState other) {
        return inPlane(state, d) && other.is(this) && other.getValue(AXIS) == state.getValue(AXIS);
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(SIDES.get(d), joins(state, d, level.getBlockState(pos.relative(d))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
        return links(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        return state.setValue(SIDES.get(direction), joins(state, direction, neighbourState));
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        Direction turn = state.getValue(AXIS) == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
        return BhcShapes.horizontal(turn, BhcShapes.box(0, 0, 7.5, 16, 16, 8.5));
    }

    @Override
    protected VoxelShape computeCollision(BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            BlockState out = state.setValue(AXIS,
                    state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            for (Direction d : Direction.values()) {
                out = out.setValue(SIDES.get(rotation.rotate(d)), state.getValue(SIDES.get(d)));
            }
            return out;
        }
        return state;
    }
}
