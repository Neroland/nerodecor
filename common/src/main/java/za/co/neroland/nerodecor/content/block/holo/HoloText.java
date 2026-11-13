package za.co.neroland.nerodecor.content.block.holo;

import java.util.List;
import java.util.Locale;

import net.minecraft.resources.Identifier;

/**
 * Text rules shared by the holo sign, holo table and wall screen: what may be shown (sanitising), and how a character
 * maps onto the shipped glyph pages. Pure Java.
 */
public final class HoloText {

    /** Characters per sign line. */
    public static final int LINE_LENGTH = 32;
    /** Characters kept from a {@code link.display} payload. */
    public static final int PAYLOAD_LENGTH = 64;
    /** Icon names in {@code holo_sign_icons} atlas order (4 x 4, 8 px cells). */
    public static final List<String> ICONS = List.of("warning", "arrow", "power", "lock", "home", "rocket", "energy",
            "fluid", "check", "cross", "info", "gear", "star", "life", "air", "hazard");

    private HoloText() {
    }

    /** Printable ASCII only (no formatting codes, no control characters), trimmed to {@code max} characters. */
    public static String sanitise(String raw, int max) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), max));
        for (int i = 0; i < raw.length() && out.length() < max; i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++; // drop the formatting code and its argument
                continue;
            }
            if (c >= 32 && c <= 126) {
                out.append(c);
            }
        }
        return out.toString().strip();
    }

    /** Glyph index on the holo sign pages ({@code holo_sign_glyphs_N}: index = codepoint - 32), or -1. */
    public static int signGlyph(char c) {
        return c >= 32 && c <= 126 ? c - 32 : -1;
    }

    /** Glyph index on the wall screen pages (upper case only; lower case folds), or -1. */
    public static int screenGlyph(char c) {
        char u = Character.toUpperCase(c);
        return u >= 0x20 && u <= 0x5F ? u - 0x20 : -1;
    }

    /** Index into {@link #ICONS} for an icon id ({@code ns:warning}, {@code ns:icon/warning}), or -1. */
    public static int icon(Identifier id) {
        if (id == null) {
            return -1;
        }
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return ICONS.indexOf((slash >= 0 ? path.substring(slash + 1) : path).toLowerCase(Locale.ROOT));
    }

    /** Greedy word wrap into at most {@code lines} lines of {@code width} characters. */
    public static List<String> wrap(String text, int width, int lines) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        String rest = text == null ? "" : text.strip();
        while (!rest.isEmpty() && out.size() < lines) {
            if (rest.length() <= width) {
                out.add(rest);
                break;
            }
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= 0) {
                cut = width;
            }
            out.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        return out;
    }
}
