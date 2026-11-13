package za.co.neroland.nerodecor.content.block.lights;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Sconce bar: a wall bar light looking {@code facing}, hung on the block behind it; {@code vertical} picks the
 * upright or the lying bar. Placed against a wall it looks away from that wall; an empty-hand use turns the bar.
 */
public class SconceBarBlock extends FacingShapeBlock {

    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");

    private static final double[] UPRIGHT = b(5, 2, 13, 11, 14, 16);
    private static final double[] LYING = b(2, 5, 13, 14, 11, 16);

    public SconceBarBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VERTICAL, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VERTICAL);
    }

    @Override
    protected VoxelShape shapeOf(BlockState state) {
        return ModelShapes.boxes(ModelShapes.horizontal(state.getValue(FACING)),
                state.getValue(VERTICAL) ? UPRIGHT : LYING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @Nullable
    protected BlockState cycled(BlockState state, Player player) {
        return state.cycle(VERTICAL);
    }
}
