package za.co.neroland.nerodecor.content.block.bulkhead;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Blockstate properties of the bulkhead, holo and command collections. Names and values are the contract the
 * collection modules under {@code tools/collections/} generate blockstates for.
 */
public final class BhcProperties {

    /** Six-way mount: the side of the block the part is attached to. */
    public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Same block continues on the positive / negative end of the run. */
    public static final BooleanProperty POS_LINK = BooleanProperty.create("pos_link");
    public static final BooleanProperty NEG_LINK = BooleanProperty.create("neg_link");
    /** Same block continues on the viewer's left / right (looking at the front). */
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty IDLE = BooleanProperty.create("idle");
    public static final BooleanProperty OPAQUE = BooleanProperty.create("opaque");
    /** Blast door tile: row * 3 + col (row 0 = top, col 0 = the viewer's left). */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);

    private BhcProperties() {
    }

    /** The viewer's left when looking at a front that faces {@code facing}. */
    public static Direction leftOf(Direction facing) {
        return facing.getClockWise();
    }

    /** The viewer's right when looking at a front that faces {@code facing}. */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }
}
