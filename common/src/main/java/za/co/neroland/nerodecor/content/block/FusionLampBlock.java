package za.co.neroland.nerodecor.content.block;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;

import za.co.neroland.nerodecor.content.fx.AmbientFx;

/**
 * The Fusion Lamp: a redstone lamp whose caged plasma core ignites (light 15) with a soft rising
 * power-up tone and powers down with a falling one; while lit it breathes and hums quietly.
 * Mirrors vanilla {@code RedstoneLampBlock} timing (instant on, 4-tick delayed off).
 */
public class FusionLampBlock extends Block {

    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    private final AmbientFx litFx;
    private final Supplier<SoundEvent> powerOn;
    private final Supplier<SoundEvent> powerOff;

    public FusionLampBlock(Properties properties, AmbientFx litFx, Supplier<SoundEvent> powerOn,
                           Supplier<SoundEvent> powerOff) {
        super(properties);
        this.litFx = litFx;
        this.powerOn = powerOn;
        this.powerOff = powerOff;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean lit = state.getValue(LIT);
        if (lit != level.hasNeighborSignal(pos)) {
            if (lit) {
                level.scheduleTick(pos, this, 4);
            } else {
                level.setBlock(pos, state.cycle(LIT), 2);
                level.playSound(null, pos, powerOn.get(), SoundSource.BLOCKS, 0.45F, 1.0F);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && !level.hasNeighborSignal(pos)) {
            level.setBlock(pos, state.cycle(LIT), 2);
            level.playSound(null, pos, powerOff.get(), SoundSource.BLOCKS, 0.4F, 1.0F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            litFx.animate(state, level, pos, random);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
