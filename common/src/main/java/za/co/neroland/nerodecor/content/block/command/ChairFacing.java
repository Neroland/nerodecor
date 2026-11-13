package za.co.neroland.nerodecor.content.block.command;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** Operator chair facing: the eight compass directions, clockwise from north (blockstate {@code facing}). */
public enum ChairFacing implements StringRepresentable {
    NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The facing nearest to compass bearing {@code degrees} (north = 0, east = 90). */
    public static ChairFacing fromBearing(float degrees) {
        int i = Math.floorMod(Math.round(degrees / 45.0F), 8);
        return values()[i];
    }

    public ChairFacing rotate(int quarterTurns) {
        return values()[Math.floorMod(ordinal() + quarterTurns * 2, 8)];
    }
}
