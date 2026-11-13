package za.co.neroland.nerodecor.content.block.lights;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ceiling coffer: a recessed tray hung under the ceiling. {@code north..west} are true where the same coffer
 * continues, and the rim on that side drops away so a row reads as one long trough.
 */
public class CeilingCofferBlock extends CollectionBlock {

    private static final Map<Direction, BooleanProperty> SIDES = Map.of(
            Direction.NORTH, PipeBlock.NORTH, Direction.EAST, PipeBlock.EAST,
            Direction.SOUTH, PipeBlock.SOUTH, Direction.WEST, PipeBlock.WEST);

    public CeilingCofferBlock(Properties properties) {
        super(properties);
        BlockState s = stateDefinition.any();
        for (BooleanProperty p : SIDES.values()) {
            s = s.setValue(p, false);
        }
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST);
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        VoxelShape out = Block.box(0, 15, 0, 16, 16, 16);
        if (!state.getValue(PipeBlock.NORTH)) {
            out = Shapes.or(out, Block.box(0, 13, 0, 16, 16, 2));
        }
        if (!state.getValue(PipeBlock.SOUTH)) {
            out = Shapes.or(out, Block.box(0, 13, 14, 16, 16, 16));
        }
        if (!state.getValue(PipeBlock.WEST)) {
            out = Shapes.or(out, Block.box(0, 13, 0, 2, 16, 16));
        }
        if (!state.getValue(PipeBlock.EAST)) {
            out = Shapes.or(out, Block.box(14, 13, 0, 16, 16, 16));
        }
        return out;
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet()) {
            state = state.setValue(e.getValue(), level.getBlockState(pos.relative(e.getKey())).is(this));
        }
        return state;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return links(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState refresh(BlockState state, LevelReader level, BlockPos pos, Direction dir, BlockState neighbour) {
        BooleanProperty p = SIDES.get(dir);
        return p == null ? state : state.setValue(p, neighbour.is(this));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet()) {
            out = out.setValue(SIDES.get(rotation.rotate(e.getKey())), state.getValue(e.getValue()));
        }
        return out;
    }
}
