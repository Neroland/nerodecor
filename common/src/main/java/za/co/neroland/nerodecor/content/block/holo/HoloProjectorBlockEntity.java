package za.co.neroland.nerodecor.content.block.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerodecor.content.block.bulkhead.SyncedBlockEntity;

/** Holo projector: just an on/off switch; the beam and idle gem are drawn by the renderer. */
public class HoloProjectorBlockEntity extends SyncedBlockEntity {

    private boolean enabled = true;

    public HoloProjectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean enabled() {
        return enabled;
    }

    public void toggle() {
        enabled = !enabled;
        sync();
    }

    @Override
    protected void write(ValueOutput output) {
        output.putBoolean("enabled", enabled);
    }

    @Override
    protected void read(ValueInput input) {
        enabled = input.getBooleanOr("enabled", true);
    }
}
