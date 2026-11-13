package za.co.neroland.nerodecor.content.block.gantry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Floor grate slab: vanilla slab placement ({@code type} bottom / top / double, two slabs merge) plus four
 * horizontal side flags for the same slab of the same type, so a run of slabs drops the frame between neighbours.
 * Not waterloggable (the blockstate contract has no {@code waterlogged}). Cutout.
 */
public class FloorGrateSlabBlock extends ShapedBlock {

    private static final VoxelShape BOTTOM = ModelShapes.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape TOP = ModelShapes.box(0, 8, 0, 16, 16, 16);

    public FloorGrateSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM)
                .setValue(BlockStateProperties.NORTH, false).setValue(BlockStateProperties.EAST, false)
                .setValue(BlockStateProperties.SOUTH, false).setValue(BlockStateProperties.WEST, false));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.SLAB_TYPE, BlockStateProperties.NORTH, BlockStateProperties.EAST,
                BlockStateProperties.SOUTH, BlockStateProperties.WEST);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return switch (state.getValue(BlockStateProperties.SLAB_TYPE)) {
            case BOTTOM -> BOTTOM;
            case TOP -> TOP;
            case DOUBLE -> Shapes.block();
        };
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
    }

    private boolean linksTo(BlockState state, BlockState other) {
        return other.is(this)
                && other.getValue(BlockStateProperties.SLAB_TYPE) == state.getValue(BlockStateProperties.SLAB_TYPE);
    }

    private BlockState withLinks(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            state = state.setValue(ModelShapes.side(d), linksTo(state, level.getBlockState(pos.relative(d))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState existing = context.getLevel().getBlockState(pos);
        if (existing.is(this)) {
            return withLinks(existing.setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE), context.getLevel(), pos);
        }
        Direction face = context.getClickedFace();
        boolean top = face == Direction.DOWN
                || face != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5;
        return withLinks(defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM),
                context.getLevel(), pos);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        SlabType type = state.getValue(BlockStateProperties.SLAB_TYPE);
        if (type == SlabType.DOUBLE || !stack.is(this.asItem())) {
            return false;
        }
        if (!context.replacingClickedOnBlock()) {
            return true;
        }
        boolean above = context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
        Direction face = context.getClickedFace();
        return type == SlabType.BOTTOM
                ? face == Direction.UP || above && face.getAxis().isHorizontal()
                : face == Direction.DOWN || !above && face.getAxis().isHorizontal();
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        return direction.getAxis().isHorizontal()
                ? state.setValue(ModelShapes.side(direction), linksTo(state, neighbourState))
                : state;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbourState, Direction direction) {
        return direction.getAxis().isHorizontal() && linksTo(state, neighbourState)
                || super.skipRendering(state, neighbourState, direction);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return ModelShapes.rotateSides(state, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return ModelShapes.mirrorSides(state, mirror);
    }
}
