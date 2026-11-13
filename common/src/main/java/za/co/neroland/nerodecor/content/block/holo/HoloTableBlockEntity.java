package za.co.neroland.nerodecor.content.block.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holo table: the terrain well renders on its own; a {@code link.display} payload floats above it as a readout. */
public class HoloTableBlockEntity extends SurfaceBlockEntity {

    private final DisplayState display = new DisplayState();

    public HoloTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected SurfaceBlockEntity displayTarget() {
        return this;
    }

    @Override
    public DisplayState display() {
        return display;
    }

    @Override
    protected void write(ValueOutput output) {
        display.write(output, "display_");
    }

    @Override
    protected void read(ValueInput input) {
        display.read(input, "display_");
    }
}
