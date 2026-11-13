package za.co.neroland.nerodecor.content.block.lights;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A fitting mounted on {@code face} of its own block space (the side it is fixed to; down = standing on the
 * floor), placed against the face that was clicked. {@code fromNorth} picks the canonical orientation: the chroma
 * lights draw on DOWN ({@code face_rotation}), the greebles plates on NORTH ({@code FACE_ROT}).
 */
public class FaceMountedBlock extends CollectionBlock {

    public static final EnumProperty<Direction> FACE = MountedStripBlock.FACE;

    private final boolean fromNorth;
    private final double[][] canonical;

    public FaceMountedBlock(Properties properties, boolean fromNorth, double[]... canonical) {
        super(properties);
        this.fromNorth = fromNorth;
        this.canonical = canonical;
        registerDefaultState(stateDefinition.any().setValue(FACE, fromNorth ? Direction.NORTH : Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE);
    }

    /** Boxes for {@code state} in the canonical orientation; subclasses swap them per variant. */
    protected double[][] canonical(BlockState state) {
        return canonical;
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        Direction face = state.getValue(FACE);
        return ModelShapes.boxes(fromNorth ? ModelShapes.fromNorth(face) : ModelShapes.fromDown(face), canonical(state));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACE, context.getClickedFace().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACE, rotation.rotate(state.getValue(FACE)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACE, mirror.mirror(state.getValue(FACE)));
    }
}
