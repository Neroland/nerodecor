package za.co.neroland.nerodecor.content.block.conduits;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * pipe_valve / pipe_gauge: an inline fitting on {@code axis} (x / y / z) with a pipe arm at both ends of the axis, so
 * it sits in a decor_pipe or coolant_tube run. The axis follows the clicked face.
 */
public class InlineFittingBlock extends ShapedBlock {

    /** The body drawn on the x axis, as the model. */
    private final VoxelShape bodyX;
    /** The body on the y axis. */
    private final VoxelShape bodyY;
    /** The body on the z axis. */
    private final VoxelShape bodyZ;

    public InlineFittingBlock(Properties properties, VoxelShape bodyX, VoxelShape bodyY, VoxelShape bodyZ) {
        super(properties);
        this.bodyX = bodyX;
        this.bodyY = bodyY;
        this.bodyZ = bodyZ;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        bakeShapes();
    }

    /** Valve: flanged body with a handwheel on top (model pipe_valve_body; y axis turned x=270, z axis y=90). */
    public static InlineFittingBlock valve(Properties properties) {
        VoxelShape x = ModelShapes.or(ModelShapes.box(3, 3, 3, 13, 13, 13), ModelShapes.box(4, 13, 4, 12, 16, 12));
        return new InlineFittingBlock(properties, x, ModelShapes.rotate(x, 270, 0), ModelShapes.rotate(x, 0, 90));
    }

    /** Gauge: square body with the dial on +z (models pipe_gauge_body_x / _y; z axis is body_x turned y=90). */
    public static InlineFittingBlock gauge(Properties properties) {
        VoxelShape x = ModelShapes.box(4, 4, 4, 12, 12, 14);
        return new InlineFittingBlock(properties, x, ModelShapes.box(4, 4, 4, 12, 12, 14), ModelShapes.rotate(x, 0, 90));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.AXIS);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        VoxelShape body = switch (axis) {
            case X -> bodyX;
            case Y -> bodyY;
            case Z -> bodyZ;
        };
        return ModelShapes.or(body, ModelShapes.along(axis, 0, 16, 5, 11));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        if (axis == Direction.Axis.Y) {
            return state;
        }
        Direction turned = rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
        return state.setValue(BlockStateProperties.AXIS, turned.getAxis());
    }
}
