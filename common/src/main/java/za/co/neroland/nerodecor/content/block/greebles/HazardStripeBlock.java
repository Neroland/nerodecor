package za.co.neroland.nerodecor.content.block.greebles;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import za.co.neroland.nerodecor.content.block.lights.MountedStripBlock;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/**
 * Hazard stripe: a 1px decal following the kit strip convention; {@code edge=false} fills the face,
 * {@code edge=true} is a 4px border band. Empty-hand use toggles {@code edge}.
 */
public class HazardStripeBlock extends MountedStripBlock {

    public static final BooleanProperty EDGE = BooleanProperty.create("edge");

    private static final double[][] FULL = {b(0, 0, 0, 16, 1, 16)};
    private static final double[][] EDGE_X = {b(0, 0, 0, 16, 1, 4)};
    private static final double[][] EDGE_Z = {b(0, 0, 0, 4, 1, 16)};

    public HazardStripeBlock(Properties properties) {
        super(properties, FULL, FULL);
        registerDefaultState(defaultBlockState().setValue(EDGE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(EDGE);
    }

    @Override
    protected double[][] canonical(BlockState state, boolean z) {
        return state.getValue(EDGE) ? (z ? EDGE_Z : EDGE_X) : FULL;
    }

    @Override
    @Nullable
    protected BlockState cycled(BlockState state, Player player) {
        return state.cycle(EDGE);
    }
}
