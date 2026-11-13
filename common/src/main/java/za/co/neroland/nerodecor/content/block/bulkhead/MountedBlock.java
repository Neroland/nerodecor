package za.co.neroland.nerodecor.content.block.bulkhead;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small part mounted on any face ({@code face} = the side it is attached to), authored on the floor. Door status
 * light, access pad; the holo projector extends it with a block entity.
 */
public class MountedBlock extends BhcBlock {

    private final double[][] boxes;

    public MountedBlock(Properties properties, double[]... boxes) {
        super(properties);
        this.boxes = boxes;
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACE, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BhcProperties.FACE, context.getClickedFace().getOpposite());
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.mounted(state.getValue(BhcProperties.FACE), boxes);
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
