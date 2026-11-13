package za.co.neroland.nerodecor.content.block.greebles;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.lights.CollectionBlock;
import za.co.neroland.nerodecor.content.block.lights.ModelShapes;
import za.co.neroland.nerodecor.content.block.lights.MountedStripBlock;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Pipe bracket: a saddle clamp mounted on {@code face}, holding a pipe that runs along {@code axis} (kit strip
 * convention, no links). On a wall the pipe runs horizontally unless the player is looking up or down.
 */
public class PipeBracketBlock extends CollectionBlock {

    public static final EnumProperty<Direction> FACE = MountedStripBlock.FACE;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** The bracket on DOWN for a pipe along x (the module's {@code BRACKET} boxes). */
    private static final double[][] ALONG_X = {
        b(5, 0, 3, 11, 1, 13), b(6, 1, 6, 10, 4, 10), b(6, 4, 4, 10, 5, 12), b(6, 5, 4, 10, 11, 5),
        b(6, 5, 11, 10, 11, 12), b(6, 11, 4, 10, 12, 12), b(7, 7, 3, 9, 9, 4), b(7, 7, 12, 9, 9, 13)};
    private static final double[][] ALONG_Z = transpose(ALONG_X);

    public PipeBracketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(AXIS, Direction.Axis.X));
    }

    private static double[][] transpose(double[][] boxes) {
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            double[] q = boxes[i];
            out[i] = b(q[2], q[1], q[0], q[5], q[4], q[3]);
        }
        return out;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, AXIS);
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        Direction face = state.getValue(FACE);
        Direction.Axis axis = state.getValue(AXIS);
        if (!ModelShapes.inPlane(face, axis)) {
            axis = ModelShapes.cross(face, face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y);
        }
        ModelShapes.StripRot r = ModelShapes.strip(face, axis);
        return ModelShapes.boxes(r.rot(), r.z() ? ALONG_Z : ALONG_X);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        Direction.Axis axis;
        if (face.getAxis() == Direction.Axis.Y) {
            axis = context.getHorizontalDirection().getAxis();
        } else if (context.getNearestLookingDirection().getAxis() == Direction.Axis.Y) {
            axis = Direction.Axis.Y;
        } else {
            axis = ModelShapes.cross(face, Direction.Axis.Y);
        }
        return defaultBlockState().setValue(FACE, face).setValue(AXIS, axis);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        if (axis != Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        }
        return state.setValue(FACE, rotation.rotate(state.getValue(FACE))).setValue(AXIS, axis);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACE, mirror.mirror(state.getValue(FACE)));
    }
}
