package za.co.neroland.nerodecor.content.block.greebles;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import za.co.neroland.nerodecor.content.block.lights.FaceMountedBlock;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/** Maintenance panel: a 3px cover on {@code face}; an empty-hand use swings it {@code open} to show the wiring. */
public class MaintenancePanelBlock extends FaceMountedBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final double[][] CLOSED = {b(0, 0, 0, 16, 16, 2), b(2, 2, 2, 14, 14, 3)};
    private static final double[][] OPENED = {b(0, 0, 0, 16, 16, 2), b(1, 2, 2, 2, 14, 14)};

    public MaintenancePanelBlock(Properties properties) {
        super(properties, true, CLOSED);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    protected double[][] canonical(BlockState state) {
        return state.getValue(OPEN) ? OPENED : CLOSED;
    }

    @Override
    @Nullable
    protected BlockState cycled(BlockState state, Player player) {
        return state.cycle(OPEN);
    }
}
