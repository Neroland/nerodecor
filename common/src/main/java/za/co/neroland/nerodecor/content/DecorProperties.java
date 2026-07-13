package za.co.neroland.nerodecor.content;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Shared blockstate properties for decor blocks. {@link #COLOR} is the paintable colour
 * (see {@link DecorColor} + ADR-001) carried by every decor block; {@link #WATERLOGGED} is
 * vanilla's, reused by the shape blocks that extend waterloggable vanilla blocks.
 */
public final class DecorProperties {

    /** The paintable colour property. Rendered via a tint provider; the paint gun rewrites it. */
    public static final EnumProperty<DecorColor> COLOR = EnumProperty.create("color", DecorColor.class);

    /** Vanilla waterlogging (reused by slab/stair/wall/pane subclasses). */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private DecorProperties() {
    }
}
