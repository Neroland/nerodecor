package za.co.neroland.nerodecor.content;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The paintable colour of a decor block — 16 vanilla-dye-aligned accents plus
 * {@link #NATURAL} (the block's own material tone, i.e. no paint). Stored as a
 * {@code BlockState} {@link net.minecraft.world.level.block.state.properties.EnumProperty}
 * and mirrored on the item via the {@code nerodecor:color} data component; the paint gun
 * (Stage F) simply rewrites the state — no new registry entry per colour (see ADR-001).
 *
 * <p>{@link #tint()} is the render tint the block/item colour providers return:
 * {@code NATURAL} is white (multiply-identity → show the base texture untouched), each
 * accent is its RGB. Accent RGBs mirror Neroland Core's {@code neroland:accent/*} palette
 * ({@code CoreFinishes}); keep them in lockstep.
 */
public enum DecorColor implements StringRepresentable {

    NATURAL("natural", 0xFFFFFF),
    WHITE("white", 0xF9FFFE),
    LIGHT_GRAY("light_gray", 0x9D9D97),
    GRAY("gray", 0x474F52),
    BLACK("black", 0x1D1D21),
    RED("red", 0xB02E26),
    ORANGE("orange", 0xF9801D),
    YELLOW("yellow", 0xFED83D),
    LIME("lime", 0x80C71F),
    GREEN("green", 0x5E7C16),
    CYAN("cyan", 0x169C9C),
    LIGHT_BLUE("light_blue", 0x3AB3DA),
    BLUE("blue", 0x3C44AA),
    PURPLE("purple", 0x8932B8),
    MAGENTA("magenta", 0xC74EBD),
    PINK("pink", 0xF38BAA),
    BROWN("brown", 0x835432);

    /** Codec (by serialized name) for the {@code nerodecor:color} data component + datapacks. */
    public static final Codec<DecorColor> CODEC = StringRepresentable.fromEnum(DecorColor::values);

    /** Network stream codec (by ordinal) for the item data component. */
    public static final StreamCodec<ByteBuf, DecorColor> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[i], DecorColor::ordinal);

    private final String name;
    private final int tint;

    DecorColor(String name, int tint) {
        this.name = name;
        this.tint = tint;
    }

    /** The render tint (0xRRGGBB); {@link #NATURAL} = white (no tint). */
    public int tint() {
        return tint;
    }

    /** Whether this is the unpainted material tone. */
    public boolean isNatural() {
        return this == NATURAL;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
