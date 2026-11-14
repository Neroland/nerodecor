package za.co.neroland.nerodecor.chroma.editor;

import java.util.Arrays;

/** A growable list of filled pixel rectangles {@code [x0,x1) x [y0,y1)} with an ARGB colour, drawn in order. */
public final class RectList {

    private int[] data = new int[5 * 256];
    private int size;
    private final int limit;

    public RectList(int limit) {
        this.limit = limit;
    }

    public int size() {
        return size;
    }

    public boolean full() {
        return size >= limit;
    }

    public void clear() {
        size = 0;
    }

    public void add(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || size >= limit) {
            return;
        }
        int at = size * 5;
        if (at + 5 > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[at] = x0;
        data[at + 1] = y0;
        data[at + 2] = x1;
        data[at + 3] = y1;
        data[at + 4] = argb;
        size++;
    }

    public int x0(int i) {
        return data[i * 5];
    }

    public int y0(int i) {
        return data[i * 5 + 1];
    }

    public int x1(int i) {
        return data[i * 5 + 2];
    }

    public int y1(int i) {
        return data[i * 5 + 3];
    }

    public int argb(int i) {
        return data[i * 5 + 4];
    }
}
