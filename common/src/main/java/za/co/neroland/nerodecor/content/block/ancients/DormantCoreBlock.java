package za.co.neroland.nerodecor.content.block.ancients;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
//? if >=1.21.2 {
import net.minecraft.world.level.redstone.Orientation;
//?}

/**
 * Dormant core: precursor stone around a dark lens. A redstone signal wakes it ({@code powered=true}): the glyph
 * layer breathes and the block gives light; without one it is dark and gives none.
 */
public class DormantCoreBlock extends Block {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public DormantCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    // 1.21.2 replaced the neighbour position argument with a redstone Orientation.
    //? if >=1.21.2 {
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
    //?} else {
    /*@Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos neighbourPos, boolean movedByPiston) {
    *///?}
        if (level.isClientSide()) {
            return;
        }
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
        }
    }
}
