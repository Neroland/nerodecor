package za.co.neroland.nerodecor.content.block.holo;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** The seven Nerospace worlds a world globe can show (blockstate {@code world}). */
public enum GlobeWorld implements StringRepresentable {
    STATION, LUNA, MARS, CINDARA, GLACIRA, GREENXERTZ, NYXARA;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public GlobeWorld next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
