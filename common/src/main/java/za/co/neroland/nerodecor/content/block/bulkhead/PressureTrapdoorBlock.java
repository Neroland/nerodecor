package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pressure trapdoor: vanilla trapdoor layout and placement; the hatch plate swings on its hinge (BE-drawn). */
public class PressureTrapdoorBlock extends AnimatedDoorBlock {

    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final int DURATION = 8;

    public PressureTrapdoorBlock(Properties properties, Supplier<? extends BlockEntityType<DoorAnimBlockEntity>> type) {
        super(properties, type, DURATION, SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE);
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACING, HALF, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState state = defaultBlockState();
        if (!context.replacingClickedOnBlock() && clicked.getAxis().isHorizontal()) {
            double y = context.getClickLocation().y - context.getClickedPos().getY();
            return state.setValue(BhcProperties.FACING, clicked).setValue(HALF, y > 0.5 ? Half.TOP : Half.BOTTOM);
        }
        return state.setValue(BhcProperties.FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, clicked == Direction.UP ? Half.BOTTOM : Half.TOP);
    }

    @Override
    protected List<BlockPos> members(BlockGetter level, BlockPos pos, BlockState state) {
        return List.of(pos);
    }

    @Override
    protected BlockPos controller(BlockPos pos, BlockState state) {
        return pos;
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        if (state.getValue(OPEN)) {
            return BhcShapes.horizontal(state.getValue(BhcProperties.FACING), BhcShapes.box(0, 0, 13, 16, 16, 16));
        }
        return state.getValue(HALF) == Half.TOP ? BhcShapes.of(BhcShapes.box(0, 13, 0, 16, 16, 16))
                : BhcShapes.of(BhcShapes.box(0, 0, 0, 16, 3, 16));
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
