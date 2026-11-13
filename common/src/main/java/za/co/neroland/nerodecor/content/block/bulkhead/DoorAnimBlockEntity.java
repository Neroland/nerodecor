package za.co.neroland.nerodecor.content.block.bulkhead;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The block entity behind every animated bulkhead closure (sliding door, iris hatch, blast door centre, pressure
 * trapdoor, shutter). It never ticks: a transition stores its start game-tick and direction once, syncs, and the
 * client renderer interpolates from that start; when the transition is over the renderer draws nothing and the static
 * model carries the look. It also remembers the last redstone level so a signal edge (not a level) toggles the door,
 * like vanilla doors, without a {@code powered} blockstate property.
 */
public class DoorAnimBlockEntity extends SyncedBlockEntity {

    private static final String START = "anim_start";
    private static final String OPENING = "opening";
    private static final String POWERED = "powered";

    private long animStart;
    private boolean opening;
    private boolean powered;

    public DoorAnimBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Start a transition now (server). */
    public void start(boolean open) {
        if (level == null) {
            return;
        }
        this.animStart = level.getGameTime();
        this.opening = open;
        sync();
    }

    public boolean powered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            setChanged();
        }
    }

    public boolean opening() {
        return opening;
    }

    public long animStart() {
        return animStart;
    }

    /**
     * Openness in [0, 1] at {@code now} (game ticks + partial tick) for a transition of {@code duration} ticks, or
     * {@code -1} when no transition is running.
     */
    public float transition(float now, int duration) {
        if (animStart == 0L) {
            return -1.0F;
        }
        float t = (now - animStart) / duration;
        if (t < 0.0F || t >= 1.0F) {
            return -1.0F;
        }
        return opening ? t : 1.0F - t;
    }

    @Override
    protected void write(ValueOutput output) {
        output.putLong(START, animStart);
        output.putBoolean(OPENING, opening);
        output.putBoolean(POWERED, powered);
    }

    @Override
    protected void read(ValueInput input) {
        animStart = input.getLongOr(START, 0L);
        opening = input.getBooleanOr(OPENING, false);
        powered = input.getBooleanOr(POWERED, false);
    }
}
