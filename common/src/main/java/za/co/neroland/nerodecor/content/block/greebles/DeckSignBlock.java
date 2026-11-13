package za.co.neroland.nerodecor.content.block.greebles;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import za.co.neroland.nerodecor.content.block.lights.FaceMountedBlock;

import static za.co.neroland.nerodecor.content.block.lights.ModelShapes.b;

/** Deck sign: a lit deck number plate on {@code face}; empty-hand use steps {@code number} up (sneak: down). */
public class DeckSignBlock extends FaceMountedBlock {

    public static final IntegerProperty NUMBER = IntegerProperty.create("number", 0, 9);

    public DeckSignBlock(Properties properties) {
        super(properties, true, b(2, 1, 0, 14, 15, 1));
        registerDefaultState(defaultBlockState().setValue(NUMBER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NUMBER);
    }

    @Override
    @Nullable
    protected BlockState cycled(BlockState state, Player player) {
        int step = player.isShiftKeyDown() ? 9 : 1;
        return state.setValue(NUMBER, (state.getValue(NUMBER) + step) % 10);
    }
}
