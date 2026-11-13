package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * Smart glass: connected glazing with an {@code opaque} (frosted) state. Using a pane switches the whole connected
 * sheet (up to {@value #MAX_SHEET} blocks) between clear and frosted.
 */
public class SmartGlassBlock extends ConnectedGlowBlock {

    private static final int MAX_SHEET = 256;

    public SmartGlassBlock(Properties properties) {
        super(properties, AmbientFx.NONE, true);
        registerDefaultState(defaultBlockState().setValue(BhcProperties.OPAQUE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BhcProperties.OPAQUE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean opaque = !state.getValue(BhcProperties.OPAQUE);
            Set<BlockPos> seen = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(pos);
            seen.add(pos);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                BlockState s = level.getBlockState(p);
                if (s.getValue(BhcProperties.OPAQUE) != opaque) {
                    level.setBlock(p, s.setValue(BhcProperties.OPAQUE, opaque), Block.UPDATE_CLIENTS);
                }
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (seen.size() < MAX_SHEET && !seen.contains(n) && level.getBlockState(n).is(this)) {
                        seen.add(n);
                        queue.add(n);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, opaque ? 0.8F : 1.2F);
        }
        return InteractionResult.SUCCESS;
    }
}
