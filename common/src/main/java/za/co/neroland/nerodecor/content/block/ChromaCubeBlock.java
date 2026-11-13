package za.co.neroland.nerodecor.content.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
//? if >=1.21.2 {
import net.minecraft.world.level.ScheduledTickAccess;
//?} else {
/*import net.minecraft.world.level.LevelAccessor;
*///?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;

/**
 * The Chroma Cube (0.8.0): a connected light-guide cube that replaces the twelve neon cubes. It carries the paintable
 * {@link DecorProperties#COLOR} (its static colour without a hub; the neon fold gives it the old neon's dye), six
 * connection booleans for the model-conditioned bezel (joins any neighbouring chroma cube), and {@link #LIGHT}: the
 * emitted light, 15 by default, which follows the committed static brightness of the hub colour controlling it
 * (rate-limited server-side; animations never touch it).
 */
public class ChromaCubeBlock extends Block {

    /** Emitted light level (0-15). */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty DOWN = PipeBlock.DOWN;
    private static final Map<Direction, BooleanProperty> BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

    public ChromaCubeBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(LIGHT)));
        registerDefaultState(stateDefinition.any().setValue(DecorProperties.COLOR, DecorColor.NATURAL)
                .setValue(LIGHT, 15)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DecorProperties.COLOR, LIGHT, UP, DOWN, NORTH, EAST, SOUTH, WEST);
    }

    /** {@code state} with its connection booleans recomputed from the world. */
    public BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState out = state;
        for (Direction d : Direction.values()) {
            out = out.setValue(BY_DIRECTION.get(d), level.getBlockState(pos.relative(d)).is(this));
        }
        return out;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    // 1.21.2 reordered updateShape and added the tick-access + random arguments.
    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
    *///?}
        return state.setValue(BY_DIRECTION.get(directionToNeighbour), neighbourState.is(this));
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
