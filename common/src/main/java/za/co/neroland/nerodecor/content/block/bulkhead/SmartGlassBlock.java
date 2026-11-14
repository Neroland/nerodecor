package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerodecor.content.block.ConnectedGlowBlock;
import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * Smart glass: connected glazing with an {@code opaque} (frosted) state. Using a pane switches the whole connected
 * sheet (up to {@value #MAX_SHEET} blocks) between clear and frosted, and so does a rising redstone edge on any pane
 * ({@code powered} records the last signal so a held lever toggles once, not every neighbour update).
 */
public class SmartGlassBlock extends ConnectedGlowBlock {

    private static final int MAX_SHEET = 256;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SmartGlassBlock(Properties properties) {
        super(properties, AmbientFx.NONE, true);
        registerDefaultState(defaultBlockState().setValue(BhcProperties.OPAQUE, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BhcProperties.OPAQUE, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null
                : state.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
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
        if (signal == state.getValue(POWERED)) {
            return;
        }
        if (signal) {
            toggleSheet(level, pos, !state.getValue(BhcProperties.OPAQUE));
        } else {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            toggleSheet(level, pos, !state.getValue(BhcProperties.OPAQUE));
        }
        return InteractionResult.SUCCESS;
    }

    /** Sets {@code opaque} (and refreshes {@code powered}) on the connected sheet through {@code pos} (at most {@value #MAX_SHEET} panes). */
    private void toggleSheet(Level level, BlockPos pos, boolean opaque) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            BlockState s = level.getBlockState(p);
            // Record each pane's current signal too, so one wire touching several panes toggles the sheet once.
            BlockState next = s.setValue(BhcProperties.OPAQUE, opaque).setValue(POWERED, level.hasNeighborSignal(p));
            if (next != s) {
                level.setBlock(p, next, Block.UPDATE_CLIENTS);
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
}
