package za.co.neroland.nerodecor.content.block.command;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerodecor.content.block.holo.DisplayState;
import za.co.neroland.nerodecor.content.block.holo.SurfaceBlockEntity;

/**
 * Wall screen tile. Every tile has one (non-ticking) and is addressable as a display surface, but only the group's
 * anchor tile (lowest x, y, z) stores a payload and renders the whole N x M display; the rest forward pushes to it and
 * store nothing.
 */
public class WallScreenBlockEntity extends SurfaceBlockEntity {

    private final DisplayState display = new DisplayState();

    public WallScreenBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public DisplayState display() {
        return display;
    }

    @Override
    protected SurfaceBlockEntity displayTarget() {
        if (level == null) {
            return null;
        }
        BlockPos anchor = WallScreenBlock.anchor(level, worldPosition, getBlockState());
        return level.getBlockEntity(anchor) instanceof WallScreenBlockEntity be ? be : null;
    }

    @Override
    protected void displayChanged() {
        super.displayChanged();
        if (!registering && level != null && !level.isClientSide()) {
            WallScreenBlock.setGroupIdle(level, worldPosition, getBlockState(), display.isBlank());
        }
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
