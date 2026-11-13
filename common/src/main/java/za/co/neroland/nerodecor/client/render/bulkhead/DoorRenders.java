package za.co.neroland.nerodecor.client.render.bulkhead;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.content.block.bulkhead.BhcProperties;
import za.co.neroland.nerodecor.content.block.bulkhead.DoorAnimBlockEntity;

/** Shared helpers for the closure renderers. */
final class DoorRenders {

    private DoorRenders() {
    }

    /**
     * Openness in [0, 1] to draw the moving part at, or -1 to draw nothing (idle: the static model carries the look).
     * After a close transition the part stays drawn shut until the server swaps the blockstate, so there is no gap.
     */
    static float openness(DoorAnimBlockEntity be, float partialTick, int duration) {
        if (be.getLevel() == null) {
            return -1.0F;
        }
        float t = be.transition(be.getLevel().getGameTime() + partialTick, duration);
        if (t >= 0.0F) {
            return t;
        }
        BlockState state = be.getBlockState();
        boolean open = state.hasProperty(BhcProperties.OPEN) && state.getValue(BhcProperties.OPEN);
        return be.animStart() != 0L && !be.opening() && open ? 0.0F : -1.0F;
    }

    /** Blockstate y rotation for a horizontal facing (north = 0). */
    static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    static float px(float v) {
        return v / 16.0F;
    }
}
