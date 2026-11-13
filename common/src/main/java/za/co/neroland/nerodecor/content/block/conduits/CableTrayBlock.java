package za.co.neroland.nerodecor.content.block.conduits;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import za.co.neroland.nerodecor.content.block.gantry.AxisLinkBlock;
import za.co.neroland.nerodecor.content.block.gantry.ModelShapes;
import za.co.neroland.nerodecor.content.block.gantry.ShapedBlock;

/**
 * Cable tray, a kit strip: {@code face} is the mounting side, {@code axis} the run (always across the face), and
 * {@code pos_link} / {@code neg_link} mean the same tray (same face and axis) continues at that end. The tray is 4px
 * deep against its face and 12px wide.
 */
public class CableTrayBlock extends ShapedBlock {

    public CableTrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACE, Direction.DOWN)
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X)
                .setValue(AxisLinkBlock.POS_LINK, false).setValue(AxisLinkBlock.NEG_LINK, false));
        bakeShapes();
    }

    /** The {@code face} property: the six-way facing named "face" in the blockstate contract. */
    public static final net.minecraft.world.level.block.state.properties.Property<Direction> FACE =
            //? if >=1.21.2 {
            net.minecraft.world.level.block.state.properties.EnumProperty.create("face", Direction.class);
            //?} else {
            /*net.minecraft.world.level.block.state.properties.DirectionProperty.create("face");
            *///?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, BlockStateProperties.AXIS, AxisLinkBlock.POS_LINK, AxisLinkBlock.NEG_LINK);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        Direction face = state.getValue(FACE);
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        if (face.getAxis() == axis) {
            return Shapes.block(); // never placed; the blockstate has no model for it
        }
        double[] min = new double[3];
        double[] max = new double[3];
        for (Direction.Axis a : Direction.Axis.values()) {
            int i = a.ordinal();
            if (a == face.getAxis()) {
                boolean negative = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
                min[i] = negative ? 0 : 12;
                max[i] = negative ? 4 : 16;
            } else if (a == axis) {
                min[i] = 0;
                max[i] = 16;
            } else {
                min[i] = 2;
                max[i] = 14;
            }
        }
        return ModelShapes.box(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    private boolean linksTo(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(FACE) == state.getValue(FACE)
                && other.getValue(BlockStateProperties.AXIS) == state.getValue(BlockStateProperties.AXIS);
    }

    private BlockState withLinks(BlockState state, BlockGetter level, BlockPos pos) {
        Direction positive = Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS),
                Direction.AxisDirection.POSITIVE);
        return state.setValue(AxisLinkBlock.POS_LINK, linksTo(state, level.getBlockState(pos.relative(positive))))
                .setValue(AxisLinkBlock.NEG_LINK, linksTo(state, level.getBlockState(pos.relative(positive.getOpposite()))));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        Direction.Axis axis = null;
        for (Direction d : context.getNearestLookingDirections()) {
            if (d.getAxis() != face.getAxis()) {
                axis = d.getAxis();
                break;
            }
        }
        if (axis == null) {
            axis = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        }
        return withLinks(defaultBlockState().setValue(FACE, face).setValue(BlockStateProperties.AXIS, axis),
                context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState onNeighbourShape(BlockState state, LevelReader level, BlockPos pos, Direction direction,
                                          BlockPos neighbourPos, BlockState neighbourState) {
        if (direction.getAxis() != state.getValue(BlockStateProperties.AXIS)) {
            return state;
        }
        BooleanProperty link = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? AxisLinkBlock.POS_LINK : AxisLinkBlock.NEG_LINK;
        return state.setValue(link, linksTo(state, neighbourState));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state.setValue(FACE, rotation.rotate(state.getValue(FACE)));
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        if (axis == Direction.Axis.Y) {
            return out;
        }
        Direction turned = rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
        out = out.setValue(BlockStateProperties.AXIS, turned.getAxis());
        if (turned.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            out = out.setValue(AxisLinkBlock.POS_LINK, state.getValue(AxisLinkBlock.NEG_LINK))
                    .setValue(AxisLinkBlock.NEG_LINK, state.getValue(AxisLinkBlock.POS_LINK));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state.setValue(FACE, mirror.mirror(state.getValue(FACE)));
        Direction positive = Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS),
                Direction.AxisDirection.POSITIVE);
        if (mirror.mirror(positive).getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            out = out.setValue(AxisLinkBlock.POS_LINK, state.getValue(AxisLinkBlock.NEG_LINK))
                    .setValue(AxisLinkBlock.NEG_LINK, state.getValue(AxisLinkBlock.POS_LINK));
        }
        return out;
    }
}
