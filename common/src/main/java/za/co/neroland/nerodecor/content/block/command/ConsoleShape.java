package za.co.neroland.nerodecor.content.block.command;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** Command console run shape (blockstate {@code shape}). */
public enum ConsoleShape implements StringRepresentable {
    STRAIGHT, INNER, OUTER;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
