package za.co.neroland.nerodecor.content.block.bulkhead;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Canopy glass: a 45-degree glazed roof pane. {@code facing} is the low edge, {@code half} flips it under a ceiling,
 * and {@code pos_link} / {@code neg_link} join panes along the ridge (world +/- along the axis across {@code facing}).
 */
public class CanopyGlassBlock extends BhcBlock {

    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    public CanopyGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM).setValue(BhcProperties.POS_LINK, false)
                .setValue(BhcProperties.NEG_LINK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACING, HALF, BhcProperties.POS_LINK, BhcProperties.NEG_LINK);
    }

    private static Direction positive(BlockState state) {
        Direction.Axis ridge = state.getValue(BhcProperties.FACING).getClockWise().getAxis();
        return Direction.fromAxisAndDirection(ridge, Direction.AxisDirection.POSITIVE);
    }

    private boolean joins(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(BhcProperties.FACING) == state.getValue(BhcProperties.FACING)
                && other.getValue(HALF) == state.getValue(HALF);
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction p = positive(state);
        return state.setValue(BhcProperties.POS_LINK, joins(state, level.getBlockState(pos.relative(p))))
                .setValue(BhcProperties.NEG_LINK, joins(state, level.getBlockState(pos.relative(p.getOpposite()))));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        double y = context.getClickLocation().y - context.getClickedPos().getY();
        Half half = clicked == Direction.DOWN || (clicked != Direction.UP && y > 0.5) ? Half.TOP : Half.BOTTOM;
        BlockState state = defaultBlockState().setValue(BhcProperties.FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, half);
        return links(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateLinks(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState) {
        Direction p = positive(state);
        if (direction == p) {
            return state.setValue(BhcProperties.POS_LINK, joins(state, neighbourState));
        }
        if (direction == p.getOpposite()) {
            return state.setValue(BhcProperties.NEG_LINK, joins(state, neighbourState));
        }
        return state;
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        Direction facing = state.getValue(BhcProperties.FACING);
        if (state.getValue(HALF) == Half.TOP) {
            return BhcShapes.horizontal(facing, BhcShapes.box(0, 8, 0, 16, 16, 16), BhcShapes.box(0, 0, 0, 16, 8, 8));
        }
        return BhcShapes.horizontal(facing, BhcShapes.box(0, 0, 0, 16, 8, 16), BhcShapes.box(0, 8, 8, 16, 16, 16));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BhcProperties.FACING, rotation.rotate(state.getValue(BhcProperties.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BhcProperties.FACING)));
    }
}
