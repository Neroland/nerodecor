package za.co.neroland.nerodecor.content.block.command;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcBlock;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.BhcShapes;

/**
 * A command-deck fitting with a horizontal {@code facing}: the front looks that way (at the player who placed it) and
 * the outline is authored facing north. Subclasses add link booleans.
 */
public class FacingBlock extends BhcBlock {

    private final double[][] boxes;

    public FacingBlock(Properties properties, double[]... boxes) {
        super(properties);
        this.boxes = boxes;
        registerDefaultState(stateDefinition.any().setValue(BhcProperties.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BhcProperties.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BhcProperties.FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape computeShape(BlockState state) {
        return BhcShapes.horizontal(state.getValue(BhcProperties.FACING), boxes);
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
