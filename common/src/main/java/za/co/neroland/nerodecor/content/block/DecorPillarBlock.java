package za.co.neroland.nerodecor.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * An axis-placed Luminous pillar (plasma conduit, starsteel pillar) that <b>connects along its
 * axis</b>: {@link #POS_LINK} / {@link #NEG_LINK} record whether the next block along the
 * positive / negative axis direction is the same pillar on the same axis. The model draws an end
 * collar only on an unlinked end, so a stacked column reads as one continuous run with a cap at
 * each end. State-driven (multipart JSON), no ticking.
 */
public class DecorPillarBlock extends RotatedPillarBlock {

    public static final BooleanProperty POS_LINK = BooleanProperty.create("pos_link");
    public static final BooleanProperty NEG_LINK = BooleanProperty.create("neg_link");

    private final AmbientFx fx;

    public DecorPillarBlock(Properties properties, AmbientFx fx) {
        super(properties);
        this.fx = fx;
        registerDefaultState(defaultBlockState().setValue(POS_LINK, false).setValue(NEG_LINK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POS_LINK, NEG_LINK);
    }

    private boolean linksTo(BlockState other, Direction.Axis axis) {
        return other.is(this) && other.getValue(AXIS) == axis;
    }

    private static BooleanProperty linkFor(Direction d) {
        return d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? POS_LINK : NEG_LINK;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getClickedFace().getAxis();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction pos1 = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return defaultBlockState().setValue(AXIS, axis)
                .setValue(POS_LINK, linksTo(level.getBlockState(pos.relative(pos1)), axis))
                .setValue(NEG_LINK, linksTo(level.getBlockState(pos.relative(pos1.getOpposite())), axis));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        Direction.Axis axis = state.getValue(AXIS);
        if (directionToNeighbour.getAxis() != axis) {
            return state;
        }
        return state.setValue(linkFor(directionToNeighbour), linksTo(neighbourState, axis));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction positive = Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
        return swapIfFlipped(state, super.rotate(state, rotation), rotation.rotate(positive));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction positive = Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
        return swapIfFlipped(state, super.mirror(state, mirror), mirror.mirror(positive));
    }

    /** If the old positive end now faces a negative direction, the two link flags trade places. */
    private static BlockState swapIfFlipped(BlockState before, BlockState after, Direction newPositive) {
        if (newPositive.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return after;
        }
        return after.setValue(POS_LINK, before.getValue(NEG_LINK)).setValue(NEG_LINK, before.getValue(POS_LINK));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        fx.animate(state, level, pos, random);
    }
}
