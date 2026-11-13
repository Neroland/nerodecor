package za.co.neroland.nerodecor.content.block.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Holo sign: two player-editable lines (sanitised, at most {@value HoloText#LINE_LENGTH} characters each) and a
 * {@code link.display} surface. A pushed payload takes over the panel until it is cleared. No author is recorded.
 */
public class HoloSignBlockEntity extends SurfaceBlockEntity {

    private final String[] lines = {"", ""};
    private final DisplayState display = new DisplayState();

    public HoloSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String line(int i) {
        return lines[i];
    }

    /** Server: replace both lines (sanitised here again) and sync. */
    public void setLines(String first, String second) {
        lines[0] = HoloText.sanitise(first, HoloText.LINE_LENGTH);
        lines[1] = HoloText.sanitise(second, HoloText.LINE_LENGTH);
        sync();
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
        output.putString("line0", lines[0]);
        output.putString("line1", lines[1]);
        display.write(output, "display_");
    }

    @Override
    protected void read(ValueInput input) {
        lines[0] = HoloText.sanitise(input.getStringOr("line0", ""), HoloText.LINE_LENGTH);
        lines[1] = HoloText.sanitise(input.getStringOr("line1", ""), HoloText.LINE_LENGTH);
        display.read(input, "display_");
    }
}
