package za.co.neroland.nerodecor.content.block.gantry;

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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gantry ladder: {@code facing} as the vanilla ladder (the side it faces, rails against the opposite wall),
 * {@code pos_link} / {@code neg_link} for the same ladder (same facing) above / below, which drops the end brackets.
 * Climbable through {@code #minecraft:climbable}; needs no supporting wall. Cutout.
 */
public class GantryLadderBlock extends ShapedBlock {

    private static final VoxelShape NORTH = ModelShapes.box(2, 0, 13, 14, 16, 16);

    public GantryLadderBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(AxisLinkBlock.POS_LINK, false).setValue(AxisLinkBlock.NEG_LINK, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, AxisLinkBlock.POS_LINK, AxisLinkBlock.NEG_LINK);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return ModelShapes.rotate(NORTH, 0, ModelShapes.yRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    private boolean linksTo(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BlockState withLinks(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return state.setValue(AxisLinkBlock.POS_LINK, linksTo(level.getBlockState(pos.above()), facing))
                .setValue(AxisLinkBlock.NEG_LINK, linksTo(level.getBlockState(pos.below()), facing));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        // Placing against another ladder continues it.
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        if (!face.getAxis().isHorizontal() && below.is(this)) {
            facing = below.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return withLinks(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing),
                context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return state;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return state.setValue(direction == Direction.UP ? AxisLinkBlock.POS_LINK : AxisLinkBlock.NEG_LINK,
                linksTo(neighbourState, facing));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                mirror.mirror(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
}
