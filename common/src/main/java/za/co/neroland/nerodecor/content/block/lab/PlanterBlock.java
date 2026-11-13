package za.co.neroland.nerodecor.content.block.lab;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Planter: a {@link HorizontalRunBlock} trough with one of four plant looks ({@code plant} 0 fern, 1 violet bloom,
 * 2 ember succulent, 3 frost reed). Using it empty-handed cycles the plant. Cutout; purely decorative (no growth).
 */
public class PlanterBlock extends HorizontalRunBlock {

    public static final IntegerProperty PLANT = IntegerProperty.create("plant", 0, 3);

    public PlanterBlock(Properties properties) {
        super(properties, HorizontalRunBlock::planterShape);
        registerDefaultState(defaultBlockState().setValue(PLANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PLANT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(PLANT, (state.getValue(PLANT) + 1) % 4), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
