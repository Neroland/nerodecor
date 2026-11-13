package za.co.neroland.nerodecor.content.block.holo;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.link.display.DisplayAddress;
import za.co.neroland.nerolandcore.link.display.DisplaySurface;
import za.co.neroland.nerolandcore.link.display.DisplaySurfaces;

import za.co.neroland.nerodecor.content.block.bulkhead.SyncedBlockEntity;

/**
 * A block entity that implements Core's display-surface contract ({@code link.display}): while loaded on the server it
 * is registered under its {@link DisplayAddress}, so driver mods can push text / icon / colour to it. The payload is
 * cosmetic and carries no personal data. Subclasses decide which {@link DisplayState} a push lands on
 * ({@link #displayTarget()}; a wall screen forwards to its group's anchor) and react in {@link #displayChanged()}.
 */
public abstract class SurfaceBlockEntity extends SyncedBlockEntity implements DisplaySurface {

    /** True while {@link DisplaySurfaces#register} re-applies a cached payload during chunk load. */
    protected boolean registering;

    protected SurfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The surface whose state a push changes, or {@code null} to ignore it. */
    protected abstract SurfaceBlockEntity displayTarget();

    /** The state stored on this block entity (may stay blank on non-anchor wall screen tiles). */
    public abstract DisplayState display();

    /** Called on the target after its display state changed (server). */
    protected void displayChanged() {
        if (!registering) {
            sync();
        } else {
            setChanged();
        }
    }

    public static DisplayAddress address(Level level, BlockPos pos) {
        return DisplayAddress.of(level.dimension(), pos);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide()) {
            registering = true;
            try {
                DisplaySurfaces.register(address(level, worldPosition), this);
            } finally {
                registering = false;
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            DisplaySurfaces.unregister(address(level, worldPosition));
        }
    }

    private void edit(Consumer<DisplayState> change) {
        SurfaceBlockEntity target = displayTarget();
        if (target == null) {
            return;
        }
        change.accept(target.display());
        boolean previous = target.registering;
        target.registering = previous || registering;
        try {
            target.displayChanged();
        } finally {
            target.registering = previous;
        }
    }

    @Override
    public void setText(Component text) {
        edit(d -> d.setText(text));
    }

    @Override
    public void setIcon(Identifier icon) {
        edit(d -> d.setIcon(icon));
    }

    @Override
    public void setStatusColour(int rgb) {
        edit(d -> d.setColour(rgb));
    }

    @Override
    public void clear() {
        edit(DisplayState::clear);
    }
}
