package za.co.neroland.nerodecor.content.block.lights;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

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
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Floor tile slab: a vanilla slab plus {@code north..west}, true where the same slab of the same {@code type}
 * continues (the glowing seam is drawn only round the outside of a group).
 */
public class TileSlabBlock extends SlabBlock {

    private static final Map<Direction, BooleanProperty> SIDES = Map.of(
            Direction.NORTH, PipeBlock.NORTH, Direction.EAST, PipeBlock.EAST,
            Direction.SOUTH, PipeBlock.SOUTH, Direction.WEST, PipeBlock.WEST);

    public TileSlabBlock(Properties properties) {
        super(properties);
        BlockState s = defaultBlockState();
        for (BooleanProperty p : SIDES.values()) {
            s = s.setValue(p, false);
        }
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST);
    }

    private boolean joins(BlockState self, BlockState other) {
        return other.is(this) && other.getValue(TYPE) == self.getValue(TYPE);
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet()) {
            state = state.setValue(e.getValue(), joins(state, level.getBlockState(pos.relative(e.getKey()))));
        }
        return state;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : links(state, context.getLevel(), context.getClickedPos());
    }

    //? if >=1.21.2 {
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        BlockState out = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState,
                random);
    //?} else {
    /*@Override
    protected BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BlockState out = super.updateShape(state, directionToNeighbour, neighbourState, level, pos, neighbourPos);
    *///?}
        return out.is(this) ? links(out, level, pos) : out;
    }
}
