package za.co.neroland.nerodecor.chroma.editor;

import java.util.Arrays;
import java.util.Set;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.RelativePos;

/**
 * Turns the hub's linked blocks into filled pixel rectangles for the editor viewport, with no game renderer involved:
 * each block's exposed, camera-facing faces are projected through the {@link OrbitCamera}, sorted back to front and
 * scan-converted into horizontal runs (merged vertically where they line up). Above {@link #LOD_THRESHOLD} visible
 * blocks it switches to plain cubes: one depth-sized square per block. Blocks above the Y slice are hidden.
 *
 * <p>Each face (or cube) is drawn as an edge-coloured quad with the face colour inset inside it, so every voxel reads as
 * a block even where neighbours share a colour. A block without a palette entry shows the model's display fallback
 * (what the world shows), else {@link #EMPTY_ARGB}.
 */
public final class ViewportRaster {

    /** Above this many visible blocks the viewport draws plain cubes. */
    public static final int LOD_THRESHOLD = 2000;
    /** Hard cap on rectangles per frame. */
    public static final int MAX_RECTS = 120_000;

    public static final int EMPTY_ARGB = 0xFF3A4152;
    public static final int SELECT_RGB = 0x22E8FF;
    public static final int GRID_ARGB = 0x5522E8FF;
    public static final int BOUNDS_ARGB = 0x998A5CFF;
    public static final int HOVER_ARGB = 0xFFFFFFFF;

    /** Inset of the face colour inside its edge, as a fraction of a block. */
    static final double EDGE_INSET = 0.07;
    /** Edges are drawn only when a block is at least this many pixels across. */
    static final double EDGE_MIN_PIXELS = 5.0;

    private static final double[] SHADE = {0.82, 0.82, 1.0, 0.55, 0.68, 0.68};
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private ViewportRaster() {
    }

    /** Whether {@code visible} blocks are drawn as plain cubes. */
    public static boolean useLod(int visible) {
        return visible > LOD_THRESHOLD;
    }

    /** Blocks at or below {@code sliceY}. */
    public static int visibleCount(EditorModel model, int sliceY) {
        int n = 0;
        for (int i = 0; i < model.blockCount(); i++) {
            if (RelativePos.y(model.offset(i)) <= sliceY) {
                n++;
            }
        }
        return n;
    }

    /**
     * Rasterises the model into {@code out} (cleared first).
     *
     * @param selected blocks drawn with the selection tint (may be empty)
     * @param hover    packed offset outlined on top, or {@code Integer.MIN_VALUE}
     * @return whether the plain-cube level of detail was used
     */
    public static boolean render(EditorModel model, OrbitCamera camera, int sliceY, Set<Integer> selected, int hover,
            RectList out) {
        out.clear();
        int visible = visibleCount(model, sliceY);
        boolean lod = useLod(visible);
        if (lod) {
            cubes(model, camera, sliceY, selected, out);
        } else {
            faces(model, camera, sliceY, selected, out);
        }
        if (hover != Integer.MIN_VALUE && model.contains(hover)) {
            cellOutline(camera, RelativePos.x(hover), RelativePos.y(hover), RelativePos.z(hover), HOVER_ARGB, out);
        }
        return lod;
    }

    /** The hub volume's floor grid (every 4 blocks) and bounding box, drawn behind the blocks. */
    public static void volume(OrbitCamera camera, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            RectList out) {
        double x0 = minX;
        double x1 = maxX + 1.0;
        double y0 = minY;
        double y1 = maxY + 1.0;
        double z0 = minZ;
        double z1 = maxZ + 1.0;
        for (int x = minX; x <= maxX + 1; x += 4) {
            line(camera, x, y0, z0, x, y0, z1, GRID_ARGB, out);
        }
        for (int z = minZ; z <= maxZ + 1; z += 4) {
            line(camera, x0, y0, z, x1, y0, z, GRID_ARGB, out);
        }
        box(camera, x0, y0, z0, x1, y1, z1, BOUNDS_ARGB, out);
    }

    // --- full faces ----------------------------------------------------------------------------------------------

    private static void faces(EditorModel model, OrbitCamera camera, int sliceY, Set<Integer> selected, RectList out) {
        int n = model.blockCount();
        double[] depth = new double[n * 6];
        int[] face = new int[n * 6];
        int count = 0;
        double ex = camera.eyeX();
        double ey = camera.eyeY();
        double ez = camera.eyeZ();
        for (int i = 0; i < n; i++) {
            int rel = model.offset(i);
            int x = RelativePos.x(rel);
            int y = RelativePos.y(rel);
            int z = RelativePos.z(rel);
            if (y > sliceY) {
                continue;
            }
            for (int d = 0; d < 6; d++) {
                int[] dir = DIRS[d];
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];
                if (ny <= sliceY && model.contains(nx, ny, nz)) {
                    continue;
                }
                double cx = x + 0.5 + dir[0] * 0.5;
                double cy = y + 0.5 + dir[1] * 0.5;
                double cz = z + 0.5 + dir[2] * 0.5;
                if ((ex - cx) * dir[0] + (ey - cy) * dir[1] + (ez - cz) * dir[2] <= 0.0) {
                    continue;
                }
                double dd = camera.depth(cx, cy, cz);
                if (dd <= 0.0) {
                    continue;
                }
                depth[count] = dd;
                face[count] = i * 6 + d;
                count++;
            }
        }
        Integer[] order = new Integer[count];
        for (int k = 0; k < count; k++) {
            order[k] = k;
        }
        final double[] depths = depth;
        Arrays.sort(order, (a, b) -> Double.compare(depths[b], depths[a]));
        double[][] pts = new double[4][3];
        for (int k = 0; k < count && !out.full(); k++) {
            int code = face[order[k]];
            int i = code / 6;
            int d = code % 6;
            int rel = model.offset(i);
            int x = RelativePos.x(rel);
            int y = RelativePos.y(rel);
            int z = RelativePos.z(rel);
            if (!corners(camera, x, y, z, d, pts, 0.0)) {
                continue;
            }
            int argb = colour(model, rel, selected, SHADE[d]);
            if (camera.pixelsPerBlock(depths[order[k]]) >= EDGE_MIN_PIXELS) {
                fillQuad(pts, camera.width(), camera.height(), edge(argb), out);
                if (!corners(camera, x, y, z, d, pts, EDGE_INSET)) {
                    continue;
                }
            }
            fillQuad(pts, camera.width(), camera.height(), argb, out);
        }
    }

    /** Projects face {@code d} of cell {@code (x,y,z)}; false if any corner is behind the eye. */
    static boolean corners(OrbitCamera camera, int x, int y, int z, int d, double[][] pts) {
        return corners(camera, x, y, z, d, pts, 0.0);
    }

    /** As {@link #corners(OrbitCamera, int, int, int, int, double[][])}, shrunk by {@code inset} on each side. */
    static boolean corners(OrbitCamera camera, int x, int y, int z, int d, double[][] pts, double inset) {
        int axis = d / 2;
        boolean positive = d % 2 == 0;
        double[] base = {x, y, z};
        double plane = base[axis] + (positive ? 1.0 : 0.0);
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        double lo = inset;
        double hi = 1.0 - inset;
        double[][] square = {{lo, lo}, {hi, lo}, {hi, hi}, {lo, hi}};
        double[] p = new double[3];
        for (int c = 0; c < 4; c++) {
            p[axis] = plane;
            p[a1] = base[a1] + square[c][0];
            p[a2] = base[a2] + square[c][1];
            if (!camera.project(p[0], p[1], p[2], pts[c])) {
                return false;
            }
        }
        return true;
    }

    /** Scan-converts a convex polygon into horizontal runs, merging rows with equal extents. */
    static void fillQuad(double[][] pts, int width, int height, int argb, RectList out) {
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : pts) {
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        int rowStart = Math.max(0, (int) Math.ceil(minY - 0.5));
        int rowEnd = Math.min(height - 1, (int) Math.floor(maxY - 0.5));
        int runX0 = 0;
        int runX1 = 0;
        int runY0 = -1;
        int runY1 = -1;
        for (int row = rowStart; row <= rowEnd; row++) {
            double yc = row + 0.5;
            double xl = Double.MAX_VALUE;
            double xr = -Double.MAX_VALUE;
            for (int e = 0; e < pts.length; e++) {
                double[] p = pts[e];
                double[] q = pts[(e + 1) % pts.length];
                if ((p[1] <= yc && q[1] > yc) || (q[1] <= yc && p[1] > yc)) {
                    double t = (yc - p[1]) / (q[1] - p[1]);
                    double xi = p[0] + t * (q[0] - p[0]);
                    xl = Math.min(xl, xi);
                    xr = Math.max(xr, xi);
                }
            }
            if (xl > xr) {
                continue;
            }
            int x0 = Math.max(0, (int) Math.round(xl));
            int x1 = Math.min(width, (int) Math.round(xr));
            if (x1 <= x0) {
                continue;
            }
            if (runY0 >= 0 && x0 == runX0 && x1 == runX1 && row == runY1) {
                runY1 = row + 1;
            } else {
                if (runY0 >= 0) {
                    out.add(runX0, runY0, runX1, runY1, argb);
                }
                runX0 = x0;
                runX1 = x1;
                runY0 = row;
                runY1 = row + 1;
            }
        }
        if (runY0 >= 0) {
            out.add(runX0, runY0, runX1, runY1, argb);
        }
    }

    // --- plain cubes (LOD) ---------------------------------------------------------------------------------------

    private static void cubes(EditorModel model, OrbitCamera camera, int sliceY, Set<Integer> selected, RectList out) {
        int n = model.blockCount();
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] ds = new double[n];
        Integer[] order = new Integer[n];
        int count = 0;
        double[] p = new double[3];
        for (int i = 0; i < n; i++) {
            int rel = model.offset(i);
            if (RelativePos.y(rel) > sliceY) {
                continue;
            }
            if (!camera.project(RelativePos.x(rel) + 0.5, RelativePos.y(rel) + 0.5, RelativePos.z(rel) + 0.5, p)) {
                continue;
            }
            xs[i] = p[0];
            ys[i] = p[1];
            ds[i] = p[2];
            order[count++] = i;
        }
        Arrays.sort(order, 0, count, (a, b) -> Double.compare(ds[b], ds[a]));
        int w = camera.width();
        int h = camera.height();
        for (int k = 0; k < count && !out.full(); k++) {
            int i = order[k];
            double half = Math.max(0.5, camera.pixelsPerBlock(ds[i]) * 0.55);
            int x0 = Math.max(0, (int) Math.round(xs[i] - half));
            int y0 = Math.max(0, (int) Math.round(ys[i] - half));
            int x1 = Math.min(w, (int) Math.round(xs[i] + half));
            int y1 = Math.min(h, (int) Math.round(ys[i] + half));
            int argb = colour(model, model.offset(i), selected, 0.9);
            if (x1 - x0 >= 4 && y1 - y0 >= 4) {
                out.add(x0, y0, x1, y1, edge(argb));
                out.add(x0 + 1, y0 + 1, x1 - 1, y1 - 1, argb);
            } else {
                out.add(x0, y0, x1, y1, argb);
            }
        }
    }

    // --- lines ---------------------------------------------------------------------------------------------------

    private static void cellOutline(OrbitCamera camera, int x, int y, int z, int argb, RectList out) {
        box(camera, x - 0.02, y - 0.02, z - 0.02, x + 1.02, y + 1.02, z + 1.02, argb, out);
    }

    private static void box(OrbitCamera camera, double x0, double y0, double z0, double x1, double y1, double z1,
            int argb, RectList out) {
        line(camera, x0, y0, z0, x1, y0, z0, argb, out);
        line(camera, x0, y1, z0, x1, y1, z0, argb, out);
        line(camera, x0, y0, z1, x1, y0, z1, argb, out);
        line(camera, x0, y1, z1, x1, y1, z1, argb, out);
        line(camera, x0, y0, z0, x0, y1, z0, argb, out);
        line(camera, x1, y0, z0, x1, y1, z0, argb, out);
        line(camera, x0, y0, z1, x0, y1, z1, argb, out);
        line(camera, x1, y0, z1, x1, y1, z1, argb, out);
        line(camera, x0, y0, z0, x0, y0, z1, argb, out);
        line(camera, x1, y0, z0, x1, y0, z1, argb, out);
        line(camera, x0, y1, z0, x0, y1, z1, argb, out);
        line(camera, x1, y1, z0, x1, y1, z1, argb, out);
    }

    /** A 1-pixel line between two world points (skipped when an end is behind the eye). */
    static void line(OrbitCamera camera, double ax, double ay, double az, double bx, double by, double bz, int argb,
            RectList out) {
        double[] a = new double[3];
        double[] b = new double[3];
        if (!camera.project(ax, ay, az, a) || !camera.project(bx, by, bz, b)) {
            return;
        }
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps > 4096) {
            return;
        }
        int w = camera.width();
        int h = camera.height();
        boolean flat = Math.abs(dx) >= Math.abs(dy);
        int runA = 0;
        int runB0 = 0;
        int runB1 = -1;
        for (int s = 0; s <= steps; s++) {
            double t = steps == 0 ? 0.0 : s / (double) steps;
            int px = (int) Math.floor(a[0] + dx * t);
            int py = (int) Math.floor(a[1] + dy * t);
            if (px < 0 || py < 0 || px >= w || py >= h) {
                continue;
            }
            // Runs along the major axis: rows for flat lines, columns for steep ones.
            int major = flat ? px : py;
            int minor = flat ? py : px;
            if (runB1 >= runB0 && minor == runA && major >= runB0 - 1 && major <= runB1 + 1) {
                runB0 = Math.min(runB0, major);
                runB1 = Math.max(runB1, major);
                continue;
            }
            emitRun(flat, runA, runB0, runB1, argb, out);
            runA = minor;
            runB0 = major;
            runB1 = major;
        }
        emitRun(flat, runA, runB0, runB1, argb, out);
    }

    private static void emitRun(boolean flat, int minor, int from, int to, int argb, RectList out) {
        if (to < from) {
            return;
        }
        if (flat) {
            out.add(from, minor, to + 1, minor + 1, argb);
        } else {
            out.add(minor, from, minor + 1, to + 1, argb);
        }
    }

    // --- colour --------------------------------------------------------------------------------------------------

    static int colour(EditorModel model, int rel, Set<Integer> selected, double shade) {
        ChromaColor c = model.colourAt(rel);
        int fallback = c == null ? model.fallbackRgb(rel) : -1;
        int rgb = c != null ? c.rgb() : fallback >= 0 ? fallback : EMPTY_ARGB & 0xFFFFFF;
        if (selected.contains(rel)) {
            rgb = mix(rgb, SELECT_RGB, 0.45);
        }
        return 0xFF000000 | shadeRgb(rgb, shade);
    }

    /** The edge colour of a face: darker, or lighter for faces already near black so the edge still shows. */
    static int edge(int argb) {
        int rgb = argb & 0xFFFFFF;
        int luma = (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        return 0xFF000000 | (luma < 70 ? mix(rgb, 0x8A93A8, 0.35) : shadeRgb(rgb, 0.6));
    }

    static int shadeRgb(int rgb, double f) {
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * f);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * f);
        int b = (int) Math.round((rgb & 0xFF) * f);
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }
}
