package za.co.neroland.nerodecor.chroma;

import java.util.ArrayList;
import java.util.List;

/**
 * A hub's palette: up to {@link #MAX_ENTRIES} colours. Blocks store a one-byte index into it.
 * Not thread-safe; owned by one hub.
 */
public final class ChromaPalette {

    public static final int MAX_ENTRIES = 256;

    private final List<ChromaColor> entries = new ArrayList<>();

    public int size() {
        return entries.size();
    }

    public boolean isFull() {
        return entries.size() >= MAX_ENTRIES;
    }

    public boolean isValidIndex(int index) {
        return index >= 0 && index < entries.size();
    }

    /** Colour at {@code index}. @throws IndexOutOfBoundsException when out of range */
    public ChromaColor get(int index) {
        if (!isValidIndex(index)) {
            throw new IndexOutOfBoundsException("palette index " + index + " of " + entries.size());
        }
        return entries.get(index);
    }

    /** Index of an equal colour (rgb and brightness), or -1. */
    public int indexOf(ChromaColor color) {
        return entries.indexOf(color);
    }

    /**
     * Adds a colour, reusing an existing equal entry.
     *
     * @return the entry's index, or -1 when the colour is new and the palette is full
     */
    public int addOrFind(ChromaColor color) {
        if (color == null) {
            throw new IllegalArgumentException("null colour");
        }
        int existing = indexOf(color);
        if (existing >= 0) {
            return existing;
        }
        if (isFull()) {
            return -1;
        }
        entries.add(color);
        return entries.size() - 1;
    }

    /**
     * Replaces the entry at {@code index}, or appends when {@code index == size()} and there is room.
     *
     * @return whether the palette changed shape or content legally
     */
    public boolean set(int index, ChromaColor color) {
        if (color == null || index < 0 || index > entries.size() || index >= MAX_ENTRIES) {
            return false;
        }
        if (index == entries.size()) {
            entries.add(color);
        } else {
            entries.set(index, color);
        }
        return true;
    }

    /** Packed colours, in index order. */
    public int[] toPacked() {
        int[] out = new int[entries.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = entries.get(i).packed();
        }
        return out;
    }

    /** Palette from packed colours; {@code null} when too long or any value is not canonical. */
    public static ChromaPalette fromPacked(int[] packed) {
        if (packed == null || packed.length > MAX_ENTRIES) {
            return null;
        }
        ChromaPalette palette = new ChromaPalette();
        for (int p : packed) {
            if (!ChromaColor.isValidPacked(p)) {
                return null;
            }
            palette.entries.add(ChromaColor.fromPacked(p));
        }
        return palette;
    }
}
