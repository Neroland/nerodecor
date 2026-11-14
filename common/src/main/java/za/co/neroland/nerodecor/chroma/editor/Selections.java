package za.co.neroland.nerodecor.chroma.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.ChromaGradient;
import za.co.neroland.nerodecor.chroma.RelativePos;

/** Block selections and colourings the editor tools build: box, flood fill, two-point and axis gradients. */
public final class Selections {

    /** What a flood fill must match to spread. */
    public enum FloodMatch {
        /** Any linked neighbour. */
        LINKED,
        /** Same zone as the start block. */
        ZONE,
        /** Same colour (palette index) as the start block. */
        COLOUR,
        /** Same zone and colour. */
        ZONE_AND_COLOUR
    }

    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private Selections() {
    }

    /** Linked blocks inside the box spanned by two corners (inclusive), at or below {@code maxY}. */
    public static List<Integer> box(EditorModel model, int ax, int ay, int az, int bx, int by, int bz, int maxY) {
        int x0 = Math.min(ax, bx);
        int x1 = Math.max(ax, bx);
        int y0 = Math.min(ay, by);
        int y1 = Math.min(Math.max(ay, by), maxY);
        int z0 = Math.min(az, bz);
        int z1 = Math.max(az, bz);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < model.blockCount(); i++) {
            int rel = model.offset(i);
            int x = RelativePos.x(rel);
            int y = RelativePos.y(rel);
            int z = RelativePos.z(rel);
            if (x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1) {
                out.add(rel);
            }
        }
        return out;
    }

    /**
     * Six-connected flood over linked blocks from {@code start}, at or below {@code maxY}, visiting at most
     * {@code limit} blocks.
     */
    public static List<Integer> flood(EditorModel model, int start, FloodMatch match, int maxY, int limit) {
        List<Integer> out = new ArrayList<>();
        if (!model.contains(start) || RelativePos.y(start) > maxY) {
            return out;
        }
        int zone = model.zoneAt(start);
        int index = model.indexAt(start);
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < limit) {
            int rel = queue.poll();
            out.add(rel);
            int x = RelativePos.x(rel);
            int y = RelativePos.y(rel);
            int z = RelativePos.z(rel);
            for (int[] n : NEIGHBOURS) {
                int nx = x + n[0];
                int ny = y + n[1];
                int nz = z + n[2];
                if (ny > maxY || !RelativePos.inRange(nx, ny, nz)) {
                    continue;
                }
                int next = RelativePos.pack(nx, ny, nz);
                if (seen.contains(next) || !model.contains(next)) {
                    continue;
                }
                boolean zoneOk = model.zoneAt(next) == zone;
                boolean colourOk = model.indexAt(next) == index;
                boolean ok = switch (match) {
                    case LINKED -> true;
                    case ZONE -> zoneOk;
                    case COLOUR -> colourOk;
                    case ZONE_AND_COLOUR -> zoneOk && colourOk;
                };
                if (ok) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return out;
    }

    /**
     * Colours {@code blocks} along the segment from block {@code a} to block {@code b} (centres), in OKLab, quantised
     * to {@code steps} colours so a gradient costs at most that many palette entries.
     */
    public static Map<Integer, ChromaColor> twoPointGradient(List<Integer> blocks, int a, int b, ChromaGradient gradient,
            int steps) {
        double ax = RelativePos.x(a);
        double ay = RelativePos.y(a);
        double az = RelativePos.z(a);
        double vx = RelativePos.x(b) - ax;
        double vy = RelativePos.y(b) - ay;
        double vz = RelativePos.z(b) - az;
        double len2 = vx * vx + vy * vy + vz * vz;
        Map<Integer, ChromaColor> out = new LinkedHashMap<>();
        for (int rel : blocks) {
            double t = len2 == 0.0 ? 0.0
                    : ((RelativePos.x(rel) - ax) * vx + (RelativePos.y(rel) - ay) * vy + (RelativePos.z(rel) - az) * vz)
                            / len2;
            out.put(rel, gradient.sample(quantise(t, steps)));
        }
        return out;
    }

    /** Colours {@code blocks} from the lowest to the highest coordinate on {@code axis} (0 x, 1 y, 2 z). */
    public static Map<Integer, ChromaColor> axisGradient(List<Integer> blocks, int axis, ChromaGradient gradient,
            int steps) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int rel : blocks) {
            int c = coord(rel, axis);
            min = Math.min(min, c);
            max = Math.max(max, c);
        }
        Map<Integer, ChromaColor> out = new LinkedHashMap<>();
        for (int rel : blocks) {
            double t = max == min ? 0.0 : (coord(rel, axis) - min) / (double) (max - min);
            out.put(rel, gradient.sample(quantise(t, steps)));
        }
        return out;
    }

    /** {@code t} clamped to 0-1 and snapped to one of {@code steps} evenly spaced values (steps &lt; 2 = 0). */
    public static double quantise(double t, int steps) {
        double u = Math.max(0.0, Math.min(1.0, t));
        if (steps < 2) {
            return 0.0;
        }
        return Math.round(u * (steps - 1)) / (double) (steps - 1);
    }

    private static int coord(int rel, int axis) {
        return switch (axis) {
            case 0 -> RelativePos.x(rel);
            case 1 -> RelativePos.y(rel);
            default -> RelativePos.z(rel);
        };
    }
}
