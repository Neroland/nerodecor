package za.co.neroland.nerodecor.content.block.bulkhead;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared behaviour of the animated bulkhead closures. Purely cosmetic (no sealing logic). Opened by use or by a
 * redstone edge. A transition never ticks: opening swaps the blockstate (and so the collision) at the start, closing
 * swaps it at the end through one scheduled block tick; in between the block entity's start tick drives the client
 * renderer.
 *
 * <p>Subclasses say which blocks move together ({@link #members}) and which one holds the block entity that remembers
 * the redstone level and owns the scheduled close ({@link #controller}).
 */
public abstract class AnimatedDoorBlock extends BhcBlock implements EntityBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private final Supplier<? extends BlockEntityType<DoorAnimBlockEntity>> type;
    private final int duration;
    private final SoundEvent openSound;
    private final SoundEvent closeSound;

    protected AnimatedDoorBlock(Properties properties, Supplier<? extends BlockEntityType<DoorAnimBlockEntity>> type,
                                int duration, SoundEvent openSound, SoundEvent closeSound) {
        super(properties);
        this.type = type;
        this.duration = duration;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    /** Transition length in ticks (the renderer uses the same value). */
    public int duration() {
        return duration;
    }

    /** Every position that opens and closes with {@code pos} (including it), read from the current world. */
    protected abstract List<BlockPos> members(BlockGetter level, BlockPos pos, BlockState state);

    /** The member holding the controlling block entity. */
    protected abstract BlockPos controller(BlockPos pos, BlockState state);

    /** Whether {@code state} carries a block entity (multiblocks keep one, on the controller). */
    protected boolean hasEntity(BlockState state) {
        return true;
    }

    /** Called after a toggle so paired blocks (double sliding doors) can follow. */
    protected void afterToggle(Level level, BlockPos pos, BlockState state, boolean open) {
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return hasEntity(state) ? new DoorAnimBlockEntity(type.get(), pos, state) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            toggle(level, pos, state, !state.getValue(OPEN));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onNeighbourChanged(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        List<BlockPos> members = members(level, pos, state);
        boolean powered = powered(level, members.toArray(new BlockPos[0]));
        if (level.getBlockEntity(controller(pos, state)) instanceof DoorAnimBlockEntity be && be.powered() != powered) {
            be.setPowered(powered);
            if (powered != state.getValue(OPEN) || powered != be.opening()) {
                toggle(level, pos, state, powered);
            }
        }
    }

    /** Open or close {@code pos}'s door (server). */
    public void toggle(Level level, BlockPos pos, BlockState state, boolean open) {
        BlockPos ctrl = controller(pos, state);
        if (!(level.getBlockEntity(ctrl) instanceof DoorAnimBlockEntity be)) {
            return;
        }
        if (be.opening() == open && state.getValue(OPEN) == open) {
            return;
        }
        List<BlockPos> members = members(level, pos, state);
        if (open) {
            for (BlockPos m : members) {
                BlockState s = level.getBlockState(m);
                if (s.is(this) && !s.getValue(OPEN)) {
                    level.setBlock(m, s.setValue(OPEN, true), Block.UPDATE_CLIENTS);
                }
            }
        } else {
            level.scheduleTick(ctrl, this, duration);
        }
        for (BlockPos m : members) {
            if (level.getBlockEntity(m) instanceof DoorAnimBlockEntity mbe) {
                mbe.start(open);
            }
        }
        level.playSound(null, ctrl, open ? openSound : closeSound, SoundSource.BLOCKS, 1.0F,
                0.9F + level.getRandom().nextFloat() * 0.1F);
        afterToggle(level, pos, state, open);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN) || !(level.getBlockEntity(pos) instanceof DoorAnimBlockEntity be) || be.opening()) {
            return;
        }
        long left = be.animStart() + duration - level.getGameTime();
        if (left > 0) {
            level.scheduleTick(pos, this, (int) left);
            return;
        }
        for (BlockPos m : members(level, pos, state)) {
            BlockState s = level.getBlockState(m);
            if (s.is(this) && s.getValue(OPEN)) {
                level.setBlock(m, s.setValue(OPEN, false), Block.UPDATE_CLIENTS);
            }
        }
    }
}
