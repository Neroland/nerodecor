package za.co.neroland.nerodecor.content.block.conduits;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * A six-way {@code facing} prop drawn facing north (duct_fan: the fan face; beam_emitter_prop: the lens).
 * {@code mounted} props face away from the clicked surface; the others face the placer.
 */
public class FacingDecorBlock extends ShapedBlock {

    private final VoxelShape north;
    private final boolean mounted;

    public FacingDecorBlock(Properties properties, VoxelShape north, boolean mounted) {
        super(properties);
        this.north = north;
        this.mounted = mounted;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        bakeShapes();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return ModelShapes.facing(north, state.getValue(BlockStateProperties.FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = mounted ? context.getClickedFace() : context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(BlockStateProperties.FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.FACING, rotation.rotate(state.getValue(BlockStateProperties.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BlockStateProperties.FACING, mirror.mirror(state.getValue(BlockStateProperties.FACING)));
    }
}
