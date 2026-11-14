package za.co.neroland.nerodecor.chroma.editor;

/**
 * Ray versus voxel grid (Amanatides-Woo traversal) over a bounded box of cells. Returns the first occupied cell the
 * ray enters and the face it entered through.
 */
public final class VoxelPicker {

    /** A cell test. */
    @FunctionalInterface
    public interface Occupancy {
        boolean occupied(int x, int y, int z);
    }

    /** A hit: the cell, and the outward normal of the face the ray came through (all zero if it started inside). */
    public record Hit(int x, int y, int z, int nx, int ny, int nz, double distance) {
    }

    private VoxelPicker() {
    }

    /**
     * Casts {@code ray} through cells {@code [minX..maxX] x [minY..maxY] x [minZ..maxZ]} (inclusive cell coordinates).
     *
     * @return the first occupied cell, or {@code null}
     */
    public static Hit pick(OrbitCamera.Ray ray, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            Occupancy occupancy) {
        double ox = ray.ox();
        double oy = ray.oy();
        double oz = ray.oz();
        double dx = ray.dx();
        double dy = ray.dy();
        double dz = ray.dz();
        // Slab test against the box [min, max + 1).
        double[] lo = {minX, minY, minZ};
        double[] hi = {maxX + 1.0, maxY + 1.0, maxZ + 1.0};
        double[] o = {ox, oy, oz};
        double[] d = {dx, dy, dz};
        double tEnter = 0.0;
        double tExit = Double.POSITIVE_INFINITY;
        int enterAxis = -1;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-12) {
                if (o[a] < lo[a] || o[a] >= hi[a]) {
                    return null;
                }
                continue;
            }
            double t0 = (lo[a] - o[a]) / d[a];
            double t1 = (hi[a] - o[a]) / d[a];
            if (t0 > t1) {
                double t = t0;
                t0 = t1;
                t1 = t;
            }
            if (t0 > tEnter) {
                tEnter = t0;
                enterAxis = a;
            }
            tExit = Math.min(tExit, t1);
        }
        if (tEnter > tExit) {
            return null;
        }
        double eps = 1e-9;
        double px = ox + dx * (tEnter + eps);
        double py = oy + dy * (tEnter + eps);
        double pz = oz + dz * (tEnter + eps);
        int x = clamp((int) Math.floor(px), minX, maxX);
        int y = clamp((int) Math.floor(py), minY, maxY);
        int z = clamp((int) Math.floor(pz), minZ, maxZ);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tDeltaX = Math.abs(dx) < 1e-12 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = Math.abs(dy) < 1e-12 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = Math.abs(dz) < 1e-12 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMaxX = Math.abs(dx) < 1e-12 ? Double.POSITIVE_INFINITY
                : ((dx > 0 ? x + 1.0 : x) - ox) / dx;
        double tMaxY = Math.abs(dy) < 1e-12 ? Double.POSITIVE_INFINITY
                : ((dy > 0 ? y + 1.0 : y) - oy) / dy;
        double tMaxZ = Math.abs(dz) < 1e-12 ? Double.POSITIVE_INFINITY
                : ((dz > 0 ? z + 1.0 : z) - oz) / dz;
        int nx = enterAxis == 0 ? -stepX : 0;
        int ny = enterAxis == 1 ? -stepY : 0;
        int nz = enterAxis == 2 ? -stepZ : 0;
        double t = tEnter;
        int limit = (maxX - minX + 1) + (maxY - minY + 1) + (maxZ - minZ + 1) + 3;
        for (int i = 0; i < limit; i++) {
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                return null;
            }
            if (occupancy.occupied(x, y, z)) {
                return new Hit(x, y, z, nx, ny, nz, t);
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
                nx = -stepX;
                ny = 0;
                nz = 0;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
                nx = 0;
                ny = -stepY;
                nz = 0;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                nx = 0;
                ny = 0;
                nz = -stepZ;
            }
        }
        return null;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
