package za.co.neroland.nerodecor.content.block.command;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * Command console run shape (blockstate {@code shape}). The {@code _MIRRORED} corners are the mirror images of
 * {@link #INNER} / {@link #OUTER}; their model is the same corner turned a further 90 degrees clockwise.
 */
public enum ConsoleShape implements StringRepresentable {
    STRAIGHT, INNER, OUTER, INNER_MIRRORED, OUTER_MIRRORED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
