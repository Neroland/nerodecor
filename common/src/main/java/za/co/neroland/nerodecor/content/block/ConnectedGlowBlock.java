package za.co.neroland.nerodecor.content.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * A {@link GlowDecorBlock} with <b>model-conditioned connected textures</b>: six booleans record
 * which neighbours are the same block, and the multipart blockstate draws a bezel strip along
 * every edge whose neighbour is <i>not</i> — so a wall, floor or ceiling of these reads as one
 * seamless framed surface. Loader-agnostic (plain vanilla multipart JSON, no model baking hooks)
 * and resource-pack friendly; the state is recomputed by {@code updateShape}, never by ticking.
 */
public class ConnectedGlowBlock extends GlowDecorBlock {

    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty DOWN = PipeBlock.DOWN;
    private static final Map<Direction, BooleanProperty> BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

    public ConnectedGlowBlock(Properties properties, AmbientFx fx, boolean glassy) {
        super(properties, fx, glassy);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, NORTH, EAST, SOUTH, WEST);
    }

    /** Whether this block visually joins {@code other} (same block only). */
    protected boolean connectsTo(BlockState other) {
        return other.is(this);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            state = state.setValue(BY_DIRECTION.get(d), connectsTo(level.getBlockState(pos.relative(d))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return state.setValue(BY_DIRECTION.get(directionToNeighbour), connectsTo(neighbourState));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction d : Direction.values()) {
            out = out.setValue(BY_DIRECTION.get(rotation.rotate(d)), state.getValue(BY_DIRECTION.get(d)));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.values()) {
            out = out.setValue(BY_DIRECTION.get(mirror.mirror(d)), state.getValue(BY_DIRECTION.get(d)));
        }
        return out;
    }
}
