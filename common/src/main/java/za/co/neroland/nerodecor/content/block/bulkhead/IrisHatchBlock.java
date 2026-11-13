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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Iris hatch: a 3 px rim plate on any face with an octagonal opening; six blades (block-entity drawn while moving)
 * spiral open or shut. Open, the opening is walkable.
 */
public class IrisHatchBlock extends AnimatedDoorBlock {

    public static final int DURATION = 12;

    public IrisHatchBlock(Properties properties, Supplier<? extends BlockEntityType<DoorAnimBlockEntity>> type) {
        super(properties, type, DURATION, SoundEvents.PISTON_EXTEND, SoundEvents.PISTON_CONTRACT);
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACE, Direction.DOWN).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACE, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BhcProperties.FACE, context.getClickedFace().getOpposite());
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
        Direction face = state.getValue(BhcProperties.FACE);
        if (!state.getValue(OPEN)) {
            return BhcShapes.mounted(face, BhcShapes.box(0, 0, 0, 16, 3, 16));
        }
        return BhcShapes.mounted(face, BhcShapes.box(0, 0, 0, 16, 3, 3), BhcShapes.box(0, 0, 13, 16, 3, 16),
                BhcShapes.box(0, 0, 3, 3, 3, 13), BhcShapes.box(13, 0, 3, 16, 3, 13));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BhcProperties.FACE, rotation.rotate(state.getValue(BhcProperties.FACE)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BhcProperties.FACE, mirror.mirror(state.getValue(BhcProperties.FACE)));
    }
}
