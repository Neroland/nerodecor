package za.co.neroland.nerodecor.content.block.greebles;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import za.co.neroland.nerodecor.content.block.lights.FaceMountedBlock;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Rivet plate: a 1px plate on {@code face}. {@code north..down} are true where the same plate on the same face
 * continues on that side (only the face's four in-plane sides can be true), so a group wears its lip and rivets on
 * the perimeter only.
 */
public class RivetPlateBlock extends FaceMountedBlock {

    private static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;

    public RivetPlateBlock(Properties properties) {
        super(properties, true, b(0, 0, 0, 16, 16, 1));
        BlockState s = defaultBlockState();
        for (BooleanProperty p : SIDES.values()) {
            s = s.setValue(p, false);
        }
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    private BlockState links(BlockState state, BlockGetter level, BlockPos pos) {
        Direction face = state.getValue(FACE);
        for (Direction d : Direction.values()) {
            boolean join = false;
            if (d.getAxis() != face.getAxis()) {
                BlockState n = level.getBlockState(pos.relative(d));
                join = n.is(this) && n.getValue(FACE) == face;
            }
            state = state.setValue(SIDES.get(d), join);
        }
        return state;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : links(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState refresh(BlockState state, LevelReader level, BlockPos pos, Direction dir, BlockState neighbour) {
        return links(state, level, pos);
    }
}
