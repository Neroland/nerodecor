package za.co.neroland.nerodecor.content.block.holo;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The {@code link.display} content a surface currently shows: sanitised text, an icon index and a status colour.
 * Cosmetic world-state only (Core's {@code DisplayPayload} contract): nothing here can identify a player.
 */
public final class DisplayState {

    private String text = "";
    private int icon = -1;
    private int colour = -1;

    public boolean isBlank() {
        return text.isEmpty() && icon < 0;
    }

    public String text() {
        return text;
    }

    public int icon() {
        return icon;
    }

    /** Packed 0xRRGGBB, or -1 for the surface default. */
    public int colour() {
        return colour;
    }

    public void setText(Component text) {
        this.text = HoloText.sanitise(text == null ? "" : text.getString(), HoloText.PAYLOAD_LENGTH);
    }

    public void setIcon(Identifier id) {
        this.icon = HoloText.icon(id);
    }

    public void setColour(int rgb) {
        this.colour = rgb < 0 ? -1 : rgb & 0xFFFFFF;
    }

    public void clear() {
        text = "";
        icon = -1;
        colour = -1;
    }

    public void copyFrom(DisplayState other) {
        text = other.text;
        icon = other.icon;
        colour = other.colour;
    }

    public void write(ValueOutput output, String prefix) {
        output.putString(prefix + "text", text);
        output.putInt(prefix + "icon", icon);
        output.putInt(prefix + "colour", colour);
    }

    public void read(ValueInput input, String prefix) {
        text = HoloText.sanitise(input.getStringOr(prefix + "text", ""), HoloText.PAYLOAD_LENGTH);
        icon = input.getIntOr(prefix + "icon", -1);
        if (icon >= HoloText.ICONS.size()) {
            icon = -1;
        }
        colour = input.getIntOr(prefix + "colour", -1);
    }
}
