package za.co.neroland.nerodecor.content.block.holo;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** Arrow sign pointing direction as read from the front (blockstate {@code direction}), clockwise from up. */
public enum ArrowDirection implements StringRepresentable {
    UP, UP_RIGHT, RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ArrowDirection next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
